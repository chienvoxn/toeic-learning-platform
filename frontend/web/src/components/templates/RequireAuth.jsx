import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';

export default function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <LoadingSpinner text="Đang kiểm tra phiên đăng nhập..." />;
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
}
