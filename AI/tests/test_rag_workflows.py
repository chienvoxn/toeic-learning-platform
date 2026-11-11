"""Business regression checks: indexing failure isolation and query scope boundaries."""

from uuid import UUID

import pytest
from alembic.script import ScriptDirectory
from conftest import DOC_ID, PRIVATE_ID
from langchain_core.documents import Document
from pydantic import ValidationError
from sqlalchemy import event
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings
from app.infrastructure.migrations import alembic_config
from app.infrastructure.vector_store import VectorStore
from app.knowledge.errors import DocumentRejected
from app.knowledge.service import KnowledgeService
from app.retrieval.service import RetrievalService
from app.schemas import SearchRequest


def test_alembic_graph_and_credentials_stay_out_of_ini(options):
    config = alembic_config(options)
    assert ScriptDirectory.from_config(config).get_heads() == ["0001_rag_index"]
    assert config.get_main_option("sqlalchemy.url") is None
    assert config.attributes["settings"] is options


def test_missing_migration_files_cannot_silently_succeed(options, monkeypatch, tmp_path):
    from app.infrastructure import migrations

    monkeypatch.setattr(migrations, "MIGRATIONS", tmp_path)
    with pytest.raises(ValueError, match="missing"):
        alembic_config(options)


def test_runtime_rejects_sqlite_without_explicit_test_opt_in(options):
    fields = options.model_dump()
    fields["allow_sqlite_for_tests"] = False
    with pytest.raises(ValidationError, match="requires PostgreSQL"):
        Settings(_env_file=None, **fields)


def test_database_credentials_are_not_in_settings_repr(options):
    fields = options.model_dump()
    fields["database_url"] = "postgresql+psycopg://user:private-password@localhost/index"
    configured = Settings(_env_file=None, **fields)
    assert "private-password" not in repr(configured)


def test_failed_database_write_rolls_back_entire_replacement(options, provider):
    store = VectorStore(options)
    knowledge = KnowledgeService(options, store, provider)
    identifier = UUID(DOC_ID)
    try:
        knowledge.ingest(identifier, "Original", [Document(page_content="Original rule")], None)

        def fail_insert(conn, cursor, statement, parameters, context, executemany):
            if statement.startswith("INSERT INTO rag_chunks"):
                raise SQLAlchemyError("simulated write failure")

        event.listen(store.engine, "before_cursor_execute", fail_insert)
        try:
            with pytest.raises(SQLAlchemyError):
                knowledge.ingest(
                    identifier, "Replacement", [Document(page_content="Changed rule")], None
                )
        finally:
            event.remove(store.engine, "before_cursor_execute", fail_insert)
        result = store.search(provider.vector("rule"), [identifier], 5)
        assert result[0].title == "Original" and result[0].excerpt == "Original rule"
    finally:
        store.close()


def test_retrieval_does_not_reuse_previous_users_scope(options, provider):
    store = VectorStore(options)
    knowledge = KnowledgeService(options, store, provider)
    retrieval = RetrievalService(options, store, provider)
    try:
        for identifier, title in ((DOC_ID, "Public"), (PRIVATE_ID, "Restricted")):
            knowledge.ingest(UUID(identifier), title, [Document(page_content="Grammar")], None)
        for identifier in (DOC_ID, PRIVATE_ID, DOC_ID):
            sources = retrieval.search(
                SearchRequest(query="Grammar", allowed_document_ids=[UUID(identifier)], top_k=1)
            )
            assert {str(source.document_id) for source in sources} == {identifier}
        previous_calls = len(provider.queries)
        assert retrieval.search(SearchRequest(query="Grammar", allowed_document_ids=[])) == []
        assert len(provider.queries) == previous_calls
    finally:
        store.close()


@pytest.mark.parametrize("title", ["   ", "x" * 301])
def test_invalid_document_title_does_not_call_embedding(options, provider, title):
    store = VectorStore(options)
    try:
        with pytest.raises(DocumentRejected):
            KnowledgeService(options, store, provider).ingest(
                UUID(DOC_ID), title, [Document(page_content="Grammar")], None
            )
        assert provider.embedded == []
    finally:
        store.close()


def test_oversized_upload_preserves_published_index(client, indexed, admin_headers, options):
    response = client.post(
        "/internal/v1/documents/upload",
        headers=admin_headers,
        data={"documentId": indexed, "title": "Replacement"},
        files={"file": ("large.txt", b"x" * (options.max_upload_bytes + 1), "text/plain")},
    )
    assert response.status_code == 413
    documents = client.get("/internal/v1/documents", headers=admin_headers).json()
    assert documents[0]["title"] == "Present perfect"


def test_upload_whitespace_title_returns_business_validation(client, admin_headers):
    response = client.post(
        "/internal/v1/documents/upload",
        headers=admin_headers,
        data={"documentId": DOC_ID, "title": "   "},
        files={"file": ("rule.txt", b"Grammar", "text/plain")},
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "DOCUMENT_REJECTED"
