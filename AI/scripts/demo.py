"""Local backend demo; loads internal keys from AI/.env, without printing them."""

import argparse
import json
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="Call the local TOEIC AI API")
    parser.add_argument("--base-url", default="http://127.0.0.1:8001")
    parser.add_argument("--ingest-sample", action="store_true")
    parser.add_argument(
        "--mode",
        choices=["ask", "grammar", "vocabulary", "explain_answer", "reading", "practice"],
        default="grammar",
    )
    parser.add_argument("--question", default="Giải thích cách dùng for và since.")
    args = parser.parse_args()
    # Prevent accidentally forwarding local internal credentials to a remote URL.
    if args.base_url not in {"http://127.0.0.1:8001", "http://localhost:8001"}:
        parser.error("This demo sends internal keys and only supports localhost:8001")
    values = dotenv_values(ROOT / ".env")

    def call(path, method, body, key_name):
        key = values.get(key_name)
        if not key:
            parser.error(f"Missing {key_name}. Run scripts/init_env.py first.")
        request = Request(
            f"{args.base_url}{path}",
            method=method,
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json", "X-API-Key": key},
        )
        with urlopen(request, timeout=120) as response:
            return json.load(response)

    try:
        if args.ingest_sample:
            sample = json.loads((ROOT / "examples/knowledge.json").read_text(encoding="utf-8"))
            result = call("/internal/v1/documents", "PUT", sample, "AI_ADMIN_KEY")
            print(f"Indexed {result['chunkCount']} chunks.")
        question = json.loads((ROOT / "examples/ask.json").read_text(encoding="utf-8"))
        question.update(question=args.question, mode=args.mode)
        result = call("/internal/v1/tutor/ask", "POST", question, "AI_SERVICE_KEY")
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except HTTPError as exc:
        print(f"AI service returned HTTP {exc.code}: {exc.read().decode('utf-8')}", file=sys.stderr)
        return 1
    except URLError:
        print("Cannot connect. Start uvicorn on 127.0.0.1:8001 first.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
