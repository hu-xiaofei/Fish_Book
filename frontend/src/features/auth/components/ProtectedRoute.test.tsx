import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitForElementToBeRemoved } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { deferred } from '../../../test/renderWithProviders';
import { CURRENT_USER_QUERY_KEY } from '../api/currentUser';
import { ProtectedRoute } from './ProtectedRoute';
import { SessionNav } from './SessionNav';

const { currentUserMock } = vi.hoisted(() => ({
  currentUserMock: vi.fn(),
}));

vi.mock('../api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/currentUser')>();
  return { ...actual, fetchCurrentUser: currentUserMock };
});

function renderProtectedProfile() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retryDelay: 0 } },
  });

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/profile']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return render(
    <Routes>
      <Route
        path="/profile"
        element={(
          <ProtectedRoute>
            <h1>个人资料</h1>
          </ProtectedRoute>
        )}
      />
      <Route path="/login" element={<><h1>登录</h1><LocationProbe /></>} />
    </Routes>,
    { wrapper: Wrapper },
  );
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

beforeEach(() => {
  currentUserMock.mockReset();
});

test('protected route waits for session lookup before redirecting', async () => {
  const session = deferred<User>();
  currentUserMock.mockReturnValue(session.promise);
  renderProtectedProfile();

  expect(screen.getByRole('status', { name: '正在检查登录状态' })).toBeInTheDocument();
  expect(screen.queryByText('登录')).not.toBeInTheDocument();

  session.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  expect(await screen.findByText('登录')).toBeInTheDocument();
  expect(screen.getByTestId('location')).toHaveTextContent('/login?returnTo=%2Fprofile');
  expect(currentUserMock).toHaveBeenCalledTimes(1);
});

test('authenticated session renders the protected content', async () => {
  currentUserMock.mockResolvedValue({
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  } satisfies User);

  renderProtectedProfile();

  expect(await screen.findByText('个人资料')).toBeInTheDocument();
});

test('non-authentication lookup failures stay on a safe error state', async () => {
  currentUserMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'jdbc://db.internal details',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  renderProtectedProfile();

  const status = await screen.findByText('暂时无法确认登录状态，请稍后重试');
  expect(status).toHaveTextContent('暂时无法确认登录状态，请稍后重试');
  expect(status).not.toHaveTextContent('jdbc://db.internal');
  expect(screen.queryByText('登录')).not.toBeInTheDocument();
});

test('session navigation hides cached actions after a confirmed 401', async () => {
  const session = deferred<User>();
  currentUserMock.mockReturnValue(session.promise);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retryDelay: 0 } },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  } satisfies User, { updatedAt: 1 });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SessionNav />
      </MemoryRouter>
    </QueryClientProvider>,
  );

  expect(screen.getByText('退出登录')).toBeInTheDocument();

  session.reject(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));

  await waitForElementToBeRemoved(() => screen.queryByText('退出登录'));
});
