import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { useEffect } from 'react';
import { expect, test } from 'vitest';
import { catchDetailQueryKey, catchPageQueryKey } from '../../catchlog/api/catchRecordsApi';
import { ApiError } from '../../../shared/api/ApiError';
import { useSessionExpiry } from './useExpireSessionOnUnauthorized';

const sessionExpiredError = new ApiError(401, {
  code: 'AUTHENTICATION_REQUIRED',
  message: 'Authentication is required',
  fieldErrors: [],
  requestId: 'session-expired',
});

test('removes catch cache before rendering sessionExpired after a confirmed 401', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(catchPageQueryKey(0), { items: [{ id: 31 }] });
  queryClient.setQueryData(catchDetailQueryKey(31), { id: 31, notes: '仅用户 A 可见' });
  const catchDataSeenWhenExpired: boolean[] = [];

  function ExpiryProbe() {
    const cache = useQueryClient();
    const { expireIfUnauthorized, sessionExpired } = useSessionExpiry();
    useEffect(() => {
      expireIfUnauthorized(sessionExpiredError);
    }, [expireIfUnauthorized]);

    if (sessionExpired) {
      catchDataSeenWhenExpired.push(
        cache.getQueriesData({ queryKey: ['catches'] }).some(([, data]) => data !== undefined),
      );
    }

    return <output>{sessionExpired ? 'expired' : 'active'}</output>;
  }

  render(
    <QueryClientProvider client={queryClient}>
      <ExpiryProbe />
    </QueryClientProvider>,
  );

  expect(await screen.findByText('expired')).toBeInTheDocument();
  expect(catchDataSeenWhenExpired).toEqual([false]);
  expect(queryClient.getQueriesData({ queryKey: ['catches'] })).toEqual([]);
});
