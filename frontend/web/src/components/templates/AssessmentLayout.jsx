import { Link } from 'react-router-dom';
import { Brand } from '@/components/atoms/Brand';
import { useAuth } from '@/features/auth/hooks/useAuth';
import LearnerTemplate from './LearnerTemplate';

export default function AssessmentLayout({ children, variant = 'catalog' }) {
  const { user } = useAuth();
  if (user) return <LearnerTemplate variant={variant}>{children}</LearnerTemplate>;
  return <div className="min-h-[100dvh] bg-slate-50 text-slate-800">
    <header className="bg-white border-b border-slate-200"><div className="max-w-6xl mx-auto px-5 py-4 flex flex-wrap items-center justify-between gap-4"><Brand /><nav className="flex items-center gap-4 text-sm" aria-label="Điều hướng thi thử"><Link to="/tests">Thi thử miễn phí</Link><Link to="/login">Đăng nhập</Link><Link className="pill-button navy" to="/register">Đăng ký</Link></nav></div></header>
    <main className="learner-content max-w-6xl mx-auto p-5 md:p-8">{children}</main>
    <footer className="max-w-6xl mx-auto px-5 py-6 text-sm text-slate-500"><Link to="/">ENGLINGO · Trang chủ</Link></footer>
  </div>;
}
