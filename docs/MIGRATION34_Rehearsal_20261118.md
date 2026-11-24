# V34 — báo cáo tích hợp và diễn tập, 17–18/11/2026

> Bằng chứng1120 thuộc bản cách ly lịch sử. Cơ chế source chính và kết quả mới
> được ghi riêng tại [runbook hiện tại](MIGRATION34_Source_Compatibility.md).

## Trạng thái bàn giao

Phương án historical locations **vẫn là đề xuất**, chưa áp dụng vào source chính
hoặc DB đang dùng. Tất cả adapter/migration mới dưới đây chỉ nằm trong
`backend/target/v34-rehearsal-20261117/` (ignored). Không truy cập `toeic_db`,
không repair/clean/baseline/outOfOrder, không cập nhật thủ công Flyway history.

Commit bảo toàn: `5bde2890e44b07e0cf8d05a553c246382f7c096c`, message
`chore(test): remove unused AuthFlow imports`. Author và committer đều là
`chienvoxn <109399678+chienvoxn@users.noreply.github.com>`.
Diff commit chỉ xóa hai import `ArgumentCaptor` và `ArgumentMatchers.eq` trong
AuthFlowIntegrationTest; không đổi thân test/assertion. Đã kiểm diff, biên dịch
test và cached diff check trước commit; không stage file nền nào vào commit này.

Đã fetch và hợp source `origin/develop` tại
`6fecb42dd95db57f7412eecbcc9525e2cc4a6aa3` vào working tree của
`feat/web-auth-integration`, dùng merge không autostash và chưa commit merge.
Hai conflict được giải quyết bằng hợp quyền:

- RbacRepository giữ Settings/review/publish, thêm `ai:chat:self` và
  `ai:knowledge:manage`. Repository tiếp tục đọc/ghi RBAC và chỉ công bố các
  permission đã có enforcement.
- AuthFlowIntegrationTest giữ strict expected lists và mọi test auth/RBAC;
  hợp quyền AI vào ba seeded roles và hai expectation Learner khác. Không giảm
  assertion hoặc khôi phục hai import vừa loại bỏ.
- IdentitySecurityConfig tự merge giữ rule Settings, question-options và AI;
  các rule ADMIN + permission đều đứng trước `/admin/**` catch-all.

Service nhận từ develop: ChatService kiểm actor/quyền/quota, lọc nguồn được phép,
điều phối gọi RAG và lưu hội thoại/citation/replay; lời gọi provider nằm ngoài
transaction DB. LearningStatisticsService bổ sung tính chuỗi ngày học từ lịch sử
review của chính user; không tạo cơ chế chấm điểm hoặc cập nhật review thứ hai.

Không còn conflict Git. Merge chưa được chốt thành commit vì source chính vẫn
chứa cả hai V34 trong `db/migration`, nên Flyway từ chối resolve. Đây là blocker
runtime cần quyết định riêng; không dùng kết quả bản diễn tập để nhận source
chính đã migrate/startup đạt. Không push, tạo PR hoặc merge PR.

## Đối chiếu tài liệu và giới hạn

Đã đối chiếu SRS UC-11/ADMIN03/ADMIN06, HLD Phần B, LLD Identity/System/RAG,
ERD và API Specification với hai SQL V34, V35 và test hiện có. Tài liệu runtime
Settings gọi V34 là `managed_system_defaults`, trong khi runtime RAG từ develop
cũng dùng version34 cho `rag_chat_runtime`. Cả hai có bằng chứng đã từng sử dụng;
không được giải quyết bằng đổi số/checksum một SQL đã áp dụng.

Giữ V35 ADMIN03 nguyên bản. Không coi OPEN về G1/media/EXAM-04 thành yêu cầu đã
chốt và không mở rộng các phần đó. Những kết quả lịch sử trong
báo cáo điều tra local `MIGRATION34_Compatibility_Integration.md` không thay thế
kết quả tích hợp lần này; file nền đó được bảo toàn nguyên byte, không đưa vào commit.

## Phương án cụ thể đã thử, chưa phê duyệt triển khai

Trong **bản sao cách ly**, giữ nguyên byte/tên/version hai V34 và chuyển chúng
ra hai thư mục ngang hàng với migration root:

```text
db/migration/          V1…V33, V35, V36__complete_settings, V37__complete_rag
db/history/settings/  V34__managed_system_defaults.sql
db/history/rag/       V34__rag_chat_runtime.sql
```

Không có V36/V37 trong các remote refs đã fetch ở thời điểm diễn tập; phải kiểm
lại khi thật sự triển khai. Cả 37 SQL gốc của cây tích hợp giữ nguyên SHA256 ở
source chính và bản sao. V35 giữ checksum `-949250551`.

Trách nhiệm các thành phần thử nghiệm:

1. **LineageGuard** kiểm identity DB diễn tập, đọc history, yêu cầu một prefix
   version đã biết, không có failed/non-SQL/duplicate/unknown version. Một row
   V34 phải khớp đồng thời script, checksum, description và success. Schema của
   feature đã có phải khớp fingerprint cột/type/default/nullability,
   constraint/FK/index và trạng thái valid. Feature chưa có phải vắng cả schema
   và permission tương ứng. Không chọn location để né lỗi validation.
2. **RehearsalFlywayConfiguration** chọn common và đúng một location theo guard
   trước khi tạo Flyway bean; từ chối override locations. Kiểm lại dưới advisory
   lock trong khi migrate/validate; kiểm tập locations thay vì thứ tự vì Flyway
   chuẩn hóa thứ tự. Flyway vẫn validate checksum toàn bộ migration đã áp dụng.
3. **V36 thử nghiệm** bổ sung Settings cho history RAG; trên Settings history chỉ
   kiểm invariant và không ghi lại giá trị/revision/quyền đã thu hồi.
4. **V37 thử nghiệm** bổ sung RAG cho Settings history; trên RAG history giữ
   nguyên bảng/dữ liệu/grants đã có. Schema partial bị từ chối, không rải
   `IF NOT EXISTS` để bỏ qua drift.

V34 nhận diện:

| History | Script | Checksum | Description |
|---|---|---:|---|
| Settings | V34__managed_system_defaults.sql | 1299604777 | managed system defaults |
| RAG | V34__rag_chat_runtime.sql | 2001005912 | rag chat runtime |

DB trống chỉ được chọn RAG khi truyền tường minh
`app.v34.empty-lineage=rag`. DB có dữ liệu không history, history tới V33 nhưng
thiếu V34, hoặc history không nhận diện được đều dừng để điều tra.

Theo tài liệu chính thức, [locations được scan đệ quy](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-locations-setting)
và [validate kiểm migration đã áp dụng so với source/checksum](https://documentation.red-gate.com/flyway/reference/commands/validate).
Vì vậy không được trỏ vào `db/` hoặc đồng thời hai thư mục history. Cơ chế đã được
thử bằng Flyway **12.4.0**, Java21, Spring Boot4.1.1, PostgreSQL17.11.

## Ma trận DB và kết quả

Cluster mới hoàn toàn: `127.0.0.1:55462`, data directory
`D:/toeic-learning-platform/backend/target/v34-rehearsal-20261117/pg`.
Mọi database đều có prefix `v34r_20261117_`; adapter thử nghiệm từ chối DB ngoài
identity này. Không khởi động lại hoặc migrate cluster cũ 55461/5432.
Sau khi hoàn tất kiểm tra đã xác minh lại identity và dừng riêng cluster55462;
DB/log/artefact được giữ trên đĩa để review. Không xóa thư mục dữ liệu.

Settings và RAG là **history tổng hợp**, tạo qua Flyway từ đúng SQL gốc, không
phải clone DB thành viên. Negative drift DB là clone của RAG tổng hợp.

| DB mới | Trước | Migration mới chạy | Sau | Khởi động lần 2 |
|---|---|---|---|---|
| empty | Trống | V1…V37, chỉ RAG V34 | V37; 38 history rows | Validate38, HTTP200 |
| settings | Settings V34 + V35 | V36, V37 | V37; V34 cũ giữ nguyên | Validate38, HTTP200 |
| rag | RAG V34 | V35, V36, V37 | V37; V34 cũ giữ nguyên | Validate38, HTTP200 |

Cả ba: migrate thành công, Flyway validate đạt, Hibernate `ddl-auto=validate`
đạt, ứng dụng HTTP khởi động và `/api/health/database` trả200. Mỗi lần chỉ resolve
**một** migration V34. Snapshot dữ liệu/history không đổi sau lần khởi động thứ2.

Đã đóng gói thêm `candidate-thin.jar` bằng JDK jar tool (không phải Spring Boot
executable fat JAR) và khởi động từ JAR này với dependency classpath riêng: cả
ba history validate38/Hibernate validate/HTTP200 và chỉ resolve một V34. JAR
không có V34 ở common root; hai SQL nằm đúng hai historical locations. Snapshot
dữ liệu/history tiếp tục giữ nguyên sau các probe từ JAR. Probe dùng pool tối đa3
để chia sẻ cluster thử; lần probe đầu với pool mặc định gặp giới hạn connection,
được ghi riêng trong `jar-boot-empty.log`.

Schema dump bỏ riêng history/owner/ACL/header ngẫu nhiên cho cùng SHA256:
`b8cc9501367da1340646abe7c830c6e738edcc181cbd3c3149f99399285dde3b`.
Danh mục permission code được sắp xếp cho cùng SHA256:
`c5b319ea1d09e92947abeb9dbbcfa40cd7ec80133b02f60409c84d786b19fd02`.
Schema có cùng bảng/cột/default/index/FK/constraint; history V34 cố ý khác theo
nguồn và grant thực tế có thể khác vì bảo toàn quyền đã thu hồi.

Đã snapshot cả 81 bảng trước khi nâng cấp mỗi history có sẵn. Đối chiếu giữ
nguyên mọi row cũ và mọi trường history cũ (theo installed_rank), gồm fixture
auth/question/media, attempt đã GRADED, Part result, hội thoại/message. Riêng RAG
có dữ liệu indexed version/chat request/citation snapshot trước upgrade.
Settings đã thay giá trị thành17/revision9 và có legacy key; tất cả được giữ
nguyên. RAG thêm cột revision=0 đúng migration, không đổi giá trị/key cũ.
Quyền Settings đã thu hồi trên Settings history và quyền AI đã thu hồi trên RAG
history không bị seed lại. Đây là fixture kiểm tra, không phải dữ liệu thực tế.

Các negative cases đã bị chặn:

- Script V34 lạ; tên script đã biết nhưng checksum lạ (tạo qua Flyway trên DB mới).
- Schema có dữ liệu nhưng không history; history thiếu V34; RAG thiếu index.
- Người vận hành cố override locations.
- Raw Flyway chọn sai historical location: validate thất bại.
- Raw Flyway quét đồng thời hai historical locations hoặc source chính còn hai
  V34: lỗi `Found more than one migration with version 34`.
- DB trống không có lựa chọn RAG tường minh.

Các ca bị adapter từ chối đã đối chiếu snapshot trước/sau, không ghi migration
hay dữ liệu. Không tạo history giả bằng INSERT/UPDATE/DELETE; history của fixture
checksum lạ cũng do Flyway tạo, không sửa history đã áp dụng.

## Kiểm thử và bằng chứng

Biên dịch trước commit và sau hợp source: Maven Wrapper `-DskipTests test-compile`
đạt; lần sau biên dịch lại main/test source. Không dùng Maven clean vì target
chứa các cluster và bằng chứng cần giữ.

Frontend: `npm test` đạt76/76; `npm run lint` exit0 với9 warning có sẵn;
`npm run build` đạt, còn warning bundle lớn. Lượt test/build đầu bị sandbox EPERM;
đã chạy lại với quyền tạo subprocess và đạt.
Chưa chạy browser E2E hoặc gọi Google/SMTP/LLM provider thật; các test backend
kiểm hợp đồng RAG bằng client giả lập. Không có commit mới trên remote để kiểm CI.

Backend full regression trên **bản cách ly**: **1120 test, 0 failure, 0 error,
0 skip**, BUILD SUCCESS, hoàn tất 18/11/2026 lúc01:04:07 +07, thời gian09:41.
DB là `v34r_20261117_regression_retry`, mới hoàn toàn. Lượt này giới hạn cache
Spring test context bằng JVM property `spring.test.context.cache.maxSize=8`;
không thay test/assertion, source hoặc schema production để làm xanh.
AuthFlow31/RBAC29/Settings18/moderation21/RAG chat9 đều đạt trong full run.

Lượt full đầu chạy1120 test, 0 failure và145 error do giới hạn100 connection
của cluster thử khi nhiều Spring context giữ pool; không được tính là đạt.
Lượt focused đầu cũng không đạt do lỗi thứ tự locations trong adapter thử;
đã sửa và toàn bộ test sau đó đạt như trên. Source chính sau merge chỉ được
biên dịch test thành công; chưa nhận là full regression/runtime đạt vì còn
duplicate V34. Kết quả green phía trên thuộc candidate có adapter/bridge cách ly.

Lệnh diễn tập và log, tính từ repository root:

```powershell
python backend/target/v34-rehearsal-20261117/rehearse.py run
# Trong backend, với datasource tường minh tới DB rehearsal:
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55462/v34r_20261117_regression_retry'
$env:SPRING_DATASOURCE_USERNAME='v34_rehearsal'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:APP_V34_EMPTY_LINEAGE='rag'
.\mvnw.cmd -o -f target/v34-rehearsal-20261117/input/backend/pom.xml test '-Dspring.test.context.cache.maxSize=8'
```

Artefact trong `backend/target/v34-rehearsal-20261117/`:
`result.json`, `before-*.json`, `after-*.json`, `schema-*.sql`,
`original-sql-sha256.json`, `isolated-source-diff.json`, các log `boot-*` và
`negative-*`, source adapter/harness và input backend cách ly. Không có thay đổi
nào trong test source giữa cây tích hợp và bản sao; chỉ bổ sung adapter, bridge
migrations và expected schema fingerprints vào bản sao.
Full-run log cuối là `full-tests-retry.log`; log/report thất bại ban đầu được giữ
ở `full-tests.log` và `first-full-reports/`. Artefact JAR và proof ở
`candidate-thin.jar`/`jar-result.json`; patch hợp nguồn ở
`integration-staged.patch`. `final-audit.json` ghi kiểm tra bàn giao cuối.

## Rủi ro và vận hành trước khi áp dụng

1. Chưa có rehearsal trên clone DB thành viên lâu dài. Cần inventory script,
   checksum, description, success, schema, version khác và backup có kiểm tra
   restore của từng DB được cấp phép. Không suy từ history tổng hợp rằng DB thực
   tương thích. Không truy cập `toeic_db` trong công việc này.
2. Adapter hiện cố ý khóa vào identity phòng thử. Trước khi đưa vào source cần
   chốt chính sách history/locations, schema `public`, table history, quyền chạy
   migrator, khóa rollout và cách cấu hình không cho override cơ chế guard.
   CLI Flyway độc lập phải có cùng preflight, không phát hướng dẫn chọn location
   thủ công theo cảm tính. Không copy nguyên adapter lab sang production.
3. Fingerprint phải được review và đóng gói cố định với artefact phát hành;
   không tự sinh từ DB đích để hợp thức hóa drift. Cần bổ sung ca failed/gapped
   history, schema khác, metadata ngoài phạm vi, timeout và triển khai đồng thời
   vào bộ kiểm tra vận hành trước rollout. Bộ hiện có chưa chứng minh mọi ca đó.
4. Cần giữ cả hai SQL lịch sử trong mọi bản phát hành tương lai nhưng mỗi DB chỉ
   resolve đúng một. Không xóa historical location khi refactor/build; không
   dùng ignoreValidation/repair/baseline để làm xanh. Kiểm artefact mới không có
   V34 thừa ở common root và kiểm lần restart sau migrate.
5. Khi triển khai được duyệt: dừng writer/rollout đồng thời; backup→restore vào
   clone mới; preflight→migrate→validate→Hibernate validate→smoke auth/RBAC,
   Settings/moderation/RAG→so schema/data/history→restart/validate. Chỉ áp dụng DB
   đã xác minh, theo change window riêng. Nếu thất bại: dừng, giữ log và history,
   điều tra/restore backup vào DB riêng; không rollback bằng sửa history.
6. Nếu không chấp nhận nhiều historical locations, phương án khác cần thiết kế
   và phê duyệt riêng, chẳng hạn artefact migrator riêng theo lineage với cùng
   common migration mới. Chỉ thêm migration version cao hơn không tự giải quyết
   được lỗi duplicate/checksum V34 trong bước resolve/validate.

File nền và settings IDE được kiểm SHA256 sau merge: 41/41 khớp; AuthFlow là file
duy nhất trong baseline được thay đổi có chủ đích theo yêu cầu. Không reset,
stash, force-push, sửa untracked cũ hoặc mở rộng G1/media/EXAM-04.
`git diff --cached --check` đạt. `git diff --check` toàn working tree báo trailing
whitespace ở AGENTS.md dòng1 đã tồn tại; giữ nguyên file nền này.

## Tiếp nối từ merge hiện tại

Xem [trạng thái tích hợp và restore backup thực tế ngày18/11](MIGRATION34_Integration_Status_20261118.md).
Backup local đã restore/đối chiếu được chỉ có history tới V33 và bị guard chặn;
không nhận nó là clone V34 thành viên đã nâng cấp. Adapter chưa được áp dụng vào
source chính, index/merge và 41 file nền vẫn được giữ nguyên.
