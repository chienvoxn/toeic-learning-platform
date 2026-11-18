# Kiểm thử tải login trên database mới

## 1. Chọn database và chạy backend

Kiểm tra datasource/profile backend thực sự dùng trước khi nạp dữ liệu. Cấu hình mặc định trong `backend/src/main/resources/application.properties` vẫn là `toeic_db`; profile `local-demo` dùng `toeic_learning_demo`, còn file local mẫu dùng `toeic_dev_fresh`. Chỉ nạp vào database thử nghiệm đã chọn, không nạp vào database cá nhân hoặc production.

Chạy Flyway qua quá trình khởi động backend và xác nhận ứng dụng lên thành công. Không thay đổi migration chỉ để phục vụ load test.

## 2. Tạo và nạp 10.000 tài khoản

CSV `login-users-10000.csv` gồm 10.000 dòng `email,password`, không có header. Mỗi tài khoản có mật khẩu riêng. File được `.gitignore` loại khỏi commit.

```powershell
python performance/generate_login_users.py
python performance/seed_login_users.py --base-url http://127.0.0.1:8080
```

Lệnh seed gọi API đăng ký theo thứ tự và có thể mất nhiều phút do bcrypt. Có thể chạy lại sau khi bị gián đoạn; HTTP 409 được đếm là tài khoản đã tồn tại. **Không tạo lại CSV sau khi đã seed**, vì mật khẩu mới sẽ không khớp. Nếu đổi database, dùng lại CSV hiện có hoặc tạo bộ mới rồi seed từ đầu.

Kiểm tra một dòng bằng request login thủ công; HTTP 200 là điều kiện trước khi chạy tải. Không in password/token vào log hoặc ảnh slide.

## 3. Tạo Test Plan trong JMeter

1. Thêm `Thread Group`: đặt số thread theo từng lượt **100, 500, 1.000**, ramp-up đủ dài để tránh dồn tất cả request vào cùng một giây, loop count `1`. Chạy mỗi mức riêng.
2. Thêm `CSV Data Set Config` trong Thread Group: Filename là đường dẫn tuyệt đối tới `performance/login-users-10000.csv`; Variable Names `email,password`; Delimiter `,`; Recycle on EOF `False`; Stop thread on EOF `True`; Sharing mode `All threads`.
3. Thêm `HTTP Request Defaults`: protocol `http`, host `127.0.0.1`, port `8080` (đổi theo backend đang chạy).
4. Thêm `HTTP Header Manager`: `Content-Type: application/json`.
5. Thêm `HTTP Request`: method `POST`, path `/api/v1/auth/login`, Body Data: `{"email":"${email}","password":"${password}"}`.
6. Thêm `Response Assertion`: response code bằng `200`. Không lưu response body vì chứa token.
7. Chạy bằng CLI, ví dụ `jmeter -n -t performance/login-load.jmx -l performance/login-100.jtl` nếu đã lưu Test Plan tại đường dẫn đó. Đổi tên JTL cho từng mức tải; không ghi đè lượt trước.

Mức 10.000 thread trên một máy JMeter có thể bị giới hạn bởi RAM/CPU hoặc cổng mạng của **máy tạo tải**. Nếu muốn mô phỏng 10.000 người dùng đồng thời, kiểm tra tài nguyên máy JMeter và cân nhắc chạy phân tán hoặc tăng tải theo nhiều bậc trung gian. Có 10.000 dòng CSV không đồng nghĩa đã tạo 10.000 request đồng thời.

## 4. Ghi nhận và trình bày

Ghi cho từng lượt: database/profile, cấu hình máy backend và JMeter, thread, ramp-up, thời gian chạy, số request, response time p95, throughput, HTTP error rate và tài nguyên CPU/RAM/DB. Đối chiếu lỗi assertion với HTTP status. Chạy Health API thành kịch bản riêng để tránh lẫn số liệu login.


