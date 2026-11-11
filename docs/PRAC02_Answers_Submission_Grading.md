# PRAC-02 — Lưu đáp án, submit, grading và explanation

## Phạm vi và dependency

TV2 Sprint 2, chỉ PRACTICE_SNAPSHOT; không EXAM-01/frontend. Đối chiếu SRS UC-04/BR-09/BR-10, API §3.4/4.4/4.5, ERD v1.6, HLD Phần B §25/26, LLD §5.2/5.3 và PRAC01_Practice_Sessions.md với code/schema/test thực tế.

Fetch origin ngày 2026-11-10 xác nhận PR #44 đã merge tại `1e5c06f` trên origin/develop. HEAD local `3af9b9e` chứa đầy đủ dependency PRAC-01; `git diff HEAD origin/develop` không có khác biệt nội dung. Giữ nhánh `feat/web-auth-integration`, không tạo branch/worktree, stage/commit/push/merge.

Lượt triển khai đầu xác nhận local/origin/develop mới nhất V23, tạo migration PRAC-02 tên V24 và chạy trên DB test riêng. Khi bàn giao, fetch origin thấy develop đã tới `4b9cf79`, thêm 14 commit sau HEAD, có V24__google_oidc_identity.sql và V25__registration_without_otp_and_password_reset.sql. Migration PRAC-02 chưa commit/push được đổi tên thành **V26__practice_grading_snapshots.sql**, giữ nguyên từng byte SQL (SHA256 `1A25625661B06F301D19499C17FB934B6CC91BDC312772768BE81D63CC20ABB5`). Bản V24 thử nghiệm được lưu nguyên trong ignored target; không sửa history/checksum hoặc reset DB đã áp dụng nó. Các lượt 838 test/20 bước HTTP bên dưới dùng tên V24 cũ; V26 được kiểm tra riêng từ cây staged trên DB mới khi bàn giao.

Không tích hợp toàn bộ develop trong lượt này: có nhiều thay đổi auth/frontend trùng phần nền phải giữ nguyên, ngoài phạm vi PRAC-02. V26 không trùng số migration trên develop và độc lập với schema Google/registration V24/V25; không đưa các thay đổi đó vào commit. Tái sử dụng attempts, attempt_answers, attempt_part_results V5 và attempt_question_snapshots V10; V26 chỉ thêm snapshot key/explanation/Part để result/replay không đọc/chấm lại nguồn. Khi tích hợp develop về sau vẫn cần review/CI; không tự merge PR.

## API lưu đáp án đã triển khai

Mọi route dùng `/api/v1`, Bearer thật + `practice:manage:self`, actor từ SessionPrincipal. Predicate attempt ID/user ID/source type ở DB; 401 thiếu/sai xác thực, 403 thiếu permission, 404 attempt không tồn tại/ngoài ownership/khác source. Service còn kiểm actor khớp principal khi gọi trực tiếp.

`PUT /attempts/{id}/answers/{questionVersionId}` nhận chính xác:

```json
{"selectedChoiceId":"<choice-uuid-cua-version-da-pin>"}
```

Bỏ chọn bằng `{"selectedChoiceId":null}`. Không nhận omitted field, unknown/duplicate property, clientSequence, batch, isCorrect hoặc trường hệ thống. UUID choice cần dạng đầy đủ; sai kiểu/payload/path/query trả 400 VALIDATION_ERROR. Toàn bộ route không nhận query params.

200 SavedAnswer:

```json
{"questionVersionId":"<pinned-uuid>","selectedChoiceId":"<uuid-hoac-null>","updatedAt":"2026-11-10T12:00:00Z"}
```

GET attempt PRAC-01 bổ sung `answers: [SavedAnswer]`, sắp theo question_order snapshot. Trước submit gồm các câu đã lưu (kể cả bỏ chọn); sau submit đủ mọi câu vì grading tạo row cho câu chưa trả lời. Vẫn không chứa isCorrect, đáp án đúng, explanation, transcript hay URL/path, kể cả sau submit; key/explanation chỉ qua submit/result. Đọc lại được attempt hết hạn. Choices/group version/order vẫn theo pin sau republish/archive; quyền media xét lại như PRAC-01.

| HTTP/code | Điều kiện |
|---|---|
| 404 ATTEMPT_QUESTION_NOT_FOUND | questionVersionId không thuộc snapshot của attempt đang sở hữu |
| 422 CHOICE_VERSION_MISMATCH | Choice không tồn tại hoặc thuộc version khác, kể cả version mới của cùng câu |
| 409 ATTEMPT_NOT_EDITABLE | status không còn IN_PROGRESS; kể cả bỏ chọn |
| 409 ATTEMPT_EXPIRED | now >= expiresAt, lấy giờ server sau khi có khóa |

Khóa attempt parent bằng SELECT FOR UPDATE có ownership/source predicate, không outer-fetch-join khi khóa. Kiểm status/expiry sau khóa, kiểm membership snapshot, kiểm choice cùng question version trước UPSERT. Dùng unique `(attempt_id,question_version_id)` và composite FK choice hiện có; không tạo answer trùng. Xóa lựa chọn giữ row với selected_choice_id/answered_at NULL; is_correct NULL trước chấm. Timestamp chuẩn hóa microseconds PostgreSQL. Hai lần save chờ cùng khóa, lần ghi sau thắng theo thứ tự khóa/commit; HTTP chưa xác nhận không được coi là đã lưu.

## Quyết định sản phẩm vừa chốt 2026-11-11

Đây là quyết định người dùng chốt trực tiếp, không phải suy diễn từ backlog hoặc mặc định các điểm OPEN đã được duyệt:

- PUT từng câu chọn mới/thay lựa chọn, null bỏ chọn; chỉ question/choice đúng snapshot đã pin. Chưa batch/clientSequence.
- Đúng = 1; sai/chưa trả lời = 0. Trả totalQuestions, correctCount, incorrectCount, unansweredCount, rawScore và accuracy trên tổng câu. Chưa quy đổi thang TOEIC 10–990; không trả score-conversion fields.
- Submit đồng bộ, chấm và lưu tất cả trong cùng transaction. Replay theo attempt trả kết quả đã lưu, không chấm lại/tạo bản ghi trùng; không yêu cầu Idempotency-Key.
- IN_PROGRESS hết hạn không lưu/submit. GRADED được replay trước kiểm expiry, kể cả đã quá expiresAt. Sau submit không sửa đáp án, kể cả bỏ chọn null.
- Chỉ owner có permission được xem key/explanation sau submit; mọi câu (gồm bỏ trống) có correctChoiceId và explanation của pin, NULL nếu nguồn không có explanation. Không transcript hoặc URL/path media.
- Save và submit tuần tự hóa: save commit trước khi submit lấy khóa thì kết quả phải chứa lựa chọn đó; submit chốt trước thì save bị từ chối. Client phải chờ HTTP save thành công trước khi coi lựa chọn đã được lưu.

HLD Phần B §25 trước đây mô tả worker/outbox bất đồng bộ; API §4.5 minh họa SUBMITTED và Idempotency-Key với storage OPEN. Quyết định đồng bộ vừa chốt thay thiết kế đó **chỉ trong PRACTICE_SNAPSHOT PRAC-02**; đã ghi override cùng nguồn ở HLD/LLD/API/SRS. Không intermediate SUBMITTED persisted: IN_PROGRESS → GRADED khi cùng transaction commit. Expiry auto-submit/scheduler trong DESIGN cũng chưa áp dụng cho practice; giữ runtime PRAC-01 24h/chỉ đọc sau hạn. Test/EXAM, outbox/worker/notification và governance quy đổi điểm ngoài phạm vi.

## API submit và result

| Method/path | Request | Response |
|---|---|---|
| POST /api/v1/attempts/{id}/submit | Không body/query | 200 PracticeResult; retry cùng kết quả |
| GET /api/v1/attempts/{id}/result | Không query | 200 PracticeResult đã lưu; trước submit 409 RESULT_NOT_READY |

Cùng Bearer/permission/actor/ownership/source guard với save, kể cả lúc replay. Submit có body (gồm `{}`/`null`/answers giả) hoặc query trả 400. Request không có key vẫn idempotent; không có cơ chế replay HTTP response theo key độc lập với attempt. Submit/GET result không tồn tại/ngoài ownership/khác source trả 404 ATTEMPT_NOT_FOUND. Snapshot/key hỏng khi chấm trả 409 PRACTICE_SNAPSHOT_UNAVAILABLE và rollback toàn bộ, không fallback current. Lưu sau submit 409 ATTEMPT_NOT_EDITABLE; submit mới sau hạn 409 ATTEMPT_EXPIRED.

```json
{
  "attemptId":"<uuid>","status":"GRADED","submittedAt":"2026-11-11T01:45:00Z",
  "totalQuestions":3,"correctCount":1,"incorrectCount":1,"unansweredCount":1,
  "rawScore":1,"accuracy":33.33,"listeningCorrectCount":1,"readingCorrectCount":0,
  "questions":[{
    "order":1,"questionVersionId":"<pinned-uuid>","partNumber":2,
    "selectedChoiceId":"<uuid>","correctChoiceId":"<uuid>","isCorrect":true,
    "outcome":"CORRECT","explanation":"Giải thích của version đã pin"
  }]
}
```

Ví dụ rút gọn mảng; runtime đủ totalQuestions, theo order snapshot. incorrectCount không gồm unansweredCount; tổng ba nhóm bằng totalQuestions. rawScore = correctCount. accuracy = correctCount / totalQuestions × 100, HALF_UP 2 chữ số thập phân, 0–100; câu bỏ trống vẫn ở mẫu số. outcome CORRECT/INCORRECT/UNANSWERED; bỏ trống selectedChoiceId NULL, isCorrect false. listeningCorrectCount cho Part 1–4, readingCorrectCount cho Part 5–7. Không điểm ước tính/quy đổi TOEIC hoặc assets/passage/transcript/URL/path trong DTO result.

## Transaction và snapshot kết quả

PracticeSubmissionService khóa parent qua repository trước mọi đọc/ghi đáp án. Sau khóa: nếu GRADED thì dựng DTO từ persisted grading snapshots ngay; không kiểm hạn hoặc gọi QuestionBank. Nếu IN_PROGRESS thì kiểm now < expiresAt rồi đọc saved choices và exact question/group pin qua PracticeQuestionCatalog.markingKey. Không dùng current publication pointer/status, nên republish/archive không đổi key chấm. Validate đầy đủ membership/count và đúng một correct choice; pin lỗi không thay nguồn.

Mỗi câu (kể cả chưa lưu/bỏ chọn) có row attempt_answers: is_correct, correct_choice_id, explanation_snapshot và part_number_snapshot. Upsert grading chỉ thay các trường grading; giữ lựa chọn, answered_at/updated_at của save đã xác nhận. Câu chưa có row có selected_choice_id/answered_at NULL. Persist part results (unique attempt/part, accuracy trên tổng câu của Part), summary correct/skill counts, submitted_at/duration_seconds/status GRADED cùng transaction. Failure giữa chấm hoặc trước commit rollback cả answer marks/part/header; không cần worker recovery.

V26 thêm ba cột nullable để tương thích autosave và legacy: correct_choice_id UUID với composite FK cùng question_version_id, explanation_snapshot TEXT, part_number_snapshot INTEGER check 1–7. Không backfill key hay correctness cũ; FK cho cả selected và correct choices giữ đúng version. Membership practice enforce tại application trước UPSERT; không thêm global FK answers→practice snapshot vì schema còn TEST_VERSION DESIGN. Không thêm bảng/entity trùng.

GET result REPEATABLE_READ thấy header/rows đồng nhất. DTO lấy correctness/key/explanation/Part từ grading snapshot; không chấm lại hoặc đọc source hiện tại. Test cố tình SQL-bypass thay key/explanation sau grading xác minh replay vẫn giữ nguyên; đây là kiểm thử idempotence, không cho phép sửa published version qua application. Không trigger chống SQL trực tiếp toàn schema; published immutability vẫn theo Q-03 application contract. Dữ liệu GRADED legacy không có snapshot V26 không được suy/backfill và sẽ trả 409 snapshot unavailable.

## Trách nhiệm service/persistence

| Thành phần | Trách nhiệm |
|---|---|
| PracticeSubmissionService | Kiểm permission/actor/ownership; cùng parent lock cho save/submit; chấm thô đúng pin, lưu nguyên tử, replay/result chỉ đọc snapshot kết quả |
| PracticeSessionService | Tạo/đọc snapshot PRAC-01 và trả lại lựa chọn đã lưu bằng allowlist an toàn |
| PracticeQuestionCatalog | Public application contract QuestionBank, exact pinned key/group/Part cho grading; không current pointer hoặc Teacher question:read; safe read trước submit giữ allowlist riêng |
| PracticeAttemptRepository | Persist/read aggregate, khóa parent có ownership/source predicate |
| PracticeAnswerRepository | UPSERT lựa chọn/grading snapshots và part results trên bảng có sẵn; tham gia cùng transaction JPA/JDBC và khóa do service giữ |
| PracticeAttempt | Giữ pin/order bất biến và chuyển GRADED/header counters sau khi chấm hợp lệ |
| PracticeAttemptController / PracticeResultDtos | Route/strict input, principal, allowlist response; không trả entity hoặc key trước submit |

## Kiểm thử thực tế

DB PostgreSQL 17.11 test riêng `toeic_prac02_verify` và `toeic_prac02_http_test`, user `q03_main_test`, loopback 127.0.0.1:55459, data_directory `D:/toeic-learning-platform/backend/target/q03-main-postgres`. Đã kiểm database/user/port/data directory trước khi tạo hai DB riêng. Flyway V1–V24 (tên PRAC-02 trước bàn giao) thành công trên cả hai DB, không reset cluster/DB cũ. Fixture dùng lại q03-authoring.sql + prac01.sql; cleanup bổ sung xóa answer/part result trước snapshot/attempt. Server HTTP test và cluster test đã dừng sau kiểm chứng; giữ nguyên DB/schema. Không dùng lại hai DB history V24 thử nghiệm để kiểm tra V26.

- Maven sandbox ban đầu lỗi FileLock cache `.m2`; chạy lại ngoài sandbox theo approval, không thay production config hoặc skip test.
- Lượt đầu: `./mvnw.cmd -o test -Dtest=PracticeAnswerIntegrationTest` với cấu hình bên dưới: **14 test đạt**, 0 failures/errors/skipped. Log `backend/target/prac02-answer-tests.log`.
- Sau bổ sung: `./mvnw.cmd -o test -Dtest=PracticeAnswerIntegrationTest,PracticeAnswerConcurrencyTest`: **19 test đạt**, 0 failures/errors/skipped, BUILD SUCCESS (2026-11-11). Log `backend/target/prac02-answer-concurrency-tests.log`. Bao gồm lưu/thay/bỏ chọn/reload, không lộ key/explanation, ownership/source/permission/401/403/404, payload chặt, expiry boundary, service guard, rollback, republish/archive, hai save cạnh tranh và recheck expiry sau chờ lock thật PostgreSQL.
- Sau chốt contract: `./mvnw.cmd -o test -Dtest=PracticeAnswerIntegrationTest,PracticeAnswerConcurrencyTest,PracticeSubmissionIntegrationTest,PracticeAttemptIntegrationTest`: **103 test đạt**, 0 failures/errors/skipped, BUILD SUCCESS (2026-11-11), 01:05 phút. Gồm 41 test PRAC-02 và 62 test API PRAC-01. Log `backend/target/prac02-submission-targeted-tests.log`. Kiểm đủ Part 1–7, mixed correct/incorrect/cleared/untouched, explanation NULL/bỏ trống, persisted replay sau expiry và SQL-bypass thay source, grouped pin/republish/archive, 401/403/404 + service guards, input, rollback giữa grading/trước commit, save→submit và submit→save/clear/submit với DB row-lock wait thật.
- Sau lượt làm việc bị gián đoạn, full đầu chạy khi DB test chưa khởi động: 838 test, 694 errors do context không kết nối DB, không test nào bị xóa/skip. Log giữ riêng `backend/target/prac02-full-db-unavailable.log`. Cluster phục hồi WAL tự động, đã xác minh đúng database/user/port/data_directory/V24 thử nghiệm trước khi chạy lại. Không sửa production code hoặc reset DB để xử lý lỗi môi trường này.
- **Full backend: 838 test đạt, 0 failures/errors/skipped, BUILD SUCCESS**, 07:50 phút, kết thúc 2026-11-11 08:35:03 +07. Chạy Maven Wrapper `./mvnw.cmd -o test` với cấu hình bên dưới và bỏ `-Dtest=...`, không chọn lọc/skip/xóa test. Gồm 787 test đã commit trước PRAC-02 + 10 AuthServiceTest có sẵn ngoài phạm vi + 41 test PRAC-02. Surefire XML xác minh độc lập tổng 838/0/0/0; log `backend/target/prac02-full-tests.log`. Lượt bàn giao chỉ đổi tên migration V24→V26 và tài liệu, không đổi SQL hoặc Java/test logic; giữ bằng chứng này, không lặp full suite chỉ để commit.
- **Một lượt HTTP socket thật đạt 20/20 bước**, log `backend/target/prac02-http-smoke.log`: login opaque session thật; tạo 5 câu Part 1/2/5 → chọn/thay/bỏ lựa chọn → reload → result trước submit 409 → submit 401/403/404 theo scope → submit GRADED → GET result → submit lặp → sửa sau submit 409 → result actor khác 404 → submit lặp sau expiresAt vẫn cùng JSON. Kết quả đúng 1, sai 1, chưa trả lời 3, rawScore 1, accuracy 20.00%; đủ 5 answer snapshots/3 part results, không duplicate/key/explanation sai pin hoặc transcript/path. Không mock response.
- Sau smoke cleanup còn 0 attempts/answers/part results và 0 fixture Learner users trong DB HTTP; Flyway V24 thử nghiệm success. Không reset DB. Lượt trước bàn giao đã chạy git diff --check riêng 23 file PRAC-02 (gồm file mới), kiểm manifest/hash 39 file nền không đổi và giữ index trống; lượt bàn giao được ủy quyền stage riêng manifest bên dưới.
- Bàn giao sau phát hiện develop dùng V24/V25: **103 test đạt, 0 failures/errors/skipped, BUILD SUCCESS**, 01:04 phút, trên DB mới `toeic_prac02_handoff_v26`, Flyway V26 success. Log `backend/target/prac02-handoff-v26-tests.log`. Nguồn export từ index tree `106c4f8f90163ea9672d1ac4b73b8658cfcdfcb3` (chỉ backend/ml, không repository/worktree mới), backend tree `c80c558cd2b69dc1febf10de549ad14eafc6df7d`; không AuthServiceTest.java hoặc web-auth chưa stage. Maven Wrapper biên dịch toàn bộ main/test source trong export và chạy bốn class practice như lượt 103 test trước; Surefire XML độc lập xác nhận 103/0/0/0. Code/SQL không đổi ngoài tên migration V24→V26; các cập nhật sau đó chỉ tài liệu, backend tree phải giữ nguyên khi commit. Các DB thử nghiệm V24 cũ giữ nguyên history, không Flyway repair/reset. Không lặp full suite hoặc HTTP chỉ để commit; CI trên SHA push sẽ kiểm tra cây Git sạch.

Cấu hình tái lập từ backend (PowerShell; cache dependency đã có, thư mục temp trên D vì lịch sử ổ C thiếu chỗ):

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_prac02_verify'
$env:SPRING_DATASOURCE_USERNAME='q03_main_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:DB_PASSWORD=''
$env:DEBUG='false'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='6'
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE='1'
New-Item -ItemType Directory -Path target/prac02-jvm-tmp -Force | Out-Null
$env:MAVEN_OPTS='-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac02-jvm-tmp -Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
.\mvnw.cmd -o test '-Dtest=PracticeAnswerIntegrationTest,PracticeAnswerConcurrencyTest' '-Dmaven.test.additionalClasspath=D:/toeic-learning-platform/backend/src/test/resources,D:/toeic-learning-platform/backend/src/main/resources' '-DargLine=-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac02-jvm-tmp -javaagent:D:/toeic-learning-platform/backend/target/q03-main-tools/mockito-agent.jar -Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
```

Password rỗng/trust chỉ thuộc cluster test loopback đã kiểm tra. Mockito agent là jar có sẵn từ Maven cache; không commit artifact/log/script runner trong target.

Chạy full suite với cùng env/JVM/classpath, bỏ duy nhất `-Dtest=...`. Không `mvn clean` vì cluster test nằm trong target. Khởi động lại cluster test nếu đã dừng, không initdb/reset:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' -D "$PWD/target/q03-main-postgres" -l "$PWD/target/prac02-resumed-postgres.log" -o '-h 127.0.0.1 -p 55459 -c shared_buffers=32MB' start
& 'C:\Program Files\PostgreSQL\17\bin\pg_isready.exe' -h 127.0.0.1 -p 55459 -t 2
# Xác minh database/user/port/data_directory bằng psql trước khi chạy Maven.
```

HTTP đã chạy trước đổi tên migration: từ backend, giữ env/JVM như trên nhưng đổi DB/port rồi chạy server. DB `toeic_prac02_http_test` đã được tạo riêng và Flyway migrate V1–V24 thử nghiệm; script từ chối DB identity sai hoặc fixture users có sẵn. Không chạy code V26 trên DB history V24 thử nghiệm; dùng DB test mới cho lần kiểm tra V26, không sửa history/repair DB cũ.

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_prac02_http_test'
$env:SERVER_PORT='18082'
$env:SERVER_ADDRESS='127.0.0.1'
.\mvnw.cmd -o spring-boot:run '-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac02-jvm-tmp -Xms64m -Xmx320m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
# Terminal khác từ backend, sau khi server đã started:
.\scripts\prac02-http-smoke.ps1
```

Smoke dùng psql 17 và Q03FixtureHash.java/spring-security-crypto 7.1.1 có sẵn; mật khẩu ngẫu nhiên/token chỉ ở memory, không log/commit. Script assert record tạo qua HTTP tồn tại đúng DB test và cleanup fixture ở finally. Không SMTP vì fixture ACTIVE; không đổi đăng ký/xác minh/resend OTP.

## Thay đổi có sẵn được bảo toàn

Không chỉnh `.gitignore`, AGENTS.md, frontend/web/**, AuthServiceTest.java, api-tests/**, docs/WEB_Auth_Integration.md hoặc tatus. Đã ghi manifest/hash trước sửa tại ignored target; kiểm tra SHA256 39 file nền khi bàn giao không có file nào đổi. Người dùng đã ủy quyền stage/commit/push riêng manifest PRAC-02 và kiểm CI đúng SHA; không merge PR. Author/committer bắt buộc pthanh137 <sleapf137@gmail.com>.

## Manifest PRAC-02 — 23 file

10 file mới:

- backend/scripts/prac02-http-smoke.ps1
- backend/src/main/java/com/toeic/backend/assessment/api/SavePracticeAnswerRequest.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeResultDtos.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSubmissionService.java
- backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAnswerRepository.java
- backend/src/main/resources/db/migration/V26__practice_grading_snapshots.sql
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeAnswerConcurrencyTest.java
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeAnswerIntegrationTest.java
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeSubmissionIntegrationTest.java
- docs/PRAC02_Answers_Submission_Grading.md

13 file đã có được sửa riêng cho PRAC-02:

- backend/src/main/java/com/toeic/backend/assessment/api/PracticeAttemptController.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeException.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionDtos.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionService.java
- backend/src/main/java/com/toeic/backend/assessment/domain/PracticeAttempt.java
- backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepository.java
- backend/src/main/java/com/toeic/backend/questionbank/application/PracticeQuestionCatalog.java
- backend/src/test/resources/fixtures/prac01-cleanup.sql
- docs/API_Specification.md
- docs/ERD_v1.6.md
- docs/HLD.md
- docs/LLD.md
- docs/SRS.md

Không EXAM-01/frontend, batch/revision, auto-submit/scheduler, worker/outbox/analytics/notification, publish/upload hoặc quy đổi TOEIC. 39 file nền (gồm tatus) ngoài manifest; không đưa log/jar/target runner vào manifest.
