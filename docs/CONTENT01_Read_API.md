# CONTENT-01 — Đọc học liệu

Ngày quyết định: 2026-11-02. Nhánh: `feat/content-read-api`. Phạm vi: mapping/persistence và hai API GET, kế thừa opaque Bearer session/RBAC hiện có. Không triển khai Teacher CRUD/publish, bookmark, progress, frontend hoặc AI.

## Đối chiếu và quyết định trước khi triển khai

- SRS UC-03 yêu cầu đúng published version/media cho Learner; bảng actor SRS và API §2/§3.2 còn cho Guest đọc published public content. Giữ hai phạm vi riêng: Guest chỉ PUBLIC, tài khoản có quyền được đọc thêm AUTHENTICATED; không mở toàn bộ published cho Guest.
- HLD Phần B ưu tiên, yêu cầu pin LearningContentVersion; LLD §2.2 chỉ hiển thị published và dùng DTO. API §6.2 giới hạn size 100, sort allowlist và không lộ dữ liệu ẩn qua count. Package thực tế `com.toeic.backend` giữ nguyên dù ví dụ LLD dùng `com.toeic.platform`.
- V3/V10 và ERD v1.6 chưa có public/private, chưa có quan hệ content→Part. `learning_contents.title/summary/body` có thể chứa bản đang soạn; chỉ đọc title/summary/body từ version published đang được `current_published_version_id` trỏ tới, đồng thời kiểm tra version thuộc đúng content. Không chọn “version mới nhất” hoặc fallback dữ liệu legacy.
- `content_resources` trong V3 gắn media vào content, không pin version và không chứng minh media đã xuất bản. Không dùng liên kết legacy này cho response. V15 thêm `learning_content_version_resources` để gắn media với version cụ thể, không backfill legacy vì không thể xác định chắc media nào thuộc bản published. Mapping mới không tạo lại bảng nội dung/media hiện có.
- V15 thêm `visibility` NOT NULL cho content và media, CHECK `PUBLIC`, `AUTHENTICATED`, `PRIVATE`, mặc định **PRIVATE** cho cả dữ liệu cũ/mới. Không tự công khai dữ liệu cũ. PUBLIC cho Guest; AUTHENTICATED chỉ khi session ACTIVE hợp lệ có authority DB `content:read`; PRIVATE không được trả qua hai API đọc này, kể cả Teacher/Admin/tác giả. Quyền quản trị/draft riêng vẫn là nhiệm vụ sau.
- V15 seed `content:read` cho LEARNER/TEACHER/ADMIN. Không có role hierarchy hoặc ADMIN bypass; thu hồi grant/khóa tài khoản áp dụng theo session mỗi request như AUTH-03. Public content không đòi permission. Filter vẫn xác thực Bearer nếu được gửi: token sai/hết hạn/thu hồi trả 401, không hạ thành Guest. Các auth endpoint công khai giữ hành vi bỏ qua token cũ như trước.
- Cùng predicate published/visibility phải áp dụng trong list, count và detail. Content không tồn tại hoặc không được phép đều trả **404 CONTENT_NOT_FOUND**. List chỉ đếm bản được phép. Chỉ expose media được gắn với version trả về và có visibility phù hợp; metadata không chứa `storage_url`, transcript, creator hoặc tài nguyên của draft. Private download/signed URL vẫn OPEN, không giả lập khả năng phát media.
- Điều kiện published: cả content và version đều có status PUBLISHED, version thuộc đúng content, archived_at NULL, published_at không NULL và không nằm trong tương lai theo Clock backend. Không fallback khi pointer NULL, pointer trỏ draft hoặc archived. Transaction đọc dùng REPEATABLE_READ để list/count và detail/media cùng snapshot; request đã xác thực trước lúc thu hồi quyền có thể hoàn tất theo thiết kế session.
- Lọc theo `categoryId`, `difficulty` khớp giá trị thực tế; không tự thêm `part` khi schema chưa hỗ trợ. Trường truy vấn không hỗ trợ bị từ chối 400 để tránh làm người gọi hiểu nhầm bộ lọc đã có tác dụng. Chưa freeze enum difficulty trong schema/tài liệu, không áp đặt EASY/MEDIUM/HARD.
- Danh sách mặc định page=0, size=20; page không âm, size 1–100, categoryId dương. Sort một trường `title`, `publishedAt`, `difficulty`, `contentId`, cú pháp `field,asc|desc`, mặc định `publishedAt,desc`; thêm contentId làm khóa phụ ổn định. Input sai trả 400 theo envelope code/message/traceId/details.

## API đã triển khai

`GET /api/v1/contents?categoryId=...&difficulty=...&page=0&size=20&sort=publishedAt,desc`

- `categoryId`: số nguyên dương, khớp category_id; category không tồn tại trả trang rỗng.
- `difficulty`: chuỗi không trống tối đa 20 ký tự, so sánh chính xác với learning_contents.difficulty, không enum cố định. Không hỗ trợ lọc part, status, visibility hoặc userId.
- page/size phải là số nguyên, offset không vượt Integer.MAX_VALUE; size vượt 100 bị từ chối 400, không âm thầm bỏ giới hạn.
- Mỗi tham số chỉ xuất hiện một lần; sort không hỗ trợ nhiều trường hoặc property path tự do. Unknown/duplicate/blank input sai trả 400 VALIDATION_ERROR.
- Response: `{content:[ContentSummary],page,size,totalElements,totalPages}`. Summary gồm contentId, contentVersionId, versionNumber, category `{categoryId,code,name}`, title, summary, difficulty, publishedAt; không trả body, author/creator hay trường legacy.

`GET /api/v1/contents/{id}`

- id là UUID của content, không phải version. UUID sai định dạng trả 400 VALIDATION_ERROR; UUID hợp lệ không tồn tại hoặc bị ẩn đều trả 404 CONTENT_NOT_FOUND, cùng message/envelope.
- Response gồm các trường Summary, thêm body và media `[{resourceId,resourceType,mimeType,fileSize,durationSeconds,displayOrder}]`. Media sắp theo displayOrder rồi resourceId. Chỉ public metadata cho Guest, thêm AUTHENTICATED metadata khi có content:read; PRIVATE không bao giờ trả.
- Không trả JPA entity, storage URL, transcript, lịch sử version, draft hay author. Không có endpoint media download, category catalogue riêng hoặc endpoint ghi mới.
- Bearer được gửi nhưng không hợp lệ trả 401 với WWW-Authenticate: Bearer, kể cả khi URL có public content. Không gửi token thì là Guest. Không có phản hồi 403 tiết lộ sự tồn tại của content ẩn; bảo vệ này không thay đổi 401/403 của identity/admin hiện có.
- Giữ security response headers mặc định ngăn cache để cùng URL không trộn kết quả Guest/tài khoản có quyền.

## Thành phần và trách nhiệm

| Thành phần | Trách nhiệm |
|---|---|
| ContentReadService | Use case đọc list/detail, một transaction read-only, tải đúng version/media được phép và chuyển thành DTO trước khi đóng transaction. |
| ContentAccessPolicy | Đọc actor/authority tin cậy từ SecurityContext, chỉ chấp nhận SessionPrincipal đã xác thực có content:read; không nhận cờ quyền từ client. |
| ContentQuery | Parse/validate tham số, sort allowlist và tạo PageRequest có khóa phụ ổn định. |
| ContentRepository | Query content/version/category có predicate published/visibility dùng chung cho list/count/detail; fetch to-one để phân trang không nhân bản dòng. |
| ContentMediaRepository | Chỉ đọc media được pin vào version đã cho phép, áp dụng visibility và thứ tự. |
| ContentController / ContentExceptionHandler | Hai GET, DTO và lỗi 400/404 đúng envelope. |

Mappings: ContentCategory, LearningContent, LearningContentVersion, MediaResource tái sử dụng bảng V3/V10; ContentVersionResource map bảng V15 bằng composite embedded ID. Không có cascade write hoặc repository CRUD công khai. Không gọi repository identity từ content; dùng principal application hiện hữu và Clock chung.

AuthService, SessionAuthenticationService, PermissionService, RegistrationService, PasswordResetService và OtpMailService giữ trách nhiệm identity theo AUTH-03 và [policy V25](Password_Recovery.md): đăng ký ACTIVE không OTP, OTP chỉ dùng khôi phục mật khẩu. Security config chỉ thêm hai GET permitAll nhưng không đưa vào danh sách bỏ qua Bearer; quyền dữ liệu do service/query quyết định. AuthFlowIntegrationTest cập nhật expected permission list vì V15 thêm content:read, không bỏ bớt test cũ.

## Kiểm thử

Fixture SQL tại `backend/src/test/resources/fixtures/content-read.sql`, dọn qua content-read-cleanup.sql sau mỗi test, được commit/cleanup trong transaction riêng để kiểm tra request dùng session và quyền thật từ DB. UUID, category ID và dữ liệu có định danh cố định; không phụ thuộc dữ liệu học liệu thật. Chỉ chạy integration test trên DB test riêng, không dùng production.

ContentReadIntegrationTest chạy MockMvc + Spring Security + PostgreSQL/Flyway + JPA thật, Open Session in View tắt. Kiểm tra public/member/private, nhiều role kể cả tác giả ADMIN không đọc PRIVATE, grant/revoke trên cùng token, khóa/thu hồi phiên, draft/archived/null pointer/pointer draft/future/null published_at, không fallback, media draft/legacy/old-version/private, filters/count/pagination/sort/input sai và gọi service không có actor. Các test identity/RBAC cũ được chạy cùng toàn bộ backend.

Kết quả bàn giao 2026-11-02 trên PostgreSQL test 17.11 tách biệt (`127.0.0.1:55433/toeic_auth_rbac_test`, override datasource bằng biến môi trường, không đổi application.properties):

- `.\mvnw.cmd test -Dmaven.resources.overwrite=true -Dtest=ContentReadIntegrationTest`: **41 tests đạt**, 0 failures/errors/skipped; Flyway nâng DB test V14→V15 thành công.
- `.\mvnw.cmd test`: **BUILD SUCCESS — 92 tests đạt**, 0 failures/errors/skipped, gồm 41 content + 51 test hiện hữu. Flyway validate đủ 15 migration. Log `backend/target/content-read-tests.log`, Surefire `backend/target/surefire-reports` là build artifacts, không thuộc thay đổi source.
- 51 test hiện hữu = 48 test tại thời điểm bàn giao AUTH-03 + 3 VocabularyServiceTest đã tích hợp từ commit `00b6eb8` qua PR #14: create_shouldSaveVocabularySuccessfully, getAll_shouldReturnUserVocabularies, getAll_shouldSearchByKeyword. Ba test này không do CONTENT-01 thêm hoặc sửa.
- Lần thử đầu bị Flyway chặn do build output target/classes thiếu bản sao V14 mặc dù source V14 còn nguyên; buộc Maven copy lại resources đã xử lý. Không sửa migration cũ, không repair/tắt validation.
- `git diff --check` và kiểm tra whitespace các file mới thuộc nhiệm vụ đạt. V1–V14, frontend và AI không sửa. File chưa tracked `tatus` có sẵn được giữ nguyên, không thuộc CONTENT-01.
- Review trước commit: đã fetch `origin/develop` tới `2c3573965a0cc9e3e03aa5e56c21fdef82a37a8a`; tree không khác base nhánh hiện tại và chỉ có migration V1–V14, chưa có V15 trùng số/tên. `content:read` được V15 seed và cấp qua role_permissions cho LEARNER/TEACHER/ADMIN; không có grant trực tiếp theo user hoặc ADMIN bypass.

## Phạm vi chưa hoàn tất

Media download/upload, cơ chế publish ghi snapshot, thay đổi visibility có audit, gán học liệu theo Part, Teacher CRUD, bookmark, progress, kho học liệu thật Part 1–7 chưa được triển khai. Fixture tái lập chỉ chứng minh behavior của API và schema, không phải bộ học liệu sản phẩm. Các quyết định reviewer/publisher, self-approval và object storage trong tài liệu vẫn OPEN.
