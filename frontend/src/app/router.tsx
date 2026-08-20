import { createBrowserRouter } from 'react-router-dom';
import { ProtectedRoute } from '../features/auth/components/ProtectedRoute';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { FishDetailPage } from '../features/catalog/pages/FishDetailPage';
import { FavoritesPage } from '../features/favorites/pages/FavoritesPage';
import { CatchListPage } from '../features/catchlog/pages/CatchListPage';
import { CatchNewPage } from '../features/catchlog/pages/CatchNewPage';
import { CatchDetailPage } from '../features/catchlog/pages/CatchDetailPage';
import { CatchEditPage } from '../features/catchlog/pages/CatchEditPage';
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
  {
    path: '/favorites',
    element: (
      <ProtectedRoute>
        <FavoritesPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/catches',
    element: (
      <ProtectedRoute>
        <CatchListPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/catches/new',
    element: (
      <ProtectedRoute>
        <CatchNewPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/catches/:id/edit',
    element: (
      <ProtectedRoute>
        <CatchEditPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/catches/:id',
    element: (
      <ProtectedRoute>
        <CatchDetailPage />
      </ProtectedRoute>
    ),
  },
]);
