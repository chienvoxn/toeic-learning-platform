# Web tích hợp Sprint 1–3 — demo 25/11/2026

Checkpoint chuẩn bị ngày **24/11/2026**, nhánh `feat/web-auth-integration`.
Chưa làm Sprint 4. Phân biệt nguồn trên PR với phần nối demo chưa commit.

## GitHub đã bàn giao

- Remote feature: `cf2b6d372fe6891b06f7a6c9791d0bd2bcce93e6`, push thường,
  tài khoản xác thực GitHub **pthanh137** được kiểm bằng API `/user` từ GCM.
  Không chỉ dựa vào `user.name`. Repo identity vẫn `pthanh137 <sleapf137@gmail.com>`.
- Hai merge `9042dda` và `cf2b6d3` giữ nguyên parents và tác giả. HEAD chứa
  develop `20c63e776b656efd5ba0daaaae3447284e863cac`. Không nhập thêm develop.
- [CI feature #226](https://github.com/chienvoxn/toeic-learning-platform/actions/runs/45380576208)
  và [CI PR #227](https://github.com/chienvoxn/toeic-learning-platform/actions/runs/45383123933)
  đều success cho head trên; BE, FE và AI/pgvector đều xanh.
- [Draft PR #73 → develop](https://github.com/chienvoxn/toeic-learning-platform/pull/73),
  tác giả pthanh137, `mergeable=true`, `mergeable_state=clean` khi kiểm.
  PR chưa merge. Draft giữ giới hạn blueprint, G1 UI và Listening/media.
  PR có toàn bộ phạm vi feature đã commit, không chỉ hai merge cuối.
- Những thay đổi trong manifest demo bên cạnh tài liệu này **chưa stage/commit/push**,
  không thuộc nguồn CI hay PR. BE chạy JAR đã kiểm của merge A, cây BE giống cf2b6d3.
- Các ref chưa vào develop được rà mốc commit nhưng không nhập: stabilization
  `5c143a1`, assessment coverage `c23ee0b` (test Full Test/vocabulary), bulk import
  `66e71a2`, microservices `9daff95`. Không nhận chức năng các nhánh này là đã tích hợp.

## Môi trường thực tế

| Thành phần | Địa chỉ / cấu hình | Trạng thái đã kiểm |
|---|---|---|
| Web Vite | http://127.0.0.1:5173 | HTTP 200; Chrome thật, API thật, không route stub |
| Backend | http://127.0.0.1:8080 | `/api/health` UP |
| Database health | http://127.0.0.1:8080/api/health/database | UP, đúng `toeic_tv2_merge_http_20261124` |
| PostgreSQL 17.11 | `127.0.0.1:55469/toeic_tv2_merge_http_20261124` | 44 history rows tới V43, giữ nguyên toàn bộ rows/checksum |
| Profile | `local-demo` **kèm override datasource trong script** | `app.migrations.mode=validate`, JPA validate; không migrate |
| Storage thật | SeaweedFS S3 4.55, loopback `9000`, private bucket | Upload/read/SigV4/expiry được kiểm; chưa là Learner Listening E2E |
| Mail demo | Mailpit v1.34.0: http://127.0.0.1:8025, SMTP `1025` | SMTP thật bắt thư local và reset OTP; không gửi Internet |
| AI RAG ngoài | Chưa chạy/cấu hình | Index trả 503; không mock thay thế |

DB review trên là clone lab phục vụ tích hợp/demo, **không phải bằng chứng clone
DB thành viên lâu dài**. Cluster cũ được tái sử dụng, system identifier
`7711587379292605296`; data directory được kiểm trong `check_demo_db.py`.
Không tạo DB/cluster mới trong lượt này; chỉ còn postgres, DB JUnit cũ và DB HTTP
trong cluster review. Không truy cập PostgreSQL cá nhân/toeic_db, không khởi động
DB nguồn `55459/toeic_learning_demo` để sửa dữ liệu.

Không chạy backend chỉ với profile `local-demo`: cấu hình profile trong source
còn trỏ demo cũ 55459. Dùng script dưới đây để có override 55469 và validate-only.

## Tài khoản và dữ liệu

Mật khẩu nằm tại **`backend/target/sprint13-demo-20261124/demo-credentials.json`**,
file ignored, ACL riêng user hiện tại/SYSTEM/Administrators. Không chép vào PR,
chat, ảnh màn hình, Postman hoặc log. File có các key `roles.learner`, `teacher`,
`admin`, `qa`, `registration`; mỗi entry có email/password. Credentials demo cũ
`backend/target/local-demo-credentials.json` được giữ nguyên.

| Role | Email / mục đích |
|---|---|
| Learner | Đọc `roles.learner.email`, tài khoản demo gốc được giữ nguyên |
| Teacher | `sprint13.teacher.20261125@example.invalid` |
| Admin | `sprint13.admin.20261125@example.invalid` |
| QA Learner | `sprint13.qa.20261125@example.invalid`, dùng vocabulary/lock–unlock |
| Registration | `sprint13.register.20261125@example.invalid`, riêng ca đổi/reset mật khẩu |

Teacher/Admin/QA được register qua API và cấp role bằng API RBAC của bootstrap
admin review hiện có, không SQL cập nhật role. Tài khoản registration được tạo
trên browser; chỉ tài khoản đó đổi/reset mật khẩu. Cuối kiểm QA đã ACTIVE lại.

Dữ liệu bổ sung tối thiểu, có checkpoint chống tạo lặp: một học liệu tự soạn
**Sprint 1-3: Meeting schedules**, Teacher gửi duyệt → Admin khác contributor
duyệt/publish AUTHENTICATED qua UI/API; một nhóm PASSAGE DRAFT; một audio tone
tự tạo một giây và câu Part 2 đi qua submit/approve. Câu audio **không publish được**,
giữ APPROVED; không gán pointer/grant bằng SQL. Deck Workplace dùng catalog đã có;
review, bookmark, answer và điểm phát sinh từ hành động thật, không seed tiến độ.

## Bảng chạy thật / giới hạn

| Phạm vi | Đã chạy thật trên browser–API–DB | Thiếu / chưa tích hợp / không nhận Pass |
|---|---|---|
| Auth | Register, login, sửa tên/mục tiêu rồi reload, đổi password 204, reset OTP Mailpit 204 và login lại | Google OAuth chưa kiểm với nhà cung cấp; không nhận mail Internet đã gửi |
| Hồ sơ | Tên và điểm **mục tiêu** lưu thật | Avatar/bio/privacy chưa có API; chỉ số nghe chép/nhại theo trên layout chưa kết nối, không dùng làm bằng chứng tiến độ |
| Học liệu | Search, đọc bài published, bookmark/reload và history của learner | Player media trong trang chi tiết chưa hỗ trợ; không coi truy cập là hoàn thành bài |
| Vocabulary/SRS | Catalog thật, enroll Workplace, lật thẻ, rating GOOD lưu server, reload và totalReviews tăng 0→1 thật | Không tự cộng điểm/progress; chức năng ngoài luồng này không nhận E2E |
| Teacher | UI mới → editor đã có; tạo/read DRAFT học liệu và nhóm; gửi duyệt đúng revision | Class/reports chưa có, nav đánh dấu Chưa có; không viết thêm Sprint 4 |
| Teacher test DRAFT | Nguồn authoring/revision không đổi; tái sử dụng live proof của cf2b6d3 | Không nhận published Full Mock demo hoàn tất |
| Admin | Học liệu approve/publish khác contributor; user lock → login401 → unlock; RBAC read; audit; DB health; Settings đổi/reload/phục hồi giá trị gốc | Không sửa role thực qua UI trong ca mới; không gọi audit là mọi hoạt động của hệ thống |
| G1 | API nguyên tử/rollback/pointer/contributor đã kiểm trên cùng BE/JAR; pin/result/replay cũ được đọc lại thành công | **Chưa có UI batch**; không bypass media để xuất bản |
| Practice P5 | Tạo4 câu, lưu/reload/nộp, raw result/Part/history, replay cùng result và đối chiếu DB | Từ Dashboard chọn P5 có preset30: đổi về4 với bank demo; không giả 30 câu có sẵn |
| Reading Section | 8 câu/20 phút cấu hình sản phẩm, Part5–7, lưu/reload/nộp/result/history thật | Không nhận cấu hình demo này là cấu trúc TOEIC chính thức |
| Full Mock/version/worker | Runtime chung `/attempts`, pin, publication/fork/retire và worker giữ nguyên; CI head đã chạy | Catalog demo không có published Full Mock hợp lệ. FULL-LR/v1 và chuyển Section A/B vẫn OPEN, không seed |
| AI/exam guard | Lời chào xử lý tại BE và history thật; Practice cho phép/Section khóa server, offline và restart đã có live proof trên cùng nguồn | Không gọi Gemini thật; RAG index503 do dependency ngoài chưa có. Không fallback mock khi API lỗi |
| Listening/storage | Owner upload tự soạn, signed GET khớp bytes; anonymous/tamper/expired403, URL mới đọc lại được | Publish409 `PUBLICATION_MEDIA_UNAVAILABLE`: Admin publisher khác author chưa có media reuse grant. **Chưa playback Learner UI** |
| Dictation/Shadowing/games | Remote route hiển thị chưa hỗ trợ | Không bật mock để trình diễn là chức năng thật |
| TOEIC conversion/NFR02 | Chỉ raw score/accuracy/đúng-sai-bỏ trống/Part đọc từ snapshot | Chưa bảng quy đổi được duyệt; chưa workload/p95, không nhận đạt |

G1 không chặn Practice/Reading từ các câu published sẵn hoặc bài văn bản mới
không media. Media mới bị chặn ở **bước publish bởi actor khác author**: M1
kiểm owner/grant trong transaction; chưa tìm thấy REST workflow cấp reuse grant
để publisher độc lập sử dụng asset. Tone ở storage không giải quyết dependency
này. Cần hoàn thiện contract/workflow M1 grant trước khi nhận Learner Listening
E2E, không sửa quyền bằng SQL hoặc nới guard.

## Bằng chứng và nguồn

Thư mục ignored **`backend/target/sprint13-demo-20261124/`**:

- `push-proof.json`, `ci-checkpoint.json`, `pr-checkpoint.json`: remote/auth/CI/PR.
- `frontend-unit.log`: **107/107**; `frontend-lint.log`, `frontend-build.log`:
  exit0, còn warning có sẵn/chunk-size. Source runtime sáu file FE không đổi
  sau lượt này; chỉ điều chỉnh harness browser sau đó.
- `browser-attempt1.log`: Teacher và AI đạt; bốn ca harness chưa đạt được giữ log.
  `browser-attempt2.log`: Admin và Learner đạt. `browser-attempt3.log`: Auth và
  Vocabulary đạt. **Sáu ca hoàn tất qua ba lượt, không phải một lượt 6/6**.
  Lỗi harness đầu: nhầm reset204/login401, selector mock catalog, chờ PATCH khi
  hồ sơ không đổi và race chờ nút catalog sau tải. Không sửa BE/hạ permission
  để chữa test. Register201/approve/publish được kiểm ngay lượt tạo đầu,
  checkpoint được tái sử dụng khi chạy lại, không tạo trùng học liệu.
- `browser-checkpoint.json`: IDs và kết quả từng luồng, không token/password.
- `media-checkpoint.json`: upload/read/TTL thực60 giây, expiry403 và blocker409;
  audio chỉ là tone tự tạo, không phải speech hoặc Learner E2E.
- `final-proof.json`: history44 giữ nguyên, scores/answers khớp DB,
  old G1 pin/result/replay và old Practice/Reading đọc lại; 43 SHA nền giữ nguyên.
- `inventory-before.json`, backup trước/sau; không chạy migration hay full local.

Giữ riêng bằng chứng trước tại `backend/target/tv2-develop-integration-20261124/`:
index A package/82 BE test, B có BE giống A; browser Teacher revision,
Practice thay/bỏ đáp án và replay, Reading, G1 atomic API, AI server guard,
offline/đóng tab/restart/pin. Các file runtime đó không đổi trong lượt demo.
Nhãn cũ **1.247 test thực thi đạt, 1 skipped** vẫn là broad working-tree cũ,
không phải full của demo mới. CI226/227 là bằng chứng độc lập của committed SHA.

## Khởi động, kiểm health, dừng

Tại `D:\toeic-learning-platform`, PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/sprint13-demo-20261124/start-demo-services.ps1
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/health/database
```

Script tái sử dụng đúng cluster/system identifier, không initdb/restore/migrate;
giữ dịch vụ đã chạy, mở Mailpit/S3/BE/FE còn thiếu, từ chối port BE/FE không đúng
process. Chỉ dùng `-RestartBackend` khi chủ ý restart đúng backend demo; không
chạy song song test/browser lúc đó. Đã kiểm gọi mặc định hai lần không đổi PID BE.
JAR cần giữ: `backend/target/tv2-develop-integration-20261124/index-A-build/backend-0.0.1-SNAPSHOT.jar`.
Không Maven clean target trước demo (có runtime, credentials, proof và JAR ở đó).

```powershell
# Sau demo; lượt bàn giao này KHÔNG chạy stop:
powershell -NoProfile -ExecutionPolicy Bypass -File backend/target/sprint13-demo-20261124/stop-demo-services.ps1
# Thêm -StopDatabase chỉ khi muốn pg_ctl dừng đúng cluster riêng; không xóa data.
```

Script stop kiểm PID/command của từng dịch vụ demo, không kill mọi postgres/node.
Stop path được review; chưa diễn tập stop cả stack trong lượt đang cần giữ demo.

## Backup và bảo toàn

Backup cuối nhỏ **330.690 bytes**, PostgreSQL17.11 custom:
`backups.local/sprint13-demo-20261125/toeic_tv2_merge_http_20261124.custom`,
SHA256 `b63d7789300bad0ecf808ff6eb00e3544dd3ef69ab1e96431b61fc35e967038d`.
`roles-no-passwords.sql` và `backup-proof.json` cùng folder có ACL riêng.
TOC đọc đạt; **chưa restore thử backup mới trong lượt này**, không gán bằng chứng
restore cũ cho nó. Media là object ngoài DB, giữ nguyên thư mục
`backend/target/demo-runtime-20261123/storage/data` và credentials tương ứng.
Không cần restore môi trường hiện đang UP. Nếu phải phục hồi, kiểm endpoint riêng,
đọc roles SQL trước, tạo DB đích mới riêng rồi `pg_restore --exit-on-error
--dbname=NEW_DEMO_DB <custom>`; không restore đè nguồn/toeic_db và không migrate
chỉ để kiểm restore. Không tự dựng lại toàn bộ cluster lab.

6 tracked nền, 35 untracked nền và settings/credentials trong baseline43 hashes
giữ nguyên nội dung. Index rỗng, không MERGE_HEAD/rebase; không stage/commit mới.
**Giới hạn artifact:** lần khởi động S3 đầu đã tái tạo `storage.out.log`,
`storage.err.log`, `process.json` cũ; không có bản lưu nguyên trạng trước đó.
Không nhận các operating log đó được bảo toàn. Source, credential, storage data,
backup, vendor/seed proof và bằng chứng cuối được giữ; script mới ghi log riêng
vào thư mục Sprint13.

## Kịch bản 10–15 phút

1. **0–2 phút:** mở5173, login Learner; Dashboard/profile và học liệu. Search
   “Meeting schedules”, đọc, bookmark, reload; phân biệt lịch sử với hoàn thành.
2. **2–5 phút:** `/practice`, Practice P5 **4 câu**, chọn/thay/bỏ/lưu đáp án,
   reload rồi nộp. Mở raw result, lời giải/Part, lịch sử. Nộp replay giữ kết quả.
3. **5–7 phút:** Section Reading **8 câu/20 phút**, một đáp án rồi nộp; xem Part5–7.
   Không mở AI giữa Section để tránh nhầm guard với lỗi dịch vụ.
4. **7–9 phút:** logout/login QA; Vocabulary → Workplace deck đã enroll → review,
   lật thẻ/rating, reload tiến độ server. Không nhấn reset lượt học chỉ để demo.
5. **9–12 phút:** cửa sổ Teacher riêng → học liệu/nhóm/bộ đề DRAFT. Tạo draft
   văn bản nhỏ mới nếu cần trình diễn gửi duyệt. Cửa sổ Admin khác contributor
   → moderation duyệt/publish; Learner reload thấy bài. Không self-publish.
6. **12–14 phút:** Admin users/RBAC/audit/health/Settings. Nếu đổi Setting thì
   ghi giá trị gốc và khôi phục; dùng QA riêng cho lock/unlock, luôn mở khóa lại.
7. **14–15 phút:** AI “Xin chào”/history thật; trình bày giới hạn RAG/media/G1 UI
   và Full Mock OPEN bằng bảng trên, không chạy fixture như demo Learner.

Khi mất Internet/AI: lõi local FE/BE/DB/Mailpit/S3 tiếp tục hoạt động. Thông báo
RAG chưa cấu hình/503, chuyển sang Practice/Reading/Vocabulary; không bật mock.
Nếu mất kết nối BE: không nhận “đã lưu” khi request thất bại, kiểm health và retry
trên attempt hiện có; không tạo attempt hoặc restore cluster khác để che lỗi.
Nếu 5173 không vào được, chạy script start idempotent rồi kiểm lại hai health.

Checkpoint tiếp tục: demo đủ các luồng độc lập ở trên, mọi service cần thiết đang
chạy. Chưa stage/commit thay đổi demo; review manifest trước một lượt commit riêng.
Full Mock blueprint/A–B, M1 media grant, UI G1 batch và RAG cấu hình ngoài vẫn là
dependency, không mở rộng Sprint4.
