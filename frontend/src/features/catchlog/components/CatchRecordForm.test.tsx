import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { deferred } from '../../../test/renderWithProviders';
import type { CatchRecordInput } from '../model/types';
import { CatchRecordForm } from './CatchRecordForm';

const fishOptions = [
  { slug: 'channa-argus', commonNameZh: '乌鳢' },
  { slug: 'carassius-auratus', commonNameZh: '鲫' },
];

function renderForm({
  onSubmit = vi.fn().mockResolvedValue(undefined),
}: {
  onSubmit?: (input: CatchRecordInput) => Promise<void>;
} = {}) {
  return {
    user: userEvent.setup(),
    onSubmit,
    ...render(
      <CatchRecordForm
        fishOptions={fishOptions}
        submitLabel="保存记录"
        onSubmit={onSubmit}
      />,
    ),
  };
}

async function completeRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText('鱼种'), 'channa-argus');
  await user.type(screen.getByLabelText('钓获日期'), '2026-08-20');
  await user.type(screen.getByLabelText('地点'), ' 城郊水库 ');
}

test('submits normalized values selected from catalog options', async () => {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const { user } = renderForm({ onSubmit });

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  expect(onSubmit).toHaveBeenCalledWith({
    fishSlug: 'channa-argus',
    caughtOn: '2026-08-20',
    location: '城郊水库',
    lengthCm: null,
    weightG: null,
    method: null,
    notes: null,
  });
});

test('exposes the parser validation messages on their matching accessible fields', async () => {
  const { user, onSubmit } = renderForm();

  await user.click(screen.getByRole('button', { name: '保存记录' }));
  expect(await screen.findByText('请选择有效鱼种')).toBeInTheDocument();
  expect(screen.getByText('请输入有效日期')).toBeInTheDocument();
  expect(screen.getByText('请输入地点')).toBeInTheDocument();

  await user.selectOptions(screen.getByLabelText('鱼种'), 'channa-argus');
  await user.type(screen.getByLabelText('钓获日期'), '2099-01-01');
  await user.type(screen.getByLabelText('地点'), '水库');
  await user.type(screen.getByLabelText('长度（cm）'), '-1');
  await user.type(screen.getByLabelText('重量（g）'), '1.234');
  fireEvent.change(screen.getByLabelText('钓法'), { target: { value: '路'.repeat(101) } });
  fireEvent.change(screen.getByLabelText('备注'), { target: { value: '记'.repeat(5001) } });
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  expect(await screen.findByText('钓获日期不能晚于今天')).toBeInTheDocument();
  expect(screen.getByText('数值不能小于 0')).toBeInTheDocument();
  expect(screen.getByText('最多保留两位小数')).toBeInTheDocument();
  expect(screen.getByText('方法最多 100 个字符')).toBeInTheDocument();
  expect(screen.getByText('备注最多 5000 个字符')).toBeInTheDocument();
  expect(screen.getByLabelText('长度（cm）')).toHaveAccessibleDescription('数值不能小于 0');
  expect(screen.getByLabelText('重量（g）')).toHaveAccessibleDescription('最多保留两位小数');
  expect(onSubmit).not.toHaveBeenCalled();

  fireEvent.change(screen.getByLabelText('地点'), { target: { value: '点'.repeat(201) } });
  fireEvent.change(screen.getByLabelText('长度（cm）'), { target: { value: '1e2' } });
  fireEvent.change(screen.getByLabelText('重量（g）'), { target: { value: '100000000' } });
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  expect(await screen.findByText('地点最多 200 个字符')).toBeInTheDocument();
  expect(screen.getByText('请输入普通十进制数值')).toBeInTheDocument();
  expect(screen.getByText('数值超出可记录范围')).toBeInTheDocument();
});

test('uses date and measurement input constraints compatible with the parser bounds', () => {
  renderForm();

  expect(screen.getByLabelText('钓获日期')).toHaveAttribute('type', 'date');
  expect(screen.getByLabelText('钓获日期')).toHaveAttribute('min', '1000-01-01');
  expect(screen.getByLabelText('钓获日期')).toHaveAttribute('max');
  expect(screen.getByLabelText('长度（cm）')).toHaveAttribute('min', '0');
  expect(screen.getByLabelText('长度（cm）')).toHaveAttribute('max', '999999.99');
  expect(screen.getByLabelText('长度（cm）')).toHaveAttribute('step', '0.01');
  expect(screen.getByLabelText('重量（g）')).toHaveAttribute('min', '0');
  expect(screen.getByLabelText('重量（g）')).toHaveAttribute('max', '99999999.99');
  expect(screen.getByLabelText('重量（g）')).toHaveAttribute('step', '0.01');
});

test('disables duplicate submission while the save is pending', async () => {
  const saving = deferred<void>();
  const onSubmit = vi.fn().mockReturnValue(saving.promise);
  const { user } = renderForm({ onSubmit });

  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));

  expect(screen.getByRole('button', { name: '保存中…' })).toBeDisabled();
  await user.click(screen.getByRole('button', { name: '保存中…' }));
  expect(onSubmit).toHaveBeenCalledTimes(1);

  saving.resolve();
  await waitFor(() => expect(screen.getByRole('button', { name: '保存记录' })).toBeEnabled());
});

test('recomputes Shanghai today on submit and refreshes the date max at midnight', async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-08-20T15:59:59.000Z'));
  try {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const { unmount } = renderForm({ onSubmit });
    const caughtOn = screen.getByLabelText('钓获日期');

    expect(caughtOn).toHaveAttribute('max', '2026-08-20');
    fireEvent.change(screen.getByLabelText('鱼种'), { target: { value: 'channa-argus' } });
    fireEvent.change(caughtOn, { target: { value: '2026-08-21' } });
    fireEvent.change(screen.getByLabelText('地点'), { target: { value: '午夜钓点' } });

    // Move the wall clock without firing the scheduled midnight callback: submit must read time afresh.
    vi.setSystemTime(new Date('2026-08-20T16:00:00.000Z'));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '保存记录' }));
      await Promise.resolve();
    });
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ caughtOn: '2026-08-21' }));

    await act(async () => {
      vi.advanceTimersByTime(1_000);
    });
    expect(caughtOn).toHaveAttribute('max', '2026-08-21');
    expect(vi.getTimerCount()).toBe(1);

    unmount();
    expect(vi.getTimerCount()).toBe(0);
  } finally {
    vi.useRealTimers();
  }
});
