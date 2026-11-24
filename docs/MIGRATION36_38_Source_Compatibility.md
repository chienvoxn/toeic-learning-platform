# Tích hợp hai lineage V36–V38 — 22/11/2026

Phạm vi: tương thích migration để tích hợp develop `314f1380411a7611b6252f4bc03381d56e770ed9` với feature tại `1f5d1bcaa7adad6e382694014f5c62824a928b9c`. Không thêm chức năng. SQL/history/checksum đã phát hành và chính sách [V34](MIGRATION34_Source_Compatibility.md)/[V35](MIGRATION35_Source_Compatibility.md) giữ nguyên. CI #200 là bằng chứng của nguồn cũ, không phải cây tích hợp này.

## Lựa chọn lịch sử và dependency

MigrationHistory nhận diện tuple script/description/checksum của V36 thực tế, kiểm toàn bộ prefix/checksum và full metadata trước mutation. Không có cấu hình cho người vận hành tự chọn lineage, không dùng DB name/port/user/host để lựa chọn. MigrationConfiguration giữ advisory lock và dùng cùng coordinator cho Spring Boot, integration test, CLI và CI.

| History | Đường nâng cấp |
|---|---|
| Trống/V33 hoặc V34/V35 hợp lệ chưa có36 | Giữ mặc định RAG34/ADMIN35 hiện hành; chọn completion36–38, tiếp tục39–43 |
| Completion36–38, kể cả DB đã tới42 | Giữ nguyên history36–42; V43 bổ sung archive/idempotency/quyền vocabulary còn thiếu; không chèn migration cũ dưới42 |
| RAG34 + RAG35 + vocabulary36/37/38 | Giữ SQL/history vocabulary; V38.1 bổ sung Settings và moderation trước V41; RAG đã có từ34 được kiểm, không chạy lại |
| Prefix vocabulary36 hoặc36/37 | Được kiểm bằng SQL gốc; tiếp tục đủ37/38 rồi bridge38.1 và39–43 |
| Settings34+RAG35, vocabulary kết hợp V34/V35 chưa nhận diện, mixed36–38, checksum/script/schema sai | Chặn trước mutation; không fallback location hoặc sửa history |

V41 bắt đầu bằng ALTER moderation_versions. Vì vậy bridge riêng của lineage vocabulary nằm sau38 và trước39–41. Bridge không thuộc migration set completion; DB completion đã tới42 không có pending38.1. V43 trên vocabulary kiểm invariant đã có và không seed lại grant đã bị thu hồi. Không bỏ một phía schema để hết duplicate.

Sáu SQL lịch sử được chuyển nguyên nội dung vào `db/history/v36/completion` và `db/history/v36/vocabulary`; mỗi lần chỉ resolve một bộ36–38. Giữ nguyên các location lịch sử34/35. Common `db/migration` không còn SQL36–38. V38.1 và V43 là số mới, đã kiểm không xuất hiện trong45 remote refs tại lần fetch này; lần phát hành tiếp cần fetch lại.

| SQL lịch sử | Checksum Flyway | Commit nguồn |
|---|---:|---|
| V36__complete_settings.sql | -725710829 | 0e6b37c |
| V37__complete_rag.sql | -1340096665 | 0e6b37c |
| V38__complete_moderation.sql | 932514876 | 012c257 |
| V36__vocabulary_archive_and_catalog_deck_deletion.sql | 747431657 | eae9562 |
| V37__vocabulary_review_idempotency.sql | -469993577 | 4b3fcea |
| V38__vocabulary_learning_permission.sql | -2132296234 | e2a2f8d |

SQL vocabulary và semantics V43 tái sử dụng từ Phuoc qua PR66, giữ attribution trong migration mới. Các file Windows có thể khác CRLF/LF; Git blob và checksum Flyway của SQL lịch sử giữ nguyên.

## Trách nhiệm và an toàn

- MigrationHistory: giữ nhận diện34/35, nhận diện36, kiểm prefix theo đúng lineage và toàn bộ resolved script/checksum trước mutation. Mixed lịch sử hoặc thiếu bridge sau38 bị chặn.
- MigrationSchema: giữ fingerprints39–42 và whitelist restore cũ. Ghép đúng14 dòng metadata vocabulary từ canonical SQL độc lập, kiểm column/FK/table/index/check và permission catalog theo prefix; không học schema chuẩn từ DB đích hoặc nới so sánh metadata.
- MigrationConfiguration: chọn resource lịch sử từ kết quả kiểm history dưới advisory lock, migrate rồi validate và kiểm metadata lại. Không repair, clean, baseline, outOfOrder hoặc tắt validation.
- V38.1: chỉ nhận released vocabulary/RAG34/RAG35, từ chối partial completion; thêm Settings/moderation còn thiếu, không suy/backfill contributor.
- V43: completion nhận schema vocabulary và quyền theo develop; vocabulary chỉ kiểm phần đã có, giữ revocation. Dữ liệu đã pin và SQL40–42 không đổi.

## Bằng chứng candidate và giới hạn

Artifact ignored ở `backend/target/v36-compat-20261122/`:

- `candidate-test-proof.json`:72 ca migration unique có bằng chứng đạt qua các lượt được ghi riêng. Lượt đầu48/49 migration và3 unit đạt; một fixture restore validate42 trong release43 bị chặn đúng, đã sửa fixture lên43. Lượt mới22/22 đạt (restore2, vocabulary20). Lỗi pagefile/native memory và lượt PostgreSQL chưa ready được giữ log, không tính pass.
- `candidate-build-proof.json`: package candidate đạt, JAR có50 SQL, toàn bộ resource DB khớp source.
- `matrix-final-proof.json`, `matrix-result.json`:8/8 đường migrate/validate/CLI và Boot/JPA/health hai lần, cùng schema canonical; giữ dữ liệu/history/sequence và grant đã thu hồi.
- `completion42-source-proof.json`: backup/restore DB lab V42 từ binary B đã bàn giao, source chỉ đọc và giữ nguyên. `vocabulary38` là clone fixture dựng bằng SQL gốc develop314f; không phải clone DB thành viên.

Các đường matrix: trống, Settings34, RAG34, Settings34+ADMIN35, RAG34+ADMIN35, RAG34+RAG35, clone vocabulary38 và clone completion42. Canonical metadata lấy từ chuỗi SQL gốc trên DB mới độc lập. So dữ liệu theo cột đã có, history prefix nguyên vẹn và sequence không lùi; sequence cấp ID quyền được phép tiến khi thêm row mới, không yêu cầu đóng băng sequence. Khởi động lại không mutation. Scheduler expiry tắt riêng trong diễn tập bảo toàn dữ liệu; worker cạnh tranh/retry được kiểm bằng nhóm test riêng, không dùng fixture này để nhận playback thật hoặc runtime Full Mock chuẩn đã đạt.

Chưa có clone/history DB thành viên lâu dài. CI develop194 chỉ chứng minh SQL vocabulary chạy trên PostgreSQL tạm; CI200 chỉ chứng minh nguồn feature trước delta. Không suy mọi DB thành viên tương thích từ fixture hoặc hai CI đó.

## Vận hành

1. Inventory history/checksum/schema và backup DB được phép; restore sang DB mới riêng. Không thao tác DB cá nhân `toeic_db`.
2. Dùng `backend/scripts/migrate-database.ps1 -Action Migrate` với SPRING_DATASOURCE_URL/USERNAME và credential trong môi trường đã cấu hình; sau đó `-Action Validate`. CLI không yêu cầu tự chọn location; không hỗ trợ standalone Flyway bypass coordinator.
3. Kiểm hội tụ schema, dữ liệu/history/sequence/grant và Boot/JPA/health hai lần trước DB đang dùng. History lạ phải dừng để điều tra.
4. DDL có thể lấy khóa: ALTER FK, CREATE TABLE/INDEX và completion cũ. Chưa benchmark DB thành viên lớn; cần cửa sổ bảo trì và đo trên clone thực tế.
5. Binary B chỉ nhận tới42; sau upgrade43 không rollback binary đơn thuần. Rollback bằng restore backup sang DB riêng và binary phù hợp, không xóa history hoặc repair.

Kết quả nguồn chính, package, hồi quy và manifest được cập nhật sau khi kiểm xong; không gán bằng chứng candidate thành kết quả nguồn chính.

## Bằng chứng nguồn chính sau tích hợp

Git merge develop314f tự kết hợp IdentitySecurityConfig, AuthFlow, API và ERD, không có entry unmerged. HEAD vẫn1f5d1bc, MERGE_HEAD314f; chưa commit/push/PR. Compatibility copy đúng candidate đã kiểm; khác CRLF/LF của SQL không đổi Git blob hoặc checksum. Fetch kiểm cuối chỉ thấy nhánh Teacher chưa merge tiến thêm; develop vẫn314f và không có V38.1/V43 trong45 remote refs đã rà, không lấy thêm nhánh thành viên.

- `main-build-related-proof.json`: compile/test-compile/package đạt104 test, 0 failure/error/skipped: AuthFlow31, RBAC29, FullMock15, worker10, vocabulary review19. JAR chính có60 resource DB/50 SQL khớp nguồn; output DB cũ được chuyển sang bản lưu ngoài classes trước build. Không còn SQL36–38 trong common; resolved set completion44 versions, vocabulary45 versions, chỉ một SQL cho mỗi version được chọn. JAR cố ý giữ cả hai bộ historical, không phải lỗi duplicate trong resolved set.
- JAR main cố định `backend/target/v36-compat-20261122/main-runtime.jar`, SHA256 `54005a15bebf2938491a95c852edde5486f386268eda6c64e6760479dd3e9708`. V38.1 checksum `-781105421`, V43 `-684908108`; sáu SQL lịch sử giữ nguyên Git blob/checksum.
- `main-runtime-proof.json`: chính JAR mới qua CLI migrate/validate và Boot/JPA/health hai lần trên clone sau upgrade của cả vocabulary/completion; dữ liệu/history/sequence và source giữ nguyên trước phần HTTP. 16 kiểm HTTP thật đạt: auth/permission/ownership, idempotent review chỉ một request/history row, archive giữ review history và grant vocabulary đã thu hồi vẫn403. Ca đầu của driver HTTP giả định nhầm DTO me có permissions đã được sửa sang GET vocabulary thực tế; không sửa API/source để đáp ứng fixture. Phần restart đã đạt được giữ bằng chứng, chỉ kiểm lại HTTP còn thiếu.
- `main-cli-wrapper-r2.log`: script migrate-database.ps1 -Action Validate chạy thành công trên lineage vocabulary đã nâng43. Lần đầu dùng JAVA_TOOL_OPTIONS bị PowerShell coi thông báo stderr là lỗi; bỏ biến đó và truyền JVM limit qua Maven, không đổi script hoặc nới guard.
- Frontend tích hợp:99 unit test, lint/build exit0 còn warnings; Chrome thật chạy vocabulary smoke gồm hai tab, reload, retry mất response và Again deferral đạt. API trong browser smoke là HTTP fixtures, không phải backend thật hoặc playback/media thật. Kiểm HTTP JAR ở dòng trên là bằng chứng khác.

Full backend **một lượt cuối đạt BUILD SUCCESS:1.266 test,0 failure,0 error,1 skipped,78 lớp** trên working tree source chính. Ca skipped là `VocabularyMlInferenceServiceTest.generatedModelMatchesPythonProbabilityWhenProvided`, chỉ chạy khi cung cấp system property `tv3.test.model`; không skip migration, quyền hoặc worker. Bằng chứng `main-full-proof.json`, `main-full-reports/` và `main-full-backend.log`; tổng XML khớp summary Maven. Full này gồm cả hai CSV test untracked nền đang có, không đưa chúng vào commit. Source không đổi từ package/JAR đã kiểm. 1.235 test working tree cũ và CI200/1.225 test vẫn thuộc nguồn trước delta, không đổi nhãn.

Lệnh nguồn chính: Maven Wrapper `.\mvnw.cmd -o test` trên datasource lab tường minh; argLine Xmx512m/metaspace256m/SerialGC/ActiveProcessorCount2/code-cache64m/context-cache2, Hikari4/idle0. Related/package dùng `-Dtest=AuthFlowIntegrationTest,RbacManagementIntegrationTest,FullMockIntegrationTest,AttemptExpiryIntegrationTest,VocabularyReviewIntegrationTest`. Frontend `npm test`, `npm run lint`, `npm run build`, rồi `node scripts/vocabulary-review-ui-smoke.mjs` trên production preview với HTTP fixtures. Không rerun full để che failure; các lỗi setup/fixture candidate đã được chẩn đoán trước lượt full cuối. JAR/preview/Chrome đã dừng; sau khi xác nhận không có client khác, cluster test mới cũng đã shutdown sạch, giữ DB/dump/log để review.

Hash42 file nền/settings,526 candidate/evidence và các artifact bằng chứng cố định giữ nguyên. Maven tạo lại hai output mặc định backend-0.0.1-SNAPSHOT.jar/.jar.original; JAR đã dùng làm bằng chứng EXAM03 cũ vẫn có bản byte-identical `exam03-final-runtime-20261122.jar` (SHA97d36359…). `.jar.original` là output trung gian, không có bản byte-identical cũ tìm thấy sau package; không gọi hai output mặc định này là còn nguyên. Chi tiết ở default-output-preservation-audit.json. Cluster lab cũ đã recovery tự nhiên, chỉ đọc backup rồi dừng sạch; không repair/history mutation.

Xem [manifest](MIGRATION36_38_Manifest_20261122.md):33 path tự stage bởi merge,20 path compatibility ngoài index và4 tài liệu. Index chưa gồm move/deletion/resource mới nên chưa phải cây commit hợp lệ; source được kiểm là working tree, không nhận staged source đã đạt. Không đưa6 tracked/35 untracked nền vào commit sau này.

## Kiểm nguồn staged để hoàn tất merge — 23/11/2026

Các trạng thái33/20 path và chưa stage phía trên là ảnh bàn giao ngày22/11. Lượt mới đã review, stage đúng nhiệm vụ và xuất Git index riêng; kết quả cuối là51 path diff/48 thay đổi có nhận diện rename, không gồm ba path vocabulary common tạm của merge. Không có file nền, dump/log/JAR trong index. Giữ Git blob/checksum SQL lịch sử và chính sách34/35; kiểm bốn resolved set không duplicate (completion44/vocabulary45 versions).

Compile/test-compile/package độc lập đạt;176 ca liên quan có bằng chứng pass qua hai lượt được ghi tách: lượt đầu138 pass/38 error do Windows pagefile1455 làm PostgreSQL recovery; trên cluster mới39/39 ca của hai nhóm bị ảnh hưởng đạt. Không thay source, nới guard hoặc tắt validation. Không gọi lượt đầu là pass. Staged frontend unit99/99 đạt; không chạy lại full local. Full working tree1.266 test/1 skipped và browser HTTP fixtures ở phần trước giữ đúng nhãn, không chuyển thành staged full hoặc E2E với BE thật.

JAR staged có60 resource DB/50 SQL khớp nguồn index. Restore backup lab completion42 và vocabulary38 dựng từ SQL gốc develop sang hai DB đích mới; migrate/validate/CLI và Boot/JPA/health hai lần đều đạt. Schema cùng canonical, prefix history và dữ liệu cũ được giữ, sequence không lùi, restart không mutation. Đây là clone lab/fixture, chưa phải DB thành viên lâu dài. Sau kiểm chỉ bổ sung hai tài liệu, code/config/test/resource không đổi. Xem artifact ignored `backend/target/v36-merge-20261123/` và manifest cho danh sách/hunk cùng bằng chứng staged.

Merge commit lượt này được phép có parents1f5d1bc và314f138, author/committer pthanh137 <sleapf137@gmail.com>. Push thường và CI phải được xác nhận trên SHA commit thực tế; CI200 không là bằng chứng của merge mới. Maven output mặc định đã được tái tạo trong lượt trước, `.jar.original` cũ không có bản lưu riêng; lượt kiểm staged mới dùng build output riêng và không ghi đè các JAR/bằng chứng cố định cũ.
