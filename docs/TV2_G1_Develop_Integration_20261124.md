# TV2/G1 — tích hợp develop, working tree 2026-11-24

HEAD trước merge `04876d32408bdb325fbe695404948ea0edc3c1af`;
chain `24839e0 → 79898e6` (TV2, pthanh137) `→ 04876d3` (G1, chienvoxn).
Fetch đầu lượt: `origin/develop=6ff5f74083b56e42f66e3a97622f2f934b99040c`;
merge base `314f1380411a7611b6252f4bc03381d56e770ed9`.
Không commit/push/PR trong lượt này; không stage tay thêm vào index merge.

## Đối chiếu trước merge

| Phần | Feature hiện tại | Develop 6ff5f74 | Cách kết hợp / quyết định |
|---|---|---|---|
| Teacher API | `/teacher/tests`, own/any permission, DRAFT document/revision, publication/fork/retire/blueprint | Cùng route GET/POST, role Teacher/Admin, title-only create, SQL trực tiếp, chưa evidence/revision | Một controller/service hiện hành, update client mới sang contract DRAFT; không mất quyền hoặc workflow đã kiểm |
| Assembly | Chọn explicit published question/group versions, nguyên nhóm/business order, revision và audit | `/teacher/test-versions/{id}/assemble-demo`, tự lấy đúng 200 DRAFT của actor theo UUID/order suy ra | Không để endpoint ghi SQL bypass; client dùng chọn unit/save của cùng DRAFT engine. Không tự chốt 200/DRAFT assembly là contract |
| Full Test start/catalog | `/tests`, `POST /attempts` TEST_VERSION/FULL_MOCK, PUBLISHED/current/offered, blueprint snapshot, media/contributor evidence | `/full-tests`, TEST_VERSION/FULL_TEST fixed 200/7200, không snapshot blueprint | Một engine version/blueprint đã có; compatibility URL chỉ delegate, không creator song song. Số liệu FULL-LR/timing mới vẫn OPEN |
| Attempt cũ | Practice/Section và FullMock đã pin/saved grading; worker chỉ FULL_MOCK có snapshot | FULL_TEST pin từ factory 200/120, không blueprint; timeout theo policy cũ | Nhận diện dữ liệu legacy đã tồn tại, giữ pin/deadline/result, không tự repin/backfill/đổi timeout; fixture không là DB thành viên |
| Teacher UI | Editor DRAFT + publication/fork/version history; management/moderation hiện hữu | TeacherLayout/dashboard/content/questions/editor/exams cards, form title-only và publish disabled | Giữ shell/style/pages mới; nối canonical API và editor/actions đã có, route cũ không mất |
| Learner UI | Practice/Section/FullMock, history/result, autosave queue, expiry polling | Full Test catalog cards, PracticeGoalTimer, navigator grid/grouping, UI vocabulary cải tiến | Hợp nhất mapper/repository/router; metadata từ server thay hard-code; không đổi timeout Practice/Section hoặc rơi pin/history |
| AI exam guard | Chat actor/quota/idempotency đã có | PR70: chặn chat/history khi SECTION/FULL_TEST đang IN_PROGRESS trước hạn, lock actor khi start exam | Giữ guard và race protection; bổ sung FULL_MOCK đã có vào nhận diện, lock create cùng actor; không dùng token/browser để xác định thi |
| Migration | Compatibility V34–V43, historical locations đã bàn giao; 50 SQL resources | 39 SQL resources; không thêm/sửa resource sau merge base | Giữ nguyên SQL/checksum/history/coordinator; không repair/clean/baseline hay chọn location để né lỗi |
| Permission/publication/G1 | ACTIVE ADMIN + permission, contributor cycle, revision/pointer, M1 owner/grant, batch audit atomic | Không thêm migration/permission; controller Teacher cũ role-only | Không thay permission bằng role; giữ G1 và mọi gate publication/media, không SQL grant |
| Worker/result/history | Bounded worker/retry/row lock/idempotent, stored key/Part stats, paginated own history | Không có delta worker/history sau merge base; shared attempt model/repo thay đổi | Giữ phần hiện hữu, adapter legacy không tạo grading/submit/result route thứ hai |

Merge preview phát hiện 8 path conflict: TeacherTestController, PracticeAccess,
PracticeAttempt, PracticeAttemptRepository, routes.jsx, server-attempt.mapper.js,
PracticeSetupPage.jsx, TestCatalogPage.jsx. 42 incoming paths không đụng 41 path
nền; settings/credentials có hash riêng để bảo toàn. Không chọn nguyên ours/theirs
để bỏ một phía; index auto-stage của Git merge được ghi riêng với hunk tự kết hợp.

## Gói Full Test đang chờ xác nhận

Đã gửi một gói chọn cho Full Test mới (không nhận code develop là phê duyệt):

- Tổng 200, Listening 100/Reading 100; Part 1–7 đề xuất 6/25/39/30/30/16/54.
  Nhóm P3 13×3, P4 10×3, P6 4×4, P7 15 nhóm: 1×2 + 9×3 + 5×5.
- A đề xuất: Listening 45 phút → Reading 75 phút, server chuyển đúng mốc,
  không chuyển sớm/quay lại, khóa Listening sau mốc.
- B: tổng 120 phút, chuyển tự do giữa Part/Section, không khóa từng Section.
- Hoặc giữ OPEN, chỉ tích hợp phần độc lập. Không seed FULL-LR/v1 hoặc thi hành
  timing/khóa Section mới khi chưa có câu trả lời xác nhận.

Cả hai giữ source question/group version published đủ điều kiện và pin blueprint,
thứ tự/deadline tại server. Attempt cũ giữ snapshot/result/deadline và timeout cũ,
không áp khóa Section hồi tố, không tính lại theo bank. Adapter dữ liệu cũ cần
phân biệt FULL_TEST develop với TEST_VERSION lạ, không tự cấp blueprint cho history.

## Phạm vi và nhãn bằng chứng

Identity repository giữ `pthanh137 <sleapf137@gmail.com>`. 121/197 test staged của
hai commit trước và 416 kiểm tra working tree hai lượt là bằng chứng lịch sử, không
gán cho cây merge mới. Storage harness không phải Learner Listening E2E.
Đến cuối lượt phải ghi source fingerprint/MERGE_HEAD/index, test BE/FE và HTTP/browser
thật riêng; không nhận full/mock/fixture là clone DB thành viên dài hạn.

## Kết quả tích hợp và điểm tiếp tục

Working tree đã kết hợp hết 8 conflict về nội dung, không còn marker. Chưa
`git add`: index vẫn giữ 8 entry unmerged và 34 path stage 0 do chính lệnh merge
tự stage; không được xem index này là cây đã kiểm hoặc sẵn sàng commit.
HEAD/MERGE_HEAD vẫn lần lượt `04876d3` / `6ff5f74`. Repo identity giữ
`pthanh137 <sleapf137@gmail.com>`, tác giả/committer của ba commit cũ không đổi.
Danh sách 56 path nhiệm vụ và hunk xem [manifest](TV2_G1_Develop_Integration_20261124_manifest.md).

Các thay đổi độc lập đã thực hiện:

- Giữ TeacherLayout/pages/CSS mới; `/teacher/exams` tạo title-only qua canonical
  DRAFT service/audit, chuyển `/teacher/tests?testId=…` để sửa có revision,
  chọn published unit và dùng publication/fork/retire hiện hữu. Reference Part
  lấy từ `/teacher/question-options`, bỏ ID gắn cứng theo DB cá nhân. Route
  Teacher kiểm cả capability từ server để không mất quyền custom-role.
- Một catalog/runtime: client dùng `GET /tests`, `POST /attempts` TEST_VERSION;
  `/full-tests` và route start cũ chỉ là adapter. Không còn creator fixed
  200/120. Catalog lọc current/published, đủ mapping, publication evidence,
  source version và media khả dụng; start khóa và kiểm lại chính sách đầy đủ.
- `/teacher/test-versions/{id}/assemble-demo` chỉ kiểm quyền/ownership và trả
  `409 EXPLICIT_TEST_SELECTION_REQUIRED`, không SQL tự ghép 200 DRAFT. UI dùng
  editor explicit version/revision. Endpoint này không được coi là hoàn thiện
  assembly product khi contract 200 câu chưa được duyệt.
- Đọc legacy FULL_TEST có đúng hình dạng factory develop: TEST_VERSION, test
  version ID, 200 pin khác nhau/order 1–200, không blueprint, deadline 120 phút.
  Không backfill, repin, sửa history hoặc đổi timeout. Dữ liệu legacy sai hình
  dạng không được phép ghi. History/media nhận diện cùng điều kiện. Worker và
  FE polling tự chốt chỉ dành FULL_MOCK có snapshot; không áp hồi tố lên legacy.
- AI guard giữ thay đổi PR70 và thêm FULL_MOCK; start exam và AI persist dùng
  cùng khóa actor. Giữ transaction, stored grading/Part stats, own pagination,
  G1 atomic revision/pointer/contributor/media và toàn bộ auth/RBAC hiện hữu.
- Giữ giao diện catalog/PracticeGoalTimer/navigator/vocabulary mới, Guest mẫu
  tách rõ; timer mục tiêu Practice không thay deadline 24h. Đã sửa lỗi catalog
  gọi nhầm `getTests()` thành interface `getCatalog()` được browser phát hiện.

## Bằng chứng cây merge hiện tại

Tất cả dưới `backend/target/tv2-develop-integration-20261124/`, ignored, không
đưa JAR/log/dump/private fixture credentials vào manifest commit.

| Kiểm tra | Kết quả thực tế / nguồn |
|---|---|
| Compile/test-compile, package | Đạt với Maven Wrapper, source directories là working tree, chỉ chuyển output sang thư mục riêng bằng temporary POM. Không tạo source copy/worktree, không Maven clean target demo |
| Backend rộng một lượt | `broad-final-result.json` exit 0; 81 class, 1.248 test ghi nhận, 1.247 thực thi, 1 skipped, 0 failure/error. Loại `MigrationCompatibilityIntegrationTest` và hai test data-driven nền. Đã bỏ sót filter `VocabularyLineageCompatibilityIntegrationTest`: 20 test migration vẫn chạy ngoài dự kiến; xem ghi nhận/dọn bên dưới. **Không gọi là full toàn bộ repository** |
| Nguồn backend rộng | Working tree `04876d3 + MERGE_HEAD 6ff5f74`, BE không đổi sau lượt đó. Không gán cho staged/committed source hoặc CI. Không chạy lại khi resume chỉ sửa FE/catalog/selector |
| Test delta | Có test adapter/title-only/revision, catalog permissions/media/source, legacy đọc/lưu/chấm/replay/history, legacy expiry không bị worker claim. Lỗi timestamp adapter ở lượt đầu đã sửa; lỗi assert null-key fixture đã sửa; backend rộng cuối chứa các test này đạt |
| FE cuối | `resumed-fe-unit.log`: 101/101; lint exit 0 (còn warning); build exit 0 (warning chunk >500 kB). Sau đó chỉ sửa selector trong live browser test, không đổi runtime |
| Teacher browser thật | `browser-teacher-recheck.log`, 1/1: shell mới → title-only create → editor chung sửa → reload; API/DB revision=1 và document khớp; stale revision=409; Part 5 question DRAFT tạo qua reference API |
| Learner browser thật | `browser-recheck.log` ca Learner đạt: Practice chọn/thay/bỏ → reload → submit/replay → history/result; Reading Section 8 câu Part 5/6/7 → stats/history/result; không route stub |
| Kết quả cũ | Browser đọc/replay 8 kết quả đã có trong DB review. Bao gồm attempt từ lượt browser trước, không khẳng định cả 8 bắt nguồn từ backup demo. Legacy 200-pin được kiểm bằng fixture factory develop riêng, không là clone DB thành viên |
| G1 HTTP/DB thật | `http-g1-proof.json`: Teacher author/submit, Admin approve/batch publish; sai quyền 403, stale replay 409; publish group version mới giữ pin/result cũ. Không publish/grant bằng SQL |
| G1 browser | Ca moderation trong `browser.log` đạt: UI đọc evidence thật. UI chưa có nút batch; không nhận API batch là thao tác UI end-to-end |
| Restart / DB | `final-live-proof.json`: G1 pin/result/replay giữ nguyên qua restart BE/PostgreSQL; đáp án/điểm browser và Teacher revision/document khớp SQL; 44 history row giữ nguyên hoàn toàn |
| Migration/JAR | Fresh output có 50 SQL trong 60 resource db, khớp chính xác source, không stale resource. Toàn bộ main/resources và coordinator giữ nguyên HEAD; historical SQL develop được giữ trong historical locations có sẵn. Boot validate 44 migrations và JPA validate đạt sau restart. Resume không chạy lại test migration; lượt broad trước vẫn đã chạy bộ Vocabulary do sót filter |

Lệnh backend rộng đã chạy:
`backend/mvnw.cmd -f <temporary-review-pom> -B "-DargLine=-Xmx1024m -Dspring.test.context.cache.maxSize=3" test "-Dtest=*,!MigrationCompatibilityIntegrationTest,!AuthDataDrivenTest,!VocabularyDataDrivenTest"`.
Temporary POM chỉ đổi build directory/source/resource/test working directory về
đường dẫn tuyệt đối; không đổi code/dependency/contract. Bản Temp bị mất khi môi
trường dừng, kết quả/log và JAR vẫn còn; không cần dựng lại để chạy lặp BE.

FE: `npm test`, `npm run lint`, `npm run build`.
Live browser: `npx playwright test tests/develop.integration.spec.js` (lượt đầu
có lỗi selector/catalog), sau đó chỉ chạy lại hai ca lỗi bằng `--grep`, cuối
cùng ca Teacher dùng `--grep "New Teacher"` đạt. Không gom thành một lượt full
browser pass hoặc che các lần fail ban đầu.

Sai sót filter được phát hiện khi kiểm DB cuối: `VocabularyLineageCompatibilityIntegrationTest`
dùng helper tạo DB của bộ base đã bị exclude nhưng không tự cleanup khi base
không chạy. Vì vậy broad trước đã chạy 20 test, tạo 22 DB `toeic_migration_it_*`.
Không nhận đã tránh toàn bộ diễn tập migration. Khi resume không chạy lại;
đã đối chiếu đúng 22 tên từ log allocation và identity cluster 55469/owner,
xác nhận không còn client, lưu schema SHA, table count/row hash, history,
sequence/role settings và SHA source test vào `owned-fixture-cleanup.json`,
rồi drop đúng 22 DB fixture tái tạo được. Không xóa source/demo/DB HTTP hoặc
bằng chứng trước; còn `postgres`, DB JUnit và DB HTTP trong cluster này. Không
tạo cluster mới. Đây là sửa sai vận hành, không thay đổi kết quả 1.248 test.

## Môi trường demo kiểm thử đang để chạy

- FE `http://127.0.0.1:5173`, BE `http://127.0.0.1:8080`.
- DB thực tế **`127.0.0.1:55469/toeic_tv2_merge_http_20261124`**, role lab
  `commit_review_test`, profile `local-demo` với datasource override rõ ràng,
  `app.migrations.mode=validate`, JPA validate. Không phải DB demo nguồn 55459.
- `/api/health` và `/api/health/database` UP, health DB trả đúng tên trên.
- Tái sử dụng đúng cluster test đã có, không initdb/restore thêm lúc resume.
  PostgreSQL phục hồi WAL bình thường sau môi trường bị ngắt; không repair/
  reset WAL. Demo nguồn và PostgreSQL cá nhân không được khởi động hay ghi khi
  resume; không truy cập `toeic_db`.
- Backup demo hiện tại đã restore vào DB review ở phần đầu lượt tích hợp:
  `existing-demo-before-integration.backup`, SHA-256
  `84f642aa4e22e708baa0b8641a3f16f7bcefd9bdbb3ac077db700a767900f311`.
  Đã đối chiếu mọi table count/row hash và Flyway history trước khi chạy app;
  nguồn không đổi. Đây là clone demo local, không là DB thành viên lâu dài.
- Dùng credentials demo Learner hiện có trên bản sao; actor Teacher/Admin
  test riêng lưu file ignored `http-g1-private.json`, không ghi password/token
  trong docs hoặc chat. Process/checkpoint trong `resumed-processes.json`,
  `resume-checkpoint.json`, `final-live-proof.json`.

## Giới hạn và bước chuẩn bị demo toàn nhóm

Chưa seed hoặc phê duyệt FULL-LR/v1. Demo hiện chưa có Full Test published đủ
điều kiện/media để chạy Full Mock end-to-end thật; runtime/publication/worker
đạt integration test MINI-LAB, không thay thế bằng chứng media/playback thật.
G1 batch API đã hoạt động, nhưng UI batch và workflow upload/grant media vẫn là
dependency cho demo soạn nguyên đề Listening; không SQL bypass. Chưa đo p95/
NFR-02, không quy đổi điểm TOEIC.

Bước tiếp theo: người dùng chọn gói contract Full Test ở trên; sau đó cấu hình
blueprint được duyệt và chuẩn bị question/group/media hợp lệ qua workflow,
kiểm Full Mock UI–API–DB theo Section timing đã chọn. Riêng merge cần review
manifest và **cho phép stage** các lời giải cùng hunk tích hợp, rồi kiểm nguồn
staged độc lập trước commit. Hiện chưa commit/push/PR, index chưa đại diện
nguồn đã kiểm. 43 hash file nền/settings/credentials khớp baseline; xem
`final-audit.json`, danh sách 6 tracked/35 untracked nền tách khỏi nhiệm vụ.

## Develop tiến thêm khi kiểm tra cuối — chưa được tích hợp

Fetch thành công sau resume thấy `origin/develop=20c63e776b656efd5ba0daaaae3447284e863cac`,
PR #72, chain `e87ceb9 → 1d28711 → 20c63e7`. MERGE_HEAD vẫn **6ff5f74**.
Không chạy merge chồng, không đổi MERGE_HEAD, không apply riêng code PR72 vào
nguồn đang gắn bằng chứng 6ff5f74; chưa được stage để kết thúc merge hiện tại.
**Chưa đồng bộ xong develop mới nhất 20c63e7.**

Đã đọc đủ diff mới: 4 FE path, 30 dòng thêm/7 dòng bỏ; không BE/API/migration,
permission SQL hoặc coordinator mới. Không tự merge nhánh thành viên; đây là
code đã vào develop qua PR72.

| Path mới | Delta và cách kết hợp an toàn cần làm sau |
|---|---|
| `features/ai/ChatBubble.jsx` | Đọc marker localStorage bằng storage event/poll, khóa gửi khi marker có mặt; nhận AI_EXAM_RESTRICTED từ server. Giữ server làm nguồn quyết định, không coi marker là quyền hoặc trạng thái thi tin cậy |
| `pages/assessment/AttemptPage.jsx` | Thêm marker cho **mọi remote attempt** và xóa khi unmount. Overlap hunk với cây tích hợp; nếu lấy nguyên sẽ khóa AI cả Practice trái chính sách BE. Cần kết hợp theo source SECTION/FULL_TEST/FULL_MOCK, IN_PROGRESS và server expiresAt, giữ queue/worker/history/pin và giải phóng marker đúng lifecycle |
| `pages/ConnectedDashboard.jsx` | Link nhanh tới `/vocabulary?review=due` khi có từ đến hạn; độc lập blueprint |
| `pages/vocabulary/VocabDeckListPage.jsx` | Mở modal due từ query, chờ loading và thêm link về Dashboard. Overlap file hiện được auto-merge; giữ progress/API/state hiện hành |

Rủi ro kỹ thuật cần kiểm khi nhập PR72: Practice không bị chặn AI; Section/
FullMock còn hạn bị chặn bởi server kể cả đóng tab; marker cũ/`server-enforced`
không khóa UI vô thời hạn sau expiry/graded/logout; chuyển route/tab không làm
mất worker/pin hoặc đổi timeout. Đây là đối chiếu với contract AI hiện hành,
không phải yêu cầu người dùng duyệt một chính sách nghiệp vụ mới.

Blocker Git cụ thể: 8 index U còn thuộc merge 6ff5f74; ràng buộc **chưa stage
thêm/chưa commit merge** vẫn giữ. Cần lượt được phép review/stage/kết thúc merge
hiện tại rồi tích hợp PR72 theo lịch sử Git, hoặc một chỉ dẫn rõ để điều chỉnh
merge đang dang dở. Không reset/stash/abort hay tự chỉnh file MERGE_HEAD để vượt
chặn. Bằng chứng 1.248/101/browser ở trên không gán cho 20c63e7.
