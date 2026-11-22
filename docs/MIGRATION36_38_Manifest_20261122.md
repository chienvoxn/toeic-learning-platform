# Manifest V36–V38 — 22/11/2026

**Lượt hoàn tất được ủy quyền 23/11/2026:** các đoạn33/20 path và index chưa sẵn sàng bên dưới ghi trạng thái bàn giao trước lượt này. Đối chiếu union54 path, stage đúng move/deletion và hunk nhiệm vụ, kiểm nguồn index độc lập rồi tạo merge commit/push theo ủy quyền mới. Không đưa file nền vào index. Ghi nhận kết quả lượt mới ở cuối tài liệu; không coi trạng thái cũ là kết quả cuối.

HEAD giữ nguyên `1f5d1bcaa7adad6e382694014f5c62824a928b9c`; MERGE_HEAD `314f1380411a7611b6252f4bc03381d56e770ed9`. Chưa commit/push/PR.

## Index do merge tạo

33 đường dẫn dưới đây được Git merge tự động stage; không tự stage thêm compatibility. Index hiện còn ba SQL vocabulary ở layout common cũ, nên **chưa phải cây commit hợp lệ**. Trước commit phải review và stage cả deletion/move cùng các hunk compatibility; không commit riêng index hiện tại.

- `backend/src/main/java/com/toeic/backend/identity/infrastructure/IdentitySecurityConfig.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/api/SubmitLearningSessionReviewRequest.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/api/VocabularyLearningSessionController.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/api/VocabularyReviewController.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/application/DeckFlashcardService.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/application/LearningRoundProgress.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/application/VocabularyLearningSessionService.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/application/VocabularyReviewRequestService.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/application/VocabularyService.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/domain/Vocabulary.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/infrastructure/DeckVocabularyRepository.java`
- `backend/src/main/java/com/toeic/backend/vocabulary/infrastructure/ReviewStateRepository.java`
- `backend/src/main/resources/db/migration/V36__vocabulary_archive_and_catalog_deck_deletion.sql`
- `backend/src/main/resources/db/migration/V37__vocabulary_review_idempotency.sql`
- `backend/src/main/resources/db/migration/V38__vocabulary_learning_permission.sql`
- `backend/src/test/java/com/toeic/backend/identity/api/AuthFlowIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/api/VocabularyReviewControllerTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/api/VocabularyReviewIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/application/DeckFlashcardServiceTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/application/VocabularyMlInferenceServiceTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/application/VocabularyRecommendationServiceTest.java`
- `backend/src/test/java/com/toeic/backend/vocabulary/application/VocabularyServiceTest.java`
- `docs/API_Specification.md`
- `docs/ERD_v1.6.md`
- `docs/WEB_Vocabulary_Integration.md`
- `frontend/web/scripts/vocabulary-review-ui-smoke.mjs`
- `frontend/web/src/features/vocabulary/repositories/vocabulary.api.js`
- `frontend/web/src/pages/ConnectedDashboard.jsx`
- `frontend/web/src/pages/profile/ProfileLearningPanel.jsx`
- `frontend/web/src/pages/profile/ProfilePage.jsx`
- `frontend/web/src/pages/vocabulary/VocabDeckListPage.jsx`
- `frontend/web/src/pages/vocabulary/VocabReviewPage.jsx`
- `frontend/web/tests/demo.integration.spec.js`

## Compatibility ngoài index

20 đường dẫn gồm ba Java coordinator/schema/history, hai test, sáu move lịch sử (12 old/new paths), V38.1, V43 và evidence metadata 14 dòng. SQL cũ giữ checksum/Git blob; không lấy file nền vào stage.

- `backend/src/main/resources/db/migration/V36__complete_settings.sql`
- `backend/src/main/resources/db/history/v36/completion/V36__complete_settings.sql`
- `backend/src/main/resources/db/migration/V37__complete_rag.sql`
- `backend/src/main/resources/db/history/v36/completion/V37__complete_rag.sql`
- `backend/src/main/resources/db/migration/V38__complete_moderation.sql`
- `backend/src/main/resources/db/history/v36/completion/V38__complete_moderation.sql`
- `backend/src/main/resources/db/migration/V36__vocabulary_archive_and_catalog_deck_deletion.sql`
- `backend/src/main/resources/db/history/v36/vocabulary/V36__vocabulary_archive_and_catalog_deck_deletion.sql`
- `backend/src/main/resources/db/migration/V37__vocabulary_review_idempotency.sql`
- `backend/src/main/resources/db/history/v36/vocabulary/V37__vocabulary_review_idempotency.sql`
- `backend/src/main/resources/db/migration/V38__vocabulary_learning_permission.sql`
- `backend/src/main/resources/db/history/v36/vocabulary/V38__vocabulary_learning_permission.sql`
- `backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationHistory.java`
- `backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationSchema.java`
- `backend/src/main/java/com/toeic/backend/system/infrastructure/migration/MigrationConfiguration.java`
- `backend/src/main/resources/db/history/v36/vocabulary/V38_1__complete_vocabulary_lineage_dependencies.sql`
- `backend/src/main/resources/db/migration/V43__complete_vocabulary_schema.sql`
- `backend/src/main/resources/db/compatibility/vocabulary-schema.txt`
- `backend/src/test/java/com/toeic/backend/system/infrastructure/migration/MigrationCompatibilityIntegrationTest.java`
- `backend/src/test/java/com/toeic/backend/system/infrastructure/migration/VocabularyLineageCompatibilityIntegrationTest.java`

## Tài liệu nhiệm vụ

- `docs/MIGRATION36_38_Source_Compatibility.md`
- `docs/MIGRATION36_38_Manifest_20261122.md`
- `docs/MIGRATION34_Source_Compatibility.md`
- `docs/MIGRATION35_Source_Compatibility.md`

## Hunk dùng chung

- MigrationConfiguration: thêm location do history36 chọn; policy/khóa/Boot/test/CLI giữ nguyên.
- MigrationHistory: prefix/identity36, migration set theo lineage, feature flags/schema43; policy34/35 giữ nguyên.
- MigrationSchema: overlay đúng14 dòng metadata vocabulary và permission catalog theo prefix; whitelist restore cũ không đổi.
- MigrationCompatibilityIntegrationTest: fixture location completion, expected current43 và restore fixture tới43; không nới assertion/validation.
- VocabularyLineageCompatibilityIntegrationTest:20 ca prefix/mixed/checksum/schema/clone/concurrency mới.
- IdentitySecurityConfig/AuthFlow/API/ERD: auto-merge giữ phần TV2 hiện hành và thay đổi vocabulary từ develop, không chọn ours/theirs nguyên file.

## File nền bảo toàn

6 tracked modified,35 untracked cùng settings: AGENTS.md, năm Problems, Postman/api-tests, tatus, CSV/data-driven tests/reports, hai tài liệu compatibility34/35 untracked, frontend-web/package-lock.json và .vscode/settings.json. Chúng không thuộc manifest nhiệm vụ. Kiểm hash42 file nền/settings,526 candidate/evidence và các artifact root cũ theo start.json.

Không git add toàn repository, không reset/stash/clean; không sửa SQL/history đã áp dụng. Manifest không nhận index là staged source đã kiểm; bằng chứng mới là candidate và main working tree.

## Hoàn thiện index và kiểm staged — 23/11/2026

Đã review union54 path thay vì cộng33+20+4. Ba SQL vocabulary common là path tạm AD do merge tạo; sau khi stage deletion/move, chúng không tồn tại ở HEAD cũ hoặc cây index cuối. Index có51 path diff khi tắt rename, tương ứng48 thay đổi khi nhận diện ba R100 completion. Sáu SQL lịch sử giữ nguyên Git blob/checksum; bốn migration set hỗ trợ không trùng version. API/ERD chỉ được bổ sung chú thích đúng lineage vocabulary/completion, không đổi contract.

Nguồn Git index đã được xuất độc lập, không copy file nền hoặc hai CSV test untracked. Compile/test-compile/package và176 ca liên quan có bằng chứng đạt: lượt đầu176 ca,138 đạt/38 error vì PostgreSQL Windows1455 rồi recovery; kiểm lại hai nhóm bị ảnh hưởng39/39 đạt trên cluster mới với heap/context nhỏ hơn. Không sửa source/guard/validation, không lặp full local. Frontend từ nguồn staged99/99 đạt. JAR có60 resource DB/50 SQL khớp nguồn; completion44 và vocabulary45 version không trùng.

Chính JAR staged đã migrate/validate qua CLI và Boot/JPA/health hai lần trên hai DB restore riêng: backup lab completion42 và vocabulary38 dựng theo SQL develop. Data/history/sequence được giữ; schema khớp canonical và restart không mutation. Chưa có clone DB thành viên lâu dài. Full1.266 test/1 skipped vẫn mang nhãn working tree trước commit; browser cũ dùng HTTP fixtures, không nhận là E2E BE thật. Sau kiểm code chỉ cập nhật hai tài liệu kết quả, không đổi source/test/resource đã kiểm.

Artifact mới ignored trong `backend/target/v36-merge-20261123/`: staged-export-proof.json, staged-tests-proof.json, staged-package-proof.json, staged-migration-scan.json, staged-runtime-proof.json và log. Chúng không thuộc commit. Commit merge được ủy quyền dùng pthanh137 <sleapf137@gmail.com>, message `merge: integrate develop and reconcile V36-V38 migration histories`, parents1f5d1bc và314f138; không amend/rebase commit cũ. Kết quả push/CI phải kiểm bằng SHA thực tế sau commit, không lấy CI200 thay thế.
