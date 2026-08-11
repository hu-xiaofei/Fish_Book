import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { App } from './App';

const { fetchFishFiltersMock, fetchFishPageMock } = vi.hoisted(() => ({
  fetchFishFiltersMock: vi.fn(),
  fetchFishPageMock: vi.fn(),
}));

vi.mock('../features/catalog/api/catalogApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../features/catalog/api/catalogApi')>();
  return {
    ...actual,
    fetchFishFilterOptions: fetchFishFiltersMock,
    fetchFishPage: fetchFishPageMock,
  };
});

beforeEach(() => {
  fetchFishPageMock.mockReset();
  fetchFishFiltersMock.mockReset();
  fetchFishPageMock.mockResolvedValue({
    items: [], page: 0, size: 12, totalItems: 0, totalPages: 0,
  });
  fetchFishFiltersMock.mockResolvedValue({ families: [], habitats: [] });
});

test('renders the FishBook catalog as the home page', async () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );

  expect(screen.getByRole('heading', { name: 'FishBook' })).toBeInTheDocument();
  expect(await screen.findByRole('searchbox', { name: '搜索鱼类' })).toBeInTheDocument();
});
