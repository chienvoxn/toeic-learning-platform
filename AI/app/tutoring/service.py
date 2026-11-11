"""Luồng đọc: retrieval có quyền, sinh câu trả lời và xác minh citation."""

import json
import time

from app.config import Settings
from app.providers import InvalidGeneration
from app.retrieval.service import RetrievalService
from app.schemas import (
    NO_SOURCE_WARNING,
    AskRequest,
    Citation,
    PracticeExercise,
    SearchRequest,
    TutorMode,
    TutorResponse,
    Usage,
)
from app.shared.contracts import Provider
from app.tutoring.prompts import SYSTEM_PROMPT


class TutorService:
    def __init__(self, settings: Settings, retrieval: RetrievalService, provider: Provider):
        self.settings, self.retrieval, self.provider = settings, retrieval, provider

    def ask(self, request: AskRequest, trace_id: str) -> TutorResponse:
        started = time.monotonic()
        usage = Usage(
            chat_model=self.settings.gemini_chat_model,
            embedding_model=self.settings.gemini_embedding_model,
        )

        def fallback():
            usage.latency_ms = round((time.monotonic() - started) * 1000)
            return TutorResponse(
                answer=NO_SOURCE_WARNING, warning=NO_SOURCE_WARNING, usage=usage, trace_id=trace_id
            )

        # Include recent user turns in retrieval so follow-up questions have topic context.
        recent = [message.content for message in request.history if message.role == "user"][-2:]
        # Keep embedding inputs short; full question/context/history still reach the chat model.
        query = "\n".join(
            [request.question[:1200], request.context[:1000], *[turn[:200] for turn in recent]]
        )
        sources = self.retrieval.search(
            SearchRequest(
                query=query,
                allowed_document_ids=request.allowed_document_ids,
                top_k=self.settings.retrieval_top_k,
            )
        )
        if not sources:
            return fallback()
        payload = {
            "mode": request.mode,
            "language": request.language,
            "question": request.question,
            "context": request.context,
            "history": [message.model_dump() for message in request.history],
            "exercise_count": request.exercise_count,
            "evidence": [
                {"chunk_id": str(source.chunk_id), "text": source.excerpt} for source in sources
            ],
        }
        generated = self.provider.generate(SYSTEM_PROMPT, json.dumps(payload, ensure_ascii=False))
        usage.input_tokens = generated.input_tokens
        usage.output_tokens = generated.output_tokens
        usage.total_tokens = generated.total_tokens
        answer = generated.answer
        if not answer.sufficient_evidence:
            return fallback()
        source_map = {str(source.chunk_id): source for source in sources}

        def verify(ids: list[str]) -> list[Citation]:
            if not ids or any(identifier not in source_map for identifier in ids):
                raise InvalidGeneration("Generated citation is missing or outside retrieved scope")
            return [source_map[identifier] for identifier in dict.fromkeys(ids)]

        citations = verify(answer.cited_chunk_ids)
        exercises = []
        if request.mode == TutorMode.PRACTICE:
            if len(answer.exercises) != request.exercise_count:
                raise InvalidGeneration("Incorrect number of practice exercises")
            for exercise in answer.exercises:
                verified = verify(exercise.cited_chunk_ids)
                exercises.append(
                    PracticeExercise(
                        question=exercise.question,
                        choices=exercise.choices,
                        correct_index=exercise.correct_index,
                        explanation=exercise.explanation,
                        citations=verified,
                    )
                )
                for source in verified:
                    if source.chunk_id not in {item.chunk_id for item in citations}:
                        citations.append(source)
        elif answer.exercises:
            raise InvalidGeneration("Unexpected exercises outside practice mode")
        usage.latency_ms = round((time.monotonic() - started) * 1000)
        return TutorResponse(
            answer=answer.answer,
            citations=citations,
            grounded=True,
            exercises=exercises,
            usage=usage,
            trace_id=trace_id,
        )
