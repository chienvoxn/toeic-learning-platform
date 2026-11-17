# TV1 Sprint 2 — AUTH-04, ADMIN-01, QA-02

Ngày đối chiếu: 2026-11-12. Nhánh `feat/web-auth-integration`, HEAD/develop
`a4a27b1ac499478f6318e578bed846fe6276cc5a`. Author/committer hiện hành
`chienvoxn <109399678+chienvoxn@users.noreply.github.com>`. Chưa stage/commit/push/merge PR.

## Nguồn, phần có sẵn và AC

Đọc AGENTS, SRS UC-01/02/15, HLD ưu tiên Phần B, LLD Identity, ERD users/roles/
permissions/audit_logs/learning_profiles, API Specification, AUTH03/04 và code/test
hiện hành. Không tìm thấy backlog/AC Sprint2 độc lập chi tiết ngoài các task và yêu
cầu người dùng. Bảng dưới đối chiếu đúng yêu cầu ngày 2026-11-12 và contract sản phẩm
đã chốt trong tài liệu, không tự lấy DESIGN/OPEN làm quyền đã được cấp.

| AC trong phạm vi yêu cầu | Trước nhiệm vụ | Sau triển khai / bằng chứng |
|---|---|---|
| AUTH-04 đọc/sửa hồ sơ chính mình | GET/PATCH me + UI có sẵn, backend permission/ownership/allowlist đã có | Tái sử dụng, kiểm UI đổi tên/reload; payload userId/trường hệ thống bị từ chối |
| Mục tiêu TOEIC | Backend/UI đã hỗ trợ integer10–990/bội5, null xóa, omitted giữ | UI ghi rõ Listening+Reading tổng và không phải điểm quy đổi; kiểm750/reload/xóa, invalid400 |
| Lưu hồ sơ không thay đổi | UI gửi PATCH rỗng và nhận400 | Không gửi PATCH khi không có thay đổi, có thông báo đúng |
| Đổi mật khẩu xác minh cũ/thu hồi phiên | Backend transaction/user lock + UI đã có | Sai mật khẩu422 không revoke; thành công204 thu hồi hai phiên, user khác không ảnh hưởng; đăng nhập cũ401/mới thành công |
| ADMIN-01 tìm/lọc Learner/Teacher | Namespace chỉ có guard role, chưa có API/UI nghiệp vụ | GET list/search/filter/paging thật, UI `/admin/users`, Unicode/literal search, data/count cùng scope |
| Server permission | Chưa có quyền user management seed | V31 grant chỉ ADMIN; cả API và service đòi ADMIN + user:manage; revoke grant có hiệu lực ngay, Learner/Teacher403 |
| Khóa/mở khóa và audit | SessionAuthenticationService đã đọc ACTIVE mỗi request, chưa có endpoint sửa status | PATCH ACTIVE↔LOCKED + audit cùng transaction/row lock; test login cạnh tranh thực sự chờ PostgreSQL, audit lỗi rollback status |
| Tác động khóa với phiên | AUTH-03 đã chốt từ trước | Khóa chặn login/token ở request sau; mở khóa cho token còn hạn/chưa revoked hoạt động lại; không đổi thành revoke vĩnh viễn |
| QA-02 Auth/Practice/SRS/Exam | Có nhiều suite riêng, có demo TV2 chưa commit | Thêm test cùng actor đi qua các module, lock/unlock và password revoke; hồi quy backend, frontend và browser thật |

Không viết lại Auth/Profile/Password/SRS/grading/Section. Không mở CRUD role/
permission, thay chính sách phiên, official TOEIC conversion hoặc Sprint3.

## Contract ADMIN-01 và trách nhiệm

`GET /api/v1/admin/users?q=&role=&status=&page=0&size=20&sort=createdAt,desc`

- Chỉ ADMIN **và** `user:manage`, không suy từ việc ẩn nút phía FE. UI Learner gọi
  thẳng URL hiển thị thiếu quyền, server403, không có table/fallback local.
- Tài khoản đích có LEARNER hoặc TEACHER; loại mọi tài khoản có ADMIN, kể cả nhiều
  role. Không cho khóa bản thân/admin hay tài khoản role khác bằng endpoint này.
- q tối đa200, strip/chuẩn hóa whitespace, substring literal tên hoặc email, không
  phân biệt hoa thường Unicode. `%`, `_`, quote không thành wildcard hoặc SQL.
- role chỉ LEARNER/TEACHER; status ACTIVE/LOCKED/PENDING/DISABLED; page>=0,
  size1–100/default20. sort createdAt/fullName/email asc/desc, khóa phụ userId ASC.
  Unknown/duplicate/type/range query sai400. Phân trang/count dùng cùng predicate RR.
- DTO allowlist `{items:[{userId,email,fullName,status,roles,createdAt,updatedAt}],
  totalElements,page,size}`, không password/hash/session/token/profile học tập.

`PATCH /api/v1/admin/users/{uuid}/status`, body chính xác:

```json
{"status":"LOCKED"}
```

Hoặc `ACTIVE` để mở. Chỉ chuyển ACTIVE↔LOCKED; giữ PENDING/DISABLED, trả409 thay vì
bỏ qua OTP/activation bằng “mở khóa”. Không nhận query hoặc role/permission/email/
trường lạ; duplicate status bị từ chối400. Không tồn tại/ngoài phạm vi404. Lặp cùng
status trả200, không thêm audit hoặc đổi timestamp. Đây là lựa chọn triển khai tối
thiểu trong phạm vi quản lý Learner/Teacher; không thêm state transition khác.

AdminUserController/Request kiểm allowlist; AdminUserService kiểm quyền method,
lọc target, khóa user chung với login/password và điều phối transaction;
AdminUserRepository tìm data/count và ghi audit V9. Audit action USER_STATUS_CHANGED,
actor từ SessionPrincipal, target UUID và details chỉ from/to; không chứa email/
credential. Audit failure rollback cả status, không trạng thái nửa chừng.

Session policy giữ AUTH03_RBAC: request đã xác thực trước commit có thể hoàn tất;
request tiếp theo đọc status mới. LOCKED login401 INVALID_CREDENTIALS, token401
INVALID_TOKEN; expiry vẫn TOKEN_EXPIRED. Không sửa revoked_at khi khóa/mở. Mở khóa
không làm token hết hạn/đã revoked hợp lệ trở lại. FE gặp401 xóa phiên local nên
người dùng UI đăng nhập lại; test API giữ token còn hạn để xác nhận đúng contract.

## Migration và database

Fetch origin trước khi thêm: develop và HEAD cùng a4a27b1, highestV30; kiểm các
remote tracking refs không có migration >30. Không tự merge nhánh thành viên.
Thêm **V31__admin_user_management_permission.sql**: user:manage và grant chỉ ADMIN,
ON CONFLICT, không thay schema, không sửa migration đã áp dụng. V27 Section và
V27_1 catalog giữ nguyên. V31 đã kiểm qua Flyway trên DB test trống và nâng demo
từ V30 bình thường. PostgreSQL17 có collation `und-x-icu` sẵn có; dùng nó cho
Unicode lowercase vì locale C chỉ lower ASCII. Không cài thêm database extension.

- Test mục tiêu: `127.0.0.1:55459/toeic_tv1_sprint2_test`, role q03_main_test.
- Full regression: DB mới riêng `toeic_tv1_sprint2_regression`, cùng cluster/role.
- Runtime: profile local-demo, `127.0.0.1:55459/toeic_learning_demo`, public,
  role toeic_demo. Không chạm datasource cá nhân `toeic_db:5432`, không reset,
  drop/repair/history edits hoặc backup/nâng cấp personal DB.
- Runtime32 migration tới V31. Dữ liệu demo/attempt/điểm sẵn có giữ nguyên; các
  account QA mới là tổng hợp qua register API, không giả điểm/progress bằng SQL.

## Kiểm chứng và kết quả

Các lệnh backend dùng Maven Wrapper, env datasource test riêng và agent/memory
flags theo môi trường local đã có; helper ignored `backend/target/tv1-run-tests.ps1`:

```powershell
# Từ repository root; DB test phải tồn tại trên cluster local đã xác minh.
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/tv1-run-tests.ps1 -Tests AdminUserIntegrationTest -Log tv1-admin-targeted-verified.log
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/tv1-run-tests.ps1 -Tests Sprint2CrossModuleIntegrationTest -Log tv1-qa-crossmodule-verified.log
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/tv1-run-tests.ps1 -FullSuite -Database toeic_tv1_sprint2_regression -Log tv1-backend-full-regression.log
cd frontend/web
npm test
npm run lint
npm run build
./node_modules/.bin/playwright.cmd test tests/tv1.sprint2.spec.js
./node_modules/.bin/playwright.cmd test tests/demo.integration.spec.js
```

- Admin targeted **27 test pass**, thêm1 test xuyên module pass. Có permission
  revoke, service bypass, typed/unknown/duplicate input, multi-role/count, Unicode,
  idempotent audit, SQL audit failure rollback và concurrency quan sát pg_blocking_pids.
- AUTH-04/Admin browser TV1 **4/4 pass**: hồ sơ/mục tiêu, password hai phiên, Admin
  search/filter/lock/unlock/RBAC, lỗi mạng không fallback. Chromium dùng UI/API/DB thật.
- Frontend unit **52/52 pass**, gồm49 baseline (có phần TV2 chưa commit) +3 Admin
  adapter mới; không phải52 test mới TV1. Node sandbox ban đầu spawnEPERM, chạy lại
  ngoài sandbox thành công; không xóa/skip test.
- Lint exit0 với9 warning tồn tại ở router/hooks/vocabulary; không tắt rule. Build
  exit0, cảnh báo bundle >500kB. Không refactor ngoài phạm vi để dọn warning.
- **Full backend chạy đúng một lượt:**927 test,926 pass/1 failure/0 error/0 skip,
  8phút21giây. Failure duy nhất là danh sách quyền seed Admin của AuthFlow chưa có
  user:manage mới. Cập nhật đúng một assertion, giữ toàn bộ test/Problems imports;
  chạy lại **AuthFlowIntegrationTest31/31 pass**,35.929giây. Không chạy full lần2 vì
  không sửa production Java. Surefire cuối gồm full run + targeted retry có tổng
  **927 tests/0 failures/0 errors/0 skipped**; đây không phải một full run mới xanh.
- Hồi quy browser TV2:7/8 pass trước sửa SMTP; sau đó chạy lại riêng OTP **1/1 pass**,
  ca3.2giây, tổng4.6giây. Tám ca khác nhau đều có kết quả đạt cuối, không gọi lượt
  đầu8/8. Không bỏ/skip ca OTP để báo xanh. TV1 browser4/4 là suite riêng.
- DB audit read-only:32 migration tớiV31,0 lỗi; user:manage grant chỉ ADMIN,2 audit
  status thực từ UI;12 attempts/8 graded trước ngày12/11 vẫn còn,0 lệch grading header.
- Đã chụp/kiểm màn Admin desktop lọc Teacher, health web/backend/DB/Mailpit200.

QA-02 mới dùng fixture Practice/Q03 **đã commit** cho published versions; gọi HTTP
thật qua MockMvc/Security/JPA/PostgreSQL, không dựa code UI TV2. Cùng actor sửa goal,
tạo/grade Practice và Reading, SRS review, kiểm ownership/secret/result trước nộp,
submit replay, khóa/mở không làm mất persisted grade và password revoke chặn tất cả
module. Cleanup mới chỉ dọn record fixture; lỗi đầu về tên cột cleanup created_by
đã sửa và chạy lại đạt. Không reset database để che lỗi.

Hồi quy UI dùng môi trường demo TV2 hiện có, nên evidence đó có dependency runtime
vào seed/profile/UI TV2 local; không đưa các file này toàn bộ vào manifest TV1.
Một hunk test cũ Admin404 được đổi200 khi API đã có. OTP bị chậm39.91giây đã được
xác nhận qua thread dump SMTPTransport.getLocalHost → InetAddress.getCanonicalHostName.
Điểm SMTP HELO được sửa bằng mail.smtp.localhost=localhost; stack tiếp theo xác nhận
lookup Message-ID ở InternetAddress/UniqueValue còn chậm20.88giây. Đối chiếu bytecode
Jakarta Mail, thêm cả mail.host=localhost trong profile local-demo. Không đổi production
mail/OTP policy hoặc nới browser timeout; smoke chờ HTTP forgot200 trước tìm thư.
Không chạy lại full backend chỉ vì config demo hoặc tài liệu; profile được xác minh
bằng HTTP/SMTP thực sau restart.

## File/hunk TV1 và phần có sẵn

Manifest **29 file có nội dung TV1**, gồm5 file dùng chung chỉ nhận các hunk nêu dưới:

```text
backend/src/main/java/com/toeic/backend/identity/api/AdminStatusRequest.java
backend/src/main/java/com/toeic/backend/identity/api/AdminUserController.java
backend/src/main/java/com/toeic/backend/identity/api/AdminUserExceptionHandler.java
backend/src/main/java/com/toeic/backend/identity/application/AdminUserException.java
backend/src/main/java/com/toeic/backend/identity/application/AdminUserQuery.java
backend/src/main/java/com/toeic/backend/identity/application/AdminUserService.java
backend/src/main/java/com/toeic/backend/identity/application/AdminUserView.java
backend/src/main/java/com/toeic/backend/identity/domain/User.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/AdminUserRepository.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
backend/src/main/resources/application-local-demo.properties
backend/src/main/resources/db/migration/V31__admin_user_management_permission.sql
backend/src/test/java/com/toeic/backend/assessment/api/Sprint2CrossModuleIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/api/AdminUserIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
docs/API_Specification.md
docs/AUTH03_RBAC.md
docs/AUTH04_Profile_Password_Goals.md
docs/LLD.md
docs/SRS.md
docs/TV1_Sprint2_AUTH04_ADMIN01_QA02.md
frontend/web/src/app/routes.jsx
frontend/web/src/components/organisms/LearnerSidebar.jsx
frontend/web/src/features/admin/admin-users.api.js
frontend/web/src/pages/admin/AdminUsersPage.jsx
frontend/web/src/pages/profile/ProfilePage.jsx
frontend/web/tests/admin.users.test.mjs
frontend/web/tests/demo.integration.spec.js
frontend/web/tests/tv1.sprint2.spec.js
```

Ba file UI/test dùng chung đã có sửa TV2: routes chỉ thêm import + route admin;
LearnerSidebar chỉ thêm link Admin; demo.integration.spec chỉ đổi mô tả/404→200
Admin và chờ HTTP forgot-password. Profile demo dùng chung chỉ thêm comment và
SMTP HELO/Message-ID localhost. AuthFlowIntegrationTest chỉ thêm user:manage trong
expected ADMIN, giữ nguyên hunk xóa import Problems đã có. Bản trước TV1 ở ignored backend/target/tv1-before-*.txt để
đối chiếu hunk; toàn bộ code Practice/Section/SRS TV2 và seed/scripts giữ nguyên.

Đối chiếu snapshot: **33 file ngoài5 hunk dùng chung giữ nguyên SHA256**;5 file chung
khôi phục logic các hunk TV1 trong bộ nhớ phải khớp nguyên văn baseline. AGENTS.md,
PostmanR2,tatus,settings và5 Problems files khác giữ nguyên byte. AuthFlow giữ các sửa
Problems như đã nêu, không revert/ghi đè chúng. Index/HEAD/branch không đổi. Full suite có chạy cả sáu Problems
files local này: kết quả là của working tree thực, không khẳng định riêng staged tree.
Chưa stage/commit/push; không viết lại commit cũ hoặc thay Git global identity.

Evidence ignored: `backend/target/tv1-final-audit.json`, `tv1-shared-hunks.patch`,
`tv1-backend-full-regression.log`, `tv1-authflow-permissions-verified.log`,
`tv1-admin-qa-targeted.log`, `tv1-qa-crossmodule-verified.log`, `tv1-browser-verified.log`,
`tv1-tv2-browser-regression.log`, `tv1-otp-verified.log`, `tv1-frontend-unit.log`,
`tv1-frontend-{lint,build}.log`, `tv1-admin-ui.png`. Shared patch chỉ ghi delta từ
baseline TV2/Problems, không đưa toàn bộ file nền vào phạm vi TV1. Hai file demo
profile/spec vốn untracked từ TV2 cần phối hợp thứ tự bàn giao nền trước khi stage
riêng hunk sau này; hiện không tự stage hoặc commit chúng. Diff check kể cả file mới,
scan secret/artifact đạt; password/token/log/target/dist không thuộc manifest.

## Demo và giới hạn

Dịch vụ demo được giữ chạy: web <http://127.0.0.1:5173>, backend
<http://127.0.0.1:8080/api/health>, Mailpit <http://127.0.0.1:8025>/SMTP1025,
PostgreSQL55459. Cách start/stop/seed tham chiếu [hướng dẫn demo có sẵn](LOCAL_DEMO_FE_BE_Integration.md).
Account demo learner/teacher/admin@demo.example, password ngẫu nhiên nằm ở file
ignored backend/target/local-demo-credentials.json. Không đưa password/token vào doc.
Đổi password trên account tự đăng ký QA để không làm sai credentials seed.

Demo5phút: Learner→Profile đổi tên/goal750/reload/xóa → Settings thử sai mật khẩu
cũ rồi đổi đúng và login lại → Admin→Quản lý người dùng tìm email Learner/Teacher,
lọcstatus, khóa→Learner request bị401/login thất bại→mở khóa→login lại → Practice/
Reading/SRS để xem dữ liệu thật và kết quả không bị mất.

Avatar/cover/bio/privacy/targetDate, role/permission CRUD, Google Cloud thật, audit
viewer, AI/Mobile/protected playback/EXAM-03 không bổ sung trong nhiệm vụ. Mục tiêu
học phút/ngày ở Settings vẫn được ghi rõ local, không coi là targetScore Identity.
Chưa chạy PostmanR2 trong lượt này và không thay file. Chưa có CI SHA mới vì không
commit/push; không tự đánh dấu toàn Sprint2/TV2/EXAM-01 Done.
