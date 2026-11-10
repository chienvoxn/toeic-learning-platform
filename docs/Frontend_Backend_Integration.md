# Tích hợp frontend web và backend

## Phạm vi hiện tại

Frontend `frontend/web` mặc định dùng API Spring Boot thực tế. Không đổi schema,
migration hoặc contract backend. Tài liệu API có các route DESIGN chưa hiện thực;
adapter trong lần tích hợp này theo controller/DTO đang có, không suy ra endpoint
chỉ từ bảng database hoặc catalogue thiết kế.

| Luồng | API hiện sử dụng |
|---|---|
| Đăng ký | `POST /api/v1/auth/register`, payload `email/password/fullName` |
| Xác minh / gửi lại OTP | `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/resend-otp` |
| Đăng nhập / đăng xuất | `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` |
| Hồ sơ / đổi mật khẩu | `GET/PATCH /api/v1/users/me`, `POST /api/v1/users/me/password` |
| Dashboard từ vựng | `GET /api/v1/learning-profile/overview`, `/progress?days=7` |
| Học liệu | `GET /api/v1/contents`, `GET /api/v1/contents/{id}` |
| Bookmark | `GET /api/v1/users/me/bookmarks`, `POST/DELETE /api/v1/contents/{id}/bookmarks` |
| Ghi lượt truy cập | `POST /api/v1/contents/{id}/history` khi nhấn Đọc bài học |
| Bộ thẻ | `GET/POST /api/v1/vocabulary-decks`, `GET /api/v1/vocabulary-decks/{id}` |
| Tạo từ và thêm vào bộ | `POST /api/v1/vocabularies`, `POST /api/v1/vocabulary-decks/{id}/vocabularies` |
| Flashcard / trạng thái | `GET /api/v1/vocabulary-decks/{id}/flashcards`, `GET /api/v1/vocabularies/{id}/review-state` |
| Ôn tập | `POST /api/v1/vocabularies/{id}/reviews`, rating integer 1–4 |

Đăng ký trả thông tin tài khoản chờ xác minh, không trả token. Chỉ sau đăng nhập
thành công mới lưu `accessToken` và gửi `Authorization: Bearer ...`. `401` từ request
của phiên hiện tại xóa phiên trình duyệt; `403` không xóa phiên. Phản hồi `401` đến
muộn của phiên cũ không xóa token của lần đăng nhập mới.

Frontend đọc `userId`, `fullName`, `targetScore` đúng DTO; null mục tiêu được giữ
nguyên. Mục tiêu gửi lại qua PATCH là số hoặc null. Đổi mật khẩu thành công yêu cầu
đăng nhập lại vì backend thu hồi mọi phiên. Nếu logout lỗi mạng, frontend vẫn xóa
phiên tại máy; không khẳng định đã thu hồi phiên server.

Again/Hard/Good/Easy được đổi sang 1/2/3/4 theo `SubmitReviewRequest` đang chạy.
Các ghi chú draft rating chuỗi không được coi là contract runtime mới. Ngày ôn tiếp
theo vẫn do `SrsScheduler` tính, frontend không tự quyết định lịch.

## Trách nhiệm các thành phần

- `apiClient`: ghép URL, gắn Bearer cho request cần phiên, chuyển lỗi HTTP/mạng
  thành lỗi giao diện và phát tín hiệu hết phiên. Request auth công khai không gắn token cũ.
- `RemoteAuthRepository`: gửi đúng DTO tài khoản, quản lý token cục bộ; `AuthProvider`
  đồng bộ người dùng giữa các màn hình và tiếp nhận sự kiện hết phiên.
- `RemoteContentRepository`: đọc học liệu đã được backend lọc quyền, bookmark và gửi
  POST lịch sử từ thao tác người dùng. Body bài học hiển thị dưới dạng văn bản an toàn.
- `RemoteVocabularyRepository`: ánh xạ deck/word/review state sang giao diện và đổi
  rating; không tính lại lịch SRS hoặc tạo dữ liệu mẫu khi API lỗi.
- Backend `RegistrationService` tạo tài khoản/OTP; `EmailVerificationService` xử lý
  xác minh/gửi lại; `AuthService` tạo/thu hồi phiên; `UserProfileService` cập nhật hồ sơ.
- Backend `ContentReadService` và `ContentPersonalService` kiểm soát đọc/quyền và
  bookmark/lịch sử; các service Vocabulary quản lý từ, bộ thẻ, ôn tập và thống kê.

Tạo từ và gắn vào bộ là hai API riêng. Nếu đã tạo từ nhưng gắn thất bại, form giữ
ID để thử lại thao tác gắn trong cùng màn hình, tránh tạo trùng khi bấm thử lại.
Adapter lấy từng review-state để có trạng thái thẻ; bộ thẻ rất lớn có thể cần API
tổng hợp trong một thay đổi backend riêng để giảm số request.

## Chạy local trên Windows

1. Chạy PostgreSQL tại `localhost:5432`, có database `toeic_db`, tài khoản `postgres`.
2. Trong PowerShell chạy backend, đặt `DB_PASSWORD` bằng mật khẩu PostgreSQL của
   bạn. Không commit hoặc ghi mật khẩu vào log. Chạy:

   ```powershell
   cd D:\TOEIC_app\backend
   .\mvnw.cmd spring-boot:run
   ```

3. Cấu hình SMTP trước khi chạy backend để gửi OTP. Mặc định là `localhost:1025`;
   cấu hình khác dùng `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, và thông tin xác thực
   tương ứng trong môi trường. Không có cơ chế bỏ qua OTP hoặc in OTP ra log.
   Để thử local mà không gửi thư ra ngoài, mở một terminal riêng:

   ```powershell
   cd D:\TOEIC_app\frontend\web
   npm run dev:mail
   ```

   Lệnh này chạy SMTP tại `127.0.0.1:1025` và hộp thư tại
   `http://localhost:8025`. Đăng ký bằng email bạn muốn thử, mở hộp thư và nhấn
   Làm mới để lấy OTP. Thư chỉ lưu trong bộ nhớ, mất khi dừng tiến trình. Không
   dùng hộp thư này cho production; đây là SMTP tối giản dành cho kiểm thử local.
   SMTP backend cần trỏ `localhost:1025`, không bật SMTP auth/STARTTLS cho hộp thư này.
4. Ở terminal khác:

   ```powershell
   cd D:\TOEIC_app\frontend\web
   npm ci
   npm run dev
   ```

Không cần tạo `.env.local` để dùng API thật. Nếu có file môi trường cũ, kiểm tra
`VITE_USE_MOCK=false` và `VITE_API_BASE_URL=/api/v1`, sau đó khởi động lại Vite.
Vite chuyển tiếp `/api` sang `http://localhost:8080`. Mở `http://localhost:5173/health`
để kiểm tra backend và database. Bản build production cần reverse proxy `/api`
đến backend và SPA fallback cho URL frontend.

Khi cả backend, Vite và hộp thư local đang chạy, kiểm tra tự động luồng thật bằng:

```powershell
cd D:\TOEIC_app\frontend\web
npm run test:registration:local
```

Lệnh này tạo **một tài khoản kiểm thử mới trong database local** mỗi lần chạy,
nhận OTP từ hộp thư, xác minh, login, đọc hồ sơ, logout và kiểm tra token đã thu hồi.
Mật khẩu ngẫu nhiên/OTP/token chỉ giữ trong bộ nhớ, không in hoặc ghi file.
Tài khoản smoke test được giữ trong database nhưng không cung cấp mật khẩu để sử dụng;
hãy đăng ký tài khoản riêng tại `http://localhost:5173/register` để tự kiểm tra UI.
Kiểm thử này đi qua HTTP proxy của Vite, không tự động thao tác trình duyệt.

## Kiểm tra thủ công khi database và SMTP sẵn sàng

1. Mở `/` khi chưa có phiên: thấy landing, vào `/profile`: chuyển tới login.
2. Đăng ký tài khoản mới: sang `/verify-email`, chưa có phiên đăng nhập.
3. Thử OTP sai; gửi lại sau cooldown; dùng OTP hợp lệ rồi đăng nhập.
4. Sửa tên/mục tiêu, tải lại trang và kiểm tra dữ liệu đã lưu.
5. Tạo bộ thẻ, thêm từ và nghĩa, lật thẻ và đánh giá; tải lại xem trạng thái.
6. Đọc bài học đã xuất bản, thêm/bỏ bookmark; kiểm tra lịch sử truy cập.
7. Đổi mật khẩu: phiên bị thu hồi, phải đăng nhập lại bằng mật khẩu mới.
8. Logout rồi tải lại: không tự tạo tài khoản mẫu. Thử token hết hạn và thiếu quyền:
   lần lượt trở về đăng nhập hoặc hiển thị lỗi quyền.

## Thi thử cho khách chưa đăng nhập

Theo yêu cầu cập nhật, `/tests`, `/tests/:testId/overview`, `/attempts/:attemptId`
và `/attempts/:attemptId/result` không nằm trong `RequireAuth`. Landing có liên kết
Thi thử không cần đăng nhập. Hồ sơ, cài đặt, học liệu cá nhân và từ vựng vẫn được bảo vệ.

Vì chưa có API assessment, `GuestAssessmentRepository` cung cấp một đề Reading
mẫu từ nội dung fixture hiện có, chỉ công bố đúng số câu Part 5/7 thực tế. Adapter
này được chọn rõ ràng cho thi thử local trong chế độ mặc định; không phải fallback
khi API lỗi và không phải kết nối tới đề/attempt server. Trên các trang thi có
thông báo dữ liệu chỉ lưu trên trình duyệt và không đồng bộ tài khoản, kể cả khi
người làm bài đang đăng nhập.

Khách chọn Part, làm bài, lưu đáp án trên thiết bị, tải lại cùng URL để tiếp tục,
nộp hoặc tự nộp khi hết giờ và xem đáp án. Submit lặp trả cùng kết quả, không sửa
bài sau nộp, autosave cũ không ghi đè đáp án mới. Không tạo phiên tài khoản, không
gửi Bearer hoặc mở các API cá nhân cho anonymous. Chỉ hiển thị số câu đúng và độ
chính xác; không dùng công thức giả để quy đổi điểm TOEIC.

Thời gian và chấm bài local phụ thuộc trình duyệt, không có đảm bảo chống can
thiệp như một kỳ thi do server quản lý. Xóa dữ liệu trình duyệt làm mất bài; mở
thiết bị/trình duyệt khác không có bài cũ. Lịch sử không tự gắn vào tài khoản sau
đăng nhập. Khi bổ sung thi backend, cần thiết kế định danh/ownership của guest,
server expiry và snapshot riêng; không bỏ kiểm tra ownership hiện có.

Kiểm tra bằng `npm test` (gồm `tests/guest-assessment.test.mjs`) và truy cập `/tests`
khi chưa có tài khoản. Đăng nhập không còn là điều kiện để dùng đề mẫu này.

## Giới hạn

- Thi/attempt/chấm điểm trên server, game, dictation/shadowing và Google login chưa
  có API backend tương ứng. Thi thử mẫu trên thiết bị được mô tả ở trên. Các trang
  game/dictation/shadowing hiện báo chưa sẵn sàng trong chế độ thật. Bản demo rộng
  hơn vẫn có thể xem riêng bằng `VITE_USE_MOCK=true`.
- Content API chỉ trả metadata media, chưa có URL phát tài nguyên. Trang chi tiết
  đọc được văn bản và báo rõ chưa phát media; không tự dựng URL tài nguyên riêng tư.
- Upload ảnh, bio, quyền riêng tư hồ sơ, xếp hạng và các cài đặt thiết bị chưa được
  đồng bộ server. Dashboard thật chỉ trình bày số liệu hiện có từ learning-profile.
- Test adapter dùng HTTP fixtures, không thay thế kiểm tra end-to-end với SMTP,
  Spring Boot và PostgreSQL thật. Các quyết định OPEN trong SRS/HLD/LLD giữ nguyên.
