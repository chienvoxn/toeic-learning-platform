import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';

export default function RequireTeacher() {
  const { user } = useAuth();

  const roles = Array.isArray(user?.roles) ? user.roles : [];

  const allowed = roles.some(
    role => ['TEACHER', 'ADMIN'].includes(
      typeof role === 'string' ? role : role?.name
    )
  );

  if (!allowed) {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}
