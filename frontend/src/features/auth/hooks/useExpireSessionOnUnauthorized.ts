import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { isConfirmedUnauthorized } from '../api/currentUser';
import { expireSessionOnUnauthorized } from '../api/sessionCache';

export function useFavoriteSessionExpiry() {
  const queryClient = useQueryClient();
  const [sessionExpired, setSessionExpired] = useState(false);
  const unauthorizedError = useRef<unknown>(undefined);

  const expireIfUnauthorized = useCallback((error: unknown) => {
    if (!isConfirmedUnauthorized(error)) return false;

    unauthorizedError.current = error;
    setSessionExpired(true);
    return true;
  }, []);

  useEffect(() => {
    if (!sessionExpired) return;
    expireSessionOnUnauthorized(queryClient, unauthorizedError.current);
  }, [queryClient, sessionExpired]);

  return { sessionExpired, expireIfUnauthorized };
}
