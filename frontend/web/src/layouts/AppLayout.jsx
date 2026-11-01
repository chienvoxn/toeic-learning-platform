import { NavLink, Outlet } from 'react-router-dom';
export default function AppLayout() {
  return <div className="app-shell">
    <header className="app-header"><strong>TOEIC Learning Platform</strong><nav aria-label="Điều hướng chính">
      <NavLink to="/">Trang chủ</NavLink><NavLink to="/health">Kiểm tra hệ thống</NavLink><NavLink to="/login">Đăng nhập</NavLink>
    </nav></header>
    <main className="app-main"><Outlet /></main>
    <footer className="app-footer">TOEIC Learning Platform · Foundation</footer>
  </div>;
}
