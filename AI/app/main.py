"""Composition root: lifecycle, HTTP concerns and two business routers."""

import logging
import time
from contextlib import asynccontextmanager
from uuid import UUID, uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings, get_settings
from app.infrastructure.vector_store import VectorStore
from app.knowledge.errors import DocumentRejected
from app.knowledge.router import router as knowledge_router
from app.knowledge.service import KnowledgeService
from app.providers import GeminiProvider, InvalidGeneration, ProviderUnavailable
from app.retrieval.service import RetrievalService
from app.shared.contracts import Provider
from app.tutoring.router import router as tutor_router
from app.tutoring.service import TutorService

logger = logging.getLogger("toeic_ai")


def create_app(settings: Settings | None = None, provider: Provider | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        options = settings or get_settings()
        store = VectorStore(options)
        application.state.settings = options
        adapter = provider or GeminiProvider(options)
        application.state.store = store
        application.state.knowledge = KnowledgeService(options, store, adapter)
        application.state.retrieval = RetrievalService(options, store, adapter)
        application.state.tutor = TutorService(options, application.state.retrieval, adapter)
        try:
            yield
        finally:
            store.close()

    application = FastAPI(
        title="TOEIC AI Service",
        version="0.2.0",
        lifespan=lifespan,
        description="Internal API. Spring Boot supplies authorized scope and conversation history.",
    )

    application.include_router(knowledge_router)
    application.include_router(tutor_router)

    @application.middleware("http")
    async def trace_requests(request: Request, call_next):
        try:
            trace_id = str(UUID(request.headers.get("X-Request-ID", "")))
        except ValueError:
            trace_id = str(uuid4())
        request.state.trace_id = trace_id
        started = time.monotonic()
        # Do not log request/response bodies, keys, SQL parameters, or provider exceptions.
        response = await call_next(request)
        response.headers["X-Request-ID"] = trace_id
        logger.info(
            "request trace_id=%s method=%s status=%s latency_ms=%s",
            trace_id,
            request.method,
            response.status_code,
            round((time.monotonic() - started) * 1000),
        )
        return response

    @application.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        # FastAPI's default error includes rejected input, which can contain private prompts.
        return JSONResponse(
            status_code=422,
            content={
                "error": {
                    "code": "VALIDATION_ERROR",
                    "message": "Invalid request fields",
                    "fields": [
                        {"location": list(error["loc"]), "type": error["type"]}
                        for error in exc.errors()
                    ],
                },
                "traceId": request.state.trace_id,
            },
        )

    def register_error(exception_type, status: int, code: str, message: str):
        async def handler(request: Request, exc: Exception):
            return JSONResponse(
                status_code=status,
                content={
                    "error": {"code": code, "message": message},
                    "traceId": request.state.trace_id,
                },
            )

        application.add_exception_handler(exception_type, handler)

    register_error(
        ProviderUnavailable, 503, "AI_PROVIDER_UNAVAILABLE", "AI provider unavailable. Retry later."
    )
    register_error(
        InvalidGeneration,
        502,
        "AI_INVALID_RESPONSE",
        "AI returned invalid output or unverified citations. Retry later.",
    )
    register_error(SQLAlchemyError, 503, "AI_INDEX_UNAVAILABLE", "Knowledge index unavailable.")

    @application.exception_handler(DocumentRejected)
    async def invalid_document(request: Request, exc: DocumentRejected):
        return JSONResponse(
            status_code=422,
            content={
                "error": {"code": "DOCUMENT_REJECTED", "message": str(exc)},
                "traceId": request.state.trace_id,
            },
        )

    @application.get("/health", tags=["Health"])
    def health():
        return {"status": "ok", "service": "toeic-ai"}

    @application.get("/ready", tags=["Health"])
    def ready(request: Request):
        request.app.state.store.ping()
        configured = bool(request.app.state.settings.google_api_key.get_secret_value())
        return JSONResponse(
            status_code=200 if configured else 503,
            content={
                "status": "ready" if configured else "missing_google_api_key",
                "index": "ok",
                "storage": "pgvector" if request.app.state.store.postgres else "test",
                "providerConfigured": configured,
                "providerConnectivityChecked": False,
            },
        )

    return application


app = create_app()
