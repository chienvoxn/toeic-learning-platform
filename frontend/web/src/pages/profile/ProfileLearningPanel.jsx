import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { RotateCcw } from 'lucide-react';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';
import './ProfileLearningPanel.css';

const REQUESTS = [
  ['overview', () => vocabularyRepo.getLearningOverview()],
  ['statistics', () => vocabularyRepo.getLearningStatistics()],
  ['progress', () => vocabularyRepo.getLearningProgress(30)],
  ['streak', () => vocabularyRepo.getLearningStreak(30)],
];

function valueOrDash(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toLocaleString('vi-VN') : '—';
}

function Metric({ label, value, hint }) {
  return <div className="tv3-profile-metric">
    <span>{label}</span>
    <strong>{valueOrDash(value)}</strong>
    {hint && <small>{hint}</small>}
  </div>;
}

function ProgressChart({ progress }) {
  const days = Array.isArray(progress?.dailyProgress) ? progress.dailyProgress : [];
  if (!days.length) return <p className="tv3-profile-muted">Chưa có dữ liệu hoạt động để hiển thị.</p>;
  const max = Math.max(1, ...days.map((day) => Number(day.reviewCount) || 0));
  const total = days.reduce((sum, day) => sum + (Number(day.reviewCount) || 0), 0);
  return <>
    <div className="tv3-profile-chart" role="img" aria-label={`Lượt ôn tập trong ${days.length} ngày gần nhất, tổng cộng ${total} lượt`}>
      {days.map((day) => {
        const count = Number(day.reviewCount) || 0;
        return <div key={day.date} className="tv3-profile-chart-column" title={`${day.date}: ${count} lượt ôn`}>
          <div className="tv3-profile-chart-bar" style={{ height: `${Math.max(count ? 7 : 2, count / max * 100)}%`, opacity: count ? 1 : 0.14 }} />
        </div>;
      })}
    </div>
    <div className="tv3-profile-chart-caption"><span>{days[0]?.date}</span><span>{days.at(-1)?.date}</span></div>
  </>;
}

export default function ProfileLearningPanel({ userId }) {
  const [attempt, setAttempt] = useState(0);
  const [request, setRequest] = useState(null);

  useEffect(() => {
    if (!userId) return undefined;
    let active = true;
    Promise.allSettled(REQUESTS.map(([, load]) => load())).then((results) => {
      if (!active) return;
      const data = {};
      REQUESTS.forEach(([name], index) => {
        const result = results[index];
        data[name] = result.status === 'fulfilled'
          ? { value: result.value, error: null }
          : { value: null, error: result.reason?.status === 404 ? 'API chưa được triển khai trên Backend đang chạy.' : 'Không thể tải dữ liệu từ Backend.' };
      });
      setRequest({ userId, attempt, data });
    });
    return () => { active = false; };
  }, [userId, attempt]);

  const loading = request?.userId !== userId || request?.attempt !== attempt;
  const data = loading ? {} : request.data;
  const overview = data.overview?.value;
  const statistics = data.statistics?.value;
  const progress = data.progress?.value;
  const streak = data.streak?.value;
  const errors = Object.entries(data).filter(([, entry]) => entry.error).map(([name, entry]) => ({ name, error: entry.error }));

  return <section className="profile-lessons surface tv3-profile-learning" aria-labelledby="tv3-learning-title">
    <div className="tv3-profile-heading">
      <div><h2 id="tv3-learning-title">Tiến độ học từ vựng</h2><p>Đồng bộ từ tài khoản đang đăng nhập</p></div>
      <button type="button" onClick={() => setAttempt((value) => value + 1)} aria-label="Tải lại thống kê" title="Tải lại thống kê" className="tv3-profile-reload" disabled={loading}><RotateCcw size={16} /> Tải lại</button>
    </div>
    {loading && <p className="tv3-profile-muted" role="status">Đang tải thống kê học tập...</p>}
    {!loading && errors.length > 0 && <div className="tv3-profile-warning" role="status">
      {errors.map(({ name, error }) => <p key={name}><strong>{({ overview: 'Tổng quan', statistics: 'Đánh giá', progress: 'Lịch học', streak: 'Chuỗi ngày học' })[name]}:</strong> {error}</p>)}
    </div>}
    <div className="tv3-profile-metrics">
      <Metric label="Từ vựng đã ôn" value={overview?.reviewedVocabularies} />
      <Metric label="Tổng lượt ôn" value={overview?.totalReviews} />
      <Metric label="Đến hạn ôn" value={overview?.dueForReview} />
      <Metric label="Đã thành thạo" value={overview?.masteredVocabularies} />
    </div>
    <div className="tv3-profile-streak-panel">
      <div><span>🔥 Chuỗi ngày học hiện tại</span><strong>{valueOrDash(streak?.currentStreak ?? streak?.currentStreakDays)} <small>ngày</small></strong></div>
      <div><span>🏆 Chuỗi dài nhất (30 ngày)</span><strong>{valueOrDash(streak?.longestStreak ?? streak?.longestStreakDays)} <small>ngày</small></strong></div>
    </div>
    <div className="tv3-profile-subsection">
      <h3>Hoạt động 30 ngày gần đây</h3>
      <ProgressChart progress={progress} />
    </div>
    <div className="tv3-profile-subsection">
      <h3>Phân bố đánh giá SRS</h3>
      <div className="tv3-profile-rating-grid">
        <Metric label="Again" value={statistics?.againCount} />
        <Metric label="Hard" value={statistics?.hardCount} />
        <Metric label="Good" value={statistics?.goodCount} />
        <Metric label="Easy" value={statistics?.easyCount} />
      </div>
    </div>
    <div className="tv3-profile-actions"><Link to="/vocabulary" className="pill-button">Tiếp tục học từ vựng</Link></div>
  </section>;
}
