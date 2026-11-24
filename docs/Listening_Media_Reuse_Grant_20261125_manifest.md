# Manifest media reuse grant (source feature, chưa stage)

Base cf2b6d3; identity pthanh137 <sleapf137@gmail.com>. Không migration/schema mới.

| Path | Hunk nhiệm vụ |
|---|---|
| backend/src/main/java/com/toeic/backend/listening/infrastructure/MediaReuseGrantRepository.java | Owner-only metadata/page, user→media→grant locks, V20 activate/revoke |
| backend/src/main/java/com/toeic/backend/listening/application/MediaReuseGrantService.java | Session/permission/owner, idempotency, sharedRBAC, audit transaction |
| backend/src/main/java/com/toeic/backend/listening/api/MediaReuseGrantController.java | Owner list/grants và PUT/DELETE strict, không nhận actor/body/query mutation |
| backend/src/main/java/com/toeic/backend/listening/api/ListeningMediaExceptionHandler.java | Handler grant API; DB/audit503 không leakSQL |
| backend/src/main/java/com/toeic/backend/listening/infrastructure/S3CompatibleMediaStorageClient.java | loadObject fail-closed; bỏ tự sinh media khi S3 lỗi |
| backend/src/main/java/com/toeic/backend/listening/application/AttemptListeningMediaService.java | Đọc object thật, lỗi storage503 |
| backend/src/test/java/com/toeic/backend/questionbank/api/MediaReuseGrantIntegrationTest.java | 7case permission/strict/idempotency/concurrency/audit rollback/publish/revoke snapshot |
| backend/src/test/java/com/toeic/backend/listening/infrastructure/S3CompatibleMediaStorageClientTest.java | Giữ SigV4/bytes, thay kỳ vọng synthetic fallback bằng storage error |
| frontend/web/src/features/assessment/media-grants.api.js | API client owner grant |
| frontend/web/src/pages/teacher/TeacherMediaGrantsPage.jsx | UI tối thiểu owner/list/grant/revoke/regrant, request thật |
| frontend/web/src/app/routes.jsx | CHỈ import/new child route teacher/media; giữ hunk Sprint13 có sẵn |
| frontend/web/src/components/teacher/TeacherLayout.jsx | CHỈ nav Quyền dùng media; giữ hunk Sprint13 có sẵn |
| docs/API_Specification.md | Thêm section runtime grant 2026-11-24, supersedes trạng thái cũ |
| docs/Listening_Media_Reuse_Grant_20261125.md | Runbook/policy/runtime/storage/bằng chứng |
| docs/Listening_Media_Reuse_Grant_20261125_manifest.md | Manifest feature này |

Không stage toàn file shared để lấy nhầm hunk demo trước. Baseline snapshots của
routes/layout ở local existing-file-hunks. Chưa stage/commit bất kỳ path nào.
Test/JAR là workingtree, không staged source hoặc CI. Data/SQL lịch sử/fixtures
demo không thuộc source feature; xem manifest dữ liệu riêng. Giữ AGENTS/Problems/
PostmanR2/tatus/settings/credential, code chưa commit có sẵn và tác giả commit cũ.
