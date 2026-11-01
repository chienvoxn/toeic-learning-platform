"""LangChain adapters. No network calls until an authorized request needs a model."""

from dataclasses import dataclass

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings

from app.config import Settings
from app.schemas import GeneratedAnswer


class ProviderUnavailable(RuntimeError):
    pass


class InvalidGeneration(RuntimeError):
    pass


@dataclass
class Generation:
    answer: GeneratedAnswer
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0


class GeminiProvider:
    def __init__(self, settings: Settings):
        self.settings = settings

    def _key(self) -> str:
        key = self.settings.google_api_key.get_secret_value()
        if not key:
            raise ProviderUnavailable("GOOGLE_API_KEY is not configured")
        return key

    def _embeddings(self):
        return GoogleGenerativeAIEmbeddings(
            model=self.settings.gemini_embedding_model,
            google_api_key=self._key(),
            output_dimensionality=self.settings.embedding_dimensions,
            request_options={"timeout": self.settings.provider_timeout_seconds},
            client_args={"timeout": self.settings.provider_timeout_seconds},
            vertexai=False,
        )

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        try:
            # Explicit task types for gemini-embedding-001: asymmetric retrieval.
            return self._embeddings().embed_documents(texts, task_type="RETRIEVAL_DOCUMENT")
        except Exception as exc:
            raise ProviderUnavailable("Document embedding failed") from exc

    def embed_query(self, query: str) -> list[float]:
        try:
            return self._embeddings().embed_query(query, task_type="RETRIEVAL_QUERY")
        except Exception as exc:
            raise ProviderUnavailable("Query embedding failed") from exc

    def generate(self, system_prompt: str, user_prompt: str) -> Generation:
        try:
            model = ChatGoogleGenerativeAI(
                model=self.settings.gemini_chat_model,
                google_api_key=self._key(),
                temperature=0.2,
                max_tokens=4096,
                timeout=self.settings.provider_timeout_seconds,
                max_retries=1,
                vertexai=False,
            )
            chain = model.with_structured_output(
                GeneratedAnswer,
                method="json_schema",
                include_raw=True,
            )
            result = chain.invoke(
                [
                    SystemMessage(content=system_prompt),
                    HumanMessage(content=user_prompt),
                ]
            )
        except Exception as exc:
            raise ProviderUnavailable("Gemini generation failed") from exc
        if result.get("parsing_error") or not isinstance(result.get("parsed"), GeneratedAnswer):
            raise InvalidGeneration("Gemini did not return a valid structured answer")
        usage = result["raw"].usage_metadata or {}
        return Generation(
            answer=result["parsed"],
            input_tokens=usage.get("input_tokens", 0),
            output_tokens=usage.get("output_tokens", 0),
            total_tokens=usage.get("total_tokens", 0),
        )
