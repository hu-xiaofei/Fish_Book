import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { FAVORITES_QUERY_KEY } from '../../favorites/api/favoritesApi';
import {
  catchDetailQueryKey,
  catchPageQueryKey,
} from '../api/catchRecordsApi';
import type { CatchRecordDetail, CatchRecordPage } from '../model/types';
import { CatchEditPage } from './CatchEditPage';

const {
  fetchCatchRecordMock,
  fetchFishPageMock,
  updateCatchRecordMock,
} = vi.hoisted(() => ({
  fetchCatchRecordMock: vi.fn(),
  fetchFishPageMock: vi.fn(),
  updateCatchRecordMock: vi.fn(),
}));

vi.mock('../api/catchRecordsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchRecordsApi')>();
  return {
    ...actual,
    fetchCatchRecord: fetchCatchRecordMock,
    updateCatchRecord: updateCatchRecordMock,
  };
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
  lengthCm: 42.5,
  weightG: 1350,
  method: '路亚',
  notes: '傍晚近岸中鱼',
  hasPhoto: false,
  createdAt: '2026-08-20T08:00:00Z',
  updatedAt: '2026-08-20T09:00:00Z',
};

const updatedCatch: CatchRecordDetail = {
  ...savedCatch,
  location: '新地点',
  lengthCm: null,
  weightG: null,
  method: null,
  notes: null,
  updatedAt: '2026-08-20T10:00:00Z',
};

const savedSummary = {
  id: savedCatch.id,
  fishSlug: savedCatch.fishSlug,
  commonNameZh: savedCatch.commonNameZh,
  caughtOn: savedCatch.caughtOn,
  location: savedCatch.location,
  lengthCm: savedCatch.lengthCm,
  weightG: savedCatch.weightG,
  method: savedCatch.method,
  hasPhoto: savedCatch.hasPhoto,
  createdAt: savedCatch.createdAt,
  updatedAt: savedCatch.updatedAt,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderCatchEdit({
  initialEntry = '/catches/31/edit',
  cachedPrivateData = false,
}: {
  initialEntry?: string;
  cachedPrivateData?: boolean;
} = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0 },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
    id: 1, email: 'angler@example.com', nickname: 'River', role: 'USER',
  });
  if (cachedPrivateData) {
    const page: CatchRecordPage = {
      items: [savedSummary], page: 0, size: 20, totalItems: 1, totalPages: 1,
    };
    queryClient.setQueryData(catchPageQueryKey(0), page);
    queryClient.setQueryData(catchDetailQueryKey(30), { ...savedCatch, id: 30 });
    queryClient.setQueryData(FAVORITES_QUERY_KEY, { items: [{ fishSlug: 'channa-argus' }] });
  }

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/catches/:id/edit" element={<><CatchEditPage /><LocationProbe /></>} />
        <Route path="/catches/:id" element={<LocationProbe />} />
        <Route path="/login" element={<LocationProbe />} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

function notFoundError() {
  return new ApiError(404, {
    code: 'CATCH_RECORD_NOT_FOUND', message: 'record does not exist', fieldErrors: [], requestId: 'test-request',
  });
}

beforeEach(() => {
  fetchCatchRecordMock.mockReset();
  fetchFishPageMock.mockReset();
  updateCatchRecordMock.mockReset();
  fetchCatchRecordMock.mockResolvedValue(savedCatch);
  fetchFishPageMock.mockResolvedValue({
    items: [{
      slug: 'channa-argus', commonNameZh: '乌鳢', scientificName: 'Channa argus',
      familyNameZh: '鳢科', aliases: [], habitats: [], imagePath: '/fish.jpg', imageAltText: '乌鳢',
    }],
    page: 0, size: 12, totalItems: 1, totalPages: 1,
  });
});

test('loads saved values into the reusable form', async () => {
  renderCatchEdit();

  expect(await screen.findByRole('heading', { name: '编辑钓获记录' })).toBeInTheDocument();
  expect(screen.getByLabelText('鱼种')).toHaveValue('channa-argus');
  expect(screen.getByLabelText('钓获日期')).toHaveValue('2026-08-20');
  expect(screen.getByLabelText('地点')).toHaveValue('城郊水库');
  expect(screen.getByLabelText('长度（cm）')).toHaveValue(42.5);
  expect(screen.getByLabelText('重量（g）')).toHaveValue(1350);
  expect(screen.getByLabelText('钓法')).toHaveValue('路亚');
  expect(screen.getByLabelText('备注')).toHaveValue('傍晚近岸中鱼');
});

test('shows loading while record or catalog data is pending', () => {
  fetchCatchRecordMock.mockImplementation(() => new Promise(() => undefined));
  renderCatchEdit();

  expect(screen.getByText('正在加载钓获记录…')).toHaveAttribute('role', 'status');
});

test('shows a safe detail error and retries before editing', async () => {
  fetchCatchRecordMock
    .mockRejectedValueOnce(new Error('detail failed at db.internal'))
    .mockRejectedValueOnce(new Error('detail failed at db.internal'))
    .mockRejectedValueOnce(new Error('detail failed at db.internal'))
    .mockResolvedValueOnce(savedCatch);
  const { user } = renderCatchEdit();

  const status = await screen.findByText('加载钓获记录失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
  await user.click(screen.getByRole('button', { name: '重试' }));

  expect(await screen.findByRole('heading', { name: '编辑钓获记录' })).toBeInTheDocument();
  expect(fetchCatchRecordMock).toHaveBeenCalledTimes(4);
});

test('uses the safe missing state for malformed IDs and owned-record 404s', async () => {
  const malformed = renderCatchEdit({ initialEntry: '/catches/nope/edit' });
  expect(await screen.findByRole('heading', { name: '没有找到钓获记录' })).toBeInTheDocument();
  expect(fetchCatchRecordMock).not.toHaveBeenCalled();
  malformed.unmount();

  fetchCatchRecordMock.mockRejectedValue(notFoundError());
  renderCatchEdit();
  expect(await screen.findByRole('heading', { name: '没有找到钓获记录' })).toBeInTheDocument();
});

test('updates all editable fields including empty optional values and returns to detail', async () => {
  updateCatchRecordMock.mockResolvedValue(updatedCatch);
  const { user, queryClient } = renderCatchEdit({ cachedPrivateData: true });

  await screen.findByRole('heading', { name: '编辑钓获记录' });
  await user.clear(screen.getByLabelText('地点'));
  await user.type(screen.getByLabelText('地点'), ' 新地点 ');
  await user.clear(screen.getByLabelText('长度（cm）'));
  await user.clear(screen.getByLabelText('重量（g）'));
  await user.clear(screen.getByLabelText('钓法'));
  await user.clear(screen.getByLabelText('备注'));
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/catches/31'));
  expect(updateCatchRecordMock).toHaveBeenCalledWith(31, {
    fishSlug: 'channa-argus', caughtOn: '2026-08-20', location: '新地点',
    lengthCm: null, weightG: null, method: null, notes: null,
  });
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toEqual(updatedCatch);
  expect(queryClient.getQueryState(catchPageQueryKey(0))?.isInvalidated).toBe(true);
});

test('keeps entered values with a safe retryable update failure', async () => {
  updateCatchRecordMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR', message: 'update failed at mysql.internal', fieldErrors: [], requestId: 'test-request',
  }));
  const { user } = renderCatchEdit();

  await screen.findByRole('heading', { name: '编辑钓获记录' });
  await user.clear(screen.getByLabelText('地点'));
  await user.type(screen.getByLabelText('地点'), '河湾');
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  const status = await screen.findByText('保存记录失败，请稍后重试');
  expect(status).not.toHaveTextContent('mysql.internal');
  expect(screen.getByLabelText('地点')).toHaveValue('河湾');
});

test('confirmed update 401 clears private caches before routing to login', async () => {
  updateCatchRecordMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  const { user, queryClient } = renderCatchEdit({ cachedPrivateData: true });

  await screen.findByRole('heading', { name: '编辑钓获记录' });
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: ['catches'] }).every(([, data]) => data === undefined))
    .toBe(true);
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY }).every(([, data]) => data === undefined))
    .toBe(true);
});
