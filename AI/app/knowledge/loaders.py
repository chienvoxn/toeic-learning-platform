"""Đọc file học liệu; không tải URL và không thực thi nội dung tài liệu."""

import io

from langchain_core.documents import Document
from pypdf import PdfReader

from app.config import Settings
from app.knowledge.errors import DocumentRejected


class UnsupportedDocument(ValueError):
    pass


def load_document(filename: str, payload: bytes, settings: Settings) -> list[Document]:
    suffix = filename.lower()
    if suffix.endswith((".txt", ".md")):
        try:
            content = payload.decode("utf-8-sig")
        except UnicodeDecodeError as exc:
            raise DocumentRejected("Text files must use UTF-8 encoding") from exc
        if len(content) > settings.max_document_characters:
            raise DocumentRejected("Document exceeds MAX_DOCUMENT_CHARACTERS")
        return [Document(page_content=content)]
    if not suffix.endswith(".pdf"):
        raise UnsupportedDocument("Only PDF, TXT and Markdown files are supported")
    try:
        reader = PdfReader(io.BytesIO(payload))
        if reader.is_encrypted or len(reader.pages) > 300:
            raise DocumentRejected("PDF must be unencrypted and at most 300 pages")
        pages, characters = [], 0
        for index, page in enumerate(reader.pages):
            content = page.extract_text() or ""
            characters += len(content)
            if characters > settings.max_document_characters:
                raise DocumentRejected("Document exceeds MAX_DOCUMENT_CHARACTERS")
            pages.append(Document(page_content=content, metadata={"page": index + 1}))
        return pages
    except DocumentRejected:
        raise
    except Exception as exc:
        raise DocumentRejected("Cannot read PDF") from exc
