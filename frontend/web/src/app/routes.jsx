import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { AppProviders } from './providers';
import HomePage from '../pages/HomePage';
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
      { path: '/', element: <HomePage /> },
      { path: '/dashboard', element: <Navigate to="/" replace /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/profile', element: <ProfilePage /> },
      { path: '/settings', element: <SettingsPage /> },
      { path: '/onboarding', element: <OnboardingPage /> },

      // Content Catalog (Figma MainScreen 1-3)
      { path: '/catalog', element: <ContentCatalogPage /> },

      // Minigames (Figma game_margin & mobile-screen 1-5)
      { path: '/games', element: <GamesCatalogPage /> },
      { path: '/games/word-match', element: <WordMatchGamePage /> },

      // Assessment Routes
      { path: '/tests', element: <TestCatalogPage /> },
      { path: '/tests/:testId/overview', element: <TestOverviewPage /> },
      { path: '/attempts/:attemptId', element: <AttemptPage /> },
      { path: '/attempts/:attemptId/result', element: <ResultPage /> },

      // Vocabulary SRS Routes
      { path: '/vocabulary', element: <VocabDeckListPage /> },
      { path: '/vocabulary/:deckId', element: <VocabDeckDetailPage /> },
      { path: '/vocabulary/:deckId/review', element: <VocabReviewPage /> },

      // Listening Practice Routes
      { path: '/listening', element: <ListeningHubPage /> },

      // System Health Check
      { path: '/health', element: <HealthPage /> },

      // 404 Fallback
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
