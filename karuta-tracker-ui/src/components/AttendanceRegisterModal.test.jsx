import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

import AttendanceRegisterModal from './AttendanceRegisterModal';

describe('AttendanceRegisterModal', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('isOpen=false のとき何も描画しない', () => {
    render(
      <AttendanceRegisterModal
        isOpen={false}
        onClose={vi.fn()}
        year={2026}
        month={5}
      />,
    );
    expect(screen.queryByRole('heading', { name: '出欠登録' })).toBeNull();
    expect(screen.queryByRole('button', { name: /参加登録/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /キャンセル登録/ })).toBeNull();
  });

  it('isCurrentMonth が省略されたとき（デフォルト true）「参加登録」「キャンセル登録」の両ボタンを表示する', () => {
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={vi.fn()}
        year={2026}
        month={5}
      />,
    );
    expect(screen.getByRole('button', { name: /参加登録/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /キャンセル登録/ })).toBeTruthy();
  });

  it('isCurrentMonth=true のとき両ボタンを表示する', () => {
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={vi.fn()}
        year={2026}
        month={5}
        isCurrentMonth
      />,
    );
    expect(screen.getByRole('button', { name: /参加登録/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /キャンセル登録/ })).toBeTruthy();
  });

  it('isCurrentMonth=false のとき「参加登録」のみ表示し「キャンセル登録」は非表示', () => {
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={vi.fn()}
        year={2026}
        month={6}
        isCurrentMonth={false}
      />,
    );
    expect(screen.getByRole('button', { name: /参加登録/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /キャンセル登録/ })).toBeNull();
  });

  it('ヘッダーに「○年○月の出欠登録を行います。」が表示される', () => {
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={vi.fn()}
        year={2026}
        month={5}
      />,
    );
    expect(screen.getByText('2026年5月の出欠登録を行います。')).toBeTruthy();
  });

  it('「参加登録」クリックで /practice/participation?year=Y&month=M へ遷移し onClose を呼ぶ', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={onClose}
        year={2026}
        month={6}
        isCurrentMonth={false}
      />,
    );
    await user.click(screen.getByRole('button', { name: /参加登録/ }));
    expect(mockNavigate).toHaveBeenCalledWith('/practice/participation?year=2026&month=6');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('「キャンセル登録」クリックで /practice/cancel?year=Y&month=M へ遷移し onClose を呼ぶ', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={onClose}
        year={2026}
        month={5}
        isCurrentMonth
      />,
    );
    await user.click(screen.getByRole('button', { name: /キャンセル登録/ }));
    expect(mockNavigate).toHaveBeenCalledWith('/practice/cancel?year=2026&month=5');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('フッターの「閉じる」ボタンクリックで onClose を呼ぶ（navigate は呼ばない）', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={onClose}
        year={2026}
        month={5}
      />,
    );
    // 右上の X（aria-label="閉じる"）とフッターの「閉じる」両方が name にマッチするため
    // インデックスで区別する。フッター側は配列末尾。
    const closeButtons = screen.getAllByRole('button', { name: '閉じる' });
    expect(closeButtons.length).toBe(2);
    await user.click(closeButtons[closeButtons.length - 1]);
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('右上の× (aria-label="閉じる") クリックでも onClose を呼ぶ', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <AttendanceRegisterModal
        isOpen
        onClose={onClose}
        year={2026}
        month={5}
      />,
    );
    const closeButtons = screen.getAllByRole('button', { name: '閉じる' });
    await user.click(closeButtons[0]);
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
