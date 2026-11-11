# TOEIC Learning Platform — Hướng dẫn cho Codex

## Tài liệu dự án
Đọc các phần liên quan trong:
- docs/SRS.md
- docs/HLD.md
- docs/LLD.md
- docs/ERD_v1.6.md
- docs/API_Specification.md

Tài liệu có nội dung draft, DESIGN, OPEN và ghi chú lịch sử.
Đối chiếu tài liệu với code, migration và test hiện có.
Khi có mâu thuẫn, nêu rõ và đề xuất cách giải quyết.
Không tự coi quyết định OPEN là yêu cầu đã được chốt.
HLD Phần B ưu tiên khi xung đột với Phần A.

## Kiến trúc backend
- Java 21, Spring Boot, PostgreSQL và Flyway.
- Giữ package com.toeic.backend.
- Giữ kiến trúc modular monolith.
- Tổ chức trong các module hiện có:
  api, application, domain, infrastructure.
- Bảo toàn chính sách tại docs/Password_Recovery.md: đăng ký ACTIVE ngay, không OTP và không tự đăng nhập; OTP chỉ dùng khôi phục mật khẩu. Không khôi phục verify-email/resend-otp đăng ký đã gỡ; không đánh dấu email đã xác minh khi chưa có bằng chứng.
- Không sửa hoặc xóa migration đã áp dụng.
- Khi thay đổi schema, thêm migration mới.
- Không đưa mật khẩu, token hoặc API key vào code hay log.

## Cách triển khai
- Làm từng luồng nghiệp vụ với phạm vi rõ ràng.
- Bao gồm API, service, persistence và test liên quan.
- Tái sử dụng code hiện có, tránh tạo chức năng trùng lặp.
- Khi đổi API contract, cập nhật tài liệu và test liên quan.
- Giải thích trách nhiệm từng service bằng tiếng Việt.

## Kiểm thử
- Chạy test phù hợp với thay đổi.
- Chạy toàn bộ backend test trước khi bàn giao nếu môi trường cho phép.
- Trong backend, dùng Maven Wrapper:
  Windows: .\mvnw.cmd test
  Linux/macOS: bash ./mvnw test
- Báo rõ lệnh đã chạy, kết quả và phần chưa kiểm tra.
- Không tuyên bố test đạt nếu chưa chạy thành công.

## Git workflow
- Kiểm tra nhánh và working tree trước khi sửa.
- Code và commit trên nhánh feature.
- Không commit trực tiếp lên develop hoặc master.
- Quy trình:
  feature → test → commit/push → PR → review/CI → develop.
- Không tự merge PR.
## Kiểm tra trước và sau mỗi nhiệm vụ
- Trước khi sửa, chạy git branch --show-current và git status.
- Không ghi đè thay đổi có sẵn của người khác.
- Sau khi sửa, kiểm tra git diff --check và diff liên quan.
- Chạy test phù hợp với thay đổi; sửa lỗi rồi chạy lại.
- Thay đổi chỉ tài liệu không bắt buộc chạy Maven.
- Sau khi push, kiểm tra CI của đúng commit nếu có quyền truy cập;
  nếu không truy cập được, báo rõ chưa xác minh.
- Bàn giao: chức năng đã làm, file thay đổi, test và phần còn thiếu.
