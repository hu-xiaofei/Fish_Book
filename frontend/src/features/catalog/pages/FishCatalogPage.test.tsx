import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { deferred } from '../../../test/renderWithProviders';
import { catchDetailQueryKey, catchPageQueryKey } from '../../catchlog/api/catchRecordsApi';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import {
  FAVORITES_QUERY_KEY,
  favoriteStatusQueryKey,
} from '../../favorites/api/favoritesApi';
import type { FishFilterOptions, FishPage, FishSummary } from '../model/types';
import { FishCatalogPage } from './FishCatalogPage';

const { fetchCurrentUserMock, fetchFavoriteStatusesMock, fetchFishFiltersMock, fetchFishPageMock } = vi.hoisted(() => ({
  fetchCurrentUserMock: vi.fn(),
  fetchFavoriteStatusesMock: vi.fn(),
  fetchFishFiltersMock: vi.fn(),
  fetchFishPageMock: vi.fn(),
}));

vi.mock('../../auth/api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

vi.mock('../../favorites/api/favoritesApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../favorites/api/favoritesApi')>();
  return { ...actual, fetchFavoriteStatuses: fetchFavoriteStatusesMock };
});

vi.mock('../api/catalogApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catalogApi')>();
  return {
    ...actual,
    fetchFishFilterOptions: fetchFishFiltersMock,
    fetchFishPage: fetchFishPageMock,
  };
});

const channaSummary: FishSummary = {
  slug: 'channa-argus',
  commonNameZh: '乌鳢',
  scientificName: 'Channa argus',
  familyNameZh: '鳢科',
  aliases: ['黑鱼'],
  habitats: [{ code: 'LAKE', labelZh: '湖泊' }],
  imagePath: '/images/fish/channa-argus.jpg',
  imageAltText: '乌鳢（Channa argus）',
};

const pageWith12Fish: FishPage = {
  items: [
    channaSummary,
    ...Array.from({ length: 11 }, (_, index) => ({
      ...channaSummary,
      slug: `fixture-fish-${index + 1}`,
      commonNameZh: `测试鱼${index + 1}`,
    })),
  ],
  page: 0,
  size: 12,
  totalItems: 12,
  totalPages: 1,
};

const filterOptions: FishFilterOptions = {
  families: ['鳢科', '鲤科'],
  habitats: [
    { code: 'RIVER', labelZh: '江河' },
    { code: 'LAKE', labelZh: '湖泊' },
  ],
};

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'River',
  role: 'USER',
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function SearchNavigation() {
  return <Link to="/?q=%E9%B2%A4">导航到鲤</Link>;
}

function renderCatalog(
  initialEntry: string,
  queryRetry: boolean | number = false,
  cachedUser?: User,
  cachedUserUpdatedAt = 1,
  prepareQueryClient?: (queryClient: QueryClient) => void,
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: queryRetry, retryDelay: 0 } },
  });
  if (cachedUser) {
    queryClient.setQueryData(
      CURRENT_USER_QUERY_KEY,
      cachedUser,
      { updatedAt: cachedUserUpdatedAt },
    );
  }
  prepareQueryClient?.(queryClient);
  const user = userEvent.setup();
  return {
    queryClient,
    user,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/" element={<><FishCatalogPage /><LocationProbe /><SearchNavigation /></>} />
            <Route path="/favorites" element={<><h1>收藏页</h1><LocationProbe /></>} />
            <Route path="/login" element={<><h1>登录页</h1><LocationProbe /></>} />
            <Route path="/register" element={<><h1>注册页</h1><LocationProbe /></>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

beforeEach(() => {
  fetchCurrentUserMock.mockReset();
  fetchFavoriteStatusesMock.mockReset();
  fetchFishPageMock.mockReset();
  fetchFishFiltersMock.mockReset();
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
  fetchFavoriteStatusesMock.mockResolvedValue({
    items: pageWith12Fish.items.map((fish) => ({ fishSlug: fish.slug, favorited: false })),
  });
  fetchFishPageMock.mockResolvedValue(pageWith12Fish);
  fetchFishFiltersMock.mockResolvedValue(filterOptions);
});

test('loads favorite statuses for all 12 visible fish in one batch request', async () => {
  renderCatalog('/');

  expect(await screen.findByRole('link', { name: /查看乌鳢详情/ })).toBeInTheDocument();
  await waitFor(() => expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(1));
  expect(fetchFavoriteStatusesMock).toHaveBeenCalledWith(
    pageWith12Fish.items.map((fish) => fish.slug),
  );
});

test('does not present unknown catalog favorite states while status is loading', async () => {
  fetchFavoriteStatusesMock.mockReturnValue(deferred().promise);

  renderCatalog('/');

  expect(await screen.findByRole('status', { name: '正在加载收藏状态' }))
    .toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '收藏' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '取消收藏' })).not.toBeInTheDocument();
});

test('catalog status outage hides unknown states and retries as one batch', async () => {
  fetchFavoriteStatusesMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'status unavailable',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderCatalog('/');

  expect(await screen.findByRole('status', { name: '收藏状态加载失败' }))
    .toHaveTextContent('加载收藏状态失败，请稍后重试');
  expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(3);
  expect(screen.queryByRole('button', { name: '收藏' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '取消收藏' })).not.toBeInTheDocument();

  fetchFavoriteStatusesMock.mockResolvedValueOnce({
    items: pageWith12Fish.items.map((fish) => ({ fishSlug: fish.slug, favorited: false })),
  });
  await user.click(screen.getByRole('button', { name: '重试收藏状态' }));

  await waitFor(() => expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(4));
  expect(fetchFavoriteStatusesMock).toHaveBeenLastCalledWith(
    pageWith12Fish.items.map((fish) => fish.slug),
  );
  expect(await screen.findAllByRole('button', { name: '收藏' })).toHaveLength(12);
});

test('authenticated catalog navigation opens personal favorites', async () => {
  const { user } = renderCatalog('/');

  await user.click(await screen.findByRole('link', { name: '我的收藏' }));

  expect(screen.getByRole('heading', { name: '收藏页' })).toBeInTheDocument();
  expect(screen.getByTestId('location')).toHaveTextContent('/favorites');
  expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '注册' })).not.toBeInTheDocument();
});

test('anonymous catalog navigation keeps login and registration without personal links', async () => {
  const session = deferred<User>();
  fetchCurrentUserMock.mockReturnValue(session.promise);
  const unauthorized = new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });

  renderCatalog('/');
  session.reject(unauthorized);

  expect(await screen.findByRole('link', { name: '登录' })).toHaveAttribute('href', '/login');
  expect(screen.getByRole('link', { name: '注册' })).toHaveAttribute('href', '/register');
  expect(screen.queryByRole('link', { name: '我的收藏' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '个人资料' })).not.toBeInTheDocument();
});

test('expired cached session replaces personal links with usable guest navigation', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderCatalog('/', false, authenticatedUser);

  await waitFor(() => expect(fetchCurrentUserMock).toHaveBeenCalledTimes(1));
  expect(await screen.findByRole('link', { name: '登录' })).toHaveAttribute('href', '/login');
  expect(screen.getByRole('link', { name: '注册' })).toHaveAttribute('href', '/register');
  expect(screen.queryByRole('link', { name: '我的收藏' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '个人资料' })).not.toBeInTheDocument();

  await user.click(screen.getByRole('link', { name: '登录' }));
  expect(screen.getByRole('heading', { name: '登录页' })).toBeInTheDocument();
  expect(screen.getByTestId('location')).toHaveTextContent('/login');
});

test('confirmed expired session ignores retained favorite flags and skips status refetch', async () => {
  const session = deferred<User>();
  const fishPage = deferred<FishPage>();
  fetchCurrentUserMock.mockReturnValue(session.promise);
  fetchFishPageMock.mockReturnValue(fishPage.promise);
  fetchFavoriteStatusesMock.mockImplementation(() => new Promise(() => undefined));
  const { queryClient } = renderCatalog('/', false, authenticatedUser);
  queryClient.setQueryData(
    favoriteStatusQueryKey(pageWith12Fish.items.map((fish) => fish.slug)),
    { items: [{ fishSlug: 'channa-argus', favorited: true }] },
    { updatedAt: 1 },
  );

  session.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  expect(await screen.findByRole('link', { name: '登录' })).toBeInTheDocument();
  fishPage.resolve(pageWith12Fish);

  expect(await screen.findByRole('link', { name: /查看乌鳢详情/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '取消收藏' })).not.toBeInTheDocument();
  expect(fetchFavoriteStatusesMock).not.toHaveBeenCalled();
});

test('favorite status 401 expires a fresh cached session and clears private state', async () => {
  const unauthorized = new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });
  fetchCurrentUserMock.mockRejectedValue(unauthorized);
  fetchFavoriteStatusesMock.mockRejectedValue(unauthorized);
  const statusKey = favoriteStatusQueryKey(
    pageWith12Fish.items.map((fish) => fish.slug),
  );
  const { queryClient } = renderCatalog(
    '/',
    false,
    authenticatedUser,
    Date.now(),
    (client) => {
      client.setQueryData(
        statusKey,
        { items: [{ fishSlug: 'channa-argus', favorited: true }] },
        { updatedAt: 1 },
      );
      client.setQueryData(catchPageQueryKey(0), { items: [{ id: 31 }] });
      client.setQueryData(catchDetailQueryKey(31), { id: 31, notes: '仅用户 A 可见' });
    },
  );

  expect(await screen.findByRole('link', { name: '登录' })).toBeInTheDocument();
  expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(1);
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(
    queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY })
      .every(([, data]) => data === undefined),
  ).toBe(true);
  expect(queryClient.getQueriesData({ queryKey: ['catches'] })).toEqual([]);
  expect(screen.queryByRole('button', { name: '取消收藏' })).not.toBeInTheDocument();
});

test('does not flash guest navigation while the session lookup is pending', () => {
  fetchCurrentUserMock.mockImplementation(() => new Promise(() => undefined));

  renderCatalog('/');

  expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '注册' })).not.toBeInTheDocument();
});

test('does not present guest navigation after a transient session lookup error', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'session database unavailable',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  renderCatalog('/');

  await waitFor(() => expect(fetchCurrentUserMock).toHaveBeenCalledTimes(3));
  expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: '注册' })).not.toBeInTheDocument();
});

test('does not retry an unauthorized favorite status request', async () => {
  const unauthorized = new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });
  fetchCurrentUserMock.mockRejectedValue(unauthorized);
  fetchFavoriteStatusesMock.mockRejectedValue(unauthorized);
  renderCatalog('/', 2, authenticatedUser, Date.now());

  expect(await screen.findByRole('link', { name: '登录' })).toBeInTheDocument();
  expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(1);
});

test('renders cards and hides pagination for one page', async () => {
  fetchFishPageMock.mockResolvedValue(pageWith12Fish);
  fetchFishFiltersMock.mockResolvedValue(filterOptions);
  renderCatalog('/');

  expect(await screen.findByRole('link', { name: /查看乌鳢详情/ })).toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: '图鉴分页' })).not.toBeInTheDocument();
});

test('submits a trimmed search and resets page', async () => {
  const { user } = renderCatalog('/?page=2');

  await user.type(await screen.findByRole('searchbox', { name: '搜索鱼类' }), '  黑鱼  ');
  await user.click(screen.getByRole('button', { name: '搜索' }));

  expect(screen.getByTestId('location')).toHaveTextContent(
    '/?q=%E9%BB%91%E9%B1%BC',
  );
});

test('changing habitat keeps other filters and resets page', async () => {
  const { user } = renderCatalog('/?q=鲤&family=鲤科&page=2');

  await screen.findByRole('option', { name: '湖泊' });
  await user.selectOptions(screen.getByLabelText('栖息环境'), 'LAKE');

  expect(screen.getByTestId('location')).toHaveTextContent(
    '/?q=%E9%B2%A4&family=%E9%B2%A4%E7%A7%91&habitat=LAKE',
  );
});

test('shows loading status while the list request is unresolved', async () => {
  fetchFishPageMock.mockImplementation(() => new Promise(() => undefined));
  renderCatalog('/');

  expect(await within(screen.getByRole('main')).findByRole('status'))
    .toHaveTextContent('正在加载鱼类…');
});

test('clears filters from the empty search state', async () => {
  fetchFishPageMock.mockResolvedValue({
    ...pageWith12Fish,
    items: [],
    totalItems: 0,
  });
  const { user } = renderCatalog('/?q=missing');

  expect(await screen.findByRole('heading', { name: '没有找到匹配的鱼类' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '清除筛选' }));

  expect(screen.getByTestId('location')).toHaveTextContent('/');
});

test('shows a safe error and retries a failed list request', async () => {
  fetchFishPageMock.mockRejectedValue(new Error('SQL fish table failed at db.internal'));
  const { user } = renderCatalog('/');

  const status = await screen.findByText('加载鱼类失败，请稍后重试');
  expect(status).toHaveTextContent('加载鱼类失败，请稍后重试');
  expect(status).not.toHaveTextContent('SQL fish table');
  await user.click(screen.getByRole('button', { name: '重试' }));
  await waitFor(() => expect(fetchFishPageMock).toHaveBeenCalledTimes(2));
});

test('paginates forward and disables the previous button on the first page', async () => {
  fetchFishPageMock.mockResolvedValue({
    ...pageWith12Fish,
    totalItems: 24,
    totalPages: 2,
  });
  const { user } = renderCatalog('/');

  const pagination = await screen.findByRole('navigation', { name: '图鉴分页' });
  expect(within(pagination).getByRole('button', { name: '上一页' })).toBeDisabled();
  expect(within(pagination).getByRole('button', { name: '下一页' })).toBeEnabled();
  await user.click(within(pagination).getByRole('button', { name: '下一页' }));

  expect(screen.getByTestId('location')).toHaveTextContent('/?page=1');
});

test('replaces a failed fish image with an accessible fallback', async () => {
  fetchFishPageMock.mockResolvedValue({
    ...pageWith12Fish,
    items: [channaSummary],
    totalItems: 1,
  });
  renderCatalog('/');

  const [image] = await screen.findAllByAltText('乌鳢（Channa argus）');
  fireEvent.error(image);

  expect(image).not.toBeInTheDocument();
  expect(screen.getByRole('img', { name: '乌鳢（Channa argus）' })).toBeInTheDocument();
});

test('resynchronizes the draft search value after navigation', async () => {
  const { user } = renderCatalog('/?q=黑鱼');

  expect(await screen.findByRole('searchbox', { name: '搜索鱼类' })).toHaveValue('黑鱼');
  await user.click(screen.getByRole('link', { name: '导航到鲤' }));

  expect(screen.getByRole('searchbox', { name: '搜索鱼类' })).toHaveValue('鲤');
});
