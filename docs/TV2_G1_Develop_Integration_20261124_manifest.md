# Manifest TV2/G1 + develop — working tree 2026-11-24

> Lượt hoàn tất được phép stage/commit: bảng 56 path và 8 U dưới đây là snapshot
> trước commit A `9042dda`. Danh sách delta commit B ở cuối; không đưa file nền
> vào hai commit. Tree/parents/SHA thực tế trong Git và evidence `commit-A/B.json`.

HEAD `04876d3`, MERGE_HEAD `6ff5f74`, merge base `314f138`.
**Chưa stage tay, commit, push hoặc PR.** Manifest mô tả nguồn working tree đã kiểm;
không coi index hiện tại là nguồn staged đã đạt kiểm chứng.

## Index và phạm vi

- 42 path incoming; 34 path stage 0 tự stage bởi Git merge và 8 path unmerged.
- 56 path nhiệm vụ bên dưới là hợp của incoming và hunk tích hợp/new test/docs; không cộng mù các danh sách.
- 6 tracked modified + 35 untracked file nền giữ nguyên; 43 SHA-256 gồm settings/credentials khớp baseline.
- Không thêm/sửa/delete SQL hoặc coordinator migration; không có resource move trong lượt tích hợp này.
- `U` vẫn ở index vì chưa được stage lời giải; working tree không còn marker. `AM/MM` có hunk working tree chưa nằm trong index.

## File/hunk cần review cho lần stage được cho phép sau

| Path | Trạng thái hiện tại | Phạm vi/hunk |
|---|---|---|
| `backend/src/main/java/com/toeic/backend/ai/application/ChatService.java` | `M ` | Incoming PR70 exam gates before provider and persistence/replay, same actor lock. |
| `backend/src/main/java/com/toeic/backend/ai/application/ExamGuardService.java` | `AM` | Incoming PR70 plus existing FULL_MOCK type. |
| `backend/src/main/java/com/toeic/backend/assessment/api/ExamDraftExceptionHandler.java` | ` M` | Assembly refusal uses structured canonical exception response. |
| `backend/src/main/java/com/toeic/backend/assessment/api/FullTestAttemptController.java` | `AM` | Old GET/start URLs delegate to the shared engine; strict query/body checks. |
| `backend/src/main/java/com/toeic/backend/assessment/api/PracticeExceptionHandler.java` | ` M` | Old FullTest adapter uses existing structured errors. |
| `backend/src/main/java/com/toeic/backend/assessment/api/TeacherDraftAssemblyController.java` | `AM` | Own/permission check, explicit 409; remove direct SQL assembly under OPEN 200-question policy. |
| `backend/src/main/java/com/toeic/backend/assessment/api/TeacherTestController.java` | `AA` | Canonical route/controller, title-only adapter creates audited DRAFT; full document/revision/workflow retained. |
| `backend/src/main/java/com/toeic/backend/assessment/application/FullMockSessionService.java` | ` M` | Actor lock for AI synchronization; published catalog source/evidence/media checks; timestamp normalization. |
| `backend/src/main/java/com/toeic/backend/assessment/application/FullTestCatalogService.java` | `AM` | DTO adapter from canonical catalog; server duration/count/blueprint. |
| `backend/src/main/java/com/toeic/backend/assessment/application/FullTestSessionService.java` | `AM` | Delegate only; no second creator or fixed 200/120 policy. |
| `backend/src/main/java/com/toeic/backend/assessment/application/PracticeAccess.java` | `UU` | Resolve permission union; no relaxation or extra submit/result route. |
| `backend/src/main/java/com/toeic/backend/assessment/application/SectionSessionService.java` | `M ` | Incoming actor lock, retain source selection and current Section policy. |
| `backend/src/main/java/com/toeic/backend/assessment/domain/PracticeAttempt.java` | `UU` | Keep blueprint model; strict read-only recognition of original develop legacy FULL_TEST shape. |
| `backend/src/main/java/com/toeic/backend/assessment/infrastructure/AttemptHistoryRepository.java` | ` M` | Own pagination includes recognized legacy; stored grading only. |
| `backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepository.java` | `UU` | Actor lock and own recognized legacy/canonical reads/writes. |
| `backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java` | ` M` | Add old full-tests alias under existing exam permission. |
| `backend/src/main/java/com/toeic/backend/listening/infrastructure/ListeningMediaRepository.java` | ` M` | Legacy recognition under existing owned attempt/media checks. |
| `backend/src/main/java/com/toeic/backend/system/application/MediaPublicationPolicy.java` | ` M` | Read-only availability check for catalog mirrors existing owner/grant/type/readability rules. |
| `backend/src/test/java/com/toeic/backend/ai/ChatIntegrationTest.java` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `backend/src/test/java/com/toeic/backend/ai/application/ExamGuardServiceTest.java` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `backend/src/test/java/com/toeic/backend/assessment/api/DevelopAssessmentIntegrationTest.java` | `??` | Adapter/title-only/revision/permission and FULL_MOCK AI guard delta tests. |
| `backend/src/test/java/com/toeic/backend/assessment/api/FullTestCatalogIntegrationTest.java` | `AM` | Retain incoming auth/eligibility assertions against canonical MINI-LAB publication/media fixtures. |
| `backend/src/test/java/com/toeic/backend/assessment/api/LegacyFullTestIntegrationTest.java` | `??` | Original factory-shaped fixture; own read/save/stored replay, malformed rejection and old expiry/worker policy. |
| `docs/API_Specification.md` | `MM` | Contract, runbook/API hoặc manifest tích hợp; giữ đúng nhãn bằng chứng và OPEN. |
| `docs/RAG_Chat_Integration.md` | `MM` | Contract, runbook/API hoặc manifest tích hợp; giữ đúng nhãn bằng chứng và OPEN. |
| `docs/TV2_G1_Develop_Integration_20261124.md` | `??` | Contract, runbook/API hoặc manifest tích hợp; giữ đúng nhãn bằng chứng và OPEN. |
| `docs/TV2_G1_Develop_Integration_20261124_manifest.md` | `??` | Contract, runbook/API hoặc manifest tích hợp; giữ đúng nhãn bằng chứng và OPEN. |
| `frontend/web/package-lock.json` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/package.json` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/app/routes.jsx` | `UU` | New Teacher shell/pages plus existing canonical editor/admin/moderation/history/Guest and Learner routes. |
| `frontend/web/src/components/organisms/LearnerSidebar.jsx` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/components/teacher/TeacherLayout.jsx` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/features/assessment/api/server-attempt.mapper.js` | `UU` | Keep server blueprint and source labels; worker expiry flag only for snapshot-backed FullMock. |
| `frontend/web/src/features/assessment/hooks/useAttemptSession.js` | ` M` | Do not poll legacy FULL_TEST as worker-managed; preserve autosave/submit queue. |
| `frontend/web/src/features/assessment/repositories/assessment.api.js` | `MM` | UI aliases call canonical catalog and attempts; retain history/save/submit/result/media. |
| `frontend/web/src/features/assessment/ui/molecules/PracticeGoalTimer.jsx` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/features/assessment/ui/organisms/AnswerNavigator.jsx` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/features/auth/RequireTeacher.jsx` | `AM` | Keep Teacher/Admin shell, allow server-proven custom-role author/reviewer capability. |
| `frontend/web/src/features/teacher/teacherContent.api.js` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/features/teacher/teacherExams.api.js` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/features/teacher/teacherQuestions.api.js` | `AM` | Keep team CRUD/list/group APIs and add reference options client. |
| `frontend/web/src/pages/assessment/AttemptPage.jsx` | `MM` | New goal timer/shell with preserved autosave/result/worker behavior; legacy expiry is explicit. |
| `frontend/web/src/pages/assessment/PracticeSetupPage.jsx` | `UU` | Retain Part/Listening/Reading config, new goal timer and FullTest selection; no new numeric blueprint. |
| `frontend/web/src/pages/assessment/TeacherTestsPage.jsx` | ` M` | Embed canonical editor in new shell, load selected testId; keep all workflow actions. |
| `frontend/web/src/pages/assessment/TestCatalogPage.jsx` | `UU` | New catalog cards, dynamic server metadata, explicit Guest samples; getCatalog contract fix. |
| `frontend/web/src/pages/teacher/TeacherContentsPage.jsx` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/pages/teacher/TeacherDashboardPage.jsx` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/pages/teacher/TeacherExamsPage.jsx` | `AM` | New cards/title modal connected to canonical editor and publication/version flow. |
| `frontend/web/src/pages/teacher/TeacherQuestionEditor.jsx` | `AM` | Part 5 ID from reference API, no personal DB ID; keep new form. |
| `frontend/web/src/pages/teacher/TeacherQuestionsPage.jsx` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/pages/teacher/teacher.css` | `A ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/pages/vocabulary/VocabDeckDetailPage.jsx` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/pages/vocabulary/VocabDeckListPage.jsx` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/src/pages/vocabulary/VocabReviewPage.jsx` | `M ` | Incoming UI/test/config từ develop; giữ tác giả qua merge, review trong diff HEAD. |
| `frontend/web/tests/assessment.remote.test.mjs` | `MM` | Canonical URL/payload and blueprint/legacy expiry mapper assertions. |
| `frontend/web/tests/develop.integration.spec.js` | `??` | Opt-in private fixture credentials; real browser Teacher/Practice/Reading/history/moderation; no HTTP stubs. |

## Entry index chưa resolved

- `backend/src/main/java/com/toeic/backend/assessment/api/TeacherTestController.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/PracticeAccess.java`
- `backend/src/main/java/com/toeic/backend/assessment/domain/PracticeAttempt.java`
- `backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepository.java`
- `frontend/web/src/app/routes.jsx`
- `frontend/web/src/features/assessment/api/server-attempt.mapper.js`
- `frontend/web/src/pages/assessment/PracticeSetupPage.jsx`
- `frontend/web/src/pages/assessment/TestCatalogPage.jsx`

## Nền loại hoàn toàn khỏi stage

Các đường dẫn sau có hash bảo toàn; không dùng `git add .`, không stage settings/credentials, log/JAR/dump hoặc file private actor fixture.

- `AGENTS.md`
- `backend/src/test/java/com/toeic/backend/identity/api/GoogleAuthIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/identity/api/PasswordChangeIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/identity/application/RegistrationFlowIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/identity/application/SessionIssuerTest.java`
- `backend/src/test/java/com/toeic/backend/questionbank/api/QuestionAuthoringConcurrencyTest.java`
- `api-tests/collections/TOEIC API AUTOMATION - R2.postman_collection.json`
- `api-tests/collections/TOEIC_API_AUTOMATION_R2.postman_collection.json`
- `api-tests/collections/TOEIC_API_AUTOMATION_R2_BVA.postman_collection.json`
- `api-tests/collections/TOEIC_API_AUTOMATION_R2_NEW.postman_collection.json`
- `api-tests/environments/local.postman_environment.json`
- `api-tests/reports/auth-data-driven.json`
- `api-tests/reports/auth-data-driven.xml`
- `api-tests/reports/test-summary.csv`
- `api-tests/reports/uc01-auth.json`
- `api-tests/reports/uc01-auth.xml`
- `api-tests/reports/uc02-vocab.json`
- `api-tests/reports/uc02-vocab.xml`
- `api-tests/reports/vocabulary-bva.json`
- `api-tests/reports/vocabulary-bva.xml`
- `api-tests/scripts/run-all-tests.ps1`
- `api-tests/test-data/auth-test-data.csv`
- `api-tests/test-data/vocabulary-test-data.csv`
- `backend/backend/src/test/resources/unit-test-data/vocabulary-create.csv`
- `backend/src/test/java/com/toeic/backend/identity/application/AuthDataDrivenTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/application/VocabularyDataDrivenTest.java`
- `backend/src/test/resources/unit-test-data/auth-login.csv`
- `backend/src/test/resources/unit-test-data/vocabulary-create.csv`
- `docs/MIGRATION34_Compatibility_Integration.md`
- `docs/MIGRATION35_Compatibility_Integration.md`
- `frontend-web/package-lock.json`
- `tatus`
- `unit-tests/reports/auth-data-driven.xml`
- `unit-tests/reports/unit-auth.xml`
- `unit-tests/reports/unit-test-details.csv`
- `unit-tests/reports/unit-test-summary.csv`
- `unit-tests/reports/unit-vocab.xml`
- `unit-tests/reports/vocabulary-data-driven.xml`
- `unit-tests/scripts/run-all-unit-tests.ps1`
- `unit-tests/test-data/auth-login.csv`
- `unit-tests/test-data/vocabulary-create.csv`
- `.vscode/settings.json`
- `backend/target/local-demo-credentials.json`

## Kiểm chứng và điều kiện hoàn tất merge

Xem [runbook](TV2_G1_Develop_Integration_20261124.md) và `backend/target/tv2-develop-integration-20261124/final-audit.json`.
Backend rộng: 1.248 test ghi nhận, 1.247 thực thi, 1 skipped; không full toàn bộ repo. Exclude MigrationCompatibilityIntegrationTest và 2 test nền nhưng sót VocabularyLineageCompatibilityIntegrationTest (20 test/22 DB fixture); đã lưu fingerprint và dọn đúng các DB đó, không chạy lại khi resume. FE 101/101 + lint/build; live browser từng ca có log riêng.
Không nhận G1 HTTP batch là UI batch; không nhận MINI-LAB là Full Mock chuẩn hay media playback thật.
Cần review/cho phép stage các path/hunk nhiệm vụ và test nguồn staged độc lập trước commit merge. FULL-LR/v1/Section timing còn OPEN, chưa seed hoặc áp cho attempt cũ.

## Remote tiến thêm, ngoài nguồn đã kiểm

Fetch cuối: `origin/develop=20c63e776b656efd5ba0daaaae3447284e863cac` (PR72).
Chưa nhập 4 path delta PR72; MERGE_HEAD vẫn 6ff5f74, không merge chồng hoặc tự
đổi index/MERGE_HEAD. Chi tiết và rủi ro marker AI trên Practice trong runbook.

- `frontend/web/src/features/ai/ChatBubble.jsx` — chưa nhập.
- `frontend/web/src/pages/ConnectedDashboard.jsx` — chưa nhập.
- `frontend/web/src/pages/assessment/AttemptPage.jsx` — overlap với nguồn hiện tại, delta PR72 chưa nhập.
- `frontend/web/src/pages/vocabulary/VocabDeckListPage.jsx` — overlap với nguồn hiện tại, delta PR72 chưa nhập.

Không thêm bốn path pending thành bốn path đã tích hợp; bảng 56 path trên chỉ
cho cây 04876d3 + 6ff5f74. Không gán test/browser hiện tại cho develop20c63e7.

## Delta commit B — tích hợp riêng PR72 20c63e7

Parents: `9042dda` và `20c63e7`. BE, historical SQL/coordinator, dependency
package/lock và blueprint không đổi so với A; không nhập develop sau mốc này.

| File | Hunk tích hợp |
|---|---|
| `frontend/web/src/features/ai/ChatBubble.jsx` | UI guard PR72, actor-scoped hint, server-denied history/ask, guarded read retry/poll, lỗi mạng/404 không tự unlock; không hiển thị cached messages khi khóa |
| `frontend/web/src/features/ai/exam-tutor-state.js` | Helper hint theo actor/attempt/source/deadline; không chặn Practice; logout actor khác không kế thừa; backend là nguồn quyết định |
| `frontend/web/src/pages/ConnectedDashboard.jsx` | Link due review từ PR72 |
| `frontend/web/src/pages/assessment/AttemptPage.jsx` | Hint từ snapshot server của exam; không xóa khi unmount; submit thành công bỏ đúng hint; giữ worker/queue/timer/pin |
| `frontend/web/src/pages/assessment/TeacherTestsPage.jsx` | Loại response read cũ bằng generation, bảo toàn nội dung editor/revision |
| `frontend/web/src/pages/vocabulary/VocabDeckListPage.jsx` | Due modal/query/loading và link Dashboard của PR72, giữ API/SRS/progress |
| `frontend/web/tests/exam-tutor-state.test.mjs` | Practice/Guest, expiry, nhiều attempt/tab, actor, denial và storage |
| `frontend/web/tests/exam-tutor.live.spec.js` | Browser/API/DB thật: Practice/Section, hint bypass, offline, đóng tab/restart/expiry, pin và replay |
| `frontend/web/tests/chat.widget.spec.js` | Case HTTP fixture cho probe conversation bị xóa, không nhận là E2E BE |
| `frontend/web/tests/develop.integration.spec.js` | Assert payload mô tả DRAFT được lưu đúng, giữ toàn bộ luồng real Teacher/Learner/moderation |
| `docs/TV2_G1_Develop_Integration_20261124.md` | Kết quả hai commit/index proof, nguồn kiểm chứng và giới hạn |
| `docs/TV2_G1_Develop_Integration_20261124_manifest.md` | Delta/phạm vi B; giữ riêng baseline 6 tracked +35 untracked |

Index B phải khớp nguồn FE cuối đã test/lint/build và browser; không dựa vào
code ngoài index. BE tree khớp A nên tái sử dụng package/82 test A; không chạy
backend rộng/migration lại. Giữ 1.247/1 skipped là bằng chứng working tree cũ.
