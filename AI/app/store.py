"""Persistent vector index. PostgreSQL uses pgvector; SQLite is a small-corpus dev fallback."""

import math
from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID

from pgvector.sqlalchemy import Vector
from sqlalchemy import (
    JSON,
    Column,
    ForeignKey,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    create_engine,
    delete,
    insert,
    select,
    text,
)
from sqlalchemy.engine import make_url
from sqlalchemy.pool import StaticPool

from app.config import Settings
from app.schemas import Citation, DocumentInfo


class IndexConfigurationError(RuntimeError):
    pass


def validate_vector(vector: list[float], dimensions: int) -> list[float]:
    if len(vector) != dimensions or not all(math.isfinite(value) for value in vector):
        raise ValueError("Invalid embedding dimensions or nonfinite vector")
    magnitude = math.hypot(*vector)
    if not magnitude or not math.isfinite(magnitude):
        raise ValueError("Embedding must have a finite, nonzero norm")
    return [value / magnitude for value in vector]


class VectorStore:
    def __init__(self, settings: Settings):
        self.settings = settings
        url = make_url(settings.database_url)
        if url.get_backend_name() not in {"sqlite", "postgresql"}:
            raise ValueError("Only SQLite and PostgreSQL are supported")
        options = {"pool_pre_ping": True}
        if url.get_backend_name() == "sqlite":
            options["connect_args"] = {"check_same_thread": False, "timeout": 30}
            if not url.database or url.database == ":memory:":
                options["poolclass"] = StaticPool
            else:
                Path(url.database).parent.mkdir(parents=True, exist_ok=True)
        self.engine = create_engine(settings.database_url, **options)
        self.postgres = self.engine.dialect.name == "postgresql"
        metadata = MetaData()
        self.config = Table(
            "rag_index_config",
            metadata,
            Column("id", Integer, primary_key=True),
            Column("signature", String(500), nullable=False),
        )
        self.documents = Table(
            "rag_documents",
            metadata,
            Column("id", String(36), primary_key=True),
            Column("title", String(300), nullable=False),
            Column("source_url", Text),
            Column("chunk_count", Integer, nullable=False),
            Column("updated_at", String(40), nullable=False),
        )
        self.chunks = Table(
            "rag_chunks",
            metadata,
            Column("id", String(36), primary_key=True),
            Column(
                "document_id",
                String(36),
                ForeignKey("rag_documents.id"),
                nullable=False,
                index=True,
            ),
            Column("content", Text, nullable=False),
            Column("page", Integer),
            Column(
                "embedding",
                Vector(settings.embedding_dimensions).with_variant(JSON(), "sqlite"),
                nullable=False,
            ),
            Column("position", Integer, nullable=False),
        )
        if self.postgres:
            with self.engine.begin() as connection:
                connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
        metadata.create_all(self.engine)
        with self.engine.begin() as connection:
            signature = connection.scalar(select(self.config.c.signature))
            if signature is None:
                # One-time initialization is idempotent across concurrent workers.
                statement = (
                    "INSERT INTO rag_index_config (id, signature) VALUES (1, :signature) "
                    "ON CONFLICT (id) DO NOTHING"
                )
                connection.execute(text(statement), {"signature": settings.embedding_signature})
                signature = connection.scalar(select(self.config.c.signature))
            if signature != settings.embedding_signature:
                raise IndexConfigurationError(
                    "Embedding/chunk configuration differs from stored index. "
                    "Use a new database and re-index all documents."
                )

    def ping(self):
        with self.engine.connect() as connection:
            connection.execute(text("SELECT 1"))

    def close(self):
        self.engine.dispose()

    def has_documents(self, allowed_ids: list[UUID]) -> bool:
        if not allowed_ids:
            return False
        with self.engine.connect() as connection:
            return (
                connection.scalar(
                    select(self.documents.c.id)
                    .where(self.documents.c.id.in_([str(value) for value in allowed_ids]))
                    .limit(1)
                )
                is not None
            )

    def replace_document(
        self, document_id: UUID, title: str, source_url: str | None, chunks: list[dict]
    ) -> DocumentInfo:
        now = datetime.now(UTC).isoformat()
        identifier = str(document_id)
        # All embeddings have been created/validated before touching the old index.
        with self.engine.begin() as connection:
            connection.execute(delete(self.chunks).where(self.chunks.c.document_id == identifier))
            connection.execute(delete(self.documents).where(self.documents.c.id == identifier))
            connection.execute(
                insert(self.documents).values(
                    id=identifier,
                    title=title,
                    source_url=source_url,
                    chunk_count=len(chunks),
                    updated_at=now,
                )
            )
            connection.execute(
                insert(self.chunks), [{**chunk, "document_id": identifier} for chunk in chunks]
            )
        return DocumentInfo(
            document_id=document_id,
            title=title,
            source_url=source_url,
            chunk_count=len(chunks),
            updated_at=now,
        )

    def list_documents(self, limit: int, offset: int) -> list[DocumentInfo]:
        with self.engine.connect() as connection:
            rows = (
                connection.execute(
                    select(self.documents)
                    .order_by(self.documents.c.updated_at.desc(), self.documents.c.id)
                    .limit(limit)
                    .offset(offset)
                )
                .mappings()
                .all()
            )
        return [
            DocumentInfo(
                document_id=row["id"],
                **{key: row[key] for key in ("title", "source_url", "chunk_count", "updated_at")},
            )
            for row in rows
        ]

    def delete_document(self, document_id: UUID) -> bool:
        with self.engine.begin() as connection:
            connection.execute(
                delete(self.chunks).where(self.chunks.c.document_id == str(document_id))
            )
            result = connection.execute(
                delete(self.documents).where(self.documents.c.id == str(document_id))
            )
            return result.rowcount > 0

    def search(self, vector: list[float], allowed_ids: list[UUID], top_k: int) -> list[Citation]:
        if not allowed_ids:
            return []
        query = (
            select(
                self.chunks.c.id,
                self.chunks.c.document_id,
                self.chunks.c.content,
                self.chunks.c.page,
                self.documents.c.title,
                self.documents.c.source_url,
            )
            .join(self.documents)
            .where(self.chunks.c.document_id.in_([str(value) for value in allowed_ids]))
        )
        # Authorization filter is part of the SQL, before ranking or constructing a prompt.
        if self.postgres:
            score = (1 - self.chunks.c.embedding.cosine_distance(vector)).label("similarity")
            query = (
                query.add_columns(score)
                .where(score >= self.settings.min_similarity)
                .order_by(score.desc(), self.chunks.c.id)
                .limit(top_k)
            )
        else:
            query = query.add_columns(self.chunks.c.embedding)
        with self.engine.connect() as connection:
            rows = [dict(row) for row in connection.execute(query).mappings()]
        if not self.postgres:
            for row in rows:
                row["similarity"] = sum(
                    a * b for a, b in zip(vector, row.pop("embedding"), strict=True)
                )
            rows = sorted(
                (row for row in rows if row["similarity"] >= self.settings.min_similarity),
                key=lambda row: (-row["similarity"], row["id"]),
            )[:top_k]
        return [
            Citation(
                document_id=row["document_id"],
                chunk_id=row["id"],
                title=row["title"],
                source_url=row["source_url"],
                page=row["page"],
                excerpt=row["content"],
                similarity=round(max(-1.0, min(1.0, float(row["similarity"]))), 6),
            )
            for row in rows
        ]
