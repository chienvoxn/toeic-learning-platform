"""Create the RAG index or adopt the verified legacy SQL migration without data loss."""

import hashlib
from pathlib import Path

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector

from app.infrastructure.migrations import validate_index_configuration

revision = "0001_rag_index"
down_revision = None
branch_labels = None
depends_on = None


def _adopt_legacy(connection, settings, schema):
    inspector = sa.inspect(connection)
    if not inspector.has_table("rag_schema_migrations", schema=schema):
        raise ValueError("Existing RAG tables have no verified legacy migration history")
    source = Path(__file__).resolve().parents[1] / "001_pgvector_index.sql"
    sql = source.read_text(encoding="utf-8").replace(
        "__DIMENSIONS__", str(settings.embedding_dimensions)
    )
    expected = {source.name: hashlib.sha256(sql.encode()).hexdigest()}
    actual = dict(
        connection.execute(sa.text("SELECT version, checksum FROM rag_schema_migrations")).all()
    )
    if actual != expected:
        raise ValueError("Legacy migration checksum/history differs; refusing to adopt")
    validate_index_configuration(connection, settings)
    expected_columns = {
        "rag_index_config": {"id": "integer", "signature": "character varying(500)"},
        "rag_documents": {
            "id": "character varying(36)",
            "title": "character varying(300)",
            "source_url": "text",
            "chunk_count": "integer",
            "updated_at": "character varying(40)",
        },
        "rag_chunks": {
            "id": "character varying(36)",
            "document_id": "character varying(36)",
            "content": "text",
            "page": "integer",
            "embedding": f"vector({settings.embedding_dimensions})",
            "position": "integer",
        },
    }
    for table, columns in expected_columns.items():
        rows = connection.execute(
            sa.text("""
            SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull
            FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = :schema AND c.relname = :table
              AND a.attnum > 0 AND NOT a.attisdropped
        """),
            {"schema": schema, "table": table},
        ).all()
        if {row.attname: row.type for row in rows} != columns:
            raise ValueError("Legacy RAG columns differ; refusing to adopt")
        nullable = {"source_url", "page"}
        if any(row.attnotnull != (row.attname not in nullable) for row in rows):
            raise ValueError("Legacy RAG nullability differs; refusing to adopt")
        if inspector.get_pk_constraint(table, schema=schema)["constrained_columns"] != ["id"]:
            raise ValueError("Legacy RAG primary key differs; refusing to adopt")
    fks = inspector.get_foreign_keys("rag_chunks", schema=schema)
    if not any(
        fk["constrained_columns"] == ["document_id"]
        and fk["referred_table"] == "rag_documents"
        and fk["referred_schema"] in (None, schema)
        and fk["referred_columns"] == ["id"]
        for fk in fks
    ):
        raise ValueError("Legacy RAG foreign key differs; refusing to adopt")


def upgrade():
    connection = op.get_bind()
    settings = op.get_context().config.attributes["settings"]
    schema = connection.scalar(sa.text("SELECT current_schema()"))
    inspector = sa.inspect(connection)
    existing = any(
        inspector.has_table(table, schema=schema)
        for table in ("rag_index_config", "rag_documents", "rag_chunks", "rag_schema_migrations")
    )
    if existing:
        _adopt_legacy(connection, settings, schema)
        return
    op.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public")
    op.create_table(
        "rag_index_config",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("signature", sa.String(500), nullable=False),
        sa.CheckConstraint("id = 1"),
    )
    op.create_table(
        "rag_documents",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("title", sa.String(300), nullable=False),
        sa.Column("source_url", sa.Text()),
        sa.Column("chunk_count", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.String(40), nullable=False),
    )
    op.create_table(
        "rag_chunks",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("document_id", sa.String(36), sa.ForeignKey("rag_documents.id"), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("page", sa.Integer()),
        sa.Column("embedding", Vector(settings.embedding_dimensions), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
    )
    op.create_index("ix_rag_chunks_document_id", "rag_chunks", ["document_id"])
    connection.execute(
        sa.text("INSERT INTO rag_index_config(id, signature) VALUES (1, :signature)"),
        {"signature": settings.embedding_signature},
    )


def downgrade():
    raise ValueError("Initial RAG baseline is irreversible to preserve indexed data")
