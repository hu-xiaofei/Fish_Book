import { hashKey, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import {
  CURRENT_USER_QUERY_KEY,
  isConfirmedUnauthorized,
} from '../api/currentUser';

const currentUserQueryHash = hashKey(CURRENT_USER_QUERY_KEY);

export function useConfirmedUnauthorizedSession(error: unknown): boolean {
  const queryClient = useQueryClient();
  const [cachedUnauthorized, setCachedUnauthorized] = useState(() => (
    isConfirmedUnauthorized(queryClient.getQueryState(CURRENT_USER_QUERY_KEY)?.error)
  ));

  useEffect(() => queryClient.getQueryCache().subscribe((event) => {
    if (event.query.queryHash !== currentUserQueryHash) return;

    if (event.type === 'removed') {
      setCachedUnauthorized(false);
      return;
    }
    if (event.type !== 'updated') return;

    if (isConfirmedUnauthorized(event.query.state.error)) {
      setCachedUnauthorized(true);
    } else if (event.query.state.status === 'success') {
      setCachedUnauthorized(false);
    }
  }), [queryClient]);

  return isConfirmedUnauthorized(error) || cachedUnauthorized;
}
