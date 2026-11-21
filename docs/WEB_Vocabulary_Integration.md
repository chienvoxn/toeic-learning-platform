# Luồng từ vựng Learner trên web

Chế độ thật: VITE_AUTH_MODE=remote, VITE_USE_MOCK=false. Vào /vocabulary,
chọn bộ cá nhân hoặc bắt đầu bộ catalog để sao chép vào tài khoản.

Trang chi tiết hỗ trợ tạo từ, sửa bộ, xóa bộ, sửa từ, gỡ từ khỏi bộ,
xóa từ khỏi sổ tay và xem lịch sử ôn. Thao tác xóa có xác nhận và chỉ cập nhật
giao diện sau HTTP thành công. PUT từ tải DTO gốc trước khi sửa để giữ audio
resource và metadata không hiển thị. Sửa từ áp dụng cho mọi bộ chứa từ đó.

API tái sử dụng /api/v1/vocabulary-decks, /vocabularies và các route con
flashcards, vocabularies/{vocabularyId}, reviews/history, learning-session.
Rating runtime là số 1–4. Màn hình học theo bộ gửi rating qua
`POST /vocabulary-decks/{deckId}/learning-session/reviews`. Màn hình ôn đến
hạn tiếp tục dùng `POST /vocabularies/{id}/reviews`.

Danh sách/chi tiết bộ và màn hình ôn dùng phiên server làm nguồn tiến độ khi
remote; không che lỗi tải phiên bằng dữ liệu local. Bộ mới chưa có phiên là 0%.
Tiến độ lượt học dùng completedIds: từ có intervalDays >= 1 trong phản hồi
đánh giá được hoàn thành trong lượt hiện tại (Khó/Tốt/Dễ theo scheduler hiện có).
Học lại lưu deferredUntil bằng nextReviewAt, chỉ xuất hiện khi đến hạn; reload
giữ thời điểm chờ. Phiên cũ easyIds được đọc tương thích và nâng cấp bằng trạng
thái SRS của các từ đã đánh giá trong phiên; không tự hoàn thành từ chưa học.
Hoàn thành lượt học khác đã thành thạo từ. Trong chế độ thật, server lưu
rating, lịch ôn, history và snapshot trong cùng giao dịch. Mỗi thao tác ghi
gửi `expectedRevision` đã đọc. Khi nhận 409, web tải phiên mới nhất để người
học xem lại. Khi mất phản hồi của rating, nút thử lại gửi cùng nội dung và
revision. Nếu yêu cầu trước đã lưu, retry nhận 409 và web tải phiên đã lưu,
vì vậy rating chỉ được ghi một lần. Reset lưu phiên rỗng với revision mới.
Chế độ mock tiếp tục lưu phiên cục bộ.

Xóa từ khỏi sổ tay ẩn từ khỏi danh sách, mọi bộ, hàng ôn và gợi ý. Lịch sử
đã ghi vẫn tính vào tổng lượt ôn và chuỗi ngày học. API theo ID của từ
đã xóa trả 404. Gỡ khỏi bộ chỉ xóa liên kết với bộ đã chọn.
Xóa bộ cá nhân giữ các từ trong sổ tay, đồng thời xóa liên kết, phiên học
và enrollment của bộ. Khi bắt đầu lại từ catalog, server tạo bản sao mới.

Trách nhiệm service giữ nguyên: VocabularyService quản lý từ thuộc tài khoản;
VocabularyDeckService quản lý bộ; DeckFlashcardService quản lý liên kết;
VocabularyReviewService ghi đánh giá/lịch SRS; VocabularyLearningSessionService
lưu snapshot lượt học; VocabularyCatalogEnrollmentService sao chép catalog.

Kiểm tra thủ công: tạo bộ → thêm từ → sửa từ → đánh giá → xem lịch sử →
reload/đổi thiết bị kiểm tiến độ → gỡ từ (sổ tay giữ từ) → xóa từ đã ôn →
xóa bộ catalog rồi bắt đầu lại. Thử lỗi mạng và tài khoản không sở hữu dữ liệu.

Ôn đến hạn gửi `Idempotency-Key` UUID cho mỗi lượt. Khi chưa xác nhận được
kết quả, người học nhấn lại cùng mức đánh giá để gửi lại key đó. Key được giữ trong sessionStorage của tab để thử lại sau reload.

Trang Vocabulary tải `GET /api/v1/vocabulary/recommendations` trong chế độ
remote. Kết quả DUE hiển thị đã đến lịch ôn, NEW hiển thị chưa từng ôn.
Người học có thể mở trực tiếp từ được gợi ý và gửi đánh giá bằng cùng luồng
ôn đến hạn. Khi API lỗi, trang cho phép thử lại và không hiển thị danh sách giả.

Learning Profile và Dashboard hiển thị ngày theo UTC, phù hợp với backend hiện
tại. Từ đã ôn là số từ đang hoạt động có review state đã đánh giá. Tổng lượt
ôn là số review history, kể cả lượt của từ đã xóa khỏi sổ tay. Thành thạo là
repetitionCount ít nhất 5. Tiến độ lượt học là số thẻ đã hoàn thành trong phiên,
khác số từ thành thạo. Quyết định dùng ngày địa phương vẫn cần nhóm chốt.
