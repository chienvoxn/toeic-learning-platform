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

| Actor | Allowed scope |
|---|---|
| Guest | register, login, published public content |
| Learner | own profile, learning, tests, SRS, listening, analytics, AI, notifications |
| Teacher | create/review/publish content/question/test if granted permission |
| Admin | users/roles, settings, conversion tables, AI usage, audit |

**Permission granularity [DESIGN]:** `content:write`, `content:publish`, `question:review`, `question:publish`, `test:publish`, `user:manage`, `score_conversion:manage`, `ai:usage:read`, `audit:read`. Seed in `permissions` and `role_permissions`.

**AUTH-03 hiện thực:** [Ma trận và phạm vi kiểm chứng](AUTH03_RBAC.md). V14 chỉ seed `user:read:self` cho LEARNER/TEACHER/ADMIN để bảo vệ hồ sơ hiện có; các permission nghiệp vụ DESIGN ở trên chưa seed/grant, ma trận reviewer/publisher vẫn OPEN. Không có ADMIN bypass hoặc kế thừa role ngầm.

## 3. Endpoint catalogue

Columns: `R` = response shape; all IDs in path are UUID unless otherwise noted.

### 3.1 Identity

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `POST` | `/auth/register` | Guest | RegisterRequest | 201 UserSummary | 409 EMAIL_EXISTS |

| `POST` | `/auth/login` | Guest | LoginRequest | 200 LoginResponse | 400 VALIDATION_ERROR; 401 INVALID_CREDENTIALS |

| `POST` | `/auth/logout` | Authenticated | — (Bearer header) | 204 | 401 |

| `GET` | `/users/me` | Authenticated + user:read:self, own user | — | 200 UserProfile | 401; 403 FORBIDDEN |

| `PATCH` | `/users/me` | Authenticated | UpdateProfileRequest | 200 UserProfile | 422 |

| `GET` | `/admin/users` | Admin | page,size,status | 200 Page<UserSummary> | 403 |

| `PATCH` | `/admin/users/{id}/status` | Admin | UpdateUserStatusRequest | 200 UserSummary | 409 |

| `PUT` | `/admin/users/{id}/roles` | Admin | AssignRolesRequest | 200 UserRoles | 403 |

### 3.2 Learning content & media

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/contents` | PUBLIC; AUTHENTICATED cần session + content:read | categoryId,difficulty,page,size,sort | 200 Page<ContentSummary> | 400; 401 nếu Bearer gửi lên sai |

| `GET` | `/contents/{id}` | Theo visibility như list | UUID content id | 200 PublishedContentVersion + media metadata | 400 UUID sai; 404 không tồn tại/không được phép; 401 Bearer sai |

| `POST` | `/teacher/contents` | Teacher | CreateContentRequest | 201 ContentDraft | 422 |

| `POST` | `/teacher/contents/{id}/versions` | Teacher | CreateContentVersionRequest | 201 ContentVersion | 409 |

| `POST` | `/teacher/content-versions/{id}/publish` | Teacher + content:publish | PublishRequest | 200 ContentVersion | 409 |

| `POST` | `/contents/{id}/progress` | Learner | UpdateProgressRequest | 200 LearningProgress | 422 |

| `POST` | `/contents/{id}/bookmarks` | Learner | — | 201 Bookmark | 409 |

| `DELETE` | `/contents/{id}/bookmarks` | Learner | — | 204 | 404 |

| `POST` | `/teacher/media` | Teacher | UploadMetadata / multipart (separate contract) | 201 MediaResource | 413 |

**CONTENT-01 hiện thực:** [Contract, quyết định schema và giới hạn](CONTENT01_Read_API.md). Hai GET trên đã triển khai; các API ghi trong §3.2 vẫn là đề xuất. page=0, size=20 mặc định, size 1–100; sort một trường trong title/publishedAt/difficulty/contentId với asc|desc, mặc định publishedAt,desc. categoryId dương, difficulty khớp chuỗi thực tế tối đa 20 ký tự; chưa hỗ trợ part vì schema chưa liên kết Part. Unknown/duplicate query parameters bị từ chối 400. List/count/detail dùng cùng điều kiện content + current version PUBLISHED, publishedAt có giá trị không ở tương lai, version chưa archived và đúng visibility. Không fallback legacy hoặc version cũ. Media pin theo version qua V15, lọc visibility; response chỉ metadata, không storage_url/transcript. V15 mặc định PRIVATE cho content/media cũ, không backfill/công khai dữ liệu. Payload DTO và trách nhiệm service được mô tả tại tài liệu CONTENT-01.

### 3.3 Question bank

**Trạng thái triển khai:** Q-01 cung cấp [domain/mapping/repository](Q01_Question_Domain.md). [Q-02](Q02_Question_Read_API.md) bổ sung hai GET dưới đây theo quyết định người dùng ngày 2026-11-02: chỉ Teacher/Admin có question:read, Bearer bắt buộc, chỉ current published question/group và DTO không đáp án/giải thích/URL/transcript. Các endpoint ghi trong mục này vẫn là đề xuất.

| Method | Path | Access | Request | R | Error |
|---|---|---|---|---|---|

| `GET` | `/questions` | Teacher/Admin + question:read | partId,topicId,difficulty,page,size,sort | 200 Page<QuestionSummary> | 400; 401; 403 |

| `GET` | `/questions/{id}` | Teacher/Admin + question:read | UUID question id, không query param | 200 QuestionDetail + choices/passage/asset metadata | 400; 401; 403; 404 không tồn tại/không readable |

| `POST` | `/questions` | Teacher | CreateQuestionRequest | 201 Question | 422 |

| `POST` | `/questions/{id}/versions` | Teacher | CreateQuestionVersionRequest | 201 QuestionVersion | 422 |

| `POST` | `/question-versions/{id}/submit-review` | Teacher | — | 200 QuestionVersion | 409 |

| `POST` | `/question-versions/{id}/approve` | Reviewer | ReviewDecisionRequest | 200 QuestionVersion | 409 |

| `POST` | `/question-versions/{id}/publish` | Teacher + question:publish | — | 200 QuestionVersion | 409 |

| `POST` | `/question-versions/{id}/archive` | Teacher + question:publish | — | 200 QuestionVersion | 409 |

| `GET` | `/tags` | Authenticated | q | 200 Tag[] | 400 |

| `POST` | `/tags` | Teacher | CreateTagRequest | 201 Tag | 409 |

| `POST` | `/question-groups/{id}/versions` | Teacher | CreateGroupVersionRequest | 201 QuestionGroupVersion | 422 |

Q-02: page=0/size=20 mặc định, size 1–100, sort một trường questionId/partId/topicId/difficulty/publishedAt, mặc định publishedAt,desc, khóa phụ questionId ASC. Unknown/duplicate parameter, status/version tự chọn và sort ngoài allowlist trả 400 VALIDATION_ERROR. V16 thêm pointer đúng identity và trạng thái group/group version (mặc định ẩn, không backfill). List/count/detail dùng cùng predicate tại DB, không latest/fallback; question và group đều phải pin đúng version PUBLISHED với published_at hợp lệ, archived_at NULL. Thiếu/sai token 401, thiếu quyền endpoint 403 FORBIDDEN; có quyền nhưng record ẩn 404 QUESTION_NOT_FOUND. Không có quyền đọc cho Guest/Learner hoặc ADMIN bypass. [Contract DTO và quyết định chi tiết](Q02_Question_Read_API.md).

### 3.4 Tests/Blueprint/Attempts

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

| `POST` | `/attempts` | Learner | StartAttemptRequest | 201 AttemptSession | 409 MAX_ATTEMPTS_REACHED |

| `GET` | `/attempts/{id}` | Owner | — | 200 AttemptSession | 403/404 |

| `PUT` | `/attempts/{id}/answers/{questionVersionId}` | Owner | SaveAnswerRequest | 200 SavedAnswer | 409 ATTEMPT_NOT_EDITABLE |

| `POST` | `/attempts/{id}/submit` | Owner | Idempotency-Key | 200 SubmissionReceipt | 409 |

| `GET` | `/attempts/{id}/result` | Owner | — | 200 AttemptResult | 409 RESULT_NOT_READY |

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

| `GET` | `/admin/audit-logs` | Admin | actor,action,from,to,page,size | 200 Page<AuditLog> | 403 |

| `GET` | `/admin/system-settings` | Admin | — | 200 Settings | 403 |

| `PATCH` | `/admin/system-settings/{key}` | Admin | UpdateSettingRequest | 200 Setting | 422 |

## 4. DTO schema definitions (contract draft)

### 4.1 Authentication

```json
// POST /auth/register
{"email":"learner@example.com","password":"<user-provided-secret>","fullName":"Learner"}
// POST /auth/login
{"email":"learner@example.com","password":"<user-provided-secret>"}
// 200 LoginResponse (opaque session; no refresh token)
{"accessToken":"<opaque-token>","tokenType":"Bearer","expiresIn":900,"user":{"userId":"<uuid>","email":"learner@example.com","fullName":"Learner","avatarUrl":null,"status":"ACTIVE","emailVerifiedAt":"2026-11-01T00:00:00Z","lastLoginAt":"2026-11-01T00:01:00Z","roles":["LEARNER"]}}
```

Do not log password or tokens. Refresh token endpoint/policy remains [OPEN]. Current-session revocation uses V13 and [ADR-017](ADR_Auth_Session.md).

**Contract identity hiện triển khai:**

- Login: email hợp lệ, tối đa 255 ký tự, chuẩn hóa không phân biệt hoa thường; password không trống, tối đa 72 ký tự và 72 byte UTF-8. Chỉ ACTIVE được login. Sai email/password hoặc không ACTIVE cùng trả `401 INVALID_CREDENTIALS`; vượt 72 byte cũng trả mã này. JSON sai/validation lỗi trả `400 VALIDATION_ERROR`.
- Private requests dùng `Authorization: Bearer <accessToken>`; thời hạn mặc định 900 giây (đọc expiresIn thực tế). Không nhận token qua cookie/query/body. Public auth bỏ qua bearer cũ.
- Logout không có body, thu hồi phiên đang dùng trước khi trả 204; phiên khác vẫn hoạt động. Dùng lại token/logout lần hai trả `401 INVALID_TOKEN`.
- `GET /api/v1/users/me` trả chính object user trong ví dụ login, với dữ liệu và role hiện tại; không chứa password, passwordHash hay tokenHash. Actor lấy từ principal.
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

```json
// Mock test
{"sourceType":"TEST_VERSION","testVersionId":"<uuid>"}
// Dynamic practice
{"sourceType":"PRACTICE_SNAPSHOT","practice":{"partIds":[5],"questionCount":20,"difficulty":"MEDIUM"}}
// 201 AttemptSession
{"attemptId":"<uuid>","sourceType":"TEST_VERSION","status":"IN_PROGRESS","startedAt":"2026-10-28T01:00:00Z","expiresAt":"2026-10-28T03:00:00Z","questions":[{"questionVersionId":"<uuid>","order":1,"questionText":"...","choices":[{"choiceId":"<uuid>","label":"A","content":"..."}]}]}
```

Response questions không chứa `isCorrect`, explanation hoặc hidden grading data. `expiresAt` do server xác định; `testVersionId` chỉ hợp lệ với `TEST_VERSION`.

### 4.4 Autosave Answer

```json
// PUT /attempts/{attemptId}/answers/{questionVersionId}
{"selectedChoiceId":"<uuid>","clientSequence":3}
// 200 SavedAnswer
{"attemptId":"<uuid>","questionVersionId":"<uuid>","selectedChoiceId":"<uuid>","savedAt":"2026-10-28T01:03:00Z"}
```

`selectedChoiceId=null` biểu diễn bỏ chọn. Server kiểm tra choice thuộc đúng questionVersion và attempt snapshot; status IN_PROGRESS; thời gian chưa hết. `clientSequence` là [DESIGN] chưa có cột persisted revision trong DBML.

### 4.5 Submit / Result

```json
// POST /attempts/{attemptId}/submit; header Idempotency-Key: <uuid>
// 200
{"attemptId":"<uuid>","status":"SUBMITTED","submittedAt":"2026-10-28T01:45:00Z"}
// GET /attempts/{attemptId}/result, after grading
{"attemptId":"<uuid>","status":"GRADED","totalQuestions":100,"correctCount":82,"listeningCorrectCount":41,"readingCorrectCount":41,"listeningEstimatedScore":null,"readingEstimatedScore":null,"totalEstimatedScore":null,"scoreConversionTableId":null,"scoreLabel":"ESTIMATED_ONLY_IF_AVAILABLE"}
```

Submit retry returns same logical receipt. Result scores null if conversion unavailable. For full TOEIC, counts are by skill; sample numbers here are illustrative, not a formal score mapping.

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
