"""Luồng quản trị knowledge: nạp, xem và gỡ index học liệu."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import Response
from langchain_core.documents import Document

from app.knowledge.loaders import UnsupportedDocument, load_document
from app.knowledge.service import KnowledgeService
from app.schemas import DocumentInfo, DocumentInput
from app.shared.security import require_admin_key

router = APIRouter(
    prefix="/internal/v1/documents",
    tags=["Knowledge ingestion"],
    dependencies=[Depends(require_admin_key)],
)


def get_knowledge(request: Request) -> KnowledgeService:
    return request.app.state.knowledge


Knowledge = Annotated[KnowledgeService, Depends(get_knowledge)]


@router.put("", response_model=DocumentInfo)
def upsert_document(body: DocumentInput, service: Knowledge):
    return service.ingest(
        body.document_id,
        body.title,
        [Document(page_content=body.text)],
        str(body.source_url) if body.source_url else None,
    )


@router.post("/upload", response_model=DocumentInfo)
def upload_document(
    service: Knowledge,
    file: Annotated[UploadFile, File()],
    document_id: Annotated[UUID, Form(alias="documentId")],
    title: Annotated[str, Form(min_length=1, max_length=300)],
):
    payload = file.file.read(service.settings.max_upload_bytes + 1)
    if len(payload) > service.settings.max_upload_bytes:
        raise HTTPException(413, "File exceeds MAX_UPLOAD_BYTES")
    try:
        pages = load_document(file.filename or "", payload, service.settings)
    except UnsupportedDocument as exc:
        raise HTTPException(415, str(exc)) from exc
    return service.ingest(document_id, title, pages, None)


@router.get("", response_model=list[DocumentInfo])
def list_documents(
    service: Knowledge, limit: int = Query(50, ge=1, le=100), offset: int = Query(0, ge=0)
):
    return service.list_documents(limit, offset)


@router.delete("/{document_id}", status_code=204)
def delete_document(document_id: UUID, service: Knowledge):
    if not service.delete_document(document_id):
        raise HTTPException(404, "Document not found")
    return Response(status_code=204)
