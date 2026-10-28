from enum import StrEnum
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, field_validator, model_validator

NO_SOURCE_WARNING = "Không tìm thấy tài liệu tham khảo trong hệ thống."


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda s: (
            s.split("_")[0] + "".join(word.capitalize() for word in s.split("_")[1:])
        ),
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class TutorMode(StrEnum):
    ASK = "ask"
    GRAMMAR = "grammar"
    VOCABULARY = "vocabulary"
    EXPLAIN_ANSWER = "explain_answer"
    READING = "reading"
    PRACTICE = "practice"


class HistoryMessage(ApiModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class SearchRequest(ApiModel):
    query: str = Field(min_length=1, max_length=4000)
    # Required and deny by default. Spring computes this list after checking ACLs.
    allowed_document_ids: list[UUID] = Field(max_length=500)
    top_k: int = Field(default=5, ge=1, le=10)


class AskRequest(ApiModel):
    question: str = Field(min_length=1, max_length=4000)
    allowed_document_ids: list[UUID] = Field(max_length=500)
    mode: TutorMode = TutorMode.ASK
    language: Literal["vi", "en"] = "vi"
    history: list[HistoryMessage] = Field(default_factory=list, max_length=12)
    # Authorized question/transcript/standard answer supplied by Spring, not used as evidence.
    context: str = Field(default="", max_length=12000)
    exercise_count: int = Field(default=3, ge=1, le=5)


class DocumentInput(ApiModel):
    document_id: UUID
    title: str = Field(min_length=1, max_length=300)
    text: str = Field(min_length=1, max_length=500_000)
    source_url: HttpUrl | None = None


class DocumentInfo(ApiModel):
    document_id: UUID
    title: str
    source_url: str | None
    chunk_count: int
    updated_at: str


class Citation(ApiModel):
    document_id: UUID
    chunk_id: UUID
    title: str
    source_url: str | None
    page: int | None = None
    excerpt: str
    similarity: float


class Usage(ApiModel):
    provider: str = "google"
    chat_model: str
    embedding_model: str
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0
    # Embedding token usage is not exposed by LangChain's Embeddings interface.
    embedding_tokens: int | None = None
    estimated_cost: float | None = None
    latency_ms: int = 0


class GeneratedExercise(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question: str = Field(min_length=1, max_length=2000)
    choices: list[str] = Field(min_length=4, max_length=4)
    correct_index: int = Field(ge=0, le=3)
    explanation: str = Field(min_length=1, max_length=2000)
    cited_chunk_ids: list[str] = Field(min_length=1, max_length=10)

    @field_validator("choices")
    @classmethod
    def distinct_choices(cls, values: list[str]):
        normalized = [value.strip().casefold() for value in values]
        if any(not value for value in normalized) or len(set(normalized)) != 4:
            raise ValueError("Four nonempty, distinct choices are required")
        return values


class GeneratedAnswer(BaseModel):
    """Structured provider output; source metadata is never accepted from the model."""

    model_config = ConfigDict(extra="forbid")
    answer: str = Field(min_length=1, max_length=16000)
    sufficient_evidence: bool
    cited_chunk_ids: list[str] = Field(default_factory=list, max_length=10)
    exercises: list[GeneratedExercise] = Field(default_factory=list, max_length=5)


class PracticeExercise(ApiModel):
    question: str
    choices: list[str]
    correct_index: int
    explanation: str
    citations: list[Citation]
    ai_generated: bool = True


class TutorResponse(ApiModel):
    answer: str
    citations: list[Citation] = Field(default_factory=list)
    grounded: bool = False
    warning: str | None = None
    exercises: list[PracticeExercise] = Field(default_factory=list)
    usage: Usage
    trace_id: str

    @model_validator(mode="after")
    def grounding_contract(self):
        if self.grounded and not self.citations:
            raise ValueError("A grounded response must have verified source IDs")
        return self
