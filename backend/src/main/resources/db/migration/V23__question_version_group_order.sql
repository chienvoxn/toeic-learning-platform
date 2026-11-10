-- Explicit business order for a question within its pinned group version.
-- Legacy NULL stays unknown: never infer/backfill order from timestamps or UUIDs.
ALTER TABLE question_versions ADD COLUMN group_question_order INTEGER;
ALTER TABLE question_versions ADD CONSTRAINT chk_question_version_group_order
    CHECK (group_question_order IS NULL OR group_question_order > 0);
-- No global unique constraint: published/history/working-draft versions of the same
-- question may share a position. Practice checks uniqueness among current members.
