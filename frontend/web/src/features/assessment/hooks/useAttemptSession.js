import { useState, useEffect, useCallback, useRef } from 'react';
import { assessmentRepo } from '../repositories/assessment.repo';

export function useAttemptSession(attemptId) {
  const [attempt, setAttempt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [saveStatus, setSaveStatus] = useState('saved');
  const [currentIndex, setCurrentIndex] = useState(0);
  const clientSeqRef = useRef(0);

  // Load Attempt data
  useEffect(() => {
    let isMounted = true;
    setIsLoading(true);
    assessmentRepo.getAttempt(attemptId)
      .then(data => {
        if (isMounted) {
          setAttempt(data);
        }
      })
      .catch(err => console.error('Error fetching attempt:', err))
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => { isMounted = false; };
  }, [attemptId]);

  // Select Answer & Autosave
  const selectAnswer = useCallback(async (questionId, optionKey) => {
    if (!attempt || attempt.status !== 'IN_PROGRESS') return;

    // Optimistic UI update
    setAttempt(prev => {
      const updatedParts = prev.parts.map(part => ({
        ...part,
        questions: part.questions.map(q =>
          q.id === questionId ? { ...q, userAnswer: optionKey } : q
        ),
      }));
      return { ...prev, parts: updatedParts };
    });

    setSaveStatus('saving');
    clientSeqRef.current += 1;

    try {
      await assessmentRepo.saveAnswer({
        attemptId,
        questionId,
        selectedOption: optionKey,
        clientSeq: clientSeqRef.current,
      });
      setSaveStatus('saved');
    } catch (err) {
      console.error('Autosave failure:', err);
      setSaveStatus('error');
    }
  }, [attempt, attemptId]);

  // Toggle Flag question
  const toggleFlag = useCallback((questionId) => {
    setAttempt(prev => {
      if (!prev) return prev;
      const updatedParts = prev.parts.map(part => ({
        ...part,
        questions: part.questions.map(q =>
          q.id === questionId ? { ...q, isFlagged: !q.isFlagged } : q
        ),
      }));
      return { ...prev, parts: updatedParts };
    });
  }, []);

  // Submit attempt
  const submitExam = useCallback(async () => {
    setIsLoading(true);
    try {
      const result = await assessmentRepo.submitAttempt(attemptId);
      return result;
    } finally {
      setIsLoading(false);
    }
  }, [attemptId]);

  return {
    attempt,
    isLoading,
    saveStatus,
    currentIndex,
    setCurrentIndex,
    selectAnswer,
    toggleFlag,
    submitExam,
    retryLastSave: () => setSaveStatus('saved'),
  };
}
