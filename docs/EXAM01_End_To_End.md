# EXAM-01 — Quản lý đề, import, xuất bản và thi trên server

## Phạm vi và quyết định

Triển khai trên nhánh `feat/toeic-bulk-import-exam`. Giữ Spring Boot modular monolith,
PostgreSQL/Flyway và React web hiện có. Không thay đổi đăng ký ACTIVE không OTP.

Quyết định người dùng ngày 2026-11-11: **Teacher soạn/gửi duyệt; Admin duyệt và
xuất bản**. Các lựa chọn kỹ thuật bên dưới là cách triển khai của EXAM-01, không
phải các quyết định OPEN trước đây đã tự động trở thành yêu cầu.

- Bản nháp lưu JSONB trong `test_versions`, có revision chống ghi đè. Import JSON
  kiểm tra toàn bộ trước khi tạo đề; lỗi chỉ rõ `questions[i]`/`groups[i]` (i bắt đầu 0).
- Trạng thái biên tập: DRAFT → IN_REVIEW → APPROVED; Admin có thể trả IN_REVIEW
  về DRAFT kèm ghi chú. Chỉ APPROVED được xuất bản. Đang duyệt không được sửa.
- Xuất bản tạo blueprint, câu hỏi/nhóm/choices/media links bằng persistence và
  domain ngân hàng câu hỏi hiện có, pin vào `test_questions` cùng một transaction.
  Quyền tác giả giữ theo chủ đề, người duyệt lưu riêng. Không thay nguồn bài cũ.
- `test_versions.status`: DRAFT → PUBLISHED → RETIRED. Sửa đề đã xuất bản bằng
  phiên bản nháp mới, tối đa một bản nháp đang mở mỗi test. Không xóa bài làm lịch sử.
- Media mới dùng `media_resources` + payload `media_uploads` trong PostgreSQL,
  visibility AUTHENTICATED, ownership người upload. Chỉ nhận signature PNG/JPEG/WebP
  hoặc MP3/WAV/OGG, tối đa 20 MB/file. Không fetch URL ngoài, không nhận HTML/SVG.
  Signature không thay thế kiểm tra biên tập chất lượng audio/ảnh trong màn xem trước.
- Người học phải đăng nhập để thi đề thật. Guest Reading mẫu cũ vẫn chạy riêng trên
  trình duyệt; không tự chuyển bài server sang local khi API lỗi.
- Start có `startKey` UUID, duy nhất theo user; khóa user trước kiểm tra giới hạn
  lượt và insert. Retry cùng key trả cùng bài, key khác áp dụng `maxAttempts`.
- Autosave khóa attempt, kiểm ownership/status/giờ server/section và choice thuộc
  đúng snapshot. `clientSeq` tăng theo bài, lưu theo câu; sequence cũ hoặc cùng
  sequence nhưng khác đáp án trả 409 ANSWER_STALE. Client cần tải lại để đồng bộ.
- Submit đồng bộ: khóa attempt → SUBMITTED → chấm exact versions → GRADED và lưu
  result JSON/part results/outbox trong một transaction. Submit lặp trả cùng result;
  không yêu cầu key submit riêng vì attempt là khóa nghiệp vụ. Worker mỗi 30 giây
  xử lý bài hết giờ; GET bài hết giờ cũng chấm trước khi trả. Một bài lỗi không rollback
  các bài khác trong worker. Bài bị lỗi snapshot cần xử lý dữ liệu, không tự thay câu.
- Media visibility được kiểm lại khi đọc bài/kết quả và tải file. Transcript và đáp án
  chỉ trả trong kết quả, không có trong DTO đang làm bài.
- Bảng điểm do Admin cung cấp có tên/nguồn, đủ raw counts 0–100 cho từng kỹ năng,
  giá trị không giảm, 5–495 và bội số 5. API không sửa bảng đã tạo. Đề pin bảng lúc
  xuất bản, attempt pin lúc bắt đầu, điểm được lưu lúc chấm. Chỉ quy đổi kỹ năng có
  đủ 100 câu; không có bảng thì chỉ hiển thị số đúng, không nội suy điểm.

## Cấu trúc đề

| Loại | Số câu | Thời gian |
|---|---|---|
| PRACTICE | 1–200, không cắt nhóm | 1–240 phút do người soạn chọn |
| LISTENING | Part 1/2/3/4: 6/25/39/30 | 45 phút |
| READING | Part 5/6/7: 30/16/54 | 75 phút |
| FULL | Cả 7 Part, 200 câu | 120 phút; khóa chuyển section tại phút 45 |

Part 2 có 3 lựa chọn, các Part khác có 4, đúng một đáp án. Part 3/4 có nhóm 3 câu,
Part 6 nhóm 4 câu, Part 7 nhóm 2–5 câu. Reading/Full yêu cầu 10 nhóm Part 7 đơn
(29 câu) và 5 nhóm đa văn bản (25 câu). Nhiều văn bản trong một nhóm được nhập
trong passageText với tiêu đề/phân cách; hệ thống kiểm cấu trúc số câu, người duyệt
kiểm nội dung thực tế. Câu phải tăng theo Part, cùng nhóm liên tiếp và đúng Part.

Cấu trúc bản 2 giờ đối chiếu [ETS Examinee Handbook](https://www.es.ets.org/pdfs/toeic/toeic-listening-reading-test-examinee-handbook.pdf)
và [ETS FAQ](https://www.ets.org/toeic/about/faq/product-specific-faq/toeic-listening-reading.html).
Đây là app luyện thi: player cho phát lại audio, không mô phỏng máy thi bảo mật
hoặc tự động ghép các file thành một audio liên tục. Điểm hiển thị luôn là ước tính.

## API runtime (base /api/v1)

| Method/path | Chức năng |
|---|---|
| GET `/teacher/tests?page=0` | Tóm tắt tối đa 50 phiên bản/trang; self/any |
| GET `/teacher/tests/{versionId}` | Nháp/preview gồm answer key, có ownership |
| POST `/teacher/tests` | Tạo nháp; cho phép nội dung còn đang soạn |
| PUT `/teacher/tests/{versionId}` | `{revision, document}`; chỉ DRAFT |
| POST `/teacher/tests/validate` | ExamDocument → mảng `{field,reason}` |
| POST `/teacher/tests/import` | ExamDocument → 201 draft, hoặc 422 và không tạo đề |
| POST `/teacher/tests/{versionId}/submit-review` | `{revision}` |
| POST `/teacher/tests/{versionId}/review` | Admin `{revision,decision:APPROVE|RETURN,note}` |
| POST `/teacher/tests/{versionId}/publish` | Admin `{revision}`, kiểm tra lại trước publish |
| POST `/teacher/tests/{versionId}/versions` | Tạo/lấy nháp mới, copy document từ bản nguồn |
| POST `/teacher/tests/{versionId}/retire` | Admin ngừng phát hành |
| POST `/teacher/media` | Multipart `file` → resourceId/type/mime/size |
| GET `/media/{resourceId}` | Bearer; chủ media, quản lý any hoặc media thuộc bài của actor |
| GET/POST `/teacher/score-conversions` | Đọc bảng / Admin nhập bảng bất biến |
| GET `/tests?page=0`, `/tests/{versionId}` | Danh mục công khai, không answer key |
| POST `/exam-attempts` | `{testVersionId,startKey}` → 201 bài làm |
| GET `/exam-attempts/{id}` | Bài của actor, serverNow/expiry/section/questions/answers |
| PUT `/exam-attempts/{id}/answers` | `{questionId,selectedOption,clientSeq}`; questionId là UUID version |
| POST `/exam-attempts/{id}/submit` | Chấm và trả result idempotent |
| GET `/exam-attempts/{id}/result` | Snapshot kết quả đã chấm |
| GET `/users/me/exam-history?page=0` | 50 bài/trang, gồm bài đang làm |
| GET `/users/me/exam-progress` | Thống kê số đúng/tổng theo Part từ bài đã chấm |
| GET `/users/me/exam-capabilities` | Permission thực của phiên để điều khiển UI |

`/tests/{id}` dùng **testVersionId**, không dùng test identity. Backend PRAC-01
POST/GET `/attempts` vẫn giữ contract cũ. Ba route `/attempts/{id}/answers`,
`/submit`, `/result` dùng chung lifecycle mới. Web đọc/làm lại các bài trong lịch sử
qua `/exam-attempts/{id}` cho cả hai source. Submit trả kết quả ngay (200), không
trả receipt async như sơ đồ DESIGN cũ.

Lỗi: 400 sai format; 401 chưa xác thực; 403 thiếu permission; 404 không tồn tại/
ngoài ownership; 409 revision/status/giới hạn lượt/sequence/expiry/section; 413 file
quá lớn; 422 đề sai cấu trúc hoặc media. Không trả storage path hoặc token trong lỗi.

## Mẫu import

Mẫu có thể tải trực tiếp tại `/teacher/tests` hoặc `/admin/tests`. JSON UTF-8,
tối đa 2 MB nội dung; audio/ảnh upload riêng, gán resourceId vào câu/nhóm.

```json
{
  "title": "Reading practice",
  "description": "Đề tự biên soạn",
  "testType": "PRACTICE",
  "durationSeconds": 600,
  "maxAttempts": null,
  "conversionId": null,
  "groups": [],
  "questions": [{
    "partNumber": 5,
    "groupKey": null,
    "questionText": "The meeting starts ___ noon.",
    "choices": ["at", "on", "in", "to"],
    "answer": "A",
    "explanation": "At chỉ một thời điểm cụ thể.",
    "audioId": null,
    "imageId": null,
    "transcript": null
  }]
}
```

Group có `key`, `partNumber`, `instruction`, `passageText`, `audioId`, `imageId`,
`transcript`; question.groupKey tham chiếu key. Không nhận Word/PDF/OCR hoặc ZIP
đề trong luồng này. Các file audio/image phải được upload bởi chủ đề hoặc có grant
tái sử dụng hợp lệ. Admin chỉnh đề của Teacher cần dùng media Teacher được quyền sử dụng.

## Service và persistence

| Thành phần | Trách nhiệm |
|---|---|
| ExamManagementService | Quyền/ownership, revision, kiểm import, review, publish và retire |
| ExamValidator | Cấu trúc Part, choices, thứ tự, nhóm và blueprint theo loại đề |
| ExamQuestionPublisher | Tái sử dụng QuestionAuthoringRepository/QuestionChoiceDraft để tạo các version đã duyệt |
| ExamMediaService | Lưu bytes nguyên tử, nhận diện signature và kiểm quyền tải |
| ExamCatalogService | Chỉ trả metadata phiên bản đã published, phân trang |
| ExamAttemptService | Start idempotent, snapshot, autosave, section/expiry, submit, chấm, lịch sử/tiến độ |
| ScoreConversionService | Kiểm/ghi bảng quy đổi do Admin cung cấp, không tự tạo công thức |
| ExamExpiryWorker | Gọi chấm bài hết giờ riêng từng transaction, retry lượt sau khi lỗi |
| ExamRepository | JDBC/JSON mapping, audit và outbox trong transaction nghiệp vụ |

V26 thêm trường nháp/review/revision/title/type/blueprint conversion, start key,
result snapshot, sequence đáp án, binary media và permission. Không sửa V1–V25.
Permission mặc định: Teacher/Admin có test:manage:self và media:upload; Admin thêm
test:manage:any/test:publish/score_conversion:manage; cả ba role có test:take.
Không có ADMIN bypass. API đăng ký không cấp Teacher/Admin; tài khoản quản trị
phải được hệ thống vận hành cấp role trước, không thêm mật khẩu mặc định.

Outbox được ghi, chưa triển khai notification consumer trong EXAM-01. JSONB kết quả
giữ raw count, đáp án/lời giải và điểm theo phiên bản; thay đổi visibility chỉ che media,
không tính lại điểm. Thống kê hiện là lịch sử bài/điểm và tổng theo Part, không suy
đoán loại lỗi hoặc gợi ý học bằng AI. Mobile Expo chưa được tích hợp luồng mới.

## Chạy và kiểm tra

Backend khởi động sẽ chạy V26. Web: `npm run dev`; URL `/teacher/tests`, `/tests`,
`/exam-history`. `BACKEND_PROXY_TARGET` tùy chọn đổi backend cho Vite (mặc định
http://localhost:8080). File binary nằm trong DB nên backup PostgreSQL bao gồm media.

- Backend: `.\mvnw.cmd test` với PostgreSQL test riêng; đặt Hikari max pool 6/min idle 1
  như CI và `APP_EXAMS_EXPIRY_ENABLED=false` khi chạy suite có fixture Clock.
- Web: `npm test`, `npm run lint`, `npm run build`.
- `ExamWorkflowIntegrationTest`: import/rollback, ownership/RBAC, revision, duyệt,
  publication, nhóm Part 7, start idempotency, maxAttempts, expiry, chấm và media.
- `backend/scripts/smoke-exam.mjs`: chỉ dành cho môi trường test tách biệt cố định
  backend 8085, PostgreSQL 127.0.0.1:55439/toeic_exam_workflow. Cần PSQL_BIN.
  Script tạo tài khoản test, cấp role trong DB test, nhập Full 200 câu tổng hợp,
  upload media, kiểm concurrent start/submit và section timing, điểm giả lập và retire.
  Không chạy trên DB sản phẩm; không ghi password/token ra file hoặc console.
- HTTP fixtures/unit test không thay thế kiểm tra trực tiếp UI, nghe audio và responsive.

Maven Wrapper Windows đã sửa cách đọc `.Target` khi `.m2` không phải symlink;
giữ nguyên URL/version Maven trong wrapper properties.
