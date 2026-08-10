import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { ProtectedRoute } from '../components/ProtectedRoute';
import { ProfilePage } from './ProfilePage';

const { logoutMock, updateNicknameMock } = vi.hoisted(() => ({
  logoutMock: vi.fn(),
  updateNicknameMock: vi.fn(),
}));

vi.mock('../api/authApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/authApi')>();
  return { ...actual, logout: logoutMock };
});

vi.mock('../api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/currentUser')>();
  return { ...actual, updateNickname: updateNicknameMock };
});

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'Wall_E',
  role: 'USER',
};

function renderAuthenticatedProfile() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData(['current-user'], authenticatedUser);

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/profile']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    user: userEvent.setup(),
    queryClient,
    ...render(
      <Routes>
        <Route
          path="/profile"
          element={(
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          )}
        />
        <Route path="/login" element={<h1>登录</h1>} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  logoutMock.mockReset();
  updateNicknameMock.mockReset();
});

test('updates nickname and current-user cache', async () => {
  updateNicknameMock.mockResolvedValue({
    ...authenticatedUser,
    nickname: 'River',
  } satisfies User);
  const { user, queryClient } = renderAuthenticatedProfile();

  await user.clear(screen.getByLabelText('昵称'));
  await user.type(screen.getByLabelText('昵称'), ' River ');
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByDisplayValue('River')).toBeInTheDocument();
  expect(updateNicknameMock).toHaveBeenCalledWith('River');
  expect(queryClient.getQueryData<User>(['current-user'])?.nickname).toBe('River');
});

test('logout clears cache and returns to login after server success', async () => {
  logoutMock.mockResolvedValue(undefined);
  const { user, queryClient } = renderAuthenticatedProfile();

  await user.click(screen.getByRole('button', { name: '退出登录' }));

  expect(await screen.findByText('登录')).toBeInTheDocument();
  expect(queryClient.getQueryData(['current-user'])).toBeUndefined();
});

test('failed logout keeps the authenticated cache and shows a safe error', async () => {
  logoutMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'Redis session delete failed at cache.internal',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user, queryClient } = renderAuthenticatedProfile();

  await user.click(screen.getByRole('button', { name: '退出登录' }));

  const status = await screen.findByRole('status');
  expect(status).toHaveTextContent('退出登录失败，请稍后重试');
  expect(status).not.toHaveTextContent('Redis session');
  expect(queryClient.getQueryData(['current-user'])).toEqual(authenticatedUser);
  expect(screen.queryByText('登录')).not.toBeInTheDocument();
});

test('blocks a blank nickname client-side', async () => {
  const { user } = renderAuthenticatedProfile();
  await user.clear(screen.getByLabelText('昵称'));
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByText('请输入昵称')).toBeInTheDocument();
  expect(updateNicknameMock).not.toHaveBeenCalled();
});

test('blocks nicknames longer than 50 characters after trimming', async () => {
  const { user } = renderAuthenticatedProfile();
  await user.clear(screen.getByLabelText('昵称'));
  await user.type(screen.getByLabelText('昵称'), ` ${'鱼'.repeat(51)} `);
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByText('昵称最多 50 个字符')).toBeInTheDocument();
  expect(updateNicknameMock).not.toHaveBeenCalled();
});

test('shows backend nickname validation error', async () => {
  updateNicknameMock.mockRejectedValue(new ApiError(400, {
    code: 'INVALID_NICKNAME',
    message: '昵称不可用',
    fieldErrors: [{ field: 'nickname', message: '昵称不可用' }],
    requestId: 'test-request',
  }));
  const { user } = renderAuthenticatedProfile();

  await user.clear(screen.getByLabelText('昵称'));
  await user.type(screen.getByLabelText('昵称'), 'Blocked');
  await user.click(screen.getByRole('button', { name: '保存' }));

  expect(await screen.findByText('昵称不可用')).toBeInTheDocument();
});

test('ordinary profile errors do not expose backend details', async () => {
  updateNicknameMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'SQL constraint users_nickname failed at db.internal',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderAuthenticatedProfile();

  await user.clear(screen.getByLabelText('昵称'));
  await user.type(screen.getByLabelText('昵称'), 'River');
  await user.click(screen.getByRole('button', { name: '保存' }));

  const status = await screen.findByRole('status');
  expect(status).toHaveTextContent('保存失败，请稍后重试');
  expect(status).not.toHaveTextContent('SQL constraint');
});

