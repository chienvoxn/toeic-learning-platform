# ADMIN-07 — audit, health và thống kê quản trị

Ngày 2026-11-13. Nhánh `feat/web-auth-integration`; Git local
`chienvoxn <109399678+chienvoxn@users.noreply.github.com>`.
Phạm vi WEB-06 trong nhiệm vụ này chỉ thêm giao diện ADMIN-07, không phải toàn bộ
Teacher/Admin. Không triển khai ADMIN-06 hoặc ADMIN-03. Chưa stage/commit/push/merge PR.

## Nguồn yêu cầu và dependency

Đã đối chiếu AGENTS, SRS UC-15/BR-25, HLD Phần B (System sở hữu audit), LLD §2.10,
ERD `audit_logs`, API §3.9, ADMIN-01/02, health công khai và code session/assessment.
Repo không có backlog riêng liệt kê ADMIN-07; AC dùng phạm vi người dùng giao trong
phiên này, không tự coi các mục DESIGN/OPEN về system settings hoặc AI usage là AC.

ADMIN-02 `ae15cb6` đã merge nguyên vẹn qua PR #59. Fetch xác nhận
`origin/develop=2c00a6c`; fast-forward từ ae15cb6 tới merge commit này, hai tree bằng
nhau. Không tạo nhánh/worktree, không merge PR. Snapshot 10 hash file nền trước/sau
đồng bộ được giữ tại ignored `backend/target/admin07-initial.json`.

Đã kiểm các origin tracking refs trước thêm migration: cao nhất V32, chưa có V33.
**V33__admin_observability_read_permissions.sql** chỉ seed/grant cho ADMIN:
`audit:read`, `system:health:read`, `system:usage:read`. Tái sử dụng V9 audit_logs,
V1/V2 users/roles và bảng attempts hiện có; không thêm hệ audit/monitoring thứ hai,
không sửa migration đã áp dụng hoặc DB cá nhân toeic_db.

Fetch cuối vẫn ở develop **2c00a6cd802cc0315a9f23ea5a0fc774bc56a1e7**, HEAD chứa
develop mới nhất, không có migration V33 remote trùng. Demo đã migrate V32→V33 bình
thường: success=true, checksum **314073239**, public trên toeic_learning_demo:55459.
Đường DB trống được kiểm chứng trên toeic_admin07_test và toeic_admin07_regression.

## Contract và phân quyền

Base `/api/v1/admin`, opaque Bearer session. Cần actor ACTIVE có role ADMIN và
permission tương ứng ở filter **và** method service; role/permission chỉ frontend
không đủ. Session tiếp tục tải quyền DB mỗi request. ADMIN-02 catalog được bổ sung
ba permission có enforcement này, không có permission CRUD tùy ý hoặc bypass.

| Method | Endpoint | Permission | Query/response |
|---|---|---|---|
| GET | `/audit-logs` | audit:read | actor,action,entityType,entityId,from,to,page,size,sort → Page |
| GET | `/audit-logs/{auditId}` | audit:read | UUID, không query → AuditDetail |
| GET | `/health` | system:health:read | không query → Health |
| GET | `/usage-statistics` | system:usage:read | from,to → Usage |

GET me/login thêm capability boolean server `auditRead`, `healthRead`, `usageRead`
bên cạnh userManagement/rbacManagement. Menu và từng widget dùng đúng capability;
không suy quyền từ tên ADMIN. Sau 403 UI refresh hồ sơ, bỏ dữ liệu cũ và ẩn thao tác.

Ví dụ:

```text
GET /api/v1/admin/audit-logs?actor=<UUID>&action=ROLE_UPDATED&entityType=ROLE&entityId=42&page=0&size=20&sort=createdAt,desc
GET /api/v1/admin/usage-statistics?from=2026-11-01T00:00:00Z&to=2026-12-01T00:00:00Z
```

Page: `{items,totalElements,page,size}`. AuditItem:
`{auditId,actorUserId,action,entityType,entityId,createdAt}`.
Detail: `{event:AuditItem,changes:{...allowlisted fields...}}`.
`entityId` là string UUID; với ROLE lấy roleId integer từ JSON before/after, kể cả
role đã xóa. Filter integer bắt buộc entityType=ROLE, integer dương ≤2147483647.
Actor/auditId chỉ nhận UUID chuẩn. Không trả email/tên actor hoặc tên/mô tả role
nguyên văn từ lịch sử để tránh lộ payload chứa dữ liệu nhạy cảm.

page≥0, size1–100/default20; sort chỉ `createdAt,asc|desc`, `action,asc|desc`, thêm
auditId ASC làm tie-break. Data/count dùng cùng predicate trong transaction
REPEATABLE READ. Timestamp ISO có offset, chuẩn hóa UTC; from gồm mốc/to không gồm
mốc: **[from,to)**. Audit cho phép từng biên hoặc bỏ cả hai. Thống kê mặc định to là
giờ server, from=to−30 ngày; có thể chỉ truyền một biên, from phải nhỏ hơn to.
Không nhận ngày thiếu timezone, query lạ/trùng, sort SQL hoặc ID ép kiểu.

Error envelope `{code,message,traceId,details}` hiện có:400 VALIDATION_ERROR;
401 thiếu/phiên lỗi/LOCKED;403 thiếu ADMIN/permission;404 AUDIT_NOT_FOUND;
503 ADMIN_DATA_UNAVAILABLE nếu truy vấn báo cáo thất bại. Không lộ SQL/exception.
API audit chỉ GET; POST/PATCH/DELETE trả405 khi actor đã qua guard. Không xóa/sửa audit.

## Phạm vi và bảo vệ audit

Hiện có đúng sáu loại audit quản trị từ ADMIN-01/02:
USER_STATUS_CHANGED, ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED,
ROLE_PERMISSIONS_CHANGED, USER_ROLES_CHANGED. Mutation + audit giữ nguyên transaction
đã có; API đọc không thay writer hoặc thêm audit trùng. No-op role/permission/status
theo contract cũ không tạo thêm bản ghi. Vi phạm guard/ghi audit lỗi rollback cả mutation.

Chỉ trả allowlist chi tiết:

- USER_STATUS_CHANGED: from/to thuộc enum trạng thái hợp lệ.
- USER_ROLES_CHANGED: beforeRoleIds/afterRoleIds integer dương, tối đa100 phần tử đọc.
- ROLE events: beforePermissionIds/afterPermissionIds tối đa1000 và changedFields
  trong name/displayName/description. Không trả **giá trị** tên/mô tả trước/sau.
- Không serialize toàn bộ details hoặc các key lạ, password/hash/token/OTP, URL,
  connection string hay dữ liệu nested tùy ý. Dữ liệu legacy sai kiểu được bỏ an toàn.
  Role ID malformed/quá lớn không làm lỗi truy vấn hoặc lọt vào output.

Action/entity type chưa được nhận diện hiển thị OTHER, chi tiết rỗng. Filter OTHER
chọn nhóm ngoài allowlist (type ngoài USER/ROLE). Các mã action/type cụ thể hợp lệ
có thể lọc chính xác, nhưng không xuất nguyên văn mã không nhận diện. Actor không có
trong bản ghi vẫn có thể null. Đây **không phải lịch sử đầy đủ mọi hoạt động**:
không suy thêm login, logout, autosave, học bài hoặc tác vụ chưa ghi audit.

## Định nghĩa thống kê từ dữ liệu thật

Mọi aggregate chạy tại PostgreSQL, không tải danh sách user/attempt về service.
Các truy vấn trong cùng snapshot REPEATABLE READ; generatedAt UTC từ server.

| Chỉ số | Nguồn và cách tính |
|---|---|
| users.total | COUNT users hiện tại, mọi status; không lọc theo ngày đăng ký |
| users.withoutRoleCount | User hiện tại không có liên kết user_roles |
| users.byStatus | GROUP BY status, trạng thái lạ gom OTHER |
| users.byRole | LEFT JOIN roles/user_roles, COUNT DISTINCT user_id mỗi role, gồm mọi status và role0user |
| practice.startedCount | attempts nguồn PRACTICE_SNAPSHOT có started_at trong [from,to) |
| section.startedCount | attempts nguồn SECTION_SNAPSHOT có started_at trong [from,to) |
| *.submittedCount | Nguồn tương ứng, submitted_at trong khoảng và status SUBMITTED/GRADED |
| administrativeActivity.recordedCount/byAction | audit_logs trong khoảng, chỉ sáu action quản trị đã nhận diện |

User nhiều role được tính ở nhiều nhóm; **không cộng các nhóm role thành tổng user**.
Bắt đầu/nộp bài tính độc lập theo hai timestamp, không phải cùng cohort: có thể
startedCount=1/submittedCount=2. Submit lặp vẫn một row, không tăng số lượt nộp.
Snapshot tài khoản luôn là hiện tại; window chỉ áp dụng attempts/audit. Không đếm
legacy/test/full-mock source như Practice/Section, không suy expired=đã nộp, không
tính online users, realtime, thời gian truy cập, chi phí AI hoặc điểm TOEIC chính thức.

## Health và giới hạn

Giữ nguyên GET công khai `/api/health` (status/message) và `/api/health/database`
(status/database) theo contract cũ. Endpoint quản trị mới chỉ trả:

```json
{
  "checkedAt": "2026-11-13T00:00:00Z",
  "components": {
    "backend": {"status":"UP","checkedAt":"2026-11-13T00:00:00Z"},
    "database": {"status":"UP","checkedAt":"2026-11-13T00:00:00Z"},
    "ai": {"status":"NOT_CONFIGURED","checkedAt":"2026-11-13T00:00:00Z"}
  }
}
```

Ví dụ shape, không phải dữ liệu health giả. Runtime được probe khi request.
Không có hostname, DB name, path, connection string, config, exception hoặc stack.
DB dùng SELECT1, statement timeout1s, deadline2s kể cả chờ connection; executor
tối đa2 worker/không queue. Timeout/exception DOWN, quá tải worker/gián đoạn UNKNOWN.
Backend UP nghĩa là endpoint hiện đang phục vụ request này. Không có monitoring daemon.

Backend chưa tích hợp AI client nghiệp vụ. Property tùy chọn server-side
`admin.monitoring.ai-health-url` (env ADMIN_MONITORING_AI_HEALTH_URL) trống →
NOT_CONFIGURED. Nếu cấu hình, chỉ HTTP(S) không userinfo/query/fragment; không follow
redirect, connect timeout1s/deadline2s, đọc tối đa2048byte. UP chỉ khi HTTP200 và body
`{"status":"ok","service":"toeic-ai"}` theo endpoint AI hiện có; sai body/503/timeout
DOWN, URL cấu hình không hợp lệ UNKNOWN. Không trả URL hoặc gửi prompt/provider call.
Đây là **liveness service HTTP**, không xác nhận /ready, provider, model, index hay
chất lượng RAG. DB/AI probe tuần tự nên tối đa khoảng4s sau bước xác thực.

Xác thực session hiện phụ thuộc DB: nếu DB mất hoàn toàn, có thể không xác thực
được để gọi protected health. Không coi endpoint này là monitoring độc lập hạ tầng.
Đã test lỗi/timeout probe bằng unit; không tắt PostgreSQL demo hay dịch vụ của người dùng.

## UI và demo

- `/admin/audit`: lọc actor/action/type/resource/time UTC, sort/page, empty, chi tiết
  sanitized; dữ liệu cũ bị bỏ khi load/lỗi, không fallback mock.
- `/admin/overview`: health theo capability, thống kê theo khoảng UTC, loading/error
  từng widget độc lập. Lỗi usage không làm giả số0/UP hoặc che health đang đọc được.
- Dùng LearnerTemplate/menu Admin hiện có; chỉ GET cho chức năng quan sát.

Demo: http://127.0.0.1:5173; backend8080, Mailpit8025, PostgreSQL127.0.0.1:55459,
schema public, DB **toeic_learning_demo**. Admin `admin@demo.example`; mật khẩu chỉ
trong ignored backend/target/local-demo-credentials.json, không đưa vào Git/log.
Khởi động bằng backend/scripts/start-local-demo.ps1 khi cổng rảnh, dừng bằng
backend/scripts/stop-local-demo.ps1. Backend demo được restart theo PID đã ghi,
không dừng web/Mailpit hoặc dịch vụ khác. Không tác động toeic_db cá nhân.

Kịch bản khoảng5phút: loginAdmin → Vai trò và quyền tạo custom role → Audit quản trị,
lọc ROLE + ID vừa tạo, xem chi tiết → Quản lý người dùng khóa/mở Learner → lọc
USER_STATUS_CHANGED/ID Learner → Tổng quan kiểm tra health và thống kê → dùng
Learner làm Practice/Reading Section và submit → làm mới thấy số bắt đầu/nộp tăng
theo dữ liệu thật → thu hồi custom role nếu đã gán rồi xóa. Không tạo tiến độ/điểm giả.

## Kiểm chứng

| Lượt chạy mới | Kết quả |
|---|---|
| Filter/health/capability unit ban đầu |20/20 |
| AdminMonitoringIntegrationTest + AuthFlow + capability lượt đầu |49/49 |
| Targeted cuối: monitoring17 + health3 + query15 + ADMIN02 29 + ADMIN01 27 |**91/91**, failure/error/skip0 |
| Frontend npm test |**60/60**, không skip |
| npm run lint/build |exit0;9 cảnh báo nền và chunk JS>500kB được giữ, không tắt cảnh báo |
| Chromium ADMIN07 |**3/3**,53 request HTTP trực tiếp + browser requests |
| Browser kiểm riêng UI khớp từng số aggregate từ API |**1/1**,17 request HTTP trực tiếp + browser requests |
| Hồi quy browser ADMIN02 + TV1 Sprint2 + TV2 demo |**15/15**,1.8m |
| Một lượt full backend cuối, DB toeic_admin07_regression riêng |**1011/1011**,failure/error/skip0, BUILD SUCCESS,8m40s |
| HTTP/SQL demo độc lập |**5 request đạt**, đối chiếu chính xác mọi aggregate tài khoản/role/Practice/Section/audit với SQL trực tiếp |

Tất cả test ghi ở đây chạy trên DB demo/test riêng, không dùng kết quả ADMIN02 cũ
để nhận ADMIN07 đạt. Các test kiểm 401/403 và revoke trên token cũ,400filter/sort/
UTC, data/count/page/tie-break, entity ROLE đã xóa, sensitive nested payload/legacy
ID, GET-only/405/404, aggregate nhiều role/start-submit độc lập, public health giữ
contract, health exception/timeout/AI503/redirect, audit rollback. Lượt browser
dùng đăng ký/login, quản trị và làm/nộp bài thật; chỉ abort mạng ở các ca lỗi có chủ ý.

Lệnh backend: `.\mvnw.cmd -o test -Dtest=AdminMonitoringIntegrationTest,AdminHealthServiceTest,AdminReadQueryTest,RbacManagementIntegrationTest,AdminUserIntegrationTest`;
full `.\mvnw.cmd -o test`, datasource chỉ định DBtest riêng55459. Helper ignored
backend/target/admin07-run-tests.ps1 giữ tham số JVM/agent/temp Windows thực tế.
Frontend/web: npm test, npm run lint, npm run build;
`npx playwright test tests/admin.monitoring.spec.js` và hồi quy
`npx playwright test tests/admin.rbac.spec.js tests/tv1.sprint2.spec.js tests/demo.integration.spec.js`.
Log/evidence ignored backend/target/admin07-*.log, không đưa log/credential/DB dump vào Git.

976 test nền giữ nguyên số lượng +35 test ADMIN07 (query15/health3/integration17)
=1011. Các sửa source cuối đã có trong lượt full này; sau đó chỉ cập nhật tài liệu,
không chạy lại full. Frontend/E2E đều là bằng chứng chạy mới trong nhiệm vụ này.

## Manifest/hunk và giới hạn bàn giao

**33 file:19 mới,14 dùng chung.** Đối chiếu git status và lưu bản máy đọc được trong
ignored backend/target/admin07-manifest.json. Danh sách độc lập với thư mục target:

```text
backend/src/main/java/com/toeic/backend/identity/application/AdminCapabilities.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/RbacRepository.java
backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/application/AdminCapabilitiesTest.java
backend/src/main/java/com/toeic/backend/system/api/AdminMonitoringController.java
backend/src/main/java/com/toeic/backend/system/api/AdminReadExceptionHandler.java
backend/src/main/java/com/toeic/backend/system/application/AdminHealthService.java
backend/src/main/java/com/toeic/backend/system/application/AdminReadException.java
backend/src/main/java/com/toeic/backend/system/application/AdminReadQuery.java
backend/src/main/java/com/toeic/backend/system/application/AdminReadService.java
backend/src/main/java/com/toeic/backend/system/application/AdminReadViews.java
backend/src/main/java/com/toeic/backend/system/infrastructure/AdminReadRepository.java
backend/src/main/resources/db/migration/V33__admin_observability_read_permissions.sql
backend/src/test/java/com/toeic/backend/system/api/AdminMonitoringIntegrationTest.java
backend/src/test/java/com/toeic/backend/system/application/AdminHealthServiceTest.java
backend/src/test/java/com/toeic/backend/system/application/AdminReadQueryTest.java
frontend/web/src/app/routes.jsx
frontend/web/src/components/organisms/LearnerSidebar.jsx
frontend/web/src/features/auth/api/auth.mapper.js
frontend/web/src/features/admin/monitoring.api.js
frontend/web/src/features/admin/useAdminReport.js
frontend/web/src/pages/admin/AdminAuditPage.jsx
frontend/web/src/pages/admin/AdminOverviewPage.jsx
frontend/web/tests/admin.monitoring.spec.js
frontend/web/tests/monitoring.api.test.mjs
frontend/web/tests/rbac.capabilities.test.mjs
docs/ADMIN07_Audit_Health_Usage.md
docs/ADMIN02_RBAC_Management.md
docs/API_Specification.md
docs/ERD_v1.6.md
docs/LLD.md
docs/SRS.md
```

Trách nhiệm/hunk dùng chung:

- System mới: api/AdminMonitoringController, AdminReadExceptionHandler;
  application/AdminReadException, AdminReadQuery, AdminReadViews, AdminReadService,
  AdminHealthService; infrastructure/AdminReadRepository trong package system hiện có.
- Migration mới V33; ba test system: AdminMonitoringIntegrationTest,
  AdminHealthServiceTest, AdminReadQueryTest.
- Identity dùng chung: AdminCapabilities thêm3flag; IdentitySecurityConfig thêm
  guard; RbacRepository thêm3permission enforced. AdminCapabilitiesTest cập nhật DTO.
  AuthFlowIntegrationTest **chỉ thêm ba grant vào assertion ADMIN**, giữ hai import
  Problems đã có. Không đổi session hoặc writer transaction ADMIN01/02.
- Frontend mới: monitoring.api.js, useAdminReport.js, AdminAuditPage.jsx,
  AdminOverviewPage.jsx, monitoring.api.test.mjs, admin.monitoring.spec.js.
- Frontend chung: auth.mapper.js thêm3flag, rbac.capabilities.test.mjs kiểm boolean;
  routes.jsx và LearnerSidebar.jsx thêm hai trang/menu theo capability.
- Docs runtime: tài liệu này và các tham chiếu SRS/API/LLD/ERD/ADMIN02 liên quan.

Bảo toàn AGENTS.md, Problems, Postman R2, tatus và ignored settings. Các test full
chạy working tree gồm Problems cũ; chúng không thuộc manifest chức năng mới.
Đối chiếu 10 hash nền đạt; AuthFlow đảo đúng một assertion grant mới để so hash.
Diff-check phần nhiệm vụ và no-index file mới đạt, index rỗng; scan credential/
artifact/conflict marker đạt. Diff-check toàn repo vẫn có trailing whitespace
dòng1 AGENTS.md có sẵn, được giữ nguyên theo yêu cầu. Không thay file ngoài manifest.

| AC | Kết quả/bằng chứng |
|---|---|
| Audit list/detail, filter/time/sort/page/count, chỉ đọc |Đạt: integration17, browser pagination/empty/405 |
| Quyền ACTIVE ADMIN,401/403/revoke/capability |Đạt: filter/service, integration và browser thu hồi ADMIN trên token cũ |
| Không lộ payload nhạy cảm/legacy sai kiểu |Đạt: allowlist projection, nested markers/ID bounds/unknown action tests |
| Health public giữ contract, admin status/time có timeout |Đạt: DB demoUP, AI NOT_CONFIGURED; lỗi/timeout/AIHTTP kiểm ở unit |
| Aggregate DB thật, nhiều role và cửa sổ độc lập |Đạt: integration, browser counts và5HTTP đối chiếu SQL demo |
| Audit mutation nguyên tử/rollback ADMIN01/02 |Đạt: test rollback mới và hồi quy ADMIN01/02/concurrency |
| UI ADMIN07 loading/error/empty không fallback |Đạt:3ca mới +15ca hồi quy,60unit/lint/build |

WEB-06 chỉ bổ sung ADMIN-07 và RBAC đã bàn giao; chưa phải toàn bộ Teacher/Admin UI.
Giới hạn còn lại: audit không phải toàn bộ hoạt động, không online/realtime,
AI chưa cấu hình/demo không xác nhận readiness, không UI cấu hình hệ thống hoặc
quản trị học liệu/bộ đề (ADMIN06/03), không monitoring production/alerting/retention.
