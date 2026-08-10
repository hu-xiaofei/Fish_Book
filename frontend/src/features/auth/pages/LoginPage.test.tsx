import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { LoginPage } from './LoginPage';

const { loginMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
}));

vi.mock('../api/authApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/authApi')>();
  return { ...actual, login: loginMock };
});

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/login']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    user: userEvent.setup(),
    queryClient,
    ...render(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/profile" element={<h1>个人资料</h1>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

async function fillLoginForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
}

beforeEach(() => {
  loginMock.mockReset();
});

test('successful login caches the user without browser storage', async () => {
  loginMock.mockResolvedValue({
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  } satisfies User);
  const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
  const { user, queryClient } = renderLoginPage();

  await fillLoginForm(user);
  await user.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByText('个人资料')).toBeInTheDocument();
  expect(queryClient.getQueryData(['current-user'])).toMatchObject({ id: 1 });
  expect(storageSpy).not.toHaveBeenCalled();
  storageSpy.mockRestore();
});

test('invalid credentials show the server message', async () => {
  loginMock.mockRejectedValue(new ApiError(401, {
    code: 'INVALID_CREDENTIALS',
    message: '邮箱或密码错误',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderLoginPage();

  await fillLoginForm(user);
  await user.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByText('邮箱或密码错误')).toBeInTheDocument();
});

test('ordinary login errors do not expose backend details', async () => {
  loginMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'SQL users password hash failed at db.internal',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderLoginPage();

  await fillLoginForm(user);
  await user.click(screen.getByRole('button', { name: '登录' }));

  const status = await screen.findByRole('status');
  expect(status).toHaveTextContent('登录失败，请稍后重试');
  expect(status).not.toHaveTextContent('SQL users');
  expect(screen.queryByText(/db\.internal/)).not.toBeInTheDocument();
});

