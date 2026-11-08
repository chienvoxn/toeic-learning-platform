"""Khóa nội bộ riêng cho luồng quản trị và luồng hỏi đáp."""

import secrets
from typing import Annotated

from fastapi import Header, HTTPException, Request


def _require(request: Request, key_name: str, supplied: str | None):
    expected = getattr(request.app.state.settings, key_name).get_secret_value()
    if supplied is None or not secrets.compare_digest(supplied.encode(), expected.encode()):
        raise HTTPException(401, "Invalid internal credentials")


def require_service_key(request: Request, x_api_key: Annotated[str | None, Header()] = None):
    _require(request, "ai_service_key", x_api_key)


def require_admin_key(request: Request, x_api_key: Annotated[str | None, Header()] = None):
    _require(request, "ai_admin_key", x_api_key)
