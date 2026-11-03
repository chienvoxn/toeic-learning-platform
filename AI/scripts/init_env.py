"""Chuẩn bị .env Gemini; giữ key hợp lệ và không xóa index của provider cũ."""

import io
import secrets
from pathlib import Path

from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parents[1]


def initialize(root: Path = ROOT):
    target = root / ".env"
    exists = target.exists()
    content = (target if exists else root / ".env.example").read_text(encoding="utf-8")
    values = dotenv_values(stream=io.StringIO(content))
    defaults = {
        "GOOGLE_API_KEY": "",
        "GEMINI_CHAT_MODEL": "gemini-3.5-flash-lite",
        "GEMINI_EMBEDDING_MODEL": "models/gemini-embedding-001",
        "AI_SERVICE_KEY": secrets.token_urlsafe(32),
        "AI_ADMIN_KEY": secrets.token_urlsafe(32),
    }
    lines = content.splitlines()
    for name, default in defaults.items():
        if name not in values:
            lines.append(f"{name}={default}")
        elif name in {"AI_SERVICE_KEY", "AI_ADMIN_KEY"} and (
            len(values[name] or "") < 24
            or name == "AI_ADMIN_KEY"
            and values[name] == values.get("AI_SERVICE_KEY")
        ):
            lines = [f"{name}={default}" if line.startswith(f"{name}=") else line for line in lines]
            values[name] = default
    # Chỉ chuyển đường dẫn SQLite mặc định cũ; không đổi DB tùy chỉnh/PostgreSQL.
    if values.get("DATABASE_URL") in {"sqlite:///./data/ai.db", "sqlite:///./data/ai-openai.db"}:
        lines = [
            "DATABASE_URL=sqlite:///./data/ai-gemini.db"
            if line.startswith("DATABASE_URL=")
            else line
            for line in lines
        ]
    updated = "\n".join(lines) + "\n"
    if not exists or updated != content:
        target.write_text(updated, encoding="utf-8")
    print("Prepared AI/.env; valid keys kept. Set GOOGLE_API_KEY locally before the live demo.")


if __name__ == "__main__":
    initialize()
