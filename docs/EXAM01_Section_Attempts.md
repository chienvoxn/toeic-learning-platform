# EXAM-01 — Section Listening/Reading và rà soát Sprint 2 TV2

## Nguồn và trạng thái

Nguồn EXAM-01 trực tiếp: yêu cầu người dùng ngày 2026-11-11 — chọn Listening hoặc Reading, cấu hình số câu/thời gian, lưu attempt và trả kết quả theo Part. Không tìm thấy backlog/ảnh/AC Sprint 2 chi tiết trong repository; không tự bổ sung AC từ DESIGN/OPEN. Đã đối chiếu AGENTS.md, SRS UC-05, HLD Phần B/§7/§25, LLD §2.4/§5.2–5.3, ERD V5/V10/V26, API §3.4/4.3–4.5, tài liệu Q-03/PRAC-01/02 và code/UI local.

Nhánh hiện tại `feat/web-auth-integration`, HEAD `593f82709f5c1644db01a952fc13304690b1e950`. Fetch origin xác nhận [PR #50](https://github.com/chienvoxn/toeic-learning-platform/pull/50) đã được người khác merge lúc 2026-11-11 09:29:35 +07, merge commit `6e81e83ed2b9bd092c044fa50aba44a356568727`; origin/develop tại commit đó, local sau 16 commit. Không tự merge/pull lại: 16 file frontend local vẫn trùng đường dẫn develop từng bị Git chặn ở lượt trước. Không lặp lệnh đã gây lỗi, không tạo nhánh/worktree hoặc reset/stash/commit ngoài nhiệm vụ.

Đã ghi snapshot/hash 39 file có sẵn (web-auth, AuthServiceTest, api-tests/Postman, AGENTS.md, .gitignore, docs/WEB_Auth_Integration.md, tatus) tại ignored `backend/target/exam01-initial-state.json`. Các file này không thuộc EXAM-01 và phải giữ nguyên byte.

Fetch trước triển khai xác nhận origin/develop mới nhất V26 practice grading snapshots; chưa có migration Section. Tất cả dependency assessment/questionbank Q-03/PRAC-01/02 cần thiết đã có ở local, main code hai module không thiếu so với develop. Không cần tích hợp auth V24/V25 hoặc frontend mới để chạy backend Section. V27__section_attempt_configuration.sql mới, không sửa V1–V26/history hoặc reset DB.

Đã đọc nhánh chưa merge `origin/feat/toeic-bulk-import-exam` (`66e71a2`): dùng TestVersion cố định, ExamAttemptController/ExamAttemptService riêng, workflow publish/import/media/score conversion và ExamExpiryWorker. Base nhánh chưa có PRAC-02, có migration V26__exam_workflow.sql trùng số V26 đã merge và alias `/attempts/{id}/submit|result` trùng controller hiện tại. Không nhập/cherry-pick nhánh đó: vượt EXAM-01, phụ thuộc TestVersion/EXAM-04 và cần phối hợp sửa route/migration trước khi tích hợp sau này. EXAM-01 dùng lại route/service/schema hiện hành, không tạo API Exam song song.

## Quyết định sản phẩm đã chốt 2026-11-11

Người dùng chốt trực tiếp sau câu hỏi gộp. Các quyết định này thay luồng TestVersion fixed DESIGN của SRS UC-05 **trong EXAM-01**, không mặc định toàn bộ contract Practice cho Section. Đã ghi cùng override tại SRS/HLD/LLD/API/ERD; không phụ thuộc CRUD bộ đề EXAM-04.

Contract thực thi:

- Ngân hàng question/group versions current published hợp lệ. LISTENING **đủ mọi Part 1–4**, READING **đủ mọi Part 5–7**, mỗi Part ít nhất một câu. Đúng tổng, nguyên nhóm và group_question_order nghiệp vụ; thiếu tổ hợp phù hợp trả 409, không cắt nhóm/fallback nguồn.
- questionCount nguyên 1–100; durationMinutes nguyên 1–120. Đây là giới hạn cấu hình sản phẩm, không khẳng định cấu trúc/thời lượng TOEIC chính thức. Cấu hình trong khoảng nhưng thiếu tổng/mọi Part/nguyên nhóm vẫn 409. Không thêm giới hạn số lượt cho snapshot độc lập TestVersion.
- Server pin phiên bản, thứ tự, section/count/duration và startedAt/expiresAt. Trước expiresAt được lưu/thay/bỏ chọn; từ expiresAt chặn mọi thay đổi đáp án. Clock kiểm lại **sau khi lấy khóa**, không dùng thời gian client.
- Submit lần đầu sau hạn vẫn được chấm các lựa chọn server đã lưu trước hạn, **không nhận answers/body**. Atomic/idempotent và cùng parent lock với save; replay chỉ đọc kết quả đã lưu. Không worker/scheduler tự chốt khi không có request; thuộc EXAM-03, **chưa triển khai**. GET session/result không tự nộp.
- Owner có permission phù hợp theo source; đúng=1, sai/bỏ trống=0, raw total/accuracy và thống kê từng Part. Đáp án đúng/explanation chỉ sau submit; không TOEIC conversion, transcript hoặc media path.

Chi tiết kỹ thuật: permission `exam:manage:self`, mặc định chỉ LEARNER, role khác dùng được nếu có grant; không hard-code role. Endpoint cho phép union practice/exam, nhưng service kiểm **đúng permission theo persisted source** sau ownership, không bypass. Practice giữ nguyên 24h/chặn submit mới sau hạn và response cũ. Source Section là SECTION_SNAPSHOT, attempt_type SECTION, test_version_id NULL. Không hỗ trợ TEST_VERSION/Full Mock ở các route này.

## API và payload

Tái sử dụng `/api/v1/attempts`; không thêm `/exam-attempts` hoặc API grading thứ hai.

| Method/path | Request | Response |
|---|---|---|
| POST /attempts | Start Section bên dưới | 201 safe Session, có section config đã pin |
| GET /attempts/{id} | Không query | 200 cùng Session + answers đã lưu, expired do server |
| PUT /attempts/{id}/answers/{questionVersionId} | selectedChoiceId UUID hoặc null | 200 SavedAnswer như PRAC-02 |
| POST /attempts/{id}/submit | Không body/query/answers | 200 GRADED Result; cùng kết quả khi replay |
| GET /attempts/{id}/result | Không query | 200 persisted Result; trước submit 409 RESULT_NOT_READY |

```json
{
  "sourceType":"SECTION_SNAPSHOT",
  "section":{"type":"READING","questionCount":6,"durationMinutes":25}
}
```

Root chỉ sourceType/section cho Section, không nhận practice (kể cả null); filter chỉ type/questionCount/durationMinutes. type đúng LISTENING/READING, không lowercase/alias. Không coerces string/float thành integer; thiếu/null/sai kiểu/unknown hoặc duplicate field/query trái contract trả 400 VALIDATION_ERROR. Không client userId/status/start/expiry/part list hoặc topic/difficulty filter cho Section. Practice vẫn dùng sourceType PRACTICE_SNAPSHOT và practice như trước.

Safe session giữ prompt/choices/assets visibility của PRAC-01, không isCorrect/key/explanation/transcript/URL/path, kể cả GET sau submit. Section thêm (Practice bỏ trường null):

```json
{"section":{"type":"READING","questionCount":6,"durationMinutes":25,"partNumbers":[5,6,7]}}
```

Lưu/thay: `{"selectedChoiceId":"<choice-uuid-cua-pinned-version>"}`; bỏ chọn: `{"selectedChoiceId":null}`. GET session trả answers theo question_order, không tự sửa lựa chọn. Chỉ question trong attempt và choice cùng questionVersionId được nhận; không current version.

Result minh họa (mảng questions rút gọn, runtime đủ totalQuestions):

```json
{
  "attemptId":"<uuid>","status":"GRADED","submittedAt":"2026-11-11T10:00:00Z",
  "totalQuestions":6,"correctCount":1,"incorrectCount":1,"unansweredCount":4,
  "rawScore":1,"accuracy":16.67,"listeningCorrectCount":0,"readingCorrectCount":1,
  "section":{"type":"READING","questionCount":6,"durationMinutes":25,"partNumbers":[5,6,7]},
  "parts":[
    {"partNumber":5,"totalQuestions":1,"correctCount":1,"incorrectCount":0,"unansweredCount":0,"rawScore":1,"accuracy":100.00},
    {"partNumber":6,"totalQuestions":2,"correctCount":0,"incorrectCount":1,"unansweredCount":1,"rawScore":0,"accuracy":0.00},
    {"partNumber":7,"totalQuestions":3,"correctCount":0,"incorrectCount":0,"unansweredCount":3,"rawScore":0,"accuracy":0.00}
  ],
  "questions":[{"order":1,"questionVersionId":"<pinned-uuid>","partNumber":5,"selectedChoiceId":"<uuid>","correctChoiceId":"<uuid>","isCorrect":true,"outcome":"CORRECT","explanation":"Lời giải của pin"}]
}
```

Mọi Part bắt buộc xuất hiện tăng dần, total/correct/incorrect/unanswered tổng bằng tổng các Part; incorrect không gồm unanswered. rawScore=correctCount; accuracy=% đúng/**tổng câu**, HALF_UP 2 chữ số, kể cả câu bỏ trống. outcome CORRECT/INCORRECT/UNANSWERED, câu bỏ trống isCorrect false, explanation có thể NULL. Không điểm 495/990, score conversion fields hoặc protected media metadata trong Result.

| HTTP/code | Điều kiện |
|---|---|
| 400 VALIDATION_ERROR | Sai payload/UUID/query/field, kể cả body trong submit |
| 401 UNAUTHENTICATED | Thiếu/sai/hết session |
| 403 FORBIDDEN | Không có permission đúng source hoặc actor service không khớp principal |
| 404 ATTEMPT_NOT_FOUND | Không tồn tại, ngoài ownership hoặc source chưa hỗ trợ |
| 404 ATTEMPT_QUESTION_NOT_FOUND | Question version không nằm trong snapshot đang sở hữu |
| 422 CHOICE_VERSION_MISMATCH | Choice không thuộc đúng pin, kể cả bản mới cùng question |
| 409 SECTION_QUESTIONS_INSUFFICIENT | Không có tổ hợp đủ mọi Part và tổng, kể cả số cấu hình nhỏ hơn số Part |
| 409 ATTEMPT_EXPIRED | Section save tại/sau deadline; không áp dụng cho submit Section |
| 409 ATTEMPT_NOT_EDITABLE | Ghi đáp án sau GRADED hoặc status không editable |
| 409 RESULT_NOT_READY | Chưa submit, kể cả Section hết hạn chưa có request submit |
| 409 PRACTICE_SOURCE_CHANGED / PRACTICE_SNAPSHOT_UNAVAILABLE | Lỗi catalog/pin dùng chung PRAC-01; giữ legacy error code, không fallback current |

## Persistence, transaction và khác biệt với Practice

V27 thêm attempts.section_type VARCHAR(20), configured_duration_seconds INTEGER; nullable/NULL cho source cũ. SECTION_SNAPSHOT bắt buộc SECTION/test_version_id NULL/type LISTENING hoặc READING, total_questions 1–100, duration 60–7200 giây/bội 60 và expires_at=started_at+configured duration. total_questions pin questionCount, required Part numbers suy từ type, snapshot V10 giữ đúng tập/group/order version. Không chỉnh/rebase migration đã áp dụng. Cột duration_seconds cũ giữ thời gian đã dùng; không dùng làm cấu hình.

SectionSessionService dùng lại PracticeQuestionCatalog và PracticeUnitSelector: toàn bộ publication/type/choice/media/membership eligibility, subset-sum theo từng Part + DP đúng tổng, mỗi Part >=1. Không greedy false shortage. Source locks group parents→versions→question parents→versions→media theo thứ tự hiện có, revalidate rồi persist attempt+snapshots cùng transaction; view thất bại vẫn rollback.

PracticeSessionService đọc safe snapshot dùng chung, requireSource bảo vệ per-attempt permission. PracticeSubmissionService giữ cùng khóa parent READ_COMMITTED cho save/submit, kiểm now sau khóa; chỉ Section bỏ điều kiện trước-hạn khi submit. AttemptGrading dùng exact pinned key/group, lưu correctness/key/explanation/Part V26, part results và header GRADED cùng transaction. Nếu key Part không thuộc section, pin/count/group hỏng hoặc failure trước commit thì rollback, không đổi nguồn. Kết quả Part đọc persisted attempt_part_results, đối chiếu với từng câu đã chấm; không đọc source key khi replay. GET result REPEATABLE_READ.

submittedAt là thời điểm server thực nhận/chốt submit (có thể sau expiresAt); duration_seconds = thời gian từ startedAt đến min(submittedAt,expiresAt) cho Section. Header elapsed không tăng vượt cấu hình khi submit muộn. Không scheduler, không tự nộp khi GET, không nhận late answer qua submit. EXAM-03 chưa có.

## Tái sử dụng phần chuẩn bị và trách nhiệm service

Giữ và tiếp tục mở rộng năm file chuẩn bị, không bỏ công việc đã làm. AttemptGrading đã tách từ PracticeSubmissionService nay dùng chung cho Section, không tạo controller/chấm điểm thứ hai. Practice giữ policy 24h như PRAC-02; Section dùng policy đã chốt riêng ở trên. Helper chỉ chạy trong transaction của use case và sau khi caller giữ khóa attempt. Lấy key qua public contract PracticeQuestionCatalog, đúng pin/group, không current version; answer/part/header cùng transaction.

PracticeAnswerRepository đọc **partResults(attemptId)** từ attempt_part_results đã persisted, join toeic_parts để trả partNumber đúng nghiệp vụ và thứ tự tăng dần. Không dùng current answer key để dựng lại kết quả. DTO dùng chung thêm fields Section có JsonInclude NON_NULL, Practice không đổi JSON contract. Test mixed-result Practice bổ sung xác minh Part 1/2/5 lần lượt total 2/1/2, correct 1/0/1, accuracy 50.00/0.00/50.00; Section kiểm tổng mỗi Part khớp toàn bài và đủ mọi Part yêu cầu.

| Thành phần | Trách nhiệm bằng tiếng Việt |
|---|---|
| SectionSessionService / SectionStartQuery | Validate cấu hình/feasibility, chọn đủ mọi Part, pin và persist Section nguyên tử, không CRUD đề |
| PracticeAttemptController / StartPracticeRequest | Dispatch source trên cùng routes, strict allowlist/type/duplicate fields, principal từ session |
| PracticeSessionService / PracticeAccess | Safe read, ownership/source permission chính xác, cấu hình từ DB; không lộ key trước submit |
| PracticeSubmissionService | Guard/ownership và cùng row lock save/submit; áp dụng expiry riêng cho Practice/Section, replay/result |
| AttemptGrading | Dùng chung chấm đúng pin, lưu correctness/key/explanation/Part, part results và summary; đọc replay từ snapshots |
| PracticeAnswerRepository | Tái sử dụng persistence V5/V26; ghi lựa chọn/kết quả và đọc thống kê từng Part đã lưu |
| PracticeAttempt / PracticeAttemptRepository | Pin/config/timestamps và chuyển GRADED, ownership/source predicate, parent lock tới commit |
| PracticeQuestionCatalog / PracticeUnitSelector | Reuse Q-03/PRAC-01 publication/whole group/positions, exact composition và revalidation/locks |
| SectionDtos / PracticeResultDtos / PracticeSessionDtos | Allowlist response, Section config và Part statistics; Practice bỏ fields null để giữ tương thích |

## Rà soát Sprint 2 TV2

Đây là đối chiếu code và bằng chứng từng luồng, **không đánh dấu Done toàn Sprint chỉ vì có API**. Các số test trong tài liệu nhiệm vụ cũ là lịch sử, không phải bằng chứng chạy lại cho EXAM-01.

| Task | Phần backend đã có và tái sử dụng | Khoảng trống cần phân biệt |
|---|---|---|
| CONTENT-02 | Search published có predicate data/count; bookmark/history theo actor, permission và pin version | Chưa xác nhận demo UI end-to-end của lượt này; history không phải completion/progress. Publish/media delivery không thuộc API search |
| CONTENT-03 | Teacher CRUD draft/working version/archive, ownership/permission, media owner/grant/visibility và rollback | Chưa publish/review/restore, upload/download/signed URL, quản trị media grant/owner; có media binding không có nghĩa file phát được trên UI |
| Q-03 | CRUD question/group/choices/explanation và link transcript theo version; published text/pointers giữ bất biến; management/history | Chưa publish/import/taxonomy workflow hoặc media delivery. V23 group_question_order được practice đọc nhưng authoring chưa có API cấp vị trí; nhóm thiếu vị trí không được chọn. Transcript authoring không đồng nghĩa được trả cho Learner trước submit |
| PRAC-01 | Tổ hợp chính xác nguyên nhóm, đủ Part được yêu cầu, publication revalidation/locks; pin version/group/order và server 24h; safe session | Chưa ngân hàng published sản phẩm hoặc protected media playback. Không dùng fixture SQL publish để khẳng định workflow publish đã có. Adapter UI local chưa khớp API runtime |
| PRAC-02 | Lưu/thay/bỏ chọn/reload, submit nguyên tử/idempotent, raw counts + answer/explanation snapshots và concurrency/ownership guards | Backend có 838-test/20-HTTP evidence trước đó; UI autosave hiện tại chưa khớp API, retry/sự đồng bộ trước submit chưa đầy đủ. Không scheduler cho Practice hết hạn, không quy đổi TOEIC |
| EXAM-01 | Backend đã có Section config/start, đủ mọi Part/nguyên nhóm/pin, ownership/source permission, save/deadline/late submit, raw result/Part statistics và HTTP smoke | **UI chưa tích hợp, không đánh dấu toàn bộ EXAM-01 Done**. Chưa EXAM-03 tự chốt không request, EXAM-04/published TestVersion CRUD hoặc protected media delivery |

## UI hiện có và phần chưa nối

Local có TestCatalogPage, TestOverviewPage, AttemptPage, ResultPage và assessment repository/hook; không có màn cấu hình Section count/time đã khớp backend.

- RemoteAssessmentRepository gọi GET `/tests`, POST `/attempts` với `test_id/selected_parts/mode`, PUT `/attempts/{id}/answers` với DTO DESIGN. Backend runtime hiện chưa có Test catalog/published TestVersion flow và dùng PUT `/answers/{questionVersionId}` với selectedChoiceId. Không gọi chúng là tích hợp được chỉ vì có nút bắt đầu.
- Mapper local đọc snake_case/duration fallback, khác payload camelCase runtime. Fake repository chứa grading/ước tính riêng; không dùng nó làm bằng chứng backend hoặc điểm TOEIC.
- Autosave hook optimistic; nhiều request có thể chạy song song; submit chưa chờ toàn bộ pending saves, lỗi chưa được phục hồi bằng retry thật. `retryLastSave` chỉ đổi hiển thị saved. Cần tuần tự hóa/flush lựa chọn đã xác nhận trước submit và hiện lỗi đúng khi nối Section.
- ResultPage còn vùng 495/990 và estimated scores; Section chỉ được hiển thị điểm thô/thống kê đã xác nhận, chưa được quy đổi ngầm. Không thiết kế lại UI của nhóm; chỉ nối khi contract rõ và giữ các file web-auth local.
- QuestionPanel/audio cần policy media delivery hợp lệ; hiện API chỉ metadata, không tự tạo/public hóa storage URL. Transcript không được tự thêm vào safe prompt hoặc result.
- Remote develop đã thay một số assessment hooks/repository và auth/frontend nhưng local chưa tích hợp do phần nền trùng đường dẫn. Không tự lấy toàn bộ ours/theirs hoặc ghi đè các thay đổi đó trong lượt EXAM-01.

## Kiểm thử và môi trường

Cluster test riêng PostgreSQL 17 tại `D:/toeic-learning-platform/backend/target/q03-main-postgres`, loopback 55459, user q03_main_test. Lần start có log file trống và server dừng; kiểm tra process/log/pg_isready rồi start với stderr trực tiếp đã thành công. Không reset/initdb hay sửa DB cá nhân.

Đã tạo DB riêng **toeic_exam01_foundation** sau khi xác minh database/user/port/data directory. Flyway V1–V23/V26/V27 áp dụng thành công theo cây local, không tự nhập auth V24/V25 từ remote. Các test dùng lại fixture Q-03/PRAC-01, published bank chỉ là test fixture, không phải workflow publish hoặc dữ liệu đề thật.

- Trước chốt contract (lịch sử): **103 test Practice đạt**, 0 failures/errors/skipped, 01:06 phút, để kiểm tách helper và Part-results read; log `target/exam01-foundation-tests.log`. Không dùng riêng kết quả này để tuyên bố Section hoàn tất.
- Sau chốt và triển khai: `.\mvnw.cmd -o test '-Dtest=SectionAttemptIntegrationTest,PracticeAnswerConcurrencyTest,PracticeSubmissionIntegrationTest,PracticeAttemptIntegrationTest,AuthFlowIntegrationTest'`: **153 test đạt**, 0 failures/errors/skipped, BUILD SUCCESS, 01:35 phút. Log `target/exam01-section-targeted-tests.log`. Gồm 36 ca Section: hai section/all Part/đủ tổng/order/config/reload, boundary 1/120 phút, strict input/ranges/coercion/duplicate/source injection, feasibility/group thiếu vị trí/thiếu Part, save/replace/clear, submit trước/sau hạn, raw/Part statistics, replay/không sửa key từ current, source-specific permission/actor/401/403/404, choice pin/republish/archive, DB config constraints và atomic create rollback. Auth/RBAC expected permissions cập nhật đúng seed V27, không sửa đăng ký/xác minh/resend OTP.
- `.\mvnw.cmd -o test '-Dtest=SectionConcurrencyTest'`: **7 test đạt**, 0 failures/errors/skipped, BUILD SUCCESS, 24.939 giây; log `target/exam01-concurrency-tests.log`. PostgreSQL lock wait thật: save commit trước submit được chấm; submit commit trước save/clear bị từ chối; hai submit cùng kết quả/không row trùng; request chờ qua expiry phải recheck, save bị chặn nhưng submit được chấm; failure trước commit rollback marks/Part/header.
- **Một lượt full backend cuối: 881 test đạt, 0 failures/errors/skipped, BUILD SUCCESS**, 08:27 phút, kết thúc 2026-11-11 16:28:54 +07. Lệnh `.\mvnw.cmd -o test` cùng env/JVM bên dưới; log `target/exam01-full-tests.log`. Gồm 838 test working tree trước đó (có 10 AuthServiceTest nền giữ nguyên) + **43 test Section**; không xóa/skip test. Không chạy lại full vì chỉ sửa script smoke/tài liệu sau đó, Java/test/migration không đổi.
- Khởi tạo script HTTP đầu có lỗi tên splat `@pracDb` khi biến khai báo là examDb: psql chờ mật khẩu trước kiểm tra DB, **chưa chạy fixture hoặc HTTP request**. Đã dừng đúng process psql của script, sửa thành @examDb và thêm StrictMode/-w để lỗi cấu hình dừng ngay, không hỏi password. Log giữ `target/exam01-http-initialization-failed.log`; không ghi/reset DB cá nhân, không chạy lại Java suite vì lỗi script.
- **Một lượt luồng HTTP thật sau sửa đạt 40/40 bước**, log `target/exam01-http-smoke.log`, không mock response. DB mới `toeic_exam01_http_test`, server loopback 18083. Ba login thật; invalid section/duration/feasibility; LISTENING 7 câu đủ Part 1–4 và READING 6 câu đủ Part 5–7; mỗi flow tạo→chọn/thay/bỏ→reload→result trước submit 409→submit 401/403/404→submit GRADED→GET result→submit lặp→sửa sau submit 409→result actor khác 404. Reading archive source rồi vẫn giữ pin, dịch deadline tại DB test (giữ đúng configured duration) để kiểm save 409 và **submit lần đầu sau hạn thành công**, không tự chốt khi GET. Mỗi section đúng 1/sai 1/bỏ trống total−2; accuracy Listening 14.29%, Reading 16.67%; đủ 4/3 Part rows và 7/6 grading answer rows, replay cùng JSON, không key trước submit/transcript/path/điểm ETS.

Runner/log/agent jar trong ignored target, không phải file giao nộp. Không sửa DB history/migration đã áp dụng. Cleanup fixture ở finally, không reset DB; sau smoke kiểm lại 0 attempts/answers/part results và fixture Learners. Surefire XML tổng cuối được đối chiếu với log trước bàn giao.

Kiểm tra cuối: Surefire XML 47 suites tổng 881/0/0/0 khớp log; DB HTTP V27 success, attempts/answers/part results/fixture Learners đều 0 sau cleanup. Manifest 29 file khớp thay đổi thực tế, 9 file mới/20 file dùng chung; git diff --check cả file tracked và mới sạch, không secret patterns/artifact ngoài phạm vi. 39 file nền (gồm tatus) nguyên SHA256, không thay đổi bất ngờ, index trống và HEAD vẫn 593f827. HTTP server 18083 và cluster test đã dừng sau kiểm chứng, DB/schema giữ nguyên. Sau full suite chỉ sửa script smoke/tài liệu; không lặp full suite.

## Chạy test và demo

Từ backend, dùng cluster/DB **test riêng**, không DB cá nhân hoặc Maven clean (cluster nằm trong target). Các DB đã được tạo và migrate; không chạy V27 trên DB có V24 PRAC-02 thử nghiệm cũ. Password rỗng/trust chỉ cho loopback cluster test.

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_exam01_foundation'
$env:SPRING_DATASOURCE_USERNAME='q03_main_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:DB_PASSWORD=''
$env:DEBUG='false'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='6'
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE='1'
$env:MAVEN_OPTS='-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac02-jvm-tmp -Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
.\mvnw.cmd -o test '-Dmaven.test.additionalClasspath=D:/toeic-learning-platform/backend/src/test/resources,D:/toeic-learning-platform/backend/src/main/resources' '-DargLine=-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac02-jvm-tmp -javaagent:D:/toeic-learning-platform/backend/target/q03-main-tools/mockito-agent.jar -Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
```

Test liên quan dùng cùng command với `-Dtest=...` như trên. Mockito agent 5.23.0 và crypto jar 7.1.1 có sẵn từ Maven cache theo PRAC-02; offline không skip test. Java temp trên D giữ cấu hình môi trường đã kiểm chứng, không thêm công nghệ production.

Khởi động cluster đã có nếu đang dừng (không initdb/reset), xác minh identity trước Maven/server:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' -D "$PWD/target/q03-main-postgres" -w -t 30 -o '-h 127.0.0.1 -p 55459 -c shared_buffers=32MB' start
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -w -h 127.0.0.1 -p 55459 -U q03_main_test -d toeic_exam01_foundation -Atc "select current_database(),current_user,inet_server_port(),current_setting('data_directory');"
```

HTTP demo giữ env trên, đổi DB/port rồi chạy server. Terminal khác từ backend chạy script; script từ chối sai DB/user/port/data directory, sai loopback port hoặc fixture đã có.

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_exam01_http_test'
$env:SERVER_PORT='18083'
$env:SERVER_ADDRESS='127.0.0.1'
.\mvnw.cmd -o spring-boot:run '-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=D:/toeic-learning-platform/backend/target/prac02-jvm-tmp -Xms64m -Xmx320m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
# Terminal khác sau khi server đã started:
.\scripts\exam01-http-smoke.ps1
```

Script dùng Q03FixtureHash.java và fixture ACTIVE để login opaque Bearer thật, mật khẩu ngẫu nhiên/token chỉ trong memory, không log hoặc commit; cleanup chỉ UUID fixture. Không SMTP vì không chạy register/OTP. Đây là demo/test backend, không giao diện sản phẩm hoặc workflow publish. Với database mới ngoài cluster này cần tạo/migrate và cấu hình credential riêng, không sao chép trust/password rỗng vào môi trường cá nhân/production.

## Ghi nhận bàn giao backend

Lượt bàn giao đã fetch origin: origin/develop vẫn `6e81e83`, migration mới nhất V26, nên V27 Section không trùng; SQL V27 đã áp dụng trên hai DB kiểm thử foundation/HTTP nêu trên, giữ nguyên nội dung/tên/history. Không thiếu dependency bắt buộc trong assessment/questionbank. Remote nhánh feat/web-auth-integration ở `a6075dd`, local bắt đầu tại `593f827`; 16 file frontend đang sửa local trùng đường dẫn incoming, nên không ép merge hoặc force-push. Commit backend EXAM-01 được ủy quyền riêng; push/CI mới chỉ có thể xác nhận khi cập nhật remote thành công.

Phạm vi bằng chứng: **881 test là working-tree suite**, gồm **10 test trong AuthServiceTest.java chưa commit**, thuộc 39 file nền phải để ngoài commit. Vì vậy chỉ **871 test trong lượt đó có source thuộc cây commit dự kiến**, không gọi đây là lượt 881 test riêng của staged tree. HTTP 40 bước dùng code/fixtures/helper backend thuộc Git + manifest; không dựa vào web-auth/fake UI. Không sửa Java/test/migration trong lượt review/stage, giữ bằng chứng cũ và không lặp full suite.

Đã export backend/ml trực tiếp từ index tree `267f9213e8c6105c8bb24ba67d03b8037316789b` vào ignored target (không nhánh/worktree/repository mới), không AuthServiceTest chưa stage hoặc frontend local. Maven Wrapper `.\mvnw.cmd -o test-compile` từ export **BUILD SUCCESS**, 9.377 giây: biên dịch 236 main source và 48 test source độc lập. Đây là bằng chứng compile/dependency, **không phải một lượt JUnit mới**. Backend tree `a538ff75ddc5c0f8dfdb00fdcefda65da480c931` phải khớp commit; các cập nhật sau compile chỉ tài liệu. Log `backend/target/exam01-staged-compile.log`. UI, protected media playback và EXAM-03 vẫn chưa hoàn thành.

Các file local đang cản cập nhật nhánh (đều thuộc phần nền, không nằm trong commit EXAM-01):

```text
frontend/web/.env.example
frontend/web/README.md
frontend/web/package.json
frontend/web/src/app/routes.jsx
frontend/web/src/features/auth/api/auth.mapper.js
frontend/web/src/features/auth/hooks/useAuth.jsx
frontend/web/src/features/auth/repositories/auth.api.js
frontend/web/src/features/auth/repositories/auth.fake.js
frontend/web/src/features/auth/repositories/auth.repo.js
frontend/web/src/pages/HealthPage.jsx
frontend/web/src/pages/HomePage.jsx
frontend/web/src/pages/auth/LoginPage.jsx
frontend/web/src/pages/auth/RegisterPage.jsx
frontend/web/src/pages/profile/ProfilePage.jsx
frontend/web/src/pages/profile/SettingsPage.jsx
frontend/web/src/services/apiClient.js
```

## Manifest EXAM-01 — 29 file

9 file mới, gồm hai file chuẩn bị được tiếp tục mở rộng:

- backend/src/main/java/com/toeic/backend/assessment/application/AttemptGrading.java
- backend/src/main/java/com/toeic/backend/assessment/application/SectionDtos.java
- backend/src/main/java/com/toeic/backend/assessment/application/SectionSessionService.java
- backend/src/main/java/com/toeic/backend/assessment/application/SectionStartQuery.java
- backend/src/main/resources/db/migration/V27__section_attempt_configuration.sql
- backend/src/test/java/com/toeic/backend/assessment/api/SectionAttemptIntegrationTest.java
- backend/src/test/java/com/toeic/backend/assessment/api/SectionConcurrencyTest.java
- backend/scripts/exam01-http-smoke.ps1
- docs/EXAM01_Section_Attempts.md

20 file dùng chung đã sửa, giữ logic chuẩn bị và bổ sung contract Section:

- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSubmissionService.java
- backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAnswerRepository.java
- backend/src/test/java/com/toeic/backend/assessment/api/PracticeSubmissionIntegrationTest.java
- backend/src/main/java/com/toeic/backend/assessment/api/PracticeAttemptController.java
- backend/src/main/java/com/toeic/backend/assessment/api/StartPracticeRequest.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeAccess.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeException.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeResultDtos.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionDtos.java
- backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionService.java
- backend/src/main/java/com/toeic/backend/assessment/domain/PracticeAttempt.java
- backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepository.java
- backend/src/main/java/com/toeic/backend/questionbank/application/PracticeQuestionCatalog.java
- backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
- backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
- docs/API_Specification.md
- docs/ERD_v1.6.md
- docs/HLD.md
- docs/LLD.md
- docs/SRS.md

Không frontend/web/** hoặc 39 file nền trong manifest. Người dùng đã ủy quyền stage/commit/push riêng backend EXAM-01 và kiểm CI SHA cuối, author/committer pthanh137 <sleapf137@gmail.com>; không ép cập nhật nhánh hoặc merge PR. Không tạo nhánh/worktree, Full Mock hoặc EXAM-03. Backend có test/HTTP evidence EXAM-01 riêng; **UI chưa tích hợp nên toàn bộ EXAM-01/Sprint 2 TV2 chưa được đánh dấu Done**. Media chỉ metadata được phép; chưa phát/download protected media, không trả transcript và không dùng SQL fixture thay workflow publish.
