# TV2 Q-01 — Question domain và JPA

Ngày: 2026-11-02. Nhánh: feat/question-domain. Chỉ domain, mapping, repository và dữ liệu DTO an toàn cho bước Q-02 sau này; không tạo API, Teacher CRUD, workflow publish, attempt hoặc grading.

## Căn cứ và giới hạn đối chiếu

Không tìm thấy backlog/Acceptance Criteria riêng mang mã Q-01/TV2 trong repo. Đối chiếu theo phạm vi người dùng giao, SRS UC-13/BR-01/BR-14/BR-24, HLD Phần B ưu tiên, LLD §2.3, ERD v1.6 và migration V4/V10. Không tuyên bố đáp ứng AC chưa được cung cấp.

Q-01 ban đầu triển khai trên base 2c35739 khi CONTENT-01 chưa có trong develop. Sau khi PR #22 được tích hợp, đã fetch và fast-forward riêng feat/question-domain tới origin/develop `f9668f29530dc75e5ad2d30f3131839cef535b48`, có đầy đủ source/test CONTENT-01 và V15. Không chỉ dựa vào SHA: đã đối chiếu nội dung content, test và migration với implementation CONTENT-01. Thay đổi Q-01 chưa commit được stash riêng (không gồm tatus), rồi apply và xác minh toàn bộ file/đoạn tài liệu đã khôi phục; không có conflict. Không thêm migration cho Q-01; V1–V15 giữ nguyên so với develop.

## Quyết định

- Question là identity/metadata (Part, topic tùy chọn, question type, group tùy chọn, creator/status/timestamps). QuestionVersion chứa text, difficulty, explanation, audit review/approve/publish/archive và trỏ chính xác group version.
- Choice gắn QuestionVersion, đáp án đúng nằm ở is_correct; explanation là cột của QuestionVersion. Không thêm bảng Answer/Explanation. Không map attempt_answers vì đó là câu trả lời của người học, ngoài Q-01.
- Group/passage dùng question_groups, question_group_versions(instruction,passage_text) và question_group_version_resources(display_order). QuestionVersion phải pin group version thuộc đúng group của question; câu standalone không được gắn group version khác.
- question_resources đã gắn theo question_version_id, nhưng không có display_order. Giữ schema, sắp ổn định theo resourceId; không giả định thứ tự biên tập. Group resources theo displayOrder rồi resourceId khi trùng order. Choice theo displayOrder, label/order duy nhất trong từng version theo constraint V4.
- Media dùng bảng media_resources có sẵn qua UUID FK và truy vấn metadata native có scope version. Không tạo entity media trùng với module content hoặc phụ thuộc repository của module khác. Q-01 không tạo/upload media; fixture tạo media để kiểm tra FK/liên kết. Không đưa storage_url/transcript vào model prompt.
- Domain chỉ cho chỉnh nội dung/choice/resource của QuestionVersion DRAFT. GroupVersion là snapshot tạo mới, không cung cấp sửa passage; khóa thay đổi collection asset sau khi persist/load vì schema group version chưa có status. Không triển khai chuyển trạng thái, chốt số đáp án đúng, số lựa chọn theo Part hay separation of duties: các quy tắc publish/reviewer vẫn cần quyết định ở nhiệm vụ tương ứng. Draft có thể chưa đủ hoặc có nhiều đáp án đúng.
- DB có FK và unique version/choice/resource; chưa có CHECK số dương, trigger immutable hoặc constraint cross-table đảm bảo group của question khớp group version. Domain kiểm tra dữ liệu mới và mapper kiểm tra lại reference trước khi tạo prompt. Không coi các kiểm tra application là DB policy cho SQL trực tiếp.
- Q-02 dùng QuestionPromptMapper tạo record allowlist, không serialize entity/AnswerKey. Prompt không có isCorrect/explanation/answer key, chỉ choice text và reference đúng version. Sensitive getter của entity có JsonIgnore; đó là phòng vệ bổ sung, không thay cho DTO, quyền và thời điểm công bố đáp án. Q-02 vẫn phải kiểm tra quyền/status/snapshot và media ACL trước khi trả response.

Fixture không phải ngân hàng đề thật Part 1–7. Tags/error categories, publish-time validation/audit, DB immutability, authoring concurrency và câu trả lời của learner để nhiệm vụ sau.

## Trách nhiệm các thành phần

| Thành phần | Trách nhiệm |
|---|---|
| Question, ToeicPart, QuestionTopic, QuestionType | Identity và metadata theo bảng V4; topic/group tùy chọn, creator UUID có FK tới users, không cascade ghi user/taxonomy. |
| QuestionVersion | Aggregate text/difficulty/explanation, choice/answer key/resource của đúng version; validate reference group, chỉ cho revise/add choice/add asset khi DRAFT. |
| QuestionChoice / QuestionAnswerKey | Choice có parent version cố định; correctness nằm ở cột V4, AnswerKey chỉ là snapshot bất biến nội bộ, toString redacted, không phải JPA table. |
| QuestionGroup / QuestionGroupVersion | Identity nhóm và snapshot instruction/passage. Không có setter sửa text hoặc API workflow; asset collection khóa sau insert (@PostPersist) hoặc load. |
| QuestionResource / QuestionGroupResource / VersionAssetId | Map link và PK ghép có sẵn; MapsId giữ liên kết parent và khóa version/resource nhất quán. |
| QuestionRepository / QuestionVersionRepository / QuestionGroupRepository | persist aggregate mới và tìm theo identity/version number, fetch to-one context, không generic merge/delete. Repository yêu cầu transaction của caller; cascade chỉ PERSIST choice/resource, không cascade xóa lịch sử/media. |
| QuestionAssetRepository | Query metadata media dùng chung qua link đúng question/group version; không tạo entity trùng, không trả URL/transcript, không triển khai media ACL hoặc download. |
| QuestionPromptMapper / QuestionPrompt | Kiểm tra lại reference rồi chuyển allowlist thành record/list bất biến trong transaction; không chứa đáp án/giải thích. Không tự quyết định status, permission hay thời điểm công bố của Q-02. |

Không thêm application service nghiệp vụ hoặc controller giả: Q-01 chưa có use case authoring/đọc API để bảo vệ bằng RBAC. Identity, content, vocabulary, frontend, AI và security config không đổi. Native metadata query chỉ dùng bảng có từ V3 nên không phụ thuộc cột visibility của CONTENT-01/V15; khi tích hợp Q-02 phải áp dụng media access policy qua interface thích hợp.

## Kiểm thử và phần còn mở

- QuestionDomainTest: duplicate label/order/resource, số thứ tự dương, version number, text/difficulty hợp lệ, group pin, list/AnswerKey bất biến; draft có thể có 0/nhiều đáp án đúng vì publish policy chưa triển khai.
- QuestionPersistenceIntegrationTest: PostgreSQL/Flyway thật, mapping và cascade persist rồi flush/clear/read lại; metadata identity-generated; nhiều version với choice/answer/explanation/passage/media khác nhau; thứ tự ổn định; draft edit không đổi version khác; non-DRAFT bị chặn bởi mọi mutator đã có; DTO serialization không lộ đáp án/giải thích/URL/transcript.
- DB negative tests kiểm tra SQLSTATE 23505 (unique version number, choice label/order và resource link) và 23503 (FK creator/version/group/media). Test ghi group mismatch bằng SQL trực tiếp để chứng minh giới hạn FK hiện có, và xác nhận mapper từ chối dữ liệu đó; không tuyên bố DB đã có cross-table guard.
- Fixture `backend/src/test/resources/fixtures/question-domain.sql` dùng ID/user/taxonomy/media test riêng; mọi integration test có transaction rollback, không phụ thuộc ngân hàng đề sản phẩm.
- Không có backlog AC Q-01 để xác nhận tính đầy đủ. Đáp án single/multiple, số choice theo Part, workflow group version có status hay không, immutable DB policy, taxonomy/tag history, review separation và kiểm soát truy cập Q-02 cần chốt/triển khai sau. Không tự chuyển các mục OPEN thành yêu cầu đã chốt.
- Lần kiểm thử đầu trên base cũ có 51 test hiện hữu (48 identity/foundation + 3 vocabulary) và 27 test Q-01. Sau tích hợp develop có thêm 41 test CONTENT-01; giữ nguyên các test, báo số thực tế của lần chạy kết hợp bên dưới.

## Kết quả kiểm thử trên base cũ (trước PR #22)

Ngày 2026-11-02, PostgreSQL 17.11 test riêng tại `127.0.0.1:55433/toeic_question_domain_test`, cấu hình datasource bằng biến môi trường của process, không sửa application.properties. Đã dọn riêng build output classes/test-classes/surefire-reports để không chạy nhầm class/resource của nhánh CONTENT-01; giữ nguyên dữ liệu cluster và mọi source.

- `.\mvnw.cmd test -Dmaven.resources.overwrite=true -Dtest=QuestionDomainTest,QuestionPersistenceIntegrationTest`: BUILD SUCCESS, **27 test đạt**, gồm 8 domain + 19 PostgreSQL integration, 0 failures/errors/skipped.
- `.\mvnw.cmd test`: BUILD SUCCESS, **78 test đạt**, gồm 27 Q-01 + 51 hiện hữu, 0 failures/errors/skipped. Flyway chạy baseline trên DB test mới và validate đủ 14 migration. Không sửa test identity/content/vocabulary.
- Các dòng SQLSTATE 23505/23503 trong log là lỗi chủ động của negative tests đã được assert; không phải test thất bại. Log tại backend/target/question-domain-tests.log và Surefire trong target là build artifacts, không thuộc source thay đổi.
- `git diff --check`, kiểm tra whitespace file mới và rà soát scope/credential đạt. File tatus giữ nguyên SHA-256 `C5DC158F6231925CDEABB6CDF81705CF9B06AC664E092F470DDF26CED1643B93`, không stage.

## Kiểm tra tích hợp sau PR #22

Schema V15 chỉ bổ sung visibility/media links cho content; các bảng/constraint question V4/V10 không đổi. Q-01 đọc metadata media qua query theo đúng version, không thêm entity media hoặc ghi visibility. Fixture Q-01 không gán visibility, dùng default PRIVATE của V15; media ACL vẫn là trách nhiệm Q-02 khi có API. Không sửa source/test content, identity hoặc vocabulary để tích hợp Q-01. Không có API Q-02 mới.

Kết quả sau tích hợp trên PostgreSQL test riêng ngày 2026-11-02: `.\mvnw.cmd test -Dmaven.resources.overwrite=true` **BUILD SUCCESS — 119 tests, 0 failures, 0 errors, 0 skipped** (41 content + 27 Q-01 + 51 hiện hữu). Flyway nâng DB test V14→V15 và validate đủ 15 migration; không sửa test để khớp số lượng. Log: backend/target/question-domain-integrated-tests.log; build artifacts không thuộc commit. Đã kiểm tra diff/schema và whitespace của cả file mới; không có thay đổi source/schema ngoài Q-01 so với origin/develop f9668f2. File tatus vẫn giữ nguyên hash đã ghi phía trên và không được stage.
