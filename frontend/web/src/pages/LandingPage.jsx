import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Check, ChevronRight, Headphones, Lightbulb, Menu, TrendingUp, X } from 'lucide-react';
import { Brand } from '@/components/atoms/Brand';
import { assetUrl } from '@/lib/figma-assets';
import '../styles/landing.css';

const photos = {
  blue: assetUrl('assets/landing/portrait-blue.jpg'),
  study: assetUrl('assets/landing/study.jpg'),
  outdoor: assetUrl('assets/landing/portrait-outdoor.jpg'),
  warm: assetUrl('assets/landing/portrait-warm.jpg'),
};
const navigation = [
  ['Luyện nghe', '/listening'], ['Shadowing', '/listening?mode=shadowing'],
  ['Từ vựng', '/vocabulary'], ['Luyện nói', '/listening?mode=shadowing&view=exercise'],
  ['Luyện thi TOEIC', '/tests'],
];
const features = [
  {
    id: 'dictation', badge: 'Phương pháp Dictation (Nghe chép) chuyên sâu',
    title: 'Luyện Nghe Sâu Như Người Bản Xứ',
    description: <>Bạn gặp khó khăn khi nghe hiểu các cuộc hội thoại <strong>tiếng Anh</strong>? Phương pháp <strong>Dictation</strong> giúp bạn luyện đôi tai qua từng câu, nhận diện từ vựng và hiểu rõ hơn nội dung mình nghe.</>,
    to: '/listening', visual: 'listening',
  },
  {
    id: 'shadowing', badge: 'Kỹ thuật Shadowing (Nói nhại theo)',
    title: 'Nói Tiếng Anh Tự Nhiên và Trôi Chảy',
    description: <>Nói <strong>tiếng Anh</strong> với ngữ điệu và sự tự tin như người bản xứ! Kỹ thuật <strong>Shadowing</strong> giúp bạn làm chủ <strong>phát âm</strong>, nhịp điệu và sự <strong>lưu loát</strong> qua việc lắng nghe và nói theo từng câu.</>,
    to: '/listening?mode=shadowing', visual: 'speaking',
  },
  {
    id: 'mistakes', badge: 'Học từ những lần luyện tập',
    title: 'Học Từ Lỗi Sai Của Chính Bạn',
    description: <>Sai lầm là cơ hội để tiến bộ. Xem lại kết quả và đáp án sau mỗi bài luyện thi, nhận ra những phần cần ôn tập và <strong>cải thiện từng bước một</strong>.</>,
    to: '/tests', visual: 'mistakes',
  },
  {
    id: 'progress', badge: 'Hồ Sơ Học Tập Cá Nhân',
    title: 'Theo Dõi Trình Độ & Lộ Trình Học Tập',
    description: <>Giữ nhịp học mỗi ngày với hồ sơ cá nhân và mục tiêu của riêng bạn. Theo dõi hoạt động học tập, duy trì thói quen và từng bước chinh phục <strong>mục tiêu tiếng Anh</strong>.</>,
    to: '/profile', visual: 'progress',
  },
];

function Mascot({ className = '' }) {
  return <img className={`lp-mascot ${className}`} src={assetUrl('favicon.svg')} width="46" height="46" alt="" />;
}
function TryLink({ to, children = 'Thử ngay' }) {
  return <Link className="lp-button lp-button-small" to={to}>{children}<ChevronRight size={15} aria-hidden="true" /></Link>;
}
function FeatureVisual({ kind }) {
  return <div className={`lp-visual lp-visual-${kind}`} aria-hidden="true">
    {kind === 'listening' && <div className="lp-listening-scene">
      <div className="lp-listening-video"><img src={photos.warm} alt="" loading="lazy" /><Lightbulb className="lp-bulb" size={42} /></div>
      <div className="lp-listening-bottom"><Mascot /><img src={photos.blue} alt="" loading="lazy" /></div>
    </div>}
    {kind === 'speaking' && <><img className="lp-speaking-photo" src={photos.outdoor} alt="" loading="lazy" /><div className="lp-speech"><Mascot /><span>😊</span></div></>}
    {kind === 'mistakes' && <div className="lp-mistake-sheet"><img src={photos.outdoor} alt="" loading="lazy" /><TrendingUp className="lp-trend" strokeWidth={2.5} /><span className="lp-check"><Check size={32} strokeWidth={3} /></span></div>}
    {kind === 'progress' && <div className="lp-progress-sheet"><p>Hành trình học tập</p><div className="lp-levels">{['Proficient', 'Advanced', 'Upper Intermediate', 'Intermediate', 'Elementary', 'Beginner'].map((level, index) => <span key={level} className={`lp-level-${index}`}>{level}</span>)}</div><TrendingUp className="lp-progress-trend" strokeWidth={1.3} /><img src={photos.blue} alt="" loading="lazy" /><span className="lp-progress-caption">Mỗi ngày một bước tiến</span></div>}
  </div>;
}
function LandingFooter() {
  const [year] = useState(() => new Date().getFullYear());
  return <footer className="lp-footer">
    <div className="lp-container lp-footer-grid">
      <div className="lp-footer-about"><Brand /><p>Học tiếng Anh theo cách của bạn.</p><p>ENGLINGO đồng hành cùng bạn luyện nghe, luyện nói, mở rộng vốn từ và ôn thi TOEIC. Xây dựng thói quen nhỏ hôm nay để tự tin hơn mỗi ngày.</p></div>
      <nav aria-label="Phương pháp học"><h2>Khám phá</h2><a href="#dictation">Nghe chép chính tả</a><a href="#shadowing">Phương pháp Shadowing</a><a href="#mistakes">Học từ lỗi sai</a><a href="#progress">Hành trình học tập</a></nav>
      <nav aria-label="Tài nguyên học tập"><h2>Học cùng ENGLINGO</h2><Link to="/vocabulary">Học từ vựng</Link><Link to="/listening">Luyện nghe tiếng Anh</Link><Link to="/tests">Luyện thi TOEIC</Link><Link to="/games">Trò chơi học tập</Link></nav>
      <div className="lp-footer-start"><h2>Bắt đầu hành trình</h2><p>Một chút luyện tập mỗi ngày, thêm một bước gần mục tiêu.</p><TryLink to="/register">Đăng ký miễn phí</TryLink><Link className="lp-footer-login" to="/login">Đã có tài khoản? Đăng nhập</Link></div>
    </div>
    <div className="lp-container lp-footer-bottom"><p>© {year} ENGLINGO. Nền tảng học tiếng Anh trực tuyến.</p><a href="#top">Về đầu trang ↑</a></div>
  </footer>;
}
export default function LandingPage() {
  const [menuOpen, setMenuOpen] = useState(false);
  return <div className="landing-page" id="top">
    <a className="lp-skip" href="#landing-content">Đến nội dung chính</a>
    <header className="lp-header"><div className="lp-container lp-header-inner"><Brand />
      <nav id="landing-navigation" aria-label="Điều hướng chính" className={`lp-nav ${menuOpen ? 'is-open' : ''}`} onKeyDown={event => { if (event.key === 'Escape') setMenuOpen(false); }}>
        {navigation.map(([label, to]) => <Link key={label} to={to} onClick={() => setMenuOpen(false)}>{label}{to === '/tests' && <span className="lp-new">MỚI</span>}</Link>)}
        <details className="lp-more"><summary>Thêm</summary><div><Link to="/games">Trò chơi học tập</Link><Link to="/catalog">Chủ đề học tập</Link><a href="#about" onClick={() => setMenuOpen(false)}>Về ENGLINGO</a></div></details>
        <Link className="lp-mobile-login" to="/login">Đăng nhập</Link>
      </nav>
      <Link className="lp-login" to="/login">Đăng nhập</Link>
      <button className="lp-menu" type="button" aria-label={menuOpen ? 'Đóng menu' : 'Mở menu'} aria-expanded={menuOpen} aria-controls="landing-navigation" onClick={() => setMenuOpen(open => !open)}>{menuOpen ? <X /> : <Menu />}</button>
    </div></header>
    <main id="landing-content">
      <section className="lp-hero" aria-labelledby="lp-title"><div className="lp-container lp-hero-grid">
        <div className="lp-hero-copy"><h1 id="lp-title">Học tiếng Anh online:<br />luyện nghe, luyện nói, từ vựng và luyện thi</h1><p>Nghe chép chính tả (<strong>Dictation</strong>) và <strong>Shadowing</strong> trên video thực tế, ghép cặp luyện nói cùng cộng đồng, <strong>luyện thi TOEIC</strong>. Khám phá cách học phù hợp với trình độ và mục tiêu của bạn.</p><Link className="lp-button lp-primary-cta" to="/register">Bắt Đầu Miễn Phí</Link><Link className="lp-guest-cta" to="/tests">Thi thử không cần đăng nhập →</Link></div>
        <div className="lp-hero-art" aria-label="Cộng đồng học tiếng Anh đa dạng"><Headphones className="lp-hero-headphones" aria-hidden="true" /><span className="lp-orbit" aria-hidden="true" /><div className="lp-photo-grid"><img src={photos.blue} alt="Người học tiếng Anh" fetchPriority="high" width="600" height="750" /><img src={photos.study} alt="Nhóm bạn cùng học tập" width="600" height="750" /><img src={photos.outdoor} alt="Học tiếng Anh để kết nối với thế giới" width="600" height="750" /><img src={photos.warm} alt="Tự tin giao tiếp mỗi ngày" width="600" height="750" /></div><div className="lp-phone" aria-hidden="true"><span /><Mascot /><i /></div></div>
      </div></section>
      <section className="lp-features lp-container" aria-labelledby="lp-feature-title"><div className="lp-section-heading"><h2 id="lp-feature-title">Trải Nghiệm Học Tiếng Anh Thông Minh</h2><p>Khám phá <strong>phương pháp học tiếng Anh hiệu quả</strong>, xây dựng sự tự tin qua từng buổi luyện tập.</p></div>
        {features.map((feature, index) => <article id={feature.id} key={feature.id} className={`lp-feature-row ${index % 2 ? 'lp-reverse' : ''}`}><div className="lp-feature-copy"><span className="lp-badge">{feature.badge}</span><h3>{feature.title}</h3><p>{feature.description}</p><TryLink to={feature.to} /></div><FeatureVisual kind={feature.visual} /></article>)}
      </section>
    </main>
    <div id="about"><LandingFooter /></div>
  </div>;
}