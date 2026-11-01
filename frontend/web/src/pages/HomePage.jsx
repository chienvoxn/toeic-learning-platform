import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Headphones, ChevronRight } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { useAuth } from '@/features/auth/hooks/useAuth';

const lessons = [
  ['imgLesson', 'Evacuate residents to safety | Today', 'B2', 'YouTube'],
  ['imgChocolateFudge', '2 INGREDIENT DOUBLE CHOCOLATE FUDGE', 'B1', 'YouTube'],
  ['imgBird', 'Bird Migration - Texas Parks and Wildlife...', 'B1', 'YouTube'],
  ['imgKidJumpRope', 'Có phải là dây nhảy không? | Bài hát học tập', 'A1', 'YouTube'],
  ['imgLanguageTips', '5 mẹo học ngoại ngữ nhanh hơn', 'B1', 'Audio'],
  ['imgWinterFinland', 'Freezing cold? This town in Finland is keeping...', 'B2', 'YouTube'],
];

export default function HomePage() {
  const { user } = useAuth();
  const [days] = useState(() => Array.from({ length: 90 }, (_, i) => { const d = new Date(); d.setDate(d.getDate() - 89 + i); return d; }));
  const today = days[89].toLocaleDateString('sv-SE');
  const [checkedIn, setCheckedIn] = useState(() => localStorage.getItem('daily_checkin') === today);
  const [selectedDay, setSelectedDay] = useState(89);
  return <LearnerTemplate>
    <div className="dashboard-page">
      <div className="page-heading"><h1>Chào mừng trở lại</h1><p>Giữ vững chuỗi học và nhận phần thưởng</p></div>
      <section className="streak-card surface">
        <div className="streak-overview"><div className="streak-count"><span className="flame-tile">🔥</span><div><small>Streak hiện tại</small><strong>{user?.streakDays ?? 0} ngày</strong></div></div><div className="level-tile">1</div><div className="level-progress"><div><strong>✨ Lên level 2</strong><span>0 XP</span></div><div className="thin-progress"><span /></div><small>Còn 138 XP đến level tiếp</small></div></div>
        <div className="streak-actions"><button type="button" className="pill-button navy" disabled={checkedIn} onClick={() => { localStorage.setItem('daily_checkin', today); setCheckedIn(true); }}>🔥 {checkedIn ? 'ĐÃ ĐIỂM DANH' : 'ĐIỂM DANH HÀNG NGÀY'}</button><Link className="pill-button amber" to="/tests">🎯 NHIỆM VỤ · 0/3</Link></div>
      </section>
      <section><div className="section-heading"><div><h2>🔮 Ôn tập nhanh</h2><p>Tiếp tục từ chỗ bạn đã dừng lại</p></div></div><div className="quick-review-grid"><Link to="/vocabulary" className="quick-review orange"><span><BookOpen size={24} /></span><div><strong>HỌC TỪ VỰNG</strong><small>Bạn đã ôn xong hết</small></div><ChevronRight size={18} /></Link><Link to="/listening" className="quick-review navy"><span><Headphones size={24} /></span><div><strong>NGHE / PHÁT ÂM</strong><small>Bạn đã ôn xong hết</small></div><ChevronRight size={18} /></Link></div></section>
      <section><div className="section-heading"><h2>Bài học nổi bật hôm nay</h2></div><div className="featured-lessons">{lessons.map(([image, title, level, source], i) => <Link to={`/listening?view=exercise&lesson=${i}`} key={image} className="lesson-card"><div className="lesson-image"><FigmaAsset name={image} alt={title} /><span className="lesson-level">{level}</span><span className={`lesson-source ${source === 'Audio' ? 'audio' : ''}`}>▶ {source}</span></div><h3>{title}</h3></Link>)}</div></section>
      <section className="activity-card surface"><h2>📅 90 ngày gần nhất</h2><p>Bấm vào một ngày để xem chi tiết. Ô càng đậm = học càng nhiều.</p><div className="activity-columns"><div className="activity-calendar"><div className="month-labels">{[0, 30, 60, 89].map(i => <span key={i}>Tháng {days[i].getMonth() + 1}</span>)}</div><div className="calendar-grid">{days.map((d, i) => <button type="button" key={i} className={`${selectedDay === i ? 'selected' : ''} ${i === 89 && checkedIn ? 'studied' : ''}`} aria-label={d.toLocaleDateString('vi-VN')} aria-pressed={selectedDay === i} title={d.toLocaleDateString('vi-VN')} onClick={() => setSelectedDay(i)} />)}</div><div className="calendar-legend">Ít <i /><i /><i /><i className="studied" /> Nhiều <span>□ Đã điểm danh</span></div></div><div className="activity-day"><strong>{days[selectedDay].toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</strong>{selectedDay === 89 && <span>Hôm nay</span>}<p>{selectedDay === 89 && checkedIn ? 'Bạn đã điểm danh hôm nay.' : 'Không có hoạt động nào ngày này.'}</p></div><div className="activity-summary"><h3>TỔNG KẾT 90 NGÀY</h3><div>📈 <p>Ngày có học<strong>{checkedIn ? 1 : 0} / 90</strong></p></div><div>◷ <p>Thời gian học<strong>0p</strong></p></div><div>📊 <p>Chuỗi dài nhất<strong>{user?.streakDays ?? 0} ngày</strong></p></div></div></div></section>
    </div>
  </LearnerTemplate>;
}
