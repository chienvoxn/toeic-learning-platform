# Manifest EXAM-03 — review riêng phần worker

Baseline publication/Full Mock: working tree 63 đường dẫn trên ee95862, được lưu hash/nội dung **trước khi sửa worker** tại ignored `backend/target/exam03-start-20261121.json`. Nhánh vẫn feat/web-auth-integration, index không đổi. Không dùng `git add .` hoặc stage toàn file dùng chung để gộp hai phạm vi.

## Hai phạm vi review/commit về sau

1. Publication/Full Mock: [manifest 2026-11-21](TV2_Publication_FullMock_Manifest_20261121.md), dùng **nội dung baseline** 63 file đã bàn giao, gồm V41/prefix tới V41. Attribution PR #61 tại d3d6f0b giữ nguyên. Chưa có worker trong baseline này.
2. Worker: delta từ baseline đó tới nguồn EXAM-03 hiện tại, phụ thuộc publication đi trước. Không đánh đồng diff toàn repository so với HEAD với diff worker. Patch review và hash được xuất riêng dưới target, không tự stage hoặc áp patch.

## Hunk worker trong 8 file dùng chung với publication

| File | Hunk worker cần review riêng |
|---|---|
| assessment/application/AttemptGrading.java | Dùng internal pinned-key port; chặn dữ liệu selection timestamp ngoài hạn chỉ cho Full Mock; clear durable retry cùng grading transaction |
| system/infrastructure/migration/MigrationHistory.java | Thêm release version 42 và cờ schema V42; không đổi nhận diện V34/V35 |
| system/infrastructure/migration/MigrationSchema.java | Chọn fingerprint V42 cho history V42; không sửa fingerprint/restore rules V41 |
| assessment/api/FullMockIntegrationTest.java (test) | Chuyển helpers sang FullMockTestSupport; giữ 15 ca publication/runtime cũ |
| migration/MigrationCompatibilityIntegrationTest.java (test) | Expected current=42; fixture index restore dựng release hiện hành trước validate, không giả history |
| docs/API_Specification.md | Chỉ phần bổ sung EXAM-03 ngày 2026-11-22 |
| frontend FullMockOverview.jsx | Hướng dẫn Full Mock tự chốt và chỉ dùng lựa chọn server nhận trước hạn |
| frontend AttemptPage.jsx | Thông báo chờ worker/mất mạng và khóa retry PUT khi Full Mock hết hạn |

55 đường dẫn publication còn lại giữ nguyên byte. File dùng chung phải review/stage đúng hunk sau khi có ủy quyền; lượt này chưa stage.

## Phần riêng worker

- Backend `pom.xml`: Surefire tắt scheduler thread trong test để điều khiển Clock; không skip JUnit; JAR diễn tập bật scheduler mặc định.
- `AttemptExpiryWorker`, `AttemptExpiryTransaction`, `AttemptExpiryRepository`, `AttemptExpirySchedule`: batch, khóa/transaction, retry và scheduler.
- `PinnedQuestionGrading`, hunk `PracticeQuestionCatalog`: trích đúng frozen-key reader, giữ auth guard của public catalog, không có route mới.
- Hunk `PracticeAnswerRepository`: clear retry marker trong cùng transaction grading.
- V42__full_mock_expiry_retries.sql và schema-v42.txt: chỉ phần schema worker mới; V1–V41 giữ nguyên.
- `FullMockTestSupport`, `AttemptExpiryIntegrationTest`: fixture tổng hợp dùng chung và Clock/rollback/retry/concurrency/legacy-policy tests.
- Frontend queue/hook, `expired-attempt-watcher`, hunk `SaveStatusBadge`: seal, nhận acknowledgement/reconcile từ server và polling GET.
- `expired-attempt.test.mjs`, `attempt-expiry.live.spec.js`: unit + browser thật/mất mạng/đóng browser.
- Runbook và manifest EXAM-03 ngày 2026-11-22.

Danh sách path/hunk chính xác và patch riêng được ghi vào bằng chứng handoff sau kiểm chứng. Không đưa patch/log/dump/JSON evidence/JAR/POM tạm trong target vào commit.

## File nền và điều kiện trước commit

Giữ nguyên 42 file nền/settings và 526 file candidate/evidence: AGENTS.md, năm file Problems, Postman R2/api-tests, backend/backend, unit-tests/CSV/reports và hai data-driven tests untracked, docs MIGRATION34/35 Compatibility untracked, frontend-web/package-lock.json, tatus, .vscode/settings.json ignored. Không đưa chúng vào hai commit TV2.

Không stage/commit/push/PR trong lượt này. Trước commit sau này: fetch lại khi GitHub DNS khả dụng, kiểm V42/collision trên remote mới; hiện chỉ có cached origin/develop 98d81c0 và lần fetch thành công trước đó. Không tự renumber migration đã áp dụng nếu phát hiện collision. Review patch hai phạm vi, secret/artifact, nguồn staged độc lập và identity pthanh137 <sleapf137@gmail.com> trước khi commit.

## Lượt review/commit được ủy quyền tiếp theo

Fetch đã thành công tới origin/develop 314f138; toàn bộ 45 refs không có V41/V42. Các collision V36–V38 mới được ghi riêng, chưa merge develop hoặc đổi migration. Commit A 4ad1590 đã chứa chính xác baseline 63 path và đã kiểm độc lập; nguồn B là 28 path delta trên A, trong đó tám file chung chỉ còn hunk worker. Không stage file nền. Các lệnh cấm stage/commit ở đoạn trước là trạng thái bàn giao trước ủy quyền lượt mới; lượt mới chỉ cho phép hai commit A/B, chưa push/PR.

Nguồn index B đã package đạt 84 test backend và 99 test frontend; JAR thực tế đã kiểm restart không session, hai tiến trình và submit replay trên clone lab riêng. Xem runbook cùng ngày để biết lệnh/phạm vi và file bằng chứng. Chỉ hai tài liệu EXAM-03 được bổ sung kết quả sau tree kiểm thử; code/test/resource không đổi. Commit B vẫn đúng 28 đường dẫn, không chứa full 1.235 như bằng chứng commit, không đưa file nền hoặc artifact vào Git.
