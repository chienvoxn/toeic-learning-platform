# Chatbot RAG trên web

Luồng runtime: bong bóng chat trong LearnerTemplate → Spring Boot `/api/v1/ai` →
FastAPI `/internal/v1/tutor/ask` → pgvector và Gemini. Spring quản lý Bearer,
permission, ownership, quota, lịch sử và citation snapshot. Python giữ index dẫn xuất.
Khả năng runtime ban đầu chỉ dùng mode `ask`; các mode giải thích đáp án/bài tập,
streaming và dashboard usage AI chưa nằm trong luồng này.

## Cấu hình và chạy

1. Trong `AI`, chuẩn bị `.venv` và cài `requirements.lock` theo `AI/README.md`.
   Chạy `python scripts/init_env.py`, điền `GOOGLE_API_KEY` trong `AI/.env`.
2. Khởi động Docker Desktop, tại `AI` chạy `docker compose up -d postgres`,
   rồi `.\.venv\Scripts\python.exe -m alembic upgrade head`.
3. Chạy `.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload`.
4. Trong file ignored `backend/config/application-local.properties`, thêm:

   ```properties
   app.ai.base-url=http://127.0.0.1:8001
   app.ai.service-key=GIA_TRI_AI_SERVICE_KEY_TRONG_AI_ENV
   app.ai.admin-key=GIA_TRI_AI_ADMIN_KEY_TRONG_AI_ENV
   app.ai.daily-limit=30
   app.ai.minute-limit=5
   ```

   Hai key này phải giống `AI_SERVICE_KEY`/`AI_ADMIN_KEY` trong `AI/.env` và khác nhau.
   Google OAuth Client ID phục vụ đăng nhập; `GOOGLE_API_KEY` gọi Gemini và chỉ ở Python.
   Đừng đưa bất kỳ secret nào vào frontend hoặc Git.

   Có thể dùng biến môi trường `PYTHON_RAG_BASE_URL`, `AI_SERVICE_KEY` và `AI_ADMIN_KEY`
   thay cho ba thuộc tính trên. Spring không cần và không được nhận `GOOGLE_API_KEY`.
5. Tại `backend`: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.
   Cần DB có lịch sử migration hợp lệ. Flyway thêm V34; không repair V26 hoặc reset DB cũ.
6. Tại `frontend/web`: `npm run dev`, đăng nhập và mở hồ sơ/dashboard.
   Bong bóng “Hỏi trợ lý” xuất hiện ở góc dưới phải, trên thanh điều hướng mobile.

## Nạp học liệu để chatbot có nguồn

Admin phải đồng bộ học liệu đã PUBLISHED, có current version hợp lệ và visibility
PUBLIC/AUTHENTICATED. API không lấy text hoặc document ID tùy ý từ browser:

```powershell
# Chỉ đặt Bearer token của admin trong terminal cá nhân; không gửi token lên chat.
$headers = @{ Authorization = "Bearer $adminToken" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/admin/ai/contents/$contentId/index" -Headers $headers
```

Mỗi version dùng documentId bằng contentVersionId. Thành công mới ghi mapping vào
`ai_indexed_versions`. Nếu index thành công mà ghi mapping thất bại, retry endpoint
với cùng version sẽ thay index và ghi mapping; nguồn chưa có mapping chưa được retrieve.
Đổi current version cần index version mới. Thu hồi/archive loại nguồn khỏi scope ngay;
chưa tự xóa index hoặc tự đồng bộ publish vì publish workflow hiện vẫn DESIGN.
Không có học liệu hợp lệ đã index thì chatbot trả cảnh báo thiếu nguồn, không gọi LLM.
Không nạp đáp án hoặc transcript đề đang thi trong MVP.

File mẫu tự biên soạn `AI/examples/toeic-grammar-sample.txt` dùng để kiểm tra upload
PDF/TXT. Xem lệnh upload trong `AI/README.md`. Upload trực tiếp vào Python chỉ tạo vector
index; để nguồn xuất hiện trong Web, `documentId` phải là một `contentVersionId` đã publish
và có mapping `ai_indexed_versions`. Luồng chuẩn của Web vẫn là gọi endpoint admin Java ở trên.

Nếu database local chưa có học liệu published (publish workflow hiện vẫn DESIGN), dùng fixture
demo idempotent, không phải Flyway migration và không dùng cho production:

```powershell
# Lấy UUID của một tài khoản Admin/Teacher local đã tồn tại; không dùng token làm author_id.
psql -d toeic_dev_fresh -U postgres `
  -v author_id="UUID_CUA_ADMIN_HOAC_TEACHER" `
  -f ..\AI\examples\seed_backend_demo_content.sql

# Sau khi đăng nhập Admin và đặt token trong terminal cá nhân:
$headers = @{ Authorization = "Bearer $adminToken" }
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/admin/ai/contents/0bd8ce55-6f85-4eef-8e02-64b7aaad39be/index" `
  -Headers $headers
```

Fixture tạo content/version published có ID ổn định; endpoint Java mới là bước ingest chuẩn vì
nó chỉ gửi text đã kiểm tra publication sang Python rồi ghi mapping nguồn cho chatbot Web.

Để nạp catalog học tập mở rộng (từ vựng, ngữ pháp, đọc, transcript nghe và chiến lược) vào đúng database
local, dùng `seed_backend_learning_catalog.sql`. Script chủ động từ chối chạy nếu database
không phải `toeic_dev_fresh`. Sau khi seed, cần index từng content qua Spring Boot; chỉ thêm
dữ liệu vào PostgreSQL chưa đủ để chatbot truy hồi vì `rag_chunks` là index dẫn xuất:

```powershell
psql -d toeic_dev_fresh -U postgres `
  -v author_id="UUID_CUA_ADMIN_HOAC_TEACHER" `
  -f ..\AI\examples\seed_backend_learning_catalog.sql

$headers = @{ Authorization = "Bearer $adminToken" }
2..26 | ForEach-Object {
  $contentId = "de400000-0000-4000-8000-{0:D12}" -f $_
  Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/v1/admin/ai/contents/$contentId/index" `
    -Headers $headers
}
```

Fixture idempotent theo ID và không phải Flyway migration production. Trách nhiệm của
`ChatService.index` là kiểm tra version đang published, gửi nội dung sang RAG và lưu mapping;
`TutorService` chỉ tạo câu trả lời từ các chunk đã được cấp quyền và truy hồi.

## Contract public runtime

Tất cả API dùng session hiện hành. `ai:chat:self` mặc định cấp Learner/Teacher/Admin;
index yêu cầu ADMIN và `ai:knowledge:manage`. Không có Admin bypass ownership chat.

| Method | Path dưới /api/v1 | Request / kết quả |
|---|---|---|
| POST | /ai/conversations | Không body; 201 `{id,title,updatedAt}` |
| GET | /ai/conversations?page=0 | Mảng tối đa 20, mới nhất trước |
| GET | /ai/conversations/{id}/messages?page=0 | Mảng tối đa 50 tin nhắn, cũ trước trong trang mới nhất |
| POST | /ai/conversations/{id}/messages | `{requestId:UUID,question:string}` → Answer |
| POST | /admin/ai/contents/{contentId}/index | Không body; 204 |

Question 1–4000 ký tự; mỗi user tối đa 100 hội thoại. Page 0–1000. Answer gồm
`answer`, `grounded`, `warning`, `citations`. Citation có documentId, chunkId,
contentId, contentVersionId, title, excerpt, page; không trả URL do model tạo.
Lỗi trả `code,message,traceId,details`; 401/403, own 404, quota 429,
request conflict/pending 409, AI response không hợp lệ 502 và AI không khả dụng 503.
Web dùng text React thay vì HTML từ AI, dẫn nguồn tới trang học liệu đã phân quyền.

## Trách nhiệm và tính nhất quán

- `ChatService`: xác thực actor/permission cả ở service, giới hạn request và điều phối.
- `ChatRepository`: persistence hội thoại, request, mapping index và citation snapshot.
- `RagClient`: timeout 3 giây connect/40 giây request, hai khóa theo chức năng,
  không retry provider tự động, không log request body/key hoặc lỗi provider thô.
- Reservation khóa hàng user để serialize quota, mỗi hội thoại một request pending
  trong 2 phút. Mặc định 5 request/phút, 30 request/24h trượt; tính cả request lỗi.
  Đây là giá trị cấu hình cho runtime ban đầu, không chốt chính sách giá/quota sản phẩm.
- Mỗi request có UUID idempotency: completed cùng nội dung được replay; reuse khác
  nội dung hoặc pending/failed trả 409. Không tự gọi lại LLM với request cũ.
  Nếu tiến trình chết sau provider call, request vẫn pending; sau 2 phút cho request mới.
  Quota reservation không được hoàn lại tự động; không cam kết exactly-once phía Gemini.
- Không giữ transaction DB trong lúc gọi HTTP/LLM. Sau thành công, lưu cặp user/assistant,
  citation snapshot, usage metadata và request completed cùng transaction.
- Chỉ current published version được retrieve. Scope rỗng fallback; hơn 500 nguồn
  trả AI_SCOPE_LIMIT, chưa có cơ chế narrowing corpus lớn.
- Recheck scope sau provider call và khi đọc/replay; câu trả lời có nguồn không còn
  được phép sẽ bị che cả nội dung và citation. Snapshot lưu theo version, không FK
  sang chunk Python hoặc phụ thuộc index còn tồn tại. Quyền thay đổi đúng thời điểm
  HTTP response vẫn có race như các lần đọc thông thường; chưa có khóa xuyên HTTP.
- Lịch sử gửi Python tối đa 12 message, mỗi message tối đa 4000 ký tự; assistant có
  nguồn bị thu hồi không được đưa lại vào context. Prompt/answer ở bảng hội thoại
  chỉ đọc theo owner; không đưa vào access log. Retention và xóa hội thoại còn OPEN.

## Schema và giới hạn

V34 thêm `ai_indexed_versions`, `ai_chat_requests`, `ai_citation_snapshots`, hai permissions.
Tái sử dụng V7 `ai_conversations`/`ai_messages` và V10 `ai_request_logs`.
`ai_message_sources` V7 vẫn là lịch sử thiết kế, không ghi chunk Python vào FK đó.
Request lỗi lưu status/error code trong ai_chat_requests; ai_request_logs ghi usage
thành công, không giả ước tính chi phí khi chưa có bảng giá được chốt.

AGENTS.md yêu cầu bảo toàn xác minh OTP đăng ký nhưng code V25/Password_Recovery.md
đã dùng đăng ký ACTIVE không OTP. Tích hợp chatbot không sửa identity; cần chốt riêng
mâu thuẫn chính sách đó trước nhiệm vụ thay đổi đăng ký.

Kiểm thử: backend `ChatIntegrationTest` dùng PostgreSQL thật và mock RagClient;
frontend `chat.api.test.mjs` kiểm contract/lỗi. Kiểm tra Gemini thật cần key/quota,
không được suy từ test mock. Bộ test backend phải chạy trên DB riêng.

## Kết quả kiểm chứng 2026-11-15

- Backend: `.\mvnw.cmd -o test`, toàn bộ **1029 test đạt, 0 failure/error/skip**,
  07:40 phút. PostgreSQL 18 trên `127.0.0.1:55461/rag_chat_final_verify` mới hoàn toàn,
  Flyway V1–V34 thành công; pool tối đa 4/minimum idle 0. Java runtime của máy là
  24.0.2, Maven vẫn compile target 21; chưa xác minh runtime Java 21 trong lượt này.
  Log ignored: `backend/target/rag-final-full-test.log`. Không chạy test trên toeic_db.
- Trong đó 9 ChatIntegrationTest dùng persistence PostgreSQL thật, provider mock;
  2 RagClientTest gọi HTTP server fixture để kiểm khóa nội bộ/JSON/error masking.
  Auth/RBAC tests đã được đồng bộ hai permission AI đã có enforcement.
- Frontend: `npm test` **62/62**, `npm run build` thành công; `npm run lint` exit 0,
  còn cảnh báo có sẵn ở các feature khác và cảnh báo bundle lớn khi build.
- `npx playwright test tests/chat.widget.spec.js` **1/1**, với HTTP fixture rõ ràng:
  mở/gửi/đọc nguồn/đóng/mở lại, mất kết nối rồi gửi lại cùng requestId, desktop/mobile.
  Ảnh trong ignored `frontend/web/test-results/chat-desktop.png` và `chat-mobile.png`.
- Chưa gọi Gemini thật, chưa dùng API key thật để ingest corpus của người dùng.
  Chưa kiểm tải lớn hoặc streaming; corpus và quyền cần được kiểm tra khi vận hành thật.

## AI-RAG-03 - Exam Guard

- Exam state is verified using server-side attempt records.
- Active SECTION, legacy FULL_TEST and blueprint-pinned FULL_MOCK attempts restrict AI Tutor access.
- PRACTICE attempts do not restrict AI Tutor.
- Expired or graded attempts do not restrict AI Tutor.
- Restricted requests return HTTP 403 with code AI_EXAM_RESTRICTED.
- Chat history and cached answer replay are restricted during exams.
- Exam state is checked before provider execution and before
  persisting a generated answer.
- Database failures must not silently bypass the guard.
- Concurrent exam-start and AI-response operations require
  additional synchronization verification.

### Transaction synchronization

- Section and Full Test creation acquire a per-user database row lock.
- AI answer persistence acquires the same user row lock.
- The exam guard is rechecked before storing generated answers.
- Remote AI provider calls remain outside database transactions.
- Frontend clients must also enforce the active exam UI state.
