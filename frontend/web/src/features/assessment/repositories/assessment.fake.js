import { MOCK_TEST_CATALOG, MOCK_PART5_QUESTIONS, MOCK_PART7_PASSAGE } from '../fixtures/toeicExamData';
import { toClientAttempt } from '../api/assessment.mapper';

const delay = (ms = 300) => new Promise(res => setTimeout(res, ms));

export class FakeAssessmentRepository {
  constructor() {
    this.storageKey = 'toeic_mock_attempts';
  }

  async getCatalog() {
    await delay(300);
    return MOCK_TEST_CATALOG;
  }

  async getTestDetail(testId) {
    await delay(250);
    const test = MOCK_TEST_CATALOG.find(t => t.id === testId) || MOCK_TEST_CATALOG[0];
    return {
      ...test,
      instructions: 'Đề thi bao gồm các phần trắc nghiệm chuẩn TOEIC. Hãy chọn câu trả lời đúng nhất cho từng câu hỏi. Hệ thống sẽ tự động lưu đáp án của bạn theo thời gian thực.',
      availableParts: [
        { partNumber: 5, name: 'Part 5: Incomplete Sentences', questionCount: 8, estimatedMinutes: 10 },
        { partNumber: 7, name: 'Part 7: Reading Comprehension', questionCount: 2, estimatedMinutes: 5 },
      ],
    };
  }

  async startAttempt(testId, selectedParts = [5, 7]) {
    await delay(400);
    const test = MOCK_TEST_CATALOG.find(t => t.id === testId) || MOCK_TEST_CATALOG[0];
    const attemptId = 'att_' + Date.now();

    const parts = [];
    if (selectedParts.includes(5)) {
      parts.push({
        part_number: 5,
        title: 'Part 5: Incomplete Sentences',
        questions: MOCK_PART5_QUESTIONS,
      });
    }
    if (selectedParts.includes(7)) {
      parts.push({
        part_number: 7,
        title: 'Part 7: Reading Comprehension',
        passage: MOCK_PART7_PASSAGE.content,
        questions: MOCK_PART7_PASSAGE.questions,
      });
    }

    const durationSeconds = selectedParts.length > 1 ? 1200 : 600;

    const attemptDto = {
      id: attemptId,
      test_id: test.id,
      test_title: test.title,
      mode: 'PRACTICE',
      status: 'IN_PROGRESS',
      expires_at: new Date(Date.now() + durationSeconds * 1000).toISOString(),
      duration_seconds: durationSeconds,
      parts,
    };

    const clientModel = toClientAttempt(attemptDto);
    localStorage.setItem(`${this.storageKey}_${attemptId}`, JSON.stringify(clientModel));
    return clientModel;
  }

  async getAttempt(attemptId) {
    await delay(250);
    const raw = localStorage.getItem(`${this.storageKey}_${attemptId}`);
    if (raw) {
      return JSON.parse(raw);
    }
    // Nếu reload URL mà chưa có sẵn trong local, tự tạo attempt mới
    return this.startAttempt('test_ets_2024_part5_mini');
  }

  async saveAnswer({ attemptId, questionId, selectedOption, clientSeq }) {
    await delay(200);
    const raw = localStorage.getItem(`${this.storageKey}_${attemptId}`);
    if (raw) {
      const attempt = JSON.parse(raw);
      attempt.parts = attempt.parts.map(part => ({
        ...part,
        questions: part.questions.map(q =>
          q.id === questionId ? { ...q, userAnswer: selectedOption } : q
        ),
      }));
      localStorage.setItem(`${this.storageKey}_${attemptId}`, JSON.stringify(attempt));
    }
    return {
      success: true,
      questionId,
      confirmedOption: selectedOption,
      savedAt: new Date().toISOString(),
      serverSequence: clientSeq,
    };
  }

  async submitAttempt(attemptId) {
    await delay(500);
    const raw = localStorage.getItem(`${this.storageKey}_${attemptId}`);
    let attempt = raw ? JSON.parse(raw) : null;

    let correctCount = 0;
    let totalQuestions = 0;
    const questionsBreakdown = [];

    if (attempt) {
      attempt.status = 'GRADED';
      attempt.parts.forEach(part => {
        part.questions.forEach(q => {
          totalQuestions++;
          const isCorrect = q.userAnswer === q.correctOption;
          if (isCorrect) correctCount++;
          questionsBreakdown.push({
            id: q.id,
            orderIndex: q.orderIndex,
            partNumber: q.partNumber,
            content: q.content,
            userAnswer: q.userAnswer,
            correctOption: q.correctOption,
            isCorrect,
            explanation: q.explanation,
          });
        });
      });
      localStorage.setItem(`${this.storageKey}_${attemptId}`, JSON.stringify(attempt));
    }

    const estimatedScore = Math.round((correctCount / (totalQuestions || 1)) * 495);

    const result = {
      attemptId,
      status: 'GRADED',
      submittedAt: new Date().toISOString(),
      rawCorrect: correctCount,
      totalQuestions: totalQuestions || 10,
      accuracyRate: Math.round((correctCount / (totalQuestions || 10)) * 100),
      estimatedReadingScore: estimatedScore,
      questions: questionsBreakdown,
    };

    localStorage.setItem(`toeic_result_${attemptId}`, JSON.stringify(result));
    return result;
  }

  async getResult(attemptId) {
    await delay(250);
    const raw = localStorage.getItem(`toeic_result_${attemptId}`);
    if (raw) return JSON.parse(raw);
    // Nếu chưa có, tự chấm điểm
    return this.submitAttempt(attemptId);
  }
}
