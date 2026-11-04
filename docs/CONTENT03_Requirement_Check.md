# CONTENT-03 — Teacher CRUD và binding media

Ngày quyết định sản phẩm: 2026-11-04. Nhánh feat/content-03. Author/committer: pthanh137 <sleapf137@gmail.com>.

## Nguồn yêu cầu

> Epic: Teacher. Task: Teacher CRUD grammar/vocab/listening/reading/strategies/docs. Owner: TV2. Depends: CONTENT-01; AUTH-03.

Nguồn là ảnh bảng Sprint 2 do người dùng cung cấp và xác nhận lại bằng văn bản trong phiên; repo không có ảnh/backlog/AC chi tiết để đối chiếu trực tiếp. Các chính sách CRUD và media trong tài liệu này là **quyết định sản phẩm vừa được người dùng chốt**, không phải Acceptance Criteria tìm thấy trong backlog. VOCAB là học liệu, không phải Deck/Flashcard/SRS TV3.

Đã đối chiếu AGENTS.md, SRS UC-12/BR-04/AC-04/BR-24/AC-24, API Specification §3.2/§5, ERD/DBML, HLD ưu tiên phần B và LLD. Trạng thái DESIGN/OPEN lịch sử không tự trở thành yêu cầu đã triển khai.

## Quyền và vòng đời content

- content:manage:self mặc định TEACHER/ADMIN; content:manage:any chỉ ADMIN. API và service kiểm permission + actor từ SessionPrincipal; DB scope owner/any nhất quán cho data/count/detail. Không role bypass, không nhận owner/creator/ID/status/visibility/pointer từ payload.
- Thiếu/sai xác thực 401; thiếu permission endpoint 403; content không tồn tại/ngoài scope 404 CONTENT_NOT_FOUND. Actor tạo là owner content và creator version; không có chuyển ownership.
- POST tạo content DRAFT/PRIVATE, pointer null, version 1 DRAFT. PATCH chỉ sửa version DRAFT chưa có published_at/archived_at; nếu chưa có working draft thì tạo từ đúng published pointer, số max+1, giữ nguyên published text/assets/pointer.
- Nhiều draft legacy, pointer bất nhất hoặc thiếu pointer khi cần clone: PATCH 409 CONTENT_STATE_CONFLICT, không chọn version mới nhất hoặc sửa dữ liệu cũ. GET quản lý vẫn xem được mọi version đúng parent; nhiều draft thì không tự chỉ định workingDraftVersionId.
- Category/difficulty ở parent chưa version hóa. Chỉ sửa khi content DRAFT. Payload hợp lệ có categoryCode/difficulty trên PUBLISHED (kể cả difficulty null hoặc giá trị bằng hiện tại) trả 409 CONTENT_METADATA_IMMUTABLE cho cả PATCH; không sửa text/timestamp hoặc tạo draft một phần. PATCH chỉ text vẫn tạo/reuse draft và kiểm quyền toàn bộ media còn giữ. Sai kiểu/giá trị đầu vào vẫn 400.
- DELETE archive content, 204 idempotent trong scope; giữ versions, published pointer, bookmark/history/assets. Archived đọc quản lý được nhưng không sửa/tạo draft. Learner read/search/bookmark/history và count đều bị ẩn qua predicate content.status, không dựa riêng pointer.

## Schema media — V20

Trước V20, V3/V15 chỉ có metadata/visibility, không chứng minh owner/creator/grant media. Sau khi được sản phẩm chốt, V20__media_ownership_and_reuse_grants.sql bổ sung:

| Bảng | Dữ liệu và ràng buộc |
|---|---|
| media_resources | owner_id UUID nullable, FK users(user_id), index owner. Không thêm created_by. Mapping owner không updatable, chưa có API chuyển owner. |
| media_reuse_grants | media_id UUID FK media_resources(resource_id), user_id UUID FK users; PK(media_id,user_id); granted_by UUID FK users, granted_at timestamptz NOT NULL; revoked_by UUID FK users và revoked_at nullable. |

revoked_at và revoked_by phải cùng null hoặc cùng có giá trị; revoked_at không trước granted_at. Grant hiệu lực khi revoked_at null. Các trường audit lưu sự kiện cấp/thu hồi của hàng grant hiện tại; chưa có workflow quản trị hoặc API grant/revoke/regrant. Fixture kiểm thử tạo các hàng này trực tiếp.

Migration không UPDATE owner hoặc INSERT grant: media cũ giữ owner_id null; không suy ownership từ content author/version creator/link, không backfill hoặc cấp hàng loạt. Link published có sẵn không đổi khi nâng schema. Ownership/grant trong fixtures chỉ là dữ liệu kiểm thử khai báo rõ, không phải backfill sản phẩm.

## Chính sách dùng media

Actor phải có permission quản lý content và đồng thời là owner media hoặc có grant chưa thu hồi. Media phải tồn tại và đọc metadata được theo policy hiện hữu: PUBLIC, hoặc AUTHENTICATED khi actor có content:read; PRIVATE luôn bị từ chối kể cả owner/grantee. content:manage:any không bỏ qua các điều kiện này. Media legacy owner null nhưng có active grant được dùng; legacy không owner/grant không được gắn/clone.

Grant chỉ cho authoring/reuse. Thu hồi không xóa link published, không thay đổi quyền xem Learner. Quyền xem vẫn chỉ theo visibility/publication/permission đọc hiện có; đổi visibility PRIVATE vẫn làm metadata bị ẩn theo CONTENT-01/02.

## API runtime và DTO

Prefix /api/v1; không đổi contract đọc CONTENT-01/02.

| Method | Path | Kết quả |
|---|---|---|
| POST | /teacher/contents | 201 Detail; content/version/assets cùng transaction |
| GET | /teacher/contents | 200 Page<Summary>; DB own/any, draft/published/archived trong scope |
| GET | /teacher/contents/{id} | 200 Detail; versions theo version_number tăng dần |
| PATCH | /teacher/contents/{id} | 200 Detail; text/metadata DRAFT và binding media theo policy |
| DELETE | /teacher/contents/{id} | 204 archive idempotent; không hard-delete |

Allowlist POST/PATCH: title,summary,body,categoryCode,difficulty,assets. POST bắt buộc title/body/categoryCode. PATCH tối thiểu một trường; omitted giữ nguyên. Title chuỗi không trắng ≤255 ký tự Java trước strip; body chuỗi không null, cho phép draft rỗng; summary chuỗi/null; difficulty chuỗi không trắng ≤20 hoặc null; NUL bị từ chối. CategoryCode chỉ GRAMMAR/VOCAB/LISTENING/READING/STRATEGIES/DOCS. Title/body/categoryCode null sai; summary/difficulty null xóa.

assets là mảng object một trường mediaId UUID: ví dụ {"assets":[{"mediaId":"40000000-0000-0000-0000-000000000001"}]}. Không nhận URL/path, field lạ, sai kiểu, ID trùng hoặc UUID không hợp lệ. Thứ tự mảng thành display_order 1..N. assets omitted giữ nguyên; [] xóa hết link draft; null trả 400. DTO lồng assets cũng dùng allowlist, không nhận owner hoặc displayOrder tùy ý.

List: categoryCode/status(DRAFT/PUBLISHED/ARCHIVED), page=0,size=20,max=100; sort createdAt/updatedAt/contentId/status với asc/desc, mặc định updatedAt,desc và contentId asc phụ. Unknown/duplicate query sai trả 400. Các endpoint khác không query; DELETE không body. Unknown/duplicate JSON fields trả 400 VALIDATION_ERROR.

Summary: contentId,category(categoryId/code/name),title từ draft duy nhất hoặc đúng pointer,difficulty,status/visibility content,publishedVersionId,workingDraftVersionId,createdAt/updatedAt. Không body/media/identity nhạy cảm. Detail {content:Summary,versions:[Version]}; Version có ID/number/status/text/timestamps và media metadata đúng version đã lọc. Page {content,page,size,totalElements,totalPages}. DTO media đọc giữ tên resourceId theo contract cũ; mediaId của request tham chiếu media_resources.resource_id. Không trả owner/grant/storage_url/URL/path/transcript hoặc JPA entity.

## Attach, giữ lại và clone

- Validate toàn bộ trước khi áp dụng text/metadata/link. ID gắn mới thiếu/không được phép dùng: 404 MEDIA_NOT_FOUND, cùng code/message cho mọi nguyên nhân, không metadata/ID chi tiết.
- Với draft đã có: assets omitted kiểm lại tất cả link còn giữ. Explicit assets kiểm toàn bộ tập đích; link cũ bị loại không cần quyền reuse nên có thể bỏ media đã bị thu hồi/PRIVATE. Media giữ lại không còn quyền: 409 CONTENT_MEDIA_REUSE_CONFLICT, rollback cả PATCH.
- Khi chưa có draft: kiểm mọi link của đúng published pointer, kể cả link PRIVATE hoặc owner/grant không còn hợp lệ. Một link không dùng được trả 409 CONTENT_MEDIA_REUSE_CONFLICT và rollback toàn bộ; không tạo draft thiếu media. assets=[]/thay tập đích không bỏ qua bước kiểm quyền clone nguồn.
- Clone hợp lệ giữ resource và display_order nguồn khi assets omitted; explicit assets thay tập đích theo mảng sau khi nguồn được kiểm đầy đủ. Thay/bỏ link chỉ ở draft; published text/assets/pointer không bị sửa.
- Tất cả lỗi rollback content/version/assets/timestamp. Không có endpoint publish, workflow upload, cấp/thu hồi grant hoặc chuyển ownership trong nhiệm vụ này.

## Transaction và thứ tự khóa

Existing content writes: parent content FOR UPDATE → versions theo version_number FOR UPDATE → toàn bộ media cần dùng theo UUID lexicographic (giống PostgreSQL UUID order) FOR SHARE → grant rows của actor theo cùng media order FOR SHARE. Clone khóa union nguồn + tập đích; draft hiện có chỉ cần tập đích nên bỏ link không còn quyền vẫn được. Mọi media được khóa trước mọi grant; không khóa theo thứ tự payload. Read grant kể cả row revoked, không dùng cache quyền.

Khóa SHARE media ngăn owner/visibility/delete cạnh tranh; khóa SHARE grant ngăn revoke/update/delete cạnh tranh tới commit. READ_COMMITTED đọc trạng thái mới sau khi chờ writer. Grant absent không thể cho phép reuse nhầm; grant được cấp sau check có thể retry request sau. Writer chỉ đổi grant hoặc chỉ visibility được serialize bởi khóa row tương ứng; writer cần cả hai phải giữ thứ tự media→grant, không lấy content lock sau khi giữ media/grant.

CREATE chưa có shared parent/version: validate media trong transaction trước khi insert aggregate mới; không lấy lock một parent tồn tại sau media. Update validate xong mới đổi metadata/text và link; version mới flush trước insert link để FK hợp lệ. CONTENT-02 tiếp tục khóa content→version, tương thích archive/authoring. List/detail quản lý đọc REPEATABLE_READ; đọc Learner vẫn dùng predicate hiện có.

## Trách nhiệm service và persistence

| Thành phần | Trách nhiệm |
|---|---|
| TeacherContentController / ContentEditRequest / ContentEdit | Actor tin cậy, allowlist JSON kể cả assets lồng nhau, presence/null/types/UUID/duplicate validation; không nhận version/owner tùy ý. |
| ContentManagementService | Permission/ownership, lifecycle, metadata rule, khóa parent/version, validate tập assets rồi tạo/reuse draft, ghi links và DTO trong cùng transaction. |
| ContentMediaReuseService | Transaction MANDATORY và permission+actor; lock toàn bộ media/grant, check owner hoặc active grant + visibility/read permission, phân loại lỗi clone/retained 409 và new ID 404. Không Admin bypass. |
| MediaReuseRepository | JDBC snapshot quyền trực tiếp DB bằng FOR SHARE, không cache entity/quyền; không mutation grant/owner API. |
| ContentAssetRepository | Đọc mọi link nguồn kể cả PRIVATE, giữ thứ tự; replace link chỉ version draft do service xác định, SQL bind parameters, FK/PK bảo vệ dữ liệu. |
| ContentRepository / ContentVersionRepository | Scope own/any ở DB cho data/count/detail, parent/version locks; unique version_number và composite pointer FK hiện hữu. |
| LearningContent / LearningContentVersion / MediaResource | Owner/creator/timestamps content, draft guard bất biến published, archive parent; media owner nullable chỉ mapping từ V20. |
| ContentMediaRepository / ContentDtos | Metadata theo đúng version và visibility, không storage/owner/grant; giữ contract CONTENT-01/02. |
| ContentReadService | Đọc/search đúng current published, DB filters/count, REPEATABLE_READ. |
| ContentPersonalService | Own bookmark/history, pin version và content→version locks; archive ẩn data/count nhưng cho xóa own bookmark. |
| ContentAccessPolicy / IdentitySecurityConfig | Permission từ Bearer principal/DB mỗi request, API và service cùng bảo vệ, không role bypass. |

## Migration và nền nhánh

Fetch origin trước khi thêm V20: origin/develop 4bdccfb, migration tới V18; chứa CONTENT-02 ead942c (merge PR #34), CONTENT-01/AUTH-03. Feature đã có dependency; không merge nhánh CONTENT-02 hoặc thay đổi AI/vocabulary/mobile mới không cần thiết. V19 local đã áp dụng và giữ nguyên; V20 chưa trùng version origin. Không sửa V1–V19, không reset/xóa DB hoặc backfill quyền. V20 không thêm permission quản trị grant hoặc upload.

## Kiểm thử

466 test trước chỉ là baseline text/category, chưa chứng minh binding media. Test dùng content-read/content-bookmark/content03-manager SQL fixture và factory grant/owner/revoke trong TeacherContentIntegrationTest; cleanup chỉ hai actor và media fixture. Không phải bộ học liệu thật sáu nhóm/Part 1–7. Fixture ghi trực tiếp DB vì chưa có API cấp/thu hồi/owner.

Test mới bao phủ owner/grant active/revoked/foreign/PRIVATE/missing/legacy/Admin/no content:read; create/attach/replace/remove, omitted/null/[]/duplicate/unknown nested fields; clone đầy đủ đúng quyền và thứ tự, rollback khi một nguồn sai kể cả assets=[]; retained revoke chặn text nhưng cho bỏ; mixed metadata/text/assets rollback; published assets/visibility Learner không phụ thuộc grant; audit/FK/PK/check constraints. Tám test DB khóa thực kiểm cả hai thứ tự attach/clone cạnh tranh revoke/visibility, không chỉ dựa vào sleep.

PostgreSQL 17.11, DB riêng toeic_content03_test cổng 55439, không reset. Lần focused đầu thiếu native memory khi compile, chưa chạy test; PostgreSQL kiểm thử cũng đã dừng vì thiếu tài nguyên. Khởi động lại cùng cluster với shared_buffers=32MB và chạy thành công với JVM C1/heap nhỏ hơn, không đổi cấu hình Maven trong repository hoặc xóa dữ liệu.

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55439/toeic_content03_test'
$env:SPRING_DATASOURCE_USERNAME='auth_rbac_test'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:DEBUG='false'
$env:MAVEN_OPTS='-Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
.\mvnw.cmd test '-DargLine=-Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m'
```

**Kết quả thực tế:** focused `-Dtest=TeacherContentIntegrationTest,ContentAuthoringPersistenceIntegrationTest` đạt **136 test, 0 failure/error/skipped** (129 Teacher + 7 persistence). Sau đó chạy toàn bộ bằng lệnh trên đạt **508 test, 0 failure, 0 error, 0 skipped, BUILD SUCCESS**, thời gian 06:59. Bao gồm 372 test nền + 7 persistence + 129 Teacher; tăng 42 trường hợp media so với baseline 466. Không điều chỉnh số test cho khớp dự kiến và không dùng baseline thay cho bằng chứng phần mới.

Log ignored: backend/target/content03-media-focused-retry.log và backend/target/content03-media-full-tests.log. Flyway áp dụng V20 trên DB riêng và validate thành công toàn bộ 20 migration, không sửa checksum/script V1–V19. Code không đổi sau lần chạy thành công; chỉ cập nhật tài liệu kết quả. Các test hiện hữu identity/content/questionbank/vocabulary tiếp tục đạt trên nhánh hiện tại; không bao gồm thay đổi AI/vocabulary/mobile mới trên origin/develop chưa tích hợp.

Đã review 31 file nhiệm vụ gồm cả file mới; git diff --check và whitespace check file chưa tracked đạt, không phát hiện secret/log/build artifact trong file nhiệm vụ. Tại thời điểm hoàn tất kiểm thử, index rỗng, author/committer đúng, checksum tatus không đổi. Fixture user/grant đã được dọn sạch và cluster riêng được dừng, không reset/drop DB cá nhân. Review bàn giao không đổi code sau bằng chứng 508 test; chỉ cập nhật ghi chú tài liệu.

## Giới hạn còn lại và Git

Binding media đã triển khai trong Teacher CRUD. **Chưa có upload/download media, API cấp/thu hồi/regrant, chuyển ownership hoặc luồng quản trị quyền media trong sản phẩm.** Dữ liệu owner/grant chỉ có bằng chứng fixture; media cũ chưa có owner/grant vẫn đọc được theo visibility nhưng không được gắn/clone mới. Không thêm created_by media. Publish/unpublish/restore, versioning category/difficulty, progress/completion/analytics ngoài phạm vi.

Giữ mọi thay đổi sẵn có; không sửa frontend/AI/vocabulary/questionbank. Author/committer pthanh137 <sleapf137@gmail.com>. tatus giữ SHA-256 C5DC158F6231925CDEABB6CDF81705CF9B06AC664E092F470DDF26CED1643B93 và bị loại khỏi commit. Bàn giao trên nhánh feature; không commit trực tiếp develop/master hoặc tự merge PR.

## Danh sách file nhiệm vụ

- `backend/src/main/java/com/toeic/backend/content/api/ContentEditRequest.java`
- `backend/src/main/java/com/toeic/backend/content/api/TeacherContentController.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentEdit.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentManagementDtos.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentManagementQuery.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentManagementService.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentMediaReuseService.java`
- `backend/src/main/java/com/toeic/backend/content/infrastructure/ContentAssetRepository.java`
- `backend/src/main/java/com/toeic/backend/content/infrastructure/ContentCategoryRepository.java`
- `backend/src/main/java/com/toeic/backend/content/infrastructure/ContentVersionRepository.java`
- `backend/src/main/java/com/toeic/backend/content/infrastructure/MediaReuseRepository.java`
- `backend/src/main/resources/db/migration/V19__content_authoring_permissions_and_categories.sql`
- `backend/src/main/resources/db/migration/V20__media_ownership_and_reuse_grants.sql`
- `backend/src/test/java/com/toeic/backend/content/api/TeacherContentIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/content/infrastructure/ContentAuthoringPersistenceIntegrationTest.java`
- `backend/src/test/resources/fixtures/content03-domain-cleanup.sql`
- `backend/src/test/resources/fixtures/content03-manager-cleanup.sql`
- `backend/src/test/resources/fixtures/content03-manager.sql`
- `docs/CONTENT03_Requirement_Check.md`
- `backend/src/main/java/com/toeic/backend/content/api/ContentExceptionHandler.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentAccessPolicy.java`
- `backend/src/main/java/com/toeic/backend/content/application/ContentException.java`
- `backend/src/main/java/com/toeic/backend/content/domain/LearningContent.java`
- `backend/src/main/java/com/toeic/backend/content/domain/LearningContentVersion.java`
- `backend/src/main/java/com/toeic/backend/content/domain/MediaResource.java`
- `backend/src/main/java/com/toeic/backend/content/infrastructure/ContentRepository.java`
- `backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java`
- `backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java`
- `docs/API_Specification.md`
- `docs/ERD_v1.6.md`
- `docs/LLD.md`

Identity chỉ có matcher content và assertion ma trận permission theo V19 từ bước trước; không đổi login/logout/session/profile/password.
