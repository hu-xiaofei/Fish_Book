import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { FISH_CATALOG_QUERY_KEY } from '../../catalog/api/catalogApi';
import { ADMIN_FISHES_QUERY_KEY, adminFishDetailQueryKey } from '../api/adminFishApi';
import type { AdminFishDetail } from '../model/types';
import { AdminFishEditPage } from './AdminFishEditPage';

const { fetchAdminFishMock, updateAdminFishMock, publishAdminFishMock, unpublishAdminFishMock } = vi.hoisted(() => ({
  fetchAdminFishMock: vi.fn(), updateAdminFishMock: vi.fn(), publishAdminFishMock: vi.fn(), unpublishAdminFishMock: vi.fn(),
}));
vi.mock('../api/adminFishApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/adminFishApi')>()),
  fetchAdminFish: fetchAdminFishMock, updateAdminFish: updateAdminFishMock,
  publishAdminFish: publishAdminFishMock, unpublishAdminFish: unpublishAdminFishMock,
}));
vi.mock('../../auth/components/SessionNav', () => ({ SessionNav: () => <span>会话导航</span> }));

const draftDetail: AdminFishDetail = {
  id: 7, slug: 'test-fish', commonNameZh: '测试鱼', scientificName: 'Testus piscis',
  familyNameZh: '测试科', familyScientificName: 'Testidae', genusNameZh: '测试属', genusScientificName: 'Testus',
  aliases: ['别名一'], habitats: ['LAKE'], appearance: '外形描述', sizeDescription: '体型描述',
  habitatDescription: '栖息环境描述', distribution: '分布描述', description: '综合介绍',
  imagePath: '/images/fish/test-fish.jpg', imageAltText: '测试鱼图片', imageSourceUrl: 'https://example.com/source',
  imageAuthor: '测试作者', imageLicenseName: 'CC BY 4.0', imageLicenseUrl: 'https://example.com/license',
  displayOrder: 1, status: 'DRAFT', publishedAt: null, createdAt: '2026-08-30T00:00:00Z', updatedAt: '2026-08-30T00:00:00Z',
};
const publishedDetail = { ...draftDetail, status: 'PUBLISHED' as const, publishedAt: '2026-08-31T00:00:00Z' };

function LocationProbe() { const location = useLocation(); return <output data-testid="location">{location.pathname}</output>; }
function renderEdit(initialEntry = '/admin/fishes/7/edit') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(ADMIN_FISHES_QUERY_KEY, { items: [] });
  queryClient.setQueryData(FISH_CATALOG_QUERY_KEY, { items: [] });
  function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter></QueryClientProvider>;
  }
  return { queryClient, user: userEvent.setup(), ...render(<Routes>
    <Route path="/admin/fishes/:id/edit" element={<><AdminFishEditPage /><LocationProbe /></>} />
    <Route path="/admin/fishes" element={<LocationProbe />} /><Route path="/login" element={<LocationProbe />} />
  </Routes>, { wrapper: Wrapper }) };
}

beforeEach(() => {
  fetchAdminFishMock.mockReset(); updateAdminFishMock.mockReset(); publishAdminFishMock.mockReset(); unpublishAdminFishMock.mockReset();
  fetchAdminFishMock.mockResolvedValue(draftDetail);
  vi.spyOn(window, 'confirm').mockReturnValue(true);
});

test('shows draft content with a read-only slug and saves an update without slug in its request', async () => {
  updateAdminFishMock.mockResolvedValue({ ...draftDetail, commonNameZh: '新名称' });
  const { user } = renderEdit();
  expect(await screen.findByRole('heading', { name: '编辑鱼类' })).toBeInTheDocument();
  expect(screen.getByLabelText('Slug')).toHaveAttribute('readonly');
  await user.clear(screen.getByLabelText('中文名'));
  await user.type(screen.getByLabelText('中文名'), '新名称');
  await user.click(screen.getByRole('button', { name: '保存修改' }));
  await waitFor(() => expect(updateAdminFishMock).toHaveBeenCalledTimes(1));
  expect(updateAdminFishMock.mock.calls[0]?.[0]).toBe(7);
  expect(updateAdminFishMock.mock.calls[0]?.[1]).not.toHaveProperty('slug');
  expect(screen.getByRole('button', { name: '发布' })).toBeInTheDocument();
});

test('shows loading while fish detail is pending', () => {
  fetchAdminFishMock.mockImplementationOnce(() => new Promise(() => undefined));
  renderEdit();
  expect(screen.getByText('正在加载鱼类资料…')).toHaveAttribute('role', 'status');
});

test('shows a safe missing-detail state', async () => {
  fetchAdminFishMock.mockReset();
  fetchAdminFishMock.mockRejectedValueOnce(new ApiError(404, { code: 'FISH_NOT_FOUND', message: 'not found', fieldErrors: [], requestId: 'request' }));
  renderEdit();
  expect(await screen.findByRole('heading', { name: '没有找到鱼类资料' })).toBeInTheDocument();
});

test('publishes a draft and invalidates both administration and public catalog roots', async () => {
  publishAdminFishMock.mockResolvedValue(publishedDetail);
  const { queryClient, user } = renderEdit();
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  await user.click(await screen.findByRole('button', { name: '发布' }));
  await waitFor(() => expect(publishAdminFishMock).toHaveBeenCalledWith(7));
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ADMIN_FISHES_QUERY_KEY });
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: FISH_CATALOG_QUERY_KEY });
  expect(queryClient.getQueryData(adminFishDetailQueryKey(7))).toEqual(publishedDetail);
});

test('confirmed unpublish updates detail and invalidates admin and public catalog roots', async () => {
  fetchAdminFishMock.mockResolvedValue(publishedDetail);
  unpublishAdminFishMock.mockResolvedValue({ ...publishedDetail, status: 'UNPUBLISHED' });
  const { queryClient, user } = renderEdit();
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  await user.click(await screen.findByRole('button', { name: '下架' }));
  expect(unpublishAdminFishMock).toHaveBeenCalledWith(7);
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ADMIN_FISHES_QUERY_KEY });
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: FISH_CATALOG_QUERY_KEY });
});

test('does not unpublish after a cancelled confirmation', async () => {
  fetchAdminFishMock.mockResolvedValue(publishedDetail);
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  const { user } = renderEdit();
  await user.click(await screen.findByRole('button', { name: '下架' }));
  expect(unpublishAdminFishMock).not.toHaveBeenCalled();
});

test('can republish an unpublished fish', async () => {
  fetchAdminFishMock.mockResolvedValue({ ...publishedDetail, status: 'UNPUBLISHED' });
  publishAdminFishMock.mockResolvedValue(publishedDetail);
  const { user } = renderEdit();
  expect(await screen.findByRole('button', { name: '重新发布' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '重新发布' }));
  expect(publishAdminFishMock).toHaveBeenCalledWith(7);
  fetchAdminFishMock.mockResolvedValue(publishedDetail);
});

test('prevents duplicate publication actions while a request is pending', async () => {
  let resolve!: (detail: AdminFishDetail) => void;
  publishAdminFishMock.mockReturnValue(new Promise<AdminFishDetail>((done) => { resolve = done; }));
  const { user } = renderEdit();
  const button = await screen.findByRole('button', { name: '发布' });
  await user.click(button);
  await user.click(button);
  expect(publishAdminFishMock).toHaveBeenCalledTimes(1);
  resolve(publishedDetail);
  await waitFor(() => expect(button).not.toBeDisabled());
});

test('renders a safe publication transition error and expires a confirmed unauthorized session', async () => {
  publishAdminFishMock.mockRejectedValueOnce(new ApiError(409, { code: 'INVALID_PUBLICATION_TRANSITION', message: 'state changed', fieldErrors: [], requestId: 'request' }));
  const { user } = renderEdit();
  await user.click(await screen.findByRole('button', { name: '发布' }));
  expect(await screen.findByText('鱼类状态已变化，请刷新后重试')).toBeInTheDocument();
});

test('expires the administrator session after a confirmed publication 401', async () => {
  publishAdminFishMock.mockRejectedValue(new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: 'expired', fieldErrors: [], requestId: 'request' }));
  const { queryClient, user } = renderEdit();
  await user.click(await screen.findByRole('button', { name: '发布' }));
  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  expect(queryClient.getQueriesData({ queryKey: ADMIN_FISHES_QUERY_KEY }).every(([, data]) => data === undefined)).toBe(true);
});
