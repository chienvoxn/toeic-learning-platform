import { useAuth } from '../features/auth/hooks/useAuth';
import HomePage from './HomePage';
import LandingPage from './LandingPage';

export default function HomeEntry() {
  const { user, loading } = useAuth();
  if (loading) return <main className="min-h-[100dvh] grid place-items-center" role="status">Đang tải...</main>;
  return user ? <HomePage /> : <LandingPage />;
}
