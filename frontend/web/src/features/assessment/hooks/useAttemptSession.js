import { useState, useEffect, useCallback, useRef } from 'react';
import { assessmentRepo } from '../repositories/assessment.repo';

export function useAttemptSession(attemptId) {
  const [attempt, setAttempt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [saveStatus, setSaveStatus] = useState('saved');
  const [currentIndex, setCurrentIndex] = useState(0);
  const sequence = useRef(0);
  const pending = useRef(new Map());
  const failed = useRef(new Set());
  const jobs = useRef(new Set());
  const submitting = useRef(false);
  useEffect(() => {
    let active = true;
    setIsLoading(true); setLoadError(''); setAttempt(null);
    pending.current.clear(); failed.current.clear(); sequence.current = 0;
    assessmentRepo.getAttempt(attemptId).then(data => {
      if (active) { sequence.current = data.clientSequence || 0; setAttempt(data); }
    }).catch(error => { if (active) setLoadError(error.message); })
      .finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
  }, [attemptId]);

  const persist = useCallback(payload => {
    pending.current.set(payload.questionId, payload);
    failed.current.delete(payload.questionId);
    setSaveStatus('saving');
    const job = (async () => {
      try {
        await assessmentRepo.saveAnswer(payload);
        if (pending.current.get(payload.questionId)?.clientSeq === payload.clientSeq) pending.current.delete(payload.questionId);
        setSaveStatus(failed.current.size ? 'error' : pending.current.size ? 'saving' : 'saved');
      } catch (error) {
        if (['ATTEMPT_EXPIRED', 'ATTEMPT_CLOSED'].includes(error.code)) {
          if (pending.current.get(payload.questionId)?.clientSeq === payload.clientSeq) pending.current.delete(payload.questionId);
          setSaveStatus(pending.current.size ? 'error' : 'saved');
        } else {
          if (pending.current.get(payload.questionId)?.clientSeq === payload.clientSeq) failed.current.add(payload.questionId);
          setSaveStatus(failed.current.size ? 'error' : pending.current.size ? 'saving' : 'saved');
        }
      }
    })();
    jobs.current.add(job);
    job.finally(() => jobs.current.delete(job));
    return job;
  }, []);
  const selectAnswer = useCallback((questionId, optionKey) => {
    if (!attempt || attempt.status !== 'IN_PROGRESS' || submitting.current) return;
    setAttempt(previous => ({ ...previous, parts: previous.parts.map(part => ({ ...part, questions: part.questions.map(question => question.id === questionId ? { ...question, userAnswer: optionKey } : question) })) }));
    return persist({ attemptId, questionId, selectedOption: optionKey, clientSeq: ++sequence.current });
  }, [attempt, attemptId, persist]);
  const toggleFlag = useCallback(questionId => {
    if (submitting.current) return;
    setAttempt(previous => !previous ? previous : { ...previous, parts: previous.parts.map(part => ({ ...part, questions: part.questions.map(question => question.id === questionId ? { ...question, isFlagged: !question.isFlagged } : question) })) });
  }, []);
  const submitExam = useCallback(async () => {
    if (submitting.current) throw new Error('Bài thi đang được nộp.');
    submitting.current = true; setIsSubmitting(true);
    try {
      await Promise.all([...jobs.current]);
      if (pending.current.size) throw new Error('Còn đáp án chưa lưu. Hãy thử lưu lại trước khi nộp bài.');
      return await assessmentRepo.submitAttempt(attemptId);
    } finally { submitting.current = false; setIsSubmitting(false); }
  }, [attemptId]);
  return { attempt, isLoading, isSubmitting, loadError, saveStatus, currentIndex, setCurrentIndex, selectAnswer, toggleFlag, submitExam,
    retryLastSave: () => Promise.all([...pending.current.values()].map(persist)) };
}
