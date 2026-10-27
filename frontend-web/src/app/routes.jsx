import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout.jsx';
import HomePage from '../pages/HomePage.jsx';
import HealthPage from '../pages/HealthPage.jsx';
import LoginPage from '../pages/LoginPage.jsx';
import NotFoundPage from '../pages/NotFoundPage.jsx';

export const router = createBrowserRouter([{
  element: <AppLayout />,
  children: [
    { path: '/', element: <HomePage /> },
    { path: '/health', element: <HealthPage /> },
    { path: '/login', element: <LoginPage /> },
    { path: '/dashboard', element: <Navigate to="/" replace /> },
    { path: '*', element: <NotFoundPage /> },
  ],
}]);
