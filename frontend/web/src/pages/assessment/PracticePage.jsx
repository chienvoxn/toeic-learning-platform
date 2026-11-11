import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ExamScoreSummary from '@/components/organisms/ExamScoreSummary';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { practiceApi } from '@/features/assessment/repositories/practice.api';

export default function PracticePage() {
  const { attemptId } = useParams();
  const navigate = useNavigate();
  const [session, setSession] = useState(null);
  const [result, setResult] = useState(null);
  const [parts, setParts] = useState([5]);
  const [mode, setMode] = useState('practice');
  const [duration, setDuration] = useState(30);
  const [filter, setFilter] = useState('ALL');
  const [count, setCount] = useState(10);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [now, setNow] = useState(Date.now);
  const [resume, setResume] = useState('');
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    let active = true;
    setSession(null); setResult(null); setError('');
    if (!attemptId) return;
    setLoading(true);
    practiceApi.get(attemptId).then(async data => {
      const graded = data.status === 'GRADED' ? await practiceApi.result(attemptId) : null;
      if (active) { setSession(data); setResult(graded); }
    }).catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attemptId]);
  async function run(action) {
    if (busy) return;
    setBusy(true); setError('');
    try { await action(); } catch (err) { setError(`${err.message}${err.code ? ` (${err.code})` : ''}`); }
    finally { setBusy(false); }
  }
  const expired = session && (session.expired || Date.parse(session.expiresAt) <= now);
  const isSection = session?.sourceType === 'SECTION_SNAPSHOT';
  const canSubmit = session?.status === 'IN_PROGRESS' && !result && (isSection || !expired);
  const secondsLeft = session ? Math.max(0, Math.ceil((Date.parse(session.expiresAt) - now) / 1000)) : 0;
  const editable = session?.status === 'IN_PROGRESS' && !expired && !result;
  const answers = new Map((session?.answers || []).map(answer => [answer.questionVersionId, answer.selectedChoiceId]));
  const results = new Map((result?.questions || []).map(question => [question.questionVersionId, question]));
  async function save(question, choiceId) {
    await run(async () => {
      const saved = await practiceApi.save(attemptId, question.questionVersionId, choiceId);
      setSession(previous => ({...previous, answers: [...previous.answers.filter(item => item.questionVersionId !== saved.questionVersionId), saved]}));
    });
  }
  return <LearnerTemplate><div className="max-w-4xl mx-auto space-y-5">
    <Link to="/tests" className="pill-button">Danh sách luyện thi</Link>
    <div className="page-heading"><h1>Luyện tập Reading · Part 5–7</h1><p>Câu hỏi, đáp án và kết quả được lưu vào tài khoản trên máy chủ.</p></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {busy && <p role="status">Đang lưu, vui lòng chờ...</p>}
    {loading && <p role="status">Đang tải bài luyện tập...</p>}
    {!attemptId && <><form className="surface p-6 space-y-4" onSubmit={event => { event.preventDefault(); run(async () => { const data = mode === 'section' ? await practiceApi.startSection(Number(count), Number(duration)) : await practiceApi.start(Number(count), parts); navigate(`/practice/${data.attemptId}`); }); }}>
      <fieldset disabled={busy} className="flex flex-wrap gap-4"><legend className="font-bold mb-3">Chế độ</legend><label><input type="radio" name="practice-mode" checked={mode==='practice'} onChange={() => setMode('practice')} /> Luyện theo Part</label><label><input type="radio" name="practice-mode" checked={mode==='section'} onChange={() => { setMode('section'); setCount(previous => Math.min(100, Number(previous) || 10)); }} /> Thi Section Reading</label></fieldset>
      {mode === 'practice' && <fieldset disabled={busy} className="flex flex-wrap gap-4"><legend className="font-bold mb-3">Chọn Part Reading</legend>{[5,6,7].map(part => <label key={part} className="pill-button"><input type="checkbox" checked={parts.includes(part)} onChange={event => setParts(previous => event.target.checked ? [...previous, part].sort() : previous.filter(item => item !== part))} /> Part {part}</label>)}</fieldset>}
      <label className="block">Số câu (1–{mode==='section'?100:200})<input type="number" className="border p-3 rounded-xl block mt-2" required min="1" max={mode==='section'?100:200} step="1" value={count} disabled={busy} onChange={event => setCount(event.target.value)} /></label>
      {mode==='section' && <label className="block">Thời gian (1–120 phút)<input className="border p-3 rounded-xl block mt-2" type="number" required min="1" max="120" step="1" disabled={busy} value={duration} onChange={event => setDuration(event.target.value)} /></label>}
      <p className="text-sm text-slate-500">{mode==='section' ? 'Section gồm đủ Part 5, 6 và 7, mỗi Part ít nhất một câu; giữ nguyên nhóm. Hết giờ sẽ khóa đáp án; bạn vẫn có thể nộp để chấm các lựa chọn đã lưu. Không tự nộp khi đóng trang. Đây là cấu hình luyện tập, không phải thời lượng TOEIC chính thức.' : 'Cần đủ câu đã xuất bản cho các Part đã chọn. Part 6–7 lấy nguyên nhóm. Phiên luyện Part có hạn 24 giờ; hết hạn không thể lưu hoặc nộp.'}</p>
      <button className="pill-button navy" disabled={busy || (mode==='practice' && !parts.length)}>Bắt đầu luyện tập</button>
    </form><form className="surface p-6 space-y-3" onSubmit={event => { event.preventDefault(); navigate(`/practice/${resume.trim()}`); }}><label>Tiếp tục bài bằng mã phiên<input className="border p-3 rounded-xl block w-full mt-2" required pattern="[0-9a-fA-F-]{36}" value={resume} onChange={event => setResume(event.target.value)} /></label><button className="pill-button">Mở phiên</button></form></>}
    {session && <>
      <section className="surface p-5 space-y-3"><p>Mã phiên: <span className="break-all">{session.attemptId}</span></p><p>Đã lưu {session.answers.filter(item => item.selectedChoiceId).length}/{session.totalQuestions} câu · Hạn nộp: {new Date(session.expiresAt).toLocaleString('vi-VN')}</p><p className="text-sm">Lưu đường dẫn trang này để tiếp tục bài. Chỉ lựa chọn được máy chủ xác nhận mới hiển thị là đã chọn.</p>
        {isSection && <><h2 className="font-bold">Section Reading · {session.section?.questionCount} câu · {session.section?.durationMinutes} phút</h2>{!result && <p role="timer" aria-label="Thời gian còn lại" className="text-2xl font-bold">{String(Math.floor(secondsLeft/60)).padStart(2,'0')}:{String(secondsLeft%60).padStart(2,'0')}</p>}</>}
        {expired && !result && <p role="alert">{isSection ? 'Đã hết giờ. Đáp án đã khóa; hãy nộp bài để xem kết quả các lựa chọn đã lưu.' : 'Phiên đã hết hạn. Không thể lưu hoặc nộp bài này.'}</p>}
        {!result && <button className="pill-button navy" disabled={busy || !canSubmit} onClick={() => { if(window.confirm('Nộp bài và chấm điểm? Sau khi nộp không thể sửa đáp án.')) run(async () => { setResult(await practiceApi.submit(attemptId)); }); }}>Nộp bài</button>}
      </section>
      {result && <><h2 className="text-xl font-bold">Kết quả luyện tập</h2><ExamScoreSummary result={{...result, rawCorrect:result.correctCount, accuracyRate:result.accuracy}} />
        <section className="surface result-panel"><h2>KẾT QUẢ THEO PART</h2>{[...new Set(result.questions.map(item => item.partNumber))].map(part => {
          const items = result.questions.filter(item => item.partNumber === part); const correct = items.filter(item => item.isCorrect).length;
          return <div className="part-result-row" key={part}><strong>Part {part}</strong><div className="thin-progress"><span style={{width:`${correct/items.length*100}%`}} /></div><b>{correct}/{items.length}</b></div>;
        })}<p>Đúng: {result.correctCount} · Sai: {result.incorrectCount} · Bỏ trống: {result.unansweredCount}</p><Link className="pill-button" to="/practice">Luyện bài mới</Link></section>
        <section className="surface result-panel"><h2>BẢN ĐỒ CÂU HỎI</h2><div className="result-question-map"><div>{result.questions.map(item => <button type="button" key={item.questionVersionId} className={!item.selectedChoiceId ? 'skipped' : item.isCorrect ? 'correct' : 'wrong'} onClick={() => { setFilter('ALL'); requestAnimationFrame(() => document.getElementById(`practice-${item.questionVersionId}`)?.scrollIntoView({behavior:'smooth',block:'center'})); }}>{item.order}</button>)}</div></div></section>
        <div className="result-filters">{[['ALL','Tất cả'],['WRONG','Sai / bỏ trống'],['CORRECT','Đúng']].map(([value,label]) => <button className={`pill-button ${filter===value?'navy':''}`} key={value} onClick={() => setFilter(value)}>{label}</button>)}</div>
      </>}
      {session.questions.map(question => {
        const outcome = results.get(question.questionVersionId);
        if(result && filter !== 'ALL' && (filter === 'CORRECT' ? !outcome?.isCorrect : outcome?.isCorrect)) return null;
        const answer = answers.get(question.questionVersionId);
        const graded = results.get(question.questionVersionId);
        const label = id => question.choices.find(choice => choice.choiceId === id)?.label ?? 'Bỏ trống';
        return <article key={question.questionVersionId} id={`practice-${question.questionVersionId}`} className="question-panel surface"><h2 className="font-bold">Câu {question.order} · Part {question.partNumber}</h2>{question.passage && <section className="exam-passage surface mb-4"><h3 className="font-bold">Đoạn văn · Nhóm câu hỏi</h3>{question.passage.instruction && <p className="whitespace-pre-wrap">{question.passage.instruction}</p>}<div className="whitespace-pre-wrap leading-8">{question.passage.passageText}</div>{question.passage.assets?.length > 0 && <p className="text-sm text-slate-500">Đoạn văn có media chưa hỗ trợ hiển thị.</p>}</section>}<p>{question.questionText}</p><div className="answer-options" role="group" aria-label={`Đáp án câu ${question.order}`}>{question.choices.map(choice => <button className={`pill-button ${answer === choice.choiceId ? 'navy' : ''}`} key={choice.choiceId} type="button" aria-pressed={answer === choice.choiceId} disabled={busy || !editable} onClick={() => save(question, choice.choiceId)}>{choice.label}. {choice.content}</button>)}</div>{answer && editable && <button className="text-button" disabled={busy} onClick={() => save(question,null)}>Bỏ chọn</button>}{graded && <div className="mt-4 border-t pt-3"><p>{graded.isCorrect ? '✓ Đúng' : graded.selectedChoiceId ? '✕ Sai' : 'Bỏ trống'} · Bạn chọn: {label(graded.selectedChoiceId)} · Đáp án: {label(graded.correctChoiceId)}</p><p className="whitespace-pre-wrap">{graded.explanation || 'Chưa có giải thích cho câu này.'}</p></div>}</article>;
      })}
    </>}
  </div></LearnerTemplate>;
}
