import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { AppProviders } from './providers';
import HomePage from '../pages/HomePage';
import HomeEntry from '../pages/HomeEntry';
import HealthPage from '../pages/HealthPage';
import LoginPage from '../pages/auth/LoginPage';
import RegisterPage from '../pages/auth/RegisterPage';
import ProfilePage from '../pages/profile/ProfilePage';
import SettingsPage from '../pages/profile/SettingsPage';
import TestCatalogPage from '../pages/assessment/TestCatalogPage';
import TestOverviewPage from '../pages/assessment/TestOverviewPage';
import AttemptPage from '../pages/assessment/AttemptPage';
import ResultPage from '../pages/assessment/ResultPage';
import VocabDeckListPage from '../pages/vocabulary/VocabDeckListPage';
import VocabDeckDetailPage from '../pages/vocabulary/VocabDeckDetailPage';
import VocabReviewPage from '../pages/vocabulary/VocabReviewPage';
import ContentCatalogPage from '../pages/content/ContentCatalogPage';
import GamesCatalogPage from '../pages/games/GamesCatalogPage';
import WordMatchGamePage from '../pages/games/WordMatchGamePage';
import OnboardingPage from '../pages/onboarding/OnboardingPage';
import ListeningHubPage from '../pages/listening/ListeningHubPage';
import NotFoundPage from '../pages/NotFoundPage';
import ForgotPasswordPage from '../pages/auth/ForgotPasswordPage';
import RequireAuth from '../features/auth/RequireAuth';
import UnavailablePage from '../pages/UnavailablePage';
import { useMock } from '../lib/runtime-config';
import ContentDetailPage from '../pages/content/ContentDetailPage';
import PracticeSetupPage from '../pages/assessment/PracticeSetupPage';
import AdminUsersPage from '../pages/admin/AdminUsersPage';
import AdminRolesPage from '../pages/admin/AdminRolesPage';
import AdminAuditPage from '../pages/admin/AdminAuditPage';
import AdminOverviewPage from '../pages/admin/AdminOverviewPage';

function RootLayout() {
  return (
    <AppProviders>
      <Outlet />
    </AppProviders>
  );
}

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { path: '/', element: <HomeEntry /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/verify-email', element: <Navigate to="/login" replace /> },
      { path: '/health', element: <HealthPage /> },
      // Thi thử công khai, không nằm trong RequireAuth.
      { path: '/tests', element: <TestCatalogPage /> },
      { path: '/tests/:testId/overview', element: <TestOverviewPage /> },
      { path: '/attempts/:attemptId', element: <AttemptPage /> },
      { path: '/attempts/:attemptId/result', element: <ResultPage /> },
      { element: <RequireAuth />, children: [
      { path: '/admin/users', element: <AdminUsersPage /> },
      { path: '/admin/roles', element: <AdminRolesPage /> },
      { path: '/admin/audit', element: <AdminAuditPage /> },
      { path: '/admin/overview', element: <AdminOverviewPage /> },
      { path: '/practice', element: <PracticeSetupPage /> },
      { path: '/practice/attempts/:attemptId', element: <AttemptPage remote /> },
      { path: '/practice/attempts/:attemptId/result', element: <ResultPage remote /> },
      { path: '/dashboard', element: <HomePage /> },
      { path: '/profile', element: <ProfilePage /> },
      { path: '/settings', element: <SettingsPage /> },
      { path: '/onboarding', element: <OnboardingPage /> },

      // Content Catalog (Figma MainScreen 1-3)
      { path: '/catalog', element: <ContentCatalogPage /> },
      { path: '/catalog/:contentId', element: <ContentDetailPage /> },

      // Minigames (Figma game_margin & mobile-screen 1-5)
      { path: '/games', element: useMock ? <GamesCatalogPage /> : <UnavailablePage title="Trò chơi học tập" /> },
      { path: '/games/word-match', element: useMock ? <WordMatchGamePage /> : <UnavailablePage title="Trò chơi ghép từ" /> },

      // Vocabulary SRS Routes
      { path: '/vocabulary', element: <VocabDeckListPage /> },
      { path: '/vocabulary/:deckId', element: <VocabDeckDetailPage /> },
      { path: '/vocabulary/:deckId/review', element: <VocabReviewPage /> },

      // Listening Practice Routes
      { path: '/listening', element: useMock ? <ListeningHubPage /> : <UnavailablePage title="Dictation và Shadowing" /> },
      ] },

      // 404 Fallback
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
