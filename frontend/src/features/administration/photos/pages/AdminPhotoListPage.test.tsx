import { screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { fetchAdminPhotoPage } from '../api/adminPhotoApi';
import { apiError, page, renderAdmin } from '../test/helpers';
import { AdminPhotoListPage } from './AdminPhotoListPage';

vi.mock('../api/adminPhotoApi', async (original) => ({ ...await original<typeof import('../api/adminPhotoApi')>(), fetchAdminPhotoPage: vi.fn() }));
vi.mock('../../../auth/components/SessionNav', () => ({ SessionNav: () => null }));
beforeEach(() => { vi.mocked(fetchAdminPhotoPage).mockReset().mockResolvedValue(page); });

test('shows minimal owner/fish/date metadata and opens detail without eager image loading', async () => {
  renderAdmin(<AdminPhotoListPage />);
  expect(await screen.findByText('河边钓友')).toBeVisible();
  expect(screen.getByText('41')).toBeVisible();
  expect(screen.getByText('鲫鱼')).toBeVisible();
  expect(screen.getByText('2026-09-12')).toBeVisible();
  expect(screen.getByText('有照片')).toBeVisible();
  expect(screen.getByRole('link', { name: '查看照片 31' })).toHaveAttribute('href', '/admin/photos/31');
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
});

test('submits owner filter and paginates via URL while preserving the filter', async () => {
  const { user } = renderAdmin(<AdminPhotoListPage />);
  await screen.findByText('河边钓友');
  await user.type(screen.getByLabelText('所属用户 ID'), '41');
  await user.click(screen.getByRole('button', { name: '筛选' }));
  expect(screen.getByTestId('location')).toHaveTextContent('/admin/photos?userId=41');
  await waitFor(() => expect(fetchAdminPhotoPage).toHaveBeenLastCalledWith({ userId: '41', page: 0, size: 20 }));
  await user.click(screen.getByRole('button', { name: '下一页' }));
  expect(screen.getByTestId('location')).toHaveTextContent('userId=41&page=1');
});

test('malformed owner filter never silently lists every user', async () => {
  renderAdmin(<AdminPhotoListPage />, '/admin/photos?userId=nope');
  expect(screen.getByRole('status')).toHaveTextContent('筛选参数无效');
  expect(fetchAdminPhotoPage).not.toHaveBeenCalled();
});

test('invalid submitted owner filter hides the prior unfiltered list rather than implying it matched', async () => {
  const { user } = renderAdmin(<AdminPhotoListPage />);
  await screen.findByText('河边钓友');
  await user.type(screen.getByLabelText('所属用户 ID'), 'wrong-id');
  await user.click(screen.getByRole('button', { name: '筛选' }));
  expect(screen.getByRole('status')).toHaveTextContent('筛选参数无效');
  expect(screen.queryByText('河边钓友')).not.toBeInTheDocument();
  expect(fetchAdminPhotoPage).toHaveBeenCalledTimes(1);
});

test('empty list is useful and generic failure offers a read-only retry', async () => {
  vi.mocked(fetchAdminPhotoPage).mockRejectedValueOnce(apiError(500)).mockResolvedValue({ ...page, items: [], totalItems: 0, totalPages: 0 });
  const { user } = renderAdmin(<AdminPhotoListPage />);
  expect(await screen.findByText('加载照片管理列表失败，请稍后重试')).toBeVisible();
  await user.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByText('暂无照片')).toBeVisible();
  expect(screen.queryByText('internal-private-key')).not.toBeInTheDocument();
});

test.each([401, 403])('status %s hides cached private list and does not retry', async (status) => {
  vi.mocked(fetchAdminPhotoPage).mockRejectedValue(apiError(status));
  renderAdmin(<AdminPhotoListPage />);
  expect(await screen.findByRole('heading', { name: status === 401 ? '登录页' : '没有管理员权限' })).toBeVisible();
  expect(fetchAdminPhotoPage).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('河边钓友')).not.toBeInTheDocument();
});
