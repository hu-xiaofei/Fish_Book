import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { CatchNewPage } from './CatchNewPage';

const { expireIfUnauthorizedMock, fetchFishPageMock } = vi.hoisted(() => ({
  expireIfUnauthorizedMock: vi.fn(),
  fetchFishPageMock: vi.fn(),
}));

vi.mock('../../auth/hooks/useExpireSessionOnUnauthorized', () => ({
  useSessionExpiry: () => ({
    sessionExpired: false,
    expireIfUnauthorized: expireIfUnauthorizedMock,
  }),
}));

vi.mock('../../catalog/api/catalogApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../catalog/api/catalogApi')>();
  return { ...actual, fetchFishPage: fetchFishPageMock };
});

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, retryDelay: 0 } },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
    id: 1, email: 'angler@example.com', nickname: 'River', role: 'USER',
  });

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/catches/new']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return render(
    <Routes>
      <Route path="/catches/new" element={<><CatchNewPage /><LocationProbe /></>} />
      <Route path="/login" element={<LocationProbe />} />
    </Routes>,
    { wrapper: Wrapper },
  );
}

beforeEach(() => {
  expireIfUnauthorizedMock.mockReset();
  fetchFishPageMock.mockReset();
});

test('waits for session-expiry state before redirecting a catalog 401', async () => {
  fetchFishPageMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'test-request',
  }));
  renderPage();

  expect(await screen.findByText('加载鱼种失败，请稍后重试')).toBeInTheDocument();
  expect(expireIfUnauthorizedMock).toHaveBeenCalled();
  expect(screen.getByTestId('location')).toHaveTextContent('/catches/new');
});
