-- PRAC-02: preserve the marking key/explanation used at submission, including unanswered questions.
-- Nullable for existing autosaves and legacy rows; no historical result is inferred/backfilled.
ALTER TABLE attempt_answers
    ADD COLUMN correct_choice_id UUID,
    ADD COLUMN explanation_snapshot TEXT,
    ADD COLUMN part_number_snapshot INTEGER,
    ADD CONSTRAINT fk_attempt_answer_correct_choice_version
        FOREIGN KEY (question_version_id, correct_choice_id)
        REFERENCES question_choices(question_version_id, choice_id),
    ADD CONSTRAINT ck_attempt_answer_part_number_snapshot
        CHECK (part_number_snapshot BETWEEN 1 AND 7);
