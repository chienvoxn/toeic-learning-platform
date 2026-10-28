import math
from uuid import UUID

import pytest
from conftest import DOC_ID
from langchain_core.documents import Document
from sqlalchemy import select
from sqlalchemy.dialects import postgresql

from app.service import DocumentRejected, RagService
from app.store import IndexConfigurationError, VectorStore, validate_vector


def test_index_persists_across_restart_and_rejects_different_vector_space(options, provider):
    store = VectorStore(options)
    service = RagService(options, store, provider)
    service.ingest(UUID(DOC_ID), "Grammar", [Document(page_content="Present perfect")], None)
    store.close()
    reopened = VectorStore(options)
    assert reopened.has_documents([UUID(DOC_ID)])
    reopened.close()
    changed = options.model_copy(update={"embedding_dimensions": 256})
    with pytest.raises(IndexConfigurationError):
        VectorStore(changed)


@pytest.mark.parametrize("vector", [[0.0] * 128, [1.0], [math.nan] * 128, [math.inf] * 128])
def test_invalid_vectors_are_rejected(vector):
    with pytest.raises(ValueError):
        validate_vector(vector, 128)


def test_postgres_column_supports_cosine_distance_operator(options):
    store = VectorStore(options)
    score = 1 - store.chunks.c.embedding.cosine_distance([1.0] * 128)
    query = select(score).where(store.chunks.c.document_id == DOC_ID)
    compiled = str(query.compile(dialect=postgresql.dialect()))
    assert "<=>" in compiled and "document_id" in compiled
    store.close()


def test_chunking_keeps_page_and_rejects_empty_text(options, provider):
    store = VectorStore(options)
    service = RagService(options, store, provider)
    service.ingest(
        UUID(DOC_ID),
        "Grammar",
        [
            Document(page_content="Present perfect " * 300, metadata={"page": 7}),
        ],
        None,
    )
    sources = store.search(provider.vector("grammar"), [UUID(DOC_ID)], 10)
    assert len(sources) > 1
    assert all(source.page == 7 for source in sources)
    assert all(len(source.excerpt) <= options.chunk_size for source in sources)
    with pytest.raises(DocumentRejected):
        service.ingest(UUID(DOC_ID), "Empty", [Document(page_content="   ")], None)
    store.close()
