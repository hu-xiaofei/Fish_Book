import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import type { User } from '../../../shared/api/types';
import { deferred } from '../../../test/renderWithProviders';
import { CURRENT_USER_QUERY_KEY } from '../../auth/api/currentUser';
import { FAVORITES_QUERY_KEY } from '../api/favoritesApi';
import { FavoriteButton } from './FavoriteButton';

const { addFavoriteMock, fetchCurrentUserMock, removeFavoriteMock } = vi.hoisted(() => ({
  addFavoriteMock: vi.fn(),
  fetchCurrentUserMock: vi.fn(),
  removeFavoriteMock: vi.fn(),
}));

vi.mock('../api/favoritesApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/favoritesApi')>();
  return {
    ...actual,
    addFavorite: addFavoriteMock,
    removeFavorite: removeFavoriteMock,
  };
});

vi.mock('../../auth/api/currentUser', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/api/currentUser')>();
  return { ...actual, fetchCurrentUser: fetchCurrentUserMock };
});

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

function renderButton({
  authenticated = true,
  expiredSession = false,
  isFavorited = false,
}: {
  authenticated?: boolean;
  expiredSession?: boolean;
  isFavorited?: boolean;
} = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0 },
      mutations: { retry: false },
    },
  });
  if (authenticated) {
    queryClient.setQueryData(
      CURRENT_USER_QUERY_KEY,
      authenticatedUser,
      expiredSession ? { updatedAt: 1 } : undefined,
    );
  }
  queryClient.setQueryData([...FAVORITES_QUERY_KEY, 'status', 'channa-argus'], {
    items: [{ fishSlug: 'channa-argus', favorited: isFavorited }],
  });

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/fish/channa-argus?view=detail']}>
          {children}
          <LocationProbe />
        </MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    queryClient,
    user: userEvent.setup(),
    ...render(
      <FavoriteButton
        fishSlug="channa-argus"
        isFavorited={isFavorited}
        returnTo="/fish/channa-argus?view=detail"
      />,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  addFavoriteMock.mockReset();
  fetchCurrentUserMock.mockReset();
  removeFavoriteMock.mockReset();
  addFavoriteMock.mockResolvedValue(undefined);
  removeFavoriteMock.mockResolvedValue(undefined);
  fetchCurrentUserMock.mockResolvedValue(authenticatedUser);
});

test('authenticated click adds a favorite and invalidates every favorites query', async () => {
  const { queryClient, user } = renderButton();

  await user.click(screen.getByRole('button', { name: '收藏' }));

  await waitFor(() => expect(addFavoriteMock).toHaveBeenCalledWith('channa-argus'));
  await waitFor(() => {
    expect(
      queryClient.getQueryState([...FAVORITES_QUERY_KEY, 'status', 'channa-argus'])
        ?.isInvalidated,
    ).toBe(true);
  });
});

test('favorited button exposes pressed state and removes the favorite', async () => {
  const { user } = renderButton({ isFavorited: true });

  const button = screen.getByRole('button', { name: '取消收藏' });
  expect(button).toHaveAttribute('aria-pressed', 'true');
  await user.click(button);

  await waitFor(() => expect(removeFavoriteMock).toHaveBeenCalledWith('channa-argus'));
});

test('anonymous click navigates to login with the encoded current path', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { user } = renderButton({ authenticated: false });

  await waitFor(() => expect(fetchCurrentUserMock).toHaveBeenCalledTimes(1));
  await user.click(screen.getByRole('button', { name: '收藏' }));

  expect(screen.getByTestId('location')).toHaveTextContent(
    '/login?returnTo=%2Ffish%2Fchanna-argus%3Fview%3Ddetail',
  );
});

test('confirmed expired cached session redirects instead of mutating', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { queryClient, user } = renderButton({ expiredSession: true });

  await waitFor(() => {
    expect(queryClient.getQueryState(CURRENT_USER_QUERY_KEY)?.status).toBe('error');
  });
  await user.click(screen.getByRole('button', { name: '收藏' }));

  expect(screen.getByTestId('location')).toHaveTextContent(
    '/login?returnTo=%2Ffish%2Fchanna-argus%3Fview%3Ddetail',
  );
  expect(addFavoriteMock).not.toHaveBeenCalled();
  expect(removeFavoriteMock).not.toHaveBeenCalled();
});

test('favorite mutation 401 clears fresh session state and routes to safe login', async () => {
  const unauthorized = new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED',
    message: '请先登录',
    fieldErrors: [],
    requestId: 'test-request',
  });
  addFavoriteMock.mockRejectedValue(unauthorized);
  fetchCurrentUserMock.mockRejectedValue(unauthorized);
  const { queryClient, user } = renderButton();

  await user.click(screen.getByRole('button', { name: '收藏' }));

  await waitFor(() => expect(addFavoriteMock).toHaveBeenCalledTimes(1));
  expect(screen.getByTestId('location')).toHaveTextContent(
    '/login?returnTo=%2Ffish%2Fchanna-argus%3Fview%3Ddetail',
  );
  expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
  expect(queryClient.getQueriesData({ queryKey: FAVORITES_QUERY_KEY })).toEqual([]);
});

test('retained user remains usable after a transient non-authentication error', async () => {
  fetchCurrentUserMock.mockRejectedValue(new ApiError(500, {
    code: 'INTERNAL_ERROR',
    message: 'temporary failure',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  const { queryClient, user } = renderButton({ expiredSession: true });

  await waitFor(() => {
    expect(queryClient.getQueryState(CURRENT_USER_QUERY_KEY)?.status).toBe('error');
  });
  await user.click(screen.getByRole('button', { name: '收藏' }));

  await waitFor(() => expect(addFavoriteMock).toHaveBeenCalledWith('channa-argus'));
  expect(screen.getByTestId('location')).toHaveTextContent(
    '/fish/channa-argus?view=detail',
  );
});

test('initial session lookup keeps the favorite action disabled', () => {
  fetchCurrentUserMock.mockReturnValue(deferred<User>().promise);
  renderButton({ authenticated: false });

  expect(screen.getByRole('button', { name: '收藏' })).toBeDisabled();
  expect(addFavoriteMock).not.toHaveBeenCalled();
  expect(removeFavoriteMock).not.toHaveBeenCalled();
});
