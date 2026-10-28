import io
import logging
import secrets
import time
from contextlib import asynccontextmanager
from typing import Annotated
from uuid import UUID, uuid4

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Query, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from langchain_core.documents import Document
from pypdf import PdfReader
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings, get_settings
from app.providers import GeminiProvider, InvalidGeneration, ProviderUnavailable
from app.schemas import (
    AskRequest,
    Citation,
    DocumentInfo,
    DocumentInput,
    SearchRequest,
    TutorResponse,
)
from app.service import DocumentRejected, Provider, RagService
from app.store import VectorStore

logger = logging.getLogger("toeic_ai")


def get_service(request: Request) -> RagService:
    return request.app.state.service


def require_service_key(request: Request, x_api_key: Annotated[str | None, Header()] = None):
    expected = request.app.state.settings.ai_service_key.get_secret_value()
    if x_api_key is None or not secrets.compare_digest(x_api_key.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="Invalid service credentials")


def require_admin_key(request: Request, x_api_key: Annotated[str | None, Header()] = None):
    expected = request.app.state.settings.ai_admin_key.get_secret_value()
    if x_api_key is None or not secrets.compare_digest(x_api_key.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="Invalid admin credentials")


Service = Annotated[RagService, Depends(get_service)]


def create_app(settings: Settings | None = None, provider: Provider | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        options = settings or get_settings()
        store = VectorStore(options)
        application.state.settings = options
        application.state.service = RagService(options, store, provider or GeminiProvider(options))
        try:
            yield
        finally:
            store.close()

    application = FastAPI(
        title="TOEIC AI Service",
        version="0.1.0",
        lifespan=lifespan,
        description="Internal API. Spring Boot supplies authorized scope and conversation history.",
    )

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
    def ready(request: Request, service: Service):
        service.store.ping()
        configured = bool(request.app.state.settings.google_api_key.get_secret_value())
        return JSONResponse(
            status_code=200 if configured else 503,
            content={
                "status": "ready" if configured else "missing_google_api_key",
                "index": "ok",
                "providerConfigured": configured,
                "providerConnectivityChecked": False,
            },
        )

    @application.post(
        "/internal/v1/tutor/ask",
        response_model=TutorResponse,
        dependencies=[Depends(require_service_key)],
        tags=["Tutor"],
    )
    def ask(body: AskRequest, request: Request, service: Service):
        return service.ask(body, request.state.trace_id)

    @application.post(
        "/internal/v1/search",
        response_model=list[Citation],
        dependencies=[Depends(require_service_key)],
        tags=["Retrieval"],
    )
    def search(body: SearchRequest, service: Service):
        return service.search(body)

    @application.put(
        "/internal/v1/documents",
        response_model=DocumentInfo,
        dependencies=[Depends(require_admin_key)],
        tags=["Knowledge administration"],
    )
    def upsert_document(body: DocumentInput, service: Service):
        return service.ingest(
            body.document_id,
            body.title,
            [Document(page_content=body.text)],
            str(body.source_url) if body.source_url else None,
        )

    @application.post(
        "/internal/v1/documents/upload",
        response_model=DocumentInfo,
        dependencies=[Depends(require_admin_key)],
        tags=["Knowledge administration"],
    )
    def upload_document(
        service: Service,
        file: Annotated[UploadFile, File()],
        document_id: Annotated[UUID, Form(alias="documentId")],
        title: Annotated[str, Form(min_length=1, max_length=300)],
    ):
        payload = file.file.read(service.settings.max_upload_bytes + 1)
        if len(payload) > service.settings.max_upload_bytes:
            raise HTTPException(413, "File exceeds MAX_UPLOAD_BYTES")
        filename = (file.filename or "").lower()
        pages = []
        if filename.endswith(".pdf"):
            try:
                reader = PdfReader(io.BytesIO(payload))
                if reader.is_encrypted or len(reader.pages) > 300:
                    raise DocumentRejected("PDF must be unencrypted and at most 300 pages")
                characters = 0
                for index, page in enumerate(reader.pages):
                    content = page.extract_text() or ""
                    characters += len(content)
                    if characters > service.settings.max_document_characters:
                        raise DocumentRejected("Document exceeds MAX_DOCUMENT_CHARACTERS")
                    pages.append(Document(page_content=content, metadata={"page": index + 1}))
            except DocumentRejected:
                raise
            except Exception as exc:
                raise DocumentRejected("Cannot read PDF") from exc
        elif filename.endswith((".txt", ".md")):
            try:
                pages = [Document(page_content=payload.decode("utf-8-sig"))]
            except UnicodeDecodeError as exc:
                raise DocumentRejected("Text files must use UTF-8 encoding") from exc
        else:
            raise HTTPException(415, "Only PDF, TXT and Markdown files are supported")
        title = title.strip()
        if not title:
            raise DocumentRejected("Document title must not be blank")
        return service.ingest(document_id, title, pages, None)

    @application.get(
        "/internal/v1/documents",
        response_model=list[DocumentInfo],
        dependencies=[Depends(require_admin_key)],
        tags=["Knowledge administration"],
    )
    def list_documents(
        service: Service, limit: int = Query(50, ge=1, le=100), offset: int = Query(0, ge=0)
    ):
        return service.store.list_documents(limit, offset)

    @application.delete(
        "/internal/v1/documents/{document_id}",
        status_code=204,
        dependencies=[Depends(require_admin_key)],
        tags=["Knowledge administration"],
    )
    def delete_document(document_id: UUID, service: Service):
        if not service.store.delete_document(document_id):
            raise HTTPException(404, "Document not found")
        return Response(status_code=204)

    return application


app = create_app()
