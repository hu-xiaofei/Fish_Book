import { screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { fetchAdminPhotoOperations } from '../api/adminPhotoApi';
import { apiError, renderAdmin } from '../test/helpers';
import { AdminPhotoOperations } from './AdminPhotoOperations';

vi.mock('../api/adminPhotoApi', async (original) => ({ ...await original<typeof import('../api/adminPhotoApi')>(), fetchAdminPhotoOperations: vi.fn() }));
beforeEach(() => { vi.mocked(fetchAdminPhotoOperations).mockReset(); });

test('displays read-only administrator evidence with separate pagination', async () => {
  vi.mocked(fetchAdminPhotoOperations).mockResolvedValue({ items: [{ id: 9, actorUserId: 2, ownerUserId: 41, recordId: 31, operation: 'REPLACED', previousRevision: '7', occurredAt: '2026-09-12T11:00:00Z' }], page: 0, size: 20, totalItems: 21, totalPages: 2 });
  const { user } = renderAdmin(<AdminPhotoOperations recordId={31} onAccessError={vi.fn()} />);
  expect(await screen.findByText('替换照片')).toBeVisible();
  expect(screen.getByText('2')).toBeVisible();
  expect(screen.getByText('7')).toBeVisible();
  await user.click(screen.getByRole('button', { name: '下一页操作' }));
  expect(fetchAdminPhotoOperations).toHaveBeenLastCalledWith(31, 1);
  expect(screen.queryByRole('button', { name: '删除操作记录' })).not.toBeInTheDocument();
});

test('history has an independent loading error, retry and empty state', async () => {
  vi.mocked(fetchAdminPhotoOperations).mockRejectedValueOnce(apiError(500)).mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  const { user } = renderAdmin(<AdminPhotoOperations recordId={31} onAccessError={vi.fn()} />);
  expect(await screen.findByText('加载操作记录失败，请稍后重试')).toBeVisible();
  await user.click(screen.getByRole('button', { name: '重试操作记录' }));
  expect(await screen.findByText('暂无管理员操作记录')).toBeVisible();
});
