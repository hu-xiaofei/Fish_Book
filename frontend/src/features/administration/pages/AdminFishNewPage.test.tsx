import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { ADMIN_FISHES_QUERY_KEY, adminFishDetailQueryKey } from '../api/adminFishApi';
import type { AdminFishDetail } from '../model/types';
import { AdminFishNewPage } from './AdminFishNewPage';

const { createAdminFishMock } = vi.hoisted(() => ({ createAdminFishMock: vi.fn() }));
vi.mock('../api/adminFishApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/adminFishApi')>()), createAdminFish: createAdminFishMock,
}));
vi.mock('../../auth/components/SessionNav', () => ({ SessionNav: () => <span>会话导航</span> }));

const createdFish: AdminFishDetail = {
  id: 7, slug: 'test-fish', commonNameZh: '测试鱼', scientificName: 'Testus piscis',
  familyNameZh: '测试科', familyScientificName: 'Testidae', genusNameZh: '测试属', genusScientificName: 'Testus',
  aliases: ['别名一'], habitats: ['LAKE'], appearance: '外形描述', sizeDescription: '体型描述',
  habitatDescription: '栖息环境描述', distribution: '分布描述', description: '综合介绍',
  imagePath: '/images/fish/test-fish.jpg', imageAltText: '测试鱼图片', imageSourceUrl: 'https://example.com/source',
  imageAuthor: '测试作者', imageLicenseName: 'CC BY 4.0', imageLicenseUrl: 'https://example.com/license',
  displayOrder: 1, status: 'DRAFT', publishedAt: null, createdAt: '2026-08-30T00:00:00Z', updatedAt: '2026-08-30T00:00:00Z',
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.state ? ':created' : ''}</output>;
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  queryClient.setQueryData(ADMIN_FISHES_QUERY_KEY, { items: [] });
  function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/admin/fishes/new']}>{children}</MemoryRouter></QueryClientProvider>;
  }
  return {
    queryClient, user: userEvent.setup(), ...render(<Routes>
      <Route path="/admin/fishes/new" element={<><AdminFishNewPage /><LocationProbe /></>} />
      <Route path="/admin/fishes/:id/edit" element={<LocationProbe />} />
      <Route path="/login" element={<LocationProbe />} />
    </Routes>, { wrapper: Wrapper }),
  };
}

async function completeForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Slug'), createdFish.slug);
  await user.type(screen.getByLabelText('中文名'), createdFish.commonNameZh);
  await user.type(screen.getByLabelText('学名'), createdFish.scientificName);
  await user.type(screen.getByLabelText('别名（每行一个）'), createdFish.aliases.join('\n'));
  await user.click(screen.getByLabelText('湖泊'));
  await user.type(screen.getByLabelText('科中文名'), createdFish.familyNameZh);
  await user.type(screen.getByLabelText('科拉丁名'), createdFish.familyScientificName);
  await user.type(screen.getByLabelText('属中文名'), createdFish.genusNameZh);
  await user.type(screen.getByLabelText('属拉丁名'), createdFish.genusScientificName);
  await user.type(screen.getByLabelText('外形'), createdFish.appearance);
  await user.type(screen.getByLabelText('体型'), createdFish.sizeDescription);
  await user.type(screen.getByLabelText('栖息环境'), createdFish.habitatDescription);
  await user.type(screen.getByLabelText('分布'), createdFish.distribution);
  await user.type(screen.getByLabelText('介绍'), createdFish.description);
  await user.type(screen.getByLabelText('图片路径'), createdFish.imagePath);
  await user.type(screen.getByLabelText('图片替代文字'), createdFish.imageAltText);
  await user.type(screen.getByLabelText('图片来源链接'), createdFish.imageSourceUrl);
  await user.type(screen.getByLabelText('图片作者'), createdFish.imageAuthor);
  await user.type(screen.getByLabelText('图片许可名称'), createdFish.imageLicenseName);
  await user.type(screen.getByLabelText('图片许可链接'), createdFish.imageLicenseUrl);
  await user.type(screen.getByLabelText('显示顺序'), '1');
}

beforeEach(() => { createAdminFishMock.mockReset(); });

test('creates a draft, invalidates management, seeds detail, and opens the saved-draft edit route', async () => {
  createAdminFishMock.mockResolvedValue(createdFish);
  const { user, queryClient } = renderPage();
  await completeForm(user);
  await user.click(screen.getByRole('button', { name: '保存草稿' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/admin/fishes/7/edit:created'));
  expect(queryClient.getQueryData(adminFishDetailQueryKey(7))).toEqual(createdFish);
  expect(queryClient.getQueryState(ADMIN_FISHES_QUERY_KEY)?.isInvalidated).toBe(true);
});
