import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Menu, Bell, Search, Flame } from 'lucide-react';
import { Brand } from '@/components/atoms/Brand';
import { isRemoteAuth } from '@/features/auth/config';

export function AppHeader({ menuOpen = false, onMenu }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [notifications, setNotifications] = useState(false);
  return <header className="app-topbar">
    <div className="topbar-left">
      <button type="button" className="icon-button mobile-menu" onClick={onMenu} aria-label="Mở menu" aria-controls="learner-sidebar" aria-expanded={menuOpen}><Menu size={21} /></button>
      <Brand compact className="topbar-brand" />
      <form className="dictionary-search" onSubmit={e => { e.preventDefault(); navigate(`/vocabulary?q=${encodeURIComponent(query)}`); }} role="search">
        <Search size={15} /><input aria-label="Tra từ điển" placeholder="Tra từ điển" value={query} onChange={e => setQuery(e.target.value)} />
      </form>
    </div>
    <div className="topbar-actions">
      <div className="notification-wrap"><button type="button" className="icon-button" aria-label="Thông báo" aria-expanded={notifications} onClick={() => setNotifications(!notifications)}><Bell size={19} /></button>
        {notifications && <div className="notification-popover" role="status">Bạn chưa có thông báo mới.</div>}
      </div>
      {!isRemoteAuth && <Link className="daily-pill" to="/">🎯 0/3</Link>}
      <Link className="streak-pill" to="/profile" aria-label="Chuỗi ngày học"><Flame size={16} /></Link>
    </div>
  </header>;
}
