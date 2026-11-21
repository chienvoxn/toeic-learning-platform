CREATE TABLE toeic_quizzes(quiz_id UUID PRIMARY KEY,title VARCHAR(255) NOT NULL,part INTEGER NOT NULL CHECK(part BETWEEN 1 AND 7),premium BOOLEAN NOT NULL DEFAULT FALSE,published BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE toeic_questions(question_id UUID PRIMARY KEY,quiz_id UUID NOT NULL REFERENCES toeic_quizzes(quiz_id),prompt TEXT NOT NULL,option_a VARCHAR(255) NOT NULL,option_b VARCHAR(255) NOT NULL,option_c VARCHAR(255) NOT NULL,option_d VARCHAR(255) NOT NULL,correct_choice VARCHAR(255) NOT NULL CHECK(correct_choice IN ('A','B','C','D')),explanation TEXT NOT NULL);
CREATE INDEX ix_quiz_questions ON toeic_questions(quiz_id);
INSERT INTO toeic_quizzes VALUES
('11111111-1111-1111-1111-111111111111','TOEIC Part 5 - Grammar Basics',5,FALSE,TRUE),
('22222222-2222-2222-2222-222222222222','TOEIC Part 6 - Reading Practice',6,TRUE,TRUE);
INSERT INTO toeic_questions VALUES
('11111111-aaaa-aaaa-aaaa-111111111111','11111111-1111-1111-1111-111111111111','The manager ____ the report before noon.','submit','submits','submitting','submitted','B','Third-person singular present simple.'),
('11111111-bbbb-bbbb-bbbb-111111111111','11111111-1111-1111-1111-111111111111','Please send the invoice ____ email.','on','by','at','for','B','By email describes the means of sending.'),
('11111111-cccc-cccc-cccc-111111111111','11111111-1111-1111-1111-111111111111','The workshop has been ____ until Friday.','postpone','postpones','postponed','postponing','C','Present perfect passive: has been + past participle.'),
('22222222-aaaa-aaaa-aaaa-222222222222','22222222-2222-2222-2222-222222222222','All visitors must ____ at reception.','register','registered','registration','registers','A','The modal must takes base verb.'),
('22222222-bbbb-bbbb-bbbb-222222222222','22222222-2222-2222-2222-222222222222','The policy is effective ____ December 1.','in','on','at','to','B','Use on for a specific date.');
