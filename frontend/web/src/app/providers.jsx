import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { LearnerLayoutContext } from './layout-context';

export function AppProviders({ children }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try { return localStorage.getItem('englingo.sidebarCollapsed') === 'true'; }
    catch { return false; }
  });
  const toggleSidebar = useCallback(() => setSidebarCollapsed(value => !value), []);

  useEffect(() => {
    try { localStorage.setItem('englingo.sidebarCollapsed', String(sidebarCollapsed)); }
    catch { /* Navigation still works when browser storage is unavailable. */ }
  }, [sidebarCollapsed]);

  const layout = useMemo(() => ({ sidebarCollapsed, toggleSidebar }), [sidebarCollapsed, toggleSidebar]);
  return (
    <AuthProvider>
      <LearnerLayoutContext.Provider value={layout}>
        {children}
      </LearnerLayoutContext.Provider>
    </AuthProvider>
  );
}
