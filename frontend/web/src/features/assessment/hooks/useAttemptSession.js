import { useState, useEffect, useCallback, useRef } from 'react';
import { assessmentRepo } from '../repositories/assessment.repo';
import { AnswerSaveQueue } from '../answer-save-queue';

export function useAttemptSession(attemptId, repository = assessmentRepo) {
  const [attempt, setAttempt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [saveStatus, setSaveStatus] = useState('saved');
  const [saveError, setSaveError] = useState('');
  const [currentIndex, setCurrentIndex] = useState(0);
  const sequence = useRef(0);
  const queue = useRef(null);
  const submitting = useRef(false);
  useEffect(() => {
    let active = true;
    setIsLoading(true); setLoadError(''); setAttempt(null);
    sequence.current = 0;
    const saves = new AnswerSaveQueue(payload => repository.saveAnswer(payload), state => {
      if (active) { setSaveStatus(state.status); setSaveError(state.error?.message || ''); }
    });
    queue.current = saves; setSaveStatus('saved'); setSaveError('');
    const leaving = event => { if (saves.pending.size) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', leaving);
    repository.getAttempt(attemptId).then(data => {
      if (active) { sequence.current = data.clientSequence || 0; setAttempt(data); }
    }).catch(error => { if (active) setLoadError(error.message); })
      .finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; window.removeEventListener('beforeunload', leaving); };
  }, [attemptId, repository]);

  const persist = useCallback(payload => {
    return queue.current?.enqueue(payload);
  }, []);
  const selectAnswer = useCallback((questionId, optionKey) => {
    if (!attempt || attempt.status !== 'IN_PROGRESS' || submitting.current) return;
    if (attempt.remote && (attempt.expired || Date.now() >= attempt.expiresAt)) return;
    const question = attempt.parts.flatMap(part => part.questions).find(item => item.id === questionId);
    if (!question) return;
    setAttempt(previous => ({ ...previous, parts: previous.parts.map(part => ({ ...part, questions: part.questions.map(question => question.id === questionId ? { ...question, userAnswer: optionKey } : question) })) }));
    return persist({ attemptId, questionId, selectedOption: optionKey,
      selectedChoiceId: optionKey === null ? null : question.options.find(option => option.key === optionKey)?.id,
      clientSeq: ++sequence.current });
  }, [attempt, attemptId, persist]);
  const toggleFlag = useCallback(questionId => {
    if (submitting.current) return;
    setAttempt(previous => !previous ? previous : { ...previous, parts: previous.parts.map(part => ({ ...part, questions: part.questions.map(question => question.id === questionId ? { ...question, isFlagged: !question.isFlagged } : question) })) });
  }, []);
  const submitExam = useCallback(async () => {
    if (submitting.current) throw new Error('Bài thi đang được nộp.');
    submitting.current = true; setIsSubmitting(true);
    try {
      await queue.current?.flush();
      return await repository.submitAttempt(attemptId);
    } finally { submitting.current = false; setIsSubmitting(false); }
  }, [attemptId, repository]);
  return { attempt, isLoading, isSubmitting, loadError, saveStatus, saveError, currentIndex, setCurrentIndex, selectAnswer, toggleFlag, submitExam,
    retryLastSave: () => queue.current?.retry() };
}
