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
import { remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';

export default function AttemptPage({ remote = false }) {
  const { attemptId } = useParams();
  const navigate = useNavigate();
  const { attempt, isLoading, isSubmitting, loadError, saveStatus, saveError, currentIndex, setCurrentIndex, selectAnswer, toggleFlag, submitExam, retryLastSave } = useAttemptSession(attemptId, remote ? remoteAssessmentRepo : undefined);
  const [error, setError] = useState('');
  const [timedOutId, setTimedOutId] = useState(null);
  const attemptBase = remote ? '/practice/attempts' : '/attempts';
  const catalogPath = remote ? '/practice' : '/tests';
  useEffect(() => {
    if (attempt?.status === 'GRADED') navigate(`${attemptBase}/${attemptId}/result`, { replace: true });
  }, [attempt?.status, attemptId, navigate, attemptBase]);
  if (isLoading) return <div className="exam-loading"><LoadingSpinner text="Đang tải dữ liệu bài thi..." /></div>;
  if (!attempt) return <div className="exam-loading"><p role="alert">{loadError || 'Không tìm thấy phiên làm bài thi.'}</p><Button onClick={() => navigate(catalogPath)}>Quay lại danh mục đề</Button></div>;
  const questions = attempt.parts.flatMap(p => p.questions);
  const answered = questions.filter(q => q.userAnswer).length;
  const expired = attempt.expired || timedOutId === attemptId;
  const submitDisabled = isSubmitting || (remote && expired && attempt.sourceType !== 'SECTION_SNAPSHOT');
  async function submit(byTimer = false) {
    if (isSubmitting) return;
    if (!byTimer && !window.confirm(`Bạn còn ${questions.length - answered} câu chưa trả lời. Bạn có chắc chắn muốn nộp bài?`)) return;
    try { const result = await submitExam(); navigate(`${attemptBase}/${attemptId}/result`, { state: { result } }); }
    catch (err) { setError(err.message || 'Không thể nộp bài. Vui lòng thử lại.'); }
  }
  function goTo(index) {
    setCurrentIndex(index);
    document.getElementById(`question-${questions[index].id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  return <ExamTemplate header={<div className="exam-toolbar"><div className="exam-title"><Brand compact variant="flat" /><div><h1>{attempt.testTitle}</h1><small>Part {questions[currentIndex]?.partNumber} · {answered}/{questions.length} câu</small><SaveStatusBadge status={saveStatus} onRetry={retryLastSave} /></div></div><CountdownTimer expiresAt={attempt.expiresAt} onExpire={remote ? () => setTimedOutId(attemptId) : () => submit(true)} /><div className="exam-toolbar-actions"><span className="exam-speed">ϟ 1×</span><Button className="submit-exam-button" disabled={submitDisabled} onClick={() => submit()}><Check size={15} />Nộp bài</Button><Button className="exit-exam-button" variant="outline" onClick={() => { if (window.confirm(saveStatus !== 'saved' ? 'Còn đáp án chưa lưu. Rời trang có thể mất những lựa chọn này. Bạn vẫn muốn thoát?' : 'Rời phòng thi? Thời gian thi vẫn tiếp tục chạy.')) navigate(catalogPath); }}><LogOut size={15} />Thoát</Button></div></div>}
    content={<>{remote ? <p role="note" className="integration-notice">Phiên tài khoản · Đáp án lưu trên máy chủ · Cờ xem lại chỉ có trên màn hình này.</p> : <GuestPracticeNotice />}{expired && remote && <p role="status" className="integration-notice">Đã hết thời gian, không thể đổi đáp án. {attempt.sourceType === 'SECTION_SNAPSHOT' ? 'Bạn vẫn có thể nộp các đáp án đã lưu. Chưa có tự chốt bài.' : 'Phiên Practice hết hạn không nhận nộp mới.'}</p>}{(error || saveError) && <p className="form-error" role="alert">{error || saveError}</p>}{saveStatus === 'error' && <p>Các lựa chọn chưa lưu không được dùng để chấm. <button className="text-button" onClick={() => window.location.reload()}>Tải lại đáp án máy chủ</button></p>}{attempt.parts.map(part => <section key={part.partNumber} className="exam-part">{part.passage && <div className="surface exam-passage">{part.passage}</div>}{part.questions.map((question,index) => <div key={question.id}>{remote && question.passage && (index === 0 || part.questions[index-1].passage?.id !== question.passage.id) && <div className="surface exam-passage" style={{ whiteSpace: 'pre-line' }}><strong>{question.passage.instruction}</strong><p>{question.passage.text}</p></div>}<QuestionPanel question={question} disabled={isSubmitting || attempt.status !== 'IN_PROGRESS' || (remote && expired)} onSelectOption={key => { setCurrentIndex(questions.findIndex(q => q.id === question.id)); selectAnswer(question.id, key); }} onToggleFlag={toggleFlag} /></div>)}</section>)}</>}
    sidebar={<><AnswerNavigator questions={questions} currentIndex={currentIndex} onSelectQuestion={goTo} /><Button className="submit-exam-button navigator-submit" disabled={submitDisabled} onClick={() => submit()}>NỘP BÀI</Button><p className="navigator-help">Bấm số câu để di chuyển.<br />Đánh dấu câu hỏi để xem lại sau.</p></>}
  />;
}
