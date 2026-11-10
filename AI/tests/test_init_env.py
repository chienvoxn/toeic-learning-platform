import pytest
from dotenv import dotenv_values
from sqlalchemy.engine import make_url

from scripts.init_env import initialize


@pytest.mark.parametrize("old_name", ["ai.db", "ai-openai.db", "ai-gemini.db"])
def test_migration_keeps_existing_keys_and_old_index(tmp_path, old_name):
    old_db = tmp_path / old_name
    old_db.write_bytes(b"existing index")
    original_key = "local-fixture-not-a-real-api-key"
    (tmp_path / ".env").write_text(
        f"GOOGLE_API_KEY={original_key}\nOPENAI_API_KEY=unused-local-fixture\n"
        f"DATABASE_URL=sqlite:///./data/{old_name}\n"
        "AI_SERVICE_KEY=\nAI_ADMIN_KEY=\nMIN_SIMILARITY=0.44\n",
        encoding="utf-8",
    )
    initialize(tmp_path)
    values = dotenv_values(tmp_path / ".env")
    assert values["GOOGLE_API_KEY"] == original_key
    assert values["OPENAI_API_KEY"] == "unused-local-fixture"
    url = make_url(values["DATABASE_URL"])
    assert url.drivername == "postgresql+psycopg"
    assert url.password == values["POSTGRES_PASSWORD"]
    assert values["MIN_SIMILARITY"] == "0.44"
    assert len(values["AI_SERVICE_KEY"]) >= 24
    assert values["AI_SERVICE_KEY"] != values["AI_ADMIN_KEY"]
    assert old_db.read_bytes() == b"existing index"
    initialize(tmp_path)
    assert dotenv_values(tmp_path / ".env") == values


def test_custom_database_and_valid_internal_keys_kept(tmp_path):
    (tmp_path / ".env").write_text(
        "DATABASE_URL=sqlite:///./data/custom.db\n"
        "AI_SERVICE_KEY=local-service-fixture-0000000000\n"
        "AI_ADMIN_KEY=local-admin-fixture-000000000000\n",
        encoding="utf-8",
    )
    initialize(tmp_path)
    values = dotenv_values(tmp_path / ".env")
    assert values["DATABASE_URL"] == "sqlite:///./data/custom.db"
    assert values["AI_SERVICE_KEY"] == "local-service-fixture-0000000000"
    assert values["AI_ADMIN_KEY"] == "local-admin-fixture-000000000000"


def test_fresh_config_generates_matching_postgres_password(tmp_path):
    (tmp_path / ".env.example").write_text(
        "GOOGLE_API_KEY=\nAI_SERVICE_KEY=\nAI_ADMIN_KEY=\nDATABASE_URL=\nPOSTGRES_PASSWORD=\n",
        encoding="utf-8",
    )
    initialize(tmp_path)
    values = dotenv_values(tmp_path / ".env")
    assert make_url(values["DATABASE_URL"]).password == values["POSTGRES_PASSWORD"]
    assert len(values["POSTGRES_PASSWORD"]) >= 24


def test_existing_postgres_url_and_password_are_not_rotated(tmp_path):
    (tmp_path / ".env").write_text(
        "DATABASE_URL=postgresql+psycopg://user:existing@localhost/custom\n"
        "POSTGRES_PASSWORD=existing\n",
        encoding="utf-8",
    )
    initialize(tmp_path)
    values = dotenv_values(tmp_path / ".env")
    assert values["POSTGRES_PASSWORD"] == "existing"
    assert make_url(values["DATABASE_URL"]).database == "custom"
