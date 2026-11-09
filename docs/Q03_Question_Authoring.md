# TV2 — Sprint 2 — Q-03: Teacher question authoring

## Nguồn và quyết định sản phẩm

Backlog do người dùng cung cấp: **Teacher CRUD câu hỏi, đáp án, lời giải, audio/ảnh/transcript**; depends Q-01 và AUTH-03. Ảnh backlog chưa có AC chi tiết. Các chính sách dưới đây là **quyết định sản phẩm do người dùng chốt ngày 2026-11-09**, không phải AC tìm thấy trong ảnh.

Đối chiếu SRS UC-13/BR-01–02, HLD Phần B, LLD §2.3, ERD v1.6, API Specification §3.3/4.2, Q-01/Q-02 và CONTENT-03. Tái sử dụng mapping/repository/QuestionPromptMapper, session/principal/RBAC và schema media owner/grant hiện có. Không triển khai lại CONTENT-03.

Thực hiện tại **D:/toeic-learning-platform**, nhánh **feat/web-auth-integration** theo yêu cầu. Không tạo/đổi nhánh hoặc worktree. Phần nền Q-03 từ D:/toeic-tv2-q03 đã được tích hợp và kiểm tra từng file; worktree nguồn giữ nguyên, không tiếp tục sửa. Mọi thay đổi web-auth và AuthServiceTest.java có sẵn được ghi checksum để bảo toàn. Riêng MockMaker được đổi tối thiểu từ subclass sang inline theo yêu cầu sửa cấu hình test.

## Quyền và ownership

- question:manage:self mặc định TEACHER/ADMIN; question:manage:any chỉ ADMIN. Không hard-code role hoặc ADMIN bypass. Actor phải khớp SessionPrincipal tin cậy ở service.
- Teacher quản lý question/group do mình tạo; any quản lý toàn bộ. Không nhận owner/creator/status/pointer/version ID hoặc trường hệ thống qua payload; chưa có chuyển ownership.
- Group cũ created_by null không tự nhận owner từ question; chỉ any quản lý được.
- API và service cùng kiểm permission. Thiếu/sai session 401, thiếu endpoint permission 403. Record ngoài phạm vi hoặc không tồn tại cùng 404 QUESTION_AUTHORING_NOT_FOUND.
- List/count cùng predicate tại DB. Question management scope còn kiểm group liên quan thuộc phạm vi quản lý, để không lộ passage của group khác.
- Read management/history dùng REPEATABLE_READ. DTO có thể chứa đáp án/lời giải cho actor có quyền; Q-02 vẫn dùng DTO riêng và không trả chúng.

## Vòng đời và version

- POST tạo parent và v1 DRAFT, published pointer null. Question không có cột visibility; quyền đọc Q-02 dựa vào publication và permission hiện có.
- PATCH chỉ sửa DRAFT. Parent PUBLISHED: cập nhật đúng một working draft nếu có; nếu chưa có, clone từ **đúng published pointer**, cấp version number max+1 và choice IDs mới. Không latest/fallback.
- Giữ nguyên published text, choices, assets, timestamps và pointer. Draft không xuất hiện qua Q-02.
- Metadata identity partId/topicId/questionTypeId và thay group identity chỉ sửa khi parent DRAFT có đúng một version. Parent published nhận yêu cầu đổi metadata trả 409 QUESTION_METADATA_IMMUTABLE cho toàn bộ request. groupType tương tự; difficulty/explanation thuộc question version nên sửa qua draft.
- groupVersionId là pin tường minh. Có thể đổi pin trong cùng group trên working draft; đổi group của published question trả 409. Teacher phải quản lý group đó; any có phạm vi tương ứng.
- Sửa group không tự repin question. Group version mới published trong fixture làm Q-02 ẩn question còn pin bản cũ. PATCH pin tường minh sang bản hợp lệ chỉ tạo/sửa working draft, giữ pin published cũ. Có thể sửa pin kể cả group version lịch sử đã archived; không reactivate/sửa bản lịch sử.
- DELETE archive **parent**, không hard delete. Giữ versions, choices, links, pointers và FK tham chiếu; DELETE lặp thuộc scope trả 204. Archived chỉ đọc management/history, không sửa/tạo working draft.
- Archive group không cascade archive question, nhưng predicate Q-02 xét status group nên list/detail/count ẩn ngay. Question liên quan vẫn đọc quản lý được; update bị chặn khi group archived.
- Pointer lỗi, trạng thái version không hợp lệ hoặc nhiều working draft: 409 QUESTION_STATE_CONFLICT, không đoán bản đúng.

## API runtime

Tất cả path dưới /api/v1, dùng Authorization: Bearer <accessToken>.

| Method | Path | Kết quả |
|---|---|---|
| POST | /teacher/questions | 201 QuestionDetail |
| GET | /teacher/questions | 200 Page<QuestionSummary> |
| GET | /teacher/questions/{id} | 200 QuestionDetail gồm versions tăng dần |
| GET | /teacher/questions/{id}/history | 200 danh sách version |
| PATCH | /teacher/questions/{id} | 200 QuestionDetail |
| DELETE | /teacher/questions/{id} | 204 archive idempotent |
| POST | /teacher/question-groups | 201 GroupDetail |
| GET | /teacher/question-groups | 200 Page<GroupSummary> |
| GET | /teacher/question-groups/{id} | 200 GroupDetail |
| GET | /teacher/question-groups/{id}/history | 200 danh sách group version |
| PATCH | /teacher/question-groups/{id} | 200 GroupDetail |
| DELETE | /teacher/question-groups/{id} | 204 archive idempotent |

List: page=0, size=20, tối đa 100; sort createdAt/updatedAt/status/questionId hoặc groupId với asc/desc. Mặc định updatedAt,desc, khóa phụ ID tăng dần. Question filter partId/topicId/status; group filter groupType/status. Unknown/duplicate query, input sai và offset quá lớn trả 400.

Detail/history và các thao tác ghi không nhận query param. DELETE không nhận body. DTO strict allowlist, kiểm kiểu JSON và duplicate field cấp request; không ép chuỗi thành số/boolean. Lỗi dùng envelope code/message/traceId/details hiện có.

### Tạo group/passage

```json
{
  "groupType": "PASSAGE",
  "instruction": "Read the passage and choose an answer.",
  "passageText": "A notice from the office...",
  "assets": [
    {"mediaId": "<owned-or-granted-audio-uuid>", "transcript": "Version-pinned transcript"}
  ]
}
```

groupType nullable, chuỗi không blank nếu có, tối đa 30; instruction/passageText nullable. Có thể tạo draft chỉ có text, audio hoặc cả hai. Chưa có publish validation bắt buộc media theo Part.

### Tạo question

```json
{
  "partId": 123,
  "topicId": null,
  "groupVersionId": "<explicit-group-version-uuid-for-parts-3-4-6-7>",
  "questionText": "What is the notice about?",
  "explanation": "The first sentence states...",
  "difficulty": "MEDIUM",
  "choices": [
    {"label": "A", "content": "Option A", "isCorrect": true, "displayOrder": 1},
    {"label": "B", "content": "Option B", "isCorrect": false, "displayOrder": 2},
    {"label": "C", "content": "Option C", "isCorrect": false, "displayOrder": 3},
    {"label": "D", "content": "Option D", "isCorrect": false, "displayOrder": 4}
  ],
  "assets": [{"mediaId": "<owned-or-granted-image-uuid>"}]
}
```

partId là PK thực tế, không phải partNumber; lookup phải có partNumber 1–7. questionTypeId tùy chọn, mặc định loại SINGLE_CHOICE đã seed; ID khác code này bị từ chối. topicId phải tồn tại hoặc null. Part 2 đúng A–C/order 1–3; Part khác A–D/order 1–4; đúng một isCorrect=true. Không chấp nhận labels/order trùng, text/choice content blank hoặc choices thiếu/thừa. Part 3/4/6/7 bắt buộc group; 1/2/5 độc lập. difficulty EASY/MEDIUM/HARD hoặc null.

Question text không blank; explanation nullable. questionText và choices bắt buộc khi tạo. Part/topic dữ liệu tham chiếu dùng bảng hiện có; fixture Part 1–7 chỉ phục vụ test, không coi là ngân hàng đề thật.

### PATCH từng phần

```json
{"questionText":"Edited draft","explanation":null,"difficulty":"HARD"}
```

```json
{"groupVersionId":"<explicit-valid-version-in-same-group>"}
```

```json
{"assets":[{"mediaId":"<audio-uuid>","transcript":"New draft transcript"}]}
```

Omitted giữ nguyên. explanation/difficulty/topicId/instruction/passageText null xóa theo schema và giới hạn identity. questionText/partId/questionTypeId/choices null không hợp lệ. assets omitted giữ links; assets=[] bỏ toàn bộ links draft; assets=null trả 400. Khi gửi assets, mỗi object chỉ có mediaId và transcript tùy chọn; bỏ transcript trong object thay thế nghĩa là null. Transcript chỉ cho AUDIO, nullable; không nhận URL/path. Choices nếu có sẽ thay trọn bộ draft sau khi validate toàn bộ, không sửa published choices.

## Media và transcript

- Dùng media_resources.owner_id và media_reuse_grants của CONTENT-03; không tạo media entity/bảng grant mới.
- Điều kiện reuse: tồn tại, AUDIO/IMAGE, không PRIVATE, actor là owner hoặc có grant chưa revoked. AUTHENTICATED còn cần question:read. question:manage:any không bỏ qua các điều kiện này.
- ID mới không tồn tại/không được dùng: chung 404 MEDIA_NOT_FOUND, không trả ID/metadata. Retained/clone media thiếu quyền: 409 QUESTION_MEDIA_REUSE_CONFLICT, rollback toàn bộ.
- Mỗi lần sửa kiểm lại toàn bộ assets còn giữ, kể cả assets passage của group version được pin. Có thể bỏ link bị revoke khỏi draft. Pin một passage không được dùng để vượt quyền reuse media của passage.
- Clone kiểm tất cả links nguồn, kể cả khi payload assets=[] hoặc đổi pin; không clone thiếu media/âm thầm bỏ link.
- Transcript nằm trên link question/group version–media, không ghi media_resources.transcript, không fallback/backfill transcript legacy.
- Management chỉ trả media metadata/transcript link còn visible (PUBLIC hoặc AUTHENTICATED + question:read), không storage URL/path/owner/grant. Grant là quyền reuse, không phải quyền xem: revoke không tự gỡ links published.
- Q-02 tiếp tục lọc resource metadata theo visibility/link version, luôn loại answer/explanation/transcript khỏi toàn bộ JSON.

## Transaction và thứ tự khóa

Write READ_COMMITTED, nguyên tử cho parent/version/choices/links. Thứ tự:
1. Group parents liên quan theo UUID string tăng dần, rồi group versions theo version_number.
2. Question parent, rồi question versions theo version_number.
3. Tất cả media trong hợp desired/source theo UUID string tăng dần; sau đó grant rows cùng thứ tự.
4. Giữ khóa tới commit; media/grant FOR SHARE tương thích với row UPDATE của revoke/visibility.

Question update đọc group cũ trước để biết tập khóa; sau khi khóa question phải kiểm lại group identity, thay đổi cạnh tranh trả 409. Không lấy thêm group lock sau khi đã giữ question/media lock. Group archive chỉ khóa parent và không cascade. External writer thay media/grant phải dùng cùng thứ tự media→grant; không khóa parent sau media.

Parent FOR UPDATE tuần tự hóa create working draft/update/archive. Unique version number/FK hiện có là chốt cuối; dữ liệu legacy nhiều DRAFT bị từ chối ghi thay vì migration tự sửa. JDBC chỉ ghi version DRAFT được server chọn; EntityManager.clear trước khi dùng mapping/repository/mapper Q-01 đọc DTO sau ghi, tránh collection cache cũ.

## Schema và migration

Đã fetch origin trước triển khai và kiểm tra tiếp khi hoàn tất: origin/develop chỉ đến V20, V21 chưa trùng.
V21__question_authoring_and_version_transcripts.sql:
- question_groups.created_by nullable FK users + index creator.
- question_resources.transcript và question_group_version_resources.transcript nullable.
- Seed SINGLE_CHOICE bằng code ổn định, hai permission và role_permissions nêu trên.
- Không backfill owner/grant/transcript/publication, không thay published links/pointers, không sửa V1–V20.
- V21 đã áp dụng trên DB kiểm thử; giữ nguyên file/checksum sau khi áp dụng.

## Trách nhiệm thành phần

| Thành phần | Trách nhiệm |
|---|---|
| TeacherQuestionController / QuestionAuthoringRequest | Route question/group, principal, DTO allowlist/kiểu JSON/query/body và HTTP status |
| QuestionAuthoringService | Scope actor, lifecycle, pin version, type/Part/choice rules, transaction/locks, archive và DTO/histories |
| QuestionAuthoringAccess | Kiểm principal actor và permission hiện tại; không trust role/flags từ payload |
| QuestionMediaReuseService | Owner/grant/visibility/type/transcript, validate source/final assets, khóa media/grant theo thứ tự |
| QuestionAuthoringRepository | SQL own/any data/count, locks, DRAFT writes, choices/link replacement, media metadata không URL |
| Q-01 repositories / SnapshotMapper / PromptMapper | Tái sử dụng mapping và đọc version chính xác; management có answers, Q-02 DTO an toàn tách riêng |
| QuestionChoiceDraft / QuestionVersion.addChoices | Structural batch validation bất biến, không mutation một phần |
| IdentitySecurityConfig | Permission endpoint self/any; service vẫn kiểm độc lập |

Không có workflow publish/restore, không tạo endpoint giả để chứng minh quyền.

## Cách chạy thử

Trong backend, dùng DB kiểm thử riêng đã xác minh. Server HTTP và cluster kiểm thử đã dừng sau bàn giao, dữ liệu/schema giữ nguyên. Khởi động lại cluster đã có khi cần (không initdb/reset):
```powershell
& 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' -D "$PWD/target/q03-main-postgres" -l "$PWD/target/q03-main-postgres.log" -o '-h 127.0.0.1 -p 55459 -c shared_buffers=32MB' start
```
Cấu hình datasource/JVM:
```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_q03_main_test'
$env:SPRING_DATASOURCE_USERNAME='q03_main_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:DB_PASSWORD=''
$env:DEBUG='false'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='6'
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE='1'
$env:MAVEN_OPTS='-Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
.\mvnw.cmd test '-DargLine=-javaagent:target/q03-main-tools/mockito-agent.jar -Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
```

Mockito agent 5.23.0 đã có trong Maven cache được copy vào target/q03-main-tools; target không commit. Chuẩn bị sau khi Maven đã resolve dependency:
```powershell
New-Item -ItemType Directory -Path target/q03-main-tools -Force
Copy-Item -LiteralPath "$env:USERPROFILE/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" -Destination target/q03-main-tools/mockito-agent.jar
``` MockMaker sửa tối thiểu sang inline để mock SecureRandom/final SessionPrincipal; không còn cần bootstrap resource override cũ. Không xóa/skip test. Password rỗng/trust chỉ dùng cluster test bind localhost; môi trường cá nhân dùng datasource/DB_PASSWORD riêng, không reset database.

Chạy server kiểm thử:
```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_q03_http_test'
$env:SERVER_PORT='18080'
.\mvnw.cmd spring-boot:run
```

Đã thử HTTP thật tại http://127.0.0.1:18080 với DB toeic_q03_http_test riêng; fixture ACTIVE Teacher/Other/Admin/Learner và taxonomy/media trong q03-authoring.sql, mật khẩu ngẫu nhiên chỉ trong bộ nhớ. Lấy accessToken từ login, gửi Bearer vào các API ở bảng trên; không in/lưu password/token trong report. Fixture cleanup chỉ xóa UUID của test, không đụng DB cá nhân. Không dùng register/OTP mock để khẳng định CRUD thật.

Ví dụ HTTP khi đã có session:
```http
POST /api/v1/teacher/question-groups
Authorization: Bearer <accessToken>
Content-Type: application/json

{"groupType":"PASSAGE","passageText":"A draft passage"}

GET /api/v1/teacher/questions?page=0&size=20&sort=updatedAt,desc
Authorization: Bearer <accessToken>

PATCH /api/v1/teacher/questions/<questionId>
Authorization: Bearer <accessToken>
Content-Type: application/json

{"questionText":"Updated draft","explanation":null}

DELETE /api/v1/teacher/questions/<questionId>
Authorization: Bearer <accessToken>
```

## Kiểm thử thực tế

- Phần nền đã tích hợp: 21 test domain/mapper/repository.
- HTTP/lifecycle/concurrency trên code cuối: **97 test đạt** (64 HTTP, 20 lifecycle, 13 concurrency). Cộng 21 test nền thành **118 test Q-03**.
- HTTP qua socket thật: **25 bước đạt**, gồm login, 401/403/404, tạo group/question media/transcript, list/detail/history, PATCH và 400 rollback, archive lặp/409 và logout.
- Toàn bộ backend suite trước bàn giao Git trên working tree: **703 test đạt, 0 failure/error/skipped, BUILD SUCCESS**, 06:24 phút. Tổng gồm 575 test nền + 10 AuthServiceTest có sẵn ngoài phạm vi commit + 118 Q-03. Đây chưa phải số test của cây Git đã commit: AuthServiceTest.java được giữ nguyên ngoài commit; bước tích hợp develop phải kiểm thử lại từ nguồn đã commit. Ca pin tường minh từ group version lịch sử archived đã đạt. Kết quả được đối chiếu Surefire XML, không xóa/skip test để đạt số lượng.
- Bàn giao Git ngày 2026-11-09: đã commit Q-03 `5d7c298660be2c25c67f8f0fa2a00779e23e61dc` và tích hợp origin/develop `2479b1c` bằng merge `ba63b2228ad7408c68dba285de603285596b3897`, không conflict/stash/reset. Maven Wrapper `clean test` trên nguồn backend/ml xuất trực tiếp từ commit merge vào thư mục build ignored `backend/target/q03-committed-ba63b2228ad7`: **693 test đạt, 0 failure/error/skipped, BUILD SUCCESS**, 07:07 phút. Gồm 575 test nền đã commit + 118 Q-03; không có source/class AuthServiceTest chưa commit hoặc frontend/web đang sửa. Dùng đúng datasource/JVM ở trên, riêng javaagent dùng đường dẫn tuyệt đối tới `backend/target/q03-main-tools/mockito-agent.jar` để không bị Maven clean xóa. Backend tree đã kiểm thử: `dc8ba8363d595ce0e2c881cabd780c6a6b6b8ed8`. Không tạo repository/nhánh/worktree mới; bản xuất chỉ là đầu vào build từ Git. Báo cáo Surefire XML được cộng kiểm tra độc lập; log ignored `backend/target/q03-handoff-committed-tests.log`.
- Q-02/auth/content/vocabulary được kiểm chứng trong full suite; không dùng số test cũ làm bằng chứng cho endpoint mới.
- Concurrency gồm hai question/group editors chỉ có một working draft, update/archive, attach/clone cả hai thứ tự với revoke/visibility, khóa nhiều media đảo thứ tự payload, và rollback outer transaction.
- Fixture tái lập, SQL publish chỉ nằm trong test để kiểm snapshot; không phải publish endpoint hoặc ngân hàng đề thật Part 1–7.

Log local không commit: q03-crud-complete-tests.log (trước ca pin bổ sung), q03-final-verified-tests.log (703 test cuối), q03-http-smoke-versioned.log (25 bước thật); tất cả trong backend/target. Script smoke và helper hash được version hóa tại backend/scripts/q03-http-smoke.ps1 và Q03FixtureHash.java; helper đọc mật khẩu ngẫu nhiên qua stdin, không log giá trị. Chạy từ backend: .\scripts\q03-http-smoke.ps1 khi server 18080 dùng toeic_q03_http_test đã migrate. Script chỉ chấp nhận loopback và DB test đúng tên/cổng/user; fixture được dọn ở finally.

## Giới hạn

Chỉ SINGLE_CHOICE; không taxonomy authoring API, không ngân hàng đề sản phẩm seed sẵn. Không upload/download/signed media URL, grant admin API/ownership transfer, publish/unpublish/review/restore, import, practice, grading hoặc exam. PRAC-01/02 và EXAM-01 chưa triển khai. Không có DB trigger immutable tổng quát cho writer SQL ngoài ứng dụng; writer tích hợp sau phải tuân thủ lock/lifecycle contract.

## Bàn giao Git

Fetch khi bàn giao xác nhận origin/develop tại 2479b1c có sáu commit mới về vocabulary/ML, không sửa questionbank/security/test/migration; chỉ thêm cấu hình vocabulary.ml.model-path ngoài module vocabulary/ml. Đã tích hợp toàn bộ bằng merge trên nhánh feature, không conflict/stash/reset. V21 chưa trùng, V1–V20 và checksum V21 giữ nguyên. Trước commit Q-03, nhánh không có commit riêng ngoài develop; sau tích hợp, diff PR so với develop vẫn đúng 37 file Q-03, không có thay đổi vocabulary/ML riêng. Commit chức năng, merge develop và commit tài liệu ghi bằng chứng là phạm vi lịch sử bàn giao; không rewrite commit cũ. Nguồn đã commit được kiểm thử độc lập như trên, không lấy 10 test AuthServiceTest ngoài commit làm bằng chứng. Author/committer pthanh137 <sleapf137@gmail.com>. Hash cả 37 file web-auth/test/tatus ngoài phạm vi không đổi sau merge. tatus giữ SHA-256 C5DC158F6231925CDEABB6CDF81705CF9B06AC664E092F470DDF26CED1643B93. Không merge PR vào develop/master; trạng thái push/CI của SHA cuối được báo riêng khi bàn giao.

Q-03 gồm module questionbank, V21, fixtures/tests Q-03, tài liệu này và các hunk tích hợp security/RBAC bên dưới. Phần web-auth có sẵn (frontend/web/**, .gitignore, docs/WEB_Auth_Integration.md, AuthServiceTest.java) không thuộc commit Q-03.

File dùng chung cần review/stage từng hunk khi được phép:
- IdentitySecurityConfig.java: chỉ matcher question/group authoring self/any.
- AuthFlowIntegrationTest.java: chỉ expected permissions do V21.
- Mockito MockMaker resource: thay subclass→inline là thay đổi cấu hình test tối thiểu được người dùng cho phép; file đã có untracked trước Q-03, Git HEAD chưa có, nên commit Q-03 chỉ nhận resource inline này, không kéo AuthServiceTest.java.
- API Specification, ERD và LLD: chỉ phần contract/schema Q-03. Hiện các file này không có hunk web-auth khác; vẫn review diff trước stage.
Không có hunk FE web-auth trong các file production Q-03. Không dùng git add .; danh sách chính xác gồm 37 file Q-03 trong manifest phía dưới. Các file web-auth/test được bảo toàn ngoài danh sách này.


### Manifest Q-03

- `backend/scripts/Q03FixtureHash.java`
- `backend/scripts/q03-http-smoke.ps1`
- `backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java`
- `backend/src/main/java/com/toeic/backend/questionbank/api/QuestionAuthoringExceptionHandler.java`
- `backend/src/main/java/com/toeic/backend/questionbank/api/QuestionAuthoringRequest.java`
- `backend/src/main/java/com/toeic/backend/questionbank/api/TeacherQuestionController.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringAccess.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringDtos.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringEdit.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringException.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringQuery.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringService.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionAuthoringSnapshotMapper.java`
- `backend/src/main/java/com/toeic/backend/questionbank/application/QuestionMediaReuseService.java`
- `backend/src/main/java/com/toeic/backend/questionbank/domain/QuestionChoiceDraft.java`
- `backend/src/main/java/com/toeic/backend/questionbank/domain/QuestionVersion.java`
- `backend/src/main/java/com/toeic/backend/questionbank/infrastructure/QuestionAuthoringRepository.java`
- `backend/src/main/java/com/toeic/backend/questionbank/infrastructure/QuestionGroupRepository.java`
- `backend/src/main/java/com/toeic/backend/questionbank/infrastructure/QuestionRepository.java`
- `backend/src/main/java/com/toeic/backend/questionbank/infrastructure/QuestionVersionRepository.java`
- `backend/src/main/resources/db/migration/V21__question_authoring_and_version_transcripts.sql`
- `backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/api/QuestionAuthoringConcurrencyTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/api/QuestionAuthoringLifecycleTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/api/QuestionAuthoringTestSupport.java`
- `backend/src/test/java/com/toeic/backend/questionbank/api/TeacherQuestionIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/application/QuestionAuthoringSnapshotMapperTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/domain/QuestionChoiceBatchTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/infrastructure/QuestionAuthoringFoundationPersistenceTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/infrastructure/QuestionAuthoringRepositoryTest.java`
- `backend/src/test/resources/fixtures/q03-authoring.sql`
- `backend/src/test/resources/fixtures/q03-authoring-cleanup.sql`
- `backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `docs/API_Specification.md`
- `docs/ERD_v1.6.md`
- `docs/LLD.md`
- `docs/Q03_Question_Authoring.md`
