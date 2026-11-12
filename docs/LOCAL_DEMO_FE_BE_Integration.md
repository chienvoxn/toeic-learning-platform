# Tích hợp FE–BE và demo local — 2026-11-11

## Bản nguồn và phạm vi

Làm tại `D:\toeic-learning-platform`, nhánh `feat/web-auth-integration`. Đã fetch và fast-forward từ `985b52b` tới **`a4a27b1ac499478f6318e578bed846fe6276cc5a`**, bằng `origin/develop` tại lần fetch cuối. Hai kiểm tra `git merge-base --is-ancestor origin/develop HEAD` và `git merge-base --is-ancestor 985b52b HEAD` đều exit 0. Không có conflict hoặc file local bị ghi đè; không tạo nhánh/worktree, reset/stash, stage/commit/push hay merge PR.

Bản sửa migration đã ở develop: giữ `V27__section_attempt_configuration.sql`, catalog là `V27_1__system_vocabulary_catalog.sql`, sau đó V28–V30. Không thêm/sửa migration trong nhiệm vụ này. Không sửa Java backend; tái sử dụng PRAC-01/02, EXAM-01, auth, content và vocabulary hiện hành.

Đối chiếu SRS/HLD Phần B/LLD/ERD/API với các tài liệu nhiệm vụ. Không tìm thấy backlog/AC Sprint 2 chi tiết độc lập trong repository; bảng dưới đánh giá phần đã chứng minh, không gán phần trăm hoặc tự đánh dấu Done toàn Sprint. Phần rà soát UI cũ trong EXAM01 là lịch sử trước lần tích hợp này.

## Hiện trạng dữ liệu và môi trường

| Nguồn | Có schema/dữ liệu | Dữ liệu đủ điều kiện API | Thao tác thực tế |
|---|---|---|---|
| Cá nhân `127.0.0.1:5432/toeic_db` | Người dùng xác nhận trước đó tới V21 | Chưa đếm được vì kết nối CLI thiếu password | Chỉ thử kết nối read-only; không migrate, seed, sửa history, repair hoặc reset |
| Demo mới `127.0.0.1:55459/toeic_learning_demo`, schema `public`, role `toeic_demo` | Tạo DB riêng trống, Flyway bình thường 31 migration tới V30 | V27 và V27.1 đều success; 0 phiên bản trùng/0 migration lỗi | JPA validate và Spring khởi động thành công |
| Sau migration, trước seed | 6 system decks / 180 từ từ V29 | Catalog có dữ liệu; chưa có câu Practice/Reading và học liệu demo | Không nhầm schema/catalog sẵn có với đủ câu published để tạo attempt |
| Sau seed chạy hai lần | 3 account seed, 11 câu, 2 nhóm, 1 học liệu published; 0 attempts/answers/reviews được seed | Part 5: 4; Part 6: 4 nguyên nhóm; Part 7: 3 nguyên nhóm; mỗi câu 4 choice/1 key/explanation/version và thứ tự hợp lệ | Practice Part 5 lấy 1–4 câu; Reading Section có tổ hợp 8–11 câu, ít hơn/không khớp trả 409 |

Demo dùng database riêng trên cluster local hiện có `backend/target/q03-main-postgres`, không dùng tên DB kiểm thử đã có trong cluster và không kết nối datasource cá nhân. Cluster chỉ listen loopback; trust authentication local hiện có, app dùng role thường `toeic_demo`. Đây không phải cấu hình production. Profile **`local-demo`** chỉ được bật rõ ràng; datasource mặc định không đổi, không thêm secret vào Git.

Audit read-only sau các lượt browser: **9 users** (3 seed + 6 tài khoản tổng hợp tạo qua API kiểm thử); 11 câu published + 3 archived từ Teacher CRUD; 1 content published + 1 draft còn từ ca thử trước + 2 archived. Không xóa các dữ liệu này để làm đẹp số liệu. Có 12 attempts thực: Practice 3 GRADED/3 IN_PROGRESS, Section 5 GRADED/1 IN_PROGRESS; 56 answer rows/52 graded; 0 lệch giữa header đúng/tổng câu với answers và snapshots; 0 attempt có official estimated score/conversion. Có 1 bookmark, 1 content history, 1 catalog enrollment, 2 SRS reviews và 1 learning session phát sinh từ thao tác thật. Những con số này sẽ tăng khi chạy demo/test lại.

## Dịch vụ đang chạy và đăng nhập

| Dịch vụ | URL/cổng | Trạng thái kiểm tra cuối |
|---|---|---|
| Web Vite | <http://127.0.0.1:5173> | HTTP 200; Chromium hiển thị UI |
| Backend | <http://127.0.0.1:8080/api/health> | HTTP 200, UP |
| DB health | <http://127.0.0.1:8080/api/health/database> | HTTP 200, UP |
| UI kiểm tra kết nối | <http://127.0.0.1:5173/health> | Dùng backend thật, auth remote |
| PostgreSQL demo | `127.0.0.1:55459/toeic_learning_demo`, `public`, `toeic_demo` | Socket mở; identity được kiểm trước seed |
| Mailpit | <http://127.0.0.1:8025>, SMTP `127.0.0.1:1025` | HTTP 200/socket mở; đã nhận mail reset thật |

Giữ backend/web/PostgreSQL demo/Mailpit chạy để mở thử. PostgreSQL cá nhân 5432 vẫn chạy, không bị dừng hoặc nâng cấp. Launcher ghi PID riêng tại `backend/target/local-demo-processes.json`; log tại `backend/target/local-demo-{backend,web.out,web.err,mail.out,mail.err}.log`.

Tài khoản seed: **`learner@demo.example`**, **`teacher@demo.example`**, **`admin@demo.example`**. Mật khẩu ngẫu nhiên dùng chung cho ba account nằm trong file local **`backend/target/local-demo-credentials.json`**; mở file này trong editor, không dán password/token vào Git hoặc log. File đã được xác nhận ignored. Seed chạy lại không đổi password account đã tồn tại và không xóa bài làm.

Đăng ký hiện hành tạo ACTIVE, không OTP. OTP dùng cho **Quên mật khẩu**: nhập địa chỉ, mở Mailpit tìm thư đúng người nhận, nhập mã và mật khẩu mới. Browser test đã đăng ký account tổng hợp riêng, nhận OTP qua Mailpit, reset trên UI và đăng nhập lại; không sửa timestamp/history SQL. Google chưa cấu hình Google Client ID ở demo, chưa kiểm Google Cloud thật.

Chạy lại dịch vụ sau khi đã dừng, từ repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-local-demo.ps1
# Chờ /api/health và /api/health/database UP, sau đó bootstrap dữ liệu nếu cần:
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/seed-local-demo.ps1
```

Launcher dùng Java 21/Maven Wrapper, PostgreSQL 17 trong `C:\Program Files\PostgreSQL\17\bin`, dependencies Maven/npm đã cài, Mailpit ở `backend/target/web-auth-tools/mailpit.exe`, cluster local nói trên. Nó dùng Maven offline, không tự cài/download. Kiểm cổng trước, từ chối chạy trùng; kiểm đúng cluster/port trước tạo DB nếu chưa có; không drop/reset DB. Thiếu công cụ/dependency phải cài riêng hoặc chỉnh đường dẫn local, không chuyển sang `toeic_db`. Không chạy Maven clean khi cluster đang nằm dưới target và đang hoạt động.

Profile local-demo trỏ cố định demo database. Vite child process đặt `VITE_AUTH_MODE=remote`, `VITE_USE_MOCK=false`; base `/api/v1`, proxy `/api` tới localhost:8080 nên browser đi cùng origin. Không cần nới CORS. Session/token dùng cơ chế remote có sẵn; không có fallback mock khi API lỗi.

Seed là lệnh opt-in, transaction + advisory lock + kiểm đúng database/user/schema, UUID xác định/ON CONFLICT, không tự bật production. Mật khẩu được hash BCrypt trong bộ nhớ; SQL không chứa mật khẩu rõ. Dữ liệu published là fixture local hợp lệ, có pointer/version/group/order/choices và không tắt constraint/permission. **Chưa có publish API**, seed không chứng minh đã hoàn thành workflow publish. Seed không tạo attempt, điểm, progress hoặc lịch sử ôn giả.

Dừng riêng các process demo đã ghi nhận (script kiểm command line/PID tree, không dừng service ngoài nhiệm vụ):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/stop-local-demo.ps1 -WhatIf
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/stop-local-demo.ps1
# Tuỳ chọn dừng cả cluster mà demo đã khởi động, chỉ khi không còn client khác:
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/stop-local-demo.ps1 -StopPostgres
```

Đã kiểm parse và chạy `-WhatIf`; chưa thực sự dừng dịch vụ vì cần giữ demo chạy. Không có lệnh xóa database/dữ liệu. PostgreSQL mặc định được để chạy nếu không có switch.

## Luồng attempt thật đã nối

Vào **Luyện thi TOEIC** hoặc `/practice`. Tận dụng AssessmentLayout/ExamTemplate/QuestionPanel/ResultPage của nhóm; giữ các route Guest mẫu cũ riêng và ghi rõ chỉ lưu trình duyệt.

| Hành vi | API runtime và payload |
|---|---|
| Tạo Practice | POST `/api/v1/attempts`, `{"sourceType":"PRACTICE_SNAPSHOT","practice":{"partNumbers":[5],"questionCount":4}}` |
| Tạo Reading | POST `/api/v1/attempts`, `{"sourceType":"SECTION_SNAPSHOT","section":{"type":"READING","questionCount":8,"durationMinutes":20}}` |
| Đọc lại | GET `/api/v1/attempts/{id}`; dùng version/group/order và startedAt/expiresAt server |
| Chọn/thay/bỏ | PUT `/api/v1/attempts/{id}/answers/{questionVersionId}`, `{"selectedChoiceId":"<choice UUID trong snapshot>"}` hoặc `{"selectedChoiceId":null}` |
| Nộp | POST `/api/v1/attempts/{id}/submit`, **không body/không gửi đáp án** |
| Kết quả | GET `/api/v1/attempts/{id}/result`; join câu pinned, dùng điểm/key/explanation server; Section dùng thống kê Part đã lưu |

Remote mapper bỏ key/explanation/transcript/media paths khỏi prompt, không suy hạn từ duration hoặc tự tính grading. Serial queue lần lượt PUT, nhận ack đúng lựa chọn, retry thật, chờ flush trước submit. Lỗi mạng hiển thị chưa lưu, không tạo kết quả giả; người dùng retry hoặc tải lại đáp án đã lưu trên server. Pending choices còn trong bộ nhớ không được coi là đã lưu khi đóng/crash tab; beforeunload/Thoát có cảnh báo. Cờ xem lại chỉ ở màn hình, không hứa lưu server.

Practice hết 24h không lưu/nộp mới. Section chặn save từ expiresAt nhưng vẫn cho nộp đáp án server đã lưu; UI không tự chốt bài. Đã sửa bộ đếm từ floor sang ceil để không hiển thị hết hạn sớm dưới một giây. Backend vẫn quyết định nhận/từ chối theo giờ server. Submit transaction/row lock/idempotent dùng backend hiện có; UI không quy đổi TOEIC. Sau nộp khóa sửa, reload đọc kết quả đã lưu. Trước nộp GET result trả 409, response snapshot không có key/explanation. Chưa có worker tự nộp EXAM-03.

## Bảng mức độ đã kiểm chứng

| Chức năng | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| Guest landing/bài mẫu | Demo/localStorage | `/tests` → overview → làm mẫu có nhãn không đồng bộ; không POST attempt server. Không coi chấm local là kết quả tài khoản |
| Email login/logout, session/guard, reset OTP | API thật + UI chạy được | Browser email login/guard/logout, invalid-session/offline trong lượt auth; reset qua Mailpit ở suite demo cuối. Google chưa kiểm Cloud thật |
| Profile/đổi mật khẩu | API + UI có sẵn | Code remote; không sửa profile/password account seed trong smoke này, chưa xác nhận lại mọi trường UI |
| Dashboard từ vựng | API thật + UI chạy được | Browser render số liệu overview/progress sau đánh giá SRS; không seed XP/streak giả |
| Practice PRAC-01/02 | API thật + UI chạy được | Chọn/thay/bỏ, reload, submit/replay, giải thích, ownership401/404 và save sau submit409; queue/network test |
| Reading EXAM-01 | API thật + UI chạy được | Đủ Part5/6/7, nguyên hai nhóm, 8 câu/20phút; infeasible409, server deadline, per-Part, Section1phút hết hạn vẫn submit được |
| Listening Section | Chỉ backend; thiếu dữ liệu/media demo | Backend contract có LISTENING, chưa chạy UI/audio Part1–4 trong demo này |
| Học liệu CONTENT-02 | API thật + UI chạy được một luồng | Catalog → đọc bài → bookmark + history DB. Search/filter/paging có code/API, chưa thử mọi biến thể UI trong lượt này |
| CONTENT-03 và Q-03 Teacher | Chỉ backend/CRUD HTTP đã kiểm | Teacher question/group/content create→patch→history/archive, RBAC; chưa có màn Teacher authoring, publish workflow/upload/grant management |
| Admin | Nền role/permission backend | Admin đọc Teacher list200, Learner403, Teacher không có practice403; `/admin/users`404, chưa có quản trị user/UI Admin |
| Vocabulary | API thật + UI chạy được | Catalog6/180 → enroll → detail → flashcard → rating SRS → learning session/overview; cache vòng học local vẫn tồn tại, restore lỗi server không âm thầm fallback local |
| Protected media/transcript | Chưa tích hợp playback | Tái sử dụng policy metadata; không tự tạo/public URL/path hoặc đưa transcript vào prompt/result; browser speechSynthesis từ vựng không phải protected audio playback |
| Dictation/Shadowing/game | Chưa tích hợp ở mode thật | Route thật hiển thị chưa sẵn sàng; các mock/demo cũ không được tính đã hoàn thành backend |
| AI/recommendation | Code/service riêng, chưa kiểm runtime | Không khởi động AI Python, không gọi provider/key thật hoặc đánh giá chất lượng AI; không tính đã chạy qua FE |
| Mobile | Mock/chưa kiểm thiết bị | Có Expo source; content/vocabulary/game hooks đang import mock repos. Không launch emulator/điện thoại hoặc nhận đã tích hợp Mobile |
| Full Mock, EXAM-03 | Chưa triển khai trong lượt này | Không official TOEIC conversion, không tự chốt khi không có request; không mở rộng Sprint3 |

Đối chiếu TV2: CONTENT-02 đã chứng minh read/bookmark/history một luồng; CONTENT-03/Q-03 có CRUD backend nhưng còn UI/publish/media; PRAC-01/02 và Reading EXAM-01 đã có luồng tài khoản thật kể cả autosave, còn Listening/protected playback/EXAM-03. **Không đánh dấu toàn EXAM-01 hoặc Sprint2 Done chỉ vì Reading chạy được.**

## Kiểm chứng thực tế

Từ `frontend/web`:

```powershell
npm test
npm run lint
npm run build
npm run test:demo:e2e
```

- `npm test`: **49/49 pass, 0 skip**, gồm 8 test mới adapter/mapper/queue/deadline và 41 test hiện có. Adapter fixtures không được dùng thay bằng chứng HTTP thật.
- `npm run lint`: exit0, **9 warnings** (hooks/Fast Refresh/purity và unused vocabulary vars); không tắt rule để làm xanh.
- `npm run build`: exit0; bundle JS khoảng534kB, cảnh báo chunk >500kB; chưa tối ưu bundle ngoài phạm vi.
- `npm run test:demo:e2e`: **8/8 pass, 0 skip, 1.3 phút**, Chromium/headless dùng Vite/backend/DB/Mailpit thật, không intercept trả mock. Có ca chờ hết hạn thực một phút, không sửa DB timestamp. Bao gồm ownership401/404, permission403, cấu hình409, Teacher group CRUD, OTP.
- Browser bổ sung render dashboard metrics thật và chụp/kiểm tra hình màn `/practice` desktop. Không khẳng định mọi viewport/thiết bị đã kiểm.
- DB audit transaction **READ ONLY** đối chiếu identity/migration/eligibility/counts và grade/snapshot mismatch =0; seed chạy hai lần không nhân đôi.
- Stop helper parse và `-WhatIf` đạt; chưa dừng demo thật. Health web/backend/DB/Mailpit HTTP200, sockets kiểm cuối đều mở.
- Không chạy lại full backend vì không đổi Java/migration; **899 test backend** là bằng chứng baseline nhiệm vụ migration trước, không phải lượt chạy mới cho frontend. Backend runtime mới được kiểm bằng Flyway/profile + các HTTP/browser/DB ở trên.

Các lần đầu có test fail đã được đọc, không xóa/skip test: phát hiện lỗi timer floor đã sửa; locator vocabulary cần mở catalog/đi đúng detail và tránh nhầm personal deck cùng tên; test Teacher lấy nested content ID. Một lệnh đầu vô tình chọn thêm suite auth cũ, fixture reset của suite đó từ chối database demo theo guard (không sửa DB khác). Suite demo cuối dùng OTP thật, tách riêng script E2E, đã đạt 8/8. Không dùng lần chạy lỗi làm bằng chứng xanh.

Log/evidence ignored tại `backend/target/`: `demo-integration-node-verified.log`, `demo-integration-browser-verified.log`, `demo-integration-lint.log`, `demo-integration-build.log`, `demo-integration-audit.log`, `demo-integration-final-audit.json`, `demo-integration-practice.png`. Không đưa target/dist/test-results/password/token/log vào source.

## File nhiệm vụ và bảo toàn local

Manifest **28 file nhiệm vụ**, tất cả chưa stage:

```text
backend/scripts/local-demo.sql
backend/scripts/seed-local-demo.ps1
backend/scripts/start-local-demo.ps1
backend/scripts/stop-local-demo.ps1
backend/src/main/resources/application-local-demo.properties
docs/LOCAL_DEMO_FE_BE_Integration.md
docs/Frontend_Backend_Integration.md
docs/EXAM01_Section_Attempts.md
frontend/web/README.md
frontend/web/package.json
frontend/web/src/app/routes.jsx
frontend/web/src/components/molecules/CountdownTimer.jsx
frontend/web/src/components/organisms/ExamScoreSummary.jsx
frontend/web/src/components/organisms/LearnerSidebar.jsx
frontend/web/src/features/assessment/answer-save-queue.js
frontend/web/src/features/assessment/api/server-attempt.mapper.js
frontend/web/src/features/assessment/attempt-time.js
frontend/web/src/features/assessment/hooks/useAttemptSession.js
frontend/web/src/features/assessment/repositories/assessment.api.js
frontend/web/src/features/assessment/repositories/assessment.repo.js
frontend/web/src/features/assessment/ui/organisms/QuestionPanel.jsx
frontend/web/src/pages/assessment/AttemptPage.jsx
frontend/web/src/pages/assessment/PracticeSetupPage.jsx
frontend/web/src/pages/assessment/ResultPage.jsx
frontend/web/src/pages/assessment/TestCatalogPage.jsx
frontend/web/src/pages/vocabulary/VocabReviewPage.jsx
frontend/web/tests/assessment.remote.test.mjs
frontend/web/tests/demo.integration.spec.js
```

Riêng phần có sẵn: `AGENTS.md`, sáu backend Problems test files, `api-tests/TOEIC API AUTOMATION - R2.postman_collection.json`, `tatus` và `.vscode/settings.json` giữ nguyên byte theo SHA256 snapshot trước nhiệm vụ (10/10). Git ban đầu có 9 status entries; settings là cấu hình ignored, cũng được kiểm hash. Không tự sửa Problems hoặc Postman. Index vẫn trống. `git diff --check` phần nhiệm vụ, kể cả file mới qua no-index check, đạt. Các warning CRLF từ Git không phải whitespace error; thay đổi whitespace có sẵn trong AGENTS không được sửa hộ.

## Kịch bản demo khoảng 5 phút

1. **0:00–0:30** mở web, login Learner bằng file credentials local; xem dashboard số liệu thật. Chọn Luyện thi TOEIC.
2. **0:30–2:00** Practice Part5/4 câu: chọn/thay/Bỏ chọn, chờ Đã lưu, reload thấy đáp án server; trả lời hai câu, Nộp bài, Mở đáp án. Xem đúng/sai/bỏ trống/điểm thô và explanation, không có TOEIC990.
3. **2:00–3:30** Reading Section/8 câu/20 phút: thấy đủ Part5–7 và passage nguyên nhóm; chọn một câu, submit, xem thống kê ba Part; reload kết quả. Có thể thử count1 để thấy409 trước khi tạo phiên hợp lệ.
4. **3:30–4:15** Chủ đề → bài Workplace grammar → bookmark → đọc; không dùng trang draft Teacher làm dữ liệu Learner.
5. **4:15–5:00** Từ vựng → Xem tất cả → TOEIC Workplace Essentials → BẮT ĐẦU HỌC → detail → học thẻ → lật → TỐT; xem lịch SRS/tiến độ dashboard cập nhật. Nếu còn thời gian, thử Quên mật khẩu và lấy OTP trong Mailpit trên account tự đăng ký riêng.

Teacher/Admin chỉ có bằng chứng HTTP trong suite, chưa có màn CRUD riêng để trình diễn. Ca hết hạn một phút nên chạy riêng ngoài kịch bản nhanh. Giữ nguyên các dịch vụ demo để người dùng tiếp tục thử.
