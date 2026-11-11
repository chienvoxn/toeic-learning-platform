import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { apiRequest } from '@/services/apiClient';
import { useAuth } from '@/features/auth/hooks/useAuth';

export default function ConnectedDashboard() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      apiRequest('/learning-profile/overview', { signal: controller.signal }),
      apiRequest('/learning-profile/progress?days=7', { signal: controller.signal }),
    ]).then(([overview, progress]) => setData({ overview, progress }))
      .catch(err => { if (err.name !== 'AbortError') setError(err.message); });
    return () => controller.abort();
  }, []);
  return <LearnerTemplate><div className="max-w-4xl mx-auto space-y-6">
    <div className="page-heading"><h1>Chào {user?.fullName || 'bạn'}!</h1><p>Tiếp tục hành trình học tiếng Anh của bạn.</p></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {!data && !error && <LoadingSpinner text="Đang tải tiến độ học tập..." />}
    {data && <>
      <section className="grid grid-cols-2 md:grid-cols-4 gap-4">{[
        ['Từ vựng', data.overview.totalVocabularies], ['Đến hạn ôn', data.overview.dueForReview],
        ['Đã thành thạo', data.overview.masteredVocabularies], ['Lượt ôn tập', data.overview.totalReviews],
      ].map(([label, count]) => <div className="surface p-5" key={label}><p className="text-sm text-slate-500">{label}</p><strong className="text-3xl">{count}</strong></div>)}</section>
      <section className="surface p-6"><h2 className="text-lg font-bold mb-4">Ôn tập trong 7 ngày gần nhất</h2><div className="grid grid-cols-7 gap-2">{data.progress.dailyProgress.map(day => <div key={day.date} className="text-center bg-slate-50 p-2 rounded-lg"><p className="text-xs">{new Date(`${day.date}T12:00:00`).toLocaleDateString('vi-VN', { day: 'numeric', month: 'numeric' })}</p><strong>{day.reviewCount}</strong></div>)}</div></section>
    </>}
    <div className="flex flex-wrap gap-3"><Link className="pill-button navy" to="/vocabulary">Học và ôn từ vựng</Link><Link className="pill-button" to="/catalog">Khám phá học liệu</Link><Link className="pill-button" to="/profile">Hồ sơ của tôi</Link></div>
  </div></LearnerTemplate>;
}
