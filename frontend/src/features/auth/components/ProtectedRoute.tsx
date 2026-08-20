import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import {
  currentUserQueryConfig,
  fetchCurrentUser,
} from '../api/currentUser';

type ProtectedRouteProps = {
  children: ReactNode;
};

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const location = useLocation();
  const currentUser = useQuery({
    ...currentUserQueryConfig,
    queryFn: fetchCurrentUser,
  });

  if (currentUser.isPending) {
    return (
      <p role="status" aria-label="正在检查登录状态">
        正在检查登录状态…
      </p>
    );
  }

  if (currentUser.isError) {
    if (currentUser.error instanceof ApiError && currentUser.error.status === 401) {
      const returnTo = `${location.pathname}${location.search}${location.hash}`;
      return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
    }

    return <p role="status">暂时无法确认登录状态，请稍后重试</p>;
  }

  return children;
}
