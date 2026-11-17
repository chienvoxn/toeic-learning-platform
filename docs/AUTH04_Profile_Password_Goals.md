# AUTH-04 — Hồ sơ, mật khẩu và mục tiêu TOEIC

Ngày đối chiếu: 2026-11-03. Nhánh `feat/auth-profile`.

**Bổ sung TV1 2026-11-12:** backend/contract dưới đây được tái sử dụng; đã nối và kiểm
UI hồ sơ/mục tiêu, xóa mục tiêu, đổi mật khẩu cũ–mới/thu hồi hai phiên. UI lưu không đổi
không gửi PATCH rỗng. [AC, file/hunk và bằng chứng Sprint 2](TV1_Sprint2_AUTH04_ADMIN01_QA02.md).
Các đoạn “chưa frontend” ở cuối là phạm vi bàn giao backend ngày 2026-11-03.

## Nguồn và giới hạn

Đã đối chiếu SRS UC-01/02, API Specification §3.1, ERD `users`/`learning_profiles`, LLD §2.1, HLD module map và ADR-017 với code/Flyway. Không tìm thấy backlog hoặc Acceptance Criteria riêng cho AUTH-04 trong repository; không thể khẳng định đã đối chiếu AC ngoài repo.

API Specification trước đây chỉ đề xuất `PATCH /users/me` với DTO chưa định nghĩa và lỗi 422. Luồng identity hiện hữu dùng 400 `VALIDATION_ERROR` cho validation; phần triển khai này dùng cùng envelope `code/message/traceId/details`, không echo payload. Không lấy contract từ frontend mock.

## Contract và quyền

- Kế thừa đăng ký–OTP, login/logout, opaque Bearer session và `GET /api/v1/users/me` hiện hữu.
- `GET /api/v1/users/me`: kế thừa endpoint, thêm `targetScore` nullable vào UserProfile. Khi chưa có learning_profiles hoặc chưa đặt mục tiêu, trả null, không tự tạo hàng/default điểm. Login cũng trả UserProfile với mục tiêu hiện tại.
- `PATCH /api/v1/users/me`: allowlist `fullName`, `targetScore`. Phải có ít nhất một trường; chỉ cập nhật actor từ `SessionPrincipal`. Bỏ qua một trường thì giữ nguyên; fullName không null/trống, tối đa 150 ký tự trước khi strip, lưu tên đã strip, thống nhất đăng ký. Hai trường cùng payload được ghi trong một transaction.
- DTO allowlist từ chối trường lạ, kể cả email, avatarUrl, userId, role/permission, status, passwordHash và timestamp hệ thống. Không cập nhật một phần nếu payload lỗi.
- API và service yêu cầu cả `user:update:self` và `user:read:self` vì response trả UserProfile; không suy diễn write ngầm bao gồm read. Service kiểm tra principal đúng kiểu, ownership, tài khoản ACTIVE và khóa hàng user khi ghi. Không có ADMIN bypass.
- V17 chỉ thêm `user:update:self` và `user:password:change:self` cho LEARNER/TEACHER/ADMIN. Đã fetch và kiểm tra `origin/develop` tại `e3ad2b1`, migration cao nhất V16, không trùng V17. Không sửa migration đã áp dụng.
- `POST /api/v1/users/me/password`: body chính xác `{currentPassword,newPassword}` dạng chuỗi; cần `user:password:change:self` tại API/service và ownership. Không cần quyền đọc hồ sơ vì response thành công 204 không có body/token mới. Thiếu/sai phiên trả 401; thiếu permission trả 403; payload/validation sai trả 400 VALIDATION_ERROR; mật khẩu hiện tại sai trả 422 CURRENT_PASSWORD_INVALID. Lỗi không echo bí mật; vẫn có code/message/traceId/details.

## Quyết định đã chốt ngày 2026-11-03

- Người dùng chốt: mục tiêu TOEIC **Listening + Reading tổng**, số nguyên **10–990, bội số 5**. Đây là validation mục tiêu của sản phẩm, không phải quy tắc tính/quy đổi điểm thi. Không suy ra từ frontend mock. JSON phải dùng số nguyên (không ép kiểu chuỗi, boolean hoặc số thập phân, kể cả 750.0); null xóa mục tiêu, bỏ qua giữ nguyên. Chưa có targetDate.
- Tái sử dụng `learning_profiles.target_score` integer nullable; không thêm bảng/cột hoặc backfill điểm. HLD gom learning_profiles vào analytics, LLD §2.1 liệt kê tại identity, analytics chưa có mapping/logic. Mapping identity chỉ gồm khóa, target_score và updated_at; không map/ghi estimated_score, listening_level, reading_level. Khi xóa mục tiêu vẫn giữ hàng hiện hữu và dữ liệu analytics; null khi chưa có hàng không tạo hàng trống.
- Người dùng chốt: đổi mật khẩu thành công phải thu hồi **mọi phiên**, kể cả phiên đang dùng; cập nhật hash và revocation trong **cùng transaction**, trả **204**, không cấp token mới, yêu cầu đăng nhập lại. Sai currentPassword/validation không thay đổi hash hoặc phiên. Không triển khai quên/reset mật khẩu qua email.
- Mật khẩu mới theo đăng ký: không trống, 8–72 ký tự Java và tối đa 72 byte UTF-8. PasswordPolicy dùng chung giới hạn BCrypt cho đăng ký/login/đổi mật khẩu; không strip mật khẩu. CurrentPassword không trống, tối đa 72 ký tự/byte, không áp min mới cho mật khẩu cũ. Command/request toString được che; production không log payload/password/token/hash.

## Transaction và cạnh tranh

- Login khóa `users` bằng SELECT FOR UPDATE **trước khi đọc/so khớp hash**, giữ đến khi tạo session, cập nhật login và commit. Đổi mật khẩu khóa cùng hàng trước khi xác minh currentPassword. Không dùng mutex JVM, bảo vệ được giữa các instance.
- Nếu login thắng khóa: session tạo bằng mật khẩu cũ commit trước; đổi mật khẩu sau đó thu hồi cả session đó. Nếu đổi mật khẩu thắng khóa: login đợi rồi đọc hash mới, mật khẩu cũ bị từ chối và không tạo session.
- Sau khi lấy khóa, đổi mật khẩu kiểm lại ACTIVE, ownership và hiệu lực session hiện tại trong DB để request đã xác thực nhưng bị thu hồi trong lúc chờ không đổi mật khẩu lần nữa.
- Bulk revoke áp dụng mọi session chưa thu hồi của user, kể cả hết hạn; không tác động user khác hoặc ghi đè timestamp phiên đã thu hồi. Timestamp revocation không sớm hơn created_at để phù hợp CHECK V13 khi đồng hồ các node lệch nhau.
- Flush hash và bulk revoke nằm trong cùng transaction. Lỗi ghi revocation làm rollback hash. Cập nhật hồ sơ/mục tiêu cũng dùng khóa user để tránh tạo trùng learning_profiles; GET me dùng snapshot REPEATABLE_READ để tên/mục tiêu/role không bị trộn giữa các commit.

## Trách nhiệm

| Thành phần | Trách nhiệm |
|---|---|
| UserController / UpdateProfileRequest / ChangePasswordRequest | Lấy actor từ principal, đọc DTO allowlist và chuyển command; trả UserProfile an toàn hoặc 204 không body. |
| UserProfileService | Đọc hồ sơ/mục tiêu, kiểm tra quyền/ownership và điều phối PATCH trong transaction. |
| PasswordChangeService | Kiểm phiên sau khóa, xác minh mật khẩu cũ, băm mật khẩu mới và thu hồi toàn bộ phiên trong một transaction. |
| AuthService | Kế thừa login/logout; login lấy khóa trước khi xác minh để không tạo phiên hợp lệ bằng credential cũ sau đổi mật khẩu. |
| ProfileUpdate / PasswordChange / User / LearningProfile | Validate tên/mục tiêu/mật khẩu, cập nhật dữ liệu theo use case; không nhận trường hệ thống tùy ý. |
| SelfProfileAccess | Chỉ chấp nhận actor từ SessionPrincipal đã xác thực cho các thao tác mới. |
| UserRepository / LearningProfileRepository / AuthSessionRepository | Khóa user chung login/ghi; tái sử dụng bảng mục tiêu; bulk revoke đúng user, không chứa token thô. |
| IdentitySecurityConfig / AuthExceptionHandler | Bảo vệ endpoint, trả lỗi 400/401/403 theo identity hiện hữu. |

## Kiểm thử và ngoài phạm vi

Test integration chạy với PostgreSQL thật, dữ liệu tài khoản được tạo và dọn theo từng ca; không dùng tài khoản sản xuất. Kết quả chạy phải lấy từ Surefire/log của lần bàn giao, không suy ra từ số test dự kiến.

Lần chạy độc lập trước khi chốt chính sách đạt 251 test (212 cũ + 39 hồ sơ). Đây là kết quả lịch sử, chưa bao gồm mục tiêu/đổi mật khẩu. Kết quả hoàn tất sau quyết định phải đối chiếu lần chạy mới.

Test bổ sung kiểm tra biên/bội số 5/kiểu JSON của mục tiêu, partial/null/cập nhật chung, bảo toàn trường analytics; đúng/sai mật khẩu, 72 byte UTF-8, hash mới/login cũ-mới, toàn bộ phiên và isolation user. Kiểm nguyên tử bằng rollback transaction và ép lỗi ghi revocation bằng CHECK tạm chỉ áp vào fixture rồi dọn trong finally. Hai ca cạnh tranh dùng latch cố định thứ tự transaction và quan sát pg_blocking_pids để xác nhận thao tác thứ hai thực sự đợi khóa PostgreSQL; không dựa vào sleep để giả định cạnh tranh. Constraint kiểm thử không nằm trong migration/production.

**Kết quả hoàn tất ngày 2026-11-03:**

- `.\mvnw.cmd test -Dtest=ProfileFlowIntegrationTest,PasswordChangeIntegrationTest`: 95 test đạt, 0 failures/errors/skipped.
- Sau thay đổi cuối về guard principal và che log lỗi DB chủ động trong test âm, chạy toàn bộ `.\mvnw.cmd test`: **307 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, PostgreSQL 17.11/Java 21. Gồm 212 test hiện hữu + 62 hồ sơ/mục tiêu + 33 đổi mật khẩu, tăng 56 so với lần 251 test trước khi chốt mục tiêu/mật khẩu.
- Flyway xác thực 17 migration, không sửa V1–V16 hoặc nội dung V17 đã áp dụng trên DB test; không cần migration thêm cho mục tiêu/mật khẩu.
- `git diff --check`, review file mới và scan secret/log/scope đạt. Không sửa frontend, AI, content, questionbank hoặc vocabulary. File tatus giữ nguyên và được loại khỏi phạm vi commit.
- Review bàn giao: fetch origin, origin/develop vẫn ở e3ad2b1 và chỉ có migration đến V16; V17 không trùng. Code/test không đổi sau lần chạy 307 test đạt nên không chạy lại Maven chỉ để commit.

Chưa triển khai đổi email, upload/avatar update, quên/reset mật khẩu qua email, Teacher/Admin CRUD, frontend hoặc các module nghiệp vụ khác.
