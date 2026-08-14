import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { deferred } from '../../../test/renderWithProviders';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { ProtectedRoute } from '../../auth/components/ProtectedRoute';
import {
  FAVORITES_QUERY_KEY,
  favoritePageQueryKey,
} from '../api/favoritesApi';
import type { FavoritePage } from '../model/types';
import { FavoritesPage } from './FavoritesPage';

const { fetchCurrentUserMock, fetchFavoritePageMock, removeFavoriteMock } = vi.hoisted(() => ({
  fetchCurrentUserMock: vi.fn(),
  fetchFavoritePageMock: vi.fn(),
  removeFavoriteMock: vi.fn(),
}));

vi.mock('../../auth/api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

vi.mock('../api/favoritesApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/favoritesApi')>();
  return {
    ...actual,
    fetchFavoritePage: fetchFavoritePageMock,
    removeFavorite: removeFavoriteMock,
  };
});

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'River',
  role: 'USER',
};

const populatedPage: FavoritePage = {
  items: [{
    slug: 'channa-argus',
    commonNameZh: '乌鳢',
    scientificName: 'Channa argus',
    familyNameZh: '鳢科',
    aliases: ['黑鱼'],
    habitats: [{ code: 'LAKE', labelZh: '湖泊' }],
    imagePath: '/images/fish/channa-argus.jpg',
    imageAltText: '乌鳢（Channa argus）',
    favoritedAt: '2026-08-13T16:00:00Z',
  }],
  page: 0,
  size: 12,
  totalItems: 1,
  totalPages: 1,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderFavorites({
  initialEntry = '/favorites',
  protectedRoute = false,
  queryRetry = false,
  cachedUser,
  cachedUserUpdatedAt = 1,
  cachedFavoritePage,
}: {
  initialEntry?: string;
  protectedRoute?: boolean;
  queryRetry?: boolean | number;
  cachedUser?: User;
  cachedUserUpdatedAt?: number;
  cachedFavoritePage?: FavoritePage;
} = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: queryRetry, retryDelay: 0 },
      mutations: { retry: false },
    },
  });
  if (!protectedRoute) {
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, authenticatedUser);
  }
  if (cachedUser) {
    queryClient.setQueryData(
      CURRENT_USER_QUERY_KEY,
      cachedUser,
      { updatedAt: cachedUserUpdatedAt },
    );
  }
  if (cachedFavoritePage) {
    queryClient.setQueryData(
      favoritePageQueryKey(cachedFavoritePage.page),
      cachedFavoritePage,
      { updatedAt: 1 },
    );
  }

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  const page = protectedRoute ? (
    <ProtectedRoute>
      <FavoritesPage />
    </ProtectedRoute>
  ) : <FavoritesPage />;

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/favorites" element={<>{page}<LocationProbe /></>} />
        <Route path="/login" element={<h1>登录</h1>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  fetchCurrentUserMock.mockReset();
  fetchFavoritePageMock.mockReset();
  removeFavoriteMock.mockReset();
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
  fetchFavoritePageMock.mockResolvedValue(populatedPage);
  removeFavoriteMock.mockResolvedValue(undefined);
});

test('shows an accessible loading state while favorites are pending', () => {
  fetchFavoritePageMock.mockImplementation(() => new Promise(() => undefined));

  renderFavorites();

  expect(screen.getByText('正在加载收藏…')).toHaveAttribute('role', 'status');
});

test('shows an empty state without catalog requests when no favorites exist', async () => {
  fetchFavoritePageMock.mockResolvedValue({
    ...populatedPage,
    items: [],
    totalItems: 0,
    totalPages: 0,
  });

  renderFavorites();

  expect(await screen.findByRole('heading', { name: '还没有收藏鱼类' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '去鱼图鉴看看' })).toHaveAttribute('href', '/');
});

test('renders response summaries directly with a stable Shanghai favorite date', async () => {
  renderFavorites();

  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(screen.getByText('Channa argus')).toBeInTheDocument();
  expect(screen.getByText('收藏于 2026/8/14')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '取消收藏' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('link', { name: '查看乌鳢详情' })).toHaveAttribute(
    'href',
    '/fish/channa-argus',
  );
});

test('uses a safe fallback instead of rendering an invalid favorite date', async () => {
  fetchFavoritePageMock.mockResolvedValue({
    ...populatedPage,
    items: [{ ...populatedPage.items[0], favoritedAt: 'not-a-date' }],
  });

  renderFavorites();

  expect(await screen.findByText('收藏时间未知')).toBeInTheDocument();
  expect(screen.queryByText(/Invalid Date/)).not.toBeInTheDocument();
});

test('shows a safe error and retries on demand', async () => {
  fetchFavoritePageMock
    .mockRejectedValueOnce(new Error('favorites SQL failed at db.internal'))
    .mockRejectedValueOnce(new Error('favorites SQL failed at db.internal'))
    .mockRejectedValueOnce(new Error('favorites SQL failed at db.internal'))
    .mockResolvedValueOnce(populatedPage);
  const { user } = renderFavorites();

  const status = await screen.findByText('加载收藏失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
  await user.click(screen.getByRole('button', { name: '重试' }));

  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(fetchFavoritePageMock).toHaveBeenCalledTimes(4);
});

test('does not retry an unauthorized favorites page request', async () => {
  fetchFavoritePageMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  renderFavorites({ queryRetry: 2 });

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(fetchFavoritePageMock).toHaveBeenCalledTimes(1);
});

test('uses server page metadata to navigate between fixed pages', async () => {
  fetchFavoritePageMock.mockImplementation((page: number) => Promise.resolve({
    ...populatedPage,
    page,
    totalItems: 24,
    totalPages: 2,
  }));
  const { user } = renderFavorites();

  const pagination = await screen.findByRole('navigation', { name: '收藏分页' });
  expect(within(pagination).getByRole('button', { name: '上一页' })).toBeDisabled();
  await user.click(within(pagination).getByRole('button', { name: '下一页' }));

  expect(screen.getByTestId('location')).toHaveTextContent('/favorites?page=1');
  await waitFor(() => expect(fetchFavoritePageMock).toHaveBeenLastCalledWith(1));
  await waitFor(() => {
    expect(
      within(screen.getByRole('navigation', { name: '收藏分页' }))
        .getByRole('button', { name: '下一页' }),
    ).toBeDisabled();
  });
});

test('removing a favorite refreshes the personal page through broad invalidation', async () => {
  fetchFavoritePageMock
    .mockResolvedValueOnce(populatedPage)
    .mockImplementation(() => new Promise(() => undefined));
  const { user } = renderFavorites();

  await user.click(await screen.findByRole('button', { name: '取消收藏' }));

  await waitFor(() => expect(removeFavoriteMock).toHaveBeenCalledWith('channa-argus'));
  await waitFor(() => expect(fetchFavoritePageMock).toHaveBeenCalledTimes(2));
});

test('returns to the last valid page after removing its final favorite', async () => {
  const lastPage = {
    ...populatedPage,
    page: 1,
    totalItems: 13,
    totalPages: 2,
  };
  const nowOutOfRange = {
    ...populatedPage,
    items: [],
    page: 1,
    totalItems: 12,
    totalPages: 1,
  };
  const validFirstPage = {
    ...populatedPage,
    page: 0,
    totalItems: 12,
    totalPages: 1,
  };
  fetchFavoritePageMock
    .mockResolvedValueOnce(lastPage)
    .mockResolvedValueOnce(nowOutOfRange)
    .mockResolvedValueOnce(validFirstPage);
  const { user } = renderFavorites({ initialEntry: '/favorites?page=1' });

  await user.click(await screen.findByRole('button', { name: '取消收藏' }));

  await waitFor(() => expect(fetchFavoritePageMock).toHaveBeenLastCalledWith(0));
  expect(screen.getByTestId('location')).toHaveTextContent('/favorites');
  expect(screen.queryByRole('heading', { name: '还没有收藏鱼类' })).not.toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
});

test('the protected page redirects an anonymous session before loading favorites', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  renderFavorites({ protectedRoute: true });

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(fetchFavoritePageMock).not.toHaveBeenCalled();
});

test('the protected page hides retained favorites after a cached session expires', async () => {
  const session = deferred<User>();
  fetchCurrentUserMock.mockReturnValue(session.promise);
  renderFavorites({
    protectedRoute: true,
    cachedUser: authenticatedUser,
    cachedFavoritePage: populatedPage,
  });

  expect(screen.getByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  session.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '乌鳢' })).not.toBeInTheDocument();
});

test('favorites page 401 expires a fresh session and redirects without private cache', async () => {
  const unauthorized = new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });
  fetchCurrentUserMock.mockRejectedValue(unauthorized);
  fetchFavoritePageMock.mockRejectedValue(unauthorized);
  const { queryClient } = renderFavorites({
    protectedRoute: true,
    cachedUser: authenticatedUser,
    cachedUserUpdatedAt: Date.now(),
    cachedFavoritePage: populatedPage,
  });

  expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  expect(fetchFavoritePageMock).toHaveBeenCalledTimes(1);
  expect(fetchCurrentUserMock).toHaveBeenCalledTimes(1);
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(
    queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY })
      .every(([, data]) => data === undefined),
  ).toBe(true);
  expect(screen.queryByRole('heading', { name: '乌鳢' })).not.toBeInTheDocument();
});
