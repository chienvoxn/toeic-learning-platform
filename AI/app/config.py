from functools import lru_cache

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    google_api_key: SecretStr = SecretStr("")
    ai_service_key: SecretStr
    ai_admin_key: SecretStr
    gemini_chat_model: str = "gemini-3.5-flash-lite"
    gemini_embedding_model: str = "models/gemini-embedding-001"
    embedding_dimensions: int = Field(default=768, ge=128, le=3072)
    database_url: str = "sqlite:///./data/ai.db"
    chunk_size: int = Field(default=1200, ge=200, le=4000)
    chunk_overlap: int = Field(default=180, ge=0)
    retrieval_top_k: int = Field(default=5, ge=1, le=10)
    min_similarity: float = Field(default=0.55, ge=-1, le=1)
    provider_timeout_seconds: float = Field(default=45, ge=1, le=120)
    max_upload_bytes: int = Field(default=5 * 1024 * 1024, ge=1024, le=20 * 1024 * 1024)
    max_document_characters: int = Field(default=200_000, ge=1000, le=500_000)
    max_document_chunks: int = Field(default=300, ge=1, le=1000)

    @model_validator(mode="after")
    def validate_settings(self):
        if self.chunk_overlap >= self.chunk_size:
            raise ValueError("CHUNK_OVERLAP must be less than CHUNK_SIZE")
        keys = [self.ai_service_key.get_secret_value(), self.ai_admin_key.get_secret_value()]
        if any(len(key) < 24 for key in keys) or keys[0] == keys[1]:
            raise ValueError("AI_SERVICE_KEY and AI_ADMIN_KEY must be distinct, >=24 characters")
        return self

    @property
    def embedding_signature(self) -> str:
        # Refuse to mix vector spaces or chunk pipelines after changing configuration.
        return (
            f"{self.gemini_embedding_model}:{self.embedding_dimensions}:"
            f"{self.chunk_size}:{self.chunk_overlap}:v1"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
