# Web Identity integration

## Đồng bộ remote ngày 2026-11-11 — contract hiện hành

Commit `695489908ca272d77289895cb5bfd21c46c04c73` lưu 36 file web-auth local đã review trước tích hợp; đây là bản bảo toàn, **không phải bằng chứng tương thích UI/contract remote mới**. Chỉ bỏ hai dòng whitespace cuối trong AuthServiceTest khi lưu. Commit EXAM-01 `279ae7860260c9faae30d79cc60421bed05bd04e` giữ nguyên và là tổ tiên của lần tích hợp. Có thể đọc bản trước tích hợp bằng `git show 6954899:<path>`.

Đã đọc 15 commit remote còn thiếu rồi merge `origin/feat/web-auth-integration` (`a6075dd`), không rebase/force push. Các thay đổi của nhóm bao gồm landing/guest practice, dashboard/content/vocabulary thật, Google OIDC và policy đăng ký ACTIVE/reset password. Không nhập nhánh bulk-import/exam chưa merge.

Quyết định kết hợp:

| Phần | Kết quả |
|---|---|
| Route/UI | Giữ landing, dashboard mới, catalog/vocabulary có guard và guest Reading sample công khai của nhóm. Sample lưu browser, chưa nối EXAM-01. |
| Đăng ký | POST `/api/v1/auth/register`, allowlist email/password/fullName, trả 201 ACTIVE, không token và không OTP; về login. `/verify-email` cũ chuyển login. |
| Google | Giữ GoogleSignIn, challenge cookie/nonce, password proof khi liên kết. Repository không gửi app Bearer vào challenge/login. User giữ `localLoginEnabled`; tài khoản Google-only không có form đổi mật khẩu. |
| Recovery | POST `/auth/forgot-password` và `/auth/reset-password`; giữ UI mới. OTP chỉ cho reset, không khôi phục endpoint xác minh đăng ký cũ. |
| Phiên | Một AuthProvider/context; canonical storage `toeic.remote.session` chứa accessToken/tokenType/expiresAt. Login/Google login gọi GET me; không dùng cached demo profile. Token legacy bị dọn khi tạo phiên. Người dùng có phiên legacy cần login lại. |
| Khôi phục/lỗi | Giữ expiry timer/focus, storage giữa tab, generation guard cho phản hồi cũ, token matching khi 401/logout. Restore lỗi mạng giữ token và cho retry; không fallback demo. |
| HTTP | Kết hợp chuẩn base `/api/v1`, đường dẫn `/api/health`, anonymous `token:null` hoặc `auth:false`, timeout/cancel và NETWORK_ERROR. 403/422 không xóa phiên. |
| Profile/password | Giữ layout của nhóm, PATCH chỉ gửi field thay đổi và allowlist; null xóa target, omitted giữ target. Kiểm 10–990/bội 5 và UTF-8 password. Đổi mật khẩu ở Settings, sai current password giữ phiên; logout lỗi được báo rõ. |
| Mode | `VITE_AUTH_MODE=remote` mặc định, độc lập `VITE_USE_MOCK=false`. Bật mock module không tự đổi auth sang fake. Muốn auth demo phải chọn `VITE_AUTH_MODE=mock`. |
| Backend | Giữ SessionIssuer/Google/reset từ remote; cập nhật constructor của 10 AuthServiceTest đã lưu sang SessionIssuer thật. Giữ union endpoint permission và source guard EXAM-01. Không thêm nghiệp vụ backend mới. |

AGENTS.md local vẫn yêu cầu giữ OTP đăng ký, nhưng backend/docs/migration V25 của nhóm đã thay policy. Theo yêu cầu tích hợp hành vi auth hiện hành, lần này theo V25; không sửa AGENTS.md local và không tự đưa OTP cũ trở lại. Toàn bộ phần dưới mục **Lịch sử trước đồng bộ** là tài liệu/bằng chứng của contract cũ, không dùng để khẳng định contract hiện tại.

### Migration và phạm vi bảo toàn

Origin/develop có V1–V26, gồm V24 Google/V25 reset; V27 Section local không trùng. Không sửa/đổi tên migration đã áp dụng, không sửa DB cũ đang thiếu V24/V25. Dùng DB mới `toeic_web_auth_sync_verify` cho Maven và `toeic_web_auth_sync_http_test` cho browser/HTTP, cluster kiểm thử riêng `127.0.0.1:55459`, user `q03_main_test`. Không reset hoặc xóa DB.

`AGENTS.md`, Postman `api-tests/TOEIC API AUTOMATION - R2.postman_collection.json` và `tatus` nằm ngoài commit. Hash SHA-256 `tatus`: `C5DC158F6231925CDEABB6CDF81705CF9B06AC664E092F470DDF26CED1643B93`. Log, target, dist, node_modules, test-results và env local không được stage. `.env.example` chỉ chứa cấu hình công khai.

### Chạy kiểm chứng sau đồng bộ

Frontend: `npm test`, `npm run lint`, `npm run build`. Browser: `npm run test:auth:e2e` với frontend/backend/Mailpit thật, Chrome, `VITE_AUTH_MODE=remote`, `VITE_USE_MOCK=false`. Đặt `WEB_AUTH_BASE_URL=http://127.0.0.1:5173`, `WEB_AUTH_MAIL_URL=http://127.0.0.1:8025`, `WEB_AUTH_PSQL` là đường dẫn psql, `WEB_AUTH_FIXTURE_DB=toeic_web_auth_sync_http_test`.

Fixture reset OTP chỉ sửa timestamp của tài khoản `web-auth-<UUID>@example.test` vừa tạo trong DB test được allowlist, không đổi clock production. Password/OTP/token ở bộ nhớ; không trace/video/screenshot hoặc in request nhạy cảm. Test auth E2E cũ đã chuyển sang ACTIVE/password-reset theo V25, không skip hoặc xóa để báo xanh. Google thật cần cấu hình client và tài khoản Google; browser local không thay thế kiểm chứng Google thực tế.

Backend dùng Maven Wrapper, chọn test auth/Google/registration/profile/password cùng Practice/Section integration/concurrency; không lặp toàn suite chỉ để commit.

### Kết quả thực tế sau tích hợp (2026-11-11)

| Kiểm tra | Kết quả và phạm vi |
|---|---|
| `npm test` | 41/41, 0 fail/skip: cả contract local lẫn tests remote về auth/session/Google SDK/content/vocabulary/guest assessment. Transport stub, không phải HTTP thật. |
| `npm run lint` | Exit 0, còn 4 warning tại router và hooks assessment/content/games. |
| `npm run build` | Exit 0, production bundle thành công; cảnh báo plugin timing. Lượt đầu trong sandbox bị EPERM, chạy ngoài sandbox đạt. |
| Maven Wrapper, DB `toeic_web_auth_sync_verify` | **323 test, 0 failure/error/skip, BUILD SUCCESS, 4:17 phút**. Gồm 18 test class auth/Google/registration/profile/password/OTP reset và Practice/Section integration/concurrency; bao gồm cả 10 AuthServiceTest được lưu trong commit bảo toàn. Không phải full backend suite. |
| Flyway | DB mới áp dụng V1–V27; V24/V25/V26/V27 đều success. Fetch cuối vẫn không có V27 trên origin/develop hoặc remote feature. |
| `npm run test:auth:e2e` | **4/4, không skip, 1.3 phút**, Chrome/frontend/backend/PostgreSQL/Mailpit thật trên DB `toeic_web_auth_sync_http_test`. Đăng ký ACTIVE; login sai/đúng; reload/PATCH omitted/null/validation; current password sai giữ phiên; đổi mật khẩu thu hồi hai token; login lại/logout; reset OTP sai/hết hạn/resend và thu hồi token; invalid session; offline/online retry. |
| Git | Không còn conflict marker; cached diff check và kiểm tra artifact/credential signature đạt; ba file local ngoài commit khớp SHA-256 ban đầu. |

Lệnh Maven thực tế: `mvnw.cmd -o test` với `-Dtest=AuthServiceTest,SessionIssuerTest,AuthFlowIntegrationTest,RegistrationControllerTest,RegistrationFlowIntegrationTest,ProfileFlowIntegrationTest,PasswordChangeIntegrationTest,GoogleAuthIntegrationTest,GoogleIdTokenVerifierTest,PasswordResetOtpTest,SectionAttemptIntegrationTest,SectionConcurrencyTest,PracticeAttemptIntegrationTest,PracticeAnswerIntegrationTest,PracticeSubmissionIntegrationTest,PracticeAnswerConcurrencyTest,PracticeConcurrencyTest,PracticeHttpRollbackTest`. Dùng Java 21, test datasource ở trên, pool 6, Mockito javaagent lấy từ Maven cache và JVM heap 384 MB như setup kiểm thử hiện có. Các runner/log nằm trong backend/target, không commit.

Các lượt browser đầu lỗi helper không chờ form hoặc response login khi đổi trang; đã sửa đồng bộ và timeout từng thao tác 15s, kiểm tra riêng rồi chạy đủ 4 kịch bản lần cuối đạt. Không tính các lượt lỗi là đạt. Diagnostic chỉ ghi route/status; ERR_ABORTED khi chủ động đổi trang và ERR_INTERNET_DISCONNECTED trong case offline là dự kiến. Google login thực với tài khoản Google/client đã cấu hình **chưa kiểm chứng**; backend Google integration và adapter/SDK tests đã chạy.

Bằng chứng EXAM-01 cũ 881 test/40 bước HTTP thuộc trạng thái trước đồng bộ, không dùng thay cho kiểm chứng merge. Nhánh bàn giao gồm EXAM-01 + web-auth bảo toàn + merge remote, không còn PR chỉ 29 file EXAM-01. Backend EXAM-01 vẫn tách trạng thái với UI: chưa nối Section vào UI, chưa protected media playback, chưa EXAM-03. CI phải kiểm tra theo SHA merge cuối sau push.

## Lịch sử trước đồng bộ — contract OTP đăng ký và bằng chứng cũ

## Phạm vi và căn cứ

Yêu cầu người dùng ngày 2026-11-06: tích hợp auth/profile trên `feat/web-auth-integration`, không thay đổi contract backend, chưa commit/push/merge. Đã đối chiếu SRS UC-01/02, HLD Phần B/ADR-017, LLD Identity/AUTH-04, ERD users/learning_profiles, API Specification và controller/DTO/service Identity thực tế. Các tài liệu draft/OPEN không được dùng để tự bổ sung Google OAuth, refresh token, đổi email hoặc upload avatar.

Backend/migration giữ nguyên. Frontend chưa tích hợp nghiệp vụ assessment/content/vocabulary/listening/games; các màn hình này là demo và có thông báo rõ.

## Contract dùng thực tế

| Thao tác | Endpoint dưới /api/v1 | Payload / kết quả |
|---|---|---|
| Đăng ký | POST /auth/register | email, password, fullName; 201 userId/email/status PENDING; không có token |
| Xác minh | POST /auth/verify-email | email, otp (6 số); thành công chuyển login |
| Gửi lại | POST /auth/resend-otp | email; thông báo thành công không khẳng định tài khoản tồn tại; cooldown server 60s |
| Login | POST /auth/login | email/password; accessToken, tokenType Bearer, expiresIn; sau đó GET me |
| Khôi phục | GET /users/me | Bearer; lấy user thật, không đọc cached demo user |
| Hồ sơ/mục tiêu | PATCH /users/me | allowlist fullName/targetScore; 200 UserProfile |
| Mật khẩu | POST /users/me/password | currentPassword/newPassword; 204, thu hồi mọi phiên, không token mới |
| Logout | POST /auth/logout | Bearer; 204, xóa phiên local |

UserProfile: userId, email, fullName, avatarUrl, status, emailVerifiedAt, lastLoginAt, roles, targetScore. Mapper dùng userId làm id cho UI, không thêm streak/XP/mục tiêu/role giả.

PATCH form chỉ gửi trường đã thay đổi; repository cũng lọc allowlist. Omitted/undefined bị bỏ qua. targetScore null xóa mục tiêu; bỏ qua giữ nguyên; số nguyên 10–990 bội số 5. Tên không blank, tối đa 150 ký tự; không gửi email/avatar/role/permission/status.

Mật khẩu đăng ký/mới: 8–72 ký tự, không blank, tối đa 72 byte UTF-8; không tự strip. Mật khẩu hiện tại không blank, tối đa 72 ký tự/byte. Backend luôn là nơi kiểm tra cuối cùng.

## Phiên, lỗi và route

- Remote session chỉ lưu token thật, tokenType và thời điểm hết hạn vào localStorage `toeic.remote.session`. Không lưu password/OTP; không cache UserProfile như nguồn xác thực. Đây là cơ chế lưu token browser hiện tại, không phải HttpOnly cookie; cần bảo vệ XSS khi triển khai.
- Startup/reload gọi GET me nếu có phiên chưa hết hạn. Hết hạn local, INVALID_TOKEN hoặc UNAUTHENTICATED 401 từ request có Bearer sẽ xóa phiên và chuyển route bảo vệ sang login. Có timer/focus check và đồng bộ storage giữa tab.
- 401 INVALID_CREDENTIALS của login không phải session expiry. 422 CURRENT_PASSWORD_INVALID giữ phiên, hiển thị lỗi mật khẩu cũ. 400 validation và 403 không tự xóa phiên.
- Lỗi mạng/5xx khi restore: trạng thái lỗi + Thử lại, không dùng user mẫu, giữ token để retry. HTTP timeout 15s. Phản hồi 401 cũ không được xóa token mới.
- Logout luôn dọn local ngay cả khi request lỗi. Nếu chưa xác minh thu hồi phía server, màn login hiển thị rõ; không báo logout server thành công.
- Mật khẩu đổi thành công: dọn local, điều hướng login; không cấp token thay thế.
- Route bảo vệ: /profile, /settings, /attempts/:attemptId, /attempts/:attemptId/result, /vocabulary/:deckId/review. Các route công khai như /, /login, /register, /verify-email, /catalog, /tests vẫn truy cập được. Bảo vệ UI không thay thế RBAC backend.
- OTP: phân biệt OTP_INVALID/EXPIRED/UNAVAILABLE, ACCOUNT_NOT_PENDING, OTP_RESEND_TOO_SOON; EMAIL_EXISTS, MAIL_UNAVAILABLE và PASSWORD_TOO_LONG có thông báo riêng. Giữ email cần xác minh trong sessionStorage để reload; không giữ OTP.
- Google login bị vô hiệu hóa và ghi chưa hỗ trợ. Profile chỉ cho sửa trường backend hỗ trợ; dữ liệu analytics chưa có hiện trạng thái chưa có dữ liệu.

## Trách nhiệm thành phần

- RemoteAuthRepository: gửi DTO camelCase, đọc token thật, gọi GET/PATCH me và password/logout; không fallback demo.
- AuthProvider: quản lý user/loading/lỗi khôi phục, vòng đời phiên, logout/password và chống kết quả async cũ ghi đè trạng thái mới.
- apiClient + session: Bearer, envelope lỗi, timeout, dọn đúng token khi 401; request đăng ký/OTP/login không mang token.
- Auth mapper: ánh xạ DTO thực, tách mapper demo.
- RequireAuth: chờ khôi phục, retry lỗi kết nối hoặc chuyển login; không lấy role client làm bằng chứng quyền backend.
- Các trang register/verify/login/profile: validation, loading/error/empty state, PATCH từng phần và điều hướng.
- Backend AuthService, EmailVerificationService, UserProfileService, PasswordChangeService giữ nguyên trách nhiệm xác thực, OTP, persistence, permission và transaction/thu hồi phiên.

## Chạy local (PowerShell)

Frontend, trong frontend/web:

```powershell
Copy-Item .env.example .env.local
npm ci
npm run dev -- --host 127.0.0.1 --strictPort
```

.env.local:

```dotenv
VITE_API_BASE_URL=/api/v1
VITE_AUTH_MODE=remote
VITE_USE_MOCK=true
```

Auth mặc định remote ngay cả khi chưa có .env.local; VITE_AUTH_MODE=mock là lựa chọn demo riêng, không bị VITE_USE_MOCK chi phối. Thay đổi env cần restart Vite. Không dùng VITE_USE_MOCK=false để khẳng định toàn bộ module web đã tích hợp.

Vite proxy /api → http://localhost:8080 đã có sẵn, không thay đổi CORS/security. URL: http://127.0.0.1:5173/register, /verify-email, /login, /profile, /settings.

Mailpit đã dùng bản Windows v1.33.0 chính thức, kiểm SHA-256 release asset; binary/artifact nằm trong backend/target, không đưa Git:

```powershell
# Từ repository, terminal riêng; hoặc dùng mailpit đã cài trong PATH.
.\backend\target\web-auth-tools\mailpit.exe --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025 --disable-version-check
```

Mailbox: http://127.0.0.1:8025; SMTP chỉ local 1025. Không dùng địa chỉ email thật cho fixture.

Backend mặc định cần PostgreSQL localhost:5432/toeic_db, user postgres và DB_PASSWORD được cấp riêng trong môi trường; MAIL_HOST/MAIL_PORT mặc định localhost/1025. Phiên làm việc này không có DB_PASSWORD hay SMTP env từ trước. Để kiểm chứng đã dùng cluster kiểm thử PostgreSQL 17 có sẵn, chỉ bind 127.0.0.1:55439, tạo DB mới, không reset/xóa DB cá nhân:

```powershell
# Trong backend, terminal riêng. Cluster test local dùng trust, password rỗng chỉ cho setup test này.
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55439/toeic_web_auth_test'
$env:SPRING_DATASOURCE_USERNAME='auth_rbac_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:DB_PASSWORD=''
$env:MAIL_HOST='127.0.0.1'
$env:MAIL_PORT='1025'
$env:DEBUG='false'
$env:MAVEN_OPTS='-Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
```

BE http://127.0.0.1:8080/api/health. Không dùng cấu hình trust/password rỗng của test cho môi trường triển khai.

## Kiểm thử và giới hạn bằng chứng

- npm run lint: đạt, còn cảnh báo hiện hữu ở router và hook assessment/content/games; không còn cảnh báo hook/provider auth.
- npm run build: đạt; lần đầu sandbox chặn spawn EPERM, chạy lại ngoài sandbox thành công. Còn cảnh báo bundle >500kB.
- npm test: 11 contract test dùng transport stub; chỉ chứng minh mapper/repository/PATCH/401/error policy/UTF-8, không thay thế kiểm thử thật.
- Backend suite: 575 test, 0 failure/error/skipped, BUILD SUCCESS trên toeic_web_auth_regression riêng. Lần đầu Mockito tự attach agent thất bại; chạy lại nạp agent ngay khi JVM khởi động thì đạt. Không sửa backend/test để né lỗi.
- Playwright/Chrome headless: 4/4 kịch bản đạt trong 1.4 phút, không skip; mọi request nghiệp vụ dùng backend/PostgreSQL/Mailpit thật. Các lần trước lỗi môi trường và đồng bộ chờ response không được tính là đạt.

| Kiểm chứng trên trình duyệt thật | Kết quả |
|---|---|
| Register 201, không token; email giữ sau reload; nhận mail SMTP local | Đạt |
| OTP sai, resend 429 khi quá sớm, resend 200 sau cooldown thật và email mới, verify → login | Đạt |
| OTP hết hạn, fixture timestamps hợp lệ trên DB riêng | Đạt |
| Login sai password; login đúng lấy user thật; reload GET me | Đạt |
| PATCH tên + targetScore, reload; tên riêng omitted target; null xóa target; validation | Đạt |
| Mật khẩu cũ sai 422 giữ phiên; đổi thành công thu hồi cả hai phiên cũ | Đạt |
| Password cũ không login được, password mới login được | Đạt |
| Logout gọi BE, token cũ 401, vào lại route bảo vệ chuyển login; catalog vẫn public/demo | Đạt |
| Token sai 401 không dùng cached demo user; Google disabled | Đạt |
| Browser offline khi GET me, không fallback, giữ token; online/Thử lại lấy đúng user | Đạt |
- Không chạy E2E cùng full Maven suite trên cluster max_connections=100: các Spring test context có thể chiếm hết kết nối. Đã thấy tác động này trong lần kiểm tra đầu; full suite hoàn tất rồi mới chạy lại trình duyệt.
- Playwright không dùng mock response; register/login/OTP/profile/password/logout đều gọi qua proxy/backend thật, OTP lấy từ Mailpit. Fixture OTP hết hạn chỉ thay timestamps của tài khoản fixture mới trên toeic_web_auth_test, giữ ràng buộc expiry > created; không reset DB.
- Tắt trace/video/screenshot và accessibility snapshot khi lỗi để không lưu password/OTP/token; test không in giá trị nhạy cảm. Tài khoản fixture dùng email ngẫu nhiên @example.test, mật khẩu ngẫu nhiên trong bộ nhớ, không đưa tài khoản mẫu vào code production.

Lệnh Playwright trong frontend/web (Chrome local hoặc WEB_AUTH_CHROME trỏ executable):

```powershell
$env:WEB_AUTH_BASE_URL='http://127.0.0.1:5173'
$env:WEB_AUTH_MAIL_URL='http://127.0.0.1:8025'
$env:WEB_AUTH_CHROME='C:\Program Files\Google\Chrome\Application\chrome.exe'
$env:WEB_AUTH_PSQL='C:\Program Files\PostgreSQL\17\bin\psql.exe'
$env:WEB_AUTH_FIXTURE_DB='toeic_web_auth_test'
npm run test:auth:e2e
```

Backend full suite trong backend, sau khi chuẩn bị DB toeic_web_auth_regression mới. Mockito JAR 5.23.0 đã có trong Maven cache được sao chép vào target/web-auth-tools/mockito-agent.jar (artifact không commit); không thay dependency/config dự án:

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55439/toeic_web_auth_regression'
# Username/password, MAVEN_OPTS và DEBUG như trên.
.\mvnw.cmd test '-DargLine=-javaagent:target/web-auth-tools/mockito-agent.jar -Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
```

Log local không commit: backend/target/web-auth-regression-agent.log; surefire-reports. Danh tính Git pthanh137 <sleapf137@gmail.com>; tatus được bảo toàn. Chưa stage/commit/push/merge.

## Chưa triển khai

Google OAuth, quên/reset password qua mail, đổi email, upload avatar, refresh token, analytics/streak/XP, đồng bộ các tùy chọn học tập và các module nghiệp vụ web khác. Không kiểm chứng môi trường PostgreSQL cá nhân hoặc gửi email ra ngoài SMTP local.

## Danh sách file thay đổi

- `.gitignore`
- `docs/WEB_Auth_Integration.md`
- `frontend/web/.env.example`
- `frontend/web/package.json`
- `frontend/web/package-lock.json`
- `frontend/web/playwright.config.js`
- `frontend/web/README.md`
- `frontend/web/src/app/providers.jsx`
- `frontend/web/src/app/routes.jsx`
- `frontend/web/src/components/organisms/AppHeader.jsx`
- `frontend/web/src/components/organisms/LearnerSidebar.jsx`
- `frontend/web/src/components/templates/LearnerTemplate.jsx`
- `frontend/web/src/features/auth/api/auth.mapper.js`
- `frontend/web/src/features/auth/AuthContext.js`
- `frontend/web/src/features/auth/AuthProvider.jsx`
- `frontend/web/src/features/auth/config.js`
- `frontend/web/src/features/auth/errors.js`
- `frontend/web/src/features/auth/hooks/useAuth.jsx`
- `frontend/web/src/features/auth/repositories/auth.api.js`
- `frontend/web/src/features/auth/repositories/auth.fake.js`
- `frontend/web/src/features/auth/repositories/auth.repo.js`
- `frontend/web/src/features/auth/RequireAuth.jsx`
- `frontend/web/src/features/auth/session.js`
- `frontend/web/src/pages/auth/LoginPage.jsx`
- `frontend/web/src/pages/auth/RegisterPage.jsx`
- `frontend/web/src/pages/auth/VerifyEmailPage.jsx`
- `frontend/web/src/pages/HealthPage.jsx`
- `frontend/web/src/pages/HomePage.jsx`
- `frontend/web/src/pages/profile/ChangePasswordForm.jsx`
- `frontend/web/src/pages/profile/ProfilePage.jsx`
- `frontend/web/src/pages/profile/SettingsPage.jsx`
- `frontend/web/src/services/apiClient.js`
- `frontend/web/src/styles/figma.css`
- `frontend/web/tests/auth.contract.test.mjs`
- `frontend/web/tests/auth.remote.spec.js`
