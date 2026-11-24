# ADMIN-03 / WEB-06 — quản trị học liệu, câu hỏi và bộ đề

## Review bàn giao2026-11-16 — phạm vi commit và nguồn kiểm chứng

Giữ nhánh feat/web-auth-integration, danh tính local và HTTPS push đã xác minh là
chienvoxn. Fetch origin: feature vẫn1389e9b (HEAD không ahead/behind), develop mới
6fecb42 có PR62 RAG. V35 chưa trùng trên remote đã fetch. Tuy nhiên develop có
V34__rag_chat_runtime.sql trùng số với V34__managed_system_defaults.sql đã nằm
trong commit1389e9b và đã áp dụng ở DB demo/test trước đây. Không merge develop,
không đổi số/sửa migration đã áp dụng; đây là blocker cần thống nhất riêng trước
merge PR vào develop, không che bằng repair/clean DB.

Chỉ stage52file/hunk manifest bên dưới. AuthFlow chỉ expectation bốn permission
mới; hai hunk Problems xóa import giữ ngoài commit. AGENTS, các sửa Problems,
Postman R2, tatus, settings IDE và các file data-driven/report mới có sẵn được
ghi hash trước thao tác và giữ nguyên. Không commit credential, dump/log/artifact.

Mốc working tree giữ nguyên: full1091/1092 (một expectation quyền cũ), sau sửa
AuthFlow31/31; workflow/RBAC50/50; frontend74/74, lint/build và browser22/22.
Không gọi lượt full đó là xanh. Các log target mốc cũ hiện không còn trên máy;
bằng chứng lịch sử là runbook đã ghi, không dựng lại hoặc chạy full để thay thế.

Kiểm chứng mới từ index: xuất đúng blob staged của backend/src, bao gồm mọi test
đã được Git theo dõi, sang vùng build tạm dưới backend/target; POM tạm lấy từ
index, chỉ đổi source/resource/output/working directory để cách ly working tree.
Không thay dependency, skip test hoặc chép code Problems chưa stage vào snapshot.
Dùng DB kiểm thử riêng, không toeic_db. Kết quả mới từ nguồn staged:

| Kiểm chứng2026-11-16 | Kết quả |
|---|---|
| Snapshot backend | 429file từ blob index,309lớp main và64lớp test; không lấy hai test data-driven hoặc CSV ngoài commit |
| Compile main/test | Maven Wrapper offline, Java21, thành công; source/resource/output cách ly dưới backend/target |
| AuthFlowIntegrationTest | 31/31 từ index, có đúng bốn permission mới và giữ các import gốc chưa dọn |
| ModerationIntegrationTest | 21/21 |
| ContentAdministrationIntegrationTest | 26/26 |
| RbacManagementIntegrationTest | 29/29 |
| Tổng nhóm liên quan | 107/107,0failure/error/skip,BUILD SUCCESS trong2phút08, kết thúc2026-11-16T19:36:21+07:00 |
| DB mới | toeic_admin03_staged_20261116, user admin03_handoff_test,127.0.0.1:55461; cluster backend/target/admin03-handoff-pg, schema ban đầu0bảng |
| Flyway | 36migration từ đầu tới V35, thành công; không reset/repair hay kết nối toeic_db |

Log mới ignored backend/target/admin03-staged-tests.log; bảng hash blob/snapshot
ở admin03-staged-source-evidence.json. POM kiểm chứng tạm chỉ thay vị trí build,
giữ dependency/plugin version/test assertion; không skip test. Sau kiểm chứng chỉ
cập nhật tài liệu này, các blob code/test/resource trong index không đổi. Không
chạy lại full backend chỉ để commit. Frontend74/browser22 và các con số full cũ
vẫn là bằng chứng working tree lịch sử, không gán thành kết quả staged107.

## Quyết định sản phẩm được xác nhận2026-11-15 — thay thế gói OPEN bên dưới

Người dùng đã xác nhận workflow DRAFT→IN_REVIEW→APPROVED→PUBLISHED cho version;
trả sửa/thu hồi approval về DRAFT bắt buộc lý do, khóa sửa khi đang review/approved.
Approval gắn đúng version/revision; sửa phải gửi và duyệt lại. Published version
bất biến, giữ archive hiện hữu, chưa unpublish/restore.

Submit theo actor/ownership và authoring self/any. Review/publish yêu cầu ACTIVE
+ ADMIN + permission riêng content:review/content:publish hoặc
question:review/question:publish; manage:any không thay thế. Reviewer có thể publish
nếu đủ quyền và không thuộc tập cấm; không bypass khi chỉ có một Admin.

Contributor trực tiếp gồm creator/editor/submitter của version; clone tạo draft
cũng ghi creator. Vòng publication được xác định bởi parent và current published
source (parent ID khi chưa từng published). Tập cấm hợp các contributor trong vòng
chưa published, không reset khi trả sửa/tái tạo draft. Publication thành công mở
ranh giới vòng mới; chỉ giữ sourceVersionId để truy nguyên, không kế thừa tập cấm
lịch sử. Legacy thiếu bằng chứng vòng hiện tại trả409, không backfill giả. Chỉ
clone từ đúng current published pointer. Không cam kết phát hiện sao chép thủ công
sang parent mới.

M1 giữ owner/grant chưa revoked, quyền đọc/type/visibility; không thêm expiresAt.
Publisher chọn PUBLIC/AUTHENTICATED tường minh cho học liệu, kiểm media trong
transaction với quyền publish độc lập manage. Không upload/grant API hoặc SQL cấp
quyền; demo bằng nội dung không media. Thiếu quyền media phải từ chối toàn bộ,
dependency cấp quyền dùng chung vẫn chưa hoàn thiện.

G1 là bước riêng, chưa triển khai. Chặn publication nhóm/câu thuộc nhóm, giữ
authoring/read/archive và publication/pin hiện tại. Batch tương lai gồm một group
version APPROVED và tập question versions APPROVED pin đúng group version,
ID/order/expected revision/pointers tường minh, đủ câu current cần giữ và câu mới;
validate actor/contributor/media rồi đổi mọi pointer cùng audit nguyên tử. Không
tự bỏ/clone/repin/archive thành viên. Bộ đề vẫn bị chặn EXAM04/PR61, không tích hợp.

Bằng chứng1071backend/71frontend/20browser bên dưới là mốc working tree TRƯỚC
workflow, không chứng minh code workflow mới và không phải nguồn commit độc lập.
Trạng thái triển khai/kiểm chứng mới sẽ được ghi ở cuối tài liệu.

Ngày2026-11-14, nhánh feat/web-auth-integration, dựa trên ADMIN06 `1389e9b`.
Git local chienvoxn <109399678+chienvoxn@users.noreply.github.com>.
**Đã triển khai phần có contract rõ: đọc/archive và mở rộng tạo/sửa DRAFT cho học
liệu, câu hỏi và nhóm. Bằng chứng hai giai đoạn được tách riêng bên dưới.
Workflow duyệt/publish đã được xác nhận và bổ sung theo mục quyết định2026-11-15;
bằng chứng triển khai mới ở cuối tài liệu. Bộ đề bị chặn dependency EXAM04.
Không đánh dấu toàn bộ ADMIN03/WEB06/Sprint3 Done.** Chưa stage/commit/push/merge PR.

## Nguồn, đồng bộ và OPEN lịch sử — mốc đọc/archive2026-11-14

Đọc AGENTS, SRS UC12/UC13/BR02/AC02/BR24/AC24, HLD Phần B §20,
LLD §2.2/2.3/4.1/4.2, ERD và migration, API §3.2/3.3, CONTENT03/Q03 và
EXAM01. Không có backlog/traceability file riêng hoặc GitHub issue ADMIN03 tìm
thấy; AC theo yêu cầu trực tiếp của người dùng và các contract runtime đã chốt.
BR02/AC02 nêu chuỗi DRAFT→IN_REVIEW→APPROVED→PUBLISHED→ARCHIVED dưới tài liệu
draft. SRS phần OPEN còn hỏi ai approve/publish và self-approval; không dùng
chuỗi đó làm contract mới tự chốt. CONTENT03/Q03 chủ động chưa bao gồm publication.

Fetch origin: develop ce552e2, remote feature1389e9b. Không có PR ADMIN06 mở/merged
trong danh sách PR của nhánh, develop chưa chứa1389e9b. Hai nhánh phân nhánh nên
không thể fast-forward; không tự tạo merge commit trong lượt chưa cho commit.
Giữ HEAD1389e9b và dependency ADMIN06, không nhập nhánh thành viên riêng.
10hash file nền được lưu ignored backend/target/admin03-initial.json.

Đối chiếu ban đầu trước triển khai (kết quả hiện tại ở mục mở rộng cuối tài liệu):

| Tài nguyên | Đã có thực tế | Còn thiếu trong ADMIN03 | Dependency/OPEN |
|---|---|---|---|
| Học liệu CONTENT03 | GET list/detail, POST DRAFT/PRIVATE, PATCH working draft, DELETE archive parent; own/any, media grant/visibility, version giữ nguyên | UI quản trị, audit archive nguyên tử; duyệt/publish | Reviewer/publisher, self-review, visibility khi publish và review transitions chưa chốt |
| Câu hỏi/nhóm Q03 | GET list/detail/history, POST/PATCH DRAFT, DELETE archive; pin groupVersion; preserve published versions/choices/assets, parent locks | UI quản trị, audit archive nguyên tử; duyệt/publish | Tách approve/publish, author của version khi Admin sửa, thứ tự/group publication; không cascade tự suy |
| Bộ đề EXAM04 | DB tests/test_versions/blueprints có trong V5/V10; Practice/Section đang chạy độc lập ngân hàng published | Không có TestVersion CRUD/publication API/service trong HEAD/develop; không có backend để nối UI quản trị | Nhánh riêng origin/feat/toeic-bulk-import-exam66e71a2 có ExamManagementController /teacher/tests nhưng chưa tích hợp, V26__exam_workflow trùng V26 hiện có và attempt routes trùng; không import tự động |

HTTP thật đã xác nhận GET /api/v1/teacher/tests bằng Admin trả404; SQL demo có
tests=0,test_versions=0. Có bảng không đồng nghĩa đã có CRUD/nguồn đề hợp lệ.
TestCatalogPage/TestOverviewPage hiện dùng assessmentRepo, là danh mục/bài mẫu
Guest được ghi rõ trong UI; không là bằng chứng EXAM04 backend. Practice/Reading
Section thật vẫn dùng /attempts và ngân hàng published độc lập bộ đề.
V23 có group_question_order nhưng Q03 authoring hiện chưa nhận thứ tự này trong
payload; chưa tự suy thứ tự từ timestamp/UUID hoặc bổ sung publish nhóm thiếu order.

Đã gửi một câu hỏi gộp đề xuất Teacher gửi duyệt, Admin approve/trả DRAFT kèm lý
do, cấm tự duyệt bản mình tạo/sửa, publish bước/quyền riêng với visibility tường
minh, không cascade question khi publish group. **Chưa có câu trả lời; đề xuất
không phải quyết định đã chốt.** Trong lúc chờ chỉ mở rộng authoring/đọc/archive
theo contract hiện hữu; không triển khai workflow mới.

## Phạm vi kỹ thuật độc lập

### Gói đề xuất lịch sử2026-11-14 — được thay thế bởi quyết định xác nhận ở đầu tài liệu

Yêu cầu đã xác nhận trong mục này là yêu cầu trực tiếp của người dùng hoặc contract
CONTENT03/Q03/PRAC đã chốt; các mã S1/R1/M1/G1 dưới đây **chỉ là phương án đề xuất**.
Không triển khai transition, permission, migration hay nút publish dựa trên bảng
này trước khi có xác nhận. Bằng chứng1058test bên dưới thuộc phần đọc/archive trước gói này.

| Mục | Yêu cầu đã xác nhận | Điểm OPEN | Phương án đề xuất | Lý do/tác động |
|---|---|---|---|---|
| Trạng thái (S1) | Runtime hiện chỉ authoring DRAFT, đọc current PUBLISHED, archive parent; SRS/LLD state machine là DESIGN | Trạng thái review có áp dụng cho cả content/question/group và có khóa sửa không | Version DRAFT→IN_REVIEW→APPROVED→PUBLISHED; IN_REVIEW→DRAFT khi trả sửa; APPROVED→DRAFT khi Admin thu hồi duyệt kèm lý do. Một parent chỉ một working version thuộc DRAFT/IN_REVIEW/APPROVED. Parent vẫn DRAFT/PUBLISHED/ARCHIVED như hiện tại | Tách approve/publish, không thêm REJECTED; chặn PATCH khi working version đang chờ/đã duyệt thay vì âm thầm clone draft song song |
| Teacher gửi duyệt (S1) | Teacher quản lý own; any scope không bypass media; actor lấy từ session | Ai gửi version nào, payload, validate | Chủ thể ACTIVE có manage:self với ownership hoặc manage:any được gửi đúng versionId DRAFT bằng command submit-review với expectedRevision; không nhận owner/status/answers tùy ý. Kiểm completeness theo content/question rules, group pin/order và media trước nhận | Chỉ tác động working version đã chọn, optimistic revision chống submit nhầm bản; endpoint tên cụ thể còn là thiết kế kỹ thuật, chưa có runtime |
| Người duyệt (R1) | Không tự coi role ADMIN là bypass; quyền bị thu hồi có hiệu lực request sau | Có Reviewer riêng/Teacher được duyệt? | ACTIVE + ADMIN + content:review hoặc question:review; không tạo SUPER_ADMIN/Reviewer role đặc biệt. Submit vẫn dùng quyền authoring hiện có. Reviewer có thể là publisher nếu có đủ quyền và không là contributor | Không cần thêm hai Admin mới vận hành được; không đồng nhất manage:any với quyền duyệt |
| Tự duyệt (R1) | SRS ghi self-approval OPEN; content version có created_by nhưng không đủ lịch sử editor; question/group version chưa có creator/contributor đầy đủ | Cấm creator, last editor hay mọi người từng sửa; dữ liệu cũ xử lý thế nào | Cấm mọi actor đã tạo/sửa working version duyệt hoặc publish bản đó. Ghi contributor từ mutation thật cho dữ liệu mới; legacy thiếu bằng chứng bị chặn transition với409, không đoán/backfill tác giả giả | Cần persistence mới sau khi chốt và kế hoạch xử lý legacy; chỉ check parent owner/last editor không ngăn actor từng sửa rồi nhờ người khác sửa tiếp |
| Quyền publish (R1) | Chưa có enforcement publish trong code hiện tại; permission catalog chỉ được code/migration định nghĩa | Tách quyền, có bắt publisher khác reviewer không | ACTIVE + ADMIN + content:publish/question:publish riêng; bản phải APPROVED. Cho cùng reviewer publish nếu không là contributor. Mỗi permission được enforcement/filter/service/RBAC catalog/capability thực thi, grant mặc định ADMIN sau migration mới | Quản lý/sửa không tự cho phép approve/publish; có thể thu hồi publish mà giữ manage/review. Nếu cần hai Admin khác nhau thì là lựa chọn R2, thêm dependency vận hành |
| Visibility học liệu (V1) | Content DRAFT mới PRIVATE; learner read chỉ PUBLIC/AUTHENTICATED theo content:read. Question/group không có visibility column tương đương | Publish PRIVATE có được không; PUBLIC chứa media AUTHENTICATED? | Content publish phải chọn PUBLIC hoặc AUTHENTICATED tường minh. PUBLIC chỉ nhận media PUBLIC; AUTHENTICATED nhận PUBLIC hoặc AUTHENTICATED theo quyền. PRIVATE giữ ở draft/approved. Question/group giữ đúng permission/publication hiện có, không tự thêm visibility | Không vô tình mở dữ liệu PRIVATE hoặc nâng quyền media; không hồi tố visibility/links của bản đã publish cũ |
| Quyền media (M1) | Attach/clone hiện đòi actor owner/active grant + PUBLIC hoặc AUTHENTICATED/content:read; PRIVATE luôn bị từ chối, manage:any không bypass | Publisher Admin cần grant reuse hay chỉ kiểm quyền của người soạn | M1 khuyến nghị: publisher cũng phải có owner/grant hợp lệ cho mọi link cuối và quyền đọc/type/visibility hợp lệ; recheck khi publish, không tự cấp grant hoặc bỏ link. Reviewer được xem metadata theo read policy, không được attach tùy ý | Ít thay đổi subject kiểm quyền, nhưng hiện chưa có API grant nên Admin có thể bị409 khi media thuộc Teacher. M2 chỉ kiểm creator/contributor reuse và publisher read sẽ đổi subject so với attach policy, cần chốt riêng; không mặc định áp dụng |
| Trả sửa/sửa lại (S1) | Published text/choice/assets bất biến; PATCH published clone đúng pointer như CONTENT03/Q03 | Reject cuối hay trả draft, ai được mở bản đã approve | Không trạng thái reject vĩnh viễn. Admin review trả DRAFT kèm lý do bắt buộc; sau sửa phải gửi/duyệt lại. APPROVED muốn sửa phải được Admin thu hồi approval kèm lý do; reset approval, giữ audit. Không sửa payload PUBLISHED | Không tái dùng approval cũ sau sửa; lý do không chứa secret, chỉ cho actor đúng scope đọc, không expose raw reason qua audit chung |
| Publication pointer | Mọi read hiện bám đúng current pointer; history/attempt pin version/order | Bản published cũ có tự archive không | Publish transaction đổi version đích thành PUBLISHED và current pointer; giữ nguyên bytes/status/timestamps/pointer references lịch sử của version cũ, không tự archive cũ. Chỉ pointer xác định current; attempt/result cũ không đọc current để chấm | Không mất snapshot; không dùng latest fallback; bookmark/history vẫn theo policy current đã chốt |
| Nhóm và thứ tự (G1) | Q02/Practice đòi question pin đúng current group version; V23 order phải tường minh, Q03 chưa có editor order | Publish group riêng có làm biến mất câu pin bản cũ; đổi nhóm có cascade không | G1: xuất bản nhóm và tập question versions APPROVED bằng danh sách ID/order tường minh trong một transaction; validate đủ thành viên/Part/thứ tự, không tự clone/duyệt/repin câu ngoài danh sách. Đơn vị độc lập vẫn publish riêng khi không đổi group pointer. G2 an toàn hơn cho v1: chặn thay current group đang có câu published cho tới khi có batch contract | Chỉ publish group mới rồi cập nhật từng câu gây thời gian ẩn/hiện thiếu nhóm. G1 thêm command nguyên nhóm sau khi được chốt; G2 ghi rõ giới hạn, không làm hỏng nhóm đang hoạt động |
| Archive và cạnh tranh | Archive parent204 idempotent, giữ version/links/attempt; group không cascade trạng thái câu con | Archive khi đang review/approve và thứ tự cạnh tranh | Giữ archive parent hiện tại, không thêm restore. Transition phải khóa/recheck parent chưa ARCHIVED, version/revision rồi media/grant; QuestionGroup trước Question như Q03, sort UUID nhất quán. Archive trước→transition409; transition trước→archive ẩn parent nhưng giữ snapshot | Audit/mutation cùng transaction, không publish/archive nửa chừng, không thay contract Practice/Section |

Lựa chọn khuyến nghị để người dùng chốt một lần: **S1 + R1 + V1 + M1 + G1** và
fail-closed legacy thiếu contributor. Có thể thay M1 bằng M2, R1 bằng R2 hoặc G1
bằng G2 khi chỉ rõ. Chưa có lựa chọn nào được xác nhận; đây không phải permission
để áp dụng workflow/migration lên demo hoặc database của thành viên khác.

### EXAM04 remote — rà chỉ đọc2026-11-14

- Nhánh origin/feat/toeic-bulk-import-exam, SHA đầy đủ
  66e71a226f3ce7c6a6f3f05f3b314f641d518027; PR
  [#61](https://github.com/chienvoxn/toeic-learning-platform/pull/61), open,
  merged=false, merged_at=null, mergeable=false/dirty ở thời điểm kiểm tra.
  Base develop ce552e2dac52a3b79831bc358077d2a7d319ef11; branch không là ancestor
  của develop. Không merge/cherry-pick/đổi migration hoặc sửa PR.
- Xung đột migration: remote V26__exam_workflow.sql thêm draft_document/revision/
  review_status vào test_versions, result_document/start_key/listening_ends_at vào
  attempts, client_sequence vào attempt_answers, media_uploads, permissions test:*
  và score_conversion:manage. HEAD đã có V26__practice_grading_snapshots.sql dùng
  cho PRAC02, cùng số26 nên Flyway không nhận cả hai. Chưa biết DB của tác giả/
  thành viên nào đã áp dụng remote V26; không tự đổi số hoặc repair/history.
- Xung đột route chính xác: ExamAttemptController remote đăng ký POST
  /api/v1/attempts/{id}/submit và GET /api/v1/attempts/{id}/result qua alias, trùng
  route PracticeAttemptController hiện có. PUT /attempts/{id}/answers remote là
  batch {questionId,selectedOption,clientSeq}; không trùng path PUT hiện tại
  /attempts/{id}/answers/{questionVersionId} nhưng khác contract/pin/ownership.
  Không chỉ đổi tên controller để tích hợp vì hai luồng đang dùng chung tables.
- Candidate tái sử dụng **sau review và tích hợp**: /teacher/tests GET list/detail,
  POST create/validate/import, PUT save {revision,document}, POST submit-review,
  review {revision,decision,note}, publish {revision}, versions và retire. Có
  optimistic revision, ownership, validate blueprint/question groups và snapshot
  TestVersion cụ thể; đủ nền cho Admin list/detail/version và retirement về sau.
- Chưa là contract chung đã chốt: test:publish remote đang gộp review/publish,
  review_status và self-review rules riêng; ExamQuestionPublisher materialize
  bank từ draft JSON; worker expiry, media_uploads và score conversion thuộc phạm
  vi khác. Không dùng các quyết định đó để tự chốt review/content/question ở đây.
  Cần thống nhất permission, dữ liệu pin, source-specific deadline, audit projection,
  media policy và đường nâng cấp migration trước nối UI ADMIN03 bộ đề.

Giai đoạn đọc/archive ban đầu không tạo API authoring trùng. UI /admin/content gọi lại:

| API dưới /api/v1 | GET list/detail | DELETE /{id} |
|---|---|---|
| /teacher/contents | CONTENT03 DTO/Page và versions | archive parent,204 idempotent |
| /teacher/questions | Q03 DTO/Page và versions | archive question,204 idempotent |
| /teacher/question-groups | Q03 DTO/Page và versions | archive group,204 idempotent |

Không nhận body/query khi DELETE; list UI dùng status/page/size có sẵn. Giữ own/any
tại server/service và DB predicates; Teacher vẫn dùng API này cho nội dung sở hữu
theo contract cũ. Không biến Teacher API thành Admin-only. Menu/page Admin dùng
hai capability server contentManagement/questionManagement = ACTIVE+ADMIN+permission
content:manage:any/question:manage:any, không suy từ role tên ADMIN phía FE.
Giai đoạn đọc/archive ban đầu không thêm permission/migration hoặc bypass media;
workflow xác nhận ở đầu tài liệu nay bổ sung V35 và bốn quyền riêng.

Giai đoạn đọc/archive ban đầu ghi qua ContentArchiveAudit (sau đổi tên thành
ContentManagementAudit khi thêm create/update) → RbacRepository.audit hiện có, transaction
MANDATORY cùng mutation và khóa parent hiện hữu. Chỉ lần chuyển trạng thái thực
mới có audit; lặp204 không ghi thêm. Các action CONTENT_ARCHIVED/QUESTION_ARCHIVED/
QUESTION_GROUP_ARCHIVED, entityType CONTENT/QUESTION/QUESTION_GROUP, ID UUID;
before/after chỉ status, không title/body/choice/key/explanation/transcript/media.
Áp dụng cả Teacher lẫn Admin sử dụng authoring endpoint. Lỗi ghi audit trả503
ARCHIVE_AUDIT_UNAVAILABLE và rollback; không trả raw SQL. ADMIN07 đọc được action/
type mới, count/data cùng predicate, chi tiết chỉ trạng thái allowlist.

Giữ archive parent thay vì hard-delete. Không sửa published text/assets/choices/
pointer, không tự đổi trạng thái version, không cascade archive question khi
archive group. Predicate đọc hiện có làm ẩn dữ liệu, còn attempt/result đã pin
vẫn đọc/submit/grading theo snapshot. QuestionGroup khóa trước Question như Q03;
Content khóa parent trước version như CONTENT03. Không thêm revision cho DELETE
khi contract hiện hữu đã serialize thao tác archive/update bằng khóa parent.

UI có loại/status/page, loading/error/empty, detail phiên bản text escaped và
xác nhận archive. Không nút duyệt/publish giả, không mock fallback, không tự trả
URL/play media hoặc transcript. Bộ đề hiển thị chưa có integration; không tạo
Test CRUD giả trên bảng DB có sẵn.

## Kiểm chứng và manifest

Nhóm test liên quan dùng DB riêng toeic_admin03_test, cổng55459/public.
Cluster workspace bị tắt không sạch từ lần trước, đã đọc log recovery; pg_ctl
timeout30giây trước khi fsync kết thúc, không ép reset. Khi log báo ready mới
bootstrap dịch vụ demo tiếp. Không dùng PostgreSQL cá nhân/toeic_db.
Không lấy1044CI của ADMIN06 để nhận source ADMIN03 đã đạt.

| AC trong phạm vi hiện tại | Kết quả/bằng chứng |
|---|---|
| Giữ nhánh/ADMIN06 và10file nền | HEAD1389e9b, hash10/10 giữ nguyên; chưa stage/commit/push |
| UI đọc/paging/filter/detail/archive qua API cũ | Browser ba kịch bản đã đạt qua các lượt nêu dưới; không API CRUD trùng |
| UI authoring tạo/sửa của Admin | Chưa bổ sung trong phần độc lập này; API CONTENT03/Q03 đã có, không nhận cả UI CRUD đã hoàn tất |
| Quyền/ownership server | Guest401, Learner403, Teacher own200/foreign404, Admin any; revoke role có hiệu lực token/browser ở request sau |
| Audit nguyên tử và idempotent |13test mới có3case audit CHECK fail→503 rollback parent/pointer/timestamp/version, repeat204 chỉ1audit |
| Cạnh tranh |3case archive/edit và3case hai archive thật cùng chờ PostgreSQL row lock, giữ published version và1audit |
| Snapshot | Archive question vẫn submit/grading pin; archive group không cascade câu con và giữ passage/order attempt |
| Media/đáp án an toàn | CONTENT03/Q03 test hồi quy; UI không play/URL/transcript/raw HTML; audit chỉ status |
| Duyệt/publish | **Chưa đạt/chờ quyết định**; không tự dùng DESIGN hoặc self-review policy |
| Bộ đề | **Bị chặn EXAM04 chưa tích hợp**; không fake API/UI |
| Hồi quy full |1058/1058,0failure/error/skip,BUILD SUCCESS; một lượt9phút49giây |

Audit mới chỉ bao phủ archive. Create/update authoring hiện có chưa được mở rộng
audit trong lượt này; không gọi bảng audit là lịch sử đầy đủ quản trị nội dung.
Điều này cùng phần UI authoring/review/publish còn thiếu được tách khỏi AC đã đạt,
không suy toàn ADMIN03 Done từ việc đã có Teacher CRUD API.

Kết quả chạy mới:

- Backend targeted235/235,0fail/error/skip: TeacherContentIntegrationTest,
  TeacherQuestionIntegrationTest, QuestionAuthoringConcurrencyTest,
  ContentAdministrationIntegrationTest và AdminCapabilities/AdminMonitoring.
  Sau bổ sung group snapshot và double-archive concurrency chỉ chạy lại riêng
  ContentAdministrationIntegrationTest:13/13,0fail/error/skip.
- Frontend66/66; npm run lint exit0 (warning có sẵn, không warning ở AdminContent),
  npm run build exit0. Không đổi source ứng dụng sau lượt này; chỉ bổ sung browser
  spec opt-in, không chạy lại test/build vì viết docs.
- Browser ban đầu2pass/1fail: test so timestamp POST JPA nanosecond với PostgreSQL
  microsecond. Sửa **test** lấy GET persisted trước archive rồi so nguyên versions
  với GET sau archive; không giảm assertion, không sửa source runtime. Chạy lại
  đúng kịch bản lỗi1/1 đạt,21HTTP tường minh. Hai kịch bản còn lại đã đạt trong
  lượt đầu. Bổ sung kiểm Teacher foreign404/own200/revoke403 và EXAM04 route404,
  chạy riêng kịch bản quyền1/1 đạt,24HTTP tường minh. Lượt đầu18HTTP, tổng63request
  tường minh qua ba lượt (request phát sinh từ UI thêm bên ngoài).
- Hồi quy browser Admin RBAC/Audit/Settings/Users và Auth13/13 đạt trong47giây,
  trước full backend để tránh cạnh tranh tài nguyên PostgreSQL.
- Full backend cuối:1058/1058,0failure/error/skip,BUILD SUCCESS trong9phút49giây.
  Chạy duy nhất một lượt trên DB mới
  toeic_admin03_regression, không lặp vì tài liệu. Source working tree gồm các
  sửa Problems có sẵn; chưa có bằng chứng cây commit/staged độc lập cho ADMIN03.

Log ignored backend/target: admin03-targeted.log, admin03-archive-final.log,
admin03-frontend-{test,lint,build}.log, admin03-browser.log,
admin03-browser-recheck.log, admin03-browser-ownership.log,
admin03-browser-regression.log, admin03-full.log.
SQL demo sau smoke: CONTENT_ARCHIVED=5,QUESTION_ARCHIVED=1,
QUESTION_GROUP_ARCHIVED=1; V34 checksum1299604777 success=true. Các hàng là thao
tác thật, không seed điểm/tiến độ/audit giả. Fixture browser tạo DRAFT QA thực,
các mục archive vẫn giữ lịch sử; không reset/xóa để làm sạch báo cáo.

## Lệnh chạy, API mẫu và demo5phút

Demo đang dùng profile local-demo, backend8080/web5173/PostgreSQL55459/Mailpit8025,
database toeic_learning_demo/public. Đăng nhập bằng credential demo local trong
ignored backend/target/local-demo-credentials.json, không copy password/token
vào Git/log/chat. Trang http://127.0.0.1:5173/admin/content.

```http
GET /api/v1/teacher/contents?status=DRAFT&page=0&size=20
GET /api/v1/teacher/contents/{contentId}
DELETE /api/v1/teacher/contents/{contentId}
GET /api/v1/admin/audit-logs?action=CONTENT_ARCHIVED&entityType=CONTENT&entityId={contentId}
```

Bearer lấy bằng login; DELETE không body. Hai loại question/group tương ứng
/teacher/questions và /teacher/question-groups. Trước/sau archive detail versions
được giữ, status parent ARCHIVED, không tạo version mới. Lỗi quyền401/403, scope404,
query/payload400, sửa archived409, audit chưa ghi được503; không dùng mock để chữa lỗi.

```powershell
# Kiểm tra cổng/process trước khi chạy; tái sử dụng dịch vụ đang có.
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-local-demo.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/stop-local-demo.ps1
cd frontend/web
npm test
npm run lint
npm run build
npm exec -- playwright test tests/admin.authoring.spec.js tests/admin.content.spec.js
```

Backend dùng Maven Wrapper; trỏ SPRING_DATASOURCE_URL tới
jdbc:postgresql://127.0.0.1:55459/toeic_admin03_test, username q03_main_test và
credential local thích hợp; không dùng toeic_db. Lệnh trong backend:

```powershell
.\mvnw.cmd test '-Dtest=ContentAdministrationIntegrationTest,TeacherContentIntegrationTest,TeacherQuestionIntegrationTest,QuestionAuthoringConcurrencyTest,AdminCapabilitiesTest,AdminMonitoringIntegrationTest'
```

Lượt thực tế dùng ignored admin03-run-tests.ps1 gọi mvnw.cmd -o test, JAVA21,
Mockito agent cache, pool6/min1 và JVM2CPU; -FullSuite với DB regression riêng.

Demo:

1. (0–1phút) Admin mở Học liệu và câu hỏi → Tạo bản nháp học liệu → lưu → sửa
   tiêu đề/tóm tắt → reload xác nhận. API CONTENT03/Q03 của Teacher vẫn giữ own/any.
   Chọn nhóm/câu hỏi để xem editor Part/topic, explicit group version và lựa chọn.
2. (1–2phút) Xác nhận archive, reload/status ARCHIVED; detail vẫn có phiên bản cũ.
   Với group giải thích predicate đọc ẩn các câu liên quan, không cascade trạng thái.
3. (2–3phút) Audit chọn action/type/ID, xem CREATED/UPDATED/ARCHIVED, UUID version
   và tên field, không có giá trị nội dung. DELETE lặp vẫn204 nhưng chỉ một audit.
   Sai role/ngoài ownership bị server chặn.
4. (3–4phút) Hủy dialog, giả lập offline bằng DevTools → lỗi rõ ràng; khôi phục
   mạng tải lại dữ liệu thật. Thu hồi ADMIN của tài khoản QA rồi refresh → capability
   và quyền server cập nhật. Không gỡ Admin cuối cùng hoặc dùng tài khoản chủ để thử.
5. (4–5phút) Trình bày test snapshot/concurrency/rollback; chỉ rõ chưa có approve,
   publish hoặc TestVersion CRUD. Không gọi tất cả ADMIN03 hoặc WEB06 Done.

## Manifest52file và hunk dùng chung (tích lũy ADMIN03)

```text
backend/src/main/java/com/toeic/backend/content/application/ContentManagementService.java
backend/src/main/java/com/toeic/backend/identity/application/AdminCapabilities.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/RbacRepository.java
backend/src/main/java/com/toeic/backend/questionbank/api/TeacherQuestionController.java
backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringDtos.java
backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringService.java
backend/src/main/java/com/toeic/backend/questionbank/infrastructure/QuestionAuthoringRepository.java
backend/src/main/java/com/toeic/backend/system/api/ContentManagementExceptionHandler.java
backend/src/main/java/com/toeic/backend/system/api/ModerationController.java
backend/src/main/java/com/toeic/backend/system/api/ModerationExceptionHandler.java
backend/src/main/java/com/toeic/backend/system/api/ModerationRequest.java
backend/src/main/java/com/toeic/backend/system/application/ContentManagementAudit.java
backend/src/main/java/com/toeic/backend/system/application/MediaPublicationPolicy.java
backend/src/main/java/com/toeic/backend/system/application/ModerationEvidence.java
backend/src/main/java/com/toeic/backend/system/application/ModerationException.java
backend/src/main/java/com/toeic/backend/system/application/ModerationService.java
backend/src/main/java/com/toeic/backend/system/infrastructure/AdminReadRepository.java
backend/src/main/java/com/toeic/backend/system/infrastructure/ModerationRepository.java
backend/src/main/resources/db/migration/V35__content_review_publication.sql
backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/application/AdminCapabilitiesTest.java
backend/src/test/java/com/toeic/backend/questionbank/api/ContentAdministrationIntegrationTest.java
backend/src/test/java/com/toeic/backend/questionbank/api/ModerationIntegrationTest.java
backend/src/test/resources/fixtures/content03-manager-cleanup.sql
backend/src/test/resources/fixtures/q03-authoring-cleanup.sql
docs/ADMIN03_Content_Moderation.md
docs/API_Specification.md
docs/ERD_v1.6.md
docs/LLD.md
docs/SRS.md
frontend/web/src/app/routes.jsx
frontend/web/src/components/organisms/LearnerSidebar.jsx
frontend/web/src/features/admin/ModerationNavigation.jsx
frontend/web/src/features/admin/content-editor.js
frontend/web/src/features/admin/content-management.api.js
frontend/web/src/features/admin/moderation.api.js
frontend/web/src/features/admin/monitoring.api.js
frontend/web/src/features/auth/api/auth.mapper.js
frontend/web/src/pages/admin/AdminAuditPage.jsx
frontend/web/src/pages/admin/AdminContentPage.jsx
frontend/web/src/pages/admin/ContentEditor.jsx
frontend/web/src/pages/admin/ModerationPage.jsx
frontend/web/tests/admin.authoring.spec.js
frontend/web/tests/admin.content.spec.js
frontend/web/tests/admin.moderation.spec.js
frontend/web/tests/admin.monitoring.spec.js
frontend/web/tests/content-editor.test.mjs
frontend/web/tests/content-management.api.test.mjs
frontend/web/tests/moderation.api.test.mjs
frontend/web/tests/monitoring.api.test.mjs
frontend/web/tests/rbac.capabilities.test.mjs
```

Manifest hiện tại52file:27file mới,25file dùng chung. Mốc trước workflow có
33file (12mới,21dùng chung); lượt workflow bổ sung19file/hunk liên quan, gồm
assertion RBAC trong AuthFlow. Các hunk Problems của file đó không thuộc nhiệm vụ.

Mô tả phần authoring trước workflow: helper archive/handler được đổi tên sang Management
để dùng chung create/update/archive, giữ nguyên hành vi archive và error code cũ.
Authoring service thêm audit sau mutation và trước commit; không đổi validation,
ownership, clone hoặc publication policy. Audit repository chỉ
allowlist action/type/status/UUID version/tên field và predicate count/usage tương ứng.
Question options cung cấp Part/topic thật; editor dùng ID tham chiếu thay vì suy đoán.
Hai fixture cleanup
chỉ xóa audit do chính actor test tạo trước cleanup FK users, không xóa/skip test.
Capability/mapper bổ sung hai boolean, unit expectations cập nhật đủ DTO; route/menu
và audit UI chỉ nối phần có backend thật. Ba tài liệu chính ghi runtime partial.
Không có migration, permission mới, pom/env hoặc script vận hành thay đổi.

Các hunk nền AGENTS,6file Problems (kể cả QuestionAuthoringConcurrencyTest),
PostmanR2,tatus và .vscode/settings.json được bảo toàn, nằm ngoài phạm vi nhiệm vụ.
AuthFlow chỉ có hunk assertion bốn quyền mới thuộc ADMIN03 như mục cuối. Không staging,
commit,push hoặc merge PR. Phần còn thiếu phải tiếp tục sau khi chốt workflow và
tích hợp EXAM04 an toàn, không import nhánh riêng/migration trùng để vượt blocker.

Kiểm ở mốc đọc/archive: git diff --check trên17file tracked nhiệm vụ và kiểm no-index cho8file
mới đều đạt; manifest cũ25file, không conflict marker/secret demo/private key/
token hoặc artifact trong phạm vi. Index rỗng, HEAD vẫn1389e9b. Source chức năng
không đổi trong/sau full; chỉ chốt tài liệu, không chạy lại full. Dịch vụ demo
được giữ chạy để mở thử; không thay đổi toeic_db hoặc Flyway history.

## Mở rộng độc lập tạo/sửa và audit — sau mốc1058test

Đây là code mới sau giai đoạn đọc/archive, không lấy bằng chứng1058/66test để
nhận source hiện tại đã đạt. Gói S1/R1/V1/M1/G1 vẫn chưa được xác nhận; chưa thêm
review/publish transition, permission, contributor storage, revision hoặc migration.

### AC hiện tại và API

| AC | Trạng thái sau mở rộng |
|---|---|
| Đọc/archive/quyền/snapshot của giai đoạn trước | Giữ lại toàn bộ, nằm trong26test backend và20browser hồi quy mới |
| UI tạo/sửa content/question/group | Đã nối POST/PATCH hiện có, typed form/validation/loading/error/success; không sửa published payload |
| Partial/null/media | Chỉ PATCH field đổi; omitted assets giữ và revalidate quyền; checkbox thay toàn bộ với [] bỏ links nháp; không gửi URL, actor/owner/status |
| Question metadata | Part/topic lấy DB qua GET /teacher/question-options, không suy partId từ partNumber hoặc từ published bank; quyền question:manage:self/any và actor tại filter/service |
| Immutable metadata | Content category/difficulty chỉ DRAFT; Q part/topic và groupType chỉ DRAFT một version như Q03; published bị disable UI và server409 |
| Audit create/update | Nguyên tử cùng aggregate/version/choices/assets, lỗi503 rollback; chỉ status/version UUID/tên field, không giá trị payload |
| Concurrency | Hai edit cùng published aggregate tạo đúng một working draft và hai audit cho hai write commit; không tự thêm optimistic API contract vào CRUD cũ |
| Review/publish và bộ đề | Ở mốc authoring này workflow còn OPEN; nay được thay thế bởi quyết định2026-11-15 và phần triển khai cuối tài liệu. G1/bộ đề vẫn bị chặn; không nhận toàn ADMIN03/WEB06 Done |

API dưới /api/v1:

- POST/PATCH /teacher/contents[/{id}], /teacher/questions[/{id}],
  /teacher/question-groups[/{id}] giữ contract CONTENT03/Q03.
- GET /teacher/question-options (không query) trả `{parts:[{partId,partNumber,skill,name}],topics:[{topicId,name}]}`,
  reuse DTO reference/read repository;401/403/400 theo session/permission/query.
  Đây là lookup hỗ trợ form, không API CRUD hoặc workflow mới trùng.
- CONTENT_AUDIT_UNAVAILABLE503 cho create/update audit thất bại;
  ARCHIVE_AUDIT_UNAVAILABLE503 cũ giữ nguyên. Không có migration mới.

Payload ví dụ, dùng ID tham chiếu thực từ lookup:

```json
{"title":"Grammar draft","categoryCode":"GRAMMAR","body":"Draft text"}
```

```json
{"summary":null,"body":"Edited draft body"}
```

```json
{"partId":5,"questionText":"Choose the best answer.","difficulty":"EASY","choices":[{"label":"A","content":"Option A","isCorrect":false,"displayOrder":1},{"label":"B","content":"Option B","isCorrect":true,"displayOrder":2},{"label":"C","content":"Option C","isCorrect":false,"displayOrder":3},{"label":"D","content":"Option D","isCorrect":false,"displayOrder":4}]}
```

partId5 ở ví dụ chỉ là minh họa; không khẳng định PK5 luôn là Part5. Part2 nhận
A–C, các Part khác A–D; group pin tường minh cho3/4/6/7. UI group hiển thị version
UUID để chọn pin, không lấy latest tự động. Group order/publish vẫn chờ G1/G2.
Media replacement là danh sách tường minh, có trường transcript tùy chọn cho Q/group;
không tự prefill tập media bị lọc hoặc âm thầm bỏ transcript/link cũ.

ContentManagementAudit tái sử dụng writer RbacRepository/V9 trong transaction
MANDATORY. Thêm CONTENT_CREATED/UPDATED, QUESTION_CREATED/UPDATED,
QUESTION_GROUP_CREATED/UPDATED; CREATED lưu after DRAFT/version UUID/field names,
UPDATED lưu before/after version UUID + parent status và requestedFields. Tên
requestedFields là **field được gửi**, không khẳng định mọi value đều đổi. Archive
giữ trước/sau status và idempotence. ADMIN07 count/usage/OTHER predicates cùng
allowlist16action; các event này ghi cả Teacher và Admin, không phải mọi hoạt động.
Projection chỉ UUID hợp lệ, status và tên field allowlist; không trả raw details,
title/body/answer/explanation/transcript hoặc lý do review chưa được triển khai.

### Kiểm chứng mới, không sửa bằng chứng cũ

- Lượt targeted đầu bị249context error vì cluster demo55459 tắt, Flyway không kết
  nối được; chưa kiểm chứng code. Khởi động lại đúng workspace cluster, không dùng
  toeic_db. Log giữ riêng admin03-authoring-offline.log.
- Lượt sau249test:246đạt/3error ở fixture archive CHECK chặn cả CREATED mới; sửa
  CHECK chỉ chặn action ARCHIVED của đúng target (vẫn kiểm audit fail/rollback).
  Chạy lại riêng ContentAdministrationIntegrationTest26/26 đạt,0fail/error/skip.
  Gồm create/update success,3create-audit rollback,3update/clone rollback,
 3concurrent edit, metadata lookup quyền, và13case archive/snapshot cũ.
- Frontend71/71, lint/build exit0; warning có sẵn. Sau fix clear lỗi cũ khi retry
  save thành công, kiểm lại frontend71/71 và build hiện tại; không đổi backend code.
- Browser authoring4/4 đạt,19request HTTP tường minh thêm request từ UI: content
  create/PATCH/null, published→working draft giữ pointer/public GET/history, group
  và question Part/group pin/choices/explanation, invalid media UUID/offline retry.
- Browser regression cuối20/20 gồm cả4authoring+3read/archive+13Admin/Auth trước;
  authoring19HTTP, read/archive49HTTP tường minh; cộng request UI và hồi quy khác.
  Mọi successful response thật, chỉ fault transport abort khi kiểm offline.
- Full backend code mới:1071/1071,0failure/error/skip,BUILD SUCCESS trong9phút23giây,
  kết thúc2026-11-14T23:15:25+07:00, DB riêng toeic_admin03_regression. Có thay đổi
  production code nên chạy một lượt hồi quy mới; không lặp lại1058chỉ vì tài liệu.
  Hash code trước/sau lượt full không đổi. Source gồm các sửa Problems có sẵn giữ
  nguyên trong working tree; chưa có bằng chứng cây staged/commit độc lập.

Log mới ignored backend/target: admin03-authoring-{targeted,final,full,browser}.log,
admin03-editor-frontend-{test,lint,build}[-final].log, admin03-editor-build-complete.log,
admin03-editor-regression.log. Mốc SQL demo sau smoke có CREATED/UPDATED/ARCHIVED
thật cho cả ba tài nguyên; schema vẫn V34 checksum1299604777 success=true.

Demo bổ sung: Admin → /admin/content → Tạo bản nháp → Lưu → Sửa bản nháp; xem
version/metadata, Part catalog và explicit group pin, đối chiếu audit field names.
Khi sửa published chỉ tạo/dùng một DRAFT, không đổi pointer; không có nút review/
publish. Browser đã so public GET/old version bằng nhau và hoàn nguyên nội dung
working draft mẫu sau kiểm tra; có thể còn working version mới, không xóa lịch sử.
POST CRUD hiện không có idempotency-key contract; test offline abort trước server,
không coi đó là bảo đảm mọi retry POST đều không trùng trong tình huống mất response.

Kiểm cuối:33file manifest khớp working tree nhiệm vụ (21tracked,12mới),
git diff --check cho phần tracked và no-index --check cho file mới không lỗi
whitespace; không conflict marker/private key/token pattern/artifact trong phạm vi.
Cả10hash file nền không đổi, index rỗng, HEAD1389e9b và nhánh cũ giữ nguyên.
Bằng chứng ignored backend/target/admin03-authoring-final-evidence.json. Web5173,
backend8080, Mailpit8025 và cluster demo55459 vẫn đang lắng nghe; không tác động
toeic_db, không nâng cấp hoặc sửa history DB cá nhân.

### Chi tiết remote cần thống nhất trước tái sử dụng EXAM04

PR61 review hiện nhận APPROVE/RETURN; note tùy chọn <=2000ký tự, không có check
actor khác owner/contributor trong method review. Cả review/publish dùng
test:publish + test:manage:any; đây không phải R1 đã chốt. Publish kiểm media theo
owner của đề và bắt media_uploads, khác M1 publisher-owner/grant; JSON draft được
materialize thành bank versions bởi ExamQuestionPublisher. Save hiện không ghi
audit update tương ứng. Route /teacher/tests/{id} dùng TestVersion UUID, không
được suy là tests.test_id. Các điểm này cần contract và kiểm chứng tích hợp riêng;
không nhập code để bỏ qua quyết định hoặc xung đột V26/attempt aliases.

## Workflow đã triển khai theo quyết định xác nhận2026-11-15

Phần này thay thế các ghi chú OPEN về content/question workflow ở mốc trước;
không thay đổi các giới hạn G1, media provisioning, EXAM04 hoặc unpublish/restore.
Fetch origin thành công: develop vẫn ce552e2, remote develop cao nhất V33,
feature có V34; git log --all không có V35 trước khi thêm. Chỉ thêm V35,
không sửa V1–V34, không nhập develop/PR61 hoặc tạo commit.

### API và revision

Tất cả path dưới /api/v1:

| API | Contract |
|---|---|
| GET /moderation/capabilities | Cờ author/review/publish cho contents/questions/groups từ actor ACTIVE và RBAC hiện tại; không suy quyền từ tên role ở UI |
| GET /moderation/{kind}?page=0 | Page0, size20 cố định, sort parent.updatedAt DESC,parentId,versionNumber DESC; count/data cùng predicate own/any hoặc Admin review/publish |
| GET /moderation/{kind}/{parentId}/versions/{versionId} | Chính xác version thuộc parent, payload authoring theo scope, sourceVersionId, revision/approvedRevision/evidenceComplete, decision reason và capability từng command |
| POST cùng path /submit | `{expectedRevision}`; authoring self+own/any; DRAFT→IN_REVIEW, ghi submitter |
| POST /approve | `{expectedRevision}`; ACTIVE ADMIN + content:review/question:review, không contributor; IN_REVIEW→APPROVED |
| POST /return | `{expectedRevision,reason}`; review permission, IN_REVIEW→DRAFT, reason bắt buộc1–2000 |
| POST /revoke | `{expectedRevision,reason}`; review permission, APPROVED→DRAFT, xóa approval |
| POST /publish | `{expectedRevision,visibility:"PUBLIC"}` cho content; question độc lập chỉ expectedRevision. APPROVED→PUBLISHED; đúng approvedRevision, actor/media hợp lệ |

kind chỉ contents/questions/groups. Mọi command không query; từ chối key lạ,
duplicate, null/sai kiểu/range400. Visibility bắt buộc tường minh PUBLIC hoặc
AUTHENTICATED cho content, không nhận trên question/group/command khác. Reason
chỉ nhận cho return/revoke. 401 chưa xác thực,403 thiếu quyền,404 ngoài ownership
hoặc parent/version sai;409 stale/state/self-review/evidence/media/group;
503 lỗi DB/audit, rollback toàn bộ và không trả SQL/stack trace.

Ví dụ sau khi GET detail lấy revision hiện tại:

```http
POST /api/v1/moderation/contents/{id}/versions/{versionId}/submit
Content-Type: application/json
Authorization: Bearer <local-session>

{"expectedRevision":0}
```

```json
{"expectedRevision":1,"reason":"Bổ sung nội dung trước khi xuất bản"}
```

```json
{"expectedRevision":4,"visibility":"PUBLIC"}
```

Revision là token của version, tăng sau authoring hoặc transition đã commit để
chặn cả trạng thái bị đổi rồi quay lại như cũ. Approval lưu revision sau approve;
không sửa payload ở revision đó. PATCH khi IN_REVIEW/APPROVED trả409. Các API
PATCH authoring cũ vẫn giữ contract, không bắt thêm field revision vào payload;
parent lock tuần tự hóa với submit/review/archive, transition cũ bị từ chối nếu
authoring thắng trước. Không tuyên bố optimistic locking cho mọi PATCH cũ.
Transition không tự replay như Practice submit: nhận409 phải GET lại version để
xác định trạng thái đã commit, không retry mù.

### Persistence, contributor, audit và media

V35 không backfill: version mới tạo/clone ghi evidence_complete=true;
legacy draft được sửa chỉ ghi false và không được chuyển trạng thái. Cycle key
là published source ID khi clone hoặc parent ID trước publication đầu tiên.
Direct event CREATED/EDITED/SUBMITTED pin version/actor/time; tập cấm truy vấn hợp
các event trong cùng parent/cycle. Trả sửa không xóa event. Sau publish, working
version tiếp theo có source/cycle là pointer mới, không kế thừa tập cấm cũ.
sourceVersionId giữ truy nguyên, không thành danh sách cấm lịch sử vô hạn.

ModerationEvidence chạy trong transaction authoring sau flush; khóa parent trước
version hiện hữu. Transition dùng shared advisory RBAC, recheck actor ACTIVE và
role/permission, rồi parent/version, media/grants theo UUID tới commit. Không gọi
authoring validator yêu cầu manage từ publish; MediaPublicationPolicy dùng lại
MediaReuseRepository và policy owner/grant/visibility/type, không cấp thêm manage.
PUBLIC không thay owner/grant, PRIVATE luôn bị từ chối; question media chỉ AUDIO/
IMAGE. Grant hiện chỉ revoked_at null, không expiresAt. Không có API/UI provision
media/grant hoặc chuyển owner; không thêm SQL cấp quyền cho demo. Kiểm grant
revoked dùng chính fixture grant có sẵn trên DB test, không phải khả năng sản phẩm.

Audit SUBMITTED/APPROVED/RETURNED/APPROVAL_REVOKED/PUBLISHED ghi cùng mutation,
before/after status/version/revision; lỗi audit rollback cả pointer, visibility,
approval, decision và contributor. ADMIN07 allowlist31action và count/usage đồng
bộ. Projection audit chỉ status/version UUID/tên field, không reason/nội dung/
key/explanation/transcript. Reason riêng chỉ endpoint review được authorize đọc.
RbacRepository.ENFORCED_PERMISSIONS thêm đúng bốn permission có enforcement mới.

### UI và AC

| AC | Kết quả/giới hạn |
|---|---|
| Teacher soạn/gửi, Admin trả sửa/gửi lại/approve/publish, Learner đọc | API và browser thật dùng content không media; /teacher/content tái sử dụng editor, /moderation dùng capability server |
| Sửa sau duyệt/publish | Review/approved khóa; revoke rồi gửi lại; PATCH published tạo working draft từ pointer, bản Learner đang đọc không đổi |
| Tự duyệt, stale, legacy, permission live | Integration tests; publisher không cần manage, thu hồi permission có hiệu lực request kế tiếp |
| Concurrency/audit rollback | Race submit/edit/approve/publish/archive; rollback approval và publication khi audit lỗi |
| Snapshot | Test publish Question version mới, old bytes và Practice attempt/result pin không đổi |
| Media | M1 enforce, thiếu quyền409; có/thu hồi grant fixture kiểm trên DB test. Demo UI không media; cấp quyền media dùng chung chưa hoàn thiện |
| Group | Review có thể lưu trạng thái, publish group/grouped-question409 GROUP_PUBLICATION_NOT_AVAILABLE. G1 batch chưa triển khai; giữ authoring/read/archive, không thay current publication |
| Bộ đề/WEB06 | EXAM04/PR61 còn V26 và attempt route conflict, chưa nhập. Không nhận toàn bộ ADMIN03/WEB06/Sprint3 Done |

UI có loading/error/empty, xác nhận command, reason validation, visibility không
chọn mặc định, giữ lỗi transport và không fallback mock; sau stale phải mở lại
detail. /admin/content giữ Admin capability cũ; /teacher/content dùng authoring
capability. Link workflow lấy capability server, không hard-code role ở frontend.

### Kiểm chứng working tree mới

- Compile Maven Wrapper offline thành công; lần sandbox đầu không mở được lock
  .m2, chạy lại với quyền cache. Không tính compile skipTests là test pass.
- Authoring/archive baseline219/219 đạt trên toeic_admin03_test sau V35.
- Workflow ban đầu16/16; mở rộng21/21 + QuestionAuthoringConcurrency13/13 +
  AdminMonitoring17/17 =51/51. Sau sửa danh mục RBAC: workflow21 + RBAC29 =50/50.
- Frontend74/74, lint/build exit0; giữ warning có sẵn. Lần sandbox spawn EPERM
  không tính đạt; chạy lại Node/Vite ngoài sandbox thành công.
- Browser workflow2/2,16HTTP tường minh cộng request UI thật. Regression đầu20/22:
  catalog RBAC thiếu bốn code gây404 thay409 self-escalation; sửa enforcement,
  không đổi assertion. Case audit revoke đợi initial fetch xong trước revoke để
  kiểm đúng refresh; không giảm kiểm quyền. Chạy lại hai nhóm browser6/6 đạt.
- Browser regression cuối22/22 đạt trong khoảng1,3phút, gồm authoring4,
  đọc/archive3, workflow2, audit3, RBAC3, Settings3 và Auth/ADMIN014.
- Full backend duy nhất trên DB trống toeic_admin03_workflow_regression:
  1091/1092 đạt,1failure/0error/0skip,BUILD FAILURE trong12phút31giây, kết thúc
  2026-11-15T01:02:31+07:00. Flyway áp dụng36migration tới V35 từ schema trống.
  Failure duy nhất AuthFlowIntegrationTest.seededRolesCanReadTheirOwnProfile[ADMIN]:
  expectation cũ thiếu đúng content:review/content:publish/question:review/
  question:publish. Không giảm assertEquals hoặc thay bằng containsAll; chỉ thêm
  đúng bốn quyền đã được migration xác nhận. Sau sửa riêng assertion, chạy lại
  AuthFlow31/31 đạt,0failure/error/skip,BUILD SUCCESS trong36,878giây, kết thúc
  2026-11-15T01:17:10+07:00 trên cùng DB regression. Không chạy lại full sau thay
  assertion; không mô tả code sau sửa assertion là full local xanh.
- Có cảnh báo housekeeper thread starvation/clock leap khoảng3phút15 trong lượt
  full; đã kiểm log/process vẫn tiến triển. Failure thực tế là expectation quyền,
  không quy lỗi đó cho clock và không thay TTL/chính sách auth.

Các log ignored backend/target/admin03-workflow-{compile,baseline,targeted,
targeted-final,rbac-final,fe-test-final,fe-lint-final,fe-build-final,browser,
regression,browser-recheck,regression-final,full,authflow-recheck}.log. Không coi log PowerShell
NativeCommandError do warning stderr là failure nếu process exit0 và summary pass;
đọc exit/summary thực tế. Không chạy Maven clean vì target chứa cluster demo.

Đây là nguồn working tree, có cả sáu sửa Problems giữ nguyên. Chưa stage/commit;
không có bằng chứng cây commit ADMIN03 độc lập. Commit HEAD1389e9b chỉ là mốc nền,
không chứa 33file ADMIN03 trước workflow hoặc phần workflow mới. Manifest tích
lũy52file là phạm vi cần bàn giao cùng nhau; không tuyên bố phần mới chạy độc lập
khi bỏ các dependency ADMIN03 chưa commit.

AuthFlowIntegrationTest là file chung: chỉ hunk danh sách quyền ADMIN trong test
seededRolesCanReadTheirOwnProfile thuộc ADMIN03. Hai hunk xóa import Problems có
sẵn vẫn nằm ngoài nhiệm vụ. Chín file nền khác giữ nguyên hash; đảo riêng hunk
assertion ADMIN03 khôi phục đúng SHA256 byte gốc của AuthFlow trước workflow.
Không ghi đè/thu hồi các sửa Problems, không stage cả file chung trong lượt này.

V35 đã được kiểm nâng cấp V34→V35 trên toeic_admin03_test/toeic_learning_demo và
từ schema trống trên toeic_admin03_workflow_regression. Không sửa V35 sau khi
áp dụng, không kết nối hay migrate toeic_db. Dịch vụ demo vẫn chạy ở8080/5173,
PostgreSQL55459 và Mailpit8025. Bằng chứng cuối ignored
backend/target/admin03-workflow-final-evidence.json ghi nguồn/hunk và giới hạn.

### Demo khoảng5phút

1. Mở http://127.0.0.1:5173/teacher/content, đăng nhập Teacher demo bằng credential
   local ignored backend/target/local-demo-credentials.json. Tạo học liệu text,
   không media; /moderation chọn content/version và Gửi duyệt.
2. Cửa sổ Admin riêng /moderation: xem đúng nội dung/revision, nhập lý do Trả sửa.
3. Teacher sửa bản DRAFT và gửi lại. Admin duyệt; thử PATCH phải409 cho tới revoke.
4. Admin chọn PUBLIC, publish. Learner /catalog/{id} đọc đúng title/body/pointer;
   sửa published bằng Teacher tạo draft mới, reload Learner vẫn bản published cũ.
5. Admin xem audit theo entityId; giải thích contributor vòng mới, legacy409,
   quyền media và group/bộ đề chưa hoàn thành. Không thử tự sửa history/DB cá nhân.

Chạy lại test khi code thay đổi: backend Maven Wrapper với datasource test riêng
trên55459, không toeic_db; frontend `npm test`, `npm run lint`, `npm run build`,
`npm exec -- playwright test tests/admin.moderation.spec.js`. Start/stop demo dùng
backend/scripts/start-local-demo.ps1 và stop-local-demo.ps1 (không -StopPostgres
khi còn test/client khác). Không cần seed lại hoặc reset DB cho workflow này.
