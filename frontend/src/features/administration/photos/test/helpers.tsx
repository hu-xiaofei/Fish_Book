import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren, ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ApiError } from '../../../../shared/api/ApiError';
import type { AdminPhotoSummary } from '../model/types';

export const photo: AdminPhotoSummary = { recordId: 31, ownerUserId: 41, ownerNickname: '河边钓友', commonNameZh: '鲫鱼', caughtOn: '2026-09-12', hasPhoto: true, revision: '7', updatedAt: '2026-09-12T10:00:00Z' };
export const page = { items: [photo], page: 0, size: 20, totalItems: 21, totalPages: 2 };
export const apiError = (status: number) => new ApiError(status, { code: status === 409 ? 'CATCH_PHOTO_CONFLICT' : 'ERROR', message: 'internal-private-key', fieldErrors: [], requestId: 'test' });

export function renderAdmin(ui: ReactElement, initialEntry = '/admin/photos', route = '/admin/photos') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter></QueryClientProvider>;
  }
  function LocationProbe() {
    const location = useLocation();
    return <div data-testid="location">{location.pathname}{location.search}</div>;
  }
  return { queryClient, user: userEvent.setup(), ...render(<Routes><Route path={route} element={<>{ui}<LocationProbe /></>} /><Route path="/login" element={<h1>登录页</h1>} /></Routes>, { wrapper: Wrapper }) };
}
