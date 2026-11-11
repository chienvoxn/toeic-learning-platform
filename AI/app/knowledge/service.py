"""Luồng ghi: kiểm tra học liệu, chia đoạn, embedding rồi thay index nguyên tử."""

from hashlib import sha256
from uuid import UUID, uuid5

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config import Settings
from app.infrastructure.vector_store import VectorStore, validate_vector
from app.knowledge.errors import DocumentRejected
from app.providers import ProviderUnavailable
from app.schemas import DocumentInfo
from app.shared.contracts import Provider


class KnowledgeService:
    def __init__(self, settings: Settings, store: VectorStore, provider: Provider):
        self.settings, self.store, self.provider = settings, store, provider
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.chunk_size,
            chunk_overlap=settings.chunk_overlap,
            separators=["\n\n", "\n", ". ", " ", ""],
        )

    def ingest(
        self, document_id: UUID, title: str, pages: list[Document], source_url: str | None
    ) -> DocumentInfo:
        title = title.strip()
        if not title or len(title) > 300:
            raise DocumentRejected("Document title must contain 1 to 300 characters")
        if sum(len(page.page_content) for page in pages) > self.settings.max_document_characters:
            raise DocumentRejected("Document exceeds MAX_DOCUMENT_CHARACTERS")
        chunks = self.splitter.split_documents(pages)
        chunks = [chunk for chunk in chunks if chunk.page_content.strip()]
        if not chunks:
            raise DocumentRejected("No readable text. Scanned PDFs need OCR before indexing.")
        if len(chunks) > self.settings.max_document_chunks:
            raise DocumentRejected("Document exceeds MAX_DOCUMENT_CHUNKS")
        vectors = self.provider.embed_documents(
            [f"{title}\n{chunk.page_content}" for chunk in chunks]
        )
        if len(vectors) != len(chunks):
            raise ProviderUnavailable("Provider returned an incorrect number of embeddings")
        prepared = []
        for index, (chunk, vector) in enumerate(zip(chunks, vectors, strict=True)):
            try:
                normalized = validate_vector(vector, self.settings.embedding_dimensions)
            except ValueError as exc:
                raise ProviderUnavailable("Provider returned an invalid embedding") from exc
            digest = sha256(chunk.page_content.encode()).hexdigest()
            prepared.append(
                {
                    "id": str(uuid5(document_id, f"{index}:{digest}")),
                    "content": chunk.page_content,
                    "page": chunk.metadata.get("page"),
                    "embedding": normalized,
                    "position": index,
                }
            )
        return self.store.replace_document(document_id, title, source_url, prepared)

    def list_documents(self, limit: int, offset: int) -> list[DocumentInfo]:
        return self.store.list_documents(limit, offset)

    def delete_document(self, document_id: UUID) -> bool:
        return self.store.delete_document(document_id)
