import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { favoriteStatusQueryKey } from '../../favorites/api/favoritesApi';
import type { FishDetail } from '../model/types';
import { FishDetailPage } from './FishDetailPage';

const { fetchCurrentUserMock, fetchFavoriteStatusesMock, fetchFishDetailMock } = vi.hoisted(() => ({
  fetchCurrentUserMock: vi.fn(),
  fetchFavoriteStatusesMock: vi.fn(),
  fetchFishDetailMock: vi.fn(),
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
    fetchFishDetail: fetchFishDetailMock,
  };
});

const channaArgusDetail: FishDetail = {
  slug: 'channa-argus',
  commonNameZh: '乌鳢',
  scientificName: 'Channa argus',
  familyNameZh: '鳢科',
  familyScientificName: 'Channidae',
  genusNameZh: '鳢属',
  genusScientificName: 'Channa',
  aliases: ['黑鱼', '生鱼'],
  habitats: [
    { code: 'LAKE', labelZh: '湖泊' },
    { code: 'POND', labelZh: '池塘' },
  ],
  appearance: '身体延长，头部宽扁，体侧有深色斑纹。',
  sizeDescription: '常见个体为中型淡水鱼。',
  habitatDescription: '常见于水草较多的静水或缓流水域。',
  distribution: '分布于中国多地淡水水系。',
  description: '乌鳢是适应力较强的淡水鱼。',
  image: {
    path: '/images/fish/channa-argus.jpg',
    altText: '乌鳢（Channa argus）',
    sourceUrl: 'https://commons.wikimedia.org/wiki/File:Channa_argus_01.jpg',
    author: 'Σ64',
    licenseName: 'CC BY 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  },
};

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'River',
  role: 'USER',
};

function renderDetail(
  initialEntry: string | { pathname: string; state?: { from: string } },
  queryRetry: boolean | number = false,
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: queryRetry, retryDelay: 0 } },
  });
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/fish/:slug" element={<FishDetailPage />} />
            <Route path="/" element={<h1>图鉴</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

beforeEach(() => {
  fetchCurrentUserMock.mockReset();
  fetchFavoriteStatusesMock.mockReset();
  fetchFishDetailMock.mockReset();
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
  fetchFavoriteStatusesMock.mockResolvedValue({
    items: [{ fishSlug: 'channa-argus', favorited: false }],
  });
  fetchFishDetailMock.mockResolvedValue(channaArgusDetail);
});

test('loads the favorite status once for the detail fish', async () => {
  renderDetail('/fish/channa-argus');

  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  await waitFor(() => expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(1));
  expect(fetchFavoriteStatusesMock).toHaveBeenCalledWith(['channa-argus']);
});

test('does not retry an unauthorized detail favorite status request', async () => {
  fetchFavoriteStatusesMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { queryClient } = renderDetail('/fish/channa-argus', 2);

  await waitFor(() => {
    expect(
      queryClient.getQueryState(favoriteStatusQueryKey(['channa-argus']))?.status,
    ).toBe('error');
  });
  expect(fetchFavoriteStatusesMock).toHaveBeenCalledTimes(1);
});

test('renders classification, content, and visible image attribution', async () => {
  fetchFishDetailMock.mockResolvedValue(channaArgusDetail);
  renderDetail('/fish/channa-argus');

  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(screen.getByText('Channa argus')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /图片来源/ })).toHaveAttribute(
    'href', channaArgusDetail.image.sourceUrl,
  );
  expect(screen.getByRole('link', { name: /许可证/ })).toHaveAttribute(
    'href', channaArgusDetail.image.licenseUrl,
  );
});

test('shows a dedicated not-found state for a 404', async () => {
  fetchFishDetailMock.mockRejectedValue(new ApiError(404, {
    code: 'FISH_NOT_FOUND',
    message: 'Fish was not found',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  renderDetail('/fish/missing-fish');

  expect(await screen.findByRole('heading', { name: '没有找到这种鱼' })).toBeInTheDocument();
});

test('shows a loading status while the detail request is unresolved', async () => {
  fetchFishDetailMock.mockImplementation(() => new Promise(() => undefined));
  renderDetail('/fish/channa-argus');

  expect(await screen.findByRole('status')).toHaveTextContent('正在加载鱼类资料…');
});

test('shows a safe generic error and retries the detail request', async () => {
  fetchFishDetailMock.mockRejectedValue(new ApiError(500, {
    code: 'DATABASE_ERROR',
    message: 'database connection failed at db.internal',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const user = userEvent.setup();
  renderDetail('/fish/channa-argus');

  const status = await screen.findByText('加载鱼类资料失败，请稍后重试');
  expect(status).not.toHaveTextContent('database connection failed');
  await user.click(screen.getByRole('button', { name: '重试' }));
  await waitFor(() => expect(fetchFishDetailMock).toHaveBeenCalledTimes(2));
});

test('renders the image, aliases, and habitats for a successful detail', async () => {
  renderDetail('/fish/channa-argus');

  expect(await screen.findByAltText('乌鳢（Channa argus）')).toHaveAttribute(
    'src',
    '/images/fish/channa-argus.jpg',
  );
  expect(screen.getByText('黑鱼、生鱼')).toBeInTheDocument();
  expect(screen.getByText('湖泊、池塘')).toBeInTheDocument();
});

test('replaces a failed detail image with an accessible fallback', async () => {
  renderDetail('/fish/channa-argus');

  const image = await screen.findByAltText('乌鳢（Channa argus）');
  fireEvent.error(image);

  expect(image).not.toBeInTheDocument();
  expect(screen.getByRole('img', { name: '乌鳢（Channa argus）' })).toBeInTheDocument();
});

test('returns to the prior catalog URL passed in navigation state', async () => {
  renderDetail({ pathname: '/fish/channa-argus', state: { from: '/?q=黑鱼&habitat=LAKE' } });

  expect(await screen.findByRole('link', { name: '返回图鉴' }))
    .toHaveAttribute('href', '/?q=黑鱼&habitat=LAKE');
});

test('returns to the catalog root for a direct detail URL', async () => {
  renderDetail('/fish/channa-argus');

  expect(await screen.findByRole('link', { name: '返回图鉴' })).toHaveAttribute('href', '/');
});
