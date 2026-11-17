// Runtime contract: PRAC-01/02 and EXAM-01. Never infer a key or a server deadline.
export function toServerAttempt(dto) {
  const expiresAt = Date.parse(dto.expiresAt);
  if (!dto.attemptId || !Number.isFinite(expiresAt) || !Array.isArray(dto.questions)) throw new Error('Phản hồi phiên làm bài không hợp lệ.');
  const answers = new Map((dto.answers || []).map(answer => [answer.questionVersionId, answer.selectedChoiceId]));
  const parts = new Map();
  for (const item of dto.questions) {
    const options = item.choices.map(choice => ({ id: choice.choiceId, key: choice.label, text: choice.content }));
    const selected = answers.get(item.questionVersionId);
    const question = { id: item.questionVersionId, questionId: item.questionId, orderIndex: item.order,
      partNumber: item.partNumber, content: item.questionText, options,
      passage: item.passage ? { id: item.passage.groupVersionId, instruction: item.passage.instruction, text: item.passage.passageText } : null,
      userAnswer: options.find(option => option.id === selected)?.key ?? null, isFlagged: false,
      hasMedia: !!item.assets?.length || !!item.passage?.assets?.length };
    if (!parts.has(item.partNumber)) parts.set(item.partNumber, { partNumber: item.partNumber, questions: [] });
    parts.get(item.partNumber).questions.push(question);
  }
  return { id: dto.attemptId, sourceType: dto.sourceType, status: dto.status, expiresAt,
    startedAt: dto.startedAt, expired: dto.expired, totalQuestions: dto.totalQuestions,
    section: dto.section, testTitle: dto.sourceType === 'SECTION_SNAPSHOT' ? `Section ${dto.section?.type || ''}` : 'Luyện tập theo Part',
    parts: [...parts.values()], remote: true };
}

export function toServerResult(dto, session) {
  if (dto.status !== 'GRADED' || dto.attemptId !== session.id) throw new Error('Kết quả chưa sẵn sàng hoặc không thuộc phiên này.');
  const pinned = new Map(session.parts.flatMap(part => part.questions).map(question => [question.id, question]));
  const questions = dto.questions.map(row => {
    const prompt = pinned.get(row.questionVersionId);
    if (!prompt) throw new Error('Không tìm thấy câu hỏi trong phiên đã lưu.');
    return { ...prompt, userAnswer: prompt.options.find(option => option.id === row.selectedChoiceId)?.key ?? null,
      correctOption: prompt.options.find(option => option.id === row.correctChoiceId)?.key ?? null,
      isCorrect: row.isCorrect, outcome: row.outcome, explanation: row.explanation };
  });
  return { id: dto.attemptId, status: dto.status, submittedAt: dto.submittedAt, totalQuestions: dto.totalQuestions,
    rawCorrect: dto.correctCount, rawScore: dto.rawScore, accuracyRate: dto.accuracy,
    incorrectCount: dto.incorrectCount, unansweredCount: dto.unansweredCount,
    partResults: dto.parts, section: dto.section, questions, remote: true };
}
