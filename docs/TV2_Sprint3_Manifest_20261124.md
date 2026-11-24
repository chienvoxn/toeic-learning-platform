# Manifest TV2 EXAM-05 — 2026-11-24

Baseline `24839e0`, index giữ nguyên rỗng; chưa stage/commit/push.
Mỗi file dưới đây chỉ có delta nhiệm vụ so với baseline, trừ các hunk tài liệu chung
được nêu riêng. Không bao gồm hỗ trợ TV1/G1 trong manifest này.

## Backend TV2

- `backend/src/main/java/com/toeic/backend/assessment/api/PracticeAttemptController.java`: inject history + GET collection + strict pagination; không đổi submit API.
- `backend/src/main/java/com/toeic/backend/assessment/application/AttemptHistoryDtos.java` (mới): allowlist page/summary/score.
- `backend/src/main/java/com/toeic/backend/assessment/application/AttemptHistoryService.java` (mới): actor/source permissions, RR read transaction.
- `backend/src/main/java/com/toeic/backend/assessment/infrastructure/AttemptHistoryRepository.java` (mới): own/permission DB predicate trước page/count, persisted score only.
- `backend/src/main/java/com/toeic/backend/assessment/application/AttemptGrading.java`: trả Part stats đã lưu cho cả Practice; không đổi grading hoặc timeout.
- `backend/src/test/java/com/toeic/backend/assessment/api/AttemptHistoryIntegrationTest.java` (mới).
- `backend/src/test/java/com/toeic/backend/assessment/api/PracticeSubmissionIntegrationTest.java`: assertions Part stats Practice.
- `backend/src/test/java/com/toeic/backend/assessment/api/FullMockIntegrationTest.java`: history TEST_VERSION sau retire, summary không lộ key.

## Frontend TV2

- `frontend/web/src/pages/assessment/AttemptHistoryPage.jsx` (mới).
- `frontend/web/src/app/routes.jsx`: import + protected `/practice/history`.
- `frontend/web/src/components/organisms/LearnerSidebar.jsx`: link lịch sử own.
- `frontend/web/src/features/assessment/repositories/assessment.api.js`: GET paginated history, không mock fallback.
- `frontend/web/tests/assessment.remote.test.mjs`: history adapter contract.
- `frontend/web/tests/demo.integration.spec.js`: browser history qua FE–BE–DB demo thật; credential file ignored, không ghi secret trong test source.
- `frontend/web/tests/storage.integration.spec.js` (mới): upload/presign/byte match/private/expiry/reload qua backend/S3 thật với account fixture test, browser native media renderer; không nhận là Learner FE Listening E2E.

## Tài liệu

- Runbook `docs/TV2_Sprint3_Completion_20261124.md` và manifest này.
- `docs/API_Specification.md`: chỉ hunk EXAM-05/history/Practice Part stats thuộc TV2.
- Hunk G1 API trong cùng API spec và mọi file liệt kê tại
  `docs/G1_TV2_Support_20261124.md` thuộc hỗ trợ TV1, phải review/commit riêng.

## Bảo toàn và bằng chứng

Không có migration mới, không đổi SQL/checksum/layout/coordinator V34–V43.
Không lấy AGENTS.md, 5 file Problems test đã modified từ trước, Postman R2,
candidate TV1/V35, `tatus`, settings, credentials, backups hay outputs vào source commit.
`QuestionAuthoringConcurrencyTest.java` là file nền đã có sửa Problems: chỉ chạy test,
không có hunk nhiệm vụ mới trong đó.

Snapshot Git/index/background/settings/credentials trước nhiệm vụ tại Temp `start.json`.
Manifest ghi phạm vi ở mốc working tree trước review. Lượt review 2026-11-24 đã
được phép stage/commit đúng phạm vi: TV2 trước, G1/TV1 sau; API spec chỉ stage
hunk EXAM-05 trong commit TV2. Không có dependency code từ TV2 sang G1 chưa stage.
Nguồn index phải được xuất riêng để compile/package/test; bằng chứng 416 qua hai
lượt vẫn là working tree lịch sử, không tự gán cho từng commit.
Storage harness lấy nhãn source/DB từ metadata chạy kiểm chứng thay vì hard-code
HEAD/DB của lượt trước; không đổi hành vi upload/playback hoặc gọi là Learner E2E.

Nguồn staged TV2 18 path đã kiểm độc lập: 121 backend tests, package, 100 FE tests,
lint/build; scope và giới hạn bằng chứng ghi tại runbook. Cập nhật Markdown sau
test phải giữ nguyên blob mọi source/config đã kiểm, rồi cached diff/scan lại
trước commit. G1 và hunk G1 trong API spec không thuộc commit TV2.
