import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Layers, ChevronRight } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { apiRequest } from '@/services/apiClient';
import { useAuth } from '@/features/auth/hooks/useAuth';
import './dashboard.css';

export default function ConnectedDashboard() {
  const { user } = useAuth();
  const [results, setResults] = useState(null);
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState(null);
  useEffect(() => {
    const controller = new AbortController();
    Promise.allSettled([
      apiRequest('/learning-profile/overview', { signal: controller.signal }),
      apiRequest('/learning-profile/progress?days=90', { signal: controller.signal }),
      apiRequest('/contents?page=0&size=6', { signal: controller.signal }),
    ]).then(items => { if (!controller.signal.aborted) setResults(items); });
    return () => controller.abort();
  }, [revision]);
  const overview = results?.[0].status === 'fulfilled' ? results[0].value : null;
  const days = results?.[1].status === 'fulfilled' ? results[1].value.dailyProgress : null;
  const contents = results?.[2].status === 'fulfilled' ? results[2].value.content : null;
  const day = days?.find(item => item.date === selected) ?? days?.at(-1);
  return <LearnerTemplate><div className="dashboard-page connected-dashboard">
    <div className="page-heading"><h1>Chào mừng trở lại, {user?.fullName || 'bạn'}!</h1><p>Mỗi ngày một chút, tiến gần hơn tới mục tiêu TOEIC.</p></div>
    {!results && <LoadingSpinner text="Đang tải hoạt động học tập..." />}
    {results?.some(item => item.status === 'rejected') && <div className="dashboard-notice" role="alert">Một số dữ liệu chưa tải được. <button className="pill-button" onClick={() => { setResults(null); setRevision(value => value + 1); }}>Thử lại</button></div>}
    <section className="streak-card surface dashboard-welcome"><div><span className="dashboard-eyebrow">HÀNH TRÌNH CỦA BẠN</span><h2>Sẵn sàng cho một bước tiến mới?</h2><p>{user?.targetScore ? `Mục tiêu của bạn: ${user.targetScore} điểm TOEIC.` : 'Bắt đầu với một bộ từ vựng hoặc khám phá bài học mới.'}</p><div className="streak-actions"><Link className="pill-button navy" to="/vocabulary">Tiếp tục học từ vựng <ChevronRight size={16} /></Link><Link className="pill-button" to="/profile">{user?.targetScore ? 'Hồ sơ của tôi' : 'Đặt mục tiêu TOEIC'}</Link></div></div><FigmaAsset screen="profile-edit" name="imgSvg15" className="dashboard-mascot" /></section>
    <section className="dashboard-metrics" aria-label="Thống kê từ vựng">{[
      ['Từ trong sổ tay', overview?.totalVocabularies, '📘'], ['Đến hạn ôn', overview?.dueForReview, '⏰'], ['Đã thành thạo', overview?.masteredVocabularies, '🌱'], ['Lượt ôn tập', overview?.totalReviews, '✨'],
    ].map(([label, count, icon]) => <div className="surface dashboard-metric" key={label}><span className="metric-icon" aria-hidden="true">{icon}</span><div><span>{label}</span><strong>{count ?? '—'}</strong></div></div>)}</section>
    <section><div className="section-heading"><div><h2>🔮 Ôn tập nhanh</h2><p>Tiếp tục từ chỗ bạn đã dừng lại</p></div></div><div className="quick-review-grid"><Link to="/vocabulary" className="quick-review orange"><span><BookOpen size={24} /></span><div><strong>HỌC TỪ VỰNG</strong><small>{overview ? `${overview.dueForReview} từ đến hạn ôn tập` : 'Mở thư viện từ vựng của bạn'}</small></div><ChevronRight size={18} /></Link><Link to="/catalog" className="quick-review navy"><span><Layers size={24} /></span><div><strong>KHÁM PHÁ HỌC LIỆU</strong><small>Đọc và lưu những bài học yêu thích</small></div><ChevronRight size={18} /></Link></div></section>
    <section><div className="section-heading"><h2>Khám phá bài học TOEIC</h2><Link to="/catalog">Xem tất cả →</Link></div>{contents?.length ? <div className="featured-lessons">{contents.map(item => <Link to={`/catalog/${item.contentId}`} key={item.contentId} className="lesson-card"><div className="lesson-image dashboard-lesson-cover"><BookOpen size={36} /><span className="lesson-level">{item.difficulty || 'TOEIC'}</span></div><h3>{item.title}</h3></Link>)}</div> : results && <div className="surface dashboard-empty">{contents ? 'Chưa có bài học được xuất bản. Bạn vẫn có thể học từ vựng trong thư viện.' : 'Chưa tải được danh sách bài học. Hãy thử lại.'}</div>}</section>
    <section className="activity-card surface"><h2>📅 90 ngày ôn từ vựng gần nhất</h2><p>Chọn một ngày để xem lượt ôn đã lưu vào tài khoản.</p>{day ? <div className="activity-columns"><div className="activity-calendar"><div className="month-labels"><span>{days[0].date}</span><span>{days.at(-1).date}</span></div><div className="calendar-grid">{days.map(item => <button type="button" key={item.date} className={`${item.date === day.date ? 'selected' : ''} ${item.reviewCount > 0 ? 'studied' : ''}`} aria-label={`${item.date}: ${item.reviewCount} lượt ôn`} aria-pressed={item.date === day.date} title={`${item.date}: ${item.reviewCount} lượt ôn`} onClick={() => setSelected(item.date)} />)}</div><div className="calendar-legend"><i />Chưa ôn <i className="studied" />Có ôn tập</div></div><div className="activity-day"><strong>{new Date(`${day.date}T12:00:00`).toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</strong><p>{day.reviewCount > 0 ? `Bạn đã hoàn thành ${day.reviewCount} lượt ôn từ vựng.` : 'Không có lượt ôn từ vựng được ghi nhận.'}</p><Link to="/vocabulary">Học từ vựng →</Link></div><div className="activity-summary"><h3>TỔNG KẾT 90 NGÀY</h3><div>📈 <p>Ngày có ôn tập<strong>{days.filter(item => item.reviewCount > 0).length} ngày</strong></p></div><div>📘 <p>Lượt ôn từ vựng<strong>{days.reduce((sum, item) => sum + item.reviewCount, 0)} lượt</strong></p></div><div>🎯 <p>Mục tiêu TOEIC<strong>{user?.targetScore ?? 'Chưa đặt'}</strong></p></div></div></div> : results && <p className="dashboard-empty">{days ? 'Chưa có dữ liệu ôn tập.' : 'Chưa tải được hoạt động ôn tập. Hãy thử lại.'}</p>}</section>
  </div></LearnerTemplate>;
}
