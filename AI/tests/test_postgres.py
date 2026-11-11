import hashlib
import os
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import UUID, uuid4

import pytest
from alembic import command
from fastapi.testclient import TestClient
from langchain_core.documents import Document
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import make_url

from app.infrastructure.migrations import alembic_config
from app.infrastructure.vector_store import IndexConfigurationError, VectorStore
from app.knowledge.service import KnowledgeService
from app.main import create_app

pytestmark = pytest.mark.skipif(
    not os.getenv("TEST_POSTGRES_URL"), reason="TEST_POSTGRES_URL not set"
)


@pytest.fixture
def pg_options(options):
    # Dedicated TEST_POSTGRES_URL only; each test gets an isolated schema.
    engine = create_engine(os.environ["TEST_POSTGRES_URL"], hide_parameters=True)
    schema = "rag_test_" + uuid4().hex
    with engine.begin() as connection:
        connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public"))
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    url = make_url(os.environ["TEST_POSTGRES_URL"]).update_query_dict(
        {"options": f"-csearch_path={schema},public"}
    )
    configured = options.model_copy(
        update={
            "database_url": url.render_as_string(hide_password=False),
            "allow_sqlite_for_tests": False,
        }
    )
    try:
        yield configured
    finally:
        with engine.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        engine.dispose()


def test_pgvector_filters_scope_before_ranking(pg_options, provider):
    settings = pg_options
    command.upgrade(alembic_config(settings), "head")
    store = VectorStore(settings)
    service = KnowledgeService(settings, store, provider)
    public_id, private_id = uuid4(), uuid4()
    try:
        service.ingest(public_id, "Grammar", [Document(page_content="Present perfect")], None)
        service.ingest(private_id, "Private", [Document(page_content="PRIVATE_SECRET")], None)
        assert store.search(provider.vector("PRIVATE_SECRET"), [public_id], 5) == []
        sources = store.search(provider.vector("Grammar"), [public_id], 5)
        assert len(sources) == 1 and sources[0].document_id == public_id
        assert sources[0].similarity == pytest.approx(1.0)
        assert store.search(provider.vector("Grammar"), [], 5) == []
        assert store.delete_document(public_id)
        assert not store.has_documents([public_id])
    finally:
        store.delete_document(public_id)
        store.delete_document(private_id)
        store.close()


def test_pgvector_two_workflows_over_http(pg_options, provider):
    command.upgrade(alembic_config(pg_options), "head")
    document_id = str(uuid4())
    with TestClient(create_app(pg_options, provider)) as client:
        admin = {"X-API-Key": pg_options.ai_admin_key.get_secret_value()}
        service = {"X-API-Key": pg_options.ai_service_key.get_secret_value()}
        assert (
            client.put(
                "/internal/v1/documents",
                headers=admin,
                json={
                    "documentId": document_id,
                    "title": "Grammar",
                    "text": "Present perfect rule",
                },
            ).status_code
            == 200
        )
        response = client.post(
            "/internal/v1/tutor/ask",
            headers=service,
            json={"question": "Explain", "allowedDocumentIds": [document_id]},
        )
        assert response.status_code == 200, response.text
        assert response.json()["grounded"]
        assert response.json()["citations"][0]["documentId"] == document_id
        assert (
            client.delete(f"/internal/v1/documents/{document_id}", headers=admin).status_code == 204
        )
        response = client.post(
            "/internal/v1/tutor/ask",
            headers=service,
            json={"question": "Explain", "allowedDocumentIds": [document_id]},
        )
        assert not response.json()["grounded"]


def test_migration_is_repeatable_and_rejects_vector_space_change(pg_options):
    command.upgrade(alembic_config(pg_options), "head")
    command.upgrade(alembic_config(pg_options), "head")
    with pytest.raises(ValueError, match="configuration differs"):
        command.upgrade(
            alembic_config(pg_options.model_copy(update={"chunk_size": pg_options.chunk_size + 1})),
            "head",
        )
    store = VectorStore(pg_options)
    try:
        with store.engine.connect() as connection:
            assert connection.scalar(text("SELECT version_num FROM rag_alembic_version")) == (
                "0001_rag_index"
            )
    finally:
        store.close()


def test_pgvector_concurrent_replace_commits_complete_document(pg_options, provider):
    command.upgrade(alembic_config(pg_options), "head")
    store = VectorStore(pg_options)
    service = KnowledgeService(pg_options, store, provider)
    identifier = uuid4()
    try:

        def replace(index):
            return service.ingest(
                identifier,
                f"Version {index}",
                [Document(page_content=f"Grammar {index}")],
                None,
            )

        with ThreadPoolExecutor(max_workers=4) as executor:
            results = list(executor.map(replace, range(8)))
        assert len(results) == 8
        sources = store.search(provider.vector("Grammar"), [identifier], 10)
        assert len(sources) == 1
        assert sources[0].title.split()[-1] == sources[0].excerpt.split()[-1]
    finally:
        store.close()


def create_legacy_index(settings):
    """Reproduce the already-applied SQL runner, never modify its historical file."""
    source = Path(__file__).resolve().parents[1] / "migrations/001_pgvector_index.sql"
    sql = source.read_text(encoding="utf-8").replace(
        "__DIMENSIONS__", str(settings.embedding_dimensions)
    )
    engine = create_engine(settings.database_url, hide_parameters=True)
    document_id, chunk_id = str(uuid4()), str(uuid4())
    try:
        with engine.begin() as connection:
            connection.exec_driver_sql(sql)
            connection.execute(
                text("""
                CREATE TABLE rag_schema_migrations (
                    version VARCHAR(100) PRIMARY KEY, checksum VARCHAR(64) NOT NULL,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
            """)
            )
            connection.execute(
                text(
                    "INSERT INTO rag_schema_migrations(version,checksum) "
                    "VALUES (:version,:checksum)"
                ),
                {"version": source.name, "checksum": hashlib.sha256(sql.encode()).hexdigest()},
            )
            connection.execute(
                text("INSERT INTO rag_index_config(id,signature) VALUES (1,:signature)"),
                {"signature": settings.embedding_signature},
            )
            connection.execute(
                text("""
                INSERT INTO rag_documents(id,title,chunk_count,updated_at)
                VALUES (:id,'Original grammar',1,'2026-11-08T00:00:00+00:00')
            """),
                {"id": document_id},
            )
            vector = "[" + ",".join(["1"] + ["0"] * (settings.embedding_dimensions - 1)) + "]"
            connection.execute(
                text("""
                INSERT INTO rag_chunks(id,document_id,content,embedding,position)
                VALUES (:id,:document_id,'Original rule',CAST(:embedding AS vector),0)
            """),
                {"id": chunk_id, "document_id": document_id, "embedding": vector},
            )
    finally:
        engine.dispose()
    return document_id, chunk_id


def test_alembic_adopts_legacy_index_without_reindexing(pg_options, provider):
    document_id, chunk_id = create_legacy_index(pg_options)
    command.upgrade(alembic_config(pg_options), "head")
    command.upgrade(alembic_config(pg_options), "head")
    store = VectorStore(pg_options)
    try:
        sources = store.search(provider.vector("Grammar"), [UUID(document_id)], 5)
        assert len(sources) == 1
        assert str(sources[0].chunk_id) == chunk_id
        assert sources[0].excerpt == "Original rule"
        assert sources[0].title == "Original grammar"
        with store.engine.connect() as connection:
            assert connection.scalar(text("SELECT count(*) FROM rag_schema_migrations")) == 1
            assert connection.scalar(text("SELECT version_num FROM rag_alembic_version")) == (
                "0001_rag_index"
            )
    finally:
        store.close()


@pytest.mark.parametrize("tamper", ["checksum", "columns"])
def test_alembic_refuses_unverified_legacy_schema(pg_options, tamper):
    create_legacy_index(pg_options)
    engine = create_engine(pg_options.database_url, hide_parameters=True)
    try:
        with engine.begin() as connection:
            if tamper == "checksum":
                connection.execute(text("UPDATE rag_schema_migrations SET checksum='invalid'"))
            else:
                connection.execute(text("ALTER TABLE rag_documents ADD COLUMN unexpected TEXT"))
        with pytest.raises(ValueError, match="refusing to adopt"):
            command.upgrade(alembic_config(pg_options), "head")
        with engine.connect() as connection:
            assert connection.scalar(text("SELECT count(*) FROM rag_documents")) == 1
            assert connection.scalar(text("SELECT count(*) FROM rag_chunks")) == 1
            schema = connection.scalar(text("SELECT current_schema()"))
            assert not inspect(connection).has_table("rag_alembic_version", schema=schema)
    finally:
        engine.dispose()


def test_runtime_refuses_schema_without_alembic_revision(pg_options):
    create_legacy_index(pg_options)
    with pytest.raises(IndexConfigurationError, match="alembic upgrade head"):
        VectorStore(pg_options)


def test_baseline_downgrade_cannot_delete_existing_vectors(pg_options):
    create_legacy_index(pg_options)
    command.upgrade(alembic_config(pg_options), "head")
    with pytest.raises(ValueError, match="irreversible"):
        command.downgrade(alembic_config(pg_options), "base")
    store = VectorStore(pg_options)
    try:
        with store.engine.connect() as connection:
            assert connection.scalar(text("SELECT count(*) FROM rag_chunks")) == 1
    finally:
        store.close()
