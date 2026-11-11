# Xung đột Flyway V27 — điều tra và diễn tập 2026-11-11

## Trạng thái

**Đã đổi riêng tên catalog sang `V27_1__system_vocabulary_catalog.sql`, giữ nguyên byte SQL.** Giữ V27 Section và V28–V30. Đã fetch và fast-forward nhánh hiện tại `feat/web-auth-integration` từ `7511371` đến `origin/develop` tại `e7f3cc0`, không tạo merge commit mới. Các thay đổi local không giao với 25 file nhận từ develop; AGENTS.md, Postman R2, tatus và sáu test dọn Problems được giữ nguyên. Không merge PR, không triển khai seed hoặc FE–BE mới. Phạm vi commit bàn giao được nêu bên dưới.

Người dùng đã xác nhận qua pgAdmin ngày 2026-11-11: `toeic_db` đúng datasource ứng dụng local, mới áp dụng đến V21 thành công, không có V27/V27.x. Ảnh history cho thấy V21 `V21__question_authoring_and_version_transcripts.sql`, checksum `3085076`, success=true. Dựa trên xác nhận này và audit DB test, người dùng chốt phương án V27.1. Đây là bằng chứng người dùng cung cấp, không phải truy vấn trực tiếp của agent vào DB 5432; phiên vẫn chưa có credential để backup/nâng cấp DB ứng dụng. Không suy lịch sử các DB thành viên từ kết quả local.

## Nguyên nhân và lịch sử Git

Trước sửa, trong 31 file SQL trên develop, **chỉ version 27 trùng**. Sau sửa có 31 version duy nhất: V1–V27, V27.1, V28–V30.

| File | Nguồn |
|---|---|
| `V27__section_attempt_configuration.sql` | `279ae7860260c9faae30d79cc60421bed05bd04e`, EXAM-01, pthanh137; vào develop qua PR #52 (`7e63a9d`). |
| `V27__system_vocabulary_catalog.sql` | Catalog ban đầu ở V24 trong `e5c7006`; `dbc22445203319ec99ee1e83ca693abaa6eb01c9` đổi chuỗi vocabulary từ V24–V27 thành V27–V30, không đổi byte SQL. Vào develop qua PR #51 (`e7f3cc0`). |

Flyway gặp hai resource cùng version khi resolve, trước khi có thể chọn theo lịch sử database. Đây là lỗi trong source trên develop; clean output đơn thuần không sửa được nguồn trùng. Sau khi đổi tên hợp lệ vẫn cần clean output để file tên cũ không tồn tại trong `target/classes/db/migration`.

## Phụ thuộc và checksum SQL

Checksum được đối chiếu bằng Flyway thực tế trong diễn tập, không thay SQL để đổi checksum.

| Migration | Phụ thuộc trực tiếp | Checksum |
|---|---|---:|
| V27 Section | `attempts` V5/V10, `roles`/`permissions` V2; không phụ thuộc catalog | -72063305 |
| V27.1 catalog (trước là V27 trùng) | Tạo `system_vocabulary_decks`, `system_vocabularies`, `system_deck_vocabularies` | 1287925395 |
| V28 enrollments | `users`, `vocabulary_decks`, **system_vocabulary_decks** | -1439625225 |
| V29 seed catalog | Ba bảng catalog do catalog migration tạo | 794467010 |
| V30 learning sessions | `users`, `vocabulary_decks` | 403924330 |

Không thể chỉ đưa catalog lên V31 rồi giữ V28/V29 chạy trước nó. V29 còn comment/temp-table mang tên V26 từ lịch sử; không sửa các byte đó trong phương án này.

## Lịch sử local đã đọc — phạm vi bằng chứng

Đã truy vấn SELECT read-only trên **22 database thuộc ba cluster kiểm thử** có sẵn: cổng 55459 (`q03_main_test`), 55439 (`auth_rbac_test`), 55432 (`postgres`). Gồm cả ba database `postgres` không có history. Không drop/reset/repair, không sửa history hoặc migration đã áp dụng.

V27 Section được áp dụng thành công ở bốn DB, cùng script/checksum:

| Database, cổng 55459 | V27 script | Checksum | Ghi chú |
|---|---|---:|---|
| toeic_exam01_foundation | V27__section_attempt_configuration.sql | -72063305 | History nhánh cũ thiếu V24/V25; không nâng cấp trực tiếp. |
| toeic_exam01_http_test | V27__section_attempt_configuration.sql | -72063305 | History nhánh cũ thiếu V24/V25; không nâng cấp trực tiếp. |
| toeic_web_auth_sync_verify | V27__section_attempt_configuration.sql | -72063305 | Đủ V1–V27; dùng làm nguồn clone diễn tập. |
| toeic_web_auth_sync_http_test | V27__section_attempt_configuration.sql | -72063305 | Đủ V1–V27; giữ nguyên DB gốc. |

Không thấy V27 catalog hoặc V28–V30 được áp dụng trong 22 DB này. Không coi điều đó là bằng chứng cho các DB chưa truy cập. Hai DB test cũ `toeic_prac02_verify` và `toeic_prac02_http_test` còn V24 Practice grading (`-856194547`), khác V24 Google hiện hành (`-628015191`); giữ nguyên, không repair/đổi lịch sử để ép chúng khớp develop.

DB `127.0.0.1:5432` ban đầu chưa đọc được (`fe_sendauth: no password supplied`); sau đó chủ DB đã xác nhận V21 và không có V27/V27.x qua pgAdmin như trên. Truy vấn sau dành cho việc đối chiếu các môi trường khác; chỉ chia sẻ **kết quả, không chia sẻ mật khẩu**:

```sql
SELECT version, script, checksum, success
FROM flyway_schema_history
WHERE version::text ~ '^(24|25|26|27|28|29|30|31|32|33|34)(\.|$)'
ORDER BY installed_rank;
```

Nếu bảng ở schema khác public, dùng schema thực tế. Cần kiểm tra tương tự với DB nào đã chạy chuỗi vocabulary, kể cả tên số V24–V27 cũ trước `dbc2244`.

## Phương án đã thực hiện — giữ Section V27, catalog chưa áp dụng đổi sang V27.1

Đã chốt với người dùng cho DB local có bằng chứng V21 và các DB test đã audit. Không tự triển khai lên DB thành viên chưa đối chiếu history:

1. Giữ nguyên `V27__section_attempt_configuration.sql` và checksum đã áp dụng.
2. Đổi **tên file, không đổi SQL**: `V27__system_vocabulary_catalog.sql` → `V27_1__system_vocabulary_catalog.sql`.
3. Flyway hiểu version mới là **27.1**. Số này không có trên bất kỳ remote branch đã fetch; thứ tự là `27 Section → 27.1 catalog → 28 enrollments → 29 seed → 30 sessions`.
4. Giữ nguyên V28–V30. Không outOfOrder, không tắt validation hoặc thêm ignore patterns. Không sửa history/repair.

Số 27.1 giữ phụ thuộc catalog trước V28/V29 và chỉ cần đổi một tên file; không phải đẩy cả chuỗi chưa áp dụng sang V31–V34. Cần nhóm thống nhất version này trước khi có migration mới trùng số.

## Nếu cả Section V27 và catalog V27 đã áp dụng ở các DB khác nhau

**Không áp dụng phương án đổi tên đơn thuần.** Một file V27 không thể đồng thời khớp hai checksum. Migration mới chạy sau cũng không tự giải quyết validation của lịch sử V27 đã phân nhánh.

Cần kế hoạch chuyển đổi được review riêng:

1. Thu thập đầy đủ history/schema và backup từng DB; xác định có lịch sử vocabulary V24–V27 cũ hay không.
2. Chọn lịch sử chuẩn cho DB mới và cách hỗ trợ từng dòng lịch sử đã tồn tại.
3. Xem xét giữ các migration đã áp dụng trong bộ location riêng theo dòng lịch sử, bổ sung forward migrations có kiểm chứng cho phần schema còn thiếu; hoặc tạo DB mới theo lịch sử chuẩn rồi chuyển dữ liệu có kiểm tra FK/count và đối soát, giữ DB cũ để rollback.
4. Diễn tập từng đường nâng cấp trên bản sao, review việc chuyển connection trước khi đụng DB thật.

Đây là các hướng cần thiết kế sau khi có history thực tế, **chưa triển khai**. Không tự viết lại history, repair, drop/reset DB hoặc sửa SQL đã áp dụng để làm xanh CI.

## Diễn tập trước khi chốt phương án — lịch sử

Trước khi người dùng xác nhận history DB ứng dụng, đã tạo bản sao filesystem của 31 migration trong `backend/target/migration27-candidate-sql`; chỉ đổi tên catalog trong bản sao. Lượt này không thay source. Sau xác nhận mới áp dụng tên V27.1 vào source, kiểm chứng lại bằng compiled resources như mục dưới.

Dùng Flyway đúng dependency runtime của backend, `validateOnMigrate=true`, sau migrate gọi validate tiếp. Không bỏ qua validation. Lượt chuẩn bị đầu gọi validate độc lập trên DB chưa migrate báo các migration pending; đã sửa harness dùng migrate với validation mặc định, không đổi SQL hoặc rule validation.

| Database test mới trên 55459 | Kết quả thực tế |
|---|---|
| toeic_v27_candidate_fresh | Từ DB trống: áp dụng **31 migration**, đến V30, validate đủ 31 thành công. |
| toeic_v27_candidate_upgrade | Clone nguyên DB toeic_web_auth_sync_verify có V1–V27: áp dụng **4 migration** 27.1/28/29/30, validate đủ 31 thành công. Toàn bộ 27 history row/checksum cũ giữ nguyên. |

Chạy lại SELECT so sánh sau diễn tập: history của **22 DB gốc không đổi**, mọi file local có sẵn và 31 SQL source không đổi. V29 chỉ chạy như một phần migration có sẵn trên hai DB test mới; không bổ sung seed hoặc chạy seed trên DB cá nhân.

Harness/log/audit JSON nằm trong backend/target, không commit. Không dùng 95 test dọn Problems hoặc CI 899 test cũ làm bằng chứng cho bản sửa hiện tại.

## Kiểm chứng source sau sửa và clean build

- Đổi tên catalog, không đổi SQL: SHA-256 trước/sau **`446b800dfe34de2155d2caea37ec95d8b98166f5710933c2ba25a5443cd76a96`**, Flyway checksum giữ **1287925395**. V27 Section giữ **-72063305**; V28–V30 giữ checksum trong bảng trên.
- Đã clean riêng các output sinh tự động: `target/classes`, `test-classes`, `generated-sources`, `generated-test-sources`, `maven-status`, `surefire-reports` (và `surefire` nếu có), sau khi kiểm tra absolute path nằm trong backend/target. Đã lưu reports cũ vào `target/migration27-before-clean-reports`. **Không xóa toàn bộ target** vì có ba cluster PostgreSQL, snapshot và log cần giữ.
- Sau compile, 31 SQL resource trong `target/classes/db/migration` khớp source từng byte, version duy nhất; không còn `V27__system_vocabulary_catalog.sql` cũ. Các test Spring khởi động với `classpath:db/migration` bình thường; không dùng location thay thế để che source trùng.
- Lượt Maven đầu dừng ở startup, không còn process và không có test result; không tính đạt. Đã chạy lại đúng nhóm liên quan và lưu log riêng.

Flyway probe sau sửa dùng **classpath compiled resources**, `validateOnMigrate=true`, validate sau migrate; không ignore/outOfOrder/repair:

| DB test mới, cổng 55459 | Nguồn và kết quả |
|---|---|
| toeic_v27_fix_fresh | DB trống → chạy **31** migration đến V30; validate 31 thành công. |
| toeic_v27_fix_upgrade21 | Clone DB test toeic_q03_main_test có V1–V21 → chạy **10** migration đến V30; validate 31 thành công. Kiểm chứng đường nâng cấp V21 tương ứng version của toeic_db, không phải clone dữ liệu DB cá nhân. |
| toeic_v27_fix_upgrade27 | Clone toeic_web_auth_sync_verify có V1–V27 Section → chạy **4** migration đến V30; validate 31 thành công. |

Test liên quan bằng Maven Wrapper `mvnw.cmd -o test` với `-Dtest=BackendApplicationTests,SectionAttemptIntegrationTest,SectionConcurrencyTest,PracticeSubmissionIntegrationTest,VocabularyReviewIntegrationTest,LearningProfileServiceTest,VocabularyDeckServiceTest`: **85 test, 0 failure/error/skip, BUILD SUCCESS, 01:01 phút** trên DB mới `toeic_v27_fix_targeted`. Log `target/migration27-targeted-retry.log`.

Full backend cuối đã chạy **một lượt** `mvnw.cmd -o test`, không filter test, DB mới `toeic_v27_fix_full`: **899 test, 0 failure/error/skip, BUILD SUCCESS, 07:58 phút**, kết thúc 2026-11-11 18:51:59 +07. Exit code 0; 50 Surefire XML suites khớp tổng 899/0/0/0. Log `target/migration27-full-tests.log`, exit marker `target/migration27-full-tests.log.exit`. Dùng Java 21, datasource test/pool 6, Mockito javaagent có sẵn và JVM 384 MB theo setup đã dùng. Không lặp suite sau khi chỉ cập nhật tài liệu.

Clean-build log xác nhận javac đã biên dịch lại **263 source Java và 52 source test** trước lượt test liên quan. Full suite sau đó tái sử dụng đúng output này, không đổi source. Bằng chứng này thuộc working tree hiện tại, gồm cả sáu file test dọn Problems chưa commit từ trước; không gọi đây là kiểm chứng một commit/staged tree riêng.

Đối chiếu sau kiểm chứng: các DB gốc trong cluster 55459 vẫn giữ history cũ; hai clone V21/V27 giữ toàn bộ 21/27 row/checksum ban đầu. Source SQL và compiled SQL giữ nguyên checksum như manifest, không còn duplicate/stale resource. Hash các file local có sẵn (`AGENTS.md`, Postman R2, `tatus`, sáu test và `.vscode/settings.json`) không đổi. `git diff --check` phần nhiệm vụ và kiểm tra whitespace file tài liệu mới đạt. Các dịch vụ PostgreSQL test do phiên này khởi động được dừng sau kiểm chứng. Không chạy full suite trên `toeic_db` hoặc sửa DB đó.

## Phạm vi bàn giao commit

Theo yêu cầu bàn giao tiếp theo, chỉ stage/commit phần rename 100% `V27__system_vocabulary_catalog.sql` → `V27_1__system_vocabulary_catalog.sql` và tài liệu này. Không có tham chiếu tên file khác trong source/docs/workflow cần cập nhật. Sáu sửa Problems, AGENTS.md, Postman R2, tatus và các artifact kiểm thử nằm ngoài commit.

Thông điệp commit: `fix(db): resolve duplicate V27 migration version`; author/committer `pthanh137 <sleapf137@gmail.com>`. Fetch trước bàn giao vẫn thấy origin/develop ở `e7f3cc0`, remote feature ở `7511371`, chưa có version 27.1 trên remote. Không đổi Java hoặc SQL, vì vậy giữ bằng chứng 899 test, không chạy lại full suite local chỉ để commit. Lượt 899 test bao gồm sáu sửa Problems ngoài commit như đã ghi ở trên; CI của SHA mới sẽ kiểm chứng chính cây commit trong checkout sạch. Không gọi bằng chứng working-tree này là kết quả riêng của staged tree.

Staged diff phải nhận diện rename 100%, không có hunk SQL; kiểm tra secret/artifact, danh sách file và `git diff --cached --check` trước commit. Push thường bằng tài khoản pthanh137, xác minh CI đúng SHA cuối; không force-push hoặc merge PR.

## Backup và nâng cấp toeic_db — ngoài phạm vi bàn giao

**Không nâng cấp, reset hoặc repair toeic_db trong lần bàn giao này**, theo yêu cầu người dùng. Các kiểm chứng chỉ chạy trên DB riêng. Phiên trước chưa tạo được backup hoặc nâng cấp toeic_db do thiếu credential. Hướng dẫn bên dưới chỉ là tài liệu cho một lần nâng cấp được yêu cầu riêng về sau, không phải các lệnh đã thực thi. Không lấy password từ pgAdmin hoặc đưa secret vào chat/source/log.

Người dùng có thể tạo backup từ pgAdmin: chọn `toeic_db` → **Backup…** → **Format: Custom**, giữ đủ schema/data (không chọn only-data/only-schema), lưu file vào vị trí riêng, ví dụ `D:\toeic-learning-platform\backups.local\toeic_db_before_V27_1_20261111.backup`. Thư mục `*.local` bị Git ignore; cần bảo vệ file vì chứa dữ liệu ứng dụng.

Nếu credential đã được cấp qua môi trường/pgpass, lệnh backup tương ứng (không ghi password trong command):

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\pg_dump.exe' -h 127.0.0.1 -p 5432 -U postgres -d toeic_db -Fc -w -f 'D:\toeic-learning-platform\backups.local\toeic_db_before_V27_1_20261111.backup'
& 'C:\Program Files\PostgreSQL\17\bin\pg_restore.exe' --list 'D:\toeic-learning-platform\backups.local\toeic_db_before_V27_1_20261111.backup'
```

Trước khi nâng cấp thật, kiểm tra backup tồn tại/đọc được và diễn tập restore vào DB test mới, đối chiếu history V21 cùng dữ liệu cần bảo toàn. Sau đó mới migrate datasource `toeic_db` với validation bật và bộ migration đã sửa; kỳ vọng thêm 10 migration đến V30, V21/checksum cũ giữ nguyên. Không reset, sửa history, repair hoặc tắt validation. Nếu history tại thời điểm nâng cấp khác bằng chứng V21, dừng và đối chiếu lại trước khi chạy.
