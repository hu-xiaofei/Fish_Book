import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import { logout } from '../api/authApi';
import {
  CURRENT_USER_QUERY_KEY,
  currentUserQueryConfig,
  fetchCurrentUser,
} from '../api/currentUser';

export function SessionNav() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUser = useQuery({
    ...currentUserQueryConfig,
    queryFn: fetchCurrentUser,
  });
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.removeQueries({
        queryKey: CURRENT_USER_QUERY_KEY,
        exact: true,
      });
      navigate('/login', { replace: true });
    },
  });

  if (
    !currentUser.data
    || (currentUser.error instanceof ApiError && currentUser.error.status === 401)
  ) {
    return null;
  }

  return (
    <nav aria-label="用户导航">
      <Link to="/profile">个人资料</Link>{' '}
      <Link to="/favorites">我的收藏</Link>{' '}
      <button
        type="button"
        disabled={logoutMutation.isPending}
        onClick={() => logoutMutation.mutate()}
      >
        {logoutMutation.isPending ? '退出中…' : '退出登录'}
      </button>
      {logoutMutation.isError ? (
        <p role="status">退出登录失败，请稍后重试</p>
      ) : null}
    </nav>
  );
}
