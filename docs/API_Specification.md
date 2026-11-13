# TOEIC Learning Platform — API Specification v1.0

> **SYNC REVIEW v1.7 — 2026-10-28 | Trạng thái: đồng bộ tài liệu, CHƯA freeze/validate runtime**
> **Đồng bộ:** Các endpoint/DTO trong tài liệu này là contract đề xuất, không phải OpenAPI đã kiểm thử. ERD v1.6 đã có `question_versions.status` và không còn cột `learning_progress.content_id` trùng; các ghi chú “missing/duplicate” dựa trên ERD v1.3 là lịch sử. API `rating: AGAIN/HARD/GOOD/EASY` chưa khớp cột `review_history.rating integer`; API `clientSequence` chưa có cột lưu ở `attempt_answers`. `Idempotency-Key` chưa có bảng lưu yêu cầu tương ứng. Các điểm này phải được duyệt trước khi freeze contract.
> Tài liệu liên quan: [SRS](SRS.md) · [HLD](HLD.md) · [LLD](LLD.md) · [API](API_Specification.md) · [ERD v1.6](ERD_v1.6.md).


> **Status:** Proposed HTTP contract (Markdown), chưa phải OpenAPI machine-readable. **Based on:** SRS v1.2, ERD v1.3, HLD v1.1, LLD v1.0. Endpoint names, field names and policies tagged [DESIGN] must be reviewed before freeze.

## 1. Contract conventions

- Base URL: `/api/v1`; JSON UTF-8; `Content-Type: application/json`; HTTPS mandatory.
- Identity hiện dùng opaque Bearer session theo [ADR-017](ADR_Auth_Session.md), thay đề xuất JWT. Guest có register/login/verify-email/resend-otp; [CONTENT-01](CONTENT01_Read_API.md) mở hai GET content, chỉ trả published PUBLIC cho Guest và thêm AUTHENTICATED khi session có content:read; PRIVATE luôn ẩn.
- IDs UUID; timestamps ISO-8601 UTC; pagination `page` (0-based), `size` (default 20, max 100), `sort` allowlist.
- `GET` safe; `PUT` idempotent where possible; `POST` state transition cần idempotency policy.
- Các API cá nhân lấy actor từ phiên Bearer đã xác thực, không tin userId request. Teacher/Admin permissions theo RBAC, ownership khi sửa draft.
- Standard response: object hoặc paginated list; không wrap tất cả response bằng `data` nếu không cần.
- Error body: `{ "code":"ATTEMPT_EXPIRED", "message":"...", "traceId":"...", "details":[{"field":"...","reason":"..."}] }`.
- HTTP codes: 200 read/update/submit, 201 create, 204 delete/no body, 400 malformed, 401 unauthenticated, 403 forbidden, 404 not found/hidden, 409 conflict, 422 business validation, 429 quota, 500 internal, 503 AI unavailable.
- `Idempotency-Key` recommended for start/submit/AI messages; implementation storage [OPEN] pending ERD revision.

## 2. Auth/RBAC roles

**ADMIN-07 runtime 2026-11-13:** [Audit, health, thống kê và bằng chứng](ADMIN07_Audit_Health_Usage.md).
Ba quyền V33 `audit:read`, `system:health:read`, `system:usage:read` chỉ thực thi khi
actor ACTIVE có ADMIN, kiểm ở API/service. GET me/login bổ sung capability
`auditRead`, `healthRead`, `usageRead`. Audit trả projection an toàn, không raw details;
thống kê aggregate DB, không gọi là online/realtime. Health public giữ contract cũ.

**ADMIN-02 runtime 2026-11-12:** [Contract, endpoint/payload và kiểm chứng RBAC](ADMIN02_RBAC_Management.md)
thay phần role-assignment DESIGN trong phạm vi quản trị. API `/admin/roles`,
`/admin/permissions`, `/admin/rbac/users`, `/admin/users/{id}/roles` kiểm ACTIVE +
ADMIN + `rbac:manage` tại server/service; roleId/permissionId là integer (userId UUID).
Permission chỉ đọc danh mục đã có enforcement. Không tự nâng quyền, không tự khóa
hoặc mất Admin ACTIVE đủ quyền cuối cùng; conflict 409 và audit nguyên tử.
Role hệ thống không đổi mã/xóa; `displayName`/mô tả/permission sửa được theo guard.
GET me/login thêm `adminCapabilities:{userManagement,rbacManagement}` do server tính.
PATCH status ADMIN-01 mở rộng cho target có ADMIN nếu actor còn `rbac:manage`,
ngoài `user:manage`; danh sách ADMIN-01 vẫn chỉ Learner/Teacher, RBAC users gồm Admin.

| Actor | Allowed scope |
|---|---|
| Guest | register, login, published public content |
| Learner | own profile, learning, tests, SRS, listening, analytics, AI, notifications |
| Teacher | create/review/publish content/question/test if granted permission |
| Admin | users/roles, settings, conversion tables, AI usage, audit |

**Permission granularity [DESIGN]:** `content:write`, `content:publish`, `question:review`, `question:publish`, `test:publish`, `user:manage`, `score_conversion:manage`, `ai:usage:read`, `audit:read`. Seed in `permissions` and `role_permissions`.

**CONTENT-03 runtime:** quản lý CRUD text/category dùng `content:manage:self`/`content:manage:any` thay cho tên `content:write` DESIGN; không cấp `content:publish` hoặc triển khai publish workflow.

**AUTH-03 hiện thực:** [Ma trận và phạm vi kiểm chứng](AUTH03_RBAC.md). V14 chỉ seed `user:read:self` cho LEARNER/TEACHER/ADMIN để bảo vệ hồ sơ hiện có; các permission nghiệp vụ DESIGN ở trên chưa seed/grant, ma trận reviewer/publisher vẫn OPEN. Không có ADMIN bypass hoặc kế thừa role ngầm.

## 3. Endpoint catalogue

Columns: `R` = response shape; all IDs in path are UUID unless otherwise noted.

### 3.1 Identity

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `POST` | `/auth/register` | Guest | RegisterRequest | 201 UserSummary, ACTIVE ngay, không OTP | 409 EMAIL_EXISTS |

| `POST` | `/auth/forgot-password` | Guest | email | 200 generic Message | 400 VALIDATION_ERROR |

| `POST` | `/auth/reset-password` | Guest | email, otp, newPassword | 204, thu hồi phiên, không tự login | 400 VALIDATION_ERROR / PASSWORD_RESET_INVALID |

| `POST` | `/auth/google/challenge` | Guest | — | 200 challenge + HttpOnly binding cookie | 503 GOOGLE_NOT_CONFIGURED |

| `POST` | `/auth/google` | Guest | challengeId, credential, currentPassword? + binding cookie | 200 LoginResponse | 401 GOOGLE_CREDENTIAL_INVALID; 409 GOOGLE_LINK_REQUIRED |

| `POST` | `/auth/login` | Guest | LoginRequest | 200 LoginResponse | 400 VALIDATION_ERROR; 401 INVALID_CREDENTIALS |

| `POST` | `/auth/logout` | Authenticated | — (Bearer header) | 204 | 401 |

| `GET` | `/users/me` | Authenticated + user:read:self, own user | — | 200 UserProfile | 401; 403 FORBIDDEN |

| `PATCH` | `/users/me` | Authenticated + user:read:self + user:update:self, own user | UpdateProfileRequest: fullName?, targetScore? | 200 UserProfile | 400 VALIDATION_ERROR; 401; 403 |

| `POST` | `/users/me/password` | Authenticated + user:password:change:self, own user/session | currentPassword, newPassword | 204, thu hồi mọi phiên; đăng nhập lại | 400 VALIDATION_ERROR; 401; 403; 422 CURRENT_PASSWORD_INVALID |

**AUTH-04 — chốt 2026-11-03:** [Hồ sơ, mật khẩu và mục tiêu](AUTH04_Profile_Password_Goals.md). PATCH allowlist fullName/targetScore, chỉ sửa actor; không nhận email/avatar/role/permission hoặc trường hệ thống. Validation identity trả 400. targetScore là mục tiêu Listening + Reading tổng 10–990, nguyên và bội số 5 theo quy tắc sản phẩm; bỏ qua giữ nguyên, null xóa; chưa có ngày mục tiêu. Đổi mật khẩu/hash và thu hồi mọi phiên trong cùng transaction, không cấp token mới.

| `GET` | `/admin/users` | ADMIN + user:manage | q,role,status,page,size,sort | 200 AdminUserPage | 400; 401; 403 |

| `PATCH` | `/admin/users/{id}/status` | ADMIN + user:manage | `{status: ACTIVE hoặc LOCKED}` | 200 AdminUserSummary | 400; 401; 403; 404; 409 |

| `PUT` DESIGN, chưa triển khai | `/admin/users/{id}/roles` | Admin | AssignRolesRequest | — | — |

**ADMIN-01 runtime 2026-11-12:** [contract, AC và kiểm chứng](TV1_Sprint2_AUTH04_ADMIN01_QA02.md).
V31 cấp `user:manage` mặc định chỉ ADMIN; API và service yêu cầu cả role và permission,
không ADMIN bypass. List/count cùng scope Learner/Teacher, loại tài khoản có ADMIN kể
cả nhiều role; q là chuỗi con literal tên/email, không phân biệt hoa thường Unicode,
tối đa200 ký tự. role LEARNER/TEACHER; status ACTIVE/LOCKED/PENDING/DISABLED; page0,
size20/max100; sort createdAt/fullName/email asc/desc, mặc định createdAt,desc, khóa phụ
userId ASC. Response `{items:[{userId,email,fullName,status,roles,createdAt,updatedAt}],totalElements,page,size}`.
PATCH chỉ nhận status, không query/trường lạ; unknown/out-of-scope user404,
PENDING/DISABLED409, lặp cùng trạng thái không ghi lại audit. Khóa user chung login,
status + audit nguyên tử. Khóa chặn login và request tiếp theo; mở khóa chỉ khôi phục
hiệu lực session còn hạn/chưa revoked theo AUTH-03, không tự revoke hoặc cấp token mới.

### 3.2 Learning content & media

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/contents` | PUBLIC; AUTHENTICATED cần session + content:read | q,categoryId,difficulty,page,size,sort | 200 Page<ContentSummary> | 400; 401 nếu Bearer gửi lên sai |

| `GET` | `/contents/{id}` | Theo visibility như list | UUID content id | 200 PublishedContentVersion + media metadata | 400 UUID sai; 404 không tồn tại/không được phép; 401 Bearer sai |

| `POST` | `/teacher/contents` | content:manage:self hoặc content:manage:any + owner/grant media | title,body,categoryCode; summary,difficulty,assets tùy chọn | 201 ManagementDetail | 400; 401; 403; 404 MEDIA_NOT_FOUND |

| `GET` | `/teacher/contents` | Permission quản lý + DB own/any | categoryCode,status,page,size,sort | 200 Page<ManagementSummary> | 400; 401; 403 |

| `GET` | `/teacher/contents/{id}` | Permission quản lý + DB own/any | UUID, không query | 200 ManagementDetail | 400; 401; 403; 404 |

| `PATCH` | `/teacher/contents/{id}` | Permission quản lý + DB own/any + owner/grant media | Partial title,summary,body,categoryCode,difficulty,assets | 200 ManagementDetail | 400; 401; 403; 404 content/media; 409 state/metadata/media reuse |

| `DELETE` | `/teacher/contents/{id}` | Permission quản lý + DB own/any | Không body/query | 204 archive, kể cả lặp lại | 400; 401; 403; 404 |

| `POST` | `/teacher/contents/{id}/versions` (DESIGN, chưa triển khai; runtime PATCH tự xử lý working draft) | Teacher | CreateContentVersionRequest | 201 ContentVersion | 409 |

| `POST` | `/teacher/content-versions/{id}/publish` | Teacher + content:publish | PublishRequest | 200 ContentVersion | 409 |

| `POST` | `/contents/{id}/progress` | Learner | UpdateProgressRequest | 200 LearningProgress | 422 |

| `POST` | `/contents/{id}/bookmarks` | Authenticated + content:bookmark:self | — | 201 {contentId,createdAt} | 400; 401; 403; 404 CONTENT_NOT_FOUND; 409 BOOKMARK_EXISTS |

| `DELETE` | `/contents/{id}/bookmarks` | Authenticated + content:bookmark:self, own bookmark | — | 204 | 400; 401; 403; 404 BOOKMARK_NOT_FOUND |

| `GET` | `/users/me/bookmarks` | Authenticated + content:bookmark:self | page,size,sort | 200 Page<{content:ContentSummary,createdAt}> | 400; 401; 403 |

| `POST` | `/contents/{id}/history` | Authenticated + content:history:self | — | 204 (create/update) | 400; 401; 403; 404 CONTENT_NOT_FOUND |

| `GET` | `/users/me/content-history` | Authenticated + content:history:self | page,size,sort | 200 Page<{content:ContentSummary,lastAccessedAt}> | 400; 401; 403 |

| `POST` | `/teacher/media` | Teacher | UploadMetadata / multipart (separate contract) | 201 MediaResource | 413 |

**CONTENT-01 hiện thực:** [Contract, quyết định schema và giới hạn](CONTENT01_Read_API.md). Hai GET kế thừa page=0, size=20, max 100; sort allowlist title/publishedAt/difficulty/contentId, mặc định publishedAt,desc và contentId phụ. Filter categoryId/difficulty theo schema. List/count/detail dùng cùng điều kiện content và current version PUBLISHED, thời gian hợp lệ, chưa archived và đúng visibility. Không fallback legacy/version cũ. Media pin version và lọc quyền, không storage_url/transcript. Teacher CRUD text/category có ở CONTENT-03 bên dưới; publish, progress và media upload vẫn chưa triển khai.

**CONTENT-03 — quyết định sản phẩm chốt 2026-11-04, không phải AC tìm thấy trong backlog:** [Contract, transaction và kiểm thử](CONTENT03_Requirement_Check.md). V19 seed sáu category code GRAMMAR/VOCAB/LISTENING/READING/STRATEGIES/DOCS; content:manage:self mặc định Teacher/Admin, content:manage:any chỉ Admin. V20 thêm media_resources.owner_id nullable và media_reuse_grants theo media/user có audit cấp/thu hồi; không backfill owner/grant hoặc thay published links. Không hard-code role/bypass. Principal quyết định owner/creator content, DB own/any cho data/count; 401/403/404 theo xác thực/quyền/phạm vi.

POST tạo DRAFT/PRIVATE; PATCH phân biệt omitted/null, sửa hoặc tạo working draft từ đúng published pointer, giữ published text/assets/pointer. Category/difficulty chỉ sửa khi content DRAFT vì chưa version hóa; published có hai trường này trả 409 CONTENT_METADATA_IMMUTABLE cho toàn bộ PATCH. DELETE archive content, giữ versions/pointer/bookmark/history; 204 idempotent, archived chỉ đọc quản lý, PATCH 409 CONTENT_STATE_CONFLICT. Learner read/search/bookmark/history/count vẫn xét content.status.

**Binding media CONTENT-03 — quyết định sản phẩm đã chốt:** assets là mảng object chỉ có mediaId UUID, ví dụ `{"assets":[{"mediaId":"40000000-0000-0000-0000-000000000001"}]}`. Thứ tự mảng thành display_order 1..N. Omitted giữ links draft, [] bỏ hết, null/duplicate ID/field lạ/URL/path trả 400. Actor cần quyền quản lý content và owner_id của media là actor hoặc có grant chưa revoked; đồng thời PUBLIC hoặc AUTHENTICATED + content:read, PRIVATE luôn từ chối. content:manage:any không bypass. ID gắn mới không tồn tại/không được dùng trả chung 404 MEDIA_NOT_FOUND, không metadata. Mỗi lần sửa phải kiểm lại mọi link cuối còn giữ; retained media không đủ quyền trả 409 CONTENT_MEDIA_REUSE_CONFLICT, nhưng có thể bỏ link đó. Clone kiểm tất cả published links trước khi tạo draft; bất kỳ link sai trả 409 và rollback toàn bộ, kể cả khi payload assets=[]; không clone thiếu hoặc bỏ âm thầm. Grant chỉ cho reuse, không đổi quyền xem Learner hoặc tự gỡ published link khi revoked. Chưa có upload/API cấp-thu hồi grant/chuyển ownership hoặc luồng quản trị quyền media.

Management list page=0,size=20,max=100; filter categoryCode/status; sort createdAt/updatedAt/contentId/status asc/desc, mặc định updatedAt,desc rồi contentId asc. Summary gồm category,title draft duy nhất hoặc đúng pointer,status/visibility content,publishedVersionId/workingDraftVersionId và timestamps. Detail `{content:Summary,versions:[...]}` chứa text/status/timestamps và media metadata đúng version đã lọc; media output vẫn dùng resourceId, input mediaId tham chiếu media_resources.resource_id. Không owner/grant/storage URL/path/transcript/identity nhạy cảm. Allowlist title,summary,body,categoryCode,difficulty,assets; unknown/duplicate/type/query sai 400 VALIDATION_ERROR. title/body/categoryCode không null; summary/difficulty null xóa, omitted giữ. Link chỉ ghi draft trong cùng transaction; khóa content→version→media UUID→grant tới commit để serialize revoke/visibility. Không publish/unpublish/restore.

**CONTENT-02 — quyết định sản phẩm chốt 2026-11-04:** [Contract và transaction](CONTENT02_Search_Bookmarks_History.md). q tìm chuỗi con title/summary/body của current version; không phân biệt hoa/thường, không bỏ dấu, trim/gộp whitespace, rỗng không lọc, tối đa 200 Unicode code point sau chuẩn hóa; %, _ và backslash literal. Quyền + tìm kiếm áp dụng cùng predicate DB cho data/count. Personal endpoints lấy actor từ Bearer principal, không nhận userId; không nhận body/query ở POST/DELETE. V18 chỉ cấp mặc định content:bookmark:self/content:history:self cho LEARNER; Teacher/Admin có permission hợp lệ vẫn được dùng. Không có role bypass. Content:read bổ sung phạm vi AUTHENTICATED; PUBLIC vẫn đọc được khi không có content:read.

List bookmark/history mặc định page=0,size=20,max=100; envelope `{content,page,size,totalElements,totalPages}`. Bookmark sort createdAt hoặc contentId; history sort lastAccessedAt hoặc contentId, cú pháp field,asc|desc, mặc định timestamp desc rồi contentId asc. Unknown/duplicate/invalid parameters trả 400 VALIDATION_ERROR với code/message/traceId/details; thiếu/sai xác thực 401, thiếu permission endpoint 403. Summary không chứa body/media/creator hoặc thông tin identity nhạy cảm.

POST bookmark 201, đã tồn tại 409; DELETE own bookmark 204 hoặc 404 không tồn tại, kể cả bài bị ẩn/không còn content:read. DELETE không lookup/trả trạng thái bài. GET không ghi lịch sử. POST history 204 cho cả tạo/cập nhật một hàng user/content, pin đúng current version + giờ server trong transaction; không cập nhật progress/completion. List/count chỉ hiện pin còn trùng current và còn được đọc; publish mới ẩn entry cũ nhưng giữ hàng, POST tiếp chuyển pin. Bookmark list luôn trả current published version được phép.

### 3.3 Question bank

**Q-03 runtime — quyết định sản phẩm chốt 2026-11-09 (không phải AC trong ảnh backlog):** [Teacher CRUD/API, payload, khóa và kiểm thử](Q03_Question_Authoring.md). Hai resource `/teacher/questions` và `/teacher/question-groups` có POST/GET list/GET detail/PATCH/DELETE và GET `/{id}/history`; question:manage:self mặc định Teacher/Admin, question:manage:any chỉ Admin. Principal quyết định creator; API/service kiểm permission, DB own/any lọc data/count, 401/403/404 theo scope. POST DRAFT; PATCH published tạo/reuse một working draft, giữ snapshot/pointer; DELETE archive parent idempotent 204, giữ FK/history, group archive làm Q-02 ẩn câu hỏi liên quan. Pin groupVersionId tường minh, không tự repin; metadata identity chỉ sửa DRAFT. SINGLE_CHOICE đúng một đáp án, Part 2 A–C, Part khác A–D; group bắt buộc 3/4/6/7. V21 thêm creator group nullable và transcript trên link version–media; owner/grant + visibility/type kiểm độc lập với manage:any. Assets omitted/[]/null theo contract tài liệu Q-03; không URL/path hoặc upload. Các endpoint review/publish bên dưới vẫn DESIGN; GET Q-02 giữ contract an toàn.

**Trạng thái triển khai:** Q-01 cung cấp [domain/mapping/repository](Q01_Question_Domain.md). [Q-02](Q02_Question_Read_API.md) bổ sung hai GET dưới đây theo quyết định người dùng ngày 2026-11-02: chỉ Teacher/Admin có question:read, Bearer bắt buộc, chỉ current published question/group và DTO không đáp án/giải thích/URL/transcript. CRUD quản lý hiện thực theo Q-03 ở trên; các endpoint review/approve/publish/tag bên dưới còn DESIGN.

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/questions` | Teacher/Admin + question:read | partId,topicId,difficulty,page,size,sort | 200 Page<QuestionSummary> | 400; 401; 403 |

| `GET` | `/questions/{id}` | Teacher/Admin + question:read | UUID question id, không query param | 200 QuestionDetail + choices/passage/asset metadata | 400; 401; 403; 404 không tồn tại/không readable |

| `POST` | `/questions` (DESIGN; runtime dùng `/teacher/questions`) | Teacher | CreateQuestionRequest | 201 Question | 422 |

| `POST` | `/questions/{id}/versions` (DESIGN; runtime PATCH tự tạo/reuse working draft) | Teacher | CreateQuestionVersionRequest | 201 QuestionVersion | 422 |

| `POST` | `/question-versions/{id}/submit-review` | Teacher | — | 200 QuestionVersion | 409 |

| `POST` | `/question-versions/{id}/approve` | Reviewer | ReviewDecisionRequest | 200 QuestionVersion | 409 |

| `POST` | `/question-versions/{id}/publish` | Teacher + question:publish | — | 200 QuestionVersion | 409 |

| `POST` | `/question-versions/{id}/archive` | Teacher + question:publish | — | 200 QuestionVersion | 409 |

| `GET` | `/tags` | Authenticated | q | 200 Tag[] | 400 |

| `POST` | `/tags` | Teacher | CreateTagRequest | 201 Tag | 409 |

| `POST` | `/question-groups/{id}/versions` (DESIGN; runtime dùng PATCH `/teacher/question-groups/{id}`) | Teacher | CreateGroupVersionRequest | 201 QuestionGroupVersion | 422 |

Q-02: page=0/size=20 mặc định, size 1–100, sort một trường questionId/partId/topicId/difficulty/publishedAt, mặc định publishedAt,desc, khóa phụ questionId ASC. Unknown/duplicate parameter, status/version tự chọn và sort ngoài allowlist trả 400 VALIDATION_ERROR. V16 thêm pointer đúng identity và trạng thái group/group version (mặc định ẩn, không backfill). List/count/detail dùng cùng predicate tại DB, không latest/fallback; question và group đều phải pin đúng version PUBLISHED với published_at hợp lệ, archived_at NULL. Thiếu/sai token 401, thiếu quyền endpoint 403 FORBIDDEN; có quyền nhưng record ẩn 404 QUESTION_NOT_FOUND. Không có quyền đọc cho Guest/Learner hoặc ADMIN bypass. [Contract DTO và quyết định chi tiết](Q02_Question_Read_API.md).

### 3.4 Tests/Blueprint/Attempts

**EXAM-01 runtime — quyết định sản phẩm 2026-11-11:** [Section Listening/Reading, payload và kiểm thử](EXAM01_Section_Attempts.md). Dùng chung năm route `/attempts` bên dưới, thêm sourceType=SECTION_SNAPSHOT với section `{type,questionCount,durationMinutes}`. LISTENING đủ Part 1–4; READING đủ Part 5–7; tổng 1–100 và 1–120 phút là giới hạn sản phẩm, nguyên nhóm/đúng tổng hoặc 409 SECTION_QUESTIONS_INSUFFICIENT. Permission exam:manage:self mặc định LEARNER; mỗi attempt kiểm permission đúng source sau ownership, không dùng union endpoint để bypass. Pin version/group/order + cấu hình/server expiry V27. Save chỉ trước hạn, submit lần đầu sau hạn được chấm các lựa chọn đã lưu; không nhận body. Không worker tự chốt (EXAM-03) hoặc phụ thuộc CRUD TestVersion (EXAM-04), không quy đổi TOEIC.

**Runtime PRAC-01/02:** PRACTICE_SNAPSHOT có POST/GET attempt và PUT answer/POST submit/GET result theo §4.3–4.5. [PRAC-01](PRAC01_Practice_Sessions.md) giữ pin/order/24h; [PRAC-02 — quyết định sản phẩm vừa chốt 2026-11-11](PRAC02_Answers_Submission_Grading.md) chấm điểm thô đồng bộ/nguyên tử, replay kết quả đã lưu, explanation chỉ sau submit. Các Test/Blueprint/list/TEST_VERSION còn DESIGN.

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/tests` | Learner | type,part,page,size | 200 Page<PublishedTest> | 400 |

| `GET` | `/tests/{id}/versions/{versionId}` | Learner | — | 200 TestOverview (no answer key) | 404 |

| `POST` | `/teacher/blueprints` | Teacher | CreateBlueprintRequest | 201 Blueprint | 422 |

| `POST` | `/teacher/tests` | Teacher | CreateTestRequest | 201 TestDraft | 422 |

| `POST` | `/teacher/tests/{id}/versions` | Teacher | CreateTestVersionRequest | 201 TestVersionDraft | 422 |

| `POST` | `/teacher/test-versions/{id}/validate` | Teacher | — | 200 BlueprintValidationResult | 422 |

| `POST` | `/teacher/test-versions/{id}/publish` | Teacher + test:publish | — | 200 TestVersion | 409 |

| `POST` | `/teacher/test-versions/{id}/retire` | Teacher + test:publish | — | 200 TestVersion | 409 |

| `POST` | `/attempts` | practice:manage:self | StartPracticeRequest | 201 PracticeSession | 400/401/403/409 PRACTICE_QUESTIONS_INSUFFICIENT hoặc PRACTICE_SOURCE_CHANGED |

| `GET` | `/attempts/{id}` | practice:manage:self + Owner | — | 200 PracticeSession, gồm expired | 400/401/403/404; 409 snapshot hỏng |

| `PUT` | `/attempts/{id}/answers/{questionVersionId}` | practice:manage:self + Owner | selectedChoiceId UUID/null | 200 SavedAnswer | 400/401/403/404/422; 409 ATTEMPT_NOT_EDITABLE hoặc ATTEMPT_EXPIRED |

| `POST` | `/attempts/{id}/submit` | practice:manage:self + Owner | Không body/query; không yêu cầu Idempotency-Key | 200 PracticeResult (GRADED), replay cùng kết quả | 400/401/403/404; 409 ATTEMPT_EXPIRED/ATTEMPT_NOT_EDITABLE hoặc snapshot hỏng |

| `GET` | `/attempts/{id}/result` | practice:manage:self + Owner | — | 200 PracticeResult đã lưu | 400/401/403/404; 409 RESULT_NOT_READY hoặc snapshot hỏng |

| `GET` | `/attempts` | Learner | page,size,status | 200 Page<AttemptSummary> | 400 |

### 3.5 Vocabulary & SRS

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/vocabularies` | Learner | q,page,size | 200 Page<Vocabulary> | 400 |

| `POST` | `/vocabularies` | Learner | CreateVocabularyRequest | 201 Vocabulary | 422 |

| `GET` | `/decks` | Learner | — | 200 Deck[] | 401 |

| `POST` | `/decks` | Learner | CreateDeckRequest | 201 Deck | 422 |

| `POST` | `/decks/{id}/vocabularies` | Owner | AddToDeckRequest | 201 DeckVocabulary | 409 |

| `DELETE` | `/decks/{id}/vocabularies/{vocabularyId}` | Owner | — | 204 | 404 |

| `GET` | `/reviews/due` | Learner | limit,deckId | 200 DueVocabulary[] | 400 |

| `POST` | `/reviews` | Learner | ReviewVocabularyRequest | 201 ReviewReceipt | 409 REVIEW_CONFLICT |

| `GET` | `/reviews/history` | Learner | page,size | 200 Page<ReviewHistory> | 400 |

### 3.6 Listening practice

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/dictations` | Learner | page,size | 200 Page<DictationExercise> | 400 |

| `POST` | `/dictations/{id}/attempts` | Learner | — | 201 DictationAttempt | 409 |

| `POST` | `/dictation-attempts/{id}/submit` | Owner | SubmitDictationRequest | 200 DictationResult | 422 |

| `GET` | `/shadowings` | Learner | page,size | 200 Page<ShadowingExercise> | 400 |

| `POST` | `/shadowings/{id}/attempts` | Learner | — | 201 ShadowingAttempt | 409 |

| `POST` | `/shadowing-attempts/{id}/recording` | Owner | RecordingMetadata | 200 RecordingReceipt | 413 |

| `GET` | `/shadowing-attempts/{id}/feedback` | Owner | — | 200 ShadowingFeedback | 404 |

### 3.7 Analytics, recommendation, notification

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/analytics/overview` | Learner | range | 200 LearningDashboard | 400 |

| `GET` | `/analytics/parts` | Learner | range | 200 PartPerformance[] | 400 |

| `GET` | `/analytics/errors` | Learner | range | 200 ErrorCategoryStat[] | 400 |

| `GET` | `/recommendations` | Learner | page,size | 200 Page<Recommendation> | 400 |

| `GET` | `/notifications` | Learner | unreadOnly,page,size | 200 Page<UserNotification> | 400 |

| `PATCH` | `/notifications/{id}/read` | Owner | — | 200 UserNotification | 404 |

| `GET` | `/notifications/unread-count` | Learner | — | 200 UnreadCount | 401 |

### 3.8 AI Tutor

**RAG internal runtime:** Python tách Knowledge ingestion và Tutor querying, LangChain + pgvector,
khóa admin/service riêng; [contract, migration và giới hạn](RAG_Tutor.md).
Các route `/internal/v1/*` không mở public, không thay thế auth/quota/history của Spring Boot.
Các API hội thoại bên dưới vẫn là DESIGN, chưa được phần triển khai Python này hiện thực.

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `POST` | `/ai/conversations` | Learner | CreateConversationRequest | 201 Conversation | 429 |

| `GET` | `/ai/conversations` | Learner | page,size | 200 Page<Conversation> | 400 |

| `GET` | `/ai/conversations/{id}/messages` | Owner | page,size | 200 Page<AiMessage> | 404 |

| `POST` | `/ai/conversations/{id}/messages` | Owner | AskTutorRequest | 200 AiTutorResponse | 429 AI_QUOTA_EXCEEDED |

| `GET` | `/admin/ai/usage` | Admin | from,to,provider,model | 200 AiUsageSummary | 403 |

### 3.9 System and score conversion

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/admin/score-conversion-tables` | Admin | status,page,size | 200 Page<ScoreConversionTable> | 403 |

| `POST` | `/admin/score-conversion-tables` | Admin | CreateScoreConversionTableRequest | 201 ScoreConversionTable | 422 |

| `POST` | `/admin/score-conversion-tables/{id}/activate` | Admin | — | 200 ScoreConversionTable | 409 |

| `POST` | `/admin/score-conversion-tables/{id}/retire` | Admin | — | 200 ScoreConversionTable | 409 |

| `GET` | `/admin/audit-logs` | ACTIVE ADMIN + audit:read | actor,action,entityType,entityId,from,to,page,size,sort | 200 Page<AuditItem> sanitized | 400/401/403/503 |
| `GET` | `/admin/audit-logs/{auditId}` | ACTIVE ADMIN + audit:read | UUID | 200 AuditDetail sanitized | 400/401/403/404/503 |
| `GET` | `/admin/health` | ACTIVE ADMIN + system:health:read | — | 200 trạng thái/thời điểm thành phần | 401/403 |
| `GET` | `/admin/usage-statistics` | ACTIVE ADMIN + system:usage:read | from,to UTC [from,to), mặc định30ngày | 200 aggregate tài khoản/Practice/Section/audit | 400/401/403/503 |

| `GET` | `/admin/system-settings` | ACTIVE ADMIN + system:settings:manage | — (không nhận query) | 200 Setting[] (đúng hai key allowlist) | 400/401/403/503 |

| `PATCH` | `/admin/system-settings/{key}` | ACTIVE ADMIN + system:settings:manage | {value: integer, expectedRevision: integer >=0}, không nhận field/query lạ | 200 Setting | 400/401/403/404/409/503 |

**ADMIN-06 runtime:** [Contract, AC và demo Settings/data management](ADMIN06_System_Configuration_Data_Management.md).
Chỉ `vocabulary.recommendations.defaultLimit` (10, 1–100) và
`learning.progress.defaultDays` (7, 1–90), kiểu INTEGER. DTO gồm key/type/value/
defaultValue/minValue/maxValue/revision/updatedAt/updatedBy. PATCH kiểm revision,
409 khi cũ; giá trị không đổi với revision đúng là no-op. Audit cùng transaction.
Không có API tạo/xóa key. `GET /vocabulary/recommendations` bỏ `limit` và
`GET /learning-profile/progress` bỏ `days` đọc mặc định từ DB ở request kế tiếp;
tham số hợp lệ đã truyền được ưu tiên, rỗng/sai/duplicate trả400. Không thay lịch sử,
attempt, auth/session/grading. Settings audit thêm action SYSTEM_SETTING_UPDATED,
entityType SYSTEM_SETTING, entityId là một trong hai key; chi tiết chỉ số/revision.

## 4. DTO schema definitions (contract draft)

### 4.1 Authentication

**Policy mới:** register trả ACTIVE ngay, không gửi OTP. Gỡ verify-email/resend-otp;
thay bằng public POST `/auth/forgot-password` và `/auth/reset-password`, OTP chỉ
khôi phục mật khẩu. [Payload, lỗi và transaction](Password_Recovery.md) ưu tiên
hơn các ví dụ OTP đăng ký lịch sử.

**Google OIDC runtime:** public POST `/api/v1/auth/google/challenge` và
`/api/v1/auth/google`, nonce/cookie binding, xác minh ID token và xác nhận mật khẩu
khi link theo [Google_OAuth2.md](Google_OAuth2.md). Login/me bổ sung
`user.localLoginEnabled`; không thay bearer/OTP contract hiện hữu.

```json
// POST /auth/register
{"email":"learner@example.com","password":"<user-provided-secret>","fullName":"Learner"}
// POST /auth/login
{"email":"learner@example.com","password":"<user-provided-secret>"}
// 200 LoginResponse (opaque session; no refresh token)
{"accessToken":"<opaque-token>","tokenType":"Bearer","expiresIn":900,"user":{"userId":"<uuid>","email":"learner@example.com","fullName":"Learner","avatarUrl":null,"status":"ACTIVE","emailVerifiedAt":"2026-11-01T00:00:00Z","lastLoginAt":"2026-11-01T00:01:00Z","roles":["LEARNER"],"targetScore":null}}
// PATCH /users/me: có ít nhất một trường; bỏ qua trường nào thì giữ nguyên
{"fullName":"Learner Updated","targetScore":750}
// PATCH /users/me: xóa mục tiêu, giữ nguyên tên
{"targetScore":null}
// POST /users/me/password; 204, không body/token mới; phải login lại
{"currentPassword":"<current-secret>","newPassword":"<new-secret>"}
```

Do not log password or tokens. Refresh token endpoint/policy remains [OPEN]. Current-session revocation uses V13 and [ADR-017](ADR_Auth_Session.md).

**Contract identity hiện triển khai:**

- Login: email hợp lệ, tối đa 255 ký tự, chuẩn hóa không phân biệt hoa thường; password không trống, tối đa 72 ký tự và 72 byte UTF-8. Chỉ ACTIVE được login. Sai email/password hoặc không ACTIVE cùng trả `401 INVALID_CREDENTIALS`; vượt 72 byte cũng trả mã này. JSON sai/validation lỗi trả `400 VALIDATION_ERROR`.
- Private requests dùng `Authorization: Bearer <accessToken>`; thời hạn mặc định 900 giây (đọc expiresIn thực tế). Không nhận token qua cookie/query/body. Public auth bỏ qua bearer cũ.
- Logout không có body, thu hồi phiên đang dùng trước khi trả 204; phiên khác vẫn hoạt động. Dùng lại token/logout lần hai trả `401 INVALID_TOKEN`.
- `GET /api/v1/users/me` trả chính object user trong ví dụ login, với dữ liệu và role hiện tại; không chứa password, passwordHash hay tokenHash. Actor lấy từ principal.
- PATCH me chỉ nhận fullName (chuỗi không blank, tối đa 150 ký tự trước strip, không null) và targetScore (JSON integer 10–990, bội số 5 hoặc null). Từ chối payload rỗng/trường lạ và ép kiểu chuỗi/boolean/số thực thành điểm. Tên và mục tiêu ghi cùng transaction; không sửa estimatedScore/skill levels.
- POST me/password chỉ nhận currentPassword/newPassword dạng chuỗi. NewPassword không blank, 8–72 ký tự và <=72 byte UTF-8 như đăng ký; currentPassword không blank, <=72 ký tự/byte. Không strip mật khẩu. Sai mật khẩu cũ trả 422 CURRENT_PASSWORD_INVALID, validation trả 400; cả hai không ghi hash/thu hồi phiên. Thành công thu hồi mọi phiên user (kể cả phiên gọi), không ảnh hưởng user khác; dùng lại phiên cũ trả 401. Login/đổi mật khẩu khóa cùng user để không tạo phiên còn hiệu lực bằng mật khẩu cũ sau đổi.
- `/users/me` yêu cầu permission DB `user:read:self` ở API và service, kèm ownership ở service. V14 cấp cho ba role; thu hồi grant/role có hiệu lực từ request sau khi commit, trả 403 dù session còn hợp lệ. Logout vẫn cho phép thu hồi chính session khi không còn permission nghiệp vụ. Payload role/permission trong register/login không được áp dụng; không có API tự cấp quyền.
- Thiếu token: `401 UNAUTHENTICATED`; sai/không tồn tại/đã thu hồi hoặc tài khoản bị khóa: `401 INVALID_TOKEN`; tại hoặc sau expiresAt: `401 TOKEN_EXPIRED`. Lỗi auth có `{code,message,traceId,details:[]}` và `WWW-Authenticate: Bearer`.
- `/api/v1/admin/**` yêu cầu ADMIN: phiên hợp lệ thiếu role trả `403 FORBIDDEN`, thiếu phiên trả 401. API admin trong catalogue vẫn là đề xuất; rule route không đồng nghĩa nghiệp vụ admin đã triển khai.
- Register/verify-email/resend-otp và hai health endpoint cũ vẫn public. Contract lỗi đăng ký–OTP giữ nguyên.

### 4.2 Question version and publish

```json
{
  "questionId":"<uuid>",
  "questionText":"Choose the correct answer...",
  "difficulty":"MEDIUM",
  "groupVersionId":null,
  "choices":[
    {"label":"A","content":"Option A","isCorrect":false,"displayOrder":1},
    {"label":"B","content":"Option B","isCorrect":true,"displayOrder":2}
  ]
}
```

- QuestionVersion state: `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED`.
- Published question choices/answer keys cannot be edited. API teacher endpoints may return answer key; learner test endpoints must **never** return answer key before grading.
- `question_versions.status` absent in DBML v1.3; schema correction mandatory.

### 4.3 Start Attempt

Thứ tự nội bộ group lấy từ question_versions.group_question_order (V23), không thời gian tạo/UUID. Group legacy thiếu hoặc trùng vị trí current members không được chọn; không backfill/suy vị trí, chưa thêm API authoring vị trí. Snapshot lưu thứ tự và không đổi khi reload.

**PRAC-01 runtime — quyết định sản phẩm chốt 2026-11-10, không phải AC backlog:** POST `/attempts` và GET `/attempts/{id}` hiện thực theo [contract, payload, transaction và kiểm thử](PRAC01_Practice_Sessions.md). Chỉ sourceType=PRACTICE_SNAPSHOT, request practice.partNumbers là Part 1–7 (không trùng), questionCount integer 1–200, optional topicId/difficulty; partIds trong DESIGN cũ không là alias runtime. Tìm tổ hợp chính xác nguyên nhóm, mỗi Part có câu, tổng đúng yêu cầu hoặc 409 PRACTICE_QUESTIONS_INSUFFICIENT; không tham lam. Permission practice:manage:self mặc định chỉ LEARNER, grant hợp lệ cho role khác được dùng; 401/403/own 404. Snapshot pin version/group/order, đọc lại sau archive/republish không repin/fallback; media visibility kiểm lại, không đáp án/lời giải/transcript/URL/path. Server expiry 24h, hết hạn vẫn GET với expired=true; PRAC-02 chặn ghi sau expiresAt theo §4.4/4.5. TEST_VERSION/list endpoints còn DESIGN; answer/submit/result đã có runtime PRAC-02.

```json
// Mock test — DESIGN, chưa triển khai trong PRAC-01
{"sourceType":"TEST_VERSION","testVersionId":"<uuid>"}
// Dynamic practice
{"sourceType":"PRACTICE_SNAPSHOT","practice":{"partNumbers":[5],"questionCount":20,"difficulty":"MEDIUM"}}
// 201 PracticeSession (trích một phần)
{"attemptId":"<uuid>","sourceType":"PRACTICE_SNAPSHOT","status":"IN_PROGRESS","startedAt":"2026-11-10T12:00:00Z","expiresAt":"2026-11-11T12:00:00Z","expired":false,"totalQuestions":20,"questions":[{"questionVersionId":"<uuid>","order":1,"questionText":"...","choices":[{"choiceId":"<uuid>","label":"A","content":"..."}]}]}
```

Response questions không chứa `isCorrect`, explanation hoặc hidden grading data. `expiresAt` do server xác định; `testVersionId` chỉ hợp lệ với `TEST_VERSION`.

### 4.4 Autosave Answer

**EXAM-01:** cùng PUT/allowlist và DTO SavedAnswer; source Section yêu cầu exam:manage:self. Từ expiresAt chặn cả chọn/thay/bỏ chọn với 409 ATTEMPT_EXPIRED. Sau GRADED luôn 409 ATTEMPT_NOT_EDITABLE. Không trả key/explanation trong safe GET session dù đã submit. POST/GET Section session và cấu hình: xem [EXAM-01](EXAM01_Section_Attempts.md).

**PRAC-02 runtime — quyết định sản phẩm vừa chốt 2026-11-11.** Chỉ từng câu, không batch/clientSequence. Không nhận trường lạ/trùng, omitted selectedChoiceId hoặc query params; sai payload/kiểu/UUID trả 400. Membership theo pin, choice sai version trả 422 CHOICE_VERSION_MISMATCH; câu ngoài snapshot trả 404 ATTEMPT_QUESTION_NOT_FOUND sau ownership check.

```json
// PUT /attempts/{attemptId}/answers/{questionVersionId}
{"selectedChoiceId":"<uuid>"}
// 200 SavedAnswer
{"questionVersionId":"<uuid>","selectedChoiceId":"<uuid>","updatedAt":"2026-11-11T01:03:00Z"}
```

`selectedChoiceId=null` bỏ chọn, giữ row với answered_at NULL. Server khóa attempt, rồi kiểm IN_PROGRESS và now < expiresAt, membership snapshot/choice đúng pin. GET `/attempts/{id}` bổ sung `answers: [SavedAnswer]` theo thứ tự snapshot, gồm các câu đã lưu/bỏ chọn; không trả key/explanation/transcript/media path. clientSequence/revision vẫn ngoài phạm vi.

### 4.5 Submit / Result

**EXAM-01:** khác Practice ở expiry: Section IN_PROGRESS **được submit lần đầu sau hạn**, chỉ chấm lựa chọn server đã lưu, không body/query/answers. submittedAt là giờ server thực nộp; duration_seconds đã dùng được chặn tối đa configured_duration_seconds. GRADED replay trước expiry check, không chấm lại. Section result thêm `section` (config đã pin) và `parts` (mọi Part bắt buộc, order tăng): mỗi Part có partNumber/totalQuestions/correctCount/incorrectCount/unansweredCount/rawScore/accuracy. Raw đúng=1, sai/bỏ trống=0; accuracy % đúng/tổng, HALF_UP 2 chữ số. Snapshot key/explanation/Part V26 và part results có sẵn được tái sử dụng; không transcript/assets/media path/điểm TOEIC. Practice giữ response cũ (không thêm section/parts NULL) và vẫn từ chối submit mới sau hạn. GET result trước submit 409 RESULT_NOT_READY; không tự chốt khi đọc. [Ví dụ đầy đủ](EXAM01_Section_Attempts.md).

**PRAC-02 runtime:** POST không body/query; cùng permission/ownership như lưu đáp án. Chấm và lưu trong một transaction với khóa attempt dùng chung autosave. GET result dùng REPEATABLE_READ. Cả hai trả cùng DTO GRADED, không cần Idempotency-Key; replay theo attempt, không chấm lại. Submit mới sau hạn 409 ATTEMPT_EXPIRED; GRADED được replay trước expiry check kể cả đã quá hạn. Trước submit GET result trả 409 RESULT_NOT_READY, không key/explanation. Lưu sau submit (kể cả null) trả 409 ATTEMPT_NOT_EDITABLE.

```json
// POST /attempts/{attemptId}/submit hoặc GET /attempts/{attemptId}/result; 200
{
  "attemptId":"<uuid>","status":"GRADED","submittedAt":"2026-11-11T01:45:00Z",
  "totalQuestions":3,"correctCount":1,"incorrectCount":1,"unansweredCount":1,
  "rawScore":1,"accuracy":33.33,"listeningCorrectCount":1,"readingCorrectCount":0,
  "questions":[{
    "order":1,"questionVersionId":"<pinned-uuid>","partNumber":2,
    "selectedChoiceId":"<uuid>","correctChoiceId":"<uuid>",
    "isCorrect":true,"outcome":"CORRECT","explanation":"Giải thích của version đã pin"
  }]
}
```

Mảng ví dụ rút gọn; runtime đủ totalQuestions theo order snapshot. Đúng = 1, sai/bỏ trống = 0; rawScore = correctCount. incorrectCount chỉ các câu đã chọn sai, không gồm unansweredCount. accuracy là phần trăm đúng trên **tổng câu**, làm tròn HALF_UP 2 số thập phân (0–100). outcome CORRECT/INCORRECT/UNANSWERED; câu chưa trả lời có selectedChoiceId NULL, isCorrect false. Key và explanation (có thể NULL) cho mọi câu được snapshot vào attempt_answers V26 lúc submit, từ exact question/group version đã pin; result/replay chỉ đọc snapshot kết quả, không gọi current published hoặc chấm lại. Không transcript/assets/media URL/path, không điểm quy đổi TOEIC/score-conversion fields. Luồng TEST_VERSION, worker/outbox và quy đổi điểm trong DESIGN cũ chưa triển khai.

### 4.6 SRS

```json
// POST /reviews
{"vocabularyId":"<uuid>","rating":"GOOD"}
// 201 ReviewReceipt
{"vocabularyId":"<uuid>","rating":"GOOD","reviewedAt":"2026-10-28T02:00:00Z","nextReviewAt":"2026-10-30T02:00:00Z","repetitionCount":2,"intervalDays":2,"easeFactor":2.5}
```

`nextReviewAt` và các thông số chỉ là ví dụ response; công thức Again/Hard/Good/Easy vẫn cần chốt.

### 4.7 AI Tutor

```json
// POST /ai/conversations/{id}/messages
{"message":"Giải thích câu này và dẫn nguồn nếu có","contextQuestionVersionId":"<uuid>"}
// 200 AiTutorResponse
{"messageId":"<uuid>","answer":"...","citations":[{"documentId":"<uuid>","chunkId":"<uuid>","title":"..."}],"grounded":true,"warning":null}
// When no reliable source
{"messageId":"<uuid>","answer":"...","citations":[],"grounded":false,"warning":"Không tìm thấy tài liệu tham khảo trong hệ thống."}
```

`grounded=false` phải rõ ràng; no fabricated citations. Token usage/cost được ghi ở server; không nhất thiết trả cho learner.

### 4.8 Publish, notifications, errors

```json
// Publish validation error
{"code":"BLUEPRINT_INVALID","message":"Cấu trúc đề chưa hợp lệ","traceId":"<trace-id>","details":[{"field":"part3.questionCount","reason":"Expected 39, got 38"}]}
// Notification
{"userNotificationId":"<uuid>","type":"SCORE_AVAILABLE","title":"Kết quả đã có","createdAt":"2026-10-28T03:00:00Z","readAt":null}
```

## 5. State transitions, validation and error codes

| Resource | Allowed transitions | Invalid transition |
|---|---|---|
| QuestionVersion | DRAFT→IN_REVIEW→APPROVED→PUBLISHED→ARCHIVED; IN_REVIEW→DRAFT | 409 INVALID_STATE_TRANSITION |
| TestVersion | DRAFT→PUBLISHED→RETIRED | 409 TEST_VERSION_IMMUTABLE |
| LearningContentVersion | DRAFT→PUBLISHED→ARCHIVED | 409 CONTENT_VERSION_IMMUTABLE |
| Attempt | CREATED→IN_PROGRESS→SUBMITTED→GRADED | 409 ATTEMPT_NOT_EDITABLE |

| Code | HTTP | When |
|---|---:|---|
| `VALIDATION_ERROR` | 422 | invalid field or domain input |
| `EMAIL_EXISTS` | 409 | registration duplicate |
| `INVALID_CREDENTIALS` | 401 | login failed |
| `FORBIDDEN` | 403 | role/permission insufficient |
| `RESOURCE_NOT_FOUND` | 404 | missing or hidden resource |
| `INVALID_STATE_TRANSITION` | 409 | invalid publish workflow |
| `VERSION_IMMUTABLE` | 409 | edit published/retired version |
| `BLUEPRINT_INVALID` | 422 | incorrect Part/question/group counts |
| `MAX_ATTEMPTS_REACHED` | 409 | attempt quota exceeded |
| `ATTEMPT_EXPIRED` | 409 | autosave after expiry |
| `ATTEMPT_NOT_EDITABLE` | 409 | autosave after submission |
| `CHOICE_VERSION_MISMATCH` | 422 | selected choice not part of question version |
| `RESULT_NOT_READY` | 409 | attempt not graded |
| `AI_QUOTA_EXCEEDED` | 429 | rate/token quota |
| `AI_SERVICE_UNAVAILABLE` | 503 | AI dependency unavailable |
| `IDEMPOTENCY_CONFLICT` | 409 | key reused for different payload |

## 6. Cross-cutting API rules

### 6.1 Concurrency and idempotency

- Start attempt: serialize max-attempts check and creation. Retry with same key must not consume another slot.
- Autosave: transaction checks status/expiry and validates snapshot membership. Race with submit serialized via attempt row lock.
- Submit: idempotent state transition; repeated call returns same receipt; grading background job can retry safely.

**Ngoại lệ runtime PRACTICE_SNAPSHOT PRAC-02:** quyết định sản phẩm 2026-11-11 tại §4.5 dùng grading đồng bộ cùng transaction và replay PracticeResult đã lưu theo attempt; không worker/outbox, bảng idempotency hay key replay HTTP tổng quát. DESIGN background/Idempotency-Key phía trên còn áp dụng làm đề xuất cho luồng Test tương lai.
- Publish: optimistic locking or row lock prevents two publish operations racing; preserve immutable version references.
- Notifications: mark read idempotent, outbox event consumer deduplicates.

### 6.2 Pagination and sorting

```json
{"content":[{"id":"<uuid>"}],"page":0,"size":20,"totalElements":142,"totalPages":8}
```

Sort only known fields; cap size at 100. Never expose hidden/unpublished content to unauthorized users through search/filter/count.

### 6.3 API versioning and compatibility

`/api/v1`; additive fields are backward compatible, changing meaning/type or deleting fields requires new API version. UTC timestamps and UUID string representation are mandatory. Generate OpenAPI YAML after final field names and response envelopes are agreed.

### 6.4 Media upload/download [OPEN]

For production prefer `POST /teacher/media/upload-intents` → signed object storage URL → `POST /teacher/media/{id}/complete` after integrity check; direct multipart endpoint is MVP alternative. Choose exactly one in OpenAPI; enforce authorization, size, MIME, malware scanning as feasible and reference-safe deletion.

### 6.5 AI logging and privacy

Log provider/model, token counts, latency, cost, status, trace ID; do not log raw prompt/PII by default. Usage queries Admin only; user content access isolated per conversation. Model/embedding dimension and provider pricing are deployment configuration, not client contract.

## 7. Contract test matrix

| ID | Endpoint | Case | Expected |
|---|---|---|---|
| API-01 | POST /auth/register | duplicate email | 409 EMAIL_EXISTS |
| API-02 | GET /attempts/{id} | different learner | 403/404 consistent |
| API-03 | POST /attempts | simultaneous last allowed attempt | one 201, one 409 |
| API-04 | PUT /attempts/{id}/answers/{qv} | choice wrong version | 422 |
| API-05 | PUT /attempts/{id}/answers/{qv} | after submit | 409 |
| API-06 | POST /attempts/{id}/submit | repeat same key | same logical receipt |
| API-07 | GET /attempts/{id}/result | grading pending | 409 RESULT_NOT_READY |
| API-08 | POST /teacher/test-versions/{id}/publish | wrong blueprint | 422 |
| API-09 | POST /question-versions/{id}/publish | wrong status | 409 |
| API-10 | POST /reviews | duplicate/racing review | consistent state/history |
| API-11 | POST /ai/conversations/{id}/messages | unauthorized retrieval | 403/no leaked sources |
| API-12 | POST /ai/conversations/{id}/messages | no evidence | warning exact, citations=[] |
| API-13 | PATCH /notifications/{id}/read | another user | 403/404 |
| API-14 | GET /admin/ai/usage | learner | 403 |
| API-15 | GET /tests/{id}/versions/{versionId} | inspect response | no answer key |

## 8. Traceability and readiness

| API family | Use cases | Module |
|---|---|---|
| `/auth`, `/users` | UC-01, UC-02, UC-15 | Identity |
| `/contents`, `/teacher/contents` | UC-03, UC-12 | Learning Content |
| `/questions`, `/question-versions` | UC-13 | Question Bank |
| `/tests`, `/attempts`, `/teacher/blueprints` | UC-04–06, UC-14 | Assessment |
| `/dictations`, `/shadowings` | UC-07–08 | Listening |
| `/vocabularies`, `/decks`, `/reviews` | UC-09 | Vocabulary/SRS |
| `/analytics`, `/recommendations` | UC-10 | Analytics |
| `/ai` | UC-11 | AI Tutor |
| `/notifications` | Engagement / Design Review | Notification |

### Open decisions / freeze gate

- [ ] Fix DBML duplicate `learning_progress.content_id` and missing `question_versions.status`.
- [ ] Confirm permission matrix for teacher/reviewer/admin and self-approval rule.
- [x] Define current-session revocation: V13 + [ADR-017](ADR_Auth_Session.md).
- [ ] Define refresh-token policy if introduced.
- [ ] Decide autosave `clientSequence` persistence / idempotency storage.
- [ ] Freeze precise SRS SRS/SM-2 rating algorithm.
- [ ] Define media upload protocol and private object authorization.
- [ ] Freeze DTO field names and pagination envelope.
- [ ] Generate and validate `openapi.yaml`; run contract tests against implementation.
- [ ] Validate PostgreSQL DDL and JPA mapping; ERD is not yet frozen.

**Document status:** design proposal; not a tested or approved API contract.



## Phụ lục — Đồng bộ liên tài liệu v1.7

| Hạng mục | Quy ước liên tài liệu | Trạng thái |
|---|---|---|
| Nguồn yêu cầu | SRS v1.2 | Draft chờ phê duyệt |
| Kiến trúc | HLD v1.1 Phần B ưu tiên; module `content` theo LLD | Design review |
| Schema | ERD v1.6 là bản logical hiện tại | Chưa kiểm chứng PostgreSQL |
| HTTP errors | `code`, `message`, `traceId`, `details` | API proposal |
| Review rating | API chuỗi, ERD integer | **OPEN — cần mapping hoặc migration** |
| Autosave concurrency | API `clientSequence`, ERD chưa lưu | **OPEN** |
| Idempotency/session | V13 bổ sung auth_sessions theo ADR-017; DBML v1.6 là lịch sử | Session hiện tại đã triển khai; refresh/idempotency **OPEN** |
| SRS scheduling | Simplified SM-2, 4 ratings | **OPEN — chưa chốt công thức** |

> **Lưu ý:** Các ghi chú về ERD v1.2/v1.3 trong phần nội dung gốc là thông tin lịch sử. Đối chiếu ERD v1.6 trước khi triển khai; không sửa Flyway migration đã được áp dụng.
