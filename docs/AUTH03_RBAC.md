# AUTH-03 — RBAC và quyền chi tiết (Sprint 1)

Ngày: 2026-11-01. Nhánh: `feat/auth-rbac`. Phạm vi: API identity hiện có, kế thừa [ADR-017](ADR_Auth_Session.md). Package giữ `com.toeic.backend`, modular monolith với các lớp api/application/domain/infrastructure.

**Cập nhật CONTENT-01 (2026-11-02):** [Hai GET đọc học liệu](CONTENT01_Read_API.md) đã được triển khai trên nền này. V15 cấp thêm content:read cho ba role, Guest chỉ đọc published PUBLIC, session có quyền đọc thêm AUTHENTICATED; PRIVATE vẫn ẩn. Ma trận bên dưới mô tả phạm vi tại thời điểm AUTH-03; publish/review và API quản trị tiếp tục chưa triển khai.

## Đối chiếu và quyết định triển khai

- SRS §2, UC-02, BR-25, NFR-01 yêu cầu RBAC, tải quyền và không tin role từ client; UC-15 yêu cầu thay đổi quyền theo chính sách phiên. HLD §9/§10 yêu cầu kiểm tra ở API/service và ownership; Phần B ưu tiên Phần A. LLD §2.1 yêu cầu chỉ admin gán role. API §2 liệt kê các permission nghiệp vụ dưới nhãn DESIGN và checklist vẫn OPEN ma trận teacher/reviewer/admin, self-approval.
- SRS UC-15 ghi actor Teacher/Admin, khác LLD “role assignment chỉ admin” và route admin hiện có. Giữ giới hạn ADMIN cho `/api/v1/admin/**`; không mở quyền gán role cho Teacher. Khi triển khai API quản trị cần chốt permission, audit và chính sách tự thay đổi đặc quyền trước khi mở use case.
- Tại thời điểm AUTH-03, public chỉ có auth và health. CONTENT-01 sau đó mở hai GET content theo visibility, không mở wildcard authoring/publish; xem cập nhật phía trên.
- LLD có ví dụ package `com.toeic.platform`, khác code và AGENTS.md. Tiếp tục dùng `com.toeic.backend`.
- ERD v1.6/V2 có `permissions` và `role_permissions`; trước AUTH-03 chưa có entity mapping và chưa seed permission. V11 đã seed LEARNER/TEACHER/ADMIN. V13 lưu opaque Bearer session. Giữ V1–V13; V14 chỉ thêm dữ liệu permission cho use case đã tồn tại, không đổi cấu trúc bảng.
- Không tự chốt các mục OPEN về reviewer khác tác giả, Teacher được publish mặc định hay Admin kế thừa quyền Teacher/Learner. Không có role hierarchy hoặc ADMIN bypass permission. Một tài khoản có thể có nhiều role; quyền hiệu lực là hợp của permission được cấp qua các role hiện tại.

## Ma trận đang thực thi

Guest là request chưa xác thực, không phải role được lưu vào DB. Dấu “Có” cho hồ sơ giả định tài khoản ACTIVE, session còn hiệu lực và grant V14 còn tồn tại.

| API / quyền | Guest | Learner | Teacher | Admin |
|---|---|---|---|---|
| POST register/login/forgot-password/reset-password/google/challenge/google | Có | Có | Có | Có |
| GET hai health endpoint hiện hữu | Có | Có | Có | Có |
| GET `/api/v1/users/me` (`user:read:self`, chỉ chính mình) | 401 | Có | Có | Có |
| POST `/api/v1/auth/logout` (chỉ phiên hiện tại) | 401 | Có | Có | Có |
| Guard `/api/v1/admin/**` | 401 | 403 | 403 | Qua guard role; chưa có API nghiệp vụ |
| Tự gán role/permission qua register/login/header/query | Không | Không | Không | Không có API gán quyền trong phạm vi này |

V14 cấp `user:read:self` cho ba role. Thu hồi grant ở `role_permissions` hoặc xóa role cuối cùng cấp quyền ở `user_roles` khiến `/users/me` trả 403 từ request tiếp theo, kể cả ADMIN. Tài khoản ACTIVE không role vẫn có thể login/logout nhưng không đọc hồ sơ qua `/users/me`. Logout không yêu cầu permission nghiệp vụ để người dùng luôn có thể thu hồi phiên của mình.

Payload register/login chỉ bind các trường DTO đã khai báo; role/permission thừa không được áp dụng. RegistrationService luôn lấy role LEARNER từ DB, không nhận role/permission từ client. `/users/me` lấy userId từ principal; UserProfileService còn kiểm tra userId trùng actor khi gọi service. AuthService kiểm tra sessionId của actor trước khi logout.

## Ma trận nghiệp vụ chưa triển khai

Đây là phạm vi tài liệu, không phải quyền đã seed hoặc API đã kiểm thử. AUTH-03 **không** cấp mặc định các permission bên dưới.

| Phạm vi / mã DESIGN | Guest | Learner | Teacher | Admin | Còn phải kiểm chứng |
|---|---|---|---|---|---|
| Published public content | Dự kiến đọc public | Đọc theo quyền | Đọc theo quyền | Đọc theo quyền | Chưa có API/public policy |
| Học, thi, SRS, listening, analytics, AI, notifications cá nhân | Không | Dự kiến tài nguyên cá nhân | Chưa chốt kế thừa | Chưa chốt kế thừa | API, permission và ownership từng tài nguyên |
| `content:write` | Không | Không | Dự kiến theo grant | Chưa chốt quyền tác nghiệp | API, ownership draft |
| `content:publish`, `question:review`, `question:publish`, `test:publish` | Không | Không | Theo grant, mặc định OPEN | Theo grant, mặc định OPEN | Workflow, separation of duties, ma trận reviewer/publisher |
| `user:manage` | Không | Không | Không theo LLD | Dự kiến theo grant | API quản trị, audit, ngăn tự nâng quyền |
| `score_conversion:manage`, `ai:usage:read`, `audit:read`, system settings | Không | Không | Không | Dự kiến theo grant | API và policy chi tiết; mã settings chưa chốt |

Khi thêm API nghiệp vụ phải thêm guard role/permission và ownership tại use case, seed được review, test endpoint thật và cập nhật ma trận. Guard ADMIN hiện tại không thay cho permission của từng API tương lai. Không thêm endpoint production giả để chứng minh phân quyền.

## Persistence và xác thực từng request

- `Permission` map `permissions(permission_id, code, description)`; `Role.permissions` map ManyToMany qua `role_permissions(role_id, permission_id)`, dùng khóa ghép có sẵn. Không cascade ghi/xóa permission qua role. `User.roles`/`user_roles` được tái sử dụng.
- `AuthSessionRepository.findByTokenHash` dùng entity graph lấy user, roles và permissions trong transaction xác thực; không phụ thuộc Open Session in View.
- `SessionAuthenticationService` kiểm tra hash, expiry, revoked_at và ACTIVE trước khi tạo principal. `PermissionService` tổng hợp, loại trùng, sắp xếp quyền từ DB; snapshot principal chỉ có ID, role/permission và dùng list bất biến. Không cache quyền giữa request hoặc lưu quyền trong token.
- `BearerSessionFilter` tạo authorities `ROLE_<name>` và permission code. Permission bắt đầu bằng `ROLE_` không được chuyển thành authority để tránh giả mạo namespace role. Không đọc role/permission từ header, query hay body.
- API `/users/me` kiểm tra authority `user:read:self`; `@EnableMethodSecurity` và `@PreAuthorize` bảo vệ UserProfileService cùng ownership và AuthService.logout cùng session ownership.
- Chưa xác thực: 401 (có `WWW-Authenticate: Bearer`); phiên hợp lệ thiếu quyền: 403 FORBIDDEN (không có header challenge). Cả hai giữ envelope `code/message/traceId/details`. Đăng ký ACTIVE không OTP; contract recovery theo [Password_Recovery.md](Password_Recovery.md).
- Thu hồi role/permission, khóa tài khoản, thu hồi phiên có hiệu lực ở request sau khi transaction thay đổi đã commit. Khóa/PENDING/DISABLED trả 401 INVALID_TOKEN cho phiên còn thời hạn; expiry giữ TOKEN_EXPIRED theo ADR-017. Request đã xác thực trước thời điểm thay đổi có thể hoàn tất. Mở khóa lại cho phép dùng session còn hạn/chưa revoked theo thiết kế hiện tại.

## Trách nhiệm từng service

| Service | Trách nhiệm |
|---|---|
| RegistrationService | Chuẩn hóa email, băm password, tạo user ACTIVE và cấp duy nhất role mặc định LEARNER; không tạo OTP, gửi thư hoặc tự đăng nhập. |
| PasswordResetService | Phát/kiểm tra OTP khôi phục, kiểm soát hạn dùng, số lần sai và cooldown; đổi mật khẩu và thu hồi phiên, không kích hoạt hoặc mở khóa tài khoản. |
| OtpMailService | Chỉ gửi email OTP khôi phục mật khẩu qua SMTP; không quản lý quyền. |
| AuthService | Kiểm tra thông tin đăng nhập và ACTIVE, tạo Bearer session, ghi lastLoginAt; logout chỉ session của actor đã xác thực. |
| SessionAuthenticationService | Xác thực session và trạng thái user, đọc role/permission hiện tại từ DB và tạo principal cho mỗi request. |
| PermissionService | Tính hợp quyền từ các role DB, loại trùng và sắp xếp; không tự cấp quyền hoặc mặc định ưu tiên ADMIN. |
| UserProfileService | Yêu cầu permission đọc hồ sơ và actor trùng userId, kiểm tra ACTIVE và trả DTO không chứa bí mật. |

## Kiểm thử và giới hạn

`AuthFlowIntegrationTest` chạy MockMvc + Security + JPA + PostgreSQL/Flyway thật với Open Session in View tắt. Các test bổ sung kiểm tra seed ba role, grant/revoke trên cùng token, hợp quyền nhiều role, thu hồi role cuối cùng, 401/403, giả mạo namespace role, ownership tại service/profile/logout và payload tự nâng quyền. Test cũ giữ kiểm tra đăng ký ACTIVE không OTP → login, token sai/hết hạn/thu hồi, nhiều phiên và đổi role. Trạng thái PENDING/LOCKED/DISABLED được kiểm tra cả login lẫn request dùng token đã cấp.

Admin probe đã có từ AUTH-02 chỉ nằm trong test và chỉ chứng minh guard role. Không coi probe này hoặc permission test của hồ sơ là bằng chứng đã kiểm thử publish, admin role assignment, audit, RAG authorization hay ownership của attempts/decks/recordings/conversations. Các API đó chưa được triển khai. Kết quả chạy test cụ thể phải đối chiếu báo cáo bàn giao và Surefire của lần chạy hiện tại.

Kết quả lần chạy bàn giao 2026-11-01: `.\mvnw.cmd test` tại `backend` **BUILD SUCCESS**, 48 tests, 0 failures, 0 errors, 0 skipped, gồm toàn bộ test cũ. Chạy trên PostgreSQL 17.11 tách biệt tại `127.0.0.1:55433/toeic_auth_rbac_test`, override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` trong process; không đổi application.properties. Flyway đã chạy V1–V14 trên DB test mới và validate đủ 14 migration ở lần chạy cuối. Lần thử bằng cấu hình datasource mặc định trước đó thất bại vì PostgreSQL từ chối xác thực user postgres; kết quả đạt nói trên dùng DB test tách biệt. Log ở `backend/target/auth-rbac-tests.log`, báo cáo từng lớp ở `backend/target/surefire-reports` (build artifacts, không commit).
