import { QueryClient } from '@tanstack/react-query';
import { expect, test } from 'vitest';
import { catchDetailQueryKey, catchPageQueryKey } from '../../catchlog/api/catchRecordsApi';
import { adminFishDetailQueryKey, adminFishPageQueryKey } from '../../administration/api/adminFishApi';
import { ApiError } from '../../../shared/api/ApiError';
import { CURRENT_USER_QUERY_KEY } from './currentUser';
import { expireSessionOnUnauthorized } from './sessionCache';

test('a confirmed session expiry removes private and administrator data before removing the user', () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, { id: 1, nickname: 'Prior' });
  queryClient.setQueryData(catchPageQueryKey(0), { items: [{ id: 31 }] });
  queryClient.setQueryData(catchDetailQueryKey(31), { id: 31, notes: '仅用户 A 可见' });
  queryClient.setQueryData(
    adminFishPageQueryKey({ q: '', status: 'DRAFT', page: 0 }),
    { items: [{ id: 99, commonNameZh: '不应保留的草稿' }] },
  );
  queryClient.setQueryData(adminFishDetailQueryKey(99), { id: 99, status: 'DRAFT' });
  queryClient.setQueryData(['admin-photos', 'page', '41', 0], { items: [{ recordId: 31 }] });
  queryClient.setQueryData(['admin-photos', 'detail', 31], { ownerNickname: '私有照片' });
  queryClient.setQueryData(['admin-photos', 'operations', 31, 0], { items: [{ actorUserId: 2 }] });
  let exposedPrivateDataWithoutAUser = false;
  const unsubscribe = queryClient.getQueryCache().subscribe(() => {
    const user = queryClient.getQueryData(CURRENT_USER_QUERY_KEY);
    const hasCatchData = queryClient.getQueriesData({ queryKey: ['catches'] })
      .some(([, data]) => data !== undefined);
    const hasAdminData = queryClient.getQueriesData({ queryKey: ['admin-fishes'] })
      .some(([, data]) => data !== undefined);
    const hasPhotoData = queryClient.getQueriesData({ queryKey: ['admin-photos'] })
      .some(([, data]) => data !== undefined);
    if (user === undefined && (hasCatchData || hasAdminData || hasPhotoData)) exposedPrivateDataWithoutAUser = true;
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
  expect(queryClient.getQueriesData({ queryKey: ['admin-fishes'] })).toEqual([]);
  expect(queryClient.getQueriesData({ queryKey: ['admin-photos'] })).toEqual([]);
  expect(exposedPrivateDataWithoutAUser).toBe(false);
});
