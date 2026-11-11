import { Navigate, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { useMock } from '@/lib/runtime-config';

export default function RequireExamAuth({ children }) {
  const { attemptId } = useParams();
  const { user, loading } = useAuth();
  const location = useLocation();
  if (useMock || attemptId?.startsWith('guest_')) return children;
  if (loading) return <p>Đang kiểm tra phiên đăng nhập…</p>;
  return user ? children : <Navigate to="/login" replace state={{ from: location.pathname }} />;
}
