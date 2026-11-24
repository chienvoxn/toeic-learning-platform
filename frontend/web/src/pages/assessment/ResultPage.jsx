import ExamScoreSummary from '@/components/organisms/ExamScoreSummary';
import { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, BookOpen, Headphones } from 'lucide-react';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { GuestPracticeNotice } from '@/components/molecules/GuestPracticeNotice';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { ListeningMediaPlayer } from '@/features/assessment/ui/molecules/ListeningMediaPlayer';
import { attachResultMedia } from '@/features/assessment/api/server-attempt.mapper';
import { assessmentRepo, remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';

export default function ResultPage({ remote = false }) {
  const { attemptId } = useParams();
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [mediaError, setMediaError] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [review, setReview] = useState(false);

  useEffect(() => {
    let active = true;
    const repo = remote ? remoteAssessmentRepo : assessmentRepo;
    repo.getResult(attemptId)
      .then(async data => {
        let enriched = data;
        if (remote && typeof repo.getAttemptMedia === 'function' && data?.questions?.some(q => q.hasMedia || q.partNumber <= 4)) {
          try {
            const mediaDto = await repo.getAttemptMedia(attemptId);
            enriched = attachResultMedia(data, mediaDto);
          } catch {
            // The graded result remains useful without optional media.
            if (active) setMediaError('Không tải được audio, hình hoặc transcript đính kèm.');
          }
        }
        if (active) setResult(enriched);
      })
      .catch(err => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attemptId, remote]);

  const retryMedia = useCallback(async () => {
    if (!remote || typeof remoteAssessmentRepo.getAttemptMedia !== 'function') return;
    setMediaError('');
    try {
      const mediaDto = await remoteAssessmentRepo.getAttemptMedia(attemptId);
      setResult(current => attachResultMedia(current, mediaDto));
    } catch {
      setMediaError('Không tải được audio, hình hoặc transcript đính kèm. Vui lòng thử lại khi kết nối ổn định.');
    }
  }, [attemptId, remote]);

  const questions = result?.questions || [];
  const parts = [...new Set(questions.map(q => q.partNumber))];
  const filtered = questions.filter(q => filter === 'ALL' || (filter === 'CORRECT' ? q.isCorrect : filter === 'UNANSWERED' ? !q.userAnswer : q.userAnswer && !q.isCorrect));

  return (
    <AssessmentLayout variant="catalog">
      <div className="result-page">
        {remote ? <p role="note" className="integration-notice">Kết quả đã nộp do máy chủ chấm trên phiên bản đã lưu.</p> : <GuestPracticeNotice />}
        <Link className="breadcrumb-back" to={remote ? '/practice' : '/tests'}><ChevronLeft size={16} />Quay về danh sách</Link>
        <div className="page-heading"><h1>Kết quả bài thi</h1></div>
        {loading && <LoadingSpinner text="Đang tải kết quả..." />}
        {error && <p className="form-error" role="alert">{error}</p>}
        {mediaError && <p className="integration-notice" role="status">{mediaError} <button type="button" className="text-button" onClick={retryMedia}>Tải lại học liệu</button></p>}
        {result && (
          <>
            <ExamScoreSummary result={result} />
            <section className="surface result-panel">
              <h2>KẾT QUẢ THEO PART</h2>
              {parts.map(part => {
                const list = questions.filter(q => q.partNumber === part);
                const persisted = result.partResults?.find(item => item.partNumber === part);
                const correct = persisted?.correctCount ?? list.filter(q => q.isCorrect).length;
                const total = persisted?.totalQuestions ?? list.length;
                return (
                  <div className="part-result-row" key={part}>
                    <strong>Part {part}</strong>
                    <div className="thin-progress"><span style={{ width: `${(correct / total) * 100}%` }} /></div>
                    <b>{correct}/{total}</b>
                  </div>
                );
              })}
            </section>
            <section className="surface result-panel">
              <h2>GỢI Ý HỌC THÊM</h2>
              <p>Tiếp tục luyện từ vựng và nghe mỗi ngày. Giữ chuỗi học của bạn nhé!</p>
              <div className="result-recommendations">
                <Link to="/vocabulary"><BookOpen size={24} /><div><strong>Từ vựng TOEIC</strong><small>Khám phá thư viện từ vựng</small></div><span>›</span></Link>
                <Link to="/listening"><Headphones size={24} /><div><strong>Luyện nghe TOEIC</strong><small>Luyện nghe và nhại theo</small></div><span>›</span></Link>
              </div>
            </section>
            <section className="surface result-panel">
              <h2>BẢN ĐỒ CÂU HỎI</h2>
              {parts.map(part => (
                <div className="result-question-map" key={part}>
                  <h3>Part {part}</h3>
                  <div>
                    {questions.filter(q => q.partNumber === part).map(q => (
                      <button
                        type="button"
                        key={q.id}
                        className={!q.userAnswer ? 'skipped' : q.isCorrect ? 'correct' : 'wrong'}
                        aria-label={`Xem đáp án câu ${q.orderIndex}`}
                        onClick={() => {
                          setReview(true);
                          setFilter('ALL');
                          requestAnimationFrame(() => document.getElementById(`review-${q.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
                        }}
                      >
                        {q.orderIndex}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
              <div className="map-legend"><span>🟢 Đúng</span><span>🔴 Sai</span><span>⚪ Bỏ qua</span></div>
            </section>
            <section className="surface result-panel">
              <h2>XEM LẠI ĐÁP ÁN</h2>
              <div className="result-filters">
                {[['ALL', 'Tất cả'], ['WRONG', 'Câu sai'], ['UNANSWERED', 'Chưa trả lời'], ['CORRECT', 'Câu đúng']].map(([value, label]) => (
                  <button
                    type="button"
                    key={value}
                    className={`pill-button ${filter === value && review ? 'navy' : ''}`}
                    onClick={() => { setFilter(value); setReview(true); }}
                  >
                    {label}
                  </button>
                ))}
                <button className="text-button" onClick={() => setReview(!review)}>{review ? 'Thu gọn' : 'Mở đáp án'}</button>
              </div>
              {review && (
                <div className="result-answer-list">
                  {filtered.map(q => {
                    const combinedAudio = [...(q.passageAudioAssets || []), ...(q.audioAssets || [])];
                    const combinedImages = [...(q.passageImageAssets || []), ...(q.imageAssets || [])];
                    const combinedTranscript = [q.passageTranscript, q.transcript].filter(Boolean).join('\n');
                    return (
                      <article key={q.id} id={`review-${q.id}`}>
                        <div>
                          <strong>Câu {q.orderIndex} · Part {q.partNumber}</strong>
                          <span className={q.isCorrect ? 'correct-text' : 'wrong-text'}>
                            {q.isCorrect ? '✓ Chính xác' : q.userAnswer ? '✕ Chưa chính xác' : 'Chưa trả lời'}
                          </span>
                        </div>
                        <p>{q.content}</p>
                        {(combinedAudio.length > 0 || combinedImages.length > 0) && (
                          <ListeningMediaPlayer
                            audioAssets={combinedAudio}
                            imageAssets={combinedImages}
                            strictExamMode={false}
                            label={`Media câu ${q.orderIndex}`}
                          />
                        )}
                        <div className="review-answer-comparison">
                          <span>Bạn chọn: <b>{q.userAnswer || 'Bỏ qua'}</b></span>
                          <span>Đáp án: <b>{q.correctOption}</b></span>
                        </div>
                        {combinedTranscript && (
                          <div className="answer-transcript rounded-lg border border-blue-200 bg-blue-50 p-3 my-2 text-sm" style={{ whiteSpace: 'pre-line' }}>
                            <strong className="block text-blue-900 mb-1">Transcript (Bản chép lời):</strong>
                            <p className="text-slate-800">{combinedTranscript}</p>
                          </div>
                        )}
                        {q.explanation && <p className="answer-explanation">{q.explanation}</p>}
                      </article>
                    );
                  })}
                </div>
              )}
            </section>
            <Link className="result-back-button surface" to={remote ? '/practice' : '/tests'}>QUAY VỀ DANH SÁCH</Link>
          </>
        )}
      </div>
    </AssessmentLayout>
  );
}
