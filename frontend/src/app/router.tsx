import { createBrowserRouter } from 'react-router-dom';
import { ProtectedRoute } from '../features/auth/components/ProtectedRoute';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { FishDetailPage } from '../features/catalog/pages/FishDetailPage';
import { App } from './App';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
  },
  {
    path: '/fish/:slug',
    element: <FishDetailPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/profile',
    element: (
      <ProtectedRoute>
        <ProfilePage />
      </ProtectedRoute>
    ),
  },
]);
