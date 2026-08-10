import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { deferred } from '../../../test/renderWithProviders';
import { RegisterPage } from './RegisterPage';

const { registerMock } = vi.hoisted(() => ({
  registerMock: vi.fn(),
}));

vi.mock('../api/authApi', () => ({
  register: registerMock,
}));

function LoginProbe() {
  const location = useLocation();
  const state = location.state as { message?: string } | null;

  return <p>{state?.message}</p>;
}

function renderRegisterPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/register']}>
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    user: userEvent.setup(),
    ...render(
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginProbe />} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
  await user.type(screen.getByLabelText('昵称'), 'Wall_E');
}

beforeEach(() => {
  registerMock.mockReset();
});

test('shows all required errors without calling the API', async () => {
  const { user } = renderRegisterPage();

  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(await screen.findByText('请输入有效邮箱')).toBeInTheDocument();
  expect(screen.getByText('密码至少 10 个字符')).toBeInTheDocument();
  expect(screen.getByText('请输入昵称')).toBeInTheDocument();
  expect(registerMock).not.toHaveBeenCalled();
});

test('rejects passwords longer than 128 characters', async () => {
  const { user } = renderRegisterPage();

  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'p'.repeat(129));
  await user.type(screen.getByLabelText('昵称'), 'Wall_E');
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(await screen.findByText('密码最多 128 个字符')).toBeInTheDocument();
  expect(registerMock).not.toHaveBeenCalled();
});

test('rejects nicknames longer than 50 characters after trimming', async () => {
  const { user } = renderRegisterPage();

  await user.type(screen.getByLabelText('邮箱'), 'angler@example.com');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
  await user.type(screen.getByLabelText('昵称'), ` ${'鱼'.repeat(51)} `);
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(await screen.findByText('昵称最多 50 个字符')).toBeInTheDocument();
  expect(registerMock).not.toHaveBeenCalled();
});

test('submits trimmed input without lowercasing email and navigates to login', async () => {
  registerMock.mockResolvedValue({
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  } satisfies User);
  const { user } = renderRegisterPage();

  await user.type(screen.getByLabelText('邮箱'), ' Angler@Example.COM ');
  await user.type(screen.getByLabelText('密码'), 'strong-pass');
  await user.type(screen.getByLabelText('昵称'), ' Wall_E ');
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(registerMock).toHaveBeenCalledWith({
    email: 'Angler@Example.COM',
    password: 'strong-pass',
    nickname: 'Wall_E',
  });
  expect(await screen.findByText('注册成功，请登录')).toBeInTheDocument();
});

test('maps duplicate email to the email field', async () => {
  registerMock.mockRejectedValue(new ApiError(409, {
    code: 'DUPLICATE_EMAIL',
    message: '该邮箱已注册',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderRegisterPage();

  await fillValidForm(user);
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(await screen.findByText('该邮箱已注册')).toBeInTheDocument();
  expect(screen.getByLabelText('邮箱')).toHaveAccessibleDescription('该邮箱已注册');
});

test('disables submission while registration is pending', async () => {
  const registration = deferred<User>();
  registerMock.mockReturnValue(registration.promise);
  const { user } = renderRegisterPage();

  await fillValidForm(user);
  await user.click(screen.getByRole('button', { name: '注册' }));

  expect(screen.getByRole('button', { name: '注册中…' })).toBeDisabled();

  registration.resolve({
    id: 1,
    email: 'angler@example.com',
    nickname: 'Wall_E',
    role: 'USER',
  });
  expect(await screen.findByText('注册成功，请登录')).toBeInTheDocument();
});

test('shows a safe live-region message for ordinary server errors', async () => {
  registerMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'SQL constraint users_email_unique failed at db.internal',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderRegisterPage();

  await fillValidForm(user);
  await user.click(screen.getByRole('button', { name: '注册' }));

  const liveRegion = await screen.findByRole('status');
  expect(liveRegion).toHaveTextContent('注册失败，请稍后重试');
  expect(liveRegion).not.toHaveTextContent('SQL constraint');
  expect(screen.queryByText(/db\.internal/)).not.toBeInTheDocument();
});
