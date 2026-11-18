# V34 — cơ chế tương thích trong source chính

Quyết định ngày18/11/2026: DB trống hoặc prefix hợp lệ tới V33 đi qua V34 RAG;
DB đã có V34 giữ đúng Settings/RAG. Working tree source chính đã triển khai cơ
chế dưới đây. Ngày19/11/2026 đã cho phép review/stage, hoàn tất merge và push;
nguồn staged được kiểm riêng trước commit, kết quả CI gắn theo SHA cuối.
Tài liệu điều tra local `MIGRATION34_Compatibility_Integration.md` (không đưa vào commit),
[diễn tập1120](MIGRATION34_Rehearsal_20261118.md) và
[điểm dừng trước quyết định V33](MIGRATION34_Integration_Status_20261118.md)
được giữ làm lịch sử, không mô tả trạng thái triển khai mới nhất.

## Locations và thứ tự

```text
db/migration/          V1…V33, V35, V36__complete_settings, V37__complete_rag
db/history/settings/  V34__managed_system_defaults.sql
db/history/rag/       V34__rag_chat_runtime.sql
db/compatibility/     schema-v37.txt, history-table.txt, restored-constraints.txt
```

Hai V34 relocate nguyên byte. Cả37 SQL gốc giữ SHA256; V34 Settings giữ CRC32
`1299604777`, V34 RAG `2001005912`, V35 `-949250551`. Source/output không còn V34
trong common root; mỗi DB chỉ resolve common và đúng một historical location.
Package vẫn giữ cả hai SQL lịch sử để validate đúng lineage các lần sau.

| History | Location V34 | Đường nâng cấp |
|---|---|---|
| DB trống | RAG | V1…V33→RAG34→35→36 Settings→37 kiểm RAG |
| Prefix SQL V1…V33 đầy đủ và schema/checksum đúng | RAG | Giữ history cũ, thêm RAG34→35→36 Settings→37 |
| Settings34, có hoặc chưa có35 | Settings | Giữ34; chạy35 nếu thiếu;36 kiểm Settings,37 tạo RAG |
| RAG34, có hoặc chưa có35 | RAG | Giữ34; chạy35 nếu thiếu;36 tạo Settings,37 kiểm RAG |
| Sau36/37 của release này | Theo34 đã ghi | Validate, không seed lại feature/grants đã có |
| History/schema/config không nhận diện | Không chọn fallback | Dừng trước mutation |

V35 chỉ tạo metadata moderation/contributor/decision và quyền review/publish;
không phụ thuộc revision Settings hay ba bảng RAG. Vì thế chạy35 trước36/37 hợp
lệ trên cả hai lineage. ORM/service chỉ khởi tạo sau coordinator hoàn tất tới37.
V36/V37 chép nguyên SQL bridge đã diễn tập, kể cả comment nguồn thử nghiệm, để
giữ checksum của các DB phòng thử đã dùng bridge; không sửa comment SQL sau này.

## Coordinator chung

`MigrationConfiguration` tạo Flyway bean cho Boot, Spring integration test và
CLI. Static policy check chạy trước datasource/SQL initializer. Dưới advisory
lock có timeout, coordinator thực hiện:

1. `MigrationHistory` kiểm schema public, định dạng history table, rank/prefix,
   success/type/checksum. Từ chối gap/duplicate/future/non-SQL hoặc checksum null.
   Không hard-code tên DB, host, cổng, role, data_directory hay máy thành viên.
2. DB trống/V33 chọn RAG theo policy release. Nếu đã có34, script/checksum/
   description/success phải khớp đúng Settings hoặc RAG; không nhận hai34.
3. Resolve đúng38 migration tới37; kiểm mọi applied checksum/script/description
   với source package **trước ghi**. `MigrationSchema` kiểm toàn metadata: column,
   default/nullability, CHECK/FK, index validity, sequence specs, trigger/function,
   extension/RLS/domain/enum. Feature catalog phải đúng lineage; không regrant.
4. Migrate với validation bật, không baseline/outOfOrder/ignore-pattern/clean;
   validate và kiểm schema/history sau migration. Boot initializer chuẩn chỉ
   validate bean đã hoàn tất dưới khóa, bảo đảm ordering trước JPA.

Fingerprint cố định được xuất từ canonical SQL đã có bằng chứng, không sinh từ
DB đích. Resource CRLF/LF được normalize để checkout Windows/Linux cho cùng kết quả.
`pg_dump`/restore PostgreSQL17 deparse sáu CHECK khác vị trí cast mảng varchar→text.
Đã dump/restore schema chuẩn vào DB mới độc lập để chứng minh đúng sáu biến thể.
`restored-constraints.txt` chỉ cho phép **toàn bộ dòng metadata chính xác** đó;
không strip cast, bỏ constraint/flag hoặc nới tập giá trị. Clone V33 dùng ba trong
sáu biến thể. Test nới visibility thêm `UNSAFE` vẫn bị chặn trước mutation.

## Cấu hình, script và CI

Config chung: Flyway enabled, locations common cố định; Hibernate ddl-auto
validate; SQL init never. `app.migrations.mode` chỉ migrate/validate;
`app.migrations.lock-timeout-ms` mặc định30000, phạm vi100…60000.
Từ chối override Flyway locations/schema/table/target/baseline/outOfOrder/
validation/ignore/URL/credentials/resolver/init SQL. Datasource dùng credential
đã cấu hình thông thường; không đưa secret vào code/log hoặc yêu cầu trong chat.

Boot và `start-local-demo.ps1` dùng cùng bean. `manage-local-data.ps1` vẫn chỉ
backup/restore/verify, không migrate ngầm. CLI từ thư mục backend:

```powershell
./scripts/migrate-database.ps1 -Action Validate
./scripts/migrate-database.ps1 -Action Migrate
```

Wrapper yêu cầu SPRING_DATASOURCE_URL tường minh để tránh chọn DB local mặc định;
gọi Maven Wrapper với MigrationCli. CLI chỉ tạo datasource/coordinator, không
tạo service/JPA/web. Validate với pending migration có thể nonzero, không ghi
history để làm xanh. Không hỗ trợ repair/clean/baseline hoặc standalone Flyway/
`mvn flyway:*` với locations tự chọn; những entry point đó bỏ qua coordinator và
không thuộc quy trình release. Chỉ dùng wrapper/CLI chung hoặc Boot.

CI dùng `bash ./mvnw -B clean verify`, cache context8/pool6, DB service ephemeral
`toeic_ci_test`. Không có adapter lab riêng. Migration tests bật khi có datasource
URL và tạo DB mới bằng SQL gốc. Chưa push nên chưa xác minh CI remote của cây này.
Clean trong CI chỉ tác động checkout CI riêng; không Maven clean workspace local.

## Kết quả source chính

Đã kiểm absolute path rồi dọn riêng `backend/target/classes/db`, giữ cluster và
target khác. Test-compile đạt; source/generated inventory không có V34 cũ ở
common root. Bốn đường sau dùng source chính, không adapter/bật cờ lab:

| DB riêng | Nguồn | Migrate/validate tới37 | Boot2 + CLI validate | Bảo toàn |
|---|---|---|---|---|
| empty_final | DB trống mới | Đạt | Đạt, health200 | Một RAG34,38 history rows |
| backup_actual | Backup local demo thực tế tới33:78bảng/1411row | Đạt | Đạt, health200 | Mọi row/history cũ, thêm RAG34 |
| main_settings | Chuỗi SQL gốc tới Settings34 | Đạt | Đạt, health200 |34, revision9, revoked Settings grant |
| main_rag | Chuỗi SQL gốc tới RAG34 | Đạt | Đạt, health200 |34, chat/citation/attempt, revoked AI grants |

Metadata chuẩn hóa của cả bốn cùng SHA256:
`69a8f9c801b91a6c7f0ec92f367868877cca575c9dc1fad78d5facd2cb207162`.
Raw dump clone V33 khác ba cast CHECK đã chứng minh, không được gọi là cùng raw
dump hash. Mỗi DB có38 history rows và chỉ resolve một V34; mọi row/history cũ
so theo installed_rank/giá trị được giữ nguyên, restart không đổi data/history.

Source chính đã đạt nhóm migration29; sau bổ sung restore allowlist, nhóm âm/
validator13; auth/RBAC/Settings/RAG/ADMIN-03 **134/134**. CLI và wrapper Validate
thật đạt. Các lỗi trong lượt phát triển đầu được giữ log, không tính là đạt.
Full regression cuối **source chính đạt1150 test,0failure,0error,0skip**,69suite,
BUILD SUCCESS, thời gian10:42; log `full-main.log`. Chỉ chạy một lượt rộng cuối
sau khi cây source ổn định, cùng context-cache8/pool6 như cấu hình CI.
**1120 test bản cách ly giữ nguyên nhãn bản cách ly**. Không lặp frontend vì
không sửa frontend. Business/test Java cũ giữ nguyên byte; khác biệt là
coordinator, metadata, config/CI và test compatibility mới. Sáu sequence của clone
V33 đều không lùi so với manifest backup gốc.

1150 là kết quả **working tree source chính** đang giữ các test local có sẵn;
không phải kết quả index hoặc commit chưa tạo. Không stage test/data nền để khớp
số lượng test CI. CI của đúng commit sau này phải kiểm riêng.

Package source chính bằng Maven Wrapper đạt; executable Spring Boot JAR chỉ có
hai V34 tại hai historical locations, không có V34 common. Cả37 SQL gốc và các
class coordinator trong JAR khớp byte với source/output đã kiểm.

Ca bị chặn có snapshot trước/sau: config sai11loại, checksum V34 lạ,
gapped/future history, column/index/CHECK/domain/schema/history-table drift,
partial feature và lock timeout. Hai startup đồng thời giữ đúng một row34.
Unit validator kiểm exact duplicate34 và failed success flag bằng input thuần,
không ghi giả history vào DB. Fixture lỗi/gap/checksum do Flyway chạy SQL trong
DB mới; không INSERT/UPDATE/DELETE history thủ công hoặc gọi fixture là member DB.

Chưa truy cập/upgrade clone V34 thành viên lâu dài. Settings/RAG34 trên là
fixtures SQL gốc; clone backup thực tế là demo local V33. Không khẳng định mọi
DB thành viên tương thích. DB ngoài schema public, prefix cũ chưa tới33 hoặc
migration sau37 cần thêm bằng chứng/version release. Không tác động toeic_db
cá nhân hoặc chờ credential cho phần độc lập này; chưa rollout DB thành viên.

## Vận hành và merge

Mỗi DB: inventory→backup/restore riêng→coordinator→validate/JPA/smoke→so data/
history→restart/validate. Không sửa history/repair/clean/baseline/tắt validation.
Khi thêm version sau37, cập nhật version set/fingerprint từ canonical SQL và
thử mọi lineage/restore; không lấy fingerprint DB lỗi làm chuẩn.

Hai parent cần giữ của merge:5bde289 và6fecb42, nhánh feat/web-auth-integration.
41file nền/settings được kiểm SHA256 và để ngoài commit. Quy trình19/11:
review43path cũ +23path compatibility, export index độc lập, kiểm compile/package
và compatibility tests rồi mới commit/push. Author/committer:
chienvoxn <109399678+chienvoxn@users.noreply.github.com>.
Kết quả staged và CI được ghi riêng trong draft PR/audit theo đúng SHA; không
đổi nhãn1150 working tree. Không tự tạo/merge PR, reset/stash/force-push;
không G1/media/EXAM-04. Draft PR local giữ ngoài repository.

Artefact: `backend/target/v34-source-20261118/` gồm matrix-result.json, before/
after snapshots, normalized/raw schema, roundtrip-proof.json, log Boot/CLI/script,
test-compile/migration/focused/full-main, manifest và audit cuối.

## Manifest cần review/stage khi được phép

Danh sách exact23path nằm ở `stage-manifest.json` trường
`compatibilityNotStaged`; `compatibility-tracked.patch` giữ diff các file tracked.
Đây là manifest nguồn cho lượt review/stage được cho phép ngày19/11/2026.

| Nhóm | File/hunk mới ngoài index |
|---|---|
| Coordinator | system/infrastructure/migration/{MigrationConfiguration,MigrationHistory,MigrationSchema,MigrationCli}.java |
| Relocate | Xóa hai path V34 cũ trong db/migration; thêm nguyên byte ở db/history/settings và db/history/rag |
| Bridge | db/migration/V36__complete_settings.sql, V37__complete_rag.sql |
| Evidence | db/compatibility/{schema-v37,history-table,restored-constraints}.txt |
| Config | application.properties: SQL init never, migration mode và lock timeout |
| Entry point | backend/scripts/migrate-database.ps1 |
| Test | MigrationCompatibilityIntegrationTest.java, MigrationHistoryTest.java |
| CI | ci.yml: ephemeral DB/trust, Maven Wrapper, cache8 |
| Docs | HLD/LLD thêm liên kết; runbook này và hai báo cáo trước thêm chỉ dẫn trạng thái hiện tại |

Index43path cũ: giữ nguyên như `existing-merge-paths.json` của lượt trước.
Ngoài nhiệm vụ: sáu file tracked có hunk AGENTS/import/matcher422 và toàn bộ
untracked/settings trong danh sách41file bảo toàn; exact paths/hunks lưu tại
`v34-main-integration-20261118/background-paths.json`/`background-hunks.patch`.
Không dùng add-all, không stage các file nền/artefact target.

Điều kiện hoàn tất merge commit: review đủ23path compatibility cùng index43path
cũ, cập nhật relocation/deletion của hai V34; staged export độc lập đạt kiểm tra;
cached diff/manifest không chứa file nền; commit đúng hai parent và identity.
CI remote và kiểm clone V34 thành viên lâu dài còn là giới hạn cần ghi rõ trước
rollout, không bị thay thế bằng bằng chứng SQL fixtures.

Đã dừng riêng cluster thử55462 sau khi xác minh identity; DB/log/JAR/backup và
snapshot giữ trên đĩa. Không dừng hoặc kết nối DB cá nhân. Cached diff check đạt;
diff toàn working tree chỉ còn whitespace AGENTS.md dòng1 thuộc baseline.
