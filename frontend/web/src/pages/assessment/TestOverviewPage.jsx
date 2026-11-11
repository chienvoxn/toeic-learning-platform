import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Headphones, BookOpen, ChevronLeft } from 'lucide-react';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { GuestPracticeNotice } from '@/components/molecules/GuestPracticeNotice';
import { TestMetadata } from '@/components/molecules/TestMetadata';
import { Button } from '@/components/ui/button';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assessmentRepo } from '@/features/assessment/repositories/assessment.repo';

export default function TestOverviewPage() {
  const { testId } = useParams();
  const navigate = useNavigate();
  const [test, setTest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState([]);
  useEffect(() => {
    let active = true;
    assessmentRepo.getTestDetail(testId).then(data => { if (active) { setTest(data); setSelected(data.availableParts.map(p => p.partNumber)); } }).catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [testId]);
  async function start() {
    setStarting(true); setError('');
    try { const attempt = await assessmentRepo.startAttempt(testId, selected); navigate(`/attempts/${attempt.id}`); }
    catch (err) { setError(err.message || 'Không thể bắt đầu bài thi.'); setStarting(false); }
  }
  const chosen = test?.availableParts.filter(p => selected.includes(p.partNumber)) || [];
  return <AssessmentLayout variant="catalog"><div className="test-overview-page"><GuestPracticeNotice />
    <Link className="breadcrumb-back" to="/tests"><ChevronLeft size={16} />Bộ đề thi</Link>
    {loading ? <LoadingSpinner text="Đang chuẩn bị đề thi..." /> : test && <>
      <div className="page-heading"><h1>{test.title}</h1><p>Đề thi thử {test.title}</p></div><TestMetadata test={test} />
      <div className="test-overview-grid"><section className="surface parts-table"><table><thead><tr><th>SECTION</th><th>PART</th><th>TYPE</th><th>QUESTIONS</th></tr></thead><tbody>{test.availableParts.map(part => <tr key={part.partNumber}><td><span className={`section-badge ${part.partNumber < 5 ? 'listening' : 'reading'}`}>{part.partNumber < 5 ? <Headphones size={14} /> : <BookOpen size={14} />}{part.partNumber < 5 ? 'LISTENING' : 'READING'}</span></td><td>Part {part.partNumber}</td><td>{part.name.replace(/^Part \d+:\s*/, '')}</td><td>{part.questionCount}</td></tr>)}</tbody></table></section>
      <div className="exam-options"><section className="surface mode-card"><h2>CHẾ ĐỘ LÀM BÀI</h2><div className="exam-modes"><div className="selected"><strong>Luyện thi</strong><p>Giống thi thật — không xem đáp án khi đang làm</p></div><div aria-disabled="true" title="Chế độ xem đáp án từng câu chưa được hỗ trợ"><strong>Luyện tập</strong><p>Xem đáp án từng câu, làm lại thoải mái</p></div></div></section>
        <section className="surface part-selection"><div className="selection-heading"><h2>CHỌN PHẦN MUỐN LÀM</h2><div><button onClick={() => setSelected(test.availableParts.map(p => p.partNumber))}>Chọn tất cả</button><span>|</span><button onClick={() => setSelected([])}>Bỏ chọn</button></div></div>{['LISTENING', 'READING'].map(section => { const parts = test.availableParts.filter(p => (p.partNumber < 5) === (section === 'LISTENING')); return parts.length > 0 && <fieldset key={section}><legend>{section}</legend>{parts.map(part => <label key={part.partNumber}><input type="checkbox" checked={selected.includes(part.partNumber)} onChange={() => setSelected(prev => prev.includes(part.partNumber) ? prev.filter(n => n !== part.partNumber) : [...prev, part.partNumber])} />P{part.partNumber} — {part.name.replace(/^Part \d+:\s*/, '')} ({part.questionCount})</label>)}</fieldset>; })}<div className="selection-total">{selected.length} parts • {chosen.reduce((n,p) => n+p.questionCount,0)} câu • ~{chosen.reduce((n,p) => n+p.estimatedMinutes,0)} phút</div></section>
        <p className="exam-instructions">Bài thi sẽ tự động nộp khi hết giờ<br />Rời trang không dừng đồng hồ; mở lại cùng URL trên trình duyệt này để tiếp tục</p><Button className="start-exam-button" disabled={starting || !selected.length} onClick={start}>{starting ? 'ĐANG CHUẨN BỊ...' : 'BẮT ĐẦU LÀM BÀI'}</Button>
      </div></div>
    </>}{error && <p className="form-error" role="alert">{error}</p>}
  </div></AssessmentLayout>;
}
