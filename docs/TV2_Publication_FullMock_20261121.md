# TV2 — TestVersion publication và Full Mock

Nguồn: working tree trên `ee95862df7b1aab35a9dd5cbce1857b9aca404a3`, nhánh `feat/web-auth-integration`. Lượt này không stage/commit/push. CI #187 thuộc ee95862, không phải bằng chứng code mới. Attribution EXAM-04 tái sử dụng PR #61 vẫn giữ tại d3d6f0b; không nhập lại nhánh thành viên.

Đã fetch origin trong lượt này: develop `98d81c0d2d2d3033fd018ecb9d5bb05780b61b39`; rà 42 remote refs chưa có V41. Các commit 012c257, e851f11 và d3d6f0b còn là ancestors của HEAD. Identity repository vẫn pthanh137 <sleapf137@gmail.com>.

## Contract và AC

| AC | Triển khai | Giới hạn |
|---|---|---|
| EXAM-04 publication | DRAFT → IN_REVIEW → APPROVED → PUBLISHED; return/revoke có lý do | Dùng ADMIN-03, không tự publish question/group |
| Quyền và evidence | ACTIVE + ADMIN + test:review/test:publish; expectedRevision; contributor theo cycle; chống tự duyệt/publish; audit cùng transaction | Legacy thiếu evidence bị chặn, không suy tác giả lịch sử |
| Version fork | Chỉ đúng current published; một working version; provenance; published chỉ đọc | Fork sao chép đúng refs cũ; giáo viên phải thay refs lỗi thời tường minh trước gửi duyệt |
| Retire/unpublish | Chặn start mới, giữ pointer/history/attempt; cùng parent lock với start | `unpublish` là alias của retire trong gói này, không phải xóa version |
| EXAM-02 runtime | TEST_VERSION qua năm route attempts hiện có; pin version/order/group/blueprint/deadline; lưu/thay/bỏ/reload; chấm và kết quả DB idempotent | Không quy đổi TOEIC, không worker EXAM-03 |
| Blueprint | Config immutable qua API; code/version, 7 Part, counts/group bounds/duration/media rules; snapshot tại publish và start | Chưa seed preset chính thức khi số liệu chưa được xác nhận |
| Media/G1 | Kiểm whole current published groups và media owner/grant, visibility/type; không ADMIN bypass; start kiểm lại grant publication | G1 atomic group publication chưa triển khai; không khẳng định publish bank toàn bộ hoặc playback storage thật |
| NFR-02 | Ownership, fresh RBAC mutation guard, parent/version/bank/media/grant locks, revision/audit transaction | Không tuyên bố benchmark tải hoặc vận hành production |

## Blueprint đang chờ xác nhận

LLD §6.1 gọi 200 câu là “tham khảo”; SRS/HLD không chốt số nhóm/thời lượng. Đã gửi duy nhất gói đề xuất sau, chưa coi là phê duyệt:

| Part | Số câu đề xuất | Nhóm đề xuất |
|---|---:|---|
| 1 | 6 | Câu độc lập |
| 2 | 25 | Câu độc lập |
| 3 | 39 | 13 × 3 |
| 4 | 30 | 10 × 3 |
| 5 | 30 | Câu độc lập |
| 6 | 16 | 4 × 4 |
| 7 | 54 | 15 nhóm, mỗi nhóm 2–5 câu |

Đề xuất `FULL-LR/v1`, Listening rồi Reading, Part 1–7, tổng 200 câu/120 phút; chưa có khóa pha theo section. Không dùng fixture 13 câu/10 phút làm preset TOEIC. API cấu hình chỉ dành ACTIVE ADMIN có test:manage:any; không có API sửa nội dung blueprint đã tạo, thay đổi phải tạo version mới.

## API thực tế

Teacher DRAFT create/read/update/question-units giữ contract trước. Response bổ sung blueprintId, currentPublishedVersionId, sourceVersionId, evidenceComplete, approvedRevision và capabilities.

| Route | Payload / quyền |
|---|---|
| GET /api/v1/teacher/tests/{testId}/versions | Owner/manage:any hoặc ADMIN review/publish |
| GET /api/v1/teacher/blueprints | Người có quyền test author/review/publish |
| POST /api/v1/teacher/blueprints | ACTIVE ADMIN + test:manage:any; code, version, name, durationSeconds, parts |
| PUT /api/v1/teacher/tests/{testId}/versions/{versionId}/blueprint | `{expectedRevision,blueprintId}`; DRAFT owner/manage:any |
| POST …/{versionId}/submit-review | `{expectedRevision}`; author |
| POST …/{versionId}/approve | `{expectedRevision}`; ACTIVE ADMIN + test:review, ngoài contributor cycle |
| POST …/{versionId}/return hoặc revoke | `{expectedRevision,reason}`; ACTIVE ADMIN + test:review |
| POST …/{versionId}/publish | `{expectedRevision}`; ACTIVE ADMIN + test:publish, ngoài contributor cycle; approval đúng revision, blueprint/media/bank hợp lệ |
| POST …/{versionId}/fork | `{expectedRevision}`; owner/manage:any; đúng current published |
| POST …/{versionId}/retire hoặc unpublish | `{expectedRevision}`; ACTIVE ADMIN + test:publish |
| GET /api/v1/tests | exam:manage:self; metadata tối đa 100 đề current published có snapshot; start kiểm lại eligibility |
| POST /api/v1/attempts | `{sourceType:"TEST_VERSION",testVersionId:"uuid"}`; không nhận blueprint/thời hạn/câu/actor từ client |
| GET /api/v1/attempts/{id} | Phiên của actor, đáp án đã lưu; không answer key/explanation/transcript |
| PUT …/{id}/answers/{questionVersionId} | `{selectedChoiceId:"uuid"}` hoặc null để bỏ; choice phải thuộc pinned question |
| POST …/{id}/submit | Không body; khóa attempt, chấm pin, lưu kết quả; replay đọc DB |
| GET …/{id}/result | Chỉ GRADED, đúng owner/quyền; không chấm lại |

Một part rule gồm `partNumber,questionCount,groupCount,minGroupSize,maxGroupSize,requireAudio,requireImage`. Part 1/2/5 độc lập (groupCount=0, bounds=null); Part 3/4/6/7 nguyên nhóm theo thứ tự bank, có bounds. Listening yêu cầu audio; Part 1 có image. Publication kiểm mọi media link, kể cả link không phải media bắt buộc.

Lỗi: 400 VALIDATION_ERROR; 401 chưa đăng nhập; 403 thiếu quyền/không ACTIVE ADMIN; 404 sai owner/version; 409 TEST_REVISION_CONFLICT/STALE_REVISION, CONTRIBUTOR_EVIDENCE_REQUIRED, SELF_REVIEW_FORBIDDEN, BLUEPRINT_REQUIRED/BLUEPRINT_NOT_AVAILABLE/BLUEPRINT_*_MISMATCH, PUBLICATION_MEDIA_UNAVAILABLE, WORKING_DRAFT_EXISTS, CURRENT_PUBLISHED_REQUIRED, TEST_NOT_OFFERED; 422 TEST_REFERENCE_INVALID cho bank ref sai/đã thay đổi. Không tự thay question/group version để giải lỗi.

Full Mock dùng deadline blueprint. Hết hạn chặn ghi đáp án; explicit first submit sau hạn chấm các đáp án đã lưu và giới hạn duration theo deadline, như timed Section. Không worker tự submit. Retire không thay deadline/snapshot/result và không bỏ kiểm quyền hiện hành.

## Schema, nâng cấp và vận hành

Chỉ thêm V41__test_publication_and_full_mock.sql. V1–V40 và SQL historical V34/V35 giữ nguyên. V41 mở rộng generic moderation kind tests, current published pointer, unique một working version, blueprint rules và JSON snapshot trên test_versions/attempts; thêm hai permission Admin. Không backfill evidence/pointer/blueprint cho legacy dữ liệu không rõ nguồn.

Checksum V41 đã áp dụng trên lab: `-517931869`; không chỉnh lại sau khi áp dụng. Source/JAR có 44 SQL (40 common + 4 historical); coordinator chọn đúng 42 version gồm 27.1, chỉ một V34 và một V35. Hai cặp historical ở location riêng là có chủ đích, không phải duplicate trong selected release.

Coordinator vẫn tự nhận diện history V34/V35 như contract đã bàn giao. Chỉ mở release prefix tới V41 và thêm fingerprint V41, giữ fingerprint cũ cho pre-V41. Canonical mới dựng bằng SQL gốc; whitelist restore chỉ sáu CHECK alternatives và một partial index alternative tái hiện qua pg_dump/restore, không bỏ constraint/cast tùy ý. Partial index phải giữ đúng tên, table, predicate, unique và valid/ready; thiếu APPROVED/IN_REVIEW vẫn bị chặn. V40 có nhiều working versions bị preflight chặn trước migrate; phải giải quyết nghiệp vụ tường minh, không repair/clean/baseline. Boot/test/CLI/CI đi cùng coordinator. Không dùng Flyway CLI trực tiếp hoặc chọn historical location bằng tay. Lab dùng Hikari pool 4; thử pool 2 không đủ connection cho coordinator/Flyway nên thất bại trước validate, không là lỗi history hoặc bằng chứng pass.

Chỉ dùng cluster lab 55463 sau kiểm data_directory. Không kết nối toeic_db cá nhân. Fixture V34/V35 là SQL gốc, không phải clone DB thành viên. Không suy tương thích tất cả DB thành viên từ kết quả này. Trước triển khai thực tế cần backup/clone DB được phép, đọc history và dữ liệu legacy, diễn tập, xem permission và preset blueprint đã xác nhận. Chưa triển khai vào DB đang dùng.

## Demo

1. Trên DB demo mới, chạy JAR với datasource riêng; coordinator migrate/validate, không ghi toeic_db.
2. ADMIN cấu hình blueprint được chốt; nếu demo dùng mini fixture thì ghi nhãn fixture rõ.
3. Teacher tạo/sửa DRAFT, chọn nguyên nhóm current published, gắn blueprint, Gửi duyệt.
4. ADMIN không là contributor Duyệt → Xuất bản, bảo đảm có media owner/grants hợp lệ.
5. Learner `/tests` → Full Mock trên máy chủ → overview → Bắt đầu; lưu/thay/bỏ đáp án, reload URL, nộp và reload result.
6. Teacher fork current published → sửa tường minh → gửi duyệt; Admin publish phiên mới. Kiểm attempt cũ vẫn giữ bản pin.
7. Admin Ngừng cung cấp; start mới bị chặn, attempt đã pin vẫn đọc/nộp theo quyền và deadline.

## Bằng chứng

| Bằng chứng | Nguồn/phạm vi chính xác | Kết quả |
|---|---|---|
| Full backend một lượt | Working tree trước delta duy nhất ở MigrationSchema về partial-index restore V41; 74 classes, có 10 test CSV nền untracked | 1.223 test, 0 failure/error/skipped, 14:13; không gọi đây là full của source cuối |
| Các nhóm trong full | FullMock 15, DRAFT 28, AuthFlow 31, RBAC 29, moderation 21; compatibility 47 | Đạt; migration có thêm giữ DRAFT V40 và chặn nhiều working versions trước mutation |
| Delta migration cuối | Nguồn cuối: v40DraftDataUpgradeAndLegacyDuplicatePreflight và v41RestoreIndexWhitelistIsExact | 4/4 đạt; predicate sai bị chặn, snapshot DB không đổi |
| Compile/package cuối | Maven Wrapper `package` với 4 ca delta, test-compile toàn nguồn; resource output đã được dời lưu trước rebuild | Đạt; JAR 52 resource DB khớp byte source, 44 SQL, không có SQL cũ dư ngoài source |
| CLI restore cuối | MigrationCli, guard cuối biên dịch riêng và resource hiện hành; schema canonical SQL qua pg_dump/restore, mode validate | Đạt với pool 4; không sửa history/schema |
| Frontend cuối | `npm test`, `npm run lint`, `npm run build` | 95 test đạt; lint exit 0 còn warnings đã có, build đạt còn chunk warning |
| HTTP/browser cuối | JAR cố định sau package + production frontend build, DB toeic_fullmock_final_http_r2_20261121 riêng, S3 HTTP mock | 29 kiểm HTTP; 2 browser real backend + 3 browser HTTP fixture DRAFT; restart mode validate đạt; history và result cũ giữ nguyên |
| Bảo toàn | 42 file nền/settings, 526 file candidate/evidence; HEAD/index | Hash không đổi; không stage/commit/push; chưa có CI cho working tree mới |

JAR cố định cuối SHA256 `7d698e2e12eeda67c038e84ab93f488810d8fb77b37af7e943b9654611ea1bd3`. Source hash/delta tại `backend/target/fullmock-final-build-proof-20261121.json`; full cũ của lượt này tại `fullmock-full-proof-20261121.json` và `fullmock-full-reports-20261121`; delta ở `fullmock-final-migration-package-20261121.log`; HTTP/browser ở `fullmock-final-http-r2-proof-20261121.json`, `fullmock-final-live-r2-browser-20261121.log`. Không stage các artifact này.

Lệnh backend: `./mvnw.cmd test` với argLine giới hạn heap/metaspace, context cache 2, Hikari 4/idle 0 và datasource lab tường minh; cuối dùng `./mvnw.cmd '-Dtest=MigrationCompatibilityIntegrationTest#v40DraftDataUpgradeAndLegacyDuplicatePreflight+v41RestoreIndexWhitelistIsExact' package`. Không chạy Maven clean trên target chứa data lab; đã lưu resource output cũ sang artifact directory riêng rồi rebuild. Workflow CI hiện hành dùng `bash ./mvnw -B clean verify` trên DB CI riêng, pool 6, không có lịch sử/location override; code Boot/test/CLI/CI gọi cùng coordinator. Chưa push nên chưa có CI mới.

Đường migration đã kiểm: empty/V33, Settings34 (+ADMIN35/37/39), RAG34 (+ADMIN35 hoặc RAG35/36/37/38/39), V40 có dữ liệu DRAFT, V41 canonical/restore; checksum/history/config/schema sai và index sai bị chặn. Đây là fixture từ migration gốc và bản restore lab, chưa phải clone DB thành viên lâu dài.

Một lượt harness cuối đã khởi động khi JAR còn repackage và lỗi class loading; không tính pass. Lượt r2 dùng bản copy artifact cố định, DB mới, đã đạt toàn luồng. Các failure fixture media ban đầu và CLI pool 2/schema cast ban đầu được giữ trong log để phân biệt với kết quả cuối; không nới media/validation để làm xanh.

Cuối lượt đã dừng JAR/preview/mock server và cluster lab do lượt này khởi động, trả cluster về trạng thái stopped ban đầu. DB/dump/bằng chứng vẫn giữ; demo không đang chạy. Git cuối: 47 tracked modified và 57 untracked file, gồm 6 tracked + 35 untracked nền nguyên hash; settings ignored cũng nguyên hash. Phần nhiệm vụ 63 đường dẫn; index giữ nguyên và không có merge dang dở.

Không gán CI #187 cho working tree mới; full local lịch sử có 5 failure giữ nguyên nhãn. Xem [manifest file/hunk](TV2_Publication_FullMock_Manifest_20261121.md) để tách 63 file nhiệm vụ khỏi file nền. `git diff --check` cho phần nhiệm vụ đạt; AGENTS.md có trailing whitespace ở dòng 1 từ trước, giữ nguyên.

## Phần còn lại

EXAM-02 còn chờ preset chính thức và dữ liệu/media đủ chuẩn cho đề thật. EXAM-03 worker và quy đổi điểm TOEIC ngoài gói. EXAM-05 tiếp tục từ raw/part results DB đã có; không dùng ước tính tự chế. G1/media production cần kiểm chứng độc lập; ADMIN/TV1 không mở rộng ngoài nối publication cho bộ đề.
