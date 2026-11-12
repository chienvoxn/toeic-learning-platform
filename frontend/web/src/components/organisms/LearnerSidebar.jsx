import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ChevronLeft, X } from 'lucide-react';
import { Brand } from '@/components/atoms/Brand';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { useAuth } from '@/features/auth/hooks/useAuth';

const items = [
  ['Trang chủ', '/', 'imgSvg1'], ['Chủ đề', '/catalog', 'imgSvg2'],
  ['Luyện nghe', '/listening', 'imgSvg3'], ['Shadowing', '/listening?mode=shadowing', 'imgSvg4'],
  ['Từ vựng', '/vocabulary', 'imgSvg5'], ['Luyện nói', '/listening?mode=shadowing&view=exercise', 'imgSvg6'],
  ['Luyện thi TOEIC', '/practice', 'imgSvg7'], ['Trò Chơi', '/games', 'imgSvg8'], ['Ôn tập', '/vocabulary?view=review', 'imgSvg9'],
];

export function LearnerSidebar({ sidebarRef, compact = false, open, onClose, onCollapse }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [notice, setNotice] = useState('');
  const navItem = ([label, to, icon]) => {
    const content = <><FigmaAsset name={icon} /><span>{label}</span></>;
    if (!to) return <button key={label} type="button" className="sidebar-link" title={label} onClick={() => setNotice(`${label} chưa có trong phiên bản hiện tại.`)}>{content}</button>;
    const target = new URL(to, 'https://local.test');
    const query = new URLSearchParams(location.search);
    let active = target.pathname === location.pathname && (!target.search ? !location.search : target.search === location.search);
    if (label === 'Từ vựng') active = location.pathname.startsWith('/vocabulary') && !query.has('view');
    if (label === 'Luyện thi TOEIC') active = location.pathname.startsWith('/practice') || location.pathname.startsWith('/tests');
    if (label === 'Trò Chơi') active = location.pathname.startsWith('/games');
    if (label === 'Shadowing') active = location.pathname === '/listening' && query.get('mode') === 'shadowing';
    if (label === 'Luyện nói') active = false;
    return <Link key={label} to={to} title={label} onClick={onClose} aria-current={active ? 'page' : undefined} className={`sidebar-link ${active ? 'active' : ''}`}>{content}</Link>;
  };
  return <>
    {open && <button className="sidebar-backdrop" onClick={onClose} aria-label="Đóng menu" />}
    <aside ref={sidebarRef} id="learner-sidebar" aria-label="Menu học tập" className={`learner-sidebar ${compact ? 'compact' : ''} ${open ? 'is-open' : ''}`}>
      <div className="sidebar-brand"><Brand /><button type="button" className="icon-button mobile-menu" onClick={onClose} aria-label="Đóng menu"><X size={18} /></button></div>
      <div className="sidebar-scroll">
        <nav aria-label="Điều hướng chính" className="sidebar-nav">{items.map(navItem)}</nav>
        <div className="sidebar-personal">
          {user?.roles?.includes('ADMIN') && navItem(['Quản lý người dùng', '/admin/users', 'imgSvg11'])}
          {navItem(['Ghi chú của tôi', null, 'imgSvg11'])}
          {navItem(['Từ vựng của tôi', '/vocabulary?view=personal', 'imgSvg5'])}
          <button type="button" className="sidebar-link logout-link" title={user ? 'Đăng xuất' : 'Đăng nhập'} onClick={async () => { const result = user ? await logout() : null; navigate('/login', { state: { logoutFailed: result?.revocationFailed } }); }}><FigmaAsset name="imgSvg12" /><span>{user ? 'Đăng xuất' : 'Đăng nhập'}</span></button>
        </div>
        {notice && <div className="sidebar-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Đóng thông báo">×</button></div>}
      </div>
      <div className="sidebar-bottom"><Link to="/profile" className="sidebar-user" onClick={onClose}><FigmaAsset name="imgAvatar" alt="Ảnh đại diện" /><div><strong>{user?.fullName || 'Học viên'}</strong><small>Hồ sơ cá nhân</small></div></Link><button type="button" className="collapse-button" onClick={onCollapse} aria-label={compact ? 'Mở rộng menu' : 'Thu gọn menu'}><ChevronLeft size={13} /><span>Thu gọn</span></button></div>
    </aside>
  </>;
}
