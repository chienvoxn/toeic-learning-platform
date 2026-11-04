"""Nạp học liệu và kiểm tra RAG độc lập qua API local. --live dùng quota Gemini."""

import argparse
import json
from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="Call the real Gemini API")
    args = parser.parse_args()
    if not args.live:
        parser.error("Use --live to run this check with your configured Gemini API key")
    settings = Settings()
    if not settings.google_api_key.get_secret_value():
        parser.error("Set GOOGLE_API_KEY in AI/.env first; never paste it in chat")
    sample = json.loads((ROOT / "examples/knowledge.json").read_text(encoding="utf-8"))
    question = json.loads((ROOT / "examples/ask.json").read_text(encoding="utf-8"))
    admin = {"X-API-Key": settings.ai_admin_key.get_secret_value()}
    service = {"X-API-Key": settings.ai_service_key.get_secret_value()}
    with TestClient(create_app(settings)) as client:
        response = client.put("/internal/v1/documents", headers=admin, json=sample)
        if response.status_code != 200:
            trace = response.headers.get("X-Request-ID")
            print(f"Ingest failed: HTTP {response.status_code}, trace={trace}")
            return 1
        print(f"Indexed {response.json()['chunkCount']} chunks.")
        response = client.post(
            "/internal/v1/search",
            headers=service,
            json={"query": question["question"], "allowedDocumentIds": [sample["documentId"]]},
        )
        if response.status_code != 200 or not response.json():
            print(
                f"Retrieval failed or no sources: HTTP {response.status_code}. "
                "Check MIN_SIMILARITY."
            )
            return 1
        sources = response.json()
        print(f"Retrieved {len(sources)} chunks; best similarity={sources[0]['similarity']}.")
        response = client.post("/internal/v1/tutor/ask", headers=service, json=question)
        if response.status_code != 200:
            trace = response.headers.get("X-Request-ID")
            print(f"Ask failed: HTTP {response.status_code}, trace={trace}")
            return 1
        result = response.json()
        valid = (
            result["grounded"]
            and result["citations"]
            and all(c["documentId"] == sample["documentId"] for c in result["citations"])
            and result["usage"]["provider"] == "google"
        )
        if not valid:
            print("Answer did not pass the citation/provider check.")
            return 1
        print(json.dumps(result, ensure_ascii=False, indent=2))
        empty = client.post(
            "/internal/v1/tutor/ask",
            headers=service,
            json={"question": question["question"], "allowedDocumentIds": []},
        )
        if empty.status_code != 200 or empty.json()["grounded"] or empty.json()["citations"]:
            print("Empty-scope fallback failed.")
            return 1
        print("PASS: ingest, retrieval, answer with verified source IDs, empty-scope fallback.")
        print("This verifies the pipeline, not semantic correctness of every generated claim.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
