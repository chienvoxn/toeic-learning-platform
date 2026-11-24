# TV2 Sprint 3 — working tree 2026-11-24

Baseline thực tế `24839e027d7c5bdf0813db077fdf89a09444bd26`, nhánh
`feat/web-auth-integration`; chưa stage/commit/push. Phần hỗ trợ G1/TV1 tách tại
[G1_TV2_Support_20261124.md](G1_TV2_Support_20261124.md), không gộp vào commit TV2.

Lượt review/commit riêng ngày 2026-11-24 giữ nguyên mốc working tree bên dưới.
Thứ tự TV2 rồi G1: code hai phần độc lập; API spec có hai hunk stage riêng.
G1 được bàn giao ở commit kế tiếp, không suy rằng code G1 nằm trong commit TV2.
Bằng chứng nguồn staged của từng phạm vi được ghi riêng khi kiểm chứng xong;
416 kiểm tra qua hai lượt không được đổi nhãn thành full pass hoặc staged pass.

## Remote và phạm vi

Fetch lại xác nhận `origin/develop=587b59805e1f8829b6e88b1aafa648260c69d0eb`;
remote feature bằng HEAD. Sáu commit develop mới so với merge base:
`ad26bb4`, `50c9421`, `b746846`, `d67a2a3`, `b3703ef`, `587b598`.
Không có migration mới trong phần incoming. Không nhập develop trong lượt này:

| Phần nhóm đã làm | Xung đột cần kết hợp trước khi merge |
|---|---|
| FullTestAttemptController/FullTestSessionService | Thêm `/api/v1/full-tests/{version}/attempts`, hard-code 200 câu/120 phút; khác contract TEST_VERSION/FULL_MOCK pin blueprint và routes attempts hiện tại |
| TeacherTestController/TeacherTestAssemblyController | Trùng `/api/v1/teacher/tests`; lắp các câu DRAFT của owner, khác expectedRevision, publication/contributor và pin nguyên nhóm hiện có |
| Teacher/Assessment FE | Thay layout/router/catalog/attempt UI trên nhánh phân kỳ; cần giữ các route remote, moderation và timeout đã bàn giao |

Đã đọc code incoming; không viết lại các chức năng đó, không lấy cả nhánh thành viên
và không gọi nhánh hiện tại là đã chứa develop mới.

## AC và giới hạn

| AC | Backend | UI/dữ liệu | Giới hạn / bằng chứng |
|---|---|---|---|
| EXAM-02 Full Mock | TEST_VERSION/PUBLISHED/current/offered; pin question/group/order/blueprint/deadline; own save/submit/result | Có chọn đề/runtime remote hiện hữu | FULL-LR/v1 và chuyển Section vẫn OPEN; không seed. MINI-LAB là fixture kiểm thử |
| EXAM-03 | Worker bounded batch/SKIP LOCKED/durable retry; không dùng token; submit tay và worker nguyên tử | Timer expiresAt, khóa sửa, polling kết quả hiện hữu | Chỉ TEST_VERSION/FULL_MOCK có snapshot. Practice/Section không tự chốt; restart worker instance trong JUnit khác restart tiến trình BE |
| EXAM-04 | DRAFT/revision/ownership; review/publish/contributor/audit; fork current, một DRAFT; retire giữ pin | TeacherTestsPage hiện hữu | G1 thêm API batch riêng; chưa có UI chọn batch. Media grant liên actor vẫn là dependency |
| EXAM-05 | Kết quả đã lưu: raw/correct/wrong/unanswered/accuracy; Part stats nay trả cả Practice; lịch sử own có phân trang | `/practice/history`, mở kết quả hoặc phiên cũ; không fallback mock | Không quy đổi TOEIC. Transcript chỉ sau GRADED qua media API và quyền hiện hành |
| Listening/media | Adapter S3 hiện hữu, URL ký, quyền attempt/owner/source | Storage thật loopback; owner upload/playback/expiry qua API trên DB test đã kiểm | DB demo chỉ có Reading published, 0 media; P1 trả 409. Chưa đạt Learner Listening E2E. WAV là âm mẫu tự tạo, không phải audio TOEIC chính thức |
| NFR-02 | Chưa đo workload/p95 | Chưa đo | Không tuyên bố đạt p95 <500 ms |

## API EXAM-05 bổ sung

`GET /api/v1/attempts?page=0&size=20` dùng Bearer session, không nhận actorId.
Page bắt đầu 0; size 1–100 (mặc định 20); sort server
`started_at DESC, attempt_id DESC`. Cùng transaction REPEATABLE_READ cho count/data.

```json
{
  "items": [{
    "attemptId": "uuid", "sourceType": "PRACTICE_SNAPSHOT", "attemptType": "PRACTICE",
    "status": "GRADED", "startedAt": "server timestamp", "expiresAt": "server timestamp",
    "submittedAt": "server timestamp", "totalQuestions": 4,
    "score": {"correctCount": 1, "incorrectCount": 1, "unansweredCount": 2,
              "rawScore": 1, "accuracy": 25.00}
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

IN_PROGRESS không có score/submittedAt. Không trả câu, đáp án, explanation,
transcript, bank version content hoặc URL media trong lịch sử.
Lọc own và permission ở DB trước count/page: Practice cần `practice:manage:self`,
Section/TEST_VERSION cần `exam:manage:self`; TEST_VERSION thiếu snapshot không
được nhận diện thành runtime mới. Session identity bị kiểm lại tại service.
400 `VALIDATION_ERROR` cho query ngoài allowlist/duplicate/sai định dạng;
401 thiếu session, 403 thiếu quyền.

Năm route create/get/save/submit/result giữ nguyên. GET result và submit replay
đọc snapshot đã chấm, không chấm lại theo bank. `parts` được bổ sung cho Practice
bằng `attempt_part_results` và câu kết quả đã lưu; giữ nguyên semantics timed attempts.

## Blueprint — một gói đề xuất, chưa duyệt

Không tìm thấy quyết định phê duyệt giá trị cụ thể trong SRS/HLD/LLD hoặc runbook;
các số liệu cũ được ghi tham khảo/đề xuất. Gói đã gửi chờ xác nhận:

| Code/version | Part | Câu | Nhóm |
|---|---|---:|---|
| FULL-LR/v1 | 1 | 6 | 6 câu độc lập |
| FULL-LR/v1 | 2 | 25 | 25 câu độc lập |
| FULL-LR/v1 | 3 | 39 | 13 × 3 |
| FULL-LR/v1 | 4 | 30 | 10 × 3 |
| FULL-LR/v1 | 5 | 30 | 30 câu độc lập |
| FULL-LR/v1 | 6 | 16 | 4 × 4 |
| FULL-LR/v1 | 7 | 54 | 15 nhóm: 1 × 2 + 9 × 3 + 5 × 5 |

Tổng 200; Listening 45 phút, Reading 75 phút. Đề xuất server chuyển ở phút 45,
khóa Listening, không quay lại/không chuyển sớm, hết 120 phút worker chốt.
Đây là cấu hình sản phẩm đề xuất, không nhận là chuẩn chính thức ETS.
Chưa lưu DB, chưa thêm timing/Section lock tương ứng. MINI-LAB không thay thế gói này.

## Demo và dữ liệu

- FE `http://127.0.0.1:5173`; backend `http://127.0.0.1:8080`.
- Profile `local-demo`, datasource bắt buộc
  `jdbc:postgresql://127.0.0.1:55459/toeic_learning_demo`, role `toeic_demo`,
  migration mode `validate`, JPA `validate`, history V43.
- Credentials giữ trong file ignored có ACL `backend/target/local-demo-credentials.json`;
  không in password/token vào runbook. Hiện xác minh credentials Learner;
  không giả định password Teacher/Admin trong DB restore trùng seed.
- Storage thật SeaweedFS 4.55: `http://127.0.0.1:9000`, private bucket `toeic-media`;
  binaries/data/credential riêng tại `backend/target/demo-runtime-20261123/storage`.
  Không đổi owner/grant/publication bằng SQL. Nội bộ storage bind loopback.
  [Nguồn release chính thức](https://github.com/seaweedfs/seaweedfs/releases/tag/4.55),
  archive SHA256 `12dac2e97997aafb423e9cca3e4d6c9420eacac0291470c7a8b720b6c621f3f2`.
- Giữ 60s TTL khi kiểm expiry URL; reload xin URL mới qua API auth. URL đã cấp là
  bearer capability tới expiry, không phải quyền công khai bucket.
- Một DB test tái sử dụng: `toeic_tv2_sprint3_test_20261123` trên cluster demo 55459,
  khác DB demo. Không kết nối PostgreSQL cá nhân 5432/toeic_db.

Demo EXAM-05: login → Practice Part 5 → lưu/thay/bỏ → reload → nộp → mở đáp án →
`/practice/history` → xem kết quả → reload. Reading Section dùng đủ Part 5/6/7,
8 câu với bộ dữ liệu demo; thiếu tổ hợp trả 409, không tự cắt nhóm.
Thử Listening Part 1, 1 câu hiện trả 409: restore có 11 câu Reading published
(P5=4/P6=4/P7=3), 6 DRAFT đã archive, 0 media. Asset object-store không tự tạo
binding/publication DB. Không seed published hoặc grant bằng SQL để vượt chặn.
Muốn demo Learner Listening cần Teacher/Admin credentials hợp lệ, nội dung/media
được author/review/publish và workflow M1 grant liên actor; hiện chưa đủ.

## Bằng chứng lượt này

Mọi kết quả mới mang nhãn **working tree trên HEAD 24839e0**, không phải staged/CI.
Evidence/scripts/log tạm tại
`%TEMP%/toeic-tv2-sprint3-20261123-2r8nekrf`; bản tổng kết cuối sẽ lưu dưới
`backend/target/tv2-sprint3-20261124`.

- Trước delta: 2 Playwright Chrome qua FE–BE–DB thật đạt (Practice change/clear,
  reload/submit/replay/ownership và Reading Section Part stats); không route fixture.
- 100 FE node tests đạt; lint/build thành công, còn cảnh báo cũ và chunk lớn.
- Test lịch sử riêng: 12 đạt sau sửa cấu hình fixture Reading từ 8 về tổ hợp 6
  hợp lệ; không nới validation sản phẩm.
- Hồi quy backend 24 lớp: 416 tests, 234 đạt/182 lỗi khởi tạo context do PG max
  16 connections và cache nhiều Hikari pools, không assertion failure.
  Retry chỉ 7 lớp bị lỗi với pool 3/cache 2; không đổi PG hoặc skip guard.
- Retry 7 lớp: 182/182 đạt, 0 failure/error/skipped. Tổng 416 kiểm tra của 24 lớp
  đã đạt qua hai lượt (234 + 182), không gọi là một full backend pass.
- Package `mvnw.cmd -DskipTests package` thành công sau test; không dùng package
  skip làm bằng chứng test. JAR cố định `backend/target/tv2-sprint3-20261124/runtime.jar`,
  SHA256 `a171c54eb6fe2a5a711cf31f651093d709759d76340a886d44e407f50d9ad55c`.
  60 DB resources khớp byte source, không có entry trùng/stale. Production source
  fingerprint khớp bản đã package. Không thay SQL/coordinator.
- Browser sau delta: history FE–BE–DB thật đạt; storage harness qua API thật trên
  DB test đạt upload 201, byte SHA256 khớp, anonymous object 403, presign 401/403,
  native browser phát WAV/hiển thị SVG, URL hết hạn thật 403, cấp URL mới/reload đạt.
  Không route HTTP fixture, nhưng account/role fixture và native media renderer
  không phải UI Learner attempt. Lần thử Learner P1 bị chặn 409 do thiếu nguồn;
  không nhận là đã đạt audio/transcript Learner trước/sau submit trên demo.
- Trên JAR working tree cuối, chạy thêm đúng 2 scenario Practice/Reading bị ảnh
  hưởng qua FE–BE–DB: 2/2 đạt (10.4s). Tổng bằng chứng browser bản cuối là 3
  scenario sản phẩm (Practice, Reading Section, history) + 1 native storage harness;
  không cộng lẫn 2 baseline trước delta thành test mới khác.
- Test browser history ban đầu lỗi selector khi chưa chờ URL điều hướng; sửa
  chờ URL/scoped score summary rồi đạt. Không thay dữ liệu hay guard để chạy xanh.
- HTTP G1 thật `127.0.0.1:18080` dùng DB test/account-role-reference fixtures:
  Teacher tạo group/câu/order → gửi duyệt → Admin approve/publish batch → Learner
  Practice P7 save/read/submit/replay; sai role 403/owner 404/stale 409. Publish group
  version mới giữ pin/result cũ. Sau restart tiến trình backend thật, result/replay
  vẫn khớp. Không SQL publish, contributor hoặc media grant bypass.
- Demo backend đã restart vật lý, login 200; GET own result/submit replay khớp
  checkpoint. `/api/health`, `/api/health/database`, FE và FE proxy DB health 200.
- Worker concurrency/retry/restart-instance có test mới trong 416. Chưa nhận là
  đã kiểm restart tiến trình với một Full Mock hết hạn trong lượt này; FULL-LR và
  media/contributor dependencies chưa cho phép chạy trọn pipeline vận hành.
- Đã dừng đúng test backend 18080, xóa một DB test sau giữ history/count/proof;
  xóa 2 object upload test bằng S3 signed DELETE, xác minh 404. Cluster còn
  `postgres` và `toeic_learning_demo`. Giữ demo FE/BE/PG/S3 đang chạy.
- Đối chiếu 8 bảng bank/media demo với fingerprint backup gốc ở cùng UTC:
  count + hash toàn bộ hàng khớp; 40 history gốc giữ nguyên, tổng 44 rows tới V43.
  So sánh đầu dùng session timezone khác khiến hash timestamp khác; kiểm lại đúng
  UTC gốc đạt, không sửa dữ liệu để làm khớp.

Chi tiết machine-readable: `backend/target/tv2-sprint3-20261124/final-audit.json`,
`package-proof.json`, `http-g1-proof.json`, `storage-browser-proof.json`,
`demo-restart-proof.json`, `cleanup-proof.json`, logs `.gz` và `ops/`.
Ops start app cố định JAR + datasource demo + validate-only và đọc secret từ file
ignored hiện có. Không dùng script demo cũ có bootstrap/migrate hoặc chạy Maven
clean trên `backend/target` đang chứa PGDATA/credential/bằng chứng.

Không gọi full 1.235 working tree/1.266 working tree/CI cũ là kết quả source mới;
không gọi browser HTTP fixtures là E2E backend thật. Chưa có benchmark NFR-02,
clone DB thành viên lâu dài, audio TOEIC chính thức hoặc full FULL-LR publish flow.

## Review commit TV2 — nguồn staged độc lập 2026-11-24

Xuất index TV2 18 path từ parent `24839e0`, chưa đưa G1 vào source/JAR.
Chỉ dùng dependency cache Maven/npm, không dùng application/test source chưa stage.
Một cluster test tạm `127.0.0.1:55469/toeic_commit_review_20261124` được dựng mới
bằng migration gốc và fixture; không là DB demo hoặc clone DB thành viên.
Endpoint demo 55459 đang dừng khi bắt đầu lượt review; giữ nguyên trạng thái đó,
không khởi động/migrate DB demo và không kết nối PostgreSQL cá nhân 5432.

- Maven Wrapper `test` với AttemptHistoryIntegrationTest,
  PracticeSubmissionIntegrationTest, FullMockIntegrationTest,
  SectionAttemptIntegrationTest, AttemptExpiryIntegrationTest và
  AuthFlowIntegrationTest: **121 tests, 0 failure/error/skipped**.
- Maven Wrapper `-DskipTests package` sau khi test đạt: thành công. JAR SHA256
  `65ad06b7b96ec0e14e1119f0c5696fe775aabbc24b5e23a93146d1aea3ef7939`;
  60 DB resources khớp index, không resource/entry cũ ngoài source, không có lớp G1.
- FE `npm test`: **100 đạt**; `npm run lint` và `npm run build` thành công,
  còn cảnh báo có sẵn. Không chạy lại full backend hoặc browser/storage harness.
- Cached diff/whitespace, conflict marker và secret/artifact scan đạt trong scope;
  file nền/Problems/AGENTS/settings/credentials không stage.
- Bổ sung tài liệu sau test chỉ đổi Markdown; mọi source/config được giữ đúng
  blob index đã compile/test. Proof tree/hash/log riêng nằm trong thư mục review
  `toeic-exam05-g1-commit-20261124-ehr4q4rk` ở Temp.

Đây là bằng chứng staged của commit TV2, tách khỏi 416 kiểm tra working tree lịch sử.
Source/DB label của storage harness nhận từ môi trường kiểm chứng, không còn gắn
cứng HEAD/DB cũ; chưa rerun harness nên không nhận playback của nguồn staged đã đạt.
