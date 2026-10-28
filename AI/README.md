# TOEIC AI — LangChain + Gemini RAG

Service FastAPI nội bộ cho AI Tutor, tách khỏi backend Spring Boot theo `docs/HLD.md`.
Đây là phần xử lý AI; chưa nối vào giao diện React hay các controller Spring Boot.

## Chức năng đã triển khai

| Chức năng | Cách dùng |
|---|---|
| Hỏi đáp TOEIC có nguồn | `mode: ask` |
| Giải thích ngữ pháp và ví dụ | `mode: grammar` |
| Giải nghĩa từ, từ loại, collocation | `mode: vocabulary` |
| Giải thích đáp án chuẩn và đáp án nhiễu | `mode: explain_answer`, gửi câu hỏi và đáp án chuẩn trong `context` |
| Giải thích, dịch, tóm tắt bài đọc/transcript | `mode: reading`, gửi nội dung được cấp quyền trong `context` |
| Sinh câu luyện tập nháp | `mode: practice`, `exerciseCount: 1..5`; 4 lựa chọn, đáp án, giải thích, nguồn |
| Tìm kiếm theo ngữ nghĩa | Endpoint `search`, không cần gọi chat model |
| Nạp/cập nhật/xóa học liệu | JSON text hoặc file PDF có text, TXT, Markdown UTF-8 |
| Hội thoại nhiều lượt | Nhận tối đa 12 tin nhắn lịch sử do Spring Boot cung cấp |
| Kiểm soát nguồn | Bắt buộc `allowedDocumentIds`; lọc trong SQL trước khi xếp hạng và tạo prompt |
| Xử lý thiếu nguồn | `grounded=false`, `citations=[]`, cảnh báo chính xác theo SRS |
| Metadata sử dụng | Provider, model, token chat, latency, trace ID trả về Spring Boot |

Các mode dùng prompt chuyên biệt trên cùng pipeline RAG. Nội dung trả lời vẫn cần đánh giá
trên học liệu thực. `grounded=true` xác nhận ID nguồn thuộc các đoạn đã truy xuất; không phải
cam kết mọi nhận định của mô hình đều đúng. Bài luyện do AI sinh có `aiGenerated=true`, cần
giáo viên duyệt trước khi đưa vào ngân hàng câu hỏi chính thức.

## Luồng xử lý

```text
React → Spring Boot (xác thực, ownership, ACL, quota, lịch sử)
             → FastAPI /internal/v1/tutor/ask
                 → Gemini query embedding qua LangChain
                 → PostgreSQL + pgvector / SQLite (lọc allowedDocumentIds)
                 → Gemini chat qua LangChain, structured output
                 → kiểm tra ID nguồn → trả answer + citations + usage
```

Nạp học liệu: text/PDF → LangChain RecursiveCharacterTextSplitter → Gemini document
embeddings → chuẩn hóa vector → lưu tất cả chunks trong một transaction. Nếu embedding thất
bại, dữ liệu cũ vẫn giữ nguyên. Nạp lại cùng `documentId` thay thế các chunks cũ.

Mặc định dùng **`models/gemini-embedding-001`**, 768 chiều, task type `RETRIEVAL_DOCUMENT`
và `RETRIEVAL_QUERY`. Đây là pipeline text; PDF được trích text trước khi embedding.
Chat mặc định `gemini-3.5-flash-lite`; có thể đổi model chat trong `.env` theo quyền API của bạn.
Không đổi tùy tiện embedding model/dimension/chunking trên index cũ: service sẽ từ chối khởi
động nếu cấu hình không khớp. Muốn đổi, dùng database mới rồi nạp lại học liệu.
Adapter hiện dùng task types của embedding-001; embedding-2 cần cách định dạng task khác.

## Chạy trên Windows

Cần Python 3.11 trở lên. Chạy trong PowerShell:

```powershell
cd D:\TOEIC_app\AI
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock
.\.venv\Scripts\python.exe -m pip install -e ".[dev]" --no-deps
.\.venv\Scripts\python.exe scripts/init_env.py
```

Mở `.env`, điền `GOOGLE_API_KEY` lấy từ [Google AI Studio](https://aistudio.google.com/apikey).
Script đã tạo hai khóa nội bộ ngẫu nhiên khác nhau; không đưa các khóa này vào React hoặc Git.
Nếu `.env` đã tồn tại, script giữ nguyên toàn bộ nội dung. Có thể dùng môi trường đã cài sẵn
trong workspace này, bỏ qua hai lệnh tạo venv/cài thư viện.

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

- Swagger: http://127.0.0.1:8001/docs
- Health: http://127.0.0.1:8001/health
- Ready: http://127.0.0.1:8001/ready — kiểm tra DB và có cấu hình key, **không gọi thử Gemini**.

Mở terminal thứ hai để nạp mẫu và hỏi thử. Lệnh này gọi Gemini thật, sử dụng quota của key:

```powershell
cd D:\TOEIC_app\AI
.\.venv\Scripts\python.exe scripts/demo.py --ingest-sample
.\.venv\Scripts\python.exe scripts/demo.py --mode practice --question "Tạo câu luyện tập về present perfect."
```

Dữ liệu mẫu nhỏ chỉ đủ thử luồng present perfect. Hãy nạp học liệu TOEIC thật mà nhóm được
phép sử dụng để các chế độ khác có căn cứ trả lời. Chưa có corpus thì tutor sẽ báo thiếu nguồn.
Không cần kích hoạt venv hay thay ExecutionPolicy khi dùng các lệnh trên.

## API nội bộ

| Method | Endpoint | Khóa `X-API-Key` |
|---|---|---|
| PUT | `/internal/v1/documents` | `AI_ADMIN_KEY` |
| POST | `/internal/v1/documents/upload` | `AI_ADMIN_KEY` |
| GET | `/internal/v1/documents?limit=50&offset=0` | `AI_ADMIN_KEY` |
| DELETE | `/internal/v1/documents/{documentId}` | `AI_ADMIN_KEY` |
| POST | `/internal/v1/search` | `AI_SERVICE_KEY` |
| POST | `/internal/v1/tutor/ask` | `AI_SERVICE_KEY` |

JSON dùng camelCase. Xem `examples/knowledge.json` và `examples/ask.json` hoặc Swagger.
Upload dùng multipart fields `documentId`, `title`, `file`. Giới hạn mặc định: 5 MiB/file,
200.000 ký tự, 300 chunks, PDF tối đa 300 trang, không mã hóa. PDF scan cần OCR bên ngoài.
`sourceUrl` chỉ là metadata HTTP/HTTPS, service không tải URL hoặc đọc đường dẫn do client gửi.

Ví dụ request hỏi đáp:

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

Response có `answer`, `grounded`, `warning`, `citations`, `exercises`, `usage`, `traceId`.
Mỗi nguồn có `documentId`, `chunkId`, `title`, `sourceUrl`, `page`, `excerpt`, `similarity`.
Thiếu nguồn trả HTTP 200 với cảnh báo **Không tìm thấy tài liệu tham khảo trong hệ thống.**
Gemini lỗi/thiếu key trả 503; cấu trúc trả lời hoặc ID nguồn không hợp lệ trả 502; input lỗi
trả 422; sai khóa trả 401; file quá lớn trả 413; định dạng file không hỗ trợ trả 415.

`usage` chứa số token **chat**; token embedding và chi phí chưa có dữ liệu đáng tin nên trả
`null`, không ước lượng thành 0. Spring Boot có thể tính chi phí từ bảng giá đã cấu hình.
Lỗi provider không lộ exception/credentials. Log ứng dụng chỉ ghi trace, method, status, latency.

## Nối với Spring Boot

1. Module `backend/.../ai` nhận request của learner, kiểm tra đăng nhập, conversation ownership,
   quota, rate limit; không nhận danh sách quyền do trình duyệt tự khai.
2. Từ ACL và trạng thái publish/version hiện hành, tính `allowedDocumentIds` ở backend.
   ID học liệu gửi vào service nên là ID phiên bản bất biến, tránh trộn nội dung giữa các phiên bản.
3. Chỉ gửi lịch sử của đúng người học, nội dung đề/bài đọc đã được cấp quyền và danh sách ID
   trên. Gọi service bằng `AI_SERVICE_KEY`; chuyển tiếp `X-Request-ID` dạng UUID nếu có.
4. Lưu messages, citations và usage vào các bảng đã thiết kế trong ERD. Wrapper Spring thêm
   `messageId` để khớp public API trong `docs/API_Specification.md`.
5. Khi xuất bản học liệu, dùng `AI_ADMIN_KEY` để upsert text. Khi thu hồi/ẩn học liệu, loại ID
   khỏi ACL ngay; xóa index khi phù hợp. Không xem index AI là nguồn quyết định quyền.
6. Đặt HTTP timeout bên Spring phù hợp; xử lý 502/503 để học/thi vẫn hoạt động. Quota và retry
   do Spring quản lý; không tự retry vô hạn vì có thể tăng chi phí gọi model.

Chưa triển khai các public controller, lưu lịch sử, quota, dashboard usage hay giao diện chat
trong repo này; đó là phần tích hợp Spring/React tiếp theo, không nằm trong service AI nội bộ.
FastAPI không chấm điểm bài thi, ghi điểm hoặc cập nhật lịch ôn tập.

Service phải ở mạng nội bộ, chỉ backend/admin đáng tin cậy gọi. Shared key chứng minh caller
là backend, không thay thế kiểm tra quyền learner. Không expose trực tiếp lên Internet.

## PostgreSQL + pgvector

SQLite mặc định lưu vector trong JSON và tính cosine trên các chunks **được cấp quyền**, phù hợp
corpus nhỏ khi phát triển. PostgreSQL dùng kiểu `vector` và toán tử cosine trong SQL. Hiện tìm
chính xác, chưa có index ANN; cần đo tải trước khi thêm HNSW/IVFFlat cho corpus lớn.

Để chạy PostgreSQL riêng cho AI, mở Docker Desktop, thêm `POSTGRES_PASSWORD` vào `.env` rồi:

```powershell
docker compose up -d postgres
```

Đổi `DATABASE_URL` trong `.env` sang PostgreSQL với mật khẩu vừa đặt, sau đó khởi động lại
service và nạp lại học liệu. URL-encode mật khẩu nếu chứa ký tự đặc biệt. Database AI này là
index dẫn xuất, không thay thế database nghiệp vụ Spring Boot; tên bảng dùng prefix `rag_`.
Account DB cần quyền tạo extension `vector` khi khởi tạo, hoặc DBA tạo sẵn extension.

Schema được tạo khi startup cho MVP; triển khai lâu dài cần migration có version và backup.
Không chạy nhiều tác vụ upsert/delete cho cùng documentId đồng thời: backend nên tuần tự hóa
các sự kiện index theo phiên bản tài liệu.

## Kiểm thử

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m ruff check --no-cache app tests scripts
.\.venv\Scripts\python.exe -m ruff format --no-cache --check app tests scripts
.\.venv\Scripts\python.exe scripts/smoke.py
```

Test mặc định dùng SQLite tạm và provider giả lập, không gửi dữ liệu ra ngoài hay tốn quota.
Có test adapter LangChain thật với network client được thay thế. Để kiểm tra pgvector, đặt
`TEST_POSTGRES_URL` trỏ đến **database test riêng**, rồi chạy lại pytest. Test chỉ xóa các tài
liệu có ID ngẫu nhiên do nó tạo. Test PostgreSQL/pgvector chạy khi bạn cấu hình biến này.

`requirements.lock` chốt các phiên bản đã cài và kiểm thử; cập nhật file này có chủ đích khi
nâng thư viện. `pyproject.toml` khai báo các khoảng phiên bản tương thích của service.

Kiểm thử với Gemini thật: khởi động service, chạy demo, kiểm tra nguồn và nội dung trả lời
trên câu hỏi tiếng Việt/Anh. Chưa có API key nên chưa thể xác minh chất lượng model/quota
thực tế; không dùng kết quả offline để kết luận độ chính xác RAG.

## Tài liệu thư viện

- [LangChain Google integration](https://reference.langchain.com/python/langchain-google-genai/embeddings/GoogleGenerativeAIEmbeddings)
- [Gemini embeddings và task types](https://ai.google.dev/gemini-api/docs/embeddings)
- [Danh sách Gemini chat models](https://ai.google.dev/gemini-api/docs/models)
