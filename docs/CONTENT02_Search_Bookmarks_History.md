# CONTENT-02 — Tìm kiếm, bookmark và lịch sử học

Ngày đối chiếu: 2026-11-04. Nhánh `feat/content-search-bookmarks-history`.

## Đối chiếu trước khi triển khai

- Đã đọc AGENTS.md, SRS UC-03/BR-04, HLD phần B §20, LLD §2.2, API Specification §3.2/§6.2 và ERD v1.6. Không tìm thấy backlog hoặc AC riêng cho CONTENT-02 trong repository; các quyết định bên dưới là người dùng chốt trong phiên, không giả định là backlog/AC ngoài repo.
- Kế thừa CONTENT-01: GET `/api/v1/contents` và `/contents/{id}`, ContentQuery, ContentDtos, ContentRepository.VISIBLE, ContentAccessPolicy, error envelope và validation. Chỉ current published version đủ trạng thái/thời gian/visibility được đọc; không fallback phiên bản cũ. Guest vẫn đọc PUBLIC.
- Schema V3 đã có `bookmarks` PK(user_id,content_id), FK user/content và created_at; chưa có mapping/repository. Tái sử dụng bảng và PK để chống trùng, không tạo bảng bookmark mới.
- V3 có `learning_progress` duy nhất user/content, last_accessed_at và completed_content_version_id. Cột version mang nghĩa **đã hoàn thành**; không dùng để lưu lần truy cập và không tự đặt IN_PROGRESS/COMPLETED.
- HLD phần B yêu cầu pin version trong learning history. Trước CONTENT-02 chưa có pin phiên bản vừa truy cập; V18 bổ sung bảng lịch sử theo quyết định đã chốt.
- Nhánh hiện tại khớp origin/develop tại 1546efa (AUTH-04 đã tích hợp). Migration trên ref này đến V17; đã kiểm tra số version trước khi bổ sung V18. Không sửa migration đã áp dụng.
- Git local author/committer đã kiểm tra: `pthanh137 <sleapf137@gmail.com>`; không đổi global hoặc commit cũ. File tatus giữ nguyên và không thuộc phạm vi stage/commit.

## Quyết định sản phẩm vừa chốt ngày 2026-11-04

1. Người dùng chốt q tìm chuỗi con trên title/summary/body của đúng current published version, không phân biệt hoa/thường, không bỏ dấu. Trim/gộp whitespace, rỗng không lọc, tối đa 200 ký tự sau chuẩn hóa; %, _ và backslash literal. Kết hợp filter/sort/page cũ, predicate dữ liệu/count cùng ở DB.
2. Người dùng chốt content:bookmark:self/content:history:self chỉ cấp mặc định LEARNER, kiểm tra permission và actor tin cậy; TEACHER/ADMIN có grant hợp lệ vẫn dùng được, không hard-code role/bypass. POST bookmark 201, trùng 409; DELETE own bookmark 204/404, được bỏ bookmark khi bài ẩn hoặc không còn content:read. List/count chỉ gồm content hiện được đọc, trả current published version.
3. Người dùng chốt POST `/contents/{id}/history` không payload, 204 cho cả tạo/cập nhật; GET không ghi. Một hàng gần nhất/user/content, pin current published version được đọc và giờ server trong transaction. List/count chỉ hiện khi pin còn trùng current và còn quyền đọc; publish mới ẩn entry cũ nhưng không xóa, POST tiếp chuyển pin sang bản mới. Đây là lịch sử truy cập, không phải completion/progress, thời lượng hay analytics.

Sau fetch kiểm tra lại origin/develop vẫn tại 1546efa, migration cao nhất V17; V18 chưa được dùng trước khi bổ sung. V18 thêm history pin version, index và grant; giữ nguyên mọi migration đã áp dụng.

## Schema và transaction

Bookmark dùng bảng/PK V3 hiện hữu, insert ON CONFLICT DO NOTHING; service chuyển kết quả trùng thành 409, không bắt lỗi unique thành 500. DELETE chỉ dùng user/content, không lookup nội dung khi xóa. Bookmark list luôn chuyển current version sang Summary.

V18 tạo learning_content_history PK(user_id,content_id), FK user/content và composite FK(content_id,content_version_id) tới unique V10 để version không thể thuộc bài khác. last_accessed_at do server. Index user/time/content cho hai list và index history version. Không backfill, không đổi learning_progress hoặc cột completed_content_version_id.

Writes dùng transaction READ_COMMITTED, lấy shared row lock identity trước, lấy riêng shared lock version của pointer, rồi dùng query CONTENT-01 kiểm publication/visibility ở DB. Giữ cả khóa đến commit: publish pointer hoặc archive version phải chờ. Nếu publish đã thắng khóa, POST chờ rồi dùng pointer mới. Shared lock vẫn cho phép hai POST bookmark cạnh tranh; PK + ON CONFLICT giải quyết đúng một 201/một 409. History upsert giữ một hàng và dùng thời gian server lớn nhất đã quan sát để lần ghi đến muộn không kéo thời gian truy cập lùi; pointer được khóa nên các upsert đồng thời cùng nhìn một version.

Hai list chạy REPEATABLE_READ, data/count cùng predicate CONTENT-01 + userId; history thêm equality pinned version = current version. Không xóa entry khi ẩn/publish, không lọc bằng Java sau pagination. Member scope chỉ có khi actor tin cậy có content:read; mất quyền này chỉ làm ẩn AUTHENTICATED, PUBLIC vẫn đọc được. Mất permission endpoint trả 403 kể cả ADMIN, session sai/thiếu/khóa tài khoản trả 401.

## API và DTO

| Method / path (base /api/v1) | Payload/query | Kết quả |
|---|---|---|
| GET /contents | q + filter/page/sort CONTENT-01 | ContentPage cũ, data/count đã lọc search/quyền tại DB |
| POST /contents/{id}/bookmarks | Không body/query | 201 {contentId,createdAt}; 409 BOOKMARK_EXISTS; 404 CONTENT_NOT_FOUND khi không được đọc |
| DELETE /contents/{id}/bookmarks | Không body/query | 204 không body; 404 BOOKMARK_NOT_FOUND nếu không có own row, không tiết lộ bài |
| GET /users/me/bookmarks | page,size,sort | Page<{content:ContentSummary,createdAt}> |
| POST /contents/{id}/history | Không body/query | 204 create/update; 404 CONTENT_NOT_FOUND khi không được đọc |
| GET /users/me/content-history | page,size,sort | Page<{content:ContentSummary,lastAccessedAt}> |

Personal list envelope `{content,page,size,totalElements,totalPages}`. Default page=0,size=20,max=100; sort bookmark createdAt/contentId, history lastAccessedAt/contentId; asc|desc, default timestamp desc rồi contentId asc. Không nhận userId, q, status, version từ request cá nhân. POST/DELETE có body hoặc query, UUID sai, input page/sort sai hoặc duplicate query đều 400 VALIDATION_ERROR. Error envelope code/message/traceId/details kế thừa; thiếu/sai auth 401, thiếu permission 403. ContentId là identity, không phải versionId.

DTO allowlist dùng ContentSummary hiện hữu, không entity, body/media/storage URL/path/transcript/author/identity secret. GET detail CONTENT-01 vẫn chỉ trả metadata media được phép. ContentSummary của history mang version đã pin và còn current; không trả lại nội dung phiên bản cũ bị ẩn.

q trim/gộp Unicode whitespace; giới hạn 200 Unicode code point **sau** chuẩn hóa; rỗng -> không tìm. LIKE bind parameter, escape '!' để %, _ và backslash là literal, kể cả chuỗi dài sau escape; không ghép input vào SQL/sort. Dùng lower với collation built-in pg_c_utf8 để case mapping Unicode không phụ thuộc locale C của DB, không unaccent hoặc đổi dấu. Runtime tìm kiếm cần **PostgreSQL 17+ / UTF8**, cùng baseline CI PostgreSQL 17, đã kiểm tra trên 17.11. Tham chiếu [PostgreSQL 17 collation](https://www.postgresql.org/docs/17/collation.html) và [HQL collate/cast](https://docs.hibernate.org/orm/7.0/querylanguage/html_single/).

Không có workflow publish/Teacher CRUD mới: test thay pointer/status bằng JDBC trong DB fixture để xác minh concurrency, không tạo endpoint giả. Không progress/completion, thời lượng, analytics/streak, playlist, frontend/AI/questionbank/vocabulary.

## Trách nhiệm thành phần

| Thành phần | Trách nhiệm |
|---|---|
| ContentBookmark | Map bảng bookmarks V3, composite ID user/content, thời gian tạo và liên kết content không cascade. |
| ContentBookmarkRepository | Insert chống trùng tại PostgreSQL, delete theo user/content; fetch list/count dùng cùng ContentRepository.VISIBLE và scope user tại DB. |
| ContentBookmarkRepositoryIntegrationTest | Kiểm tra PostgreSQL thật: FK, trùng/cạnh tranh, giữ thời gian insert đầu, ownership trong query/delete, phân trang ổn định và ẩn nội dung khỏi data/count. |
| ContentReadService / ContentQuery | Kế thừa list/detail, thêm normalization q, Unicode/literal pattern và query DB chung data/count; giữ DTO/media CONTENT-01. |
| ContentPersonalService | Transaction bookmark/history, kiểm permission/ownership, khóa pin publication khi ghi; trả DTO trước khi đóng transaction list. |
| ContentAccessPolicy | Chỉ actor SessionPrincipal tin cậy, kiểm ownership tại service; tính member scope từ authority DB, không từ client. |
| ContentHistory / ContentHistoryRepository | Map lịch sử riêng, upsert pin/time và list/count đúng actor/current/readable; không ghi tiến độ. |
| PersonalContentQuery / PersonalContentDtos | Dùng lại page/parameter validation, sort allowlist ổn định; DTO không thông tin nhạy cảm. |
| PersonalContentController / ContentExceptionHandler / IdentitySecurityConfig | Routes cá nhân, principal và payload allowlist, guard API, lỗi 400/401/403/404/409 thống nhất. |

## Kiểm thử phần độc lập trước khi chốt (lịch sử)

- Database riêng **toeic_content02_test** trong PostgreSQL kiểm thử tại backend/target; không sửa/xóa dữ liệu DB cá nhân. Fixture tái lập và cleanup chỉ scope user/content kiểm thử. Fixture không phải bộ học liệu thật Part 1–7.
- Lần đầu test compile gặp suy luận overload assertEquals/TransactionTemplate; đã sửa bằng biến int rõ kiểu rồi chạy lại thành công.
- `.\mvnw.cmd test -Dtest=ContentBookmarkRepositoryIntegrationTest`: **14 test**, 0 failures/errors/skipped, BUILD SUCCESS.
- Toàn bộ `.\mvnw.cmd test`: **321 test**, 0 failures/errors/skipped, BUILD SUCCESS, gồm 307 hiện hữu và 14 repository mới. Flyway áp dụng/validate V1–V17 trên DB test riêng; không bổ sung hoặc sửa migration.
- `git diff --check`, review cả sáu file mới và kiểm tra scope/secret/log đạt. Git local author/committer đúng pthanh137 <sleapf137@gmail.com>; tatus nguyên checksum, không stage.

## Kiểm thử API sau khi chốt

Kết quả 321 bên trên chỉ là lịch sử, không chứng minh API mới. Test mới dùng MockMvc, Bearer/session/permission DB thật và Clock điều khiển. Bao phủ q Unicode/literal/blank/length/filter/count, Actor/permission/ownership, HTTP repeat codes, pagination/time order, hide/revoke, history pin/revisit và GET không ghi. Các test concurrency dùng latch + pg_blocking_pids quan sát khóa PostgreSQL thật cho pointer và trạng thái version, không giả lập publish production. Đã sửa lỗi bind q null và khóa fetch chỉ khóa identity được phát hiện ở lần chạy đầu; lower Unicode dùng pg_c_utf8 thay locale C mặc định.

**Kết quả sau khi hoàn thiện:**

- `.\mvnw.cmd test -Dtest=Content02IntegrationTest`: **51 test đạt**, 0 failures/errors/skipped.
- Sau kiểm tra cuối đầu vào NUL và GET bookmark không ghi history, toàn bộ `.\mvnw.cmd test` trên database riêng toeic_content02_test: **372 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Gồm 307 test có trước CONTENT-02 + 14 repository + 51 API mới; kết quả này thay thế bằng chứng 321 cho lần bàn giao hoàn chỉnh.
- PostgreSQL 17.11 / Java 21, Flyway validate 18 migration. V18 được thêm sau khi xác minh origin/develop chỉ tới V17; không sửa V1–V17 hoặc sửa lại V18 đã áp dụng trên DB test.
- Đã chạy git diff --check và review cả 15 file mới trong tổng 27 file thuộc nhiệm vụ; kiểm tra scope/secret/log đạt. File tatus nguyên checksum và được loại khỏi commit; author/committer pthanh137 <sleapf137@gmail.com>.
- Review bàn giao sau fetch origin: origin/develop vẫn tại 1546efa và có migration đến V17, V18 không trùng. Không tích hợp thêm hoặc sửa backend code/test so với lần chạy 372 test đạt nên không chạy lại Maven chỉ để commit. Đã xác minh credential được chọn qua GitHub /user là pthanh137; cấu hình chọn account chỉ ở local repository, không đổi global.

Không còn phần chức năng thiếu trong ba quyết định đã chốt. Không có backlog/AC CONTENT-02 riêng ngoài repo để đối chiếu thêm. Ngoài phạm vi: progress/completion/analytics, publish workflow, media download, playlist và bộ học liệu thật. Việc test dùng JDBC đổi trạng thái/pointer không chứng minh workflow publish đã được triển khai.
