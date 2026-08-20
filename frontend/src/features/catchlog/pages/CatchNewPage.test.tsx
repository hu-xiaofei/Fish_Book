import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { deferred } from '../../../test/renderWithProviders';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { clearSessionScopedQueries } from '../../auth/api/sessionCache';
import { catchDetailQueryKey, catchPageQueryKey } from '../api/catchRecordsApi';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import type { CatchRecordDetail, CatchRecordPage } from '../model/types';
import { CatchNewPage } from './CatchNewPage';

const { createCatchRecordMock, fetchFishPageMock } = vi.hoisted(() => ({
  createCatchRecordMock: vi.fn(),
  fetchFishPageMock: vi.fn(),
}));

vi.mock('../api/catchRecordsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchRecordsApi')>();
  return { ...actual, createCatchRecord: createCatchRecordMock };
});

vi.mock('../../catalog/api/catalogApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../catalog/api/catalogApi')>();
  return { ...actual, fetchFishPage: fetchFishPageMock };
});

const savedCatch: CatchRecordDetail = {
  id: 31,
  fishSlug: 'channa-argus',
  commonNameZh: '乌鳢',
  caughtOn: '2026-08-20',
  location: '城郊水库',
  lengthCm: null,
  weightG: null,
  method: null,
  notes: null,
  hasPhoto: false,
  createdAt: '2026-08-20T08:00:00Z',
  updatedAt: '2026-08-20T08:00:00Z',
};

const emptyCatchPage: CatchRecordPage = {
  items: [], page: 0, size: 20, totalItems: 0, totalPages: 0,
};

const userBCatchPage: CatchRecordPage = {
  items: [{
    id: 99, fishSlug: 'cyprinus-carpio', commonNameZh: '鲤', caughtOn: '2026-08-21',
    location: '用户 B 的钓点', lengthCm: null, weightG: null, method: null,
    hasPhoto: false, createdAt: '2026-08-21T08:00:00Z', updatedAt: '2026-08-21T08:00:00Z',
  }],
  page: 0, size: 20, totalItems: 1, totalPages: 1,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function AccountTransitionControl({ queryClient }: { queryClient: QueryClient }) {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => {
        // Exercise the same cache-transition boundary used by completed logout and login.
        clearSessionScopedQueries(queryClient);
        clearSessionScopedQueries(queryClient);
        queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
          id: 2, email: 'user-b@example.com', nickname: 'User B', role: 'USER',
        });
        queryClient.setQueryData(catchPageQueryKey(0), userBCatchPage);
        queryClient.setQueryData(catchDetailQueryKey(99), { ...savedCatch, id: 99, location: '用户 B 的钓点' });
        queryClient.setQueryData([...FAVORITES_QUERY_KEY, 'status', 'cyprinus-carpio'], {
          items: [{ fishSlug: 'cyprinus-carpio', favorited: true }],
        });
        navigate('/profile');
      }}
    >
      完成退出并登录用户 B
    </button>
  );
}

function renderNewPage({
  cachedCatches = false,
}: { cachedCatches?: boolean } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0 },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
    id: 1, email: 'angler@example.com', nickname: 'River', role: 'USER',
  });
  if (cachedCatches) {
    queryClient.setQueryData(catchPageQueryKey(0), emptyCatchPage);
    queryClient.setQueryData(catchDetailQueryKey(30), { ...savedCatch, id: 30 });
    queryClient.setQueryData([...FAVORITES_QUERY_KEY, 'status', 'channa-argus'], {
      items: [{ fishSlug: 'channa-argus', favorited: true }],
    });
  }

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/catches/new']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/catches/new" element={<><AccountTransitionControl queryClient={queryClient} /><CatchNewPage /><LocationProbe /></>} />
        <Route path="/catches/:id" element={<LocationProbe />} />
        <Route path="/login" element={<LocationProbe />} />
        <Route path="/profile" element={<><h1>用户 B 个人资料</h1><LocationProbe /></>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

async function completeRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(await screen.findByLabelText('鱼种'), 'channa-argus');
  await user.type(screen.getByLabelText('钓获日期'), '2026-08-20');
  await user.type(screen.getByLabelText('地点'), ' 城郊水库 ');
}

beforeEach(() => {
  createCatchRecordMock.mockReset();
  fetchFishPageMock.mockReset();
  fetchFishPageMock.mockResolvedValue({
    items: [{
      slug: 'channa-argus', commonNameZh: '乌鳢', scientificName: 'Channa argus',
      familyNameZh: '鳢科', aliases: [], habitats: [], imagePath: '/fish.jpg', imageAltText: '乌鳢',
    }],
    page: 0, size: 12, totalItems: 1, totalPages: 1,
  });
});

test('loads the full catalog as select-only fish options', async () => {
  renderNewPage();

  expect(await screen.findByRole('option', { name: '乌鳢' })).toHaveValue('channa-argus');
  expect(fetchFishPageMock).toHaveBeenCalledWith({ q: '', family: '', habitat: '', page: 0 });
  expect(screen.getByLabelText('鱼种')).toHaveProperty('tagName', 'SELECT');
});

test('shows a safe catalog loading error and retries without rendering a free-text fish field', async () => {
  fetchFishPageMock
    .mockRejectedValueOnce(new Error('catalog connection refused at db.internal'))
    .mockRejectedValueOnce(new Error('catalog connection refused at db.internal'))
    .mockRejectedValueOnce(new Error('catalog connection refused at db.internal'))
    .mockResolvedValueOnce({
      items: [], page: 0, size: 12, totalItems: 0, totalPages: 0,
    });
  const { user } = renderNewPage();

  const status = await screen.findByText('加载鱼种失败，请稍后重试');
  expect(status).toHaveTextContent('加载鱼种失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
  expect(screen.queryByLabelText('鱼种')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '重试' }));

  await waitFor(() => expect(fetchFishPageMock).toHaveBeenCalledTimes(4));
});

test('successful creation seeds detail cache, invalidates catches, and navigates to the record', async () => {
  createCatchRecordMock.mockResolvedValue(savedCatch);
  const { user, queryClient } = renderNewPage({ cachedCatches: true });

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/catches/31'));
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toEqual(savedCatch);
  expect(queryClient.getQueryState(catchPageQueryKey(0))?.isInvalidated).toBe(true);
  expect(createCatchRecordMock.mock.calls[0]?.[0]).toEqual({
    fishSlug: 'channa-argus', caughtOn: '2026-08-20', location: '城郊水库',
    lengthCm: null, weightG: null, method: null, notes: null,
  });
});

test('maps backend field errors and keeps entered values in place', async () => {
  createCatchRecordMock.mockRejectedValue(new ApiError(400, {
    code: 'INVALID_CATCH_RECORD', message: 'Fish unavailable at api.internal',
    fieldErrors: [{ field: 'location', message: '地点当前不可用' }], requestId: 'test-request',
  }));
  const { user } = renderNewPage();

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  expect(await screen.findByText('地点当前不可用')).toBeInTheDocument();
  expect(screen.getByLabelText('地点')).toHaveValue(' 城郊水库 ');
  expect(screen.getByTestId('location')).toHaveTextContent('/catches/new');
});

test('shows a safe generic save failure without backend details', async () => {
  createCatchRecordMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR', message: 'insert failed at mysql.internal',
    fieldErrors: [], requestId: 'test-request',
  }));
  const { user } = renderNewPage();

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  const status = await screen.findByText('保存记录失败，请稍后重试');
  expect(status).toHaveTextContent('保存记录失败，请稍后重试');
  expect(status).not.toHaveTextContent('mysql.internal');
  expect(screen.getByLabelText('地点')).toHaveValue(' 城郊水库 ');
});

test('confirmed save 401 clears private catch caches before routing to login', async () => {
  createCatchRecordMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  const { user, queryClient } = renderNewPage({ cachedCatches: true });

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  expect(queryClient.getQueriesData({ queryKey: ['catches'] }).every(([, data]) => data === undefined))
    .toBe(true);
});

test('confirmed catalog 401 clears private caches before rendering the login redirect', async () => {
  fetchFishPageMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  const { queryClient } = renderNewPage({ cachedCatches: true });

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));

  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: ['catches'] }).every(([, data]) => data === undefined))
    .toBe(true);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).every(([, data]) => data === undefined))
    .toBe(true);
});

test('ignores user A create success after logout and user B login', async () => {
  const saving = deferred<CatchRecordDetail>();
  createCatchRecordMock.mockReturnValue(saving.promise);
  const { user, queryClient } = renderNewPage();

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));
  await waitFor(() => expect(createCatchRecordMock).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '完成退出并登录用户 B' }));
  expect(await screen.findByRole('heading', { name: '用户 B 个人资料' })).toBeInTheDocument();

  saving.resolve(savedCatch);
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('success'));

  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
  expect(queryClient.getQueryData(catchPageQueryKey(0))).toEqual(userBCatchPage);
  expect(queryClient.getQueryState(catchPageQueryKey(0))?.isInvalidated).toBe(false);
  expect(screen.getByTestId('location')).toHaveTextContent('/profile');
});

test('ignores user A create 401 after logout and user B login', async () => {
  const saving = deferred<CatchRecordDetail>();
  createCatchRecordMock.mockReturnValue(saving.promise);
  const { user, queryClient } = renderNewPage();

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));
  await waitFor(() => expect(createCatchRecordMock).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '完成退出并登录用户 B' }));

  saving.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '用户 A 会话已过期', fieldErrors: [], requestId: 'user-a-create',
  }));
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('error'));

  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toMatchObject({ id: 2 });
  expect(queryClient.getQueryData(catchPageQueryKey(0))).toEqual(userBCatchPage);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).some(([, data]) => data !== undefined))
    .toBe(true);
  expect(screen.getByTestId('location')).toHaveTextContent('/profile');
});

test('stops create success side effects when the account changes during invalidation', async () => {
  const invalidating = deferred<void>();
  createCatchRecordMock.mockResolvedValue(savedCatch);
  const { user, queryClient } = renderNewPage();
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries').mockReturnValue(invalidating.promise);

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));
  await waitFor(() => expect(invalidateSpy).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '完成退出并登录用户 B' }));

  invalidating.resolve();
  await waitFor(() => expect(queryClient.getMutationCache().getAll().at(-1)?.state.status).toBe('success'));

  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toBeUndefined();
  expect(screen.getByTestId('location')).toHaveTextContent('/profile');
});
