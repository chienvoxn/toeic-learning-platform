
import {
  createBrowserRouter,
  Navigate,
  Outlet,
} from 'react-router-dom';

import { AppProviders } from './providers';

// ==================== PUBLIC ====================
import HomePage from '../pages/HomePage';
import HomeEntry from '../pages/HomeEntry';
import HealthPage from '../pages/HealthPage';
import NotFoundPage from '../pages/NotFoundPage';
import UnavailablePage from '../pages/UnavailablePage';

// ==================== AUTH ====================
import LoginPage from '../pages/auth/LoginPage';
import RegisterPage from '../pages/auth/RegisterPage';
import ForgotPasswordPage from '../pages/auth/ForgotPasswordPage';
import VerifyResetOtpPage from '../pages/auth/VerifyResetOtpPage';
import ResetPasswordPage from '../pages/auth/ResetPasswordPage';

import RequireAuth from '../features/auth/RequireAuth';
import RequireTeacher from '../features/auth/RequireTeacher';

// ==================== PROFILE ====================
import ProfilePage from '../pages/profile/ProfilePage';
import SettingsPage from '../pages/profile/SettingsPage';

// ==================== ASSESSMENT ====================
import TestCatalogPage from '../pages/assessment/TestCatalogPage';
import TestOverviewPage from '../pages/assessment/TestOverviewPage';
import AttemptPage from '../pages/assessment/AttemptPage';
import ResultPage from '../pages/assessment/ResultPage';
import PracticeSetupPage from '../pages/assessment/PracticeSetupPage';

// ==================== VOCABULARY ====================
import VocabDeckListPage from '../pages/vocabulary/VocabDeckListPage';
import VocabDeckDetailPage from '../pages/vocabulary/VocabDeckDetailPage';
import VocabReviewPage from '../pages/vocabulary/VocabReviewPage';

// ==================== CONTENT ====================
import ContentCatalogPage from '../pages/content/ContentCatalogPage';
import ContentDetailPage from '../pages/content/ContentDetailPage';

// ==================== GAMES ====================
import GamesCatalogPage from '../pages/games/GamesCatalogPage';
import WordMatchGamePage from '../pages/games/WordMatchGamePage';

// ==================== LISTENING ====================
import ListeningHubPage from '../pages/listening/ListeningHubPage';

// ==================== ONBOARDING ====================
import OnboardingPage from '../pages/onboarding/OnboardingPage';

// ==================== ADMIN ====================
import AdminUsersPage from '../pages/admin/AdminUsersPage';
import AdminRolesPage from '../pages/admin/AdminRolesPage';
import AdminAuditPage from '../pages/admin/AdminAuditPage';
import AdminOverviewPage from '../pages/admin/AdminOverviewPage';

// ==================== TEACHER WEB V5 ====================
import TeacherLayout from '../components/teacher/TeacherLayout';
import TeacherDashboardPage from '../pages/teacher/TeacherDashboardPage';
import TeacherExamsPage from '../pages/teacher/TeacherExamsPage';
import TeacherContentsPage from '../pages/teacher/TeacherContentsPage';
import TeacherQuestionsPage from '../pages/teacher/TeacherQuestionsPage';
import TeacherQuestionEditor from '../pages/teacher/TeacherQuestionEditor';

// ==================== CONFIG ====================
import { useMock } from '../lib/runtime-config';

// ==================== ROOT ====================
function RootLayout() {
  return (
    <AppProviders>
      <Outlet />
    </AppProviders>
  );
}

// ==================== ROUTER ====================
export const router = createBrowserRouter([
  {
    element: <RootLayout />,

    children: [
      // ==========================================
      // PUBLIC ROUTES
      // ==========================================
      {
        path: '/',
        element: <HomeEntry />,
      },
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        path: '/register',
        element: <RegisterPage />,
      },
      {
        path: '/forgot-password',
        element: <ForgotPasswordPage />,
      },
      {
        path: '/verify-reset-otp',
        element: <VerifyResetOtpPage />,
      },
      {
        path: '/reset-password',
        element: <ResetPasswordPage />,
      },
      {
        path: '/verify-email',
        element: <Navigate to="/login" replace />,
      },
      {
        path: '/health',
        element: <HealthPage />,
      },

      // Public TOEIC Tests
      {
        path: '/tests',
        element: <TestCatalogPage />,
      },
      {
        path: '/tests/:testId/overview',
        element: <TestOverviewPage />,
      },
      {
        path: '/attempts/:attemptId',
        element: <AttemptPage />,
      },
      {
        path: '/attempts/:attemptId/result',
        element: <ResultPage />,
      },

      // ==========================================
      // AUTHENTICATED ROUTES
      // ==========================================
      {
        element: <RequireAuth />,

        children: [
          // ======================================
          // ADMIN WEB
          // ======================================
          {
            path: '/admin/users',
            element: <AdminUsersPage />,
          },
          {
            path: '/admin/roles',
            element: <AdminRolesPage />,
          },
          {
            path: '/admin/audit',
            element: <AdminAuditPage />,
          },
          {
            path: '/admin/overview',
            element: <AdminOverviewPage />,
          },

          // ======================================
          // STUDENT WEB
          // ======================================
          {
            path: '/practice',
            element: <PracticeSetupPage />,
          },
          {
            path: '/practice/attempts/:attemptId',
            element: <AttemptPage remote />,
          },
          {
            path: '/practice/attempts/:attemptId/result',
            element: <ResultPage remote />,
          },
          {
            path: '/dashboard',
            element: <HomePage />,
          },
          {
            path: '/profile',
            element: <ProfilePage />,
          },
          {
            path: '/settings',
            element: <SettingsPage />,
          },
          {
            path: '/onboarding',
            element: <OnboardingPage />,
          },

          // Student Content
          {
            path: '/catalog',
            element: <ContentCatalogPage />,
          },
          {
            path: '/catalog/:contentId',
            element: <ContentDetailPage />,
          },

          // Student Games
          {
            path: '/games',
            element: useMock
              ? <GamesCatalogPage />
              : <UnavailablePage title="Trò chơi học tập" />,
          },
          {
            path: '/games/word-match',
            element: useMock
              ? <WordMatchGamePage />
              : <UnavailablePage title="Trò chơi ghép từ" />,
          },

          // Vocabulary
          {
            path: '/vocabulary',
            element: <VocabDeckListPage />,
          },
          {
            path: '/vocabulary/:deckId',
            element: <VocabDeckDetailPage />,
          },
          {
            path: '/vocabulary/:deckId/review',
            element: <VocabReviewPage />,
          },

          // Listening
          {
            path: '/listening',
            element: useMock
              ? <ListeningHubPage />
              : <UnavailablePage title="Dictation và Shadowing" />,
          },

          // ======================================
          // TEACHER WEB V5
          // ======================================
          {
            element: <RequireTeacher />,

            children: [
              {
                path: '/teacher',
                element: <TeacherLayout />,

                children: [
                  // Teacher Dashboard
                  {
                    index: true,
                    element: <TeacherDashboardPage />,
                  },

                  // Exams & Submissions
                  {
                    path: 'exams',
                    element: <TeacherExamsPage />,
                  },

                  // Content Management
                  {
                    path: 'contents',
                    element: <TeacherContentsPage />,
                  },

                  // Question Bank
                  {
                    path: 'questions',
                    element: <TeacherQuestionsPage />,
                  },

                  // Create TOEIC Question
                  {
                    path: 'questions/new',
                    element: <TeacherQuestionEditor />,
                  },

                  // Future modules:
                  // classes, reports,
                  // exams/new, exams/:id
                ],
              },
            ],
          },
        ],
      },

      // ==========================================
      // NOT FOUND
      // ==========================================
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
]);
