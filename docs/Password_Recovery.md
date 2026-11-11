# Đăng ký không OTP và khôi phục mật khẩu

## Chính sách hiện hành

Theo yêu cầu người dùng ngày 2026-11-11, thay thế chính sách xác minh đăng ký bằng OTP:

Đây là policy hiện hành cho đăng ký và khôi phục mật khẩu, đối chiếu với
RegistrationService, PasswordResetService và migration V25. Các mô tả đăng ký
PENDING rồi verify-email/resend-otp trong tài liệu lịch sử đã được thay thế;
không coi chúng là yêu cầu cần triển khai lại.

- Đăng ký email tạo tài khoản ACTIVE ngay, không gửi thư, không tạo OTP, không tự login.
- Sau đăng ký chuyển về login, có thông báo và email điền sẵn. Login email/password không cần OTP.
- `emailVerifiedAt` vẫn null khi chưa có bằng chứng sở hữu email; không đánh dấu đã xác minh giả.
- OTP chỉ dùng quên mật khẩu. Không dùng OTP đăng ký cũ để reset.
- Google OAuth2 vẫn dùng ID token/nonce. Email local trùng phải xác nhận mật khẩu,
  không tự link chỉ vì trùng email. Không thêm bước OTP vào Google signup/login.

## API

| API | Hành vi |
|---|---|
| POST `/api/v1/auth/register` | Giữ payload email/password/fullName, trả 201 userId/email/status=ACTIVE |
| POST `/api/v1/auth/login` | Giữ opaque session contract, ACTIVE login ngay |
| POST `/api/v1/auth/forgot-password` | `{email}`; public, 200 thông điệp chung |
| POST `/api/v1/auth/reset-password` | `{email,otp,newPassword}`; public, 204 khi đổi thành công |
| POST `/api/v1/auth/verify-email`, `/resend-otp` | Đã gỡ, không còn nghiệp vụ xác minh đăng ký |

Forgot-password luôn trả thông điệp chung cho email không tồn tại, account bị khóa,
Google-only không có password, còn cooldown hoặc SMTP không gửi được. Không trả OTP
hoặc trạng thái tồn tại account trong response. SMTP lỗi rollback OTP mới, không
làm mất hiệu lực mã cũ còn dùng được. OTP mới chỉ gửi cho ACTIVE có local password.

Reset sai email/mã, hết hạn, quá số lần thử hoặc dùng lại đều trả
`400 PASSWORD_RESET_INVALID`. Request sai định dạng/mật khẩu mới trái policy trả
400 VALIDATION_ERROR. Mật khẩu 8–72 ký tự và tối đa 72 byte UTF-8.

Reset không phát session mới; frontend quay về login. Các phiên đang dùng của
account được reset đều bị thu hồi, kể cả phiên tạo bằng Google nếu account đã link.
Google-only tiếp tục đăng nhập Google; đặt local password lần đầu nằm ngoài phạm vi.

## Persistence và transaction

V25 tạo `password_reset_otps` riêng, chỉ lưu BCrypt hash. TTL 5 phút, tối đa 5 lần
thử, cooldown gửi lại 60 giây; thay đổi bằng `app.password-reset.*`. Mã mới vô hiệu
mã cũ. Bộ đếm thử sai vẫn commit dù API trả lỗi. Reset khóa user cùng thứ tự với
password login/change để không tạo session bằng mật khẩu cũ sau khi đổi.

OTP consume, hash mật khẩu mới, email proof và revoke session nằm cùng transaction.
Nộp mã đồng thời chỉ một lần thành công; không có API verify mã riêng rồi bỏ qua
mã ở bước đổi mật khẩu. Reset không mở khóa hoặc kích hoạt account bị khóa.

V25 chỉ chuyển tài khoản PENDING cũ có password sang ACTIVE. Không đổi trạng thái
LOCKED/BLOCKED/DISABLED, không đặt emailVerifiedAt cho tài khoản chưa có proof.
Bảng `email_verification_otps` lịch sử giữ nguyên, không được đọc trong reset.
Không sửa migration V1–V24. ORM/service OTP xác minh cũ được thay bằng reset OTP.

## Trách nhiệm service

- `RegistrationService`: chuẩn hóa email, chống trùng, hash password, cấp LEARNER,
  kích hoạt account và lưu user. Không phụ thuộc SMTP hoặc OTP.
- `PasswordResetService`: phát OTP có giới hạn, kiểm hash/expiry/attempts và đổi
  mật khẩu; thu hồi session, không tự cấp quyền hoặc mở khóa user.
- `OtpMailService`: chỉ gửi email đặt lại mật khẩu, không gửi thư xác minh đăng ký.
- `AuthService`/`SessionIssuer`: cấp session khi login email hoặc Google.
- `GoogleAuthService`: giữ policy explicit password linking và subject identity;
  không thêm OTP signup. ID token phải có Google email_verified=true, nhưng email
  bên thứ ba không được đánh dấu proof email hệ thống nếu Google không có thẩm quyền.

## Giao diện

- `/register`: thông báo không cần OTP, thành công về `/login`.
- `/login`: có nút Google và liên kết Quên mật khẩu; không có link xác minh email.
- `/forgot-password`: nhập email → gửi mã → nhập OTP + password mới + xác nhận → login.
- `/verify-email` cũ chuyển hướng về login; không còn trang yêu cầu mã đăng ký.
- Cài đặt giữ đổi mật khẩu bằng mật khẩu hiện tại; không nhầm với khôi phục bằng OTP.
- Hộp thư local `npm run dev:mail`, mở `http://localhost:8025`; chỉ cần khi thử recovery.

## Kiểm tra local

Khởi động lại backend để Flyway áp dụng V24/V25. Đăng ký không cần SMTP.
Để thử recovery, chạy SMTP local tại 1025 trước khi gửi mã:

```powershell
cd D:\TOEIC_app\frontend\web
npm run dev:mail
```

Đăng ký và login ngay. Chọn Quên mật khẩu, nhập email vừa đăng ký, mở hộp thư local
và lấy OTP. Đặt mật khẩu mới; thử login mật khẩu cũ phải thất bại, mật khẩu mới
thành công. Token cũ phải bị từ chối. Thử mã sai 5 lần, mã hết hạn, gửi lại và dùng
lại mã để kiểm tra giới hạn. Trong production, cấu hình SMTP thật từ môi trường.

Test: `npm test`, `npm run lint`, `npm run build` trong web và `.\mvnw.cmd test`
trong backend với PostgreSQL test riêng. Test không thay thế thao tác UI/Google thật.
