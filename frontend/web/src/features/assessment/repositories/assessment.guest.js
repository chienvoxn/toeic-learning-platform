import { MOCK_PART5_QUESTIONS, MOCK_PART7_PASSAGE } from '../fixtures/toeicExamData';
import { toClientAttempt } from '../api/assessment.mapper';

const testId = 'guest-reading-sample';
const availableParts = [
  { partNumber: 5, name: 'Part 5: Incomplete Sentences', questionCount: MOCK_PART5_QUESTIONS.length, estimatedMinutes: 10 },
  { partNumber: 7, name: 'Part 7: Reading Comprehension', questionCount: MOCK_PART7_PASSAGE.questions.length, estimatedMinutes: 5 },
];
const catalog = [{ id: testId, title: 'Bài thi thử Reading mẫu · Part 5 & 7', totalQuestions: availableParts.reduce((sum, part) => sum + part.questionCount, 0), durationMinutes: 15, parts: [5, 7], difficulty: 'PRACTICE' }];
function failure(message, code = 'INVALID_ATTEMPT') { return Object.assign(new Error(message), { code }); }

// Thi thử công khai trên thiết bị. Không gọi API tài khoản, không tạo điểm server.
export class GuestAssessmentRepository {
  constructor({ storage, now = () => Date.now(), createId = () => crypto.randomUUID() } = {}) {
    this.storage = storage;
    this.now = now;
    this.createId = createId;
    this.storageKey = 'toeic_guest_attempt_v1_';
  }
  async getCatalog() { return structuredClone(catalog); }
  async getTestDetail(id) {
    if (id !== testId) throw failure('Không tìm thấy đề thi thử.');
    return { ...structuredClone(catalog[0]), availableParts: structuredClone(availableParts) };
  }
  read(id) {
    let raw;
    try { raw = (this.storage || localStorage).getItem(this.storageKey + id); }
    catch { throw failure('Trình duyệt không cho phép đọc dữ liệu bài thi.'); }
    if (!raw) throw failure('Không tìm thấy bài thi trên trình duyệt này. Hãy chọn đề và bắt đầu bài mới.');
    try {
      const attempt = JSON.parse(raw);
      if (attempt.id !== id || !Array.isArray(attempt.parts) || !Number.isFinite(attempt.expiresAt)) throw new Error();
      return attempt;
    } catch { throw failure('Dữ liệu bài thi trên thiết bị không hợp lệ. Vui lòng bắt đầu bài mới.'); }
  }
  write(attempt) {
    try { (this.storage || localStorage).setItem(this.storageKey + attempt.id, JSON.stringify(attempt)); }
    catch { throw failure('Không lưu được bài thi. Kiểm tra quyền lưu trữ hoặc dung lượng trình duyệt.'); }
  }
  view(attempt) {
    const result = structuredClone(attempt);
    delete result.result;
    if (result.status === 'IN_PROGRESS') result.parts.forEach(part => part.questions.forEach(question => { delete question.correctOption; delete question.explanation; }));
    return result;
  }
  async startAttempt(id, selectedParts = [5, 7]) {
    await this.getTestDetail(id);
    if (!Array.isArray(selectedParts) || !selectedParts.length || new Set(selectedParts).size !== selectedParts.length || selectedParts.some(part => ![5, 7].includes(part))) throw failure('Hãy chọn ít nhất một phần hợp lệ.');
    const parts = [];
    if (selectedParts.includes(5)) parts.push({ part_number: 5, title: availableParts[0].name, questions: structuredClone(MOCK_PART5_QUESTIONS) });
    if (selectedParts.includes(7)) parts.push({ part_number: 7, title: availableParts[1].name, passage: MOCK_PART7_PASSAGE.content, questions: structuredClone(MOCK_PART7_PASSAGE.questions) });
    let order = 0;
    parts.forEach(part => part.questions.forEach(question => { question.order_index = ++order; }));
    const durationSeconds = availableParts.filter(part => selectedParts.includes(part.partNumber)).reduce((sum, part) => sum + part.estimatedMinutes * 60, 0);
    const attempt = toClientAttempt({ id: `guest_${this.createId()}`, test_id: id, test_title: catalog[0].title, duration_seconds: durationSeconds, expires_at: new Date(this.now() + durationSeconds * 1000).toISOString(), parts });
    attempt.totalQuestions = order;
    attempt.answerSequences = {};
    attempt.clientSequence = 0;
    this.write(attempt);
    return this.view(attempt);
  }
  async getAttempt(id) {
    const attempt = this.read(id);
    if (attempt.status === 'IN_PROGRESS' && this.now() >= attempt.expiresAt) this.grade(attempt);
    return this.view(attempt);
  }
  async saveAnswer({ attemptId, questionId, selectedOption, clientSeq }) {
    const attempt = this.read(attemptId);
    if (attempt.status !== 'IN_PROGRESS') throw failure('Bài đã nộp, không thể sửa đáp án.', 'ATTEMPT_CLOSED');
    if (this.now() >= attempt.expiresAt) { this.grade(attempt); throw failure('Bài thi đã hết giờ.', 'ATTEMPT_EXPIRED'); }
    const question = attempt.parts.flatMap(part => part.questions).find(item => item.id === questionId);
    if (!question || !question.options.some(option => option.key === selectedOption) || !Number.isSafeInteger(clientSeq) || clientSeq < 1) throw failure('Đáp án không hợp lệ.');
    if (clientSeq > (attempt.answerSequences[questionId] || 0)) {
      question.userAnswer = selectedOption;
      attempt.answerSequences[questionId] = clientSeq;
      attempt.clientSequence = Math.max(attempt.clientSequence, clientSeq);
      this.write(attempt);
    }
    return { confirmedOption: question.userAnswer, serverSequence: attempt.answerSequences[questionId] };
  }
  grade(attempt) {
    if (attempt.result) return attempt.result;
    const questions = attempt.parts.flatMap(part => part.questions).map(question => ({ ...question, isCorrect: question.userAnswer === question.correctOption }));
    const rawCorrect = questions.filter(question => question.isCorrect).length;
    const result = { attemptId: attempt.id, status: 'GRADED', submittedAt: new Date(this.now()).toISOString(), rawCorrect, totalQuestions: questions.length, accuracyRate: Math.round(rawCorrect / questions.length * 100), estimatedReadingScore: null, questions };
    attempt.status = 'GRADED'; attempt.result = result;
    this.write(attempt);
    return result;
  }
  async submitAttempt(id) { return this.grade(this.read(id)); }
  async getResult(id) {
    const attempt = this.read(id);
    if (!attempt.result) throw failure('Bài thi chưa được nộp. Hãy quay lại bài làm.', 'ATTEMPT_NOT_SUBMITTED');
    return structuredClone(attempt.result);
  }
}
