import { useEffect, useRef, useState } from 'react';
import { AppHeader } from '@/components/organisms/AppHeader';
import { LearnerSidebar } from '@/components/organisms/LearnerSidebar';
import { MobileBottomNav } from '@/components/organisms/MobileBottomNav';
import { useLearnerLayout } from '@/hooks/useLearnerLayout';
import { useLocation } from 'react-router-dom';
import { demoModules, isRemoteAuth } from '@/features/auth/config';

export default function LearnerTemplate({ children, className = '', variant = 'dashboard' }) {
  const { pathname } = useLocation();
  const demoPage = !['/profile', '/settings'].includes(pathname);
  const [menuOpen, setMenuOpen] = useState(false);
  const { sidebarCollapsed, toggleSidebar } = useLearnerLayout();
  const sidebarRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    sidebarRef.current?.querySelector('[aria-label="Đóng menu"]')?.focus();
    const onKeyDown = event => {
      if (event.key === 'Escape') setMenuOpen(false);
      if (event.key !== 'Tab') return;
      const focusable = [...sidebarRef.current.querySelectorAll('a[href], button:not(:disabled), input, select, textarea, [tabindex="0"]')]
        .filter(element => element.getClientRects().length);
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const breakpoint = window.matchMedia('(max-width: 900px)');
    const onResize = () => { if (!breakpoint.matches) setMenuOpen(false); };
    document.addEventListener('keydown', onKeyDown);
    breakpoint.addEventListener('change', onResize);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
      breakpoint.removeEventListener('change', onResize);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [menuOpen]);

  return <div className={`learning-shell shell-${variant} ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
    <div className="learning-body">
      <LearnerSidebar sidebarRef={sidebarRef} compact={sidebarCollapsed} open={menuOpen} onClose={() => setMenuOpen(false)} onCollapse={toggleSidebar} />
      <div className="learning-main">
        <AppHeader menuOpen={menuOpen} onMenu={() => setMenuOpen(true)} />
        <main id="main-content" className={`learner-content ${className}`}>
          {!isRemoteAuth && <p className="integration-notice" role="status">Auth đang chạy DEMO, không xác thực backend.</p>}
          {demoModules && demoPage && <p className="integration-notice" role="status">DEMO — học liệu, bài thi, từ vựng và hoạt động học chưa tích hợp backend; dữ liệu minh họa không phải tiến độ tài khoản.</p>}
          {children}</main>
      </div>
    </div>
    <MobileBottomNav />
  </div>;
}
