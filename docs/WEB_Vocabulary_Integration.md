# Luồng từ vựng Learner trên web

Chế độ thật: VITE_AUTH_MODE=remote, VITE_USE_MOCK=false. Vào /vocabulary,
chọn bộ cá nhân hoặc bắt đầu bộ catalog để sao chép vào tài khoản.

Trang chi tiết hỗ trợ tạo từ, sửa bộ, xóa bộ, sửa từ, gỡ từ khỏi bộ,
xóa từ khỏi sổ tay và xem lịch sử ôn. Thao tác xóa có xác nhận và chỉ cập nhật
giao diện sau HTTP thành công. PUT từ tải DTO gốc trước khi sửa để giữ audio
resource và metadata không hiển thị. Sửa từ áp dụng cho mọi bộ chứa từ đó.

API tái sử dụng /api/v1/vocabulary-decks, /vocabularies và các route con
flashcards, vocabularies/{vocabularyId}, reviews/history, learning-session.
Không thay đổi payload API. Rating runtime là số 1–4; các enum trong thiết kế
cũ không được gửi trực tiếp xuống backend.

Danh sách/chi tiết bộ và màn hình ôn dùng phiên server làm nguồn tiến độ khi
remote; không che lỗi tải phiên bằng dữ liệu local. Bộ mới chưa có phiên là 0%.
Tiến độ lượt học dùng completedIds: từ có intervalDays >= 1 trong phản hồi
đánh giá được hoàn thành trong lượt hiện tại (Khó/Tốt/Dễ theo scheduler hiện có).
Học lại lưu deferredUntil bằng nextReviewAt, chỉ xuất hiện khi đến hạn; reload
giữ thời điểm chờ. Phiên cũ easyIds được đọc tương thích và nâng cấp bằng trạng
thái SRS của các từ đã đánh giá trong phiên; không tự hoàn thành từ chưa học.
Hoàn thành lượt học khác đã thành thạo từ. Local vẫn lưu bản dự phòng khi ghi
snapshot thất bại, có thông báo; đánh giá SRS và lưu snapshot là hai request.
Chưa có optimistic concurrency giữa nhiều tab/thiết bị.

Backend giữ nguyên theo phạm vi frontend. Migration V31 và test backend thêm
trong lượt tích hợp đã được gỡ khỏi source theo yêu cầu người dùng. API xóa
hiện có có thể bị khóa ngoại chặn khi từ có liên kết/lịch sử ôn hoặc bộ được
tạo từ catalog; frontend hiển thị lỗi và không báo thành công giả. Cần chuyển
vấn đề này cho người phụ trách backend. Không tự sửa Flyway history/database.

Trách nhiệm service giữ nguyên: VocabularyService quản lý từ thuộc tài khoản;
VocabularyDeckService quản lý bộ; DeckFlashcardService quản lý liên kết;
VocabularyReviewService ghi đánh giá/lịch SRS; VocabularyLearningSessionService
lưu snapshot lượt học; VocabularyCatalogEnrollmentService sao chép catalog.

Kiểm tra thủ công: tạo bộ → thêm từ → sửa từ → đánh giá → xem lịch sử →
reload/đổi thiết bị kiểm tiến độ → gỡ từ (sổ tay giữ từ) → xóa từ đã ôn →
xóa bộ catalog rồi bắt đầu lại. Thử lỗi mạng và tài khoản không sở hữu dữ liệu.
