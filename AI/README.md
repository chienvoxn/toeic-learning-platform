# AI Tutor

Phần AI của ứng dụng học TOEIC, dùng FastAPI, LangChain và Gemini.
Service tìm nội dung trong học liệu rồi trả lời kèm nguồn tham khảo.
Hiện chạy riêng, chưa nối với Spring Boot và giao diện React.

## Cấu trúc thư mục

```text
AI/
├── app/                 # API, xử lý RAG và lưu vector
├── tests/               # Kiểm thử
├── scripts/             # Tạo cấu hình, chạy demo, kiểm tra server
├── examples/            # Học liệu và request mẫu
├── .env.example         # Mẫu cấu hình
├── compose.yaml         # PostgreSQL có pgvector
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
`init_env.py` tạo sẵn hai khóa gọi API nội bộ và giữ nguyên file `.env` nếu đã có.
Không đưa `.env` lên Git hoặc gửi các khóa này xuống frontend.

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Swagger ở http://127.0.0.1:8001/docs. `/health` kiểm tra server;
`/ready` kiểm tra database và cấu hình key, chưa kiểm tra kết nối tới Gemini.

Mở terminal khác, vẫn ở thư mục `AI`, để nạp học liệu mẫu và hỏi thử:

```powershell
.\.venv\Scripts\python.exe scripts/demo.py --ingest-sample
.\.venv\Scripts\python.exe scripts/demo.py --mode practice --question "Tạo câu luyện tập về present perfect."
```

Demo gọi Gemini thật và dùng quota của API key. Học liệu mẫu chỉ có phần hiện tại hoàn thành;
cần nạp thêm tài liệu cho những chủ đề khác.

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

Mặc định dùng SQLite tại `data/ai.db` để chạy local. Vector lưu dạng JSON, tìm kiếm bằng cosine
trên các đoạn được phép truy cập. Cách này phù hợp với lượng học liệu nhỏ.

Để dùng PostgreSQL + pgvector, mở Docker Desktop, thêm `POSTGRES_PASSWORD` vào `.env` rồi chạy:

```powershell
docker compose up -d postgres
```

Đổi `DATABASE_URL` theo mẫu trong `.env.example`, khởi động lại service và nạp lại học liệu.
Mật khẩu có ký tự đặc biệt cần được URL-encode. Tài khoản DB cần quyền tạo extension `vector`,
hoặc extension phải được tạo sẵn.

Các bảng có tiền tố `rag_`, chỉ lưu index học liệu. PostgreSQL dùng toán tử cosine của pgvector,
chưa thêm index ANN. Schema hiện được tạo lúc khởi động; migration và backup cần bổ sung
khi triển khai. Các lần cập nhật/xóa cùng một `documentId` cần được xử lý tuần tự.

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
.\.venv\Scripts\python.exe -m ruff check --no-cache app tests scripts
.\.venv\Scripts\python.exe -m ruff format --no-cache --check app tests scripts
.\.venv\Scripts\python.exe scripts/smoke.py
```

Test mặc định dùng SQLite tạm và model giả lập, không gọi Gemini. Test adapter LangChain thay
network client bằng mock. Test pgvector chỉ chạy khi có `TEST_POSTGRES_URL` trỏ tới database
test riêng; dữ liệu kiểm thử được tạo bằng ID ngẫu nhiên và xóa sau khi chạy.

`smoke.py` khởi động server để kiểm tra HTTP rồi dừng lại, không gọi Gemini. Để đánh giá chất
lượng trả lời, cần chạy demo bằng key thật và đối chiếu nguồn trên học liệu của dự án.

## Tham khảo

- [LangChain Google embeddings](https://reference.langchain.com/python/langchain-google-genai/embeddings/GoogleGenerativeAIEmbeddings)
- [Gemini embeddings](https://ai.google.dev/gemini-api/docs/embeddings)
- [Gemini models](https://ai.google.dev/gemini-api/docs/models)
