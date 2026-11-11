# Luyện Reading Part 5–7 — frontend Learner

Vào /tests → Luyện Reading Part 5–7, hoặc /practice (cần đăng nhập remote).
Nhập 1–200 câu. Không gọi /tests của backend, không sử dụng adapter đề mẫu
RemoteAssessmentRepository cũ. Bài mẫu guest vẫn chạy riêng tại /tests và /attempts.

practice.api.js dùng contract PRAC-01/02:
- POST /api/v1/attempts: sourceType PRACTICE_SNAPSHOT, practice.partNumbers là các Part Reading đã chọn trong [5,6,7], questionCount.
- GET /attempts/{id}: đọc snapshot và answers; không tự sinh deadline hoặc đáp án.
- PUT /attempts/{id}/answers/{questionVersionId}: selectedChoiceId UUID hoặc null.
- POST /attempts/{id}/submit: không body; kết quả chấm đồng bộ.
- GET /attempts/{id}/result: đọc kết quả persisted sau GRADED.

/practice/{attemptId} cho phép reload/tiếp tục bằng URL. Form mở mã phiên không
thay quyền ownership server. Frontend khóa toàn bộ lựa chọn khi đang ghi; chỉ
hiển thị lựa chọn mới khi HTTP thành công, không tự retry request ghi.
Submit chờ ghi xong. Không tự submit khi hết hạn vì runtime practice từ chối
save/submit sau expiresAt; chỉ đọc phiên hết hạn. Dùng giờ thiết bị để vô hiệu hóa
sớm trên UI; server là nơi kiểm tra cuối cùng.

Kết quả hiển thị rawScore/count/accuracy và explanation theo questionVersionId;
không suy ra điểm TOEIC. Giữ choiceId trong request, label chỉ dùng hiển thị.
Section Reading: tại /practice chọn Thi Section Reading, nhập 1–100 câu và
1–120 phút. POST sourceType SECTION_SNAPSHOT với section {type:READING,
questionCount,durationMinutes}. Backend bắt buộc đủ Part 5–7 và nguyên nhóm.
Đồng hồ dùng expiresAt của phiên, không reset khi reload. Hết hạn khóa lưu,
nhưng Section vẫn cho submit đáp án đã lưu; không tự nộp, không worker client.
Practice thường vẫn chặn submit khi hết hạn. Kết quả dùng chung giao diện hiện có.

Part 6–7 hiển thị passage/instruction đúng snapshot cạnh các câu thuộc nhóm;
server chọn nguyên nhóm, không tự cắt nhóm để đủ số câu. Kết quả dùng
ExamScoreSummary và CSS kết quả có sẵn: theo Part, bản đồ câu hỏi, lọc đúng/sai
và bỏ trống. Chưa nối Listening/media hay Full Mock. Không sửa backend.

Kiểm tra: npm.cmd test; npm.cmd run build; npm.cmd run lint.
Browser fixture: node scripts/practice-ui-smoke.mjs (Chrome, preview build port 5174).
Test fixture không chứng minh ngân hàng thật đủ câu hỏi published. Cần thử bằng
tài khoản Learner với permission practice:manage:self và dữ liệu Part 5 đã xuất bản.
