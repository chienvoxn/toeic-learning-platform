# TV2 Q-02 — API đọc câu hỏi

Nhánh: feat/question-read-api. Ngày bắt đầu: 2026-11-02. Base origin/develop `027d3b6e88b2893862924d4587ff3f51d1d7733b`, đã có Q-01 (PR #23) và CONTENT-01 (PR #22). Giữ package com.toeic.backend và modular monolith; tatus không thuộc nhiệm vụ.

Không tìm thấy backlog/AC riêng của Q-02 trong repo. Đối chiếu phạm vi người dùng, SRS UC-04/UC-13, HLD Phần B, LLD §2.3, ERD và migration V4/V10/V15, API §3.3. Catalogue hiện ghi GET /questions dành Teacher; policy Learner practice thuộc luồng attempt chưa triển khai, không suy ra quyền đọc trực tiếp ngân hàng câu hỏi.

## Phần đã rõ

- Filter partId/topicId là khóa taxonomy thực tế, không giả định part_id bằng số Part; difficulty khớp chuỗi tối đa 20 ký tự của version, chưa áp đặt enum.
- page=0, size=20 mặc định; page không âm, size 1–100, offset không vượt Integer.MAX_VALUE. Sort một trường questionId/partId/topicId/difficulty/publishedAt với asc|desc; thêm questionId làm khóa phụ ổn định. Unknown/duplicate query parameters, status/version tự chọn và sort field tùy ý bị từ chối 400 VALIDATION_ERROR.
- Tái sử dụng QuestionPromptMapper và mapping Q-01; DTO phải allowlist, không trả entity, answer key, isCorrect/explanation, URL/transcript hoặc asset ID không được phép trong bất kỳ object lồng nhau nào.
- Không có Teacher CRUD/publish, attempt, submit/grading hoặc endpoint xem đáp án. Fixture kiểm thử không phải ngân hàng đề thật Part 1–7.

## Quyết định vừa chốt với người dùng

Ngày chốt: 2026-11-02, theo chỉ đạo trực tiếp trong nhiệm vụ Q-02. Các quyết định này thay thế đề xuất 404 cho thiếu quyền endpoint ở bước trước.

1. Bearer + permission question:read chỉ dành TEACHER/ADMIN; không cấp LEARNER/Guest, không ADMIN bypass. Thiếu/sai/xác thực không còn hợp lệ: 401. Đã xác thực nhưng thiếu quyền endpoint: 403. Có quyền nhưng question không tồn tại hoặc bị ẩn: 404, cùng envelope/message để tránh lộ sự tồn tại. List/count lọc cùng điều kiện với detail.
2. Được bổ sung schema để pin published question và kiểm soát publication của group/group version; mặc định ẩn, không tự backfill published, không chọn version mới nhất hoặc fallback. Chưa triển khai publish workflow.

Đã fetch origin/develop 027d3b6, kiểm tra migration chỉ đến V15. V4/V10 đã có question/version status, timestamps và FK group_version_id; còn thiếu current pointer question/group và publication state của group. V16 bổ sung đúng phần thiếu: questions.current_published_version_id; question_groups.status/current_published_version_id; question_group_versions.status/published_at/archived_at và unique key phục vụ composite FK. Pointer question/group phải thuộc đúng identity tương ứng. Pointer NULL, state DRAFT mặc định; không cập nhật dữ liệu thành published. Không sửa V1–V15.

Read rule: question và pointed version đều PUBLISHED; version published_at không NULL/không ở tương lai và archived_at NULL. Standalone không có group/group version. Grouped question phải pin đúng group version của cùng group; group và group version đều PUBLISHED, group pointer trùng version đang pin, timestamps hợp lệ. Nếu group pointer đổi sang bản khác thì câu hỏi pin bản cũ bị ẩn, không tự thay passage hoặc fallback. API không quyết định số choice/đáp án đúng hay quy trình approve/publish.

Question không có chính sách public như content. Các trạng thái/pointer trên và quyền endpoint là phạm vi truy cập đã chốt; chưa có ownership/tenant ACL riêng từng question trong schema. Asset metadata chỉ lấy từ link đúng question/group version và media.visibility PUBLIC/AUTHENTICATED của V15; PRIVATE luôn bị loại cả metadata lẫn ID trong JSON. Không trả storage URL/path, signed URL hoặc transcript; chưa có download endpoint.

## Phần độc lập đã triển khai

QuestionReadQuery parse/validate filter/page/sort theo quy tắc phía trên; QuestionReadException chuẩn bị lỗi 400/404. QuestionPromptMapper có overload nhận các tập asset ID đã được query lọc quyền; chỉ xuất giao của tập đó với link đúng version, kể cả passage lồng nhau. Không nhận tập ID này từ HTTP request. Overload Q-01 cũ giữ nguyên hành vi nội bộ, không phải đường gọi API Q-02.

QuestionReadQueryTest kiểm tra giới hạn, taxonomy ID, unknown/duplicate parameters và sort injection. QuestionReadPromptMapperTest kiểm tra toàn bộ JSON không có đáp án/giải thích/ID tài nguyên ẩn hoặc tài nguyên ngoài version; kiểm tra cả standalone và contract mapper Q-01 cũ. Đây là phần được hoàn thành trước khi chốt policy; implementation endpoint sau quyết định được mô tả bên dưới.

## Kết quả kiểm thử phần độc lập

Ngày 2026-11-02:

- `.\mvnw.cmd test -Dtest=QuestionReadQueryTest`: 31 test parser đạt, 0 failures/errors/skipped.
- `.\mvnw.cmd test` trên PostgreSQL 17.11 test riêng tại 127.0.0.1:55433/toeic_question_domain_test (datasource override bằng biến môi trường): **BUILD SUCCESS, 153 test đạt**, 0 failures/errors/skipped, gồm 119 test hiện hữu + 31 query + 3 mapper. Không sửa application.properties hoặc migration. Log ở backend/target/question-read-independent-tests.log, không thuộc source thay đổi.
- `git diff --check` và kiểm tra whitespace file mới đạt. tatus giữ nguyên SHA-256 C5DC158F6231925CDEABB6CDF81705CF9B06AC664E092F470DDF26CED1643B93; không stage/commit/push/merge.

Kết quả 153 test phía trên thuộc giai đoạn độc lập trước khi chốt policy, chưa bao gồm test endpoint và V16. Kết quả kiểm thử endpoint/toàn bộ backend hiện tại phải đối chiếu phần bàn giao sau implementation.

## API sau quyết định đã chốt

`GET /api/v1/questions?partId=...&topicId=...&difficulty=...&page=0&size=20&sort=publishedAt,desc`

- Filter partId/topicId theo khóa ID thực tế; difficulty so sánh chính xác, không enum cố định. Part/topic không tồn tại trả trang rỗng. Không hỗ trợ lọc status, tagIds, versionId hoặc partNumber.
- Response `{content:[QuestionSummary],page,size,totalElements,totalPages}`. Summary có questionId, questionVersionId, versionNumber, part `{partId,partNumber,skill,name}`, topic `{topicId,name}` hoặc null, questionType, difficulty, questionText, publishedAt. Không có choice/answer/explanation/media trong summary.
- Sort chỉ questionId/partId/topicId/difficulty/publishedAt, một trường asc|desc, khóa phụ questionId ASC khi cần; không nhận property path từ client. Cả data và count thực hiện predicate readAllowed, publication và filter tại DB; fetch to-one, không join-fetch collection khi phân trang.

`GET /api/v1/questions/{id}`

- id là UUID của question, không phải version. Không nhận query parameter ở detail; không cho client tự chọn version. UUID sai: 400 VALIDATION_ERROR. Không tồn tại hoặc không readable: 404 QUESTION_NOT_FOUND, cùng message và envelope.
- Detail gồm Summary + choices `{choiceId,label,content,displayOrder}`, assets `{resourceId,resourceType,mimeType,fileSize,durationSeconds,displayOrder}`, passage `{groupId,groupVersionId,versionNumber,instruction,passageText,assets}` hoặc null. Question assets không có thứ tự biên tập trong schema nên displayOrder null, sắp theo resourceId. Group assets theo displayOrder rồi resourceId; choice theo displayOrder.
- Tái sử dụng QuestionPromptMapper overload lọc ID và mapping version của Q-01, không serialize entity/AnswerKey. Metadata tài nguyên được query đúng version và lọc visibility tại DB, rồi giao với link của version khi tạo DTO; không lộ asset ID bị ẩn trong passage.

Hai GET yêu cầu SessionPrincipal từ Bearer và role TEACHER/ADMIN cùng authority question:read, kiểm tra tại security chain và service. Tài khoản Learner vẫn bị 403 nếu có grant question:read ngoài chính sách do SQL trực tiếp; không có ADMIN bypass. Quyền/trạng thái tài khoản đọc lại từ DB mỗi request theo session hiện tại; permission bị thu hồi có hiệu lực request tiếp theo. Thiếu/sai token 401 có WWW-Authenticate: Bearer; thiếu quyền endpoint 403 FORBIDDEN, không tiết lộ data/count; có quyền nhưng record bị ẩn 404. Lỗi giữ code/message/traceId/details. Không mở hai route thành public hoặc bỏ qua Bearer.

## Trách nhiệm thành phần

| Thành phần | Trách nhiệm |
|---|---|
| QuestionReadService | Query list/detail trong transaction read-only REPEATABLE_READ, đọc cùng publication snapshot, lấy media được phép và trả DTO an toàn. |
| QuestionReadAccessPolicy | Đọc role/permission từ principal tin cậy, kiểm tra Teacher/Admin + question:read; không nhận cờ quyền từ request. |
| QuestionVersionRepository | Tái sử dụng repository Q-01, thêm query có cùng predicate cho data/count/detail; pin question/group, kiểm tra trạng thái và timestamps tại DB. |
| QuestionAssetRepository | Giữ query nội bộ Q-01, thêm query Q-02 chỉ metadata media PUBLIC/AUTHENTICATED thuộc đúng version; không URL/transcript. |
| QuestionPromptMapper / QuestionReadDtos | Allowlist choice/passage, lọc cả ID lồng nhau và chỉ kết hợp metadata trùng link đúng version. |
| QuestionReadController / QuestionReadQuery / QuestionReadExceptionHandler | Hai GET, validation/filter/page/sort, lỗi 400/404 đúng envelope; auth 401/403 qua security hiện có. |

Không thêm workflow publish/setter công bố, Teacher CRUD, attempts, grading, endpoint đáp án hoặc media download. V16 chỉ biểu diễn publication metadata, không chứng minh dữ liệu đã qua validation/approve thực tế. Question-level tenant/ownership ACL chưa có trong schema; không tự suy ra chính sách đó. Chưa có AC backlog riêng để đối chiếu ngoài yêu cầu người dùng và tài liệu hiện có.

## Kết quả bàn giao sau khi chốt policy

Ngày 2026-11-02, chạy trên PostgreSQL 17.11 test riêng `127.0.0.1:55433/toeic_question_domain_test`, datasource override bằng biến môi trường; không sửa application.properties:

- `.\mvnw.cmd test -Dmaven.resources.overwrite=true -Dtest=QuestionReadIntegrationTest`: **59/59 test endpoint đạt**, 0 failures/errors/skipped. Kiểm tra 401/403/404, Teacher/Admin không bypass permission, Learner không được truy cập kể cả khi có grant SQL ngoài policy, grant/revoke áp dụng request sau; filter/page/sort/count; current pointers/default ẩn/không fallback; publication và group mismatch; JSON/asset isolation; composite FK chặn pointer sang identity khác.
- `.\mvnw.cmd test`: **BUILD SUCCESS — 212 tests, 0 failures, 0 errors, 0 skipped**: 119 hiện hữu + 31 query + 3 mapper + 59 endpoint. Flyway validate đủ 16 migration. Chỉ cập nhật expected permission của test role Teacher/Admin trong AuthFlowIntegrationTest vì V16 cấp question:read; không xóa/bỏ qua test cũ để đạt số lượng.
- Lần chạy endpoint đầu thất bại do cleanup fixture thiếu xóa choices trước version. Đã sửa thứ tự FK, dọn riêng fixture tồn dư trên DB test, chạy lại endpoint và toàn bộ backend thành công; không sửa migration đã áp dụng hoặc tắt constraint/validation.
- Log cuối: backend/target/question-read-tests.log; endpoint log: backend/target/question-read-endpoint-tests.log. Các log/Surefire là build artifacts, không thuộc source thay đổi. Các lỗi SQLSTATE 23503/23505 trong negative tests được assert và không phải test thất bại.
- `git diff --check` và kiểm tra whitespace file mới đạt; V1–V15, source/test content và vocabulary, frontend/AI giữ nguyên. Security config chỉ thêm rule hai GET; auth service/session/OTP không đổi. tatus nguyên hash đã ghi, không stage/commit/push/merge.

Fixture tại question-read.sql và question-read-cleanup.sql có UUID/taxonomy ID cố định, transaction riêng cho setup/cleanup để kiểm tra session/grant thật giữa request. Không seed câu hỏi sản phẩm trong migration, không coi fixture là ngân hàng đề thật Part 1–7.

Review trước commit: đã fetch origin, origin/develop vẫn ở 027d3b6, không có thay đổi mới để tích hợp và migration chỉ đến V15 nên V16 không trùng. Code/test/schema không đổi so với lần chạy đạt 212/212; chỉ bổ sung ghi nhận review vào tài liệu. Đã đối chiếu quyền Teacher/Admin + question:read, composite FK/published pointers, predicate chung list/detail/count, DTO allowlist và asset visibility/version. Không phát hiện credential thật, build log/artifact hoặc file ngoài phạm vi trong tập 26 file Q-02; tatus không được stage.
