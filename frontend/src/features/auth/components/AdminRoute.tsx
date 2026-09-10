import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import {
  currentUserQueryConfig,
  fetchCurrentUser,
} from '../api/currentUser';
import { expireSessionOnUnauthorized } from '../api/sessionCache';

type AdminRouteProps = {
  children: ReactNode;
};

export function AdminRoute({ children }: AdminRouteProps) {
  const location = useLocation();
  const queryClient = useQueryClient();
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
      expireSessionOnUnauthorized(queryClient, currentUser.error);
      const returnTo = `${location.pathname}${location.search}${location.hash}`;
      return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
    }

    return <p role="status">暂时无法确认登录状态，请稍后重试</p>;
  }

  if (currentUser.data.role !== 'ADMIN') {
    return (
      <main>
        <h1>没有管理员权限</h1>
        <p>此页面仅供管理员维护鱼类图鉴。</p>
        <Link to="/">返回首页</Link>
      </main>
    );
  }

  return children;
}
