import json

import pytest
from conftest import DOC_ID, PRIVATE_ID

from app.schemas import NO_SOURCE_WARNING

ASK = "/internal/v1/tutor/ask"
DOCUMENTS = "/internal/v1/documents"


def test_service_and_admin_credentials_are_separate(client, service_headers, admin_headers):
    body = {"question": "hello", "allowedDocumentIds": []}
    assert client.post(ASK, json=body).status_code == 401
    assert client.post(ASK, json=body, headers=admin_headers).status_code == 401
    assert client.get(DOCUMENTS, headers=service_headers).status_code == 401
    assert client.post(ASK, json=body, headers=service_headers).status_code == 200


def test_scope_required_and_empty_scope_never_calls_provider(client, service_headers, provider):
    assert client.post(ASK, headers=service_headers, json={"question": "hello"}).status_code == 422
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "hello",
            "allowedDocumentIds": [],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == body["warning"] == NO_SOURCE_WARNING
    assert body["grounded"] is False and body["citations"] == []
    assert provider.queries == provider.prompts == []


def test_acl_applies_before_ranking_and_prompt(
    client, indexed, service_headers, admin_headers, provider
):
    assert (
        client.put(
            DOCUMENTS,
            headers=admin_headers,
            json={
                "documentId": PRIVATE_ID,
                "title": "Confidential",
                "text": "PRIVATE_SECRET text",
            },
        ).status_code
        == 200
    )
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "PRIVATE_SECRET",
            "allowedDocumentIds": [DOC_ID],
        },
    )
    assert response.json()["grounded"] is False
    assert provider.prompts == []
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "Explain present perfect",
            "allowedDocumentIds": [DOC_ID],
        },
    )
    assert response.status_code == 200
    assert {source["documentId"] for source in response.json()["citations"]} == {DOC_ID}
    payload = json.loads(provider.prompts[-1][1])
    assert "PRIVATE_SECRET" not in json.dumps(payload["evidence"])


@pytest.mark.parametrize("mode", ["ask", "grammar", "vocabulary", "explain_answer", "reading"])
def test_tutor_modes_return_verified_citations(client, indexed, service_headers, provider, mode):
    trace = "2dc62337-e87d-4b44-938e-b8bcfdab1120"
    response = client.post(
        ASK,
        headers={**service_headers, "X-Request-ID": trace},
        json={
            "question": "Explain this rule",
            "mode": mode,
            "allowedDocumentIds": [indexed],
            "history": [{"role": "user", "content": "Present perfect"}],
        },
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["grounded"] and body["warning"] is None
    assert body["citations"][0]["title"] == "Present perfect"
    assert body["citations"][0]["sourceUrl"] == "https://example.com/grammar"
    assert body["usage"]["totalTokens"] == 130
    assert body["usage"]["estimatedCost"] is None
    assert body["traceId"] == response.headers["X-Request-ID"] == trace
    assert "Present perfect" in provider.queries[-1]


def test_practice_returns_drafts_with_per_question_sources(client, indexed, service_headers):
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "Generate practice",
            "mode": "practice",
            "exerciseCount": 2,
            "allowedDocumentIds": [indexed],
        },
    )
    assert response.status_code == 200, response.text
    exercises = response.json()["exercises"]
    assert len(exercises) == 2
    assert all(item["aiGenerated"] and item["citations"] for item in exercises)
    assert all(len(item["choices"]) == 4 for item in exercises)


def test_forged_citation_is_rejected(client, indexed, service_headers, provider):
    provider.forge_citation = True
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "Explain",
            "allowedDocumentIds": [indexed],
        },
    )
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "AI_INVALID_RESPONSE"
    assert PRIVATE_ID not in response.text


def test_low_similarity_never_calls_chat_model(client, indexed, service_headers, provider):
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "unrelated",
            "allowedDocumentIds": [indexed],
        },
    )
    assert response.status_code == 200
    assert response.json()["warning"] == NO_SOURCE_WARNING
    assert provider.prompts == []


def test_model_insufficient_evidence_returns_no_sources(client, indexed, service_headers, provider):
    provider.insufficient = True
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "Explain",
            "allowedDocumentIds": [indexed],
        },
    )
    body = response.json()
    assert body["warning"] == NO_SOURCE_WARNING and body["citations"] == []
    assert body["usage"]["totalTokens"] == 130


def test_provider_outage_is_sanitized(client, indexed, service_headers, provider):
    provider.fail_generation = True
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "Explain",
            "allowedDocumentIds": [indexed],
        },
    )
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "AI_PROVIDER_UNAVAILABLE"
    assert "secrets" not in response.text
    assert client.get("/health").status_code == 200


def test_failed_reindex_preserves_old_document(
    client, indexed, admin_headers, service_headers, provider
):
    provider.fail_embeddings = True
    response = client.put(
        DOCUMENTS,
        headers=admin_headers,
        json={
            "documentId": indexed,
            "title": "Replacement",
            "text": "Replacement text",
        },
    )
    assert response.status_code == 503
    provider.fail_embeddings = False
    response = client.post(
        "/internal/v1/search",
        headers=service_headers,
        json={
            "query": "present perfect",
            "allowedDocumentIds": [indexed],
        },
    )
    assert response.json()[0]["title"] == "Present perfect"


def test_upsert_is_idempotent_and_delete_removes_chunks(
    client, indexed, admin_headers, service_headers
):
    for _ in range(2):
        response = client.put(
            DOCUMENTS,
            headers=admin_headers,
            json={
                "documentId": indexed,
                "title": "Updated",
                "text": "Updated grammar text",
            },
        )
        assert response.status_code == 200
    docs = client.get(DOCUMENTS, headers=admin_headers).json()
    assert len(docs) == 1 and docs[0]["title"] == "Updated"
    assert client.delete(f"{DOCUMENTS}/{indexed}", headers=admin_headers).status_code == 204
    assert client.delete(f"{DOCUMENTS}/{indexed}", headers=admin_headers).status_code == 404
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "Updated",
            "allowedDocumentIds": [indexed],
        },
    )
    assert response.json()["warning"] == NO_SOURCE_WARNING


def test_upload_validation_and_utf8_text(client, admin_headers):
    response = client.post(
        f"{DOCUMENTS}/upload",
        headers=admin_headers,
        data={"documentId": DOC_ID, "title": "Học liệu"},
        files={"file": ("lesson.md", "Nội dung ngữ pháp".encode(), "text/plain")},
    )
    assert response.status_code == 200
    response = client.post(
        f"{DOCUMENTS}/upload",
        headers=admin_headers,
        data={"documentId": DOC_ID, "title": "Broken"},
        files={"file": ("lesson.pdf", b"not a pdf", "application/pdf")},
    )
    assert response.status_code == 422
    response = client.post(
        f"{DOCUMENTS}/upload",
        headers=admin_headers,
        data={"documentId": DOC_ID, "title": "Bad"},
        files={"file": ("run.exe", b"bad", "application/octet-stream")},
    )
    assert response.status_code == 415


def test_validation_does_not_echo_sensitive_input(client, service_headers):
    response = client.post(
        ASK,
        headers=service_headers,
        json={
            "question": "private_question_" * 400,
            "allowedDocumentIds": [],
        },
    )
    assert response.status_code == 422
    assert "private_question_" not in response.text


def test_readiness_does_not_claim_gemini_connectivity(client):
    response = client.get("/ready")
    assert response.status_code == 503
    assert response.json()["providerConnectivityChecked"] is False


def test_openapi_contract(client):
    schema = client.get("/openapi.json").json()
    assert ASK in schema["paths"]
    assert "allowedDocumentIds" in schema["components"]["schemas"]["AskRequest"]["required"]
