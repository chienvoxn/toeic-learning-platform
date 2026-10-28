"""Start the real ASGI server and check local HTTP without making any Gemini calls."""

import json
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen

from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parents[1]


def main():
    values = dotenv_values(ROOT / ".env")
    key = values.get("AI_SERVICE_KEY")
    if not key:
        raise SystemExit("Run scripts/init_env.py first.")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
    process = subprocess.Popen(
        [
            sys.executable,
            "-m",
            "uvicorn",
            "app.main:app",
            "--host",
            "127.0.0.1",
            "--port",
            str(port),
            "--no-access-log",
        ],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=flags,
    )
    base = f"http://127.0.0.1:{port}"
    try:
        deadline = time.monotonic() + 20
        while True:
            try:
                with urlopen(f"{base}/health", timeout=2) as response:
                    assert json.load(response)["status"] == "ok"
                break
            except URLError:
                if process.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError(
                        "Server did not start. Check .env and database settings."
                    ) from None
                time.sleep(0.2)
        request = Request(
            f"{base}/internal/v1/tutor/ask",
            data=json.dumps({"question": "Present perfect?", "allowedDocumentIds": []}).encode(),
            headers={"Content-Type": "application/json", "X-API-Key": key},
        )
        with urlopen(request, timeout=5) as response:
            result = json.load(response)
            assert result["grounded"] is False and result["citations"] == []
        with urlopen(f"{base}/openapi.json", timeout=5) as response:
            assert "/internal/v1/tutor/ask" in json.load(response)["paths"]
        print("HTTP smoke passed: startup, health, OpenAPI, authenticated no-source fallback.")
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


if __name__ == "__main__":
    main()
