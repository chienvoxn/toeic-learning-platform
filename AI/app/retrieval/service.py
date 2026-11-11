"""Truy xuất dùng chung, luôn giới hạn nguồn trước khi xếp hạng."""

from app.config import Settings
from app.infrastructure.vector_store import VectorStore
from app.retrieval.retriever import AuthorizedPgvectorRetriever
from app.schemas import Citation, SearchRequest
from app.shared.contracts import Provider


class RetrievalService:
    def __init__(self, settings: Settings, store: VectorStore, provider: Provider):
        self.settings, self.store, self.provider = settings, store, provider

    def search(self, request: SearchRequest) -> list[Citation]:
        # Never mutate a shared retriever's scope: concurrent users have isolated instances.
        retriever = AuthorizedPgvectorRetriever(
            store=self.store,
            provider=self.provider,
            allowed_document_ids=tuple(request.allowed_document_ids),
            dimensions=self.settings.embedding_dimensions,
            top_k=request.top_k,
        )
        documents = retriever.invoke(request.query)
        return [Citation.model_validate(doc.metadata["citation"]) for doc in documents]
