# Manifest demo Sprint 1–3 (chưa stage/commit)

Base/remote/PR: `cf2b6d372fe6891b06f7a6c9791d0bd2bcce93e6`, Draft PR73.
Không đưa file nền vào phạm vi. Không thay BE, API contract, schema, historical
SQL, coordinator V34–V43, blueprint hoặc worker policy trong lượt này.

| Path | Hunk nhiệm vụ |
|---|---|
| `frontend/web/src/app/routes.jsx` | Cùng URL `/teacher/content` chuyển vào RequireTeacher + TeacherLayout, dùng editor có sẵn embedded |
| `frontend/web/src/components/teacher/TeacherLayout.jsx` | Nav editor, tên mục đúng chức năng; class/reports chưa có đánh dấu disabled thay route404 |
| `frontend/web/src/pages/admin/AdminContentPage.jsx` | Prop embedded với Fragment, query kind whitelist, giữ permission/API; copy publication/G1 đúng thực tế và link bộ đề |
| `frontend/web/src/pages/teacher/TeacherContentsPage.jsx` | Nút tạo học liệu nối editor hiện hữu, không tạo API/editor trùng |
| `frontend/web/src/pages/teacher/TeacherQuestionsPage.jsx` | Link sang editor nhóm hiện hữu |
| `frontend/web/src/pages/teacher/TeacherDashboardPage.jsx` | Trạng thái các luồng đã nối và giới hạn Full Mock chính xác |
| `frontend/web/tests/sprint13-demo.live.spec.js` | Chrome/API/DB thật Auth/Teacher/Admin/Learner/SRS/AI giới hạn; credential env local, không route mock; checkpoint tránh nhân dữ liệu |
| `docs/SPRINT13_Web_Demo_20261125.md` | Bàn giao GitHub, bảng chức năng, vận hành, chứng cứ, backup, kịch bản và dependency |
| `docs/SPRINT13_Web_Demo_20261125_manifest.md` | Manifest này |

Sáu path runtime FE modified + một test và hai docs mới = **9 path nhiệm vụ**.
Index phải tiếp tục rỗng. FE107 unit/lint/build áp dụng runtime cuối; browser
hoàn tất sáu ca qua ba lượt có log giữ nguyên, không gọi một lượt 6/6.

## Local ignored — không commit

`backend/target/sprint13-demo-20261124/`: start/stop/check DB helper, demo_ops,
mail/storage probe, GitHub delivery helper, final_proof, logs/checkpoints và
credentials ACL riêng. Mailpit official binary/checksum; không vendor vào Git.
`backups.local/sprint13-demo-20261125/`: custom backup330690 bytes, roles không
password, history/count/hash proof và TOC; chưa restore thử backup mới.
Storage thật tái sử dụng `backend/target/demo-runtime-20261123/storage/`.
DB review/data directory cũ giữ nguyên, không tạo fixture DB mới.

## Nền/ngoài phạm vi — không stage

AGENTS.md; Problems trong GoogleAuthIntegrationTest, PasswordChangeIntegrationTest,
RegistrationFlowIntegrationTest, SessionIssuerTest, QuestionAuthoringConcurrencyTest;
api-tests/Postman R2; backend/backend; AuthDataDrivenTest, VocabularyDataDrivenTest,
unit-test-data; tài liệu MIGRATION34/35 chưa track; frontend-web; tatus; unit-tests;
settings/credential cũ. Baseline là **6 tracked modified +35 untracked**, 43 hash
kể cả hai ignored file được kiểm trước/sau. Không sửa tác giả commit TV1/TV2.

Các operating log S3 cũ bị tái tạo ở lần start đầu được nêu riêng trong runbook;
không gán baseline43 hash thành bằng chứng bảo toàn mọi artifact cũ.

## Review trước commit tương lai

Review riêng 9 path trên, xác minh không chứa secret/output, dùng staged source
độc lập nếu được cho phép commit sau này. Không lấy CI cf2b6d3 làm CI cho demo
working tree. Không tự add toàn bộ repo, không đưa backup/runtime/credentials vào
index và không merge PR73.
