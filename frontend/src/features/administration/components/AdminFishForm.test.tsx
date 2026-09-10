import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { ApiError } from '../../../shared/api/ApiError';
import { AdminFishForm } from './AdminFishForm';
import type { AdminFishFormValues } from '../model/adminFishForm';

const initialValues: AdminFishFormValues = {
  slug: 'test-fish', commonNameZh: '测试鱼', scientificName: 'Testus piscis',
  familyNameZh: '测试科', familyScientificName: 'Testidae', genusNameZh: '测试属', genusScientificName: 'Testus',
  aliasesText: '别名一\n别名二', habitats: ['LAKE'], appearance: '外形描述', sizeDescription: '体型描述',
  habitatDescription: '栖息环境描述', distribution: '分布描述', description: '综合介绍',
  imagePath: '/images/fish/test-fish.jpg', imageAltText: '测试鱼图片', imageSourceUrl: 'https://example.com/source',
  imageAuthor: '测试作者', imageLicenseName: 'CC BY 4.0', imageLicenseUrl: 'https://example.com/license',
  displayOrder: '1',
};

function renderForm(overrides: Partial<React.ComponentProps<typeof AdminFishForm>> = {}) {
  return {
    user: userEvent.setup(),
    ...render(<AdminFishForm initialValues={initialValues} submitLabel="保存修改" onSubmit={vi.fn().mockResolvedValue(undefined)} {...overrides} />),
  };
}

test('edit mode renders slug read-only and submits normalized content once', async () => {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const { user } = renderForm({ slugReadOnly: true, onSubmit });

  expect(screen.getByLabelText('Slug')).toHaveAttribute('readonly');
  await user.clear(screen.getByLabelText('中文名'));
  await user.type(screen.getByLabelText('中文名'), '  新名称  ');
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ commonNameZh: '新名称' }));
});

test('create mode keeps slug editable and renders every accessible field group', () => {
  renderForm({ slugReadOnly: false, submitLabel: '保存草稿' });

  expect(screen.getByLabelText('Slug')).not.toHaveAttribute('readonly');
  ['基本信息', '分类信息', '内容', '图片署名', '排序'].forEach((name) => {
    expect(screen.getByRole('group', { name })).toBeInTheDocument();
  });
  expect(screen.getByLabelText('河流')).toBeInstanceOf(HTMLInputElement);
  expect(screen.getByLabelText('湖泊')).toBeInstanceOf(HTMLInputElement);
  expect(screen.getByLabelText('水库')).toBeInstanceOf(HTMLInputElement);
  expect(screen.getByLabelText('池塘')).toBeInstanceOf(HTMLInputElement);
  expect(screen.getByLabelText('溪流')).toBeInstanceOf(HTMLInputElement);
});

test('shows client validation errors without sending invalid content', async () => {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const { user } = renderForm({ onSubmit });
  await user.clear(screen.getByLabelText('中文名'));
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  expect(await screen.findByText('此字段不能为空或超出长度限制')).toBeInTheDocument();
  expect(onSubmit).not.toHaveBeenCalled();
});

test('maps backend aliases and known flat field errors to their form controls', async () => {
  const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, {
    code: 'VALIDATION_FAILED', message: 'internal detail', requestId: 'request',
    fieldErrors: [{ field: 'aliases', message: '别名不可重复' }, { field: 'commonNameZh', message: '中文名不可用' }],
  }));
  const { user } = renderForm({ onSubmit });
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  expect(await screen.findByText('别名不可重复')).toBeInTheDocument();
  expect(screen.getByText('中文名不可用')).toBeInTheDocument();
});

test.each([
  [500, 'INTERNAL_ERROR'],
  [400, 'UNKNOWN_FAILURE'],
])('does not expose known field errors from unsafe %s %s responses', async (status, code) => {
  const onSubmit = vi.fn().mockRejectedValue(new ApiError(status, {
    code, message: 'internal detail', requestId: 'request',
    fieldErrors: [{ field: 'commonNameZh', message: '数据库字段错误' }],
  }));
  const { user } = renderForm({ onSubmit });
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  expect(await screen.findByText('保存鱼类资料失败，请稍后重试')).toBeInTheDocument();
  expect(screen.queryByText('数据库字段错误')).not.toBeInTheDocument();
});

test('shows a conflict message and never exposes generic backend details', async () => {
  const onSubmit = vi.fn()
    .mockRejectedValueOnce(new ApiError(409, {
      code: 'CATALOG_ENTRY_CONFLICT', message: 'duplicate at db.internal', fieldErrors: [], requestId: 'request',
    }))
    .mockRejectedValueOnce(new ApiError(500, {
      code: 'INTERNAL_ERROR', message: 'stack at db.internal', fieldErrors: [], requestId: 'request',
    }));
  const { user } = renderForm({ onSubmit });
  await user.click(screen.getByRole('button', { name: '保存修改' }));
  expect(await screen.findByText('Slug、中文名或学名已被使用')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '保存修改' }));
  const status = await screen.findByText('保存鱼类资料失败，请稍后重试');
  expect(status).not.toHaveTextContent('db.internal');
});

test('suppresses local save messages for a confirmed 401', async () => {
  const onSubmit = vi.fn().mockRejectedValue(new ApiError(401, {
    code: 'AUTHENTICATION_REQUIRED', message: '请先登录', fieldErrors: [], requestId: 'request',
  }));
  const { user } = renderForm({ onSubmit });
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  await waitFor(() => expect(onSubmit).toHaveBeenCalled());
  expect(screen.queryByText('保存鱼类资料失败，请稍后重试')).not.toBeInTheDocument();
});

test('prevents duplicate submits while the save is pending', async () => {
  let resolve!: () => void;
  const onSubmit = vi.fn().mockReturnValue(new Promise<void>((done) => { resolve = done; }));
  const { user } = renderForm({ onSubmit });
  const button = screen.getByRole('button', { name: '保存修改' });
  await user.click(button);
  await user.click(button);

  expect(onSubmit).toHaveBeenCalledTimes(1);
  expect(button).toBeDisabled();
  resolve();
  await waitFor(() => expect(button).not.toBeDisabled());
});
