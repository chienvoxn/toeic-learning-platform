# ADMIN-02 / WEB-06 — quản trị role và permission

**Cập nhật ADMIN-07 (2026-11-13):** [Audit/health/usage quản trị](ADMIN07_Audit_Health_Usage.md)
thêm ba quyền đọc có enforcement vào catalog ADMIN02 và ba capability server.
Không đổi guard, transaction, session hoặc writer audit của ADMIN01/02. Bằng chứng
976/56/18 bên dưới là lịch sử bàn giao ADMIN02, không thay kiểm chứng ADMIN07.

Ngày 2026-11-12. Nhánh `feat/web-auth-integration`. Git local:
`chienvoxn <109399678+chienvoxn@users.noreply.github.com>`.
**ADMIN-02 backend và UI RBAC đã triển khai; WEB-06 chỉ hoàn thành phần RBAC.**
Phạm vi bàn giao: ADMIN-02 và UI RBAC của WEB-06. Không mở thêm nội dung, bộ đề,
AI/RAG hoặc ADMIN-07; không tự merge PR.

## Quyết định sản phẩm vừa chốt

Đã đối chiếu AGENTS, SRS UC-15/BR-25, HLD Phần B, LLD, ERD/API, AUTH03/ADMIN01,
code session và migration. Quyết định người dùng thay các đề xuất OPEN trước đó:

- LEARNER/TEACHER/ADMIN không đổi mã hoặc xóa. Tên hiển thị, mô tả, permission
  sửa được qua API nếu giữ invariant; không đóng cứng toàn bộ permission ADMIN.
- Permission chỉ là danh mục code/migration có enforcement; không tạo/xóa tùy ý.
- Actor ACTIVE + ADMIN + rbac:manage mới quản trị RBAC. Cấp ADMIN chỉ cho user ACTIVE
  hiện có. Không tự nhận thêm effective permission qua role hoặc sửa grant.
- Luôn còn ít nhất một ACTIVE ADMIN đủ khả năng quản trị, gồm thay role/permission
  và khóa tài khoản ADMIN01, cả request đồng thời. Không tự mất khả năng quản trị.
- Custom role chỉ xóa khi không user nào được gán (kể cả LOCKED); còn user trả409.
  Xóa role cùng liên kết permission của chính role; giữ user, permission và audit.
  Audit lưu ID/mã/tên role sau khi role bị xóa.
- Grant/revoke có hiệu lực request tiếp theo trên token cũ theo AUTH03. Không thêm
  cache quyền, SUPER_ADMIN, bypass hoặc tự revoke token chỉ vì đổi RBAC.
- UI dùng capability server và refresh sau mutation; xác nhận cấp/thu hồi ADMIN và
  xóa role. Hiển thị lỗi thật, không fallback mock.

## Đồng bộ, schema và transaction

Hai commit Sprint2 1ab0347 và30fd964 đã nằm nguyên vẹn trong develop qua PR58.
Fast-forward an toàn tới **e4f5cee1b57cad86b8777b8efd7ebe232c51a3af**, tree trước/sau
bằng nhau; fetch lại vẫn SHA này, develop là ancestor HEAD. Đã kiểm các origin refs:
remote cao nhấtV31, không cóV32. Thêm **V32__rbac_management.sql**: nullable
roles.display_name, seed rbac:manage và grant ADMIN; không sửa migration đã áp dụng.
roles.name tiếp tục là authority code; API fallback displayName về name nếuNULL.

RbacService điều phối RBAC trên bảng V1/V2 hiện có; RbacRepository đọc/ghi parameter
SQL và auditV9. PermissionService dùng cùng phép hợp quyền cho session/capability;
không thêm RBAC thứ hai. AdminCapabilities tính ACTIVE + ADMIN + permission tương ứng.

RbacMutationGuard dùng pg_advisory_xact_lock(73532002) chung cho RBAC write và
ADMIN01 status; thứ tự advisory → user nếu có → role ID tăng dần → links. Sau chờ
khóa đọc lại actor DB. READ COMMITTED; mutation, guard cuối và audit cùng transaction,
vi phạm409/lỗi audit rollback toàn bộ. Lock giữ đến commit/rollback, chỉ tuần tự hóa
thao tác quản trị. SQL trực tiếp của DBA ngoài API không được guard ứng dụng bảo vệ.

Chi tiết kỹ thuật tự quyết: Admin đủ khả năng quản trị gồm ACTIVE + ADMIN + hợp
rbac:manage,user:manage,user:read:self (RBAC, trạng thái tài khoản, đọc hồ sơ/capability
UI). Không buộc tất cả nằm trên cùng role: có thể giữ qua custom role bổ sung.
Chặn actor tự mất các quyền quản trị thiết yếu đang có. Thêm role mang các quyền
actor đã có không phải tự tăng effective permission.

AdminUserService tái sử dụng khóa user login/password. Target có ADMIN cần cả
user:manage và rbac:manage; target Learner/Teacher giữ scope ADMIN01. Danh sách
ADMIN01 vẫn loại ADMIN; RBAC users gồm ADMIN và user không role. SessionAuthenticationService
vẫn tải ACTIVE/role/permission từ DB mỗi request. LOCKED chặn login/token, mở khóa
phục hồi token còn hạn/chưa revoke; đổi mật khẩu vẫn thu hồi mọi phiên như trước.

## API runtime

Base `/api/v1/admin`, Bearer session. Mọi RBAC route kiểm ACTIVE+ADMIN+rbac:manage
ở filter/service. Role/permission ID **integer**, user ID UUID.

| Method | Path | Payload/kết quả |
|---|---|---|
| GET | /roles?q=&page=0&size=20 | Page<Role> |
| GET | /roles/{id} | Role |
| POST | /roles | {name,displayName?,description?} →201 Role |
| PATCH | /roles/{id} | subset {name?,displayName?,description?} →Role |
| DELETE | /roles/{id} | Không body/query →204 |
| GET | /permissions | Permission[] readonly |
| PUT | /roles/{id}/permissions | {permissionIds:[1,2]} thay toàn bộ grant →Role |
| GET | /rbac/users?q=&page=0&size=20 | Page<UserRoles>, gồm ADMIN |
| GET | /users/{uuid}/roles | UserRoles |
| PUT | /users/{uuid}/roles | {roleIds:[1,2]} thay toàn bộ role →UserRoles |
| PATCH | /users/{uuid}/status | ADMIN01 {status:ACTIVE hoặc LOCKED}, thêm guard ADMIN02 |

Ví dụ tạo role: `{"name":"COURSE_REVIEWER","displayName":"Người kiểm tra","description":null}`.
Role trả `{roleId,name,displayName,description,permissionIds,userCount,systemRole,
administrativeRole,canRename,canDelete}`. Permission: `{permissionId,code,description}`.
UserRoles: `{userId,email,fullName,status,roles:[{roleId,name,displayName}],canChangeStatus}`.
Page: `{items,totalElements,page,size}`. ID ví dụ chỉ minh họa, phải lấy catalog thực tế.
GETme/login thêm `adminCapabilities:{userManagement,rbacManagement}` do server tính.

Validation: code ASCII2–50 strip/uppercase `[A-Z][A-Z0-9_]{1,49}`, không prefix ROLE_;
trùng không phân biệt hoa thường409. displayName nullable/nonblank nếu có giá trị,
max150; description nullable/max1000. PATCH omitted giữ nguyên/null xóa field nullable.
q literal/max200, page>=0,size1–100/default20. Từ chối query/field lạ/trùng. ID JSON
integer dương/distinct, max100role/1000permission, không ép kiểu string/boolean/float.
[] được nhận nhưng qua invariant. PUT không đổi dữ liệu không thêm audit.

Error dùng AuthError hiện có; 400 validation,401 thiếu/phiên lỗi,403 thiếu quyền
hoặc actor mất quyền sau chờ khóa,404 thiếu role/user/permission hoặc chưa enforcement.
409: ROLE_CODE_CONFLICT, SYSTEM_ROLE_PROTECTED, ROLE_IN_USE, USER_STATUS_CONFLICT,
LAST_RBAC_ADMIN, SELF_LOCKOUT, SELF_PRIVILEGE_ESCALATION. Persistence500
RBAC_PERSISTENCE_ERROR không lộSQL. Catalog không có POST/DELETE (405).

## UI/demo

**http://127.0.0.1:5173/admin/roles**, dùng layout/menu Admin hiện có. Tabs Vai trò và
Gán vai trò tài khoản: search/paging server, form metadata, checkbox permission/role,
loading/saving/error409 và confirm. Metadata systemRole/canRename/canDelete/
administrativeRole/canChangeStatus và capability từ server, không suy grant từ tên role.
Refresh capability ở nền tránh mất form; tải lại server sau mutation;403 bỏ dữ liệu cũ.
Target Admin được khóa/mở khóa nếu server cho phép; backend luôn kiểm invariant.

DB demo **toeic_learning_demo**, PostgreSQL **127.0.0.1:55459/public**, backend8080,
web5173, Mailpit8025. V32 success=true, checksum **-1536746678**, nâng cấp bình thường
V31→32. DB trống được migrate qua test riêng toeic_admin02_test/toeic_admin02_regression.
Không kết nối/nâng cấp/reset/repair toeic_db cá nhân. Dịch vụ demo giữ chạy sau bàn giao.

Khởi động khi cổng rảnh: `powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-local-demo.ps1`.
Dừng bằng script `backend/scripts/stop-local-demo.ps1` đã có. Admin `admin@demo.example`,
mật khẩu ngẫu nhiên chỉ trong ignored backend/target/local-demo-credentials.json,
không đưa vàoGit/log. Mailpit http://127.0.0.1:8025 cho OTP reset; RBAC không cầnOTP.

Demo5phút: loginAdmin → Vai trò và quyền → tạo/sửa customrole → gán content:read →
tìmLearner, gánrole → cấpADMIN (xem/hủy confirm trước khi đồng ý) → token user gọi
quản trị200 → thu hồiADMIN, request kế tiếp403 → thu hồi customrole → xóa role.
Tự thêm practice:manage:self vào ADMIN sẽ409, giữ nguyên dữ liệu/audit.

## Kiểm chứng thực tế

| Chạy mới | Kết quả |
|---|---|
| RbacInputTest + AdminCapabilitiesTest |20/20 |
| AdminUserIntegrationTest + AuthFlowIntegrationTest lượt liên quan |58/58 |
| RbacManagementIntegrationTest cuối |29/29 |
| Full backend trên DB toeic_admin02_regression trống, **trước sửa canChangeStatus** |**976/976**, failure/error/skip0, BUILD SUCCESS,12m16s |
| Sau sửa canChangeStatus: RbacManagementIntegrationTest + AdminUserIntegrationTest |**56/56**, failure/error/skip0, BUILD SUCCESS,44.9s |
| Frontend npm test cuối |**56/56**, không skip |
| npm run lint |exit0,9warning ở các vị trí nền hiện có, không trong trang/API RBAC mới |
| npm run build |exit0, warning JSbundle>500kB, không đổi ngưỡng/tắt cảnh báo |
| Chromium ADMIN02 CRUD/gán/thu hồi/confirm/delete + system/self-escalation/Learnerguard |2/2 |
| Live HTTP sau sửa canChangeStatus: token cũ nhận/mất quyền, tạo Reading Section, capability user không role |**18 request đạt**, 1/1 ca smoke |
| Chromium tv1.sprint2.spec.js + demo.integration.spec.js |**12/12**,1.6m |

LượtADMIN02 đầu2caUI pass,1HTTP fail do test gọi nhầm /section-attempts (404).
Sửa test sang /attempts với SECTION_SNAPSHOT hiện có, chạy lại đúng ca đạt
(14 request trực tiếp trong lượt sửa URL; sau sửa canChangeStatus chạy lại với
18 request đạt, browser API được kiểm riêng). Không tạo API trùng/nới quyền.
Targeted đầu có3lỗi fixture/quan sát: Unicodequery doubleencode, CHECKaudit đụng
setup, snapshot pg_stat_activity cache trong transaction. Sửa test rồi29/29; không
xóa test/giảm assertion. Lint9warning nằm ở games/content/vocabulary/attempt hook và
fastrefresh routes có sẵn; build vẫn có cảnh báo chunk lớn.

Test bao phủ400/401/403/404/409, CRUD, system metadata/permission, delete role đang dùng,
audit identity sau xóa, self-escalation trực tiếp/gián tiếp, grantADMIN chỉACTIVE,
next-request rights, lastAdmin/self-lockout, lock/unlockAdmin, auditfail rollback
create/edit/links/delete. Hai race thật chờ advisorylock PostgreSQL: haiAdmin gỡ
ADMIN của nhau; ADMIN01 khóa actor cạnh tranh thu hồi user:manage. Mỗi race chỉ1
mutation commit, loser403,1audit, còn1manager. Giữ test rollbackstatus/login cũ.

Full suite chạy working tree gồm các sửa Problems local; không coi chúng thuộc ADMIN-02.
927 test Sprint 2 giữ nguyên số lượng +20 unit +29 integration =976. Lượt full này
chạy **trước** sửa cờ DTO canChangeStatus cho tài khoản không còn role hệ thống để
khớp scope ADMIN-01. Sau sửa chỉ chạy hai lớp liên quan (56/56) và 18 request HTTP;
**chưa chạy full suite trên code sau sửa**. Bàn giao không lặp full local; CI kiểm
chứng toàn bộ nguồn commit, không có các sửa Problems chưa stage. Các sửa ngoài
commit chỉ dọn import hoặc thay deprecated assertion bằng API tương đương được hỗ trợ,
không phải dependency của code ADMIN-02. Full chậm do thread starvation/clock leap;
đã kiểm log/process và suite kết thúc. Log ignored backend/target/admin02-*.log.

Backend `.\mvnw.cmd -o test '-Dtest=RbacManagementIntegrationTest,AdminUserIntegrationTest,AuthFlowIntegrationTest,RbacInputTest,AdminCapabilitiesTest'`;
full `.\mvnw.cmd -o test`, set datasource DBtest55459 riêng/username q03_main_test.
Helper ignored backend/target/admin02-run-tests.ps1 giữ tham số agent/temp/memory JVM
Windows thực tế. Frontend: npm test, npm run lint, npm run build;
`npx playwright test tests/admin.rbac.spec.js`;
`npx playwright test tests/tv1.sprint2.spec.js tests/demo.integration.spec.js`.

## Manifest/hunk

**37 file: 21 mới, 16 file chung**, tách khỏi 9 file dirty nền và ignored settings.

Prefix Java production: backend/src/main/java/com/toeic/backend/identity/.

File mới:

- api/AdminRbacController.java, RbacExceptionHandler.java, RbacRequest.java.
- application/AdminCapabilities.java, RbacException.java, RbacMutationGuard.java,
  RbacQuery.java, RbacService.java, RbacViews.java, RoleEdit.java.
- infrastructure/RbacRepository.java; backend/src/main/resources/db/migration/V32__rbac_management.sql.
- backend/src/test/java/com/toeic/backend/identity/api/RbacInputTest.java,
  RbacManagementIntegrationTest.java; application/AdminCapabilitiesTest.java.
- frontend/web/src/features/admin/rbac.api.js; src/pages/admin/AdminRolesPage.jsx.
- frontend/web/tests/admin.rbac.spec.js, rbac.api.test.mjs, rbac.capabilities.test.mjs.
- docs/ADMIN02_RBAC_Management.md.

File dùng chung:

- api/AdminUserExceptionHandler.java: lỗi guard409; application/AdminUserService.java:
  khóa/invariant chung và targetAdmin có điều kiện.
- application/PermissionService.java, UserProfile.java: hợp quyền/capability.
- infrastructure/IdentitySecurityConfig.java: guardRBAC trước ruleADMIN01.
- test api/AdminUserIntegrationTest.java: contractAdmin404 cũ →self409/other200/missing404,
  kiểm đúng2auditstatus, không giảm assertion.
- test api/AuthFlowIntegrationTest.java: **chỉ1hunkADMIN02** thêm rbac:manage vào expected
  quyềnADMIN; hai import đã dọn từ Problems vẫn giữ nguyên ngoài nhiệm vụ.
- frontend src/app/routes.jsx, components/organisms/LearnerSidebar.jsx: route/menucapability;
  features/auth/api/auth.mapper.js: booleanserver; AuthProvider.jsx: refreshnền.
- docs/API_Specification.md, SRS.md, LLD.md, ERD_v1.6.md, AUTH03_RBAC.md: runtime link,
  display_name, phân biệt ghi chú DESIGN/lịch sử với contract vừa chốt.

Ngoài nhiệm vụ: AGENTS.md, PostmanR2, tatus, ignored .vscode/settings.json và Problems
ở GoogleAuthIntegrationTest, PasswordChangeIntegrationTest, RegistrationFlowIntegrationTest,
SessionIssuerTest, QuestionAuthoringConcurrencyTest cùng hai import AuthFlow giữ nguyên.
Đối chiếu 10 hash nền đạt (AuthFlow đảo đúng một hunk assertion mới).
Scoped git diff --check và no-index check file mới đạt. Diff-check toàn working tree
vẫn báo trailing whitespace dòng 1 AGENTS.md có sẵn; không sửa file được bảo toàn.
Trước bàn giao index rỗng; scan secret/artifact/conflict marker đạt. Snapshot hash
ignored backend/target/admin02-initial.json; AuthFlow có bản trước hunk mới.
Không đưa credential, DBdump, log, backup hoặc artifact vào source.

## AC và giới hạn

ADMIN02 CRUD/catalog/grant/revoke/userroles, invariant ADMIN01, next-request quyền,
auditrollback/concurrency, backend/UI demo đạt bằng chứng trên. **WEB06 chỉRBAC**;
chưa làm auditexplorer, quản trị nội dung/bộđề, AI/RAG, rolehierarchy, permissionCRUD,
protectedmedia hoặc tự động chốt EXAM03. Không tự gán phần trăm toàn Sprint3.
