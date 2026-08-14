import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import type { User } from '../../../shared/api/types';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import { CURRENT_USER_QUERY_KEY } from '../api/currentUser';
import { SessionNav } from './SessionNav';

const { fetchCurrentUserMock, logoutMock } = vi.hoisted(() => ({
  fetchCurrentUserMock: vi.fn(),
  logoutMock: vi.fn(),
}));

vi.mock('../api/authApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/authApi')>();
  return { ...actual, logout: logoutMock };
});

vi.mock('../api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'River',
  role: 'USER',
};

function seedUserFavorites(queryClient: QueryClient) {
  queryClient.setQueryData([...FAVORITES_QUERY_KEY, 'page', 0], {
    items: [{ fishSlug: 'user-a-fish' }],
    page: 0,
  });
  queryClient.setQueryData([...FAVORITES_QUERY_KEY, 'status', 'user-a-fish'], {
    items: [{ fishSlug: 'user-a-fish', favorited: true }],
  });
}

function renderSessionNav() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, authenticatedUser);

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<SessionNav />} />
            <Route path="/login" element={<h1>登录页</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

beforeEach(() => {
  fetchCurrentUserMock.mockReset();
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
  logoutMock.mockReset();
  logoutMock.mockResolvedValue(undefined);
});

test('authenticated navigation includes favorites without advertising catches early', () => {
  renderSessionNav();

  expect(screen.getByRole('link', { name: '我的收藏' })).toHaveAttribute('href', '/favorites');
  expect(screen.queryByRole('link', { name: /钓获/ })).not.toBeInTheDocument();
});

test('successful logout removes all favorite queries before current-user data', async () => {
  const { queryClient, user } = renderSessionNav();
  seedUserFavorites(queryClient);
  let exposedFavoritesWithoutAUser = false;
  const unsubscribe = queryClient.getQueryCache().subscribe(() => {
    const currentUser = queryClient.getQueryData(CURRENT_USER_QUERY_KEY);
    const hasFavoriteData = queryClient
      .getQueriesData({ queryKey: FAVORITES_QUERY_KEY })
      .some(([, data]) => data !== undefined);
    if (currentUser === undefined && hasFavoriteData) {
      exposedFavoritesWithoutAUser = true;
    }
  });

  await user.click(screen.getByRole('button', { name: '退出登录' }));

  expect(await screen.findByRole('heading', { name: '登录页' })).toBeInTheDocument();
  unsubscribe();
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY })).toEqual([]);
  expect(exposedFavoritesWithoutAUser).toBe(false);
});

test('failed logout retains current-user and favorite queries', async () => {
  logoutMock.mockRejectedValue(new Error('network unavailable'));
  const { queryClient, user } = renderSessionNav();
  seedUserFavorites(queryClient);

  await user.click(screen.getByRole('button', { name: '退出登录' }));

  expect(await screen.findByRole('status')).toHaveTextContent('退出登录失败，请稍后重试');
  await waitFor(() => expect(logoutMock).toHaveBeenCalledTimes(1));
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toEqual(authenticatedUser);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }))
    .toHaveLength(2);
});
