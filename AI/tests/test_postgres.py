import os
from uuid import uuid4

import pytest
from langchain_core.documents import Document

from app.service import RagService
from app.store import VectorStore


@pytest.mark.skipif(not os.getenv("TEST_POSTGRES_URL"), reason="TEST_POSTGRES_URL not set")
def test_pgvector_filters_scope_before_ranking(options, provider):
    settings = options.model_copy(update={"database_url": os.environ["TEST_POSTGRES_URL"]})
    store = VectorStore(settings)
    service = RagService(settings, store, provider)
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
