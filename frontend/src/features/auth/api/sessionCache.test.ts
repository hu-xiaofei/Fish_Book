import { QueryClient } from '@tanstack/react-query';
import { expect, test } from 'vitest';
import { catchDetailQueryKey, catchPageQueryKey } from '../../catchlog/api/catchRecordsApi';
import { ApiError } from '../../../shared/api/ApiError';
import { CURRENT_USER_QUERY_KEY } from './currentUser';
import { expireSessionOnUnauthorized } from './sessionCache';

test('a confirmed session expiry removes catch pages and details before resetting the user', () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, { id: 1, nickname: 'Prior' });
  queryClient.setQueryData(catchPageQueryKey(0), { items: [{ id: 31 }] });
  queryClient.setQueryData(catchDetailQueryKey(31), { id: 31, notes: '仅用户 A 可见' });
  let exposedPrivateDataWithoutAUser = false;
  const unsubscribe = queryClient.getQueryCache().subscribe(() => {
    const user = queryClient.getQueryData(CURRENT_USER_QUERY_KEY);
    const hasCatchData = queryClient.getQueriesData({ queryKey: ['catches'] })
      .some(([, data]) => data !== undefined);
    if (user === undefined && hasCatchData) exposedPrivateDataWithoutAUser = true;
  });

  const handled = expireSessionOnUnauthorized(queryClient, new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: 'Authentication is required',
    fieldErrors: [],
    requestId: 'session-expired',
  }));

  unsubscribe();
  expect(handled).toBe(true);
  expect(queryClient.getQueriesData({ queryKey: ['catches'] })).toEqual([]);
  expect(exposedPrivateDataWithoutAUser).toBe(false);
});
