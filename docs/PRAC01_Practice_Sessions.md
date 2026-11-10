# PRAC-01 — Luyện tập Part 1–7 và kết hợp nhiều Part

## Nguồn yêu cầu và quyết định sản phẩm

Backlog do người dùng giao TV2: luyện tập Part 1–7 và kết hợp nhiều Part. Không tìm thấy backlog/AC PRAC-01 chi tiết trong repo. Đã đối chiếu SRS UC-04, BR-07/AC-07; HLD phần B/§7.1; API Specification §3.4/4.3; ERD v1.6; LLD §2.4 và schema V5/V10/V16/V21. Q-03 đã merge qua PR #43 tại d49c7bf. Nhánh hiện tại feat/web-auth-integration được fast-forward trong lượt nền trước, không tạo nhánh/worktree/repository mới hoặc stash/reset/commit.

**Các chính sách dưới đây là quyết định sản phẩm vừa được người dùng chốt ngày 2026-11-10, không phải Acceptance Criteria tìm thấy trong backlog.** Chúng thay các đề xuất OPEN trước đó. Đề xuất cũ từ chối snapshot khi nguồn archive không được áp dụng.

- Tổng integer 1–200 câu. Chọn một/nhiều Part 1–7, không trùng, mỗi Part phải có ít nhất một câu. Optional topicId/difficulty theo schema; chưa filter tags.
- Chỉ current published question/version, SINGLE_CHOICE hợp lệ: Part 2 có A–C, Part khác A–D, đúng một đáp án, label/order/content hợp lệ. Part 3/4/6/7 bắt buộc group/version published đúng identity/pointer/timestamps, chưa archived; Part 1/2/5 độc lập.
- Chọn nguyên nhóm của đúng group version. Pool nội bộ giữ cả thành viên invalid để loại cả unit khi có câu sai publication/type/choices/filter hoặc khác Part. Thành viên published mất pointer/group pin cũng làm nhóm không khả dụng. Lịch sử publication chỉ phát hiện membership hỏng, không dùng làm fallback chọn version. Câu DRAFT mới chưa từng published không làm thay đổi nhóm published. Không trộn group version.
- Đúng tổng yêu cầu hoặc 409 PRACTICE_QUESTIONS_INSUFFICIENT, không tạo attempt dở dang. Không chọn ngẫu nhiên tham lam rồi báo thiếu khi có tổ hợp.
- Part tăng dần; ngẫu nhiên unit câu/group trong mỗi Part, không đảo choices hay câu trong group. Review bàn giao xác nhận created_at/UUID không bảo đảm thứ tự nghiệp vụ: V23 thêm question_versions.group_question_order nullable, integer dương, thuộc đúng question/group version. Chỉ chọn group có vị trí đầy đủ và không trùng trong tập current members; sắp theo vị trí này, lưu question_order snapshot. Không suy/backfill vị trí cho dữ liệu cũ. Group thiếu/trùng vị trí bị loại nguyên unit, không fallback thời gian/UUID. Working draft/history có thể cùng vị trí vì không là current member; chưa thêm API authoring vị trí.
- Permission practice:manage:self riêng, mặc định chỉ LEARNER. Teacher/Admin dùng được khi có grant hợp lệ; kiểm permission, không hard-code role. Không cấp question:read cho Learner.
- Actor chỉ tạo/đọc attempt của mình từ principal. 401 chưa/sai xác thực, 403 thiếu permission, 404 không tồn tại/ngoài ownership.
- Media tái sử dụng chính sách đọc hiện có: PUBLIC/AUTHENTICATED, không owner/reuse grant. PRIVATE bị ẩn cả metadata/ID. Không storage URL/path, answer key, explanation hoặc transcript trong DTO.
- Republish/archive nguồn vẫn đọc đúng version/order đã pin, không repin/thay câu/xóa attempt/fallback current. Media visibility kiểm lại ở mỗi lần đọc. Pin/version hỏng trả 409 PRACTICE_SNAPSHOT_UNAVAILABLE.
- expiresAt = startedAt + 24 giờ server, timestamp chuẩn hóa microseconds PostgreSQL. Hết hạn vẫn đọc, expired=true, không tự xóa. Status persisted IN_PROGRESS chưa chuyển bằng scheduler. PRAC-02 phải kiểm now < expiresAt và status trước ghi đáp án/submit; chưa triển khai các ghi đó.

## API runtime

| Method/path | Response | Bảo vệ |
|---|---|---|
| POST /api/v1/attempts | 201 PracticeSession | Bearer + practice:manage:self |
| GET /api/v1/attempts/{id} | 200 PracticeSession, gồm expired | Cùng permission, DB ownership/source |

POST chỉ hỗ trợ PRACTICE_SNAPSHOT. TEST_VERSION, GET list, answers/submit/result còn DESIGN/nhiệm vụ sau; không tạo endpoint giả.

~~~json
{
  "sourceType": "PRACTICE_SNAPSHOT",
  "practice": {
    "partNumbers": [1, 3],
    "questionCount": 7,
    "topicId": 960001,
    "difficulty": "EASY"
  }
}
~~~

partNumbers là số Part 1–7, không phải PK part_id. partIds trong DESIGN cũ không là alias runtime, bị từ chối để tránh nhầm. topicId ví dụ là fixture, phải dùng khóa topics thực tế. difficulty EASY/MEDIUM/HARD hoặc null; topicId integer dương hoặc null. Omitted/null filter nghĩa là không lọc; filter ID không có dữ liệu trả 409 thiếu bộ câu, không tự bỏ filter.

Allowlist root chỉ sourceType/practice; filter chỉ partNumbers/questionCount/topicId/difficulty. Owner/userId/status/system fields, trường lạ, duplicate JSON property, sai kiểu, float thay integer, duplicate Part, count ngoài 1–200, UUID sai hoặc query params trái contract trả 400 VALIDATION_ERROR. Hai route không nhận query params.

~~~json
{
  "attemptId": "<uuid>",
  "sourceType": "PRACTICE_SNAPSHOT",
  "status": "IN_PROGRESS",
  "startedAt": "2026-11-10T12:00:00Z",
  "expiresAt": "2026-11-11T12:00:00Z",
  "expired": false,
  "totalQuestions": 7,
  "questions": [{
    "order": 1,
    "questionId": "<uuid>",
    "questionVersionId": "<pinned-uuid>",
    "versionNumber": 1,
    "partNumber": 1,
    "topic": "Fixture topic",
    "questionType": "SINGLE_CHOICE",
    "difficulty": "EASY",
    "questionText": "Prompt",
    "choices": [{"choiceId":"<uuid>","label":"A","content":"Choice A","displayOrder":1}],
    "assets": [],
    "passage": null
  }]
}
~~~

Response trên chỉ minh họa một phần mảng; runtime luôn đủ totalQuestions và 3/4 choices. Passage có groupVersionId/versionNumber/instruction/passageText/assets theo pin. Asset chỉ resourceId/resourceType/mimeType/fileSize/durationSeconds/displayOrder; không URL/path/transcript.

| HTTP/code | Ý nghĩa |
|---|---|
| 401 UNAUTHENTICATED | Thiếu/sai/hết hạn Bearer hoặc session không hợp lệ |
| 403 FORBIDDEN | Thiếu permission; service kiểm actor tin cậy |
| 404 ATTEMPT_NOT_FOUND | Không tồn tại, khác actor hoặc khác source |
| 400 VALIDATION_ERROR | Input trái contract |
| 409 PRACTICE_QUESTIONS_INSUFFICIENT | Không có tổ hợp đủ tổng và mọi Part |
| 409 PRACTICE_SOURCE_CHANGED | Nguồn đổi giữa chọn/khóa; rollback và thử lại |
| 409 PRACTICE_SNAPSHOT_UNAVAILABLE | Pin/version hỏng, không fallback |

Archive không phải lỗi đọc snapshot. Expiry attempt độc lập expiry Bearer; có thể đăng nhập lại để đọc attempt đã hết hạn. PRAC-01 không đăng ký route ghi đáp án, submit hay grading.

## Lựa chọn, snapshot và transaction

SQL PracticePublicationRepository áp dụng publication/type/choice/filter tại DB và giữ đầy đủ thành viên group để kiểm cả unit. Chỉ ID/metadata/eligible nội bộ, không đưa pool/draft/answer key ra API. Response totalQuestions là số snapshot đã chọn, không có endpoint count/list ngân hàng cho Learner.

PracticeUnitSelector dùng bounded subset sum trong mỗi Part, rồi DP phân bổ tổng giữa các Part với lượng mỗi Part >=1. Đường truy vết immutable, không lặp question, không cắt nhóm. Random unit/lượng rồi shuffle các unit được chọn trong từng Part; không cam kết lấy mẫu đồng đều mọi tổ hợp. Độ phức tạp O(số unit * 200 + số Part * 200²), không backtracking không giới hạn.

Create READ_COMMITTED, chọn sơ bộ rồi khóa FOR SHARE tới commit, tương thích Q-03:
1. Group parents theo UUID text tăng dần.
2. Group versions theo group ID/version number.
3. Question parents theo UUID text, rồi question versions theo question ID/version number.
4. Mọi media link của các pin theo UUID text; không khóa reuse grant vì đây là quyền xem.

Sau khóa, query lại publication/filter và toàn bộ membership từng unit; thay đổi trả PRACTICE_SOURCE_CHANGED, không ghép lại với tập khóa cũ. Q-03/publish/revoke/visibility writer phải tuân parent/version trước media và thứ tự ID ổn định. Không có trigger immutable tổng quát bảo vệ SQL writer ngoài ứng dụng.

Ghi attempts + attempt_question_snapshots cùng transaction; flush xong mà dựng DTO thất bại vẫn rollback cả parent/snapshots. Không ghi attempt_answers hoặc points chấm điểm. Tái sử dụng V5/V10 composite FK và unique order/version trong attempt. Snapshot tham chiếu version bất biến theo contract Q-03, không copy toàn bộ JSON.

GET REPEATABLE_READ; repository lọc attempt ID/user ID/source type tại DB. Dùng Q-01 exact-version repository/mapper, không check source current pointer/status để đọc pin cũ. Group pin phải khớp version. Media từ đúng link version và visibility tại lần đọc. Không gọi Teacher QuestionReadService hay cấp question:read.

## Trách nhiệm từng thành phần

| Thành phần | Trách nhiệm |
|---|---|
| PracticeAttemptController / StartPracticeRequest | Principal, strict JSON/query allowlist, POST 201 / GET 200 |
| PracticeAccess / IdentitySecurityConfig | Permission endpoint/service và actor khớp SessionPrincipal |
| PracticeSessionService | Transaction, chọn/pin nguồn, 24h/expired, persistence và session DTO |
| PracticeUnitSelector | Tổ hợp chính xác, đủ mọi Part, random unit, giữ thứ tự group |
| PracticeAttempt / PracticeQuestionSnapshot | Aggregate bất biến, pin/version/order, không answer mutation |
| PracticeAttemptRepository | Persist aggregate; ownership/source predicate |
| PracticeQuestionCatalog | Contract module QuestionBank: whole unit, khóa/revalidate và exact pin read |
| PracticePublicationRepository | SQL eligibility/filter/membership, source/media locks |
| PracticeQuestionViewMapper / QuestionPromptMapper | DTO practice allowlist, không đáp án/lời giải/transcript/URL |
| QuestionVersionRepository / QuestionAssetRepository | Reuse Q-01 version và metadata PUBLIC/AUTHENTICATED |
| PracticeExceptionHandler | Error envelope code/message/traceId/details, không SQL/metadata hidden |

## Migration và Git

Fetch trước bổ sung xác nhận origin/develop chỉ V1–V21; V22/V23 chưa bị dùng. V22__practice_self_permission.sql chỉ seed practice:manage:self cho LEARNER, giữ nguyên checksum đã áp dụng. V23__question_version_group_order.sql bổ sung vị trí nghiệp vụ nullable/positive; không backfill. Không global unique constraint vì history/working draft cùng question có thể giữ cùng vị trí; practice kiểm unique giữa current members. Không sửa V1–V22 hoặc published text/choices/assets. Dữ liệu group cũ NULL chưa đủ điều kiện practice cho tới khi có vị trí chính xác; API cấp/sửa vị trí authoring chưa triển khai.

Giữ nguyên 38 file web-auth/test/api-tests/tatus có sẵn. Người dùng đã cho phép stage/commit/push riêng PRAC-01 khi kiểm tra đạt; không merge PR. Danh tính Git pthanh137 <sleapf137@gmail.com>. File shared đã review hunk riêng: IdentitySecurityConfig, AuthFlowIntegrationTest (expected permission LEARNER), API Specification, ERD, LLD. AuthServiceTest.java, frontend/web/**, .gitignore, docs/WEB_Auth_Integration.md, api-tests/** và tatus không thuộc commit PRAC-01.

Trong lúc thực hiện xuất hiện thay đổi header AGENTS.md ngoài phạm vi; đã đọc lại và giữ nguyên, không đưa vào manifest PRAC-01. Kiểm tra diff/staged chỉ PRAC-01, gồm whitespace của file mới theo line ending Windows. Toàn repo có trailing whitespace AGENTS.md:1 ngoài phạm vi; không tự sửa thay đổi của người dùng.

## Kiểm thử và cách chạy

PostgreSQL 17 riêng: data_directory D:/toeic-learning-platform/backend/target/q03-main-postgres, 127.0.0.1:55459, user q03_main_test. DB toeic_prac01_verify cho JUnit, toeic_prac01_http_test cho HTTP. Đã xác minh database/user/port/data directory; không reset DB cá nhân. Fixture q03-authoring.sql + prac01.sql có Part 1–7, groups/choices/assets và tài khoản ACTIVE riêng; không phải ngân hàng đề thật. Cleanup chỉ các UUID fixture, giữ DB/schema.

- Lượt nền trước quyết định: 19 test mới, full suite 722 đạt, gồm 10 AuthServiceTest có sẵn. Không coi đây là bằng chứng API.
- Sau chốt: 59 HTTP integration, 6 selector (brute-force feasibility, không greedy, max 200), 6 concurrency dùng PostgreSQL lock wait thực và 1 HTTP failure sau flush xác minh rollback. Spy setup qua security proxy đã sửa tại test; không bỏ guard production. Bảy case group gồm mất pointer/pin đã chạy đạt.
- HTTP socket thật: 30 bước đạt trên 127.0.0.1:18081; log ignored target/prac01-http-final-smoke.log. Login/session thật, không mock response, không in password/token. Kiểm nhiều Part, exact group, 401/403/404, validation, snapshot sau pointer/archive, media, expired và logout. Sau smoke fixture users/attempts đều 0, Flyway V22 success.
- Full suite trước hardening: 792 test, 102 errors vì Mockito không tạo được temp JAR khi ổ C hết chỗ. Chuyển java.io.tmpdir của riêng JVM Maven/test sang D: và dùng cache offline; không xóa dữ liệu cá nhân hay skip test.
- Lượt 794 test tiếp theo còn 2 assertion AuthFlow kỳ vọng tập quyền LEARNER cũ. Đã cập nhật cả ba kỳ vọng theo V22 (seeded role, union role, chống tự nâng quyền), không đổi logic kiểm tra hoặc bỏ test.
- **Full suite trước review V23: 794 test đạt, 0 failures/errors/skipped, BUILD SUCCESS**, 07:35 phút, 2026-11-10. Gồm 693 test đã commit từ Q-03 + 10 AuthServiceTest có sẵn + 91 test PRAC-01. Log ignored: backend/target/prac01-handoff-full-tests.log. Đây là bằng chứng trước sửa thứ tự.
- Review bàn giao sửa tối thiểu thứ tự group bằng V23. **4 test trực tiếp đạt**, log target/prac01-order-targeted-tests.log: vị trí đảo thứ tự tạo/UUID, reload ổn định, NULL/trùng loại cả nhóm và working draft không ảnh hưởng. Không chạy lại HTTP smoke.
- **Full suite cuối từ nguồn staged: 787 test đạt, 0 failures/errors/skipped, BUILD SUCCESS**, 07:42 phút. Chỉ chạy full một lần sau sửa. Tổng 693 test nền đã commit + **94 test PRAC-01**, không gồm 10 AuthServiceTest ngoài commit; không xóa/skip test để khớp số. Nguồn export từ index tree a26be0ed7c4bca104605ee83e03b0241bb0e151d trong thư mục build ignored backend/target/prac01-staged-a26be0ed7c4b, chỉ backend/ml, không có web-auth hoặc AuthServiceTest chưa commit. Backend tree b203bbc6f880883d1d47255e4cface482989db09 đã kiểm thử, khớp staged backend; Mockito config có sẵn trong cây Git. Không tạo repository/branch/worktree mới. Log ignored backend/target/prac01-staged-full-tests.log; Surefire XML tại thư mục build đó. Chạy Maven Wrapper -o test với cùng datasource/JVM/temp D: bên dưới, resource classpath trỏ vào nguồn export, không nguồn ngoài commit. Auth/OTP/session/profile/RBAC, Q-02/Q-03/content/vocabulary đạt.

Từ backend, khởi động cluster test nếu đã dừng, không initdb/reset:
~~~powershell
& 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' -D "$PWD/target/q03-main-postgres" -l "$PWD/target/prac01-postgres.log" -o '-h 127.0.0.1 -p 55459 -c shared_buffers=32MB' start
~~~

Lệnh full suite đã dùng:
~~~powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_prac01_verify'
$env:SPRING_DATASOURCE_USERNAME='q03_main_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:DB_PASSWORD=''
$env:DEBUG='false'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='6'
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE='1'
New-Item -ItemType Directory -Path target/prac01-jvm-tmp -Force | Out-Null
$env:MAVEN_OPTS='-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac01-jvm-tmp -Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
.\mvnw.cmd -o test '-Dmaven.test.additionalClasspath=D:/toeic-learning-platform/backend/src/test/resources,D:/toeic-learning-platform/backend/src/main/resources' '-DargLine=-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac01-jvm-tmp -javaagent:D:/toeic-learning-platform/backend/target/q03-main-tools/mockito-agent.jar -Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
~~~

Mockito agent 5.23.0 copy từ Maven cache theo Q-03; không commit jar/log/build artifact. Offline chỉ dùng cache đã resolve, không skip test. Source resources thêm vào classpath để tránh target/test-classes bị IDE rebuild như lượt nền. Không Maven clean trong backend vì cluster test nằm trong target. Password rỗng/trust chỉ cho cluster test loopback đã kiểm tra.

Backend thật, đổi DB/port trong cùng cấu hình:
~~~powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_prac01_http_test'
$env:SERVER_PORT='18081'
$env:SERVER_ADDRESS='127.0.0.1'
.\mvnw.cmd -o spring-boot:run '-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac01-jvm-tmp -Xms64m -Xmx320m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
# Terminal khác, từ backend; server phải dùng đúng DB HTTP test đã migrate:
.\scripts\prac01-http-smoke.ps1
~~~

Script dùng psql 17, Q03FixtureHash.java và spring-security-crypto 7.1.1 từ cache (có tham số CryptoJar), kiểm DB/user/port trước fixture và dọn ở finally. Không cần SMTP vì fixture ACTIVE và login thật, không sửa OTP. Server/cluster test dừng sau bàn giao, DB giữ nguyên. Logs ignored: prac01-api-tests.log, prac01-complete-api-tests.log (spy setup), prac01-rollback-verified.log, prac01-group-integrity-tests.log, prac01-http-final-smoke.log, prac01-final-full-tests.log (temp C lỗi), prac01-final-verified-tests.log (hai kỳ vọng quyền cũ), prac01-handoff-full-tests.log (794 đạt).

## Giới hạn

Chưa protected media playback/download/signed URL; chỉ metadata được phép, chưa tự tạo URL công khai. Có cột vị trí version-specific V23, chưa có API authoring vị trí: group legacy NULL không được suy thứ tự hoặc dùng để practice. Chưa taxonomy authoring, upload, publish/unpublish/import, frontend, lưu đáp án/submit/grading/explanation PRAC-02 hoặc EXAM-01. Không scheduler chỉ cho expired, không xóa attempt và không seed ngân hàng đề thật Part 1–7.

## Manifest PRAC-01 — 35 file

30 file mới và 5 file shared; không gồm 38 file web-auth/test/api-tests/tatus và AGENTS.md ngoài phạm vi. Shared hunk gồm matcher attempts, expected permission LEARNER và API/ERD/LLD.

- backend/scripts/prac01-http-smoke.ps1
- backend/src/main/java/com/toeic/backend/assessment/api/PracticeAttemptController.java
- backend/src/main/java/com/toeic/backend/assessment/api/PracticeExceptionHandler.java
- backend/src/main/java/com/toeic/backend/assessment/api/StartPracticeRequest.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeAccess.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeException.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionDtos.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionService.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeStartQuery.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeUnitSelector.java
- backend/src/main/java/com/toeic/backend/assessment/domain/PracticeAttempt.java
- backend/src/main/java/com/toeic/backend/assessment/domain/PracticeQuestionSnapshot.java
- backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepository.java
- backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
- backend/src/main/java/com/toeic/backend/questionbank/application/PracticeQuestionCatalog.java
- backend/src/main/java/com/toeic/backend/questionbank/application/PracticeQuestionViewMapper.java
- backend/src/main/java/com/toeic/backend/questionbank/infrastructure/PracticePublicationRepository.java
- backend/src/main/resources/db/migration/V22__practice_self_permission.sql
- backend/src/main/resources/db/migration/V23__question_version_group_order.sql
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeAttemptIntegrationTest.java
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeConcurrencyTest.java
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeHttpRollbackTest.java
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeTestSupport.java
- backend/src/test/java/com/toeic/backend/assessment/application/PracticeUnitSelectorTest.java
- backend/src/test/java/com/toeic/backend/assessment/domain/PracticeAttemptTest.java
- backend/src/test/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepositoryTest.java
- backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
- backend/src/test/java/com/toeic/backend/questionbank/application/PracticeQuestionViewMapperTest.java
- backend/src/test/java/com/toeic/backend/questionbank/infrastructure/PracticePublicationRepositoryTest.java
- backend/src/test/resources/fixtures/prac01-cleanup.sql
- backend/src/test/resources/fixtures/prac01.sql
- docs/API_Specification.md
- docs/ERD_v1.6.md
- docs/LLD.md
- docs/PRAC01_Practice_Sessions.md
