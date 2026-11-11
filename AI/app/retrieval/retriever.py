"""LangChain retriever with an immutable per-request authorization scope."""

from typing import Any
from uuid import UUID

from langchain_core.callbacks import CallbackManagerForRetrieverRun
from langchain_core.documents import Document
from langchain_core.retrievers import BaseRetriever

from app.infrastructure.vector_store import validate_vector
from app.providers import ProviderUnavailable


class AuthorizedPgvectorRetriever(BaseRetriever):
    store: Any
    provider: Any
    allowed_document_ids: tuple[UUID, ...]
    dimensions: int
    top_k: int

    def _get_relevant_documents(
        self, query: str, *, run_manager: CallbackManagerForRetrieverRun
    ) -> list[Document]:
        scope = list(self.allowed_document_ids)
        if not scope or not self.store.has_documents(scope):
            return []
        try:
            vector = validate_vector(self.provider.embed_query(query), self.dimensions)
        except ValueError as exc:
            raise ProviderUnavailable("Provider returned an invalid query embedding") from exc
        sources = self.store.search(vector, scope, self.top_k)
        return [
            Document(
                id=str(source.chunk_id),
                page_content=source.excerpt,
                metadata={"citation": source.model_dump(mode="json")},
            )
            for source in sources
        ]
