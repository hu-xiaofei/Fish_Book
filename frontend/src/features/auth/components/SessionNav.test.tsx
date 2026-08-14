import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import type { User } from '../../../shared/api/types';
import { CURRENT_USER_QUERY_KEY } from '../api/currentUser';
import { SessionNav } from './SessionNav';

const { fetchCurrentUserMock } = vi.hoisted(() => ({
  fetchCurrentUserMock: vi.fn(),
}));

vi.mock('../api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

const authenticatedUser: User = {
  id: 1,
  email: 'angler@example.com',
  nickname: 'River',
  role: 'USER',
};

beforeEach(() => {
  fetchCurrentUserMock.mockReset();
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
});

test('authenticated navigation includes favorites without advertising catches early', () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, authenticatedUser);

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SessionNav />
      </MemoryRouter>
    </QueryClientProvider>,
  );

  expect(screen.getByRole('link', { name: '我的收藏' })).toHaveAttribute('href', '/favorites');
  expect(screen.queryByRole('link', { name: /钓获/ })).not.toBeInTheDocument();
});
