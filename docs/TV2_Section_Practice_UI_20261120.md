# TV2 — Part1–7 và cấu hình/khôi phục Section Listening

Base tích hợp012c257 chứa develop a1a85b6; author/committer TV2 pthanh137
<sleapf137@gmail.com>. Không sửa backend/migration/ADMIN trong commit TV2.

## Rà phạm vi và dependency

Không tìm thấy backlog/AC độc lập trong repo hoặc issue GitHub không phải PR.
Đối chiếu các quyết định đã chốt trong SRS/HLD phầnB/LLD/API và runbook runtime;
không coi DESIGN/OPEN là quyết định mới.

| Mục | Đã có | Thiếu/dependency/OPEN |
|---|---|---|
| CONTENT-01 | GET published/current theo visibility, DTO an toàn | Media delivery/progress ngoài contract đọc |
| CONTENT-02 | Search, bookmark/history own, permission/transaction | Không tự gán completion |
| CONTENT-03 | Draft authoring/version/archive own/any | G1/media chưa chốt/ngoài phạm vi; không mở ADMIN |
| Q-01 | Domain/mapping version/choice và safe mapper | Không tạo mapping Answer trùng |
| Q-02 | Đọc current published cho quyền Teacher/Admin | Không dùng làm endpoint Learner |
| Q-03 | Authoring câu/group, pin/version/history | Batch publish G1 và media giữ giới hạn cũ |
| PRAC-01 | Backend Part1–7, nguyên nhóm, snapshot | UI develop chỉ chọn5–7; mở selector1–7 trong lượt này |
| PRAC-02 | Save/unselect, submit/result atomic, source/owner/time guards | Không đổi contract server hoặc expiry Practice24h |
| EXAM-01 | Backend Listening/Reading, Reading UI đã có | Bổ sung Listening selection và nhãn phiên phục hồi; chưa phát media |
| EXAM-02 | TestVersion schema có sẵn | Full Mock runtime chưa vào develop, phụ thuộc EXAM-04 |
| EXAM-03 | Submit Section muộn khi có request đã có | Worker tự nộp chưa có; không tự chốt scope scheduler |
| EXAM-04 | PR61 có mã riêng nhưng chưa merge | V26 exam_workflow trùng V26 grading; submit/result aliases trùng routes hiện tại; không nhập nhánh thành viên hay viết lại |
| EXAM-05 | Raw/accuracy/Part statistics đã có | Chưa quy đổi TOEIC; governance conversion còn OPEN |
| NFR-02 | Có benchmark/report performance từ nhóm | Workload p95 mới chưa được chốt/đo; không nhận performance đạt |

PR61 được kiểm qua GitHub: open, merged=false, head66e71a2, base develop.
Route management /teacher/tests và TestVersion workflow chưa có trên cây đồng bộ.
Không lấy code PR61 vào lượt này. Phần UI chọn1–7/Listening dùng contract PRAC-01
đã chốt10/11 và EXAM-01 đã chốt11/11, không phụ thuộc TestVersion/EXAM-04.

## Phạm vi và AC

- Giữ luồng đang chạy PracticeSetupPage→AttemptPage→ResultPage và repository/mapper
  hiện có. Reading vẫn mặc định; thêm lựa chọn LISTENING tường minh trong setup.
  PracticePage/practiceApi cũ không được router dùng: không thay hoặc đưa vào commit.
- Practice chọn một/nhiều Part1–7,1–200 câu; Section chọn Listening1–4 hoặc
  Reading5–7,1–100 câu/1–120 phút. Backend quyết định tổ hợp đủ/mọi Part/nguyên nhóm.
- Không thêm partNumbers hoặc practice vào Section payload; không fallback khi409.
- Tiếp tục dùng nhãn Section từ session.section.type qua mapper hiện có khi mở
  lại/reload; không viết lại mapper hoặc luồng Attempt/Result. Giữ expiry/timer, khóa save và bodyless/manual submit.
- Hiển thị giới hạn chưa phát protected audio/media. Không trả/đọc URL, không
  triển khai playback, Full Mock, auto-submit hoặc quy đổi điểm.

Test Node kiểm wire contract của chính production adapter; browser fixture kiểm
Listening/Reading+reload, Part1/2 request,409 không fallback và expired Listening
khóa answers/manual bodyless submit. HTTP/DB thật được ghi riêng với fixture ngân
hàng published, không gọi đó là dữ liệu production hoặc clone DB thành viên.

Kết quả cuối, nguồn staged và manifest được ghi trong backend/target/tv2-* audit.
Backend không đổi so với commit tích hợp đã kiểm1160 test; không chạy full backend
lặp cho thay đổi chỉ frontend. Không dùng CI1140/WT1150 làm kết quả cây mới.
