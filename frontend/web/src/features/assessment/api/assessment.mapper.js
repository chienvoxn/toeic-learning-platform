export function toClientAttempt(dto) {
  if (!dto) return null;
  return {
    id: dto.id,
    testId: dto.test_id || dto.testId,
    testTitle: dto.test_title || dto.title || 'TOEIC Practice Test',
    mode: dto.mode || 'PRACTICE',
    status: dto.status || 'IN_PROGRESS',
    expiresAt: dto.expires_at ? new Date(dto.expires_at).getTime() : Date.now() + 1200 * 1000,
    durationSeconds: dto.duration_seconds || 1200,
    totalQuestions: dto.total_questions || (dto.questions ? dto.questions.length : 0),
    parts: (dto.parts || []).map(p => ({
      partNumber: p.part_number,
      title: p.title,
      passage: p.passage || null,
      questions: (p.questions || []).map(q => ({
        id: q.id,
        orderIndex: q.order_index,
        partNumber: q.part_number,
        content: q.content,
        passage: q.passage || null,
        audioUrl: q.audio_url || null,
        imageUrl: q.image_url || null,
        options: (q.options || []).map(opt => ({
          key: opt.key,
          text: opt.text,
        })),
        userAnswer: q.user_answer || null,
        isFlagged: Boolean(q.is_flagged),
        correctOption: q.correct_option || null,
        explanation: q.explanation || null,
      })),
    })),
  };
}

export function toSaveAnswerDto(attemptId, questionId, selectedOption, clientSeq) {
  return {
    attempt_id: attemptId,
    question_id: questionId,
    selected_option: selectedOption,
    client_sequence: clientSeq,
    client_timestamp: new Date().toISOString(),
  };
}
