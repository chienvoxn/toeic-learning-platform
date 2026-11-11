"""Luồng hỏi đáp: chỉ nhận scope/lịch sử do backend đã xác thực cung cấp."""

from fastapi import APIRouter, Depends, Request

from app.schemas import AskRequest, Citation, SearchRequest, TutorResponse
from app.shared.security import require_service_key

router = APIRouter(
    prefix="/internal/v1",
    tags=["Tutor querying"],
    dependencies=[Depends(require_service_key)],
)


@router.post("/tutor/ask", response_model=TutorResponse)
def ask(body: AskRequest, request: Request):
    return request.app.state.tutor.ask(body, request.state.trace_id)


@router.post("/search", response_model=list[Citation])
def search(body: SearchRequest, request: Request):
    return request.app.state.retrieval.search(body)
