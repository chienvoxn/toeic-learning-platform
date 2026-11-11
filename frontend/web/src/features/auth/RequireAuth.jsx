import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import { authErrorMessage } from './errors';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';

export default function RequireAuth() {
  const { user, loading, sessionError, restore } = useAuth();
  const location = useLocation();
  if (loading) return <LoadingSpinner text="Đang khôi phục phiên..." />;
  if (sessionError) return <main className="auth-recovery"><p role="alert">{authErrorMessage(sessionError)}</p>
    <button type="button" className="pill-button" onClick={restore}>Thử lại</button></main>;
  return user ? <Outlet /> : <Navigate to="/login" state={{ from: location.pathname + location.search, sessionEnded: true }} replace />;
}
