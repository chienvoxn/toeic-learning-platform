import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogOut, Check } from 'lucide-react';
import ExamTemplate from '@/components/templates/ExamTemplate';
import { Brand } from '@/components/atoms/Brand';
import { useAttemptSession } from '@/features/assessment/hooks/useAttemptSession';
import { QuestionPanel } from '@/features/assessment/ui/organisms/QuestionPanel';
import { AnswerNavigator } from '@/features/assessment/ui/organisms/AnswerNavigator';
import { CountdownTimer } from '@/components/molecules/CountdownTimer';
import { SaveStatusBadge } from '@/components/molecules/SaveStatusBadge';
import { Button } from '@/components/ui/button';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { GuestPracticeNotice } from '@/components/molecules/GuestPracticeNotice';

export default function AttemptPage() {
  const { attemptId } = useParams();
  const navigate = useNavigate();
  const { attempt, isLoading, isSubmitting, loadError, saveStatus, currentIndex, setCurrentIndex, selectAnswer, toggleFlag, submitExam, retryLastSave } = useAttemptSession(attemptId);
  const [error, setError] = useState('');
  useEffect(() => {
    if (attempt?.status === 'GRADED') navigate(`/attempts/${attemptId}/result`, { replace: true });
  }, [attempt?.status, attemptId, navigate]);
  if (isLoading) return <div className="exam-loading"><LoadingSpinner text="Đang tải dữ liệu bài thi..." /></div>;
  if (!attempt) return <div className="exam-loading"><p role="alert">{loadError || 'Không tìm thấy phiên làm bài thi.'}</p><Button onClick={() => navigate('/tests')}>Quay lại danh mục đề</Button></div>;
  const questions = attempt.parts.flatMap(p => p.questions);
  const answered = questions.filter(q => q.userAnswer).length;
  async function submit(expired = false) {
    if (isSubmitting) return;
    if (!expired && !window.confirm(`Bạn còn ${questions.length - answered} câu chưa trả lời. Bạn có chắc chắn muốn nộp bài?`)) return;
    try { const result = await submitExam(); navigate(`/attempts/${attemptId}/result`, { state: { result } }); }
    catch (err) { setError(err.message || 'Không thể nộp bài. Vui lòng thử lại.'); }
  }
  function goTo(index) {
    setCurrentIndex(index);
    document.getElementById(`question-${questions[index].id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  return <ExamTemplate header={<div className="exam-toolbar"><div className="exam-title"><Brand compact variant="flat" /><div><h1>{attempt.testTitle}</h1><small>Part {questions[currentIndex]?.partNumber} · {answered}/{questions.length} câu</small><SaveStatusBadge status={saveStatus} onRetry={retryLastSave} /></div></div><CountdownTimer expiresAt={attempt.expiresAt} onExpire={() => submit(true)} /><div className="exam-toolbar-actions"><span className="exam-speed">ϟ 1×</span><Button className="submit-exam-button" disabled={isSubmitting} onClick={() => submit()}><Check size={15} />Nộp bài</Button><Button className="exit-exam-button" variant="outline" onClick={() => { if (window.confirm('Rời phòng thi? Thời gian thi vẫn tiếp tục chạy.')) navigate('/tests'); }}><LogOut size={15} />Thoát</Button></div></div>}
    content={<><GuestPracticeNotice />{error && <p className="form-error" role="alert">{error}</p>}{attempt.parts.map(part => <section key={part.partNumber} className="exam-part">{part.passage && <div className="surface exam-passage">{part.passage}</div>}{part.questions.map(question => <QuestionPanel key={question.id} question={question} disabled={isSubmitting || attempt.status !== 'IN_PROGRESS'} onSelectOption={key => { setCurrentIndex(questions.findIndex(q => q.id === question.id)); selectAnswer(question.id, key); }} onToggleFlag={toggleFlag} />)}</section>)}</>}
    sidebar={<><AnswerNavigator questions={questions} currentIndex={currentIndex} onSelectQuestion={goTo} /><Button className="submit-exam-button navigator-submit" disabled={isSubmitting} onClick={() => submit()}>NỘP BÀI</Button><p className="navigator-help">Bấm số câu để di chuyển.<br />Đánh dấu câu hỏi để xem lại sau.</p></>}
  />;
}
