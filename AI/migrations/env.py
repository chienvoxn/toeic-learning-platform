"""Alembic online environment for the RAG index only."""

from alembic import context
from sqlalchemy import create_engine, text
from sqlalchemy.pool import NullPool

from app.config import Settings
from app.infrastructure.migrations import VERSION_TABLE, validate_index_configuration

if context.is_offline_mode():
    raise ValueError("RAG migrations require an online DB to validate legacy schema/configuration")

settings = context.config.attributes.get("settings") or Settings()
engine = create_engine(
    settings.database_url,
    poolclass=NullPool,
    hide_parameters=True,
    connect_args={"connect_timeout": 8},
)
try:
    if engine.dialect.name != "postgresql":
        raise ValueError("Index migrations require PostgreSQL + pgvector")
    with engine.begin() as connection:
        connection.execute(text("SELECT pg_advisory_xact_lock(7240193001)"))
        schema = connection.scalar(text("SELECT current_schema()"))
        context.configure(
            connection=connection,
            version_table=VERSION_TABLE,
            version_table_schema=schema,
            target_metadata=None,
        )
        context.config.attributes["settings"] = settings
        with context.begin_transaction():
            context.run_migrations()
        validate_index_configuration(connection, settings)
finally:
    engine.dispose()
