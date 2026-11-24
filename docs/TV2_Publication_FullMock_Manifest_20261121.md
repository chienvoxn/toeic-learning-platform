# TV2 publication / Full Mock: manifest working tree 2026-11-21

Baseline ee95862; NO stage/commit/push. Review list below excludes all 42 protected background/settings files.

Shared hunks: IdentitySecurityConfig adds teacher blueprint/review/publish and learner catalog guards; RbacRepository adds enforced test:review/test:publish; ModerationRepository/Service extends kind tests using the same contributor/revision/audit engine; MediaPublicationPolicy adds linked test media; ListeningMediaRepository admits recognized TEST_VERSION pins; MigrationHistory/Schema extends release to V41 and exact canonical evidence. Existing V34/V35 policy and applied SQL stay unchanged.

Task files (63 paths; entire task diff against baseline, shared hunks described above):

- `backend/src/main/java/com/toeic/backend/assessment/api/ExamDraftExceptionHandler.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/ExamDraftRequest.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/FullMockCatalogController.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/PracticeAttemptController.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/PracticeExceptionHandler.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/StartPracticeRequest.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/TeacherTestController.java`
- `backend/src/main/java/com/toeic/backend/assessment/api/TestBlueprintController.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/AttemptGrading.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/ExamDraftException.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/ExamDraftService.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/ExamMutationAccess.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/ExamPublicationPolicy.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/ExamReferencePolicy.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/ExamWorkflowService.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/FullMockSessionService.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/PracticeAccess.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/PracticeResultDtos.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionDtos.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/PracticeSessionService.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/PracticeSubmissionService.java`
- `backend/src/main/java/com/toeic/backend/assessment/application/TestBlueprintService.java`
- `backend/src/main/java/com/toeic/backend/assessment/domain/PracticeAttempt.java`
- `backend/src/main/java/com/toeic/backend/assessment/domain/TestAttemptSnapshot.java`
- `backend/src/main/java/com/toeic/backend/assessment/domain/TestBlueprintSnapshot.java`
- `backend/src/main/java/com/toeic/backend/assessment/infrastructure/ExamDraftRepository.java`
- `backend/src/main/java/com/toeic/backend/assessment/infrastructure/PracticeAttemptRepository.java`
- `backend/src/main/java/com/toeic/backend/assessment/infrastructure/TestBlueprintRepository.java`
- `backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java`
- `backend/src/main/java/com/toeic/backend/identity/infrastructure/RbacRepository.java`
- `backend/src/main/java/com/toeic/backend/listening/infrastructure/ListeningMediaRepository.java`
- `backend/src/main/java/com/toeic/backend/system/application/AssessmentPublication.java`
- `backend/src/main/java/com/toeic/backend/system/application/MediaPublicationPolicy.java`
- `backend/src/main/java/com/toeic/backend/system/application/ModerationService.java`
- `backend/src/main/java/com/toeic/backend/system/infrastructure/ModerationRepository.java`
- `backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationHistory.java`
- `backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationSchema.java`
- `backend/src/main/resources/db/compatibility/restored-constraints-v41.txt`
- `backend/src/main/resources/db/compatibility/restored-indexes-v41.txt`
- `backend/src/main/resources/db/compatibility/schema-v41.txt`
- `backend/src/main/resources/db/migration/V41__test_publication_and_full_mock.sql`
- `backend/src/test/java/com/toeic/backend/assessment/api/ExamDraftIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/assessment/api/FullMockIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/system/infrastructure/migration/MigrationCompatibilityIntegrationTest.java`
- `docs/API_Specification.md`
- `docs/EXAM04_Draft_Authoring_20261120.md`
- `docs/TV2_Publication_FullMock_20261121.md`
- `docs/TV2_Publication_FullMock_Manifest_20261121.md`
- `frontend/web/src/features/assessment/ExamDraftNavigation.jsx`
- `frontend/web/src/features/assessment/FullMockCatalog.jsx`
- `frontend/web/src/features/assessment/FullMockOverview.jsx`
- `frontend/web/src/features/assessment/api/server-attempt.mapper.js`
- `frontend/web/src/features/assessment/exam-drafts.api.js`
- `frontend/web/src/features/assessment/repositories/assessment.api.js`
- `frontend/web/src/pages/assessment/AttemptPage.jsx`
- `frontend/web/src/pages/assessment/ResultPage.jsx`
- `frontend/web/src/pages/assessment/TeacherTestsPage.jsx`
- `frontend/web/src/pages/assessment/TestCatalogPage.jsx`
- `frontend/web/src/pages/assessment/TestOverviewPage.jsx`
- `frontend/web/tests/assessment.remote.test.mjs`
- `frontend/web/tests/exam-drafts.spec.js`
- `frontend/web/tests/fullmock.live.spec.js`

Do not stage AGENTS.md, the five existing Problems edits, api-tests/Postman R2, unit-tests/CSVs/reports, backend/backend, the two untracked data-driven test classes/resources, frontend-web/package-lock.json, docs/MIGRATION34_Compatibility_Integration.md, docs/MIGRATION35_Compatibility_Integration.md, tatus or ignored .vscode/settings.json. Dumps, lab credentials, logs, target/node_modules/dist remain outside source.

V41 is new. V1-V40 and historical V34/V35 contents/checksums are unchanged. Keep d3d6f0b attribution to PR #61; no member branch imported again.
