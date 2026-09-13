import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useState, useSyncExternalStore } from 'react';
import { ApiError } from '../../../../shared/api/ApiError';
import { captureSessionGeneration, subscribeSessionGeneration } from '../../../auth/api/sessionCache';
import { useSessionExpiry } from '../../../auth/hooks/useExpireSessionOnUnauthorized';
import { ADMIN_PHOTOS_QUERY_KEY } from '../api/adminPhotoApi';

export function useAdminPhotoSession() {
  const [initialGeneration] = useState(captureSessionGeneration);
  const generation = useSyncExternalStore(subscribeSessionGeneration, captureSessionGeneration);
  return { generation, sessionChanged: generation !== initialGeneration };
}

export const isAdminPhotoAccessError = (error: unknown) => error instanceof ApiError && (error.status === 401 || error.status === 403);

export function useAdminPhotoAccess() {
  const queryClient = useQueryClient();
  const { sessionChanged } = useAdminPhotoSession();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const [forbidden, setForbidden] = useState(false);
  const onAccessError = useCallback((error: unknown) => {
    if (expireIfUnauthorized(error)) return;
    if (error instanceof ApiError && error.status === 403) {
      setForbidden(true);
      queryClient.removeQueries({ queryKey: ADMIN_PHOTOS_QUERY_KEY });
    }
  }, [expireIfUnauthorized, queryClient]);
  return { sessionEnded: sessionChanged || sessionExpired, forbidden, onAccessError };
}
