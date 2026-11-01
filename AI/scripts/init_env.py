"""Create a local .env with random internal keys. Never overwrite existing secrets."""

import secrets
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
target = ROOT / ".env"
if target.exists():
    print(".env already exists; kept unchanged.")
else:
    content = (ROOT / ".env.example").read_text(encoding="utf-8")
    content = content.replace("AI_SERVICE_KEY=\n", f"AI_SERVICE_KEY={secrets.token_urlsafe(32)}\n")
    content = content.replace("AI_ADMIN_KEY=\n", f"AI_ADMIN_KEY={secrets.token_urlsafe(32)}\n")
    with target.open("x", encoding="utf-8") as stream:
        stream.write(content)
    print("Created AI/.env with random internal keys. Set GOOGLE_API_KEY before using Gemini.")
