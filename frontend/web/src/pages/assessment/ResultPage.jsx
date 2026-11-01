import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, BookOpen, Headphones } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assessmentRepo } from '@/features/assessment/repositories/assessment.repo';

export default function ResultPage() {
  const { attemptId } = useParams();
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [review, setReview] = useState(false);
  useEffect(() => { let active = true; assessmentRepo.getResult(attemptId).then(data => { if (active) setResult(data); }).catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, [attemptId]);
  const questions = result?.questions || [];
  const parts = [...new Set(questions.map(q => q.partNumber))];
  const filtered = questions.filter(q => filter === 'ALL' || (filter === 'CORRECT' ? q.isCorrect : !q.isCorrect));
  const reading = questions.filter(q => q.partNumber >= 5);
  const listening = questions.filter(q => q.partNumber < 5);
  return <LearnerTemplate variant="catalog"><div className="result-page"><Link className="breadcrumb-back" to="/tests"><ChevronLeft size={16} />Quay về danh sách</Link><div className="page-heading"><h1>Kết quả bài thi</h1></div>
    {loading && <LoadingSpinner text="Đang tải kết quả..." />}{error && <p className="form-error" role="alert">{error}</p>}{result && <>
      <section className="score-summary surface"><div className="score-circle"><strong>{result.totalScore ?? result.estimatedReadingScore ?? '—'}</strong><span>/{result.totalScore != null ? 990 : 495}</span></div><h2>{result.totalScore != null ? 'Tổng điểm' : 'Điểm Reading ước tính'}</h2><div className="score-sections"><div className="listening-score"><Headphones size={24} /><strong>{result.listeningScore ?? '—'}</strong><span>/495</span><p>Đúng: {listening.filter(q => q.isCorrect).length} / {listening.length}</p></div><div className="reading-score"><BookOpen size={24} /><strong>{result.estimatedReadingScore ?? '—'}</strong><span>/495</span><p>Đúng: {reading.filter(q => q.isCorrect).length} / {reading.length}</p></div></div><p className="score-submitted">Hoàn thành: {new Date(result.submittedAt).toLocaleString('vi-VN')}</p></section>
      <section className="surface result-panel"><h2>KẾT QUẢ THEO PART</h2>{parts.map(part => { const list = questions.filter(q => q.partNumber === part); const correct = list.filter(q => q.isCorrect).length; return <div className="part-result-row" key={part}><strong>Part {part}</strong><div className="thin-progress"><span style={{ width: `${correct/list.length*100}%` }} /></div><b>{correct}/{list.length}</b></div>; })}</section>
      <section className="surface result-panel"><h2>GỢI Ý HỌC THÊM</h2><p>Tiếp tục luyện từ vựng và nghe mỗi ngày. Giữ chuỗi học của bạn nhé!</p><div className="result-recommendations"><Link to="/vocabulary"><BookOpen size={24} /><div><strong>Từ vựng TOEIC</strong><small>Khám phá thư viện từ vựng</small></div><span>›</span></Link><Link to="/listening"><Headphones size={24} /><div><strong>Luyện nghe TOEIC</strong><small>Luyện nghe và nhại theo</small></div><span>›</span></Link></div></section>
      <section className="surface result-panel"><h2>BẢN ĐỒ CÂU HỎI</h2>{parts.map(part => <div className="result-question-map" key={part}><h3>Part {part}</h3><div>{questions.filter(q => q.partNumber === part).map(q => <button type="button" key={q.id} className={!q.userAnswer ? 'skipped' : q.isCorrect ? 'correct' : 'wrong'} aria-label={`Xem đáp án câu ${q.orderIndex}`} onClick={() => { setReview(true); setFilter('ALL'); requestAnimationFrame(() => document.getElementById(`review-${q.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })); }}>{q.orderIndex}</button>)}</div></div>)}<div className="map-legend"><span>🟢 Đúng</span><span>🔴 Sai</span><span>⚪ Bỏ qua</span></div></section>
      <section className="surface result-panel"><h2>XEM LẠI ĐÁP ÁN</h2><div className="result-filters">{[['ALL','Tất cả'],['WRONG','Câu sai'],['CORRECT','Câu đúng']].map(([value,label]) => <button type="button" key={value} className={`pill-button ${filter === value && review ? 'navy' : ''}`} onClick={() => { setFilter(value); setReview(true); }}>{label}</button>)}<button className="text-button" onClick={() => setReview(!review)}>{review ? 'Thu gọn' : 'Mở đáp án'}</button></div>{review && <div className="result-answer-list">{filtered.map(q => <article key={q.id} id={`review-${q.id}`}><div><strong>Câu {q.orderIndex} · Part {q.partNumber}</strong><span className={q.isCorrect ? 'correct-text' : 'wrong-text'}>{q.isCorrect ? '✓ Chính xác' : '✕ Chưa chính xác'}</span></div><p>{q.content}</p><div className="review-answer-comparison"><span>Bạn chọn: <b>{q.userAnswer || 'Bỏ qua'}</b></span><span>Đáp án: <b>{q.correctOption}</b></span></div>{q.explanation && <p className="answer-explanation">{q.explanation}</p>}</article>)}</div>}</section>
      <Link className="result-back-button surface" to="/tests">QUAY VỀ DANH SÁCH</Link>
    </>}
  </div></LearnerTemplate>;
}
