import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  currentUserQueryConfig,
  fetchCurrentUser,
  hasUsableCurrentUser,
  isConfirmedUnauthorized,
} from '../../auth/api/currentUser';
import {
  addFavorite,
  FAVORITES_QUERY_KEY,
  removeFavorite,
} from '../api/favoritesApi';

type FavoriteButtonProps = {
  fishSlug: string;
  isFavorited: boolean;
  returnTo: string;
};

export function FavoriteButton({
  fishSlug,
  isFavorited,
  returnTo,
}: FavoriteButtonProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUser = useQuery({
    ...currentUserQueryConfig,
    queryFn: fetchCurrentUser,
  });
  const hasAuthenticatedSession = hasUsableCurrentUser(
    currentUser.data,
    currentUser.error,
  );
  const confirmedUnauthorized = isConfirmedUnauthorized(currentUser.error);
  const effectiveIsFavorited = hasAuthenticatedSession && isFavorited;
  const mutation = useMutation({
    mutationFn: () => (
      effectiveIsFavorited ? removeFavorite(fishSlug) : addFavorite(fishSlug)
    ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FAVORITES_QUERY_KEY }),
  });

  const label = effectiveIsFavorited ? '取消收藏' : '收藏';
  const isSessionUnresolved = !hasAuthenticatedSession && !confirmedUnauthorized;
  const isPending = currentUser.isPending || isSessionUnresolved || mutation.isPending;

  const handleClick = () => {
    if (confirmedUnauthorized) {
      navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`);
      return;
    }

    if (!hasAuthenticatedSession) return;
    mutation.mutate();
  };

  return (
    <>
      <button
        type="button"
        aria-label={mutation.isPending ? '正在处理收藏' : label}
        aria-pressed={effectiveIsFavorited}
        disabled={isPending}
        onClick={handleClick}
      >
        {mutation.isPending ? '处理中…' : label}
      </button>
      {mutation.isError ? (
        <p role="status" aria-live="polite">收藏操作失败，请稍后重试</p>
      ) : null}
    </>
  );
}
