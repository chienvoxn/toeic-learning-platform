# EXAM-04 — biên soạn bộ đề DRAFT

Các mục triển khai bên dưới ghi bằng chứng working tree trước commit. Lượt review/commit riêng
ngày21/11 và kết quả nguồn staged được ghi ở cuối tài liệu; không đổi nhãn các lượt test cũ.

## Phạm vi và nguồn

Working tree trên `feat/web-auth-integration`, base `e851f118fbda5ecafb6df843fcde53d0c9713424`.
Mốc tích hợp `012c257f81b840b7bf7fa7ad6c79e3c43e66e285` của chienvoxn được giữ nguyên.
Fetch origin trong lượt này: develop `a1a85b6edf388f68b60ea44f5b251e8a47c78a81` đã là ancestor của HEAD.
Không stage, commit, push hoặc thao tác PR trong lượt này.

PR [#61](https://github.com/chienvoxn/toeic-learning-platform/pull/61) vẫn open, chưa merge.
Đã đọc commit `66e71a226f3ce7c6a6f3f05f3b314f641d518027`, tác giả
`tannhat1607 <tannhat1607@gmail.com>`, và đối chiếu với HEAD; chưa có patch đó trên HEAD.
Tái sử dụng nguyên `assessment/application/ExamAccess.java` từ commit này; giữ nguồn/tác giả tại đây
để attribution khi commit sau. Pattern ownership/khóa version cũng được đối chiếu với service remote.
Nếu sau này được phép commit TV2, giữ attribution này và ghi Co-authored-by cho tác giả phần tái sử dụng;
không viết lại tác giả/lịch sử commit gốc.
Không cherry-pick/merge nhánh thành viên. Phần triển khai còn lại dùng tham chiếu version, không dùng
inline import/publisher của remote. Không tự đổi tác giả commit gốc.

| Collision/khác biệt | Xử lý |
|---|---|
| Remote `V26__exam_workflow.sql` CRC32 -289424885 vs HEAD `V26__practice_grading_snapshots.sql` CRC32 -856194547 | Không nhập/đổi số migration remote; thêm V40 độc lập cho DRAFT |
| Remote alias submit/result trùng `/attempts` | Không nhập ExamAttemptController; giữ toàn bộ Practice/Section hiện hành |
| Remote `/teacher/tests/{id}` dùng TestVersion UUID | API mới phân biệt testId và versionId trên path; chưa có client remote trong HEAD để phá vỡ |
| Inline document tự tạo/publish question bank | Chọn version published hiện hành từ ngân hàng có sẵn, không side effect xuất bản |
| Remote review/publish và số câu/thời lượng TOEIC hard-code | Không coi là quyết định đã chốt; chưa triển khai các endpoint này |
| V34/V35 cùng version, khác lịch sử | Giữ nguyên historical locations/coordinator/policy đã bàn giao; V40 là phần nối tiếp chung |

Không tìm thấy backlog/AC độc lập trong repo ở mốc rà trước; đối chiếu SRS UC14/BR03,
HLD phần B, LLD TestVersion, ERD và API DESIGN với quyết định người dùng trong lượt này.
Không coi DESIGN/OPEN là AC publish đã chốt.

## Đối chiếu Sprint 3

| AC | Backend | UI nối API | Dữ liệu/dependency/OPEN |
|---|---|---|---|
| EXAM-02 | Schema TestVersion có sẵn; TEST_VERSION attempt runtime chưa có | Full Mock hiện chưa nối runtime này | Cần publication/blueprint và đường tạo attempt dùng version; không suy từ UI Listening |
| EXAM-03 | Section cho submit muộn khi có request, atomic/idempotent | Timer/manual submit đang có | Chưa có worker auto-submit; policy scheduler còn thiếu |
| EXAM-04 DRAFT | Tạo/đọc/sửa own/any, version refs, group/order, audit/revision/transaction | `/teacher/tests` dùng layout và API client hiện hành | Dùng ngân hàng published thực tế; fixture test không là dữ liệu production |
| EXAM-04 publication/versioning | Không mở publish/unpublish/retire/fork/import | Không có nút publish | ADMIN-03/G1, mandatory blueprint/count/time và unpublish/active attempt chưa chốt |
| EXAM-05 | Practice/Section raw total, accuracy, thống kê Part có sẵn | Kết quả Practice/Section có sẵn | Chưa quy đổi TOEIC; conversion governance OPEN |
| NFR-02 | Có benchmark từ nhóm | Không là bằng chứng p95 mới | Chưa có workload/đo tải cho EXAM-04; không nhận đạt p95 |

## Contract DRAFT thực tế

Quyền `test:manage:self` mặc định Teacher/Admin; `test:manage:any` mặc định Admin.
Luôn đối chiếu actor trong SecurityContext và ownership ở service. Sai owner trả 404,
không có quyền trả 403; không dựa riêng vào role/nút UI. Chọn tham chiếu còn cần `question:read`.
Không cấp `test:publish`, không mở media hoặc thay đổi quyền nội dung/câu hỏi.

| Endpoint dưới `/api/v1` | Payload/kết quả |
|---|---|
| `GET /teacher/tests/capabilities` | `{manage,readQuestions}`; chỉ cho actor có quyền quản lý |
| `GET /teacher/tests?page=0` | Tối đa 50 parent, own/any, thứ tự created_at/test_id ổn định |
| `GET /teacher/tests/question-units?partNumber=3&page=0` | Tối đa 50 nguyên mục published; version ID, group version ID, prompt; không đáp án/media |
| `POST /teacher/tests` | Document bên dưới; 201, `{testId,versionId,ownerId,version,revision,status,document}` |
| `GET /teacher/tests/{testId}` | Version mới nhất; ID là parent Test |
| `GET /teacher/tests/{testId}/versions/{versionId}` | Chính xác version thuộc parent; không đoán version ID từ testId |
| `PUT /teacher/tests/{testId}/versions/{versionId}` | `{revision:0,document:{...}}`; thay document toàn bộ; revision tăng 1 |

```json
{
  "title": "Bộ đề nháp",
  "description": null,
  "testType": "FULL",
  "durationSeconds": null,
  "sections": [
    {"type":"READING","parts":[
      {"partNumber":5,"questions":[
        {"questionVersionId":"UUID-question-version-thực-từ-question-units","groupVersionId":null}
      ]}
    ]}
  ]
}
```

`testType`: PRACTICE/LISTENING/READING/FULL. DRAFT được rỗng Section/câu và để trống
duration; nếu có duration thì là số nguyên dương trong miền INTEGER. Không áp cấu trúc
200 câu hoặc thời lượng TOEIC chưa chốt. Title 1–255 ký tự không trắng; description tối đa 10000.
Section nếu có theo Listening → Reading; Part tăng dần, đúng Section, không trùng.
Thứ tự câu trong mảng là thứ tự lưu `test_questions.question_order`, không suy từ UUID.
Một question version chỉ xuất hiện một lần trong bộ đề.

Nguồn chọn dùng publication pool hiện hành, cùng eligibility với Practice: published/current,
không future/archive, SINGLE_CHOICE hợp lệ; không expose key/explanation. Group phải đủ current members,
cùng Part, đúng group version, contiguous và theo `group_question_order` dương, không trùng.
Không tự đặt số thành viên nhóm theo mẫu TOEIC. DRAFT giữ ID đã chọn khi ngân hàng đổi;
lần lưu tiếp theo revalidate và báo 422, không thay bằng version mới âm thầm.
GET DRAFT chỉ trả tài liệu/tham chiếu đã lưu, không hydrate nội dung/media riêng tư.
Ngân hàng DRAFT/unpublished không được dùng làm nguồn chọn trong contract này.

Parser cục bộ từ chối field lạ, duplicate JSON ở mọi cấp, coercion số/UUID, query ngoài allowlist.
Không thay cấu hình Jackson toàn cục. API không nhận owner/status/publish/answer/media URL.

| HTTP/code | Tình huống |
|---|---|
| 400 `VALIDATION_ERROR` | Shape/type/UUID/Section/Part/query/revision không hợp lệ |
| 401 | Chưa đăng nhập; writer auth hiện hành |
| 403 | Thiếu quyền quản lý hoặc question:read khi dùng bank |
| 404 `TEST_NOT_FOUND` | Không sở hữu/không tồn tại/sai parent-version |
| 409 `TEST_REVISION_CONFLICT` | Expected revision cũ |
| 409 `TEST_VERSION_NOT_EDITABLE` | PUBLISHED/RETIRED, legacy không có document DRAFT, hoặc có bất kỳ attempt pin |
| 422 `TEST_REFERENCE_INVALID` | Version/group/publication/ordering không hợp lệ hoặc FK không khớp |

Không có API publish/unpublish/import/review/fork trong lượt này. Request đến các path đó
không tạo mutation. Test published/retired dùng fixture trạng thái để kiểm guard, không là
bằng chứng workflow publication end-to-end. Không ghi history/version mới cho một bản đã pin.

## Persistence, audit và nâng cấp

V40 thêm `test_version_drafts(test_version_id FK PRIMARY KEY,revision,document JSONB)` và hai quyền quản lý.
Không ALTER/backfill các row TestVersion/attempt cũ. TestVersion và `test_questions` hiện hữu giữ các FK
version/order; document version DRAFT lưu metadata/Section/Part. Version mới tạo có version_number=1;
chưa mở thao tác tạo version tiếp theo trên parent đã publish.

`ExamDraftService` chịu ownership, reference validation, mutation/audit chung transaction;
`ExamDraftRepository` lưu parent/version/document/reference; `ExamDraftRequest` chỉ parse HTTP strict.
Khóa parent Test trước TestVersion, so revision rồi mới mutation; khóa nguồn nhóm/version theo
thứ tự publication repository hiện hành và đọc lại trước ghi. FK attempt cùng row lock version
ngăn tranh chấp pin/edit. Audit TEST_CREATED/TEST_UPDATED chỉ ID/revision/status, không chứa
title/prompt/đáp án/media. Audit failure rollback toàn bộ document/revision/reference.

Coordinator chỉ thêm version40 vào release prefix, nhận schema-v39 trước V40 và schema-v40
sau V40, cùng catalog hai quyền mới. `schema-v40.txt` xuất từ DB mới chạy toàn bộ SQL gốc
đến V40; khi loại bảng mới, signature trùng hoàn toàn schema-v39 đã bàn giao.
Không đổi bất kỳ SQL/checksum/history đã áp dụng, policy V34/V35, restore equivalence,
repair/clean/baseline hoặc validation. Settings34+RAG35 vẫn bị chặn trước mutation.
Boot/test/CLI/CI tiếp tục qua cùng MigrationConfiguration; standalone Flyway location override
vẫn không phải cách chạy release được hỗ trợ. Bản remote có V26 exam đã áp dụng không được
coi là lịch sử hỗ trợ: cần điều tra riêng, không sửa lịch sử để nhập.
CI hiện cấu hình PostgreSQL17/Java21 và Maven `clean verify`; không có Flyway location override.
Đây là kiểm cấu hình CI, chưa phải một CI run của working tree. Binary release39 cũ sẽ chặn
DB đã lên40 theo guard unknown-version hiện hữu; cần phối hợp triển khai binary mới, không
rollback bằng cách sửa history. Lượt này chỉ áp dụng trên DB lab, không triển khai DB đang dùng.

## Demo

Dùng DB demo riêng; đặt datasource tường minh, không dùng default local/toeic_db.
Chạy migrate/validate qua `backend/scripts/migrate-database.ps1` như runbook migration.
Teacher đăng nhập → sidebar **Biên soạn bộ đề** → tên → chọn Part, **Thêm nguyên mục** →
**Lưu bản nháp**. Sửa metadata, di chuyển cả mục trong cùng Part, bỏ cả nhóm, lưu và đọc lại.
UI gửi đúng expected revision và version path. 409 dừng lưu, yêu cầu đọc lại rõ ràng;
không tự retry đè nội dung phiên khác. Server 403/404/422 được giữ, không fallback sang mock
hoặc tự bỏ validation. Khi đọc lại, mục đã lưu có thể chỉ hiện số thứ tự nếu chưa nạp prompt;
ID/order/group trên wire giữ nguyên. Không phát audio hoặc nhận đây là Full Mock chạy được.

## Kiểm chứng và manifest

Không lấy CI1140/working-tree1150/candidate1120 hoặc kết quả bàn giao cũ làm bằng chứng
của code này. Nguồn lượt này là working tree/JAR/browser production build, chưa staged/committed/CI.

| Kiểm chứng đã chạy | Kết quả và nguồn |
|---|---|
| `mvnw.cmd -DskipTests test-compile`, sau đó package | Biên dịch và đóng gói thành công; Java21.0.7 |
| Test DRAFT liên quan ban đầu | 28/28; lỗi cleanup audit ở lần đầu đã sửa trong test, không sửa file nền |
| Một lượt `mvnw.cmd test` toàn backend | 1201 test, 5 failure RBAC, 0 error/skip; bản biên dịch trước sửa catalog quyền |
| RBAC + AuthFlow + EXAM-04 sau sửa catalog, lifecycle `package` | 88/88, 0 fail/error/skip; JAR mới đóng gói thành công |
| MigrationCompatibility + MigrationHistory trong lượt full | 45 + 3 đạt; coordinator/config/SQL không đổi sau đó nên tái sử dụng đúng phạm vi |
| `npm test` cuối | 93/93 |
| `npm run lint`, `npm run build` cuối | Exit0; lint còn cảnh báo có sẵn, không có cảnh báo trong file EXAM-04 mới |
| Browser production build, Chrome headless | 8/8: Teacher DRAFT3 + Practice/Section5; HTTP fixture, không nhận là DB thật |
| JAR trước sửa catalog, HTTP/PG riêng | 25 request đạt, V39→40, giữ row/history cũ và boot lần2; lưu riêng, không gán cho JAR cuối |
| JAR cuối sau sửa catalog, HTTP/PG riêng | 33 request đạt: DRAFT CRUD, lỗi validation/ownership/revision, catalog RBAC/no-op role permissions, revoke/restore quyền trên phiên hiện tại, pin guard và result cũ, boot lần2 |
| Script CLI chính thức `migrate-database.ps1 -Action Migrate` và `-Action Validate` | Cả hai exit0/BUILD SUCCESS trên DB lab V40; không tham số chọn location |
| Audit source/JAR/Git cuối | 28 file nhiệm vụ; SQL43/resource fingerprint/classes khớp output; không conflict/unmerged/staged, own diff-check và cached diff-check đạt |

5 failure của lượt full đều vì hai quyền test:manage mới thiếu trong danh mục
`RbacRepository.ENFORCED_PERMISSIONS`: API nhận permission IDs từ role nhưng không nhận lại
hai ID mới khi lưu. Đã đăng ký đúng hai mã có enforcement thật, không sửa guard/test expectation.
Chạy lại cả 29 test RBAC, 31 AuthFlow và 28 EXAM-04 đều đạt. Không lặp toàn bộ suite sau
thay đổi một dòng registry; không gọi lượt full trước sửa là full pass của source cuối.
Lượt full còn chạy 10 case trong hai test data-driven nền chưa tracked: đều đạt nhưng
hai file đó/CSV vẫn ngoài manifest nhiệm vụ, giữ nguyên nội dung.

Migration tests dựng history bằng SQL gốc: empty, V33, Settings34, RAG34,
Settings34+ADMIN35, RAG34+ADMIN35, RAG34+RAG35, các prefix36–38 và ba nhánh đã tới39.
Migrate/validate40, restart/no rewrite, seed cũ và schema hội tụ đều qua; history/checksum/
schema/config sai bị chặn trước mutation. Đây là fixture SQL, không là clone DB thành viên lâu dài.
HTTP nâng clone của fixture V39 gốc, không restore đè nguồn. Chưa có bằng chứng nâng một
clone DB thành viên có lịch sử V26 exam remote; lịch sử đó vẫn không hỗ trợ và phải bị chặn.

Source/JAR có 43 SQL: 39 common và 4 historical alternatives V34/V35. Coordinator chọn
41 version duy nhất (1–40 và27.1); không có V26 exam_workflow trong source/JAR. Các historical
alternatives ở location riêng là có chủ đích, không chạy hai V34 hoặc hai V35.

Báo cáo máy: `backend/target/exam04-full-before-rbac-catalog-fix-summary.json`,
`exam04-final-focused-summary.json`, `exam04-real-http-final.json`, `exam04-browser.log`,
`exam04-cli-migrate.log`, `exam04-cli-validate.log`, `exam04-node-tests-final.log`.
Các report/log/JAR chỉ ở target, không trong manifest source.

HTTP dùng clone mới `toeic_exam04_http_final_20261121` từ canonical fixture39, cổng lab55463;
không kết nối toeic_db. Pin guard được thử bằng FK fixture đối kháng gắn attempt Practice đã
chấm vào version, không phải bằng chứng TEST_VERSION runtime. Toàn bộ row/history trước
nâng cấp còn nguyên, kết quả Practice không đổi sau request bị chặn và sau boot lần2.
Đã kiểm Boot/test/CLI thực thi cùng coordinator; CI chỉ được review cấu hình, chưa chạy SHA mới.

Trạng thái bàn giao 21/11: HEAD vẫn e851f11, develop đã fetch/kiểm là a1a85b6 và có trong HEAD;
nhánh feat/web-auth-integration, không merge/rebase dang dở. Repo author/committer vẫn
pthanh137 <sleapf137@gmail.com>. Index giữ nguyên/sạch; không stage/commit/push/tạo PR.
Git có 17 tracked modified và52 untracked: 28 đường dẫn nhiệm vụ cộng41 file nền Git đã có;
settings ignored thứ42 cũng giữ hash. Cả526 file candidate/evidence V35 ghi nhận đầu lượt
giữ nguyên. Global diff-check còn khoảng trắng có sẵn ở AGENTS.md, không sửa hunk nền đó.

Phần tiếp: EXAM-02 cần đường attempt TEST_VERSION và dữ liệu publication hợp lệ;
EXAM-03 cần contract tự chốt khi hết hạn; EXAM-05 giữ raw statistics đến khi governance
conversion được chốt. Không mở các dependency này, G1/media hoặc ADMIN-03 trong lượt DRAFT.

## Manifest nhiệm vụ, chưa stage

28 đường dẫn; hunk trong file có sẵn chỉ phục vụ route/quyền mới, release40, assertion quyền
và tài liệu contract. Hai hunk AuthFlow chỉ thêm kỳ vọng ba grant, không bỏ kiểm chứng cũ.
Không đổi SQL V1–V39 hoặc fingerprint schema-v39/restored-constraints.

```text
backend/src/main/java/com/toeic/backend/assessment/api/ExamDraftExceptionHandler.java
backend/src/main/java/com/toeic/backend/assessment/api/ExamDraftRequest.java
backend/src/main/java/com/toeic/backend/assessment/api/TeacherTestController.java
backend/src/main/java/com/toeic/backend/assessment/application/ExamAccess.java
backend/src/main/java/com/toeic/backend/assessment/application/ExamDraftException.java
backend/src/main/java/com/toeic/backend/assessment/application/ExamDraftService.java
backend/src/main/java/com/toeic/backend/assessment/domain/ExamDraftDocument.java
backend/src/main/java/com/toeic/backend/assessment/infrastructure/ExamDraftRepository.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/RbacRepository.java
backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationHistory.java
backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationSchema.java
backend/src/main/resources/db/compatibility/schema-v40.txt
backend/src/main/resources/db/migration/V40__exam_draft_authoring.sql
backend/src/test/java/com/toeic/backend/assessment/api/ExamDraftIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
backend/src/test/java/com/toeic/backend/system/infrastructure/migration/MigrationCompatibilityIntegrationTest.java
docs/API_Specification.md
docs/EXAM04_Draft_Authoring_20261120.md
docs/MIGRATION35_Source_Compatibility.md
docs/SRS.md
frontend/web/src/app/routes.jsx
frontend/web/src/components/organisms/LearnerSidebar.jsx
frontend/web/src/features/assessment/ExamDraftNavigation.jsx
frontend/web/src/features/assessment/exam-drafts.api.js
frontend/web/src/pages/assessment/TeacherTestsPage.jsx
frontend/web/tests/exam-drafts.spec.js
frontend/web/tests/exam-drafts.test.mjs
```

Ngoài phạm vi, giữ nguyên 42 file theo hash đầu lượt (41 tracked/untracked và 1 settings ignored):
AGENTS.md, năm test Problems, Postman/api-tests, tatus, frontend-web/package-lock,
backend/backend cũ, unit-tests/CSV/report, hai test data-driven chưa tracked, hai tài liệu
Compatibility_Integration V34/V35 chưa tracked và `.vscode/settings.json`. Không đưa log,
dump, backup, JAR, report hoặc công cụ tạm dưới target vào source nhiệm vụ.

Snapshot ban đầu `backend/target/exam04-start-20261120.json`; inventory SQL hai remote/HEAD,
checksum và đối chiếu source/JAR nằm ở `backend/target/exam04-source-audit.json`.

## Review chuẩn bị commit riêng — 21/11/2026

Fetch origin trong lượt review này: develop đã lên
`98d81c0d2d2d3033fd018ecb9d5bb05780b61b39` (PR65 Listening, thêm hai commit so với mốc
đã tích hợp a1a85b6). HEAD trước commit vẫn e851f11, ahead6/behind2 so với develop mới;
ahead9/behind0 so với origin/feat/web-auth-integration0e6b37c. Không merge/rebase trong
lượt commit EXAM-04. Vì vậy bằng chứng ở đây không bao gồm code Listening mới của develop.

Đã rà migration trên cả42 remote refs origin: chưa có V40 trùng. Migration V40 của nhiệm vụ
giữ nguyên bytes/checksum đã diễn tập; không sửa SQL lịch sử. Review28 file khớp đúng hash
bàn giao trước, không phát hiện hunk nghiệp vụ ngoài phạm vi. Fingerprint40 chỉ thêm chín
metadata lines của test_version_drafts; phần39 giữ nguyên. ExamAccess khớp blob gốc PR61;
commit riêng giữ Co-authored-by tannhat1607 <tannhat1607@gmail.com> cùng attribution phía trên.

Shared files được đưa vào index theo từng hunk đã review; file mới add riêng theo manifest.
Không stage AGENTS.md, Problems, Postman, tatus, settings, các report/dump/công cụ hoặc
candidate TV1/V35. Kết quả staged compile/test không suy từ88 test working tree và không
gọi full1201 trước sửa là full pass.

### Bằng chứng nguồn staged cho commit EXAM-04

- Index đúng28 file: áp riêng18 hunk trong11 shared files, add riêng17 file mới.
  Cached diff-check, scan secret/artifact/conflict và đối chiếu SQL lịch sử đều đạt.
  Attribution ExamAccess khớp blob PR61.42 file nền/settings và526 file candidate nguyên hash.
- Xuất486 blob backend trực tiếp từ index vào `backend/target/exam04-index-src-20261121`;
  đối chiếu lại toàn bộ name/object ID với index trước khi chạy. Không lấy Problems hunk,
  AuthDataDrivenTest, VocabularyDataDrivenTest hoặc CSV chưa stage.416 file frontend trong
  index khớp nội dung bản working tree đã kiểm; không lặp frontend khi không đổi source.
- POM tạm `backend/target/exam04-index-pom-20261121.xml` chỉ đổi đường dẫn source,
  test source, resources và output. Parent/dependency/properties/plugin giữ nguyên POM index.
  Output mới `backend/target/exam04-index-output-20261121`, không dùng classes/resources cũ.
- Dùng Maven Wrapper với `-f target/exam04-index-pom-20261121.xml`,
  `-Dtest=ExamDraftIntegrationTest,RbacManagementIntegrationTest,AuthFlowIntegrationTest package`.
  DB mới riêng `toeic_exam04_staged_20261121`, PostgreSQL lab55463; không kết nối toeic_db.
  Compile/package đạt; EXAM-0428 + RBAC29 + AuthFlow31 =88/88, không failure/error/skipped.
  Không chạy lại full local. Đây là bằng chứng nguồn staged, khác với88 test working tree trước.
- JAR staged khớp byte của classes output,43 SQL và fingerprint40 trong index export;
  không còn migration cũ dư ngoài source. Kiểm toàn bộ backend index sau test vẫn khớp
  bản đã xuất; bổ sung tài liệu này không làm đổi backend hoặc frontend được kiểm.

Report máy ở `backend/target/exam04-staged-proof.json`, `exam04-index-export.json`,
`exam04-index-package-tests.log`. Không đưa các artifact/POM tạm/report vào commit.
Chưa có full pass của source cuối và chưa có CI của commit mới. Develop98d81c0 chưa
được tích hợp trong commit riêng này; không nhận code Listening remote đã kiểm trên cây này.

## Phần tiếp nối 2026-11-21

Sau mốc tích hợp `ee95862`, publication/version và runtime Full Mock được triển khai trong working tree riêng,
theo [runbook TV2 Publication / Full Mock](TV2_Publication_FullMock_20261121.md).
Các kết quả staged/commit và full local cũ ở trên giữ nguyên nhãn lịch sử; không dùng để chứng minh code mới.
Gói tiếp nối không stage/commit/push, không thay SQL V40 hoặc historical V34/V35, và giữ attribution PR #61.
