# AI Tutor

Phần AI của ứng dụng học TOEIC, dùng FastAPI, LangChain và Gemini.
Service tìm nội dung trong học liệu rồi trả lời kèm nguồn tham khảo.
Hiện chạy riêng, chưa nối với Spring Boot và giao diện React.

Sau khi đã cài đặt và chạy migration, hằng ngày chỉ cần:

```powershell
docker compose up -d postgres
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Mở http://127.0.0.1:8001/docs để dùng API. Không cần chạy scripts hoặc tests mỗi lần mở app.

## Cấu trúc thư mục

```text
AI/
├── app/
│   ├── knowledge/       # Luồng quản trị: đọc file, chia đoạn, nạp/gỡ index
│   ├── tutoring/        # Luồng học: prompt, hỏi đáp, xác minh citation
│   ├── retrieval/       # LangChain retriever có scope riêng mỗi request
│   ├── infrastructure/  # pgvector repository và migration runner
│   ├── shared/          # Khóa nội bộ và provider contract
│   └── main.py          # Ghép dependency, lifecycle và lỗi HTTP
├── migrations/          # Alembic env và revisions; giữ SQL lịch sử đã áp dụng
├── tests/               # Kiểm thử
├── scripts/
│   ├── init_env.py      # Chuẩn bị cấu hình local khi thiết lập
│   └── verify_rag.py    # Một lệnh chạy thử toàn bộ RAG với Gemini
├── examples/            # Học liệu và request mẫu
├── .env.example         # Mẫu cấu hình
├── compose.yaml         # PostgreSQL có pgvector
├── alembic.ini          # Cấu hình migration, không chứa mật khẩu
├── pyproject.toml       # Thông tin package và thư viện
└── requirements.lock    # Phiên bản thư viện đã chốt
```

## Cài đặt và chạy

Dùng Python 3.11 trở lên. Các lệnh dưới đây chạy trong PowerShell, tại thư mục `AI`:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock
.\.venv\Scripts\python.exe -m pip install -e ".[dev]" --no-deps
.\.venv\Scripts\python.exe scripts/init_env.py
```

Điền `GOOGLE_API_KEY` vào `.env`. Có thể lấy key tại [Google AI Studio](https://aistudio.google.com/apikey).
`init_env.py` bổ sung cấu hình Gemini, tạo hai khóa nội bộ nếu thiếu/không hợp lệ và giữ
các key hợp lệ đã có. Nếu database là SQLite mặc định cũ `data/ai.db` hoặc `data/ai-openai.db`,
script chuyển cấu hình sang PostgreSQL + pgvector, không xóa file index cũ. Với PostgreSQL
hoặc database tùy chỉnh, cần chọn index mới và nạp lại tài liệu nếu chữ ký cấu hình khác.
Không đưa `.env` lên Git hoặc gửi các khóa này xuống frontend.

Runtime bắt buộc PostgreSQL + pgvector. `init_env.py` tạo mật khẩu PostgreSQL và URL tương ứng
cho database local mới; không tự đổi password của database đã tồn tại. Bật Docker Desktop rồi chạy:

```powershell
docker compose up -d postgres
.\.venv\Scripts\python.exe -m alembic upgrade head
```

Migration chạy trước API, không tạo schema trong request/startup. Database `toeic_ai` chỉ chứa
index tái tạo được, độc lập với các bảng nghiệp vụ do Spring Boot/Flyway quản lý.
Database đã chạy migration SQL cũ sẽ được kiểm tra checksum, schema và vector configuration
trước khi ghi nhận revision Alembic; giữ nguyên học liệu/vector và bảng lịch sử cũ.

```powershell
.\.venv\Scripts\python.exe -m alembic current
.\.venv\Scripts\python.exe -m alembic history
# Tạo revision mới rồi viết upgrade/downgrade trong file được sinh ra:
.\.venv\Scripts\python.exe -m alembic revision -m "describe schema change"
```

Hiện dùng revision viết tay, chưa cấu hình autogenerate. Không sửa SQL/revision đã áp dụng.
Revision baseline không hỗ trợ downgrade để tránh xóa index đang có dữ liệu.

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Swagger ở http://127.0.0.1:8001/docs. `/health` kiểm tra server;
`/ready` kiểm tra database và cấu hình key, chưa kiểm tra kết nối tới Gemini.

Để nạp học liệu mẫu và chạy thử trọn luồng, dùng duy nhất lệnh sau.
Không cần bật Spring Boot/frontend hoặc uvicorn trước:

```powershell
.\.venv\Scripts\python.exe -m scripts.verify_rag --live
```

Lệnh này gọi Gemini thật: nạp học liệu mẫu → tìm đoạn liên quan → hỏi đáp → đối chiếu ID
nguồn → kiểm tra fallback khi danh sách quyền rỗng. Cần `GOOGLE_API_KEY` hợp lệ và quota.
Đây là kiểm tra pipeline; vẫn cần đọc câu trả lời và nguồn để đánh giá độ đúng ngữ nghĩa.
Học liệu mẫu chỉ có phần hiện tại hoàn thành; cần nạp thêm tài liệu cho các chủ đề khác.

## Các chế độ hỗ trợ

| `mode` | Nội dung |
|---|---|
| `ask` | Hỏi đáp TOEIC |
| `grammar` | Giải thích ngữ pháp, đưa ví dụ |
| `vocabulary` | Giải nghĩa từ, từ loại và cách dùng |
| `explain_answer` | Giải thích câu hỏi và đáp án chuẩn gửi trong `context` |
| `reading` | Giải thích, dịch hoặc tóm tắt bài đọc/transcript trong `context` |
| `practice` | Tạo 1–5 câu luyện tập, mỗi câu có 4 lựa chọn và giải thích |

Các chế độ dùng chung luồng RAG:

```text
Câu hỏi → Gemini embedding → tìm đoạn học liệu được phép xem
        → Gemini trả lời → kiểm tra ID nguồn → trả kết quả
```

Khi nạp tài liệu, LangChain chia text thành các đoạn nhỏ, Gemini tạo embedding rồi lưu vào
database. Nạp lại cùng `documentId` sẽ thay nội dung cũ; nếu tạo embedding thất bại thì dữ liệu
cũ vẫn được giữ lại.

Model embedding đang dùng là `models/gemini-embedding-001`, 768 chiều, với task type
`RETRIEVAL_DOCUMENT` và `RETRIEVAL_QUERY`. Model chat mặc định là `gemini-3.5-flash-lite`.
Có thể đổi model chat trong `.env`. Nếu đổi embedding model, số chiều hoặc cách chia đoạn,
cần tạo database mới và nạp lại tài liệu. Code hiện dùng task type của embedding-001;
embedding-2 cần chỉnh lại cách định dạng đầu vào.

## API

Gửi khóa qua header `X-API-Key`:

| Method | Endpoint | Khóa |
|---|---|---|
| PUT | `/internal/v1/documents` | `AI_ADMIN_KEY` |
| POST | `/internal/v1/documents/upload` | `AI_ADMIN_KEY` |
| GET | `/internal/v1/documents?limit=50&offset=0` | `AI_ADMIN_KEY` |
| DELETE | `/internal/v1/documents/{documentId}` | `AI_ADMIN_KEY` |
| POST | `/internal/v1/search` | `AI_SERVICE_KEY` |
| POST | `/internal/v1/tutor/ask` | `AI_SERVICE_KEY` |

Request mẫu nằm trong `examples/`. JSON dùng camelCase. Upload dùng multipart với ba trường
`documentId`, `title`, `file`; nhận PDF có text, TXT và Markdown UTF-8. Giới hạn mặc định là
5 MiB/file, 200.000 ký tự, 300 đoạn và 300 trang PDF. PDF mã hóa không được hỗ trợ; PDF scan
cần OCR trước. `sourceUrl` chỉ lưu làm thông tin nguồn, không dùng để tải nội dung.

Ví dụ giải thích đáp án:

```json
{
  "question": "Vì sao dùng has worked?",
  "mode": "explain_answer",
  "language": "vi",
  "context": "She ___ here for three years. A. has worked B. work C. working D. works. Standard answer: A.",
  "allowedDocumentIds": ["27bd260f-9558-4884-a9d5-d3058e112d49"],
  "history": []
}
```

`allowedDocumentIds` là trường bắt buộc. Danh sách này phải do Spring Boot xác định sau khi
kiểm tra quyền, không lấy trực tiếp từ trình duyệt. Service lọc tài liệu trước khi tìm nguồn
và tạo prompt. `history` nhận tối đa 12 tin nhắn do backend gửi sang.

Kết quả gồm `answer`, `citations`, `grounded`, `warning`, `exercises`, `usage`, `traceId`.
Nguồn trích dẫn có ID tài liệu, ID đoạn, tiêu đề, URL, trang, nội dung và điểm tương đồng.
`grounded=true` nghĩa là các ID nguồn đã được đối chiếu với kết quả tìm kiếm; vẫn cần kiểm tra
độ đúng của câu trả lời. Câu luyện tập được đánh dấu `aiGenerated=true` và cần duyệt trước
khi đưa vào ngân hàng câu hỏi.

Nếu thiếu nguồn, API trả HTTP 200, `grounded=false`, `citations=[]` và cảnh báo:
“Không tìm thấy tài liệu tham khảo trong hệ thống.” Các lỗi còn lại:

- `401`: sai khóa gọi service.
- `413` / `415`: file quá lớn hoặc sai định dạng.
- `422`: dữ liệu đầu vào không hợp lệ hoặc tài liệu không đọc được.
- `502`: kết quả từ model sai cấu trúc hoặc chứa ID nguồn không hợp lệ.
- `503`: lỗi Gemini, thiếu key hoặc lỗi truy cập index.

`usage` trả số token chat và thời gian xử lý. Token embedding và chi phí hiện trả `null`;
phần tính chi phí cần bổ sung ở backend. Log chỉ ghi trace ID, method, status và thời gian,
không ghi nội dung câu hỏi hay khóa API.

## Database

Runtime dùng PostgreSQL + pgvector. SQLite chỉ được bật rõ ràng trong fixture kiểm thử bằng
`allow_sqlite_for_tests=True`, không có fallback SQLite khi PostgreSQL lỗi.

Để khởi động database đã cấu hình, mở Docker Desktop rồi chạy:

```powershell
docker compose up -d postgres
.\.venv\Scripts\python.exe -m alembic upgrade head
```

Đổi `DATABASE_URL` theo mẫu trong `.env.example`, khởi động lại service và nạp lại học liệu.
Mật khẩu có ký tự đặc biệt cần được URL-encode. Tài khoản DB cần quyền tạo extension `vector`,
hoặc extension phải được tạo sẵn.

Các bảng có tiền tố `rag_`, chỉ lưu index học liệu. PostgreSQL dùng toán tử cosine của pgvector,
lọc quyền trong SQL trước ranking và limit; chưa dùng ANN để giữ exact top-k trong scope.
Alembic quản lý revision dưới khóa deploy; checksum chỉ dùng để tiếp nhận migration SQL cũ.
API kiểm tra revision/signature và số chiều vector trước khi phục vụ,
không tự tạo extension/table. Re-index tạo/validate mọi embedding trước transaction; ghi/xóa cùng
documentId được serialize bằng advisory transaction lock. Lần commit sau cùng thắng; backend
cần serialize tác vụ publish/archive theo version và loại nguồn thu hồi khỏi scope ngay.

Xem [thiết kế hai luồng và contract tích hợp](../docs/RAG_Tutor.md). Không sửa V7/V10 để đổi
REAL[] thành vector: index `rag_*` là dữ liệu dẫn xuất riêng, không thay bảng knowledge nghiệp vụ.

## Phần tích hợp còn lại

Spring Boot sẽ quản lý đăng nhập, chủ hội thoại, quyền học liệu, quota, rate limit và lưu
lịch sử. Khi gọi AI, backend gửi danh sách tài liệu được phép xem, lịch sử và nội dung đã
kiểm tra quyền qua `AI_SERVICE_KEY`. Có thể gửi thêm `X-Request-ID` dạng UUID để theo dõi lỗi.

Khi học liệu được xuất bản, backend dùng `AI_ADMIN_KEY` để nạp dữ liệu; nên dùng ID phiên bản
tài liệu làm `documentId`. Khi thu hồi học liệu, phải bỏ ID khỏi danh sách quyền ngay.
Backend lưu câu trả lời, nguồn và usage, thêm `messageId` theo hợp đồng public API trong
`../docs/API_Specification.md`, đồng thời xử lý timeout và lỗi 502/503.

Chưa có controller Spring Boot, giao diện chat, lưu hội thoại hay dashboard usage cho phần này.
Service AI chỉ dùng nội bộ; khóa service không thay thế quyền của người học. Việc chấm điểm
bài thi và cập nhật lịch ôn tập vẫn do backend xử lý.

## Kiểm tra

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m ruff check --no-cache app migrations tests scripts
.\.venv\Scripts\python.exe -m ruff format --no-cache --check app migrations tests scripts
```

Test mặc định dùng SQLite tạm và model giả lập, không gọi Gemini. Test adapter LangChain thay
network client bằng mock. Test pgvector chỉ chạy khi có `TEST_POSTGRES_URL` trỏ tới database
test riêng. Test pgvector tạo schema ngẫu nhiên và xóa schema đó sau khi chạy; kiểm tra migration,
hai luồng HTTP, lọc quyền và concurrent re-index. Không trỏ TEST_POSTGRES_URL vào DB production.
Job `ai-rag` trong `.github/workflows/ci.yml` chạy cả test pgvector bằng database container riêng.

```powershell
# Đặt TEST_POSTGRES_URL trong môi trường local, không commit giá trị có mật khẩu.
.\.venv\Scripts\python.exe -m pytest tests/test_postgres.py -q
```

Kiểm tra health, OpenAPI và fallback nằm trong `tests/test_api.py`. Để đánh giá chất lượng
trả lời, chạy `python -m scripts.verify_rag --live` rồi đối chiếu nguồn trên học liệu của dự án.

## Tham khảo

- [LangChain RAG với Deep Agents](https://docs.langchain.com/oss/python/deepagents/rag)
- [LangChain retrieval và 2-step RAG](https://docs.langchain.com/oss/python/langchain/retrieval)
- [LangChain Google embeddings](https://reference.langchain.com/python/langchain-google-genai/embeddings/GoogleGenerativeAIEmbeddings)
- [Gemini embeddings](https://ai.google.dev/gemini-api/docs/embeddings)
- [Gemini models](https://ai.google.dev/gemini-api/docs/models)
