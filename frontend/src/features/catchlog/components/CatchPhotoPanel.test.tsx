import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { deferred } from '../../../test/renderWithProviders';
import { CatchPhotoPanel } from './CatchPhotoPanel';

const { putCatchPhotoMock, removeCatchPhotoMock } = vi.hoisted(() => ({
  putCatchPhotoMock: vi.fn(),
  removeCatchPhotoMock: vi.fn(),
}));

vi.mock('../api/catchPhotoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/catchPhotoApi')>();
  return {
    ...actual,
    putCatchPhoto: putCatchPhotoMock,
    removeCatchPhoto: removeCatchPhotoMock,
  };
});

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderPanel(hasPhoto = false) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData(['catches'], { seeded: true });
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/catches/31']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  }

  return {
    queryClient,
    user: userEvent.setup({ applyAccept: false }),
    ...render(
      <Routes>
        <Route
          path="/catches/:id"
          element={(
            <>
              <CatchPhotoPanel recordId={31} hasPhoto={hasPhoto} photoAlt="乌鳢钓获照片" />
              <LocationProbe />
            </>
          )}
        />
        <Route path="/login" element={<LocationProbe />} />
      </Routes>,
      { wrapper: Wrapper },
    ),
  };
}

beforeEach(() => {
  putCatchPhotoMock.mockReset();
  removeCatchPhotoMock.mockReset();
});

test('shows no-photo state and rejects unsupported or oversized files before upload', async () => {
  const { user } = renderPanel();
  const input = screen.getByLabelText('钓获照片');

  expect(screen.getByText('暂无照片')).toBeInTheDocument();
  await user.upload(input, new File(['gif'], 'catch.gif', { type: 'image/gif' }));
  expect(screen.getByText('请选择 10 MB 以内的 JPEG、PNG 或 WebP 照片')).toBeInTheDocument();
  expect(putCatchPhotoMock).not.toHaveBeenCalled();

  await user.upload(input, new File(
    [new Uint8Array(10 * 1024 * 1024 + 1)],
    'huge.jpg',
    { type: 'image/jpeg' },
  ));
  expect(screen.getByText('请选择 10 MB 以内的 JPEG、PNG 或 WebP 照片')).toBeInTheDocument();
  expect(putCatchPhotoMock).not.toHaveBeenCalled();
});

test('shows upload pending, then renders the protected image and invalidates catch data', async () => {
  const uploading = deferred<void>();
  putCatchPhotoMock.mockReturnValue(uploading.promise);
  const { user, queryClient } = renderPanel();
  const file = new File(['jpeg'], 'catch.jpg', { type: 'image/jpeg' });

  await user.upload(screen.getByLabelText('钓获照片'), file);
  await user.click(screen.getByRole('button', { name: '上传照片' }));
  expect(screen.getByRole('button', { name: '上传中…' })).toBeDisabled();
  uploading.resolve();

  const image = await screen.findByRole('img', { name: '乌鳢钓获照片' });
  expect(image.getAttribute('src')).toContain('/api/v1/catches/31/photo');
  expect(putCatchPhotoMock).toHaveBeenCalledWith(31, file);
  expect(queryClient.getQueryState(['catches'])?.isInvalidated).toBe(true);
});

test('keeps a failed selection for safe retry without exposing backend details', async () => {
  putCatchPhotoMock
    .mockRejectedValueOnce(new Error('minio unavailable at storage.internal'))
    .mockResolvedValueOnce(undefined);
  const { user } = renderPanel();
  const file = new File(['png'], 'catch.png', { type: 'image/png' });

  await user.upload(screen.getByLabelText('钓获照片'), file);
  await user.click(screen.getByRole('button', { name: '上传照片' }));
  const status = await screen.findByText('照片上传失败，请稍后重试');
  expect(status).not.toHaveTextContent('storage.internal');
  await user.click(screen.getByRole('button', { name: '重试上传' }));

  expect(await screen.findByRole('img', { name: '乌鳢钓获照片' })).toBeInTheDocument();
  expect(putCatchPhotoMock).toHaveBeenCalledTimes(2);
});

test('supports image fallback, replacement, and confirmed removal', async () => {
  putCatchPhotoMock.mockResolvedValue(undefined);
  removeCatchPhotoMock.mockResolvedValue(undefined);
  const { user } = renderPanel(true);

  const image = screen.getByRole('img', { name: '乌鳢钓获照片' });
  fireEvent.error(image);
  expect(screen.getByText('照片暂时无法显示')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '重新加载照片' }));
  expect(screen.getByRole('img', { name: '乌鳢钓获照片' })).toBeInTheDocument();

  const replacement = new File(['webp'], 'replacement.webp', { type: 'image/webp' });
  await user.upload(screen.getByLabelText('钓获照片'), replacement);
  await user.click(screen.getByRole('button', { name: '替换照片' }));
  await waitFor(() => expect(putCatchPhotoMock).toHaveBeenCalledWith(31, replacement));

  await user.click(screen.getByRole('button', { name: '移除照片' }));
  expect(removeCatchPhotoMock).not.toHaveBeenCalled();
  expect(screen.getByRole('alertdialog', { name: '确认移除照片' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '确认移除' }));

  expect(await screen.findByText('暂无照片')).toBeInTheDocument();
  expect(removeCatchPhotoMock).toHaveBeenCalledWith(31);
});
