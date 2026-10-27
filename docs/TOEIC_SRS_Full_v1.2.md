# TOEIC Learning Platform — Software Requirements Specification v1.2

> **Trạng thái:** Consolidated design-review draft. Nguồn gốc: SRS v1.0.1 (09/2026), requirements baseline v1.1, ERD v1.2, HLD v1.0 và 13 cải tiến Design Review. Các yêu cầu bổ sung là thay đổi đề xuất cần phê duyệt trước khi freeze.

## 1. Giới thiệu

### 1.1 Mục đích
Đặc tả yêu cầu cho hệ thống học TOEIC đa nền tảng, phục vụ phân tích, thiết kế, triển khai và kiểm thử; phân biệt yêu cầu nghiệp vụ với giải pháp kỹ thuật.

### 1.2 Phạm vi
- Learner: học nội dung, luyện Part, Section/Full Mock, Dictation, Shadowing, vocabulary SRS, analytics, AI Tutor.
- Teacher: quản lý học liệu, ngân hàng câu hỏi, phiên bản, blueprint và bộ đề.
- Admin: quản lý user/role, cấu hình, score conversion, AI quota/log và notification.
- Ngoài phạm vi MVP: chấm điểm ETS chính thức, giám sát thi từ xa, thanh toán, push/email đa kênh, microservices phức tạp.

### 1.3 Thuật ngữ
| Thuật ngữ | Ý nghĩa |
|---|---|
| Attempt | Một lượt làm bài có tập câu hỏi bất biến |
| QuestionVersion | Phiên bản câu hỏi và đáp án được đóng băng |
| QuestionGroupVersion | Phiên bản instruction/passage/audio dùng chung |
| TestVersion | Phiên bản bộ đề bất biến sau publish |
| Blueprint | Cấu trúc số câu/nhóm theo Part cho một loại đề |
| SRS/SM-2 | Ôn tập giãn cách dựa trên đánh giá nhớ từ |
| RAG | Retrieval-Augmented Generation |
| Score conversion | Bảng quy đổi ước tính, không phải điểm ETS chính thức |

## 2. Actors và quyền
| Actor | Quyền tiêu biểu |
|---|---|
| Guest | Đăng ký, đăng nhập, xem thông tin công khai |
| Learner | Học, thi, xem kết quả cá nhân, SRS, AI Tutor |
| Teacher | Soạn, duyệt theo phân quyền, xuất bản học liệu/câu hỏi/đề |
| Admin | RBAC, audit, cấu hình, conversion table, AI usage |

## 3. Yêu cầu chức năng — Use Case Catalogue

### UC-01 — Đăng ký tài khoản

**Actor chính:** Guest. **Tiền điều kiện:** chưa có tài khoản.

**Luồng chính:**
1. Nhập thông tin đăng ký hợp lệ.
2. Hệ thống kiểm tra email trùng và validate.
3. Tạo user với mật khẩu băm và trạng thái phù hợp.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-02 — Đăng nhập và phân quyền

**Actor chính:** Learner. **Tiền điều kiện:** tài khoản tồn tại.

**Luồng chính:**
1. Nhập thông tin xác thực.
2. Xác minh mật khẩu/trạng thái.
3. Phát hành phiên đăng nhập/token và tải quyền.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-03 — Học nội dung TOEIC

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Chọn học liệu đã published.
2. Hiển thị đúng LearningContentVersion và media.
3. Lưu tiến độ và phiên bản đã học.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-04 — Luyện tập TOEIC theo Part

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Chọn Part, topic, difficulty/tags.
2. Sinh bộ câu hỏi và đóng băng AttemptQuestionSnapshot.
3. Làm bài, nộp và xem kết quả.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-05 — Thực hiện Section Test

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Chọn Section Test đã published.
2. Kiểm tra giới hạn lượt và tạo Attempt gắn TestVersion.
3. Autosave, submit hoặc auto-submit, chấm điểm.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-06 — Thực hiện Full Mock Test

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Chọn Full Mock Test.
2. Tải TestVersion và blueprint đã xác thực.
3. Làm Listening/Reading; nộp bài; hiển thị raw counts và điểm ước tính nếu có bảng quy đổi.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-07 — Luyện Daily Dictation

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Chọn bài Dictation.
2. Nghe audio và nhập câu trả lời.
3. So sánh với transcript và lưu kết quả.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-08 — Luyện Shadowing

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Chọn bài Shadowing.
2. Nghe mẫu, ghi âm/luyện nói.
3. Lưu attempt và feedback trong giới hạn khả năng đã triển khai.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-09 — Quản lý và ôn tập từ vựng cá nhân

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Tạo/chọn deck, thêm vocabulary lexical sense.
2. Xem flashcard đến hạn và chọn Again/Hard/Good/Easy.
3. Tính lịch SM-2, cập nhật state, ghi history nguyên tử.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-10 — Theo dõi kết quả và tiến độ

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Xem dashboard cá nhân.
2. Tổng hợp kết quả Part/Attempt/SRS và lỗi thường gặp.
3. Đưa ra khuyến nghị có thể giải thích.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-11 — Sử dụng RAG-based AI Tutor

**Actor chính:** Learner. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Gửi câu hỏi.
2. Kiểm tra quyền/quota, retrieval từ tài liệu được cấp quyền.
3. Trả lời có nguồn hoặc cảnh báo fallback, ghi AI usage.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-12 — Quản lý học liệu

**Actor chính:** Teacher/Admin. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Tạo bản nháp LearningContentVersion.
2. Duyệt và xuất bản phiên bản theo quyền.
3. Archive phiên bản cũ mà không xóa lịch sử.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-13 — Quản lý ngân hàng câu hỏi

**Actor chính:** Teacher/Admin. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Tạo câu hỏi/nhóm và phiên bản.
2. Gắn tags và error categories.
3. Review/approve/publish với kiểm tra đáp án, media và audit.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-14 — Quản lý bộ đề

**Actor chính:** Teacher/Admin. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Tạo TestVersion và chọn blueprint.
2. Kiểm tra số câu, số nhóm, phiên bản nội dung.
3. Publish đề bất biến hoặc retire phiên bản.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

### UC-15 — Quản lý người dùng và phân quyền

**Actor chính:** Teacher/Admin. **Tiền điều kiện:** đã xác thực và được cấp quyền tương ứng.

**Luồng chính:**
1. Quản lý user, role và permission.
2. Ghi audit thay đổi đặc quyền.
3. Áp dụng quyền ngay theo chính sách phiên đăng nhập.

**Ngoại lệ:** đầu vào không hợp lệ; thiếu quyền; bản ghi không tồn tại; trạng thái không cho phép; lỗi mạng. Giao dịch phải an toàn, không phát sinh dữ liệu nửa chừng.

**Hậu điều kiện:** nghiệp vụ thành công được lưu với timestamp/audit thích hợp; thất bại không làm thay đổi trạng thái trái phép.

## 4. Business Rules (BR) và Acceptance Criteria (AC)

| ID | Quy tắc bắt buộc | Tiêu chí nghiệm thu |
|---|---|
| BR-01 / AC-01 | Phiên bản Question/Group/Test published bất biến | Chỉnh sửa phải tạo version mới; Attempt cũ không đổi |
| BR-02 / AC-02 | QuestionVersion state machine DRAFT→IN_REVIEW→APPROVED→PUBLISHED→ARCHIVED | Không cho publish khi chưa approved/thiếu quyền |
| BR-03 / AC-03 | TestVersion DRAFT→PUBLISHED→RETIRED | Retire không xóa đề khỏi Attempt cũ |
| BR-04 / AC-04 | LearningContentVersion DRAFT→PUBLISHED→ARCHIVED | Citation/learning history trỏ version cụ thể |
| BR-05 / AC-05 | Tagging câu hỏi hỗ trợ nhiều tags | Filter theo tag hoạt động |
| BR-06 / AC-06 | Blueprint kiểm tra số câu và nhóm | Đề sai blueprint không publish |
| BR-07 / AC-07 | Attempt phân biệt TEST_VERSION và PRACTICE_SNAPSHOT | Mỗi Attempt chỉ có một nguồn câu hỏi bất biến |
| BR-08 / AC-08 | Server quản lý expires_at | Client chỉnh đồng hồ không kéo dài thời gian |
| BR-09 / AC-09 | Submit idempotent | Nộp lặp trả cùng kết quả |
| BR-10 / AC-10 | Autosave sau submit bị từ chối | Không ghi đè bài đã chấm |
| BR-11 / AC-11 | max_attempts NULL nghĩa là vô hạn | Giới hạn >0 được kiểm tra đồng thời |
| BR-12 / AC-12 | Bảng quy đổi điểm có version bất biến | Không có bảng hợp lệ chỉ hiển thị số câu đúng |
| BR-13 / AC-13 | Điểm TOEIC chỉ là estimated | UI không ghi official ETS |
| BR-14 / AC-14 | Grading snapshot dựa trên đáp án của version/snapshot đã dùng | Câu hỏi đổi đáp án không thay đổi điểm lịch sử |
| BR-15 / AC-15 | SRS 1 Vocabulary=1 Flashcard trong MVP | UNIQUE user+vocabulary; lịch sử review append-only |
| BR-16 / AC-16 | Rating Again/Hard/Good/Easy | Lịch ôn thay đổi theo rating; history đầy đủ |
| BR-17 / AC-17 | AI truy xuất nguồn theo quyền | Nguồn trái phép không xuất hiện trong prompt/citation |
| BR-18 / AC-18 | Không có nguồn tin cậy thì hiển thị cảnh báo | Hiển thị chính xác: Không tìm thấy tài liệu tham khảo trong hệ thống. |
| BR-19 / AC-19 | AI lỗi không làm hỏng core | Học/thi/SRS vẫn hoạt động |
| BR-20 / AC-20 | AI usage log ghi provider/model/tokens/latency/cost/status | Thống kê chi phí có thể tổng hợp |
| BR-21 / AC-21 | Notification in-app cho review due/score/content/recommendation/quota | Mỗi user xem đúng thông báo của mình |
| BR-22 / AC-22 | Event catalogue/outbox đảm bảo không mất sự kiện | Retry không tạo notification trùng |
| BR-23 / AC-23 | Error categories cho analytics | Tổng hợp sai theo loại, không dùng để suy đoán chắc chắn nguyên nhân |
| BR-24 / AC-24 | Media được tham chiếu bởi published version không hard-delete | Xóa bị chặn hoặc chuyển soft delete |
| BR-25 / AC-25 | RBAC và audit | Người không có quyền không thể publish/escalate role |

## 5. Publish workflow và transition permissions

| Đối tượng | Chuyển trạng thái | Điều kiện |
|---|---|---|
| QuestionVersion | DRAFT→IN_REVIEW | Đủ dữ liệu và người gửi có quyền |
| QuestionVersion | IN_REVIEW→APPROVED | Reviewer khác tác giả nếu áp dụng separation-of-duties |
| QuestionVersion | APPROVED→PUBLISHED | Publisher có quyền; media/answer hợp lệ |
| QuestionVersion | PUBLISHED→ARCHIVED | Không ảnh hưởng attempts đã bắt đầu |
| TestVersion | DRAFT→PUBLISHED | Blueprint hợp lệ, đủ câu và group |
| TestVersion | PUBLISHED→RETIRED | Không nhận attempt mới theo chính sách |
| LearningContentVersion | DRAFT→PUBLISHED→ARCHIVED | Citation và progress giữ version cũ |

## 6. NFR — yêu cầu phi chức năng

| ID | Thuộc tính | Mục tiêu nghiệm thu đề xuất |
|---|---|---|
| NFR-01 | Security | HTTPS, password hash mạnh, RBAC ở API và service, không tin role từ client |
| NFR-02 | Performance | API thông thường p95 <500 ms trong workload benchmark được chốt |
| NFR-03 | Reliability | Submit idempotent; retry grading/outbox an toàn |
| NFR-04 | Availability | AI outage không làm gián đoạn thi/học/SRS |
| NFR-05 | Integrity | Không mất câu trả lời hợp lệ; published content bất biến |
| NFR-06 | Compatibility | React Web và React Native dùng chung API contract |
| NFR-07 | Observability | Request ID, structured logs, health check, AI usage logs |
| NFR-08 | Privacy | Không log plaintext password, token, prompt nhạy cảm mặc định |
| NFR-09 | Maintainability | Module boundaries và automated tests |
| NFR-10 | Accessibility | UI có nhãn, điều hướng bàn phím, feedback lỗi rõ ràng |

> Các ngưỡng hiệu năng và availability phải được thầy/nhóm xác nhận, không coi là chỉ tiêu đã đo.

## 7. Expanded test matrix

| Nhóm | Test scenario | Expected |
|---|---|---|
| Concurrency | Hai request start attempt cùng lúc | Không vượt max_attempts |
| Concurrency | Submit đồng thời | Chỉ một lần grading hiệu lực |
| Concurrency | Autosave đua với submit | Không ghi sau submitted |
| Versioning | Publish Question/Test mới giữa attempt | Attempt giữ version cũ |
| Security | Unauthorized RAG retrieval | Bị chặn, không lộ chunk |
| Security | Role escalation/permission bypass | HTTP 403 và audit |
| Integrity | Xóa media đã được tham chiếu | Bị chặn/soft-delete |
| Integrity | Retire conversion table | Kết quả lịch sử giữ conversion cũ |
| Integrity | Snapshot mismatch | Không được chấm theo question khác |
| Recovery | Crash giữa grading | Retry idempotent và nhất quán |
| Recovery | Outbox retry nhiều lần | Consumer idempotent |
| AI | Không có nguồn hợp lệ | Cảnh báo fallback, không giả citation |
| SRS | Review đồng thời | Không ghi state sai, history không trùng |

## 8. Requirements Traceability Matrix

| Use Case | Module HLD | Bảng dữ liệu trọng tâm |
|---|---|---|
| UC-01 Đăng ký tài khoản | Identity | `users,roles` |
| UC-02 Đăng nhập và phân quyền | Identity | `users,user_roles` |
| UC-03 Học nội dung TOEIC | Learning | `learning_contents,learning_content_versions,learning_progress` |
| UC-04 Luyện tập TOEIC theo Part | Assessment | `questions,attempt_question_snapshots` |
| UC-05 Thực hiện Section Test | Assessment | `test_versions,attempts` |
| UC-06 Thực hiện Full Mock Test | Assessment | `test_versions,score_conversion_tables` |
| UC-07 Luyện Daily Dictation | Listening | `dictation_exercises,dictation_attempts` |
| UC-08 Luyện Shadowing | Listening | `shadowing_exercises,shadowing_attempts` |
| UC-09 Quản lý và ôn tập từ vựng cá nhân | Vocabulary/SRS | `vocabularies,review_states,review_history` |
| UC-10 Theo dõi kết quả và tiến độ | Analytics | `part_performance,user_error_statistics` |
| UC-11 Sử dụng RAG-based AI Tutor | AI | `ai_conversations,knowledge_chunks,ai_request_logs` |
| UC-12 Quản lý học liệu | Learning | `learning_content_versions` |
| UC-13 Quản lý ngân hàng câu hỏi | Question Bank | `question_versions,question_group_versions,tags` |
| UC-14 Quản lý bộ đề | Assessment/Question Bank | `test_blueprints,test_versions,test_questions` |
| UC-15 Quản lý người dùng và phân quyền | Identity/System | `users,roles,permissions,audit_logs` |

## 9. Change control / open issues

- SRS v1.0.1 là nguồn use case gốc; v1.2 là bản thiết kế hợp nhất có bổ sung các mục review. Nếu có mâu thuẫn về workflow/versioning, áp dụng yêu cầu mới sau khi được phê duyệt.
- Xác nhận role Teacher có quyền approve/publish hay cần Reviewer/Publisher riêng.
- Xác nhận notifications chỉ in-app cho MVP và cách gửi reminder định kỳ.
- Xác nhận chỉ tiêu NFR đo được, quota AI, retention logs, privacy policy và mô hình tính phí.
- Xác nhận việc lưu tag/error category ở question identity hay question version.
- SRS chưa được ký duyệt; không tuyên bố requirements frozen.