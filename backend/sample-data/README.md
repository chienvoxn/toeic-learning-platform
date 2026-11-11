# Dữ liệu mẫu TOEIC — nạp bằng pgAdmin

File chạy ngay: **toeic_sample_data.sql** (UTF-8, khoảng 9 MB). Đây là seed tùy chọn,
không thuộc Flyway và không tự chạy trên môi trường sản phẩm.

## Nạp vào toeic_db

1. Khởi động backend code hiện tại để Flyway áp dụng V26.
2. Trong pgAdmin, chọn database **toeic_db** → **Query Tool**.
3. Chọn **Open File**, mở `D:\TOEIC_app\backend\sample-data\toeic_sample_data.sql`.
4. Chạy **toàn bộ file** bằng F5, đợi thông báo `COMMIT`/`Sample data ready`.
5. Đăng xuất rồi đăng nhập lại frontend tại `http://localhost:5173/login`.
   Bấm Ctrl+F5 nếu trang cũ đang mở. Frontend phải dùng API thật
   (`VITE_USE_MOCK=false`, mặc định hiện tại); không cần thay source để hiển thị seed.

Nếu báo thiếu V26 hoặc hai tài khoản quản trị, transaction dừng và không nạp dữ liệu.
Không chạy riêng từng đoạn hoặc thêm `COMMIT` khi một đoạn đã báo lỗi.

SQL yêu cầu hai tài khoản ACTIVE đã tạo trước:

- `admin@toeic.local`: được bổ sung role ADMIN.
- `teacher@toeic.local`: được bổ sung role TEACHER.

Seed không tạo/reset mật khẩu, không kích hoạt tài khoản khóa, không cấp quyền
quản trị cho tài khoản khác. Giữ các role sẵn có, bao gồm LEARNER của hai tài khoản.

## Dữ liệu xuất hiện ở đâu

| URL | Dữ liệu mẫu |
|---|---|
| `/dashboard` | 36 từ, thẻ mới/đến hạn/đã thành thạo, 84 lượt ôn và biểu đồ 7 ngày |
| `/catalog` | 12 bài có nội dung, thuộc 6 nhóm Grammar/Vocabulary/Listening/Reading/Strategies/Documents |
| `/catalog` → Đã lưu | 4 bookmark mẫu riêng mỗi tài khoản |
| `/vocabulary` | 3 bộ × 12 từ, nghĩa Việt, IPA, từ loại, ví dụ và ghi chú |
| `/vocabulary/:id/review` | Thẻ mới, đến hạn ôn và đã thành thạo; dùng rating thật để cập nhật SRS |
| `/tests` | 4 đề đã xuất bản: Full 200 câu, Listening 100, Reading 100, Part 5 nhanh 12 |
| `/exam-history` | 3 bài đã chấm (Full, Reading, Part 5), 1 bài Part 5 đang làm có 3 đáp án đã lưu |
| `/attempts/:id/result` | Số câu đúng, kết quả theo Part, câu sai/bỏ trống, lời giải, transcript, audio và ảnh |
| `/teacher/tests`, `/admin/tests` | 8 đề: 4 published, 1 nháp, 1 chờ duyệt, 1 đã duyệt, 1 retired |
| Teacher Question/Content API | Ngân hàng 200 câu published, 42 nhóm, 775 lựa chọn và 12 học liệu của Teacher |

Thẻ và lịch sử cá nhân được tạo cho **mọi tài khoản ACTIVE đang tồn tại lúc chạy SQL**.
Đây là bản sao riêng theo ownership, không chia sẻ hoặc sửa từ vựng riêng đã có.
Tài khoản tạo sau cần chạy lại seed để nhận phần cá nhân. Profile có targetScore
sẵn được giữ nguyên; profile mới nhận mục tiêu mẫu 750.

Lịch sử có ngày tương đối so với thời điểm nạp lần đầu. Bài đang làm có 12 phút;
nếu mở muộn, backend sẽ chấm hết giờ và chuyển sang kết quả. Chạy seed lần nữa
không kéo dài thời gian hoặc đặt lại bài đã làm. Có thể bắt đầu bài mới từ danh mục.

## Nội dung và giới hạn

- Ngân hàng đủ Part 1–7: **6 / 25 / 39 / 30 / 30 / 16 / 54** câu.
- Có **54 file WAV tiếng Anh nghe được** và **1 hình minh họa văn phòng PNG**, nhúng
  sẵn vào SQL và lưu trong PostgreSQL. Không cần copy file media hoặc có Internet.
- Các câu tự biên soạn để trải nghiệm tính năng. Hội thoại dùng một giọng tổng hợp
  có nhãn người nói; Part 1 dùng tranh minh họa. Nội dung được đánh dấu `[Mẫu]`.
- Câu trả lời mẫu có cả đúng, sai và bỏ trống để thử bộ lọc chữa bài và thống kê.
- **Không tạo bảng quy đổi điểm giả.** Chưa có bảng được thẩm định nên các bài mẫu
  hiển thị raw counts; trường điểm TOEIC ước tính là null, đúng contract hiện hành.
- Profile và đổi mật khẩu dùng tài khoản thật. Không tạo OTP, session hoặc Google
  identity giả; đăng nhập/Google/reset password cần đi qua luồng xác thực thực tế.
- Game, Dictation/Shadowing, thông báo và AI chat chưa có luồng web/backend hoàn chỉnh;
  seed không biến các màn hình chưa tích hợp thành tính năng đã chạy.
- Bốn file `import-*.json` có thể dùng thử màn import **sau khi đã nạp SQL**, vì các
  mediaId trong JSON tham chiếu media seed. Không có trường hệ thống `_sourceOrder`.

## An toàn dữ liệu khi chạy lại

UUID cố định theo namespace `toeic-sample-v1`, insert có `ON CONFLICT DO NOTHING`,
toàn bộ thao tác trong một transaction và advisory lock chống nạp song song. Không
truncate/delete dữ liệu, không sửa migration, không ghi đè đáp án/điểm/lịch ôn đã có.
Các đối tượng mẫu đã bị xóa (ví dụ bookmark) có thể được thêm lại khi nạp seed.
Các bản xuất bản giữ nguyên; không đổi pointer trừ khi pointer của chính đối tượng
mẫu đang trống. Đề mới được tạo bằng giao diện hoạt động độc lập với seed.

## Tái tạo và kiểm chứng

Nguồn: `backend/scripts/sample-data/generate.py`, `template.sql`, `synthesize.ps1`.
Từ thư mục repository:

```powershell
python backend/scripts/sample-data/generate.py
```

Cần Python stdlib, Windows System.Speech với giọng English được bật và System.Drawing.
Media cache trong `backend/target/sample-data-media` (không commit). Khi máy hạn chế
COM trong PowerShell con, chạy trực tiếp rồi đóng gói lại:

```powershell
& .\backend\scripts\sample-data\synthesize.ps1 `
  -Manifest .\backend\target\sample-data-media\speech.json `
  -OutputDirectory .\backend\target\sample-data-media
python backend/scripts/sample-data/generate.py --reuse-media
```

`verify.py` chỉ chạy với DB test tách biệt `toeic_sample_seed*` ở 127.0.0.1:55439,
backend 8086, không nhắm đến toeic_db. Nó tạo tài khoản test có password ngẫu nhiên
chỉ giữ trong bộ nhớ, nạp SQL nhiều lần, kiểm API/RBAC/ownership/media và bảo toàn
tiến độ sau khi người dùng lưu đáp án/ôn thẻ. `SAMPLE_VERIFY_DATABASE` chọn tên DB
test mới nếu cần; `PSQL_BIN` tùy chọn đường dẫn psql.

Đã kiểm tra SQL trên PostgreSQL 18 cùng migrations V1–V26 và API backend thật.
Chưa xác minh trực tiếp màn hình bằng trình duyệt; cấu trúc JSON/ID/visibility và
ownership được đối chiếu với repository/API hiện dùng của frontend.
