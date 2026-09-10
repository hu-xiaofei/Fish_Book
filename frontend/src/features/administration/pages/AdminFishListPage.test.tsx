import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { ADMIN_FISHES_QUERY_KEY, adminFishDetailQueryKey } from '../api/adminFishApi';
import type { AdminFishPage } from '../model/types';
import { AdminFishListPage } from './AdminFishListPage';

const { fetchAdminFishPageMock } = vi.hoisted(() => ({ fetchAdminFishPageMock: vi.fn() }));

vi.mock('../api/adminFishApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/adminFishApi')>();
  return { ...actual, fetchAdminFishPage: fetchAdminFishPageMock };
});

vi.mock('../../auth/components/SessionNav', () => ({
  SessionNav: () => <nav aria-label="用户导航">会话导航</nav>,
}));

const populatedPage: AdminFishPage = {
  items: [{
    id: 7, slug: 'cyprinus-carpio', commonNameZh: '鲤鱼', scientificName: 'Cyprinus carpio',
    status: 'DRAFT', updatedAt: '2026-08-30T10:00:00Z',
  }],
  page: 0, size: 20, totalItems: 21, totalPages: 2,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderList(initialEntry = '/admin/fishes') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, retryDelay: 0 }, mutations: { retry: false } },
  });

  function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter></QueryClientProvider>;
  }

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/admin/fishes" element={<><AdminFishListPage /><LocationProbe /></>} />
        <Route path="/login" element={<><h1>登录</h1><LocationProbe /></>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  fetchAdminFishPageMock.mockReset();
  fetchAdminFishPageMock.mockImplementation((filters) => Promise.resolve({ ...populatedPage, page: filters.page }));
});

test('shows an accessible loading state while managed fish are pending', () => {
  fetchAdminFishPageMock.mockImplementation(() => new Promise(() => undefined));
  renderList();
  expect(screen.getByText('正在加载图鉴管理列表…')).toHaveAttribute('role', 'status');
});

test('renders management rows with localized publication statuses and edit links', async () => {
  renderList();
  const row = await screen.findByRole('row', { name: /鲤鱼/ });
  expect(within(row).getByText('草稿')).toBeInTheDocument();
  expect(within(row).getByRole('link', { name: '编辑鲤鱼' })).toHaveAttribute('href', '/admin/fishes/7/edit');
  expect(screen.getByRole('link', { name: '新建鱼类' })).toHaveAttribute('href', '/admin/fishes/new');
});

test('submits a keyword through the URL and resets the page', async () => {
  const { user } = renderList('/admin/fishes?page=2');
  await user.type(await screen.findByRole('searchbox', { name: '搜索鱼类' }), '  鲤  ');
  await user.click(screen.getByRole('button', { name: '搜索' }));
  expect(screen.getByTestId('location')).toHaveTextContent('/admin/fishes?q=%E9%B2%A4');
});

test('filters by status through the URL and resets the page', async () => {
  const { user } = renderList('/admin/fishes?q=鲤&page=2');
  await user.selectOptions(await screen.findByLabelText('发布状态'), 'DRAFT');
  expect(screen.getByTestId('location')).toHaveTextContent('/admin/fishes?q=%E9%B2%A4&status=DRAFT');
});

test('uses pagination metadata to update the URL', async () => {
  const { user } = renderList();
  const pagination = await screen.findByRole('navigation', { name: '图鉴管理分页' });
  expect(within(pagination).getByRole('button', { name: '上一页' })).toBeDisabled();
  await user.click(within(pagination).getByRole('button', { name: '下一页' }));
  expect(screen.getByTestId('location')).toHaveTextContent('/admin/fishes?page=1');
  await waitFor(() => expect(fetchAdminFishPageMock).toHaveBeenLastCalledWith({ q: '', status: '', page: 1 }));
});

test('shows a safe retryable error and an empty state', async () => {
  fetchAdminFishPageMock
    .mockRejectedValueOnce(new Error('db.internal unavailable'))
    .mockRejectedValueOnce(new Error('db.internal unavailable'))
    .mockRejectedValueOnce(new Error('db.internal unavailable'))
    .mockResolvedValueOnce({ ...populatedPage, items: [], totalItems: 0, totalPages: 0 });
  const { user } = renderList();
  const status = await screen.findByText('加载图鉴管理列表失败，请稍后重试');
  expect(status).toHaveTextContent('加载图鉴管理列表失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
  await user.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByRole('heading', { name: '还没有管理中的鱼类' })).toBeInTheDocument();
});

test('a confirmed management-page 401 clears administrator queries before redirecting', async () => {
  fetchAdminFishPageMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  const { queryClient } = renderList();
  queryClient.setQueryData(adminFishDetailQueryKey(7), { id: 7, status: 'DRAFT' });
  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(
    queryClient.getQueriesData({ queryKey: ADMIN_FISHES_QUERY_KEY })
      .every(([, data]) => data === undefined),
  ).toBe(true);
});
