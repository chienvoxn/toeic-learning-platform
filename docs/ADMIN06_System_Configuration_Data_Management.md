# ADMIN-06 — cấu hình hệ thống và quản trị dữ liệu

Triển khai/kiểm chứng 2026-11-13–14 trên `feat/web-auth-integration`.
Git local `chienvoxn <109399678+chienvoxn@users.noreply.github.com>`.
Phạm vi: ADMIN-06 và phần Settings của WEB-06; không phải toàn WEB-06/Sprint3,
không triển khai ADMIN-03. Kết quả triển khai dưới đây được ghi trước khi bàn giao;
review/commit/push theo yêu cầu tiếp theo, không tự merge PR.

## Nguồn yêu cầu và quyết định sản phẩm vừa chốt

Đã đối chiếu AGENTS, SRS, HLD Phần B, LLD §2.10, ERD/V9, API §3.9,
ADMIN02/07, code consumer và yêu cầu/AC trực tiếp của người dùng. Repo không có
backlog/AC ADMIN06 riêng; các mục DESIGN/OPEN không tự coi là quyết định sản phẩm.
Người dùng đã chốt hai key sau, thay cho blocker danh sách key trong bản ghi trước:

| Key duy nhất được quản trị | Kiểu | Mặc định sản phẩm | Phạm vi | Consumer runtime hiện có |
|---|---|---|---|---|
| vocabulary.recommendations.defaultLimit | INTEGER | 10 | 1–100 | GET /api/v1/vocabulary/recommendations, khi bỏ limit |
| learning.progress.defaultDays | INTEGER | 7 | 1–90 | GET /api/v1/learning-profile/progress, khi bỏ days |

Request có `limit`/`days` hợp lệ luôn ưu tiên và không cần đọc Settings. Chỉ khi
**không truyền** mới đọc giá trị hiện tại từ DB; không cache, có hiệu lực request
tiếp theo sau commit, không restart. Empty/duplicate/không phải integer/out of
range trả400, không âm thầm dùng mặc định. Giữ cách parse integer tham số HTTP
hiện hữu (cho phép dấu cộng/leading zero/strip whitespace); JSON Settings chặt
chẽ hơn, chỉ JSON integer, không nhận string/float/boolean/null.

Không đổi auth/session/grading/SRS algorithm/giới hạn bảo mật hoặc dữ liệu quá khứ,
snapshot/kết quả/attempt. Frontend vocabulary hiện bỏ limit nên dùng mặc định server;
ConnectedDashboard đang truyền days=7 tường minh nên vẫn nhận7 đúng contract.
Không tạo nghiệp vụ mới để ép sử dụng cấu hình. Không tạo/xóa key, expose legacy
key, biến môi trường, đường dẫn/secret/datasource hoặc endpoint SQL/shell.

ADMIN07 `4a41fa0` đã merge PR60. Fetch/fast-forward an toàn đến
`9c4db88830b664a06ab8265c63d90e27dd7d93e8`, chứa origin/develop mới nhất ở lần
fetch trước kiểm chứng. Tree khi fast-forward không đổi. Trước thêm migration đã
kiểm toàn bộ origin refs: cao nhất V33; V34 mới không trùng. Không sửa V9/migration
đã áp dụng, không repair hoặc thay history. V34 thêm revision bigint >=0 default0,
seed hai key nếu chưa có, permission và grant ADMIN; không overwrite legacy value.

## AC và kiểm chứng

| AC | Bằng chứng |
|---|---|
| Allowlist đúng hai key, metadata current/default/type/range | API integration + browser Settings |
| Runtime dùng giá trị đã lưu khi thiếu tham số; explicit ưu tiên | 12 từ vựng thật; default4/explicit2, progress11/explicit3 qua HTTP và integration |
| ACTIVE + ADMIN + permission ở filter và service | 401/403 Learner/Teacher, locked actor; revoke cùng token/browser và sau chờ khóa |
| Strict payload/range/query/key | unit + 10 payload parameterized integration + browser validation; không POST/DELETE |
| Chống lost update, audit nguyên tử | hai request chờ advisory lock:200/409, một revision/audit; audit CHECK failure rollback toàn DTO |
| UI loading/save/reload/error/capability, không mock | browser3/3: lưu DB/reload, stale editor409, transport error, thu hồi ADMIN |
| Không hồi tố kết quả | kết quả Practice đã submit so JSON bằng nhau trước/sau đổi Settings |
| Backup/restore thật, DB đích riêng và Verify | bằng chứng trước:78 bảng/1.411 dòng, hash/count và sequence đạt; không chạy lại |
| Bảo toàn nền, hồi quy | kết quả cuối và hash kiểm tra ở phần dưới |

**Đối chiếu AC chức năng ADMIN06: đã đạt API/UI/runtime/RBAC/concurrency/audit và
diễn tập dữ liệu trong phạm vi đã chốt.** Hồi quy có một failure do quãng gián đoạn
thời gian, nhóm đó chạy lại đạt; không nhận một lượt full backend xanh tuyệt đối.
Không nhận backup ở V34: archive đã diễn tập **V33 trước Settings**, script/archive không đổi.

## API, quyền, transaction và audit

`GET /api/v1/admin/system-settings` →200 array đúng hai Setting.
`PATCH /api/v1/admin/system-settings/{key}` →200 Setting:

```json
{"value":12,"expectedRevision":0}
```

Ví dụ DTO (timestamp minh họa, không phải evidence):

```json
{"key":"vocabulary.recommendations.defaultLimit","type":"INTEGER","value":12,"defaultValue":10,"minValue":1,"maxValue":100,"revision":1,"updatedAt":"2026-11-14T00:00:00Z","updatedBy":"<actor-uuid>"}
```

Cả hai API không nhận query; PATCH yêu cầu đúng value/expectedRevision, không field
lạ/trùng/thiếu; revision JSON integer >=0 trong signed64bit. Không có create/delete.
401 thiếu/invalid session;403 thiếu ACTIVE/ADMIN/permission;400 validation;
404 key không thuộc allowlist;409 SETTING_VERSION_CONFLICT;503 SETTINGS_UNAVAILABLE
khi DB/audit thất bại hoặc row đã lưu hỏng/mất. Không trả raw SQL/value hỏng/secret.
Giá trị bằng hiện tại và revision đúng là no-op: không tăng revision/thời gian/audit;
revision cũ vẫn409 kể cả cùng value. Không tự retry ghi đè.

Permission `system:settings:manage` do V34/code định nghĩa, được RBAC ADMIN02
grant/revoke, không phải permission tùy ý. Server `/me` và login cung cấp boolean
`adminCapabilities.settingsManagement`, cần ACTIVE+ADMIN+permission. UI không suy
quyền từ tên role. Request tiếp theo đọc quyền DB hiện hành;403 refresh capability.

- SystemSettingDefaults: ánh xạ đúng limit/days, explicit hợp lệ bỏ qua Settings;
  omitted đọc DB, không fallback khi row hỏng. Không đổi thuật toán consumer.
- SystemSettingsService: list REPEATABLE READ, kiểm actor DB; update transaction
  dùng RbacMutationGuard.begin khóa advisory RBAC → Settings row FOR UPDATE →
  kiểm actor lại sau chờ → so revision → mutation và RbacRepository.audit cùng TX.
  Thứ tự khóa chung ADMIN01/02 bảo vệ request đợi khi permission bị thu hồi.
- SystemSettingsRepository: chỉ query hai key enum, validate giá trị DB, update CAS
  revision. Runtime đọc giá trị commit cũ hoặc mới, không thấy ghi chưa commit.
- Audit `SYSTEM_SETTING_UPDATED`, entityType `SYSTEM_SETTING`, entity_id UUID null;
  details before/after chỉ `{key,value,revision}`, không secret. ADMIN07 suy entityId
  từ key allowlist và chỉ expose số hợp lệ/revision, count/data cùng predicate.
  Audit/usage nhận thêm action thứ7; vẫn chỉ bao phủ sự kiện hệ thống đã ghi.

UI `/admin/settings` tái sử dụng layout Admin, có current/default/range/type/revision/
UTC, input số, pending/error/success/no-op và tải lại. Khi409 yêu cầu reload trước
lưu tiếp; không nhận thành công giả. Data management chỉ dẫn script vận hành thật,
không có nút backup giả hoặc chạy arbitrary shell qua web.

## Công cụ dữ liệu đã triển khai

- [manage-local-data.ps1](../backend/scripts/manage-local-data.ps1): wrapper vận hành,
  các action Backup/Restore/Verify; tìm JDBC driver PostgreSQL có sẵn trong Maven
  cache hoặc nhận -JdbcJar. Cần Java21 và bộ PostgreSQL17, không tải dependency mới.
- [LocalDataManagement.java](../backend/scripts/LocalDataManagement.java): chạy ở
  source-file mode, dùng JDBC hiện có để giữ snapshot nguồn, gọi pg_dump/pg_restore
  bằng argument array và xác minh dữ liệu. Không nằm trong API/webserver.

Phạm vi chủ động giới hạn ở PostgreSQL demo **127.0.0.1:55459**. Script đối chiếu
data_directory với backend/target/q03-main-postgres của workspace trước thao tác.
Nguồn chỉ toeic_learning_demo hoặc toeic_admin06_test/toeic_admin06_regression.
Đích phải là DB mới `toeic_admin06_restore_<suffix>`, khác nguồn và chưa tồn tại.
Không có DROP, reset, repair, restore đè hoặc lệnh SQL/shell qua HTTP.

Archive phải nằm trực tiếp trong thư mục **backups.local** của repo, không overwrite;
wrapper từ chối directory link và đường dẫn ngoài vùng này. Archive/manifest
restore phải là regular file, đúng source/format và SHA-256. Thư mục đã được Git
ignore bởi quy tắc *.local hiện có; không sửa gitignore hoặc đưa dump vào source.
Không nhận password/token qua command line; nếu cần dùng PGPASSWORD trong môi trường
local. Không in credentials/SQL error/row data; lỗi DB chỉ in SQLSTATE.

Backup dùng pg_export_snapshot trong transaction REPEATABLE READ/READ ONLY và
pg_dump --snapshot tương ứng. Inventory lấy từ **cùng snapshot**: count và SHA-256
từng bảng public bằng JSONB canonical, timezone UTC, thứ tự COLLATE C, đọc theo batch.
Không so snapshot backup với trạng thái nguồn có thể đã thay đổi sau đó.

Restore dùng custom archive, --single-transaction --exit-on-error --no-owner
--no-privileges. Chỉ tạo DB đích sau kiểm archive; lỗi giữ đích/artifact để kiểm tra,
không tự xóa. Verify so danh sách bảng, count và hash từng bảng, sequence không lùi
và không chuyển is_called về trạng thái có thể tái cấp ID cũ ở cùng giá trị.
Sequence PostgreSQL không MVCC nên chấp nhận giá trị tăng sau mốc inventory.
Mỗi native operation có deadline2phút, có tiến độ/log local, không chờ vô hạn.

Giới hạn: dành cho DB demo/test nhỏ với schema public và bảng thường; từ chối
schema ứng dụng khác, partition/foreign/materialized tables chưa có inventory policy.
Không backup cluster roles/ACL, môi trường, media ngoài DB hoặc DB AI khác.
DB restore thuộc role vận hành, chưa được chuyển thành datasource ứng dụng.
Không coi đây là runbook production/PITR, retention hay monitoring backup định kỳ.
Chỉ dùng archive tin cậy; SHA-256/manifest kiểm hỏng/mismatch, không thay chữ ký nguồn.
Archive chứa dữ liệu ứng dụng nhạy cảm, cần bảo vệ local và không chia sẻ/Git.

## Diễn tập thực tế

| Mục | Kết quả |
|---|---|
| DB nguồn | toeic_learning_demo,127.0.0.1:55459,public |
| DB đích mới | toeic_admin06_restore_20261113_a, cùng server demo |
| Archive | backups.local/admin06_demo_20261113.backup,266335byte |
| SHA-256 archive | a9771ab299e25d74a59b32ce9a4b4b7fb5e3e1d2a453c76b58a74932c5b0e975 |
| Backup/restore | Exit0;78 bảng,1.411 dòng khớp hash/count từng bảng |
| Verify độc lập | Exit0; sequence không lùi |
| Flyway ở DB restore | V33,script V33__admin_observability_read_permissions.sql,checksum314073239,success=true |
| Constraint public chưa validate |0 |
| User/attempt/audit trong DB restore |47/31/79 |

Lượt backup đầu dừng trước khi tạo archive vì các dịch vụ demo đang tắt. Đã kiểm
cổng/process/log, khởi động đúng cluster demo; PostgreSQL recovery tự động hoàn tất
sau khi máy trước đó tắt không sạch. Không khởi chạy trùng hoặc sửa history.
Sau đó backup/restore thật thành công. Backend/web/Mailpit được bật lại bằng script
demo đã có; không chạm PostgreSQL cá nhân cổng5432 hoặc toeic_db.

Kiểm tra7 đường từ chối đã chạy, không in thông báo thành công:

1. Nguồn toeic_db bị từ chối trước kết nối.
2. Đích toeic_db bị từ chối.
3. Đích trùng nguồn bị từ chối.
4. Đích đã tồn tại bị từ chối, giữ nguyên DB restore đã xác minh.
5. Archive đã tồn tại không bị ghi đè.
6. Archive ngoài backups.local bị từ chối.
7. Archive bản copy bị đổi byte có checksum sai bị từ chối trước CREATE DATABASE.

Archive gốc giữ nguyên SHA-256 sau tất cả kiểm tra. DB đích tampered không được tạo.
Log và evidence chỉ ở ignored backend/target/admin06-*.log/.json; corrupt archive
thử nghiệm chỉ ở backups.local và được giữ để kiểm tra, không đưa vào Git.

## Kiểm chứng Settings và hồi quy

Các lượt mới trong nhiệm vụ này (không dùng kết quả ADMIN07 để nhận Settings):

| Lượt | Kết quả thực tế | Evidence local ignored |
|---|---|---|
| Unit SettingsInput, AdminCapabilities, LearningProfileController |20/20,0 fail/error/skip | backend/target/admin06-unit.log |
| Integration Settings, AuthFlow, AdminMonitoring trên toeic_admin06_test |66/66,0 fail/error/skip; riêng Settings18 | backend/target/admin06-integration.log |
| Frontend npm test |63/63 | backend/target/admin06-frontend-test.log |
| Frontend npm run lint |exit0;9 warning có sẵn, không warning ở Settings | backend/target/admin06-frontend-lint.log |
| Frontend npm run build sau chỉnh copy Audit cuối |exit0; cảnh báo chunk >500kB có sẵn | backend/target/admin06-frontend-build-final.log |
| Playwright Settings/Chrome thật |3/3;51 HTTP tường minh, thêm request UI | backend/target/admin06-browser.log |
| Browser regression Admin RBAC/Audit/Users/Auth/Practice/Section/OTP/SRS |18/18 | backend/target/admin06-browser-regression.log |
| Full backend cuối, DB toeic_admin06_regression mới |1044 test:1043 đạt,1 failure,0 error/skip; BUILD FAILURE | backend/target/admin06-full.log |
| Kiểm tra lại riêng RbacManagementIntegrationTest, không đổi code/test |29/29,0 fail/error/skip; BUILD SUCCESS | backend/target/admin06-rbac-recheck.log |

Lượt full duy nhất mất32phút49giây, trong đó Hikari ghi `Thread starvation or clock
leap detected` khoảng24phút giữa00:08 và00:32. Test
`lockingOtherAdminUsesExistingSessionPolicyAndNeedsBothPermissions` mất1423giây,
line174 (đọc role sau mở khóa) nhận401 thay200. Quãng này vượt hạn phiên mặc định
900giây của app; bằng chứng phù hợp với gián đoạn môi trường làm phiên hết hạn,
không phải thay policy Settings. Kiểm tra lại nguyên nhóm29test trong29,817giây
đạt, không sửa code/giảm assertion/tăng TTL/skip test. Các test Settings đều đạt
trong full. Không chạy lại full chỉ để tạo báo cáo xanh. Cần phân biệt rõ bằng
chứng full có failure với kết quả nhóm kiểm lại đã đạt.

Full bao gồm source hiện tại và các sửa Problems có sẵn chưa commit; không gọi đây
là bằng chứng cây staged/commit độc lập. AuthFlow chỉ thêm assertion permission
Settings; hai import xóa có sẵn không thuộc ADMIN06. Assertion capability Settings
positive/không ADMIN được thêm trước full cuối. Không chạy lại full vì sửa tài liệu.

Maven Wrapper thực chạy qua local runner ignored `backend/target/admin06-run-tests.ps1`:
`-Tests SystemSettingsInputTest,AdminCapabilitiesTest,LearningProfileControllerTest`,
sau đó `-Tests SystemSettingsIntegrationTest,AuthFlowIntegrationTest,AdminMonitoringIntegrationTest`,
cuối `-FullSuite -Database toeic_admin06_regression`. Runner gọi `mvnw.cmd -o test`,
pin URL127.0.0.1:55459/DB kiểm thử, Mockito javaagent cache và JVM nhỏ2CPU.
Không đổi datasource ứng dụng hoặc personal DB. Cách chạy kiểm thử chuẩn:

```powershell
cd D:/toeic-learning-platform/backend
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55459/toeic_admin06_test'
$env:SPRING_DATASOURCE_USERNAME='q03_main_test'
$env:SPRING_DATASOURCE_PASSWORD=''
.\mvnw.cmd test '-Dtest=SystemSettingsInputTest,SystemSettingsIntegrationTest,AdminCapabilitiesTest'
```

DB kiểm thử cần tồn tại riêng, không trỏ toeic_db. Browser opt-in cần dịch vụ demo,
Chrome cài sẵn và file credential local ignored, không được chạy với DB cá nhân:

```powershell
cd D:/toeic-learning-platform/frontend/web
npm test
npm run lint
npm run build
npm exec -- playwright test tests/admin.settings.spec.js
npm exec -- playwright test tests/admin.rbac.spec.js tests/admin.monitoring.spec.js tests/tv1.sprint2.spec.js tests/demo.integration.spec.js
```

## Demo khoảng5phút và vận hành

Web http://127.0.0.1:5173/admin/settings; API http://127.0.0.1:8080/api/v1;
Mailpit http://127.0.0.1:8025 (SMTP1025); PostgreSQL demo127.0.0.1:55459/public.
Profile `local-demo`, ứng dụng dùng `toeic_learning_demo`, không toeic_db.
Flyway V34 checksum1299604777 success=true được áp dụng bình thường khi khởi động backend demo; đường DB trống
được kiểm trên test/regression. Không đổi script/SQL cũ hoặc Flyway history.
Đăng nhập bằng `adminEmail` và `password` trong ignored
`backend/target/local-demo-credentials.json` (mở local, không copy vào Git/log/chat).
Learner/Teacher cũng dùng credential demo trong file này. Đối chiếu SQL sau browser:
hai value đã hoàn nguyên10/7, revision2/5, có7audit Settings thật. Restore DB cũ
vẫnV33 success=true. Login không cần OTP;
OTP reset mật khẩu xem Mailpit. Không tạo tiến độ/điểm giả.

1. (0–1phút) Admin → Cấu hình hệ thống: xem hai giá trị hiện tại/mặc định/giới hạn.
2. (1–2phút) Lưu limit4/days11, reload; API recommendations không limit trả tối đa4
   trên tài khoản có đủ từ, progress không days trả11 ngày. Explicit limit2/days3
   vẫn2/3. Browser test tạo12 từ sở hữu thật để loại trừ thiếu dữ liệu.
3. (2–3phút) Nhập0/91/1.5 cho days → lỗi; hai tab cùng revision, tab1 lưu18,
   tab2 lưu19 →409, tải lại rồi mới lưu. Không báo thành công giả.
4. (3–4phút) Audit → action SYSTEM_SETTING_UPDATED/type SYSTEM_SETTING/key lọc,
   xem before/after số/revision. Learner/Teacher API403, guest401; token mất quyền
   bị chặn ở request tiếp theo và menu/capability refresh.
5. (4–5phút) Xem evidence backup/restore đã diễn tập và giới hạn bên trên, giải thích
   script/DB đích riêng. Hoàn nguyên hai giá trị bằng API/UI với revision hiện tại
   (không sửa DB/history). Không cần diễn tập backup lại để trình bày Settings.

Kiểm tra cổng trước chạy; nếu dịch vụ đang chạy dùng nguyên, không chạy trùng.
Các script hiện có (không thuộc thay đổi ADMIN06):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-local-demo.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/stop-local-demo.ps1
```

Backup mới khi thực sự cần, không ghi đè archive cũ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/manage-local-data.ps1 -Action Backup -SourceDatabase toeic_learning_demo
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/manage-local-data.ps1 -Action Restore -SourceDatabase toeic_learning_demo -TargetDatabase toeic_admin06_restore_demo_b -Archive D:/toeic-learning-platform/backups.local/<archive>.backup
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/manage-local-data.ps1 -Action Verify -SourceDatabase toeic_learning_demo -TargetDatabase toeic_admin06_restore_20261113_a -Archive D:/toeic-learning-platform/backups.local/admin06_demo_20261113.backup
```

Restore chỉ tới DB **mới**; lệnh Verify cuối dùng đúng archive cũ/DB đã diễn tập,
không so với nguồn đã thay đổi sau backup. Không đưa archive/manifest/credential vào Git.

## Manifest39file và hunk dùng chung

```text
backend/scripts/LocalDataManagement.java
backend/scripts/manage-local-data.ps1
backend/src/main/java/com/toeic/backend/system/api/SettingUpdateRequest.java
backend/src/main/java/com/toeic/backend/system/api/SystemSettingsController.java
backend/src/main/java/com/toeic/backend/system/api/SystemSettingsExceptionHandler.java
backend/src/main/java/com/toeic/backend/system/application/SettingDefinition.java
backend/src/main/java/com/toeic/backend/system/application/SystemSettingDefaults.java
backend/src/main/java/com/toeic/backend/system/application/SystemSettingView.java
backend/src/main/java/com/toeic/backend/system/application/SystemSettingsException.java
backend/src/main/java/com/toeic/backend/system/application/SystemSettingsService.java
backend/src/main/java/com/toeic/backend/system/infrastructure/SystemSettingsRepository.java
backend/src/main/resources/db/migration/V34__managed_system_defaults.sql
backend/src/test/java/com/toeic/backend/system/api/SystemSettingsIntegrationTest.java
backend/src/test/java/com/toeic/backend/system/application/SystemSettingsInputTest.java
frontend/web/src/features/admin/settings.api.js
frontend/web/src/pages/admin/AdminSettingsPage.jsx
frontend/web/tests/admin.settings.spec.js
frontend/web/tests/settings.api.test.mjs
docs/ADMIN06_System_Configuration_Data_Management.md
backend/src/main/java/com/toeic/backend/identity/application/AdminCapabilities.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/RbacRepository.java
backend/src/main/java/com/toeic/backend/system/application/AdminReadQuery.java
backend/src/main/java/com/toeic/backend/system/infrastructure/AdminReadRepository.java
backend/src/main/java/com/toeic/backend/vocabulary/api/LearningProfileController.java
backend/src/main/java/com/toeic/backend/vocabulary/api/VocabularyRecommendationController.java
backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/application/AdminCapabilitiesTest.java
frontend/web/src/app/routes.jsx
frontend/web/src/components/organisms/LearnerSidebar.jsx
frontend/web/src/features/admin/monitoring.api.js
frontend/web/src/features/auth/api/auth.mapper.js
frontend/web/src/pages/admin/AdminAuditPage.jsx
frontend/web/tests/monitoring.api.test.mjs
frontend/web/tests/rbac.capabilities.test.mjs
docs/API_Specification.md
docs/ERD_v1.6.md
docs/LLD.md
docs/SRS.md
```

19file mới (gồm hai script giữ nguyên từ phần đã làm),20file tracked dùng chung.
Identity chỉ capability/filter/enforced permission; Audit chỉ action/type/key và
projection số/revision; vocabulary chỉ chỗ áp dụng mặc định, giữ API/service/algorithm.
Frontend chỉ Settings/route/menu/capability và audit filter/copy liên quan.
Test capability DTO thêm boolean; AuthFlow **chỉ một assertion thêm
system:settings:manage**, tuyệt đối không lấy hai hunk xóa import Problems có sẵn.
Tài liệu chính chỉ thêm contract/runtime/schema V34 tương ứng.

Hash14/14 được đối chiếu:10file nền (AuthFlow loại đúng assertion mới khi so) và
hai script/backup/manifest cũ; không ghi đè nền. AGENTS,6test Problems, PostmanR2,
tatus và ignored .vscode/settings.json giữ nguyên nội dung nền. Task diff check
gồm tracked và19file mới; known-secret/private-key/GitHub-token scan không thấy
credential demo trong39file. Tại mốc kiểm chứng triển khai, index rỗng và chưa
stage/commit/push/merge PR; đây không phải kết quả kiểm thử một commit đã xuất bản.

## Review bàn giao 2026-11-14

Fetch mới nhất thấy origin/develop `ce552e2dac52a3b79831bc358077d2a7d319ef11`
thêm learning analytics/UI TV3; origin/feat/web-auth-integration vẫn `4a41fa0`.
Nhánh hiện tại đã chứa ADMIN07 và merge PR60 ở `9c4db88`, có thể push fast-forward
lên remote feature. Không ghép thêm code TV3 vào commit ADMIN06. Đối chiếu40origin
refs vẫn cao nhấtV33, không trùngV34. Không đổi SQL đã áp dụng hoặc DB nào.

Manifest39file được review riêng; AuthFlow chỉ stage assertion Settings, giữ hai
import Problems ngoài commit. Nguồn production/frontend/migration của phần bàn
giao không phụ thuộc phần chưa stage: các hunk nền còn lại chỉ AGENTS, import test,
assertion HTTP422 deprecated tương đương, Postman/tatus và settings IDE. Không
export thêm project/worktree, không dùng reset/stash. Source ứng dụng/test nhiệm vụ
và hai script không đổi so mốc đã kiểm chứng; chỉ cập nhật ghi chú bàn giao này.
Giữ nguyên giới hạn full1043/1044 và nhóm RBAC chạy lại29/29, không chạy lại full
local chỉ để commit. CI của SHA push mới được kiểm riêng, không thay bằng chứng
local lịch sử. Bản nháp title/body PR được chuẩn bị local, không tự tạo/merge PR.

Giới hạn: chỉ hai default, không arbitrary settings, không auth/grading/attempt
configuration; backup vận hành local/demo, không production/PITR/scheduled backup
hoặc media/cluster roles. AI vẫn NOT_CONFIGURED theo môi trường demo, audit chỉ
bao phủ sự kiện đã ghi. WEB-06 chỉ thêm Settings/data-management guidance,
không nhận hoàn tất toàn Teacher/Admin hoặc Sprint3; không mở ADMIN03.
