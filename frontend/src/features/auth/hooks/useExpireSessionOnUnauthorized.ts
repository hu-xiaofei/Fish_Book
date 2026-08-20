import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { expireSessionOnUnauthorized } from '../api/sessionCache';

export function useSessionExpiry() {
  const queryClient = useQueryClient();
  const [sessionExpired, setSessionExpired] = useState(false);

  const expireIfUnauthorized = useCallback((error: unknown) => {
    if (!expireSessionOnUnauthorized(queryClient, error)) return false;

    setSessionExpired(true);
    return true;
  }, [queryClient]);

  return { sessionExpired, expireIfUnauthorized };
}
