"""Cấu hình Alembic và kiểm tra index; không còn migration runner tự viết."""

import sys
from pathlib import Path

from alembic.config import Config
from alembic.migration import MigrationContext
from alembic.script import ScriptDirectory
from sqlalchemy import inspect, text

from app.config import Settings

MIGRATIONS = Path(__file__).resolve().parents[2] / "migrations"
VERSION_TABLE = "rag_alembic_version"


def alembic_config(settings: Settings | None = None) -> Config:
    directory = (
        MIGRATIONS if MIGRATIONS.is_dir() else (Path(sys.prefix) / "share/toeic-ai/migrations")
    )
    if not (directory / "env.py").is_file():
        raise ValueError("Alembic migration files are missing from the installation")
    config = Config()
    config.set_main_option("script_location", str(directory).replace("%", "%%"))
    if settings is not None:
        # Never interpolate a credential-bearing URL through alembic.ini/logging.
        config.attributes["settings"] = settings
    return config


def check_current_revision(connection):
    schema = connection.scalar(text("SELECT current_schema()"))
    context = MigrationContext.configure(
        connection,
        opts={
            "version_table": VERSION_TABLE,
            "version_table_schema": schema,
        },
    )
    expected = set(ScriptDirectory.from_config(alembic_config()).get_heads())
    if not expected or set(context.get_current_heads()) != expected:
        raise ValueError("RAG schema is not current; run python -m alembic upgrade head")


def validate_index_configuration(connection, settings: Settings):
    schema = connection.scalar(text("SELECT current_schema()"))
    inspector = inspect(connection)
    if not all(
        inspector.has_table(name, schema=schema)
        for name in ("rag_index_config", "rag_documents", "rag_chunks")
    ):
        raise ValueError("RAG index schema is incomplete")
    signature = connection.scalar(text("SELECT signature FROM rag_index_config WHERE id=1"))
    if signature != settings.embedding_signature:
        raise ValueError("Embedding/chunk configuration differs; use a new index")
    dimension = connection.scalar(
        text("""
        SELECT format_type(a.atttypid, a.atttypmod)
        FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = :schema AND c.relname = 'rag_chunks' AND a.attname = 'embedding'
    """),
        {"schema": schema},
    )
    if dimension != f"vector({settings.embedding_dimensions})":
        raise ValueError("Existing vector column dimension differs; use a new index")
