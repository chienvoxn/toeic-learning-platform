# V34 — tiếp tục tích hợp từ merge đang mở, 18/11/2026

> Đây là điểm dừng lịch sử. Sau quyết định V33, cơ chế đã vào working tree
> source chính; xem [runbook hiện tại](MIGRATION34_Source_Compatibility.md).

## Kết quả và điểm dừng

Giữ nhánh `feat/web-auth-integration`, commit bảo toàn
`5bde2890e44b07e0cf8d05a553c246382f7c096c` và MERGE_HEAD
`6fecb42dd95db57f7412eecbcc9525e2cc4a6aa3`. Không chạy thêm `git merge`.
Không còn conflict; index vẫn nguyên 43 đường dẫn của merge trước, chưa stage
thêm hoặc commit. Các hunk hợp quyền trong AuthFlow/RbacRepository giữ nguyên.

**Source chính vẫn có duplicate V34 và chưa được đổi locations.** Chưa đủ điều
kiện an toàn để đưa adapter phòng thử vào source chính. Điểm dừng này dựa trên
điều kiện người dùng yêu cầu: “Đủ các điều kiện an toàn ghi trong báo cáo diễn
tập” và “Nếu ... có bước chuyển đổi chưa được xác nhận, dừng đúng phần đó”.
Không phải yêu cầu duyệt lại commit bảo toàn hoặc merge đã được cho phép.

Phần đã thực hiện thêm: kiểm index/hash và toàn bộ source so với candidate cũ;
kiểm đường chạy Boot/test/script/CI; tìm backup/cấu hình kết nối; restore một
backup local thực tế vào DB mới; xác nhận dữ liệu và chứng minh guard chặn history
V33 trước mutation. Chưa thay đổi Java/config/migration source chính.

## Cơ chế đã diễn tập và phạm vi bằng chứng

Theo [báo cáo diễn tập](MIGRATION34_Rehearsal_20261118.md), common location chứa
V1…V33/V35/V36/V37; hai V34 nguyên byte nằm ở hai historical locations ngang
hàng. Guard nhận diện một row V34 theo script/checksum/description/success,
kiểm prefix history và fingerprint schema; chọn đúng một location, rồi
Flyway migrate/validate. V36 hoàn thiện Settings trên RAG history, V37 hoàn
thiện RAG trên Settings history. Feature đã có được kiểm tra và không seed lại
giá trị/quyền đã thu hồi. Schema cuối hội tụ, history V34 cố ý giữ khác biệt.

| Nguồn | Loại lịch sử thực sự đã kiểm | Bằng chứng | Không suy ra |
|---|---|---|---|
| DB trống phòng thử | Chọn RAG mới tường minh | Migrate/validate/start/restart/JAR đạt tới37 | Tương thích DB thành viên |
| Settings tổng hợp | V34 Settings + V35, tạo từ SQL gốc | Giữ history/data, tới37, restart đạt | Clone Settings thành viên đã kiểm |
| RAG tổng hợp | V34 RAG, tạo từ SQL gốc | Giữ history/data, tới37, restart đạt | Clone RAG thành viên đã kiểm |
| Backup local demo thực tế 13/11 | V1…V33, 34 row history, chưa có V34 | Restore đúng 78 bảng/1411 row; guard chặn; dữ liệu/history/sequence không đổi | Được tự gán Settings/RAG V34 |
| ADMIN03 local 55461 | Trước đây đã đọc Settings V34 + V35; lần này chưa truy cập lại được DB | Cluster mở READ ONLY, chưa tìm được role kết nối hợp lệ | Có backup/clone mới hoặc đã upgrade thành công |
| DB thành viên lâu dài khác | Chưa có backup/history/credential được xác minh | Không có | Bất kỳ tuyên bố “tất cả DB tương thích” |

CRC32 V34 Settings: `1299604777`; V34 RAG: `2001005912`; V35:
`-949250551`. Không sửa checksum hoặc row history nào.

## Audit Boot, test, CLI/script và CI

| Đường chạy hiện tại | Thực tế | Việc cần hoàn tất trước khi áp dụng source |
|---|---|---|
| Spring Boot source chính | `spring.flyway.locations=classpath:db/migration`, cả hai V34 cùng root | Adapter dùng policy release, không hard-code identity lab; guard config trước mutation |
| Spring integration test source chính | Dùng BackendApplication/config thật, chưa có adapter | Cùng coordinator với Boot; test âm có snapshot chứng minh không mutation |
| Candidate đã đạt1120 | Có hai helper và hai fingerprint thêm vào bản sao; truyền `app.v34.empty-lineage=rag` riêng | Chuyển policy DB mới thành cấu hình vận hành rõ ràng của release, không dựa cờ lab |
| CLI Flyway trực tiếp | Không có entry point chung trong source; chọn location sai/both đã có ca âm | Cần CLI dùng cùng preflight/lock/config validation; không hướng dẫn tự chọn location |
| `start-local-demo.ps1` | Gọi Maven Wrapper `spring-boot:run` với profile local-demo | Sẽ nhận adapter khi Boot có; DB demo V33 phải dừng, không lặng lẽ chọn lineage |
| `manage-local-data.ps1`/LocalDataManagement | Backup/restore/verify DB local allowlist; không migrate | Giữ nhiệm vụ backup/restore; không chèn migration tự động vào thao tác đọc/backup |
| `.github/workflows/ci.yml` backend | Hiện chạy Maven `clean verify`, dùng config source; không có adapter hay kiểm matrix V34 | Dùng Maven Wrapper theo AGENTS; nối cùng cấu hình release và thêm kiểm history/CLI thật |

CI hiện có DB service ephemeral đặt tên `toeic_db`; đó không phải DB cá nhân,
nhưng không khởi chạy workflow/service đó trong lượt này. Không kết nối cổng5432.
Chưa có push nên không có CI cho cây tích hợp mới. CI cũ không thay thế bằng chứng
cho adapter/release chưa triển khai.

Các điều kiện trong báo cáo trước vẫn còn thiếu: guard production cho schema
`public`/history table và config không cho override; cơ chế CLI chung; kiểm failed
và gapped history, metadata ngoài phạm vi, timeout và hai startup cạnh tranh;
fingerprint release cố định được review; clone V34 thực tế. Không coi các ca chưa
chạy này đã đạt và không chỉ bỏ identity lab rồi copy adapter vào source.

Tài liệu chính thức: [locations scan đệ quy](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-locations-setting),
[validate đối chiếu checksum/history](https://documentation.red-gate.com/flyway/reference/commands/validate).
Vì vậy chỉ thêm V36/V37 vào common root không giải quyết được duplicate V34 ở
bước resolve. Không dùng ignore patterns/repair/baseline để làm xanh.

## Backup thực tế và thao tác clone mới

Đã tìm `backups.local/admin06_demo_20261113.backup` với manifest
`toeic-admin06-v1`, source `toeic_learning_demo`. SHA256 archive khớp manifest.
Không sử dụng hai archive có tên `corrupt_guard`.

Restore vào **DB mới** `v34r_20261117_backup_actual_20261118`, cluster phòng thử
55462, role phòng thử đã có. Kiểm identity port/data_directory trước CREATE
DATABASE, từ chối target đã tồn tại. Dùng `pg_restore --no-owner --no-privileges
--exit-on-error --single-transaction`; không restore đè source.

Tái sử dụng hàm inventory của LocalDataManagement: transaction READ ONLY,
REPEATABLE_READ, UTC; SHA256 từng row JSON theo COLLATE C và kiểm mọi table count,
sequence last_value/is_called. Kết quả khớp manifest **78 bảng, 1411 row**, gồm
34 history row tới V33. Guard cũ được gọi trực tiếp và trả chính xác:

```text
Missing V34 in nonempty history; manual investigation required
```

Inventory sau từ chối giống trước. Chưa chạy migrate/startup trên clone này vì
guard phải dừng trước đó. Đây là một **restore thực tế kiểm được**, không phải
một **upgrade V34 thành viên thành công**.

Nếu muốn nâng cấp backup V33, thay đổi cần xác nhận là mở rộng policy guard từ
“DB trống hoặc history đã có V34 xác định” sang “prefix V1…V33 được validate
đầy đủ và có schema tương ứng”. Cần chốt lineage cho migration V34 sắp chạy,
diễn tập riêng tuyến33→34→37 và restart. Chưa chọn RAG/Settings hoặc chạy tuyến
đó. Không có sửa history cũ nào được đề xuất để thay thế bước này.

Đối với nguồn ADMIN03 55461: chỉ khởi động cluster đã biết bằng
`default_transaction_read_only=on`, không chạy app/migrate trên nguồn. Các role
mặc định/cấu hình local tìm được chưa kết nối được. Không tìm thấy credential
trong environment, pgpass, `.env` hoặc local datasource config. Đã hỏi tên role
hoặc đường dẫn config; không yêu cầu password trong chat. Không đổi pg_hba,
tạo role hay dùng single-user mode để vượt xác thực.

## Bằng chứng được tái sử dụng và kiểm tra mới

- Source chính: log `v34-rehearsal-20261117/main-test-compile.log` đã BUILD SUCCESS
  sau merge trước. 43 file hợp nguồn hiện khớp SHA256 bản đó. Không chạy lại
  test-compile trên cùng code chỉ để tạo log mới.
- So từng file backend hiện tại với bản cách ly: mọi đường dẫn chung đều khớp
  byte, gồm toàn bộ business/test/config. Khác biệt chỉ ở hai V34 được relocate
  và tám file riêng của candidate (hai helper, hai fingerprint, hai V34 ở history,
  V36/V37). `source-comparison.json` ghi danh sách chính xác.
- 1120/1120 thuộc **bản cách ly** có adapter/bridge. Chỉ tái sử dụng làm bằng
  chứng cho business/test code không đổi; không gọi là source chính/runtime xanh.
- Kiểm mới: compile harness CloneBackupAudit + LocalDataManagement bằng javac21;
  chạy CloneBackupAudit trên DB restore mới; guard từ chối đúng và inventory
  trước/sau khớp. Không có test assertion bị giảm.
- Không rerun full suite hoặc frontend: không có thay đổi Java/config/frontend
  source chính hoặc rủi ro mới ở các phần đó. Các ca âm release còn thiếu được
  ghi là chưa kiểm; không giả lập trạng thái đạt.
- Không Maven clean hoặc xóa `backend/target`: có cluster/backup/proof cần giữ.
  Chưa đổi resource location source nên chưa có output mới cần clean. Khi source
  được thay đổi, chỉ dọn thư mục resource output đã xác minh trong workspace
  hoặc build output mới riêng; phải kiểm không còn V34 cũ ở common root.

Artefact mới tại `backend/target/v34-main-integration-20261118/`:
`start.json`, `start-index.bin`, `start-cached.patch`, `background-hunks.patch`,
`background-paths.json`, `existing-merge-paths.json`, `source-comparison.json`,
`backup-history.sql`, `clone-backup-result.properties`, `CloneBackupAudit.java`,
các log lifecycle PostgreSQL và draft PR `PR_into_develop.md`.

## Điều kiện còn thiếu trước commit merge

1. Xác minh được backup/history của nguồn V34 thực tế, restore mới và thử nâng
   cấp/restart; hoặc ghi nhận phạm vi release chỉ hỗ trợ history nào được kiểm
   với quyết định rõ ràng. Không mặc định các DB thành viên giống fixture.
2. Triển khai/kiểm coordinator chung Boot/test/CLI/CI và đóng các ca an toàn còn
   thiếu nêu trên. Nếu phải bổ sung policy tuyến V33, xác nhận trước tuyến đó.
3. Sau khi đủ điều kiện mới relocate nguyên byte cả hai V34, thêm bridge và
   fingerprint vào source, dọn resource output có kiểm soát, test-compile và
   chạy các test phần khác biệt. Chưa coi source đã hết duplicate.
4. Review đúng manifest/hunk nhiệm vụ, loại các hunk nền khỏi nội dung commit;
   user vẫn chưa cho stage thêm hoặc commit merge. Author/committer khi được
   phép: chienvoxn <109399678+chienvoxn@users.noreply.github.com>.

Không reset/stash/force-push; chưa push/tạo/merge PR, chưa G1/media/EXAM-04.
Các danh sách chính xác của index và file nền được bổ sung bên dưới từ Git/hash.

Sau kiểm tra đã dừng cả cluster READ ONLY55461 và cluster clone55462, trở về
trạng thái không chạy như đầu lượt. Backup, DB clone và log đều giữ trên đĩa.
`git diff --cached --check` đạt; `git diff --check` chỉ còn trailing whitespace
AGENTS.md dòng1 thuộc baseline đã biết, không sửa file đó.

## File/hunk hi?n n?m trong index merge (43 ???ng d?n)

C?c file d??i ??y thu?c merge ?? chu?n b? t? tr??c; ch?a stage th?m trong l??t n?y.
Hai hunk x? l? conflict ? RbacRepository v? n?m expected permission lists c?a
AuthFlow gi? nguy?n. Gi? c? hai ph?a quy?n AI/Settings/review/publish.

```text
README.md
backend/.gitignore
backend/config/application-local.properties.example
backend/src/main/java/com/toeic/backend/ai/api/ChatController.java
backend/src/main/java/com/toeic/backend/ai/application/ChatContracts.java
backend/src/main/java/com/toeic/backend/ai/application/ChatFailure.java
backend/src/main/java/com/toeic/backend/ai/application/ChatService.java
backend/src/main/java/com/toeic/backend/ai/infrastructure/ChatRepository.java
backend/src/main/java/com/toeic/backend/ai/infrastructure/RagClient.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java
backend/src/main/java/com/toeic/backend/identity/infrastructure/RbacRepository.java
backend/src/main/java/com/toeic/backend/vocabulary/api/LearningProfileController.java
backend/src/main/java/com/toeic/backend/vocabulary/api/LearningStreakResponse.java
backend/src/main/java/com/toeic/backend/vocabulary/application/LearningStatisticsService.java
backend/src/main/resources/application.properties
backend/src/main/resources/db/migration/V34__rag_chat_runtime.sql
backend/src/test/java/com/toeic/backend/ai/ChatIntegrationTest.java
backend/src/test/java/com/toeic/backend/ai/RagClientTest.java
backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java
backend/src/test/java/com/toeic/backend/vocabulary/api/LearningProfileControllerTest.java
backend/src/test/java/com/toeic/backend/vocabulary/application/LearningStatisticsServiceTest.java
docs/API_Specification.md
docs/ERD_v1.6.md
docs/HLD.md
docs/LLD.md
docs/RAG_Chat_Integration.md
docs/RAG_Tutor.md
docs/SRS.md
frontend/web/src/components/organisms/GoogleSignIn.jsx
frontend/web/src/components/templates/LearnerTemplate.jsx
frontend/web/src/features/ai/ChatBubble.jsx
frontend/web/src/features/ai/README.md
frontend/web/src/features/ai/chat.api.js
frontend/web/src/features/ai/chat.css
frontend/web/src/features/vocabulary/repositories/vocabulary.api.js
frontend/web/src/pages/auth/LoginPage.jsx
frontend/web/src/pages/profile/ProfileLearningPanel.css
frontend/web/src/pages/profile/ProfileLearningPanel.jsx
frontend/web/src/pages/profile/ProfilePage.jsx
frontend/web/src/services/apiClient.js
frontend/web/src/styles/figma.css
frontend/web/tests/chat.api.test.mjs
frontend/web/tests/chat.widget.spec.js
```

## File/hunk n?n ph?i ?? ngo?i merge commit

S?u file tracked c? diff n?n: AGENTS ??i ti?u ??; GoogleAuth/RegistrationFlow/
SessionIssuer/QuestionAuthoringConcurrency x?a import th?a; PasswordChange ??i
matcher HTTP422 sang isUnprocessableContent. C?c hunk n?y kh?ng n?m trong index.
To?n b? 41 file/settings sau v?n kh?p SHA256 l?c b?n giao tr??c:

```text
.vscode/settings.json
AGENTS.md
api-tests/collections/TOEIC API AUTOMATION - R2.postman_collection.json
api-tests/collections/TOEIC_API_AUTOMATION_R2.postman_collection.json
api-tests/collections/TOEIC_API_AUTOMATION_R2_BVA.postman_collection.json
api-tests/collections/TOEIC_API_AUTOMATION_R2_NEW.postman_collection.json
api-tests/environments/local.postman_environment.json
api-tests/reports/auth-data-driven.json
api-tests/reports/auth-data-driven.xml
api-tests/reports/test-summary.csv
api-tests/reports/uc01-auth.json
api-tests/reports/uc01-auth.xml
api-tests/reports/uc02-vocab.json
api-tests/reports/uc02-vocab.xml
api-tests/reports/vocabulary-bva.json
api-tests/reports/vocabulary-bva.xml
api-tests/scripts/run-all-tests.ps1
api-tests/test-data/auth-test-data.csv
api-tests/test-data/vocabulary-test-data.csv
backend/backend/src/test/resources/unit-test-data/vocabulary-create.csv
backend/src/test/java/com/toeic/backend/identity/api/GoogleAuthIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/api/PasswordChangeIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/application/AuthDataDrivenTest.java
backend/src/test/java/com/toeic/backend/identity/application/RegistrationFlowIntegrationTest.java
backend/src/test/java/com/toeic/backend/identity/application/SessionIssuerTest.java
backend/src/test/java/com/toeic/backend/questionbank/api/QuestionAuthoringConcurrencyTest.java
backend/src/test/java/com/toeic/backend/vocabulary/application/VocabularyDataDrivenTest.java
backend/src/test/resources/unit-test-data/auth-login.csv
backend/src/test/resources/unit-test-data/vocabulary-create.csv
docs/MIGRATION34_Compatibility_Integration.md
frontend-web/package-lock.json
tatus
unit-tests/reports/auth-data-driven.xml
unit-tests/reports/unit-auth.xml
unit-tests/reports/unit-test-details.csv
unit-tests/reports/unit-test-summary.csv
unit-tests/reports/unit-vocab.xml
unit-tests/reports/vocabulary-data-driven.xml
unit-tests/scripts/run-all-unit-tests.ps1
unit-tests/test-data/auth-login.csv
unit-tests/test-data/vocabulary-create.csv
```

Runbook m?i n?y v? b?n c?p nh?t li?n k?t ? b?o c?o di?n t?p ch?a ???c stage;
ch? ?? xu?t ??a ph?n t?i li?u nhi?m v? v?o commit khi user cho ph?p. C?c file
Java/migration/CI compatibility t??ng lai ch?a ???c ??a v?o source ho?c index.
