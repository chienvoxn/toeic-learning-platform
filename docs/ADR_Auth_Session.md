# ADR-017 — Bearer session cho identity

Ngày: 2026-11-01. Phạm vi: đăng nhập, xác thực, đăng xuất và hồ sơ hiện tại trên `feat/auth-session`.

## Quyết định và mâu thuẫn tài liệu

HLD §9 cho phép JWT hoặc session; API/LLD trước đây đề xuất JWT nhưng refresh token/revocation vẫn OPEN. V1–V12 chưa có nơi lưu phiên. Triển khai chọn **opaque Bearer token + PostgreSQL session**, phù hợp modular monolith cần logout ngay. Quyết định này thay thế đề xuất JWT trong luồng identity hiện tại; không chốt các mục OPEN khác.

- Token: 32 byte từ SecureRandom, Base64 URL không padding (43 ký tự); DB chỉ lưu SHA-256 hex. Không log token/password/hash. Token gốc chỉ trả khi login.
- Thời hạn tuyệt đối mặc định 900 giây, cấu hình `AUTH_SESSION_TTL_SECONDS` phải dương. Không tự gia hạn, không có refresh token hay cookie đăng nhập.
- Chỉ ACTIVE được login; BCrypt và chuẩn hóa email tái sử dụng từ đăng ký. Giới hạn mật khẩu 72 byte UTF-8. Email không tồn tại vẫn kiểm tra bằng hash giả để giảm chênh lệch thời gian. Sai thông tin hoặc tài khoản không ACTIVE cùng trả 401 INVALID_CREDENTIALS.
- Mỗi request đọc phiên, trạng thái tài khoản và role từ DB; actor lấy từ principal. Khóa tài khoản chặn token; thay đổi role có hiệu lực từ request kế tiếp. Mở khóa lại cho phép dùng phiên chưa hết hạn/chưa thu hồi.
- AUTH-03 bổ sung permission từ DB mỗi request, guard hồ sơ và ownership tại service theo [ma trận RBAC](AUTH03_RBAC.md). V14 cấp user:read:self cho LEARNER/TEACHER/ADMIN; thu hồi quyền áp dụng request tiếp theo sau khi commit, không cần login lại. Quyền publish/review và API quản trị chưa triển khai.
- User dùng DynamicUpdate: login chỉ ghi lastLoginAt/updatedAt, tránh ghi lại status/password cũ nếu có thay đổi đồng thời sau khi đọc tài khoản.
- AUTH-04 chốt 2026-11-03: [đổi mật khẩu](AUTH04_Profile_Password_Goals.md) thu hồi mọi phiên, kể cả phiên hiện tại, trong cùng transaction với hash; trả 204 không token mới. Login và đổi mật khẩu khóa cùng hàng user trước kiểm tra hash, tránh tạo phiên hợp lệ bằng mật khẩu cũ sau đổi. Logout thường vẫn chỉ thu hồi phiên hiện tại. Chính sách này không thêm API logout-all/reset password độc lập.
- Logout ghi revoked_at cho phiên đang dùng trước khi trả 204; phiên khác vẫn hoạt động. Phát lại token/logout lại trả 401. Request đã được xác thực trước lúc logout có thể hoàn tất.
- HTTP session, HTTP Basic và form login bị tắt. CSRF tắt vì credential chỉ nhận qua Authorization, không tự gửi bằng cookie. Không mở rộng CORS. Public POST theo policy V25: register, login, forgot-password, reset-password, google/challenge, google; public GET: hai health endpoint hiện hữu. Public auth bỏ qua bearer cũ để vẫn đăng nhập hoặc khôi phục mật khẩu được.
- CONTENT-01 mở thêm GET /api/v1/contents và /api/v1/contents/{id} theo [visibility và permission content:read](CONTENT01_Read_API.md). Hai GET này vẫn xác thực Bearer nếu được gửi, không bỏ qua token sai; không gửi token chỉ đọc PUBLIC. Các endpoint authoring/publish không được mở.
- `/api/v1/admin/**` yêu cầu ADMIN; route khác yêu cầu xác thực trừ allowlist và ERROR dispatch nội bộ. Permission/ownership nghiệp vụ khác vẫn cần triển khai riêng.
- Lỗi auth có code/message/traceId/details; 401 có WWW-Authenticate: Bearer. Response nhạy cảm ngăn cache. Đăng ký ACTIVE không OTP; contract recovery theo [Password_Recovery.md](Password_Recovery.md). Tắt SQL logging mặc định và auto user mặc định của Spring Security.

## Trách nhiệm thành phần

| Thành phần | Trách nhiệm |
|---|---|
| AuthService | Kiểm tra đăng nhập/ACTIVE, tạo phiên và cập nhật lastLoginAt trong transaction; thu hồi phiên khi logout. |
| SessionAuthenticationService | Kiểm tra token hash, thời hạn, thu hồi, trạng thái; lấy role hiện tại và tạo principal không chứa bí mật. |
| UserProfileService | Đọc tài khoản bằng userId từ principal, trả DTO chỉ gồm trường được công khai. |
| SessionToken | Sinh token, kiểm tra định dạng và băm SHA-256; không lưu token gốc. |
| BearerSessionFilter / IdentitySecurityConfig | Đọc Authorization, tạo SecurityContext, bảo vệ route và trả 401/403 JSON. Filter chỉ gắn vào SecurityFilterChain. |
| RegistrationService | Tạo tài khoản ACTIVE, cấp role LEARNER; không gửi OTP hoặc tự tạo phiên đăng nhập. |
| PasswordResetService / OtpMailService | Phát/gửi và kiểm tra OTP khôi phục mật khẩu; đổi mật khẩu và thu hồi phiên theo [policy V25](Password_Recovery.md). |

## Schema và đánh đổi

V13 thêm auth_sessions(session_id UUID PK, user_id UUID FK users, token_hash VARCHAR(64) UNIQUE, created_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ NULL). CHECK expiry > creation, revocation >= creation; index user và expiry. Không sửa V1–V12. DBML v1.6 đính kèm trong ERD giữ làm lịch sử, chưa bao gồm V13.

Mỗi request cần DB, đổi lại phiên tồn tại qua restart và logout/quyền được kiểm tra chung trên mọi instance, không cần signing key hoặc denylist JWT. Refresh token vẫn OPEN. Logout mọi thiết bị, tự động dọn phiên hết hạn, giới hạn phiên đồng thời và rate limit login chưa nằm trong phạm vi này.

## Kiểm thử

AuthFlowIntegrationTest chạy qua MockMvc + Spring Security + JPA + PostgreSQL/Flyway thật. Clock điều khiển kiểm tra expiry chính xác, không sleep. Bao phủ login đúng/sai, email không tồn tại, PENDING/LOCKED/DISABLED, token sai/hết hạn/thu hồi, nhiều phiên, không lộ bí mật, thay đổi role/trạng thái, 401/403 và đăng ký ACTIVE không OTP → login. Admin probe chỉ tồn tại trong test, không thêm API quản trị vào sản phẩm.

Chạy toàn bộ backend bằng `.\mvnw.cmd test` với datasource PostgreSQL test đã cấu hình. Không dùng DB production cho integration test.
