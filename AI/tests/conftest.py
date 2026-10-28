import json

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.providers import Generation, ProviderUnavailable
from app.schemas import GeneratedAnswer, GeneratedExercise

SERVICE_KEY = "test-service-key-0000000000000000"
ADMIN_KEY = "test-admin-key-000000000000000000"
DOC_ID = "27bd260f-9558-4884-a9d5-d3058e112d49"
PRIVATE_ID = "ef71841f-9549-456d-a801-835836c1befd"


class FakeProvider:
    """Deterministic retrieval vectors; never calls Gemini or requires a real API key."""

    def __init__(self):
        self.embedded = []
        self.queries = []
        self.prompts = []
        self.fail_embeddings = False
        self.fail_generation = False
        self.forge_citation = False
        self.insufficient = False

    def vector(self, text):
        index = 1 if "PRIVATE_SECRET" in text else 2 if "unrelated" in text else 0
        return [1.0 if i == index else 0.0 for i in range(128)]

    def embed_documents(self, texts):
        if self.fail_embeddings:
            raise ProviderUnavailable("private provider exception with secrets")
        self.embedded.extend(texts)
        return [self.vector(text) for text in texts]

    def embed_query(self, query):
        self.queries.append(query)
        return self.vector(query)

    def generate(self, system_prompt, user_prompt):
        if self.fail_generation:
            raise ProviderUnavailable("private provider exception with secrets")
        self.prompts.append((system_prompt, user_prompt))
        payload = json.loads(user_prompt)
        source = payload["evidence"][0]["chunk_id"]
        ids = [PRIVATE_ID] if self.forge_citation else [source]
        exercises = []
        if payload["mode"] == "practice":
            exercises = [
                GeneratedExercise(
                    question=f"Draft {i}: She ___ worked here for three years.",
                    choices=["has", "have", "having", "had been"],
                    correct_index=0,
                    explanation="She takes has in the present perfect.",
                    cited_chunk_ids=ids,
                )
                for i in range(payload["exercise_count"])
            ]
        return Generation(
            GeneratedAnswer(
                answer="Thì hiện tại hoàn thành dùng have/has + past participle.",
                sufficient_evidence=not self.insufficient,
                cited_chunk_ids=ids,
                exercises=exercises,
            ),
            input_tokens=100,
            output_tokens=30,
            total_tokens=130,
        )


@pytest.fixture
def options(tmp_path):
    return Settings(
        _env_file=None,
        ai_service_key=SERVICE_KEY,
        ai_admin_key=ADMIN_KEY,
        google_api_key="",
        embedding_dimensions=128,
        database_url=f"sqlite:///{(tmp_path / 'index.db').as_posix()}",
        min_similarity=0.5,
    )


@pytest.fixture
def provider():
    return FakeProvider()


@pytest.fixture
def client(options, provider):
    with TestClient(create_app(options, provider)) as test_client:
        yield test_client


@pytest.fixture
def service_headers():
    return {"X-API-Key": SERVICE_KEY}


@pytest.fixture
def admin_headers():
    return {"X-API-Key": ADMIN_KEY}


@pytest.fixture
def indexed(client, admin_headers):
    result = client.put(
        "/internal/v1/documents",
        headers=admin_headers,
        json={
            "documentId": DOC_ID,
            "title": "Present perfect",
            "text": (
                "The present perfect uses have/has + past participle. For expresses a duration."
            ),
            "sourceUrl": "https://example.com/grammar",
        },
    )
    assert result.status_code == 200, result.text
    return DOC_ID
