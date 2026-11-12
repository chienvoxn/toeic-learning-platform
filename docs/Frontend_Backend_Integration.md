# Tích hợp frontend web và backend

## Cấu hình

Frontend `frontend/web` mặc định dùng API thật (`VITE_USE_MOCK=false`), base `/api/v1`.
Auth dùng `VITE_AUTH_MODE=remote` mặc định và độc lập với mock module; phiên browser là `toeic.remote.session`, restore bằng GET me. Bản local trước đồng bộ và kết quả kiểm chứng merge: [WEB_Auth_Integration.md](WEB_Auth_Integration.md).
Vite proxy `/api` sang `http://localhost:8080`. Không có fallback tự động sang mock
khi API lỗi. Nếu đổi biến môi trường frontend, khởi động lại Vite.

## Các luồng đã nối

| Luồng | API |
|---|---|
| Đăng ký không OTP | POST `/api/v1/auth/register` → 201 ACTIVE |
| Login/logout | POST `/api/v1/auth/login`, `/auth/logout` |
| Google OIDC | POST `/api/v1/auth/google/challenge`, `/auth/google` |
| Quên/đặt lại mật khẩu | POST `/api/v1/auth/forgot-password`, `/auth/reset-password` |
| Hồ sơ/đổi mật khẩu hiện tại | GET/PATCH `/api/v1/users/me`, POST `/users/me/password` |
| Dashboard từ vựng | GET `/api/v1/learning-profile/overview`, `/progress?days=7` |
| Học liệu | GET `/api/v1/contents`, `/contents/{id}` |
| Bookmark/lịch sử | GET `/api/v1/users/me/bookmarks`, POST/DELETE `/contents/{id}/bookmarks`, POST `/contents/{id}/history` |
| Bộ thẻ | GET/POST `/api/v1/vocabulary-decks`, GET `/vocabulary-decks/{id}` |
| Từ vựng/flashcard | POST `/api/v1/vocabularies`, POST `/vocabulary-decks/{id}/vocabularies`, GET `/vocabulary-decks/{id}/flashcards` |
| Trạng thái/ôn tập | GET `/api/v1/vocabularies/{id}/review-state`, POST `/vocabularies/{id}/reviews`, rating 1–4 |

Đăng ký không tạo phiên, không gửi email và không cần SMTP; thành công chuyển về
login. Login lưu accessToken và gửi Bearer cho request riêng tư. `401` của phiên
hiện tại xóa phiên trình duyệt; `403` không xóa. Phản hồi phiên cũ không xóa token
mới. Đổi/reset mật khẩu thu hồi mọi phiên của account, phải đăng nhập lại.

Policy tài khoản hiện hành: [Password_Recovery.md](Password_Recovery.md).
Google Client ID, cookie/nonce và linking: [Google_OAuth2.md](Google_OAuth2.md).
Các mô tả verify-email/resend-otp trước đây đã được thay thế; URL frontend
`/verify-email` cũ chỉ chuyển về login.

## Chạy local

Demo đã kiểm tại `D:\toeic-learning-platform` dùng profile `local-demo` và database
riêng `toeic_learning_demo:55459`; xem [hướng dẫn demo và bằng chứng](LOCAL_DEMO_FE_BE_Integration.md).
Không khởi động datasource cá nhân `toeic_db` bên dưới nếu chưa backup/cho phép nâng cấp.
Các bước sau là cấu hình mặc định lịch sử, không phải cấu hình phiên demo đang chạy.

1. PostgreSQL chạy tại localhost:5432, database toeic_db, user postgres.
2. Đặt `DB_PASSWORD` trong terminal backend theo mật khẩu PostgreSQL của bạn,
   không ghi vào source hoặc log. Chạy:

   ```powershell
   cd D:\TOEIC_app\backend
   .\mvnw.cmd spring-boot:run
   ```

3. Ở terminal khác:

   ```powershell
   cd D:\TOEIC_app\frontend\web
   npm ci
   npm run dev
   ```

4. Mở `http://localhost:5173/health` để kiểm backend/database.
5. Đăng ký ở `/register` rồi login ngay, không có bước OTP.
6. Chỉ khi thử quên mật khẩu, chạy SMTP local ở terminal riêng:

   ```powershell
   cd D:\TOEIC_app\frontend\web
   npm run dev:mail
   ```

   Chọn Quên mật khẩu trên trang login, gửi mã, mở `http://localhost:8025` và làm
   mới hộp thư để lấy OTP. SMTP nhận tại localhost:1025, không auth/STARTTLS.
   Thư chỉ trong bộ nhớ, không gửi Internet. Production cần cấu hình MAIL_HOST,
   MAIL_PORT, MAIL_FROM và các biến xác thực SMTP phù hợp.

Google chỉ bật khi backend có GOOGLE_CLIENT_ID. Production dùng HTTPS,
GOOGLE_SECURE_COOKIE=true và reverse proxy /api cùng origin, SPA fallback cho web.

## Kiểm tra

- `npm test`, `npm run lint`, `npm run build` trong frontend/web.
- `.\mvnw.cmd test` trong backend với PostgreSQL test riêng.
- `npm run test:registration:local` khi backend và Vite đang chạy: tạo tài khoản
  smoke test ACTIVE, login/me/logout, không cần hộp thư. Password/token chỉ giữ
  trong bộ nhớ. Tài khoản test được giữ trong DB; tạo account riêng để sử dụng UI.
- Thử quên mật khẩu: mã sai, hết hạn, cooldown, dùng lại và giới hạn 5 lần; password
  cũ/token cũ phải vô hiệu sau reset. Google-only dùng Google để vào lại account.
- Test adapter HTTP fixture không thay thế UI/SMTP/Google Cloud thật.

## Thi thử và giới hạn

Thi thử công khai tại `/tests`, `/tests/:testId/overview`, `/attempts/:attemptId`,
`/attempts/:attemptId/result`. GuestAssessmentRepository cung cấp đề Reading mẫu
Part 5/7 trên thiết bị, không sync account; chỉ raw count, không quy đổi TOEIC.
Thời gian/chấm local không có bảo đảm như server. Xóa dữ liệu trình duyệt mất bài.

Luồng tài khoản tại `/practice` đã nối PRAC-01/02 và Reading EXAM-01: tạo snapshot,
PUT lựa chọn từng questionVersionId, reload, submit không body và đọc grading/giải thích
đã lưu. API lỗi không fallback Guest. Section giữ nhóm/đủ Part và deadline server,
không tự nộp khi hết hạn; chưa có thi Guest server hoặc EXAM-03. Xem
[payload, kiểm chứng và giới hạn demo](LOCAL_DEMO_FE_BE_Integration.md).
Game, dictation/shadowing chưa có backend tương ứng nên chế độ thật báo chưa sẵn sàng.
Media content chỉ trả metadata, chưa có URL playback. Avatar/bio/privacy chưa được
server hỗ trợ. Tạo từ và gắn vào bộ là hai API; UI giữ ID nếu bước gắn lỗi để retry
không tạo trùng. Review-state được tải từng thẻ; bộ rất lớn cần API tổng hợp sau này.
