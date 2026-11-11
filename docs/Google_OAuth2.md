# Đăng nhập Google OAuth2 / OpenID Connect

## Phạm vi

Web dùng Google Identity Services (GIS), nhận ID token qua JavaScript callback.
Backend xác minh ID token rồi cấp opaque Bearer session như đăng nhập email.
Không xin quyền Gmail/Drive, không lưu Google access token, không dùng Client Secret.
Đăng ký/login không cần OTP; OTP chỉ dùng quên mật khẩu theo [Password_Recovery.md](Password_Recovery.md). Thi thử khách giữ nguyên.

Tài liệu chính thức: [thiết lập](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid),
[kiểm ID token](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token),
[JavaScript API](https://developers.google.com/identity/gsi/web/reference/js-reference).

## Tạo Client ID khi chưa có

1. Mở [Google Cloud Console](https://console.cloud.google.com/), tạo hoặc chọn project.
2. Vào **Google Auth Platform → Branding**, đặt tên ENGLINGO, email hỗ trợ và liên hệ.
3. Trong **Audience**, chọn phạm vi phù hợp. Nếu chế độ Testing yêu cầu test users,
   thêm các tài khoản sẽ dùng thử.
4. Vào **Clients → Create client**, chọn **Web application**.
5. Trong **Authorized JavaScript origins**, thêm:

   ```text
   http://localhost
   http://localhost:5173
   ```

   Không thêm `/login` hay path vào origin. Production cần origin HTTPS thực tế;
   nếu đổi hostname/port local phải đăng ký đúng origin đó.
6. Bấm Create và sao chép **Client ID** kết thúc bằng `.apps.googleusercontent.com`.

Luồng này dùng popup/callback GIS, không dùng Spring Security redirect callback,
nên không thêm `/login/oauth2/code/google` hoặc redirect URI giả. Không cần Client Secret.

## Cấu hình và chạy local

Trong terminal backend:

```powershell
cd D:\TOEIC_app\backend
$env:GOOGLE_CLIENT_ID = "CLIENT_ID_CUA_BAN.apps.googleusercontent.com"
$env:GOOGLE_SECURE_COOKIE = "false"
# DB_PASSWORD được cấu hình trong môi trường theo hướng dẫn tích hợp.
.\mvnw.cmd spring-boot:run
```

Khởi động lại backend để đọc biến môi trường; Flyway áp dụng V24/V25, không sửa migration đã áp dụng.
Frontend nhận Client ID từ challenge API, không cần cấu hình trùng ở frontend:

```powershell
cd D:\TOEIC_app\frontend\web
npm run dev
```

Mở `http://localhost:5173/login`, dùng chế độ mặc định `VITE_USE_MOCK=false`.
Thiếu Client ID trả `503 GOOGLE_NOT_CONFIGURED`; email login vẫn hoạt động.

Production cần HTTPS, `GOOGLE_SECURE_COOKIE=true`, reverse proxy `/api` cùng origin
với web. Backend cần kết nối JWKS Google; SDK tải từ URL chính thức GIS. Nếu có
CSP/COOP, cấu hình theo tài liệu Google để không chặn script/frame/popup. Với HTTP
localhost, Google khuyến nghị Referrer-Policy `no-referrer-when-downgrade` khi kiểm
tra. Không ghi credential/password/token vào URL, access log hoặc body log.

## Contract API

- Public **POST `/api/v1/auth/google/challenge`**: trả
  `{challengeId,clientId,nonce,expiresIn:300}`, `Cache-Control: no-store` và cookie
  HttpOnly/SameSite=Strict, path `/api/v1/auth/google`, TTL 5 phút. Cookie có tên
  theo challenge để nhiều tab/StrictMode không ghi đè nhau. DB chỉ lưu hash nonce/binding.
- Public **POST `/api/v1/auth/google`**, chỉ nhận JSON và cookie của challenge:

  ```json
  {"challengeId":"<uuid>","credential":"<Google ID token>"}
  ```

- Nếu trùng email local ACTIVE chưa liên kết: `409 GOOGLE_LINK_REQUIRED`.
  Người dùng xác nhận mật khẩu tài khoản hiện có:

  ```json
  {"challengeId":"<uuid>","credential":"<Google ID token>","currentPassword":"<user-provided-secret>"}
  ```

- Thành công: cùng `LoginResult {accessToken,tokenType,expiresIn,user}` như email
  login; consume challenge và xóa cookie. Login/me bổ sung `localLoginEnabled`
  để UI ẩn đổi mật khẩu khi Google-only chưa có local password. Google ID token
  không dùng làm Bearer gọi API nghiệp vụ.

## Policy tài khoản

- Kiểm RSA signature RS256 từ JWKS cố định chính thức; issuer, audience, azp nếu có,
  exp/iat, email_verified và nonce. Nonce/cookie phải khớp challenge còn hiệu lực.
- Challenge khóa DB; consume, tạo/liên kết account và cấp session cùng transaction.
  Challenge thành công không dùng lại được. Không có session nếu xác minh thất bại.
- Định danh Google bằng `sub` bất biến; không tự đổi email canonical khi Google email thay đổi.
- Không auto-link dựa vào email. Local ACTIVE cần mật khẩu hiện tại; PENDING cũ được V25 kích hoạt nếu có local password. Tài khoản blocked/inactive không đăng nhập Google được.
- User mới có Google email_verified=true: ACTIVE, chỉ LEARNER, không sinh mật khẩu
  giả. V24 cho phép `password_hash` NULL với Google-only. Email registration vẫn
  bắt buộc mật khẩu và băm BCrypt; Google-only không login bằng password.
- Email bên thứ ba: Google có thể không còn kiểm soát email dù email_verified=true.
  Signup không cần OTP theo policy mới, nhưng emailVerifiedAt vẫn null nếu Google không có thẩm quyền. Link email local vẫn phải chứng minh mật khẩu.
- PK/UNIQUE bảo đảm một subject chỉ gắn một user, mỗi user chỉ gắn một Google identity.
- Chưa có unlink/chuyển identity hoặc đặt local password lần đầu cho Google-only.
  Logout chỉ thu hồi session ứng dụng, không đăng xuất Google ở Google.

| HTTP/code | Ý nghĩa |
|---|---|
| 503 GOOGLE_NOT_CONFIGURED | Chưa có GOOGLE_CLIENT_ID |
| 401 GOOGLE_CREDENTIAL_INVALID | Token/challenge/cookie/nonce hoặc mật khẩu liên kết sai |
| 409 GOOGLE_LINK_REQUIRED | Cần mật khẩu local để link |
| 409 GOOGLE_ACCOUNT_NOT_ACTIVE | Cần hoàn tất luồng tài khoản hiện hữu |
| 409 IDENTITY_CONFLICT | Tạo/liên kết đồng thời bị conflict, thử lại |

## Trách nhiệm service

- `GoogleIdTokenVerifier`: kiểm chữ ký/claim, chỉ trả identity đã kiểm chứng;
  không tin profile/role tự khai từ frontend.
- `GoogleAuthService`: kiểm browser binding, nonce/replay; tạo/link user theo
  policy và kiểm trạng thái trong transaction.
- `SessionIssuer`: cấp chung opaque session/TTL/hash cho password và Google,
  yêu cầu transaction đang mở, tránh session dở dang.
- `AuthService`, `RegistrationService`, `PasswordResetService`: giữ luồng
  email và khôi phục mật khẩu; OTP chỉ dùng recovery, đăng ký hoạt động ngay.
- `GoogleSignIn`: hiển thị nút GIS chính thức, callback và bước xác nhận link;
  credential giữ tạm trong bộ nhớ, chỉ app session được lưu theo auth hiện hữu.

## Kiểm tra

Frontend: `npm test`, `npm run lint`, `npm run build` trong `frontend/web`.
Backend: `.\mvnw.cmd test` trong `backend`, dùng PostgreSQL test riêng.
Test chữ ký dùng RSA key tạm; integration mock verifier nhưng dùng DB, cookie,
transaction và session thật. Không gọi Google hoặc dùng credential Google thật.
Kiểm tra popup/consent thật cần Client ID đã cấu hình và người dùng tự đăng nhập;
unit/integration test không chứng minh Google Cloud origin/consent đã đúng.