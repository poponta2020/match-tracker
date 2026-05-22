import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import SaveProgressOverlay from './SaveProgressOverlay';

const baseProps = {
  state: 'idle',
  savingMessage: '保存中...',
  successMessage: '参加登録を保存しました',
  errorMessage: '保存に失敗しました',
  errorDetail: '',
  onSuccessConfirm: vi.fn(),
  onErrorClose: vi.fn(),
};

describe('SaveProgressOverlay', () => {
  afterEach(() => {
    cleanup();
  });

  it("state='idle' のときは何も描画しない", () => {
    const { container } = render(
      <SaveProgressOverlay {...baseProps} state="idle" />,
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it("state='saving' のときは savingMessage と aria-busy=true の dialog を描画する", () => {
    render(<SaveProgressOverlay {...baseProps} state="saving" />);
    expect(screen.getByText('保存中...')).toBeTruthy();

    const dialog = screen.getByRole('dialog');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.getAttribute('aria-busy')).toBe('true');

    expect(screen.queryByRole('button', { name: 'カレンダーに戻る' })).toBeNull();
    expect(screen.queryByRole('button', { name: '閉じる' })).toBeNull();
  });

  it("state='success' のときは successMessage と「カレンダーに戻る」ボタンを描画する", () => {
    render(<SaveProgressOverlay {...baseProps} state="success" />);
    expect(screen.getByText('参加登録を保存しました')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'カレンダーに戻る' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: '閉じる' })).toBeNull();

    const dialog = screen.getByRole('dialog');
    expect(dialog.getAttribute('aria-busy')).toBe('false');
  });

  it("success: 「カレンダーに戻る」クリックで onSuccessConfirm が呼ばれる", async () => {
    const user = userEvent.setup();
    const onSuccessConfirm = vi.fn();
    render(
      <SaveProgressOverlay
        {...baseProps}
        state="success"
        onSuccessConfirm={onSuccessConfirm}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'カレンダーに戻る' }));
    expect(onSuccessConfirm).toHaveBeenCalledTimes(1);
  });

  it("state='error' で errorDetail が空のときは errorMessage と「閉じる」ボタンのみ描画する", () => {
    render(
      <SaveProgressOverlay
        {...baseProps}
        state="error"
        errorMessage="保存に失敗しました"
        errorDetail=""
      />,
    );
    expect(screen.getByText('保存に失敗しました')).toBeTruthy();
    expect(screen.getByRole('button', { name: '閉じる' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'カレンダーに戻る' })).toBeNull();
  });

  it("state='error' で errorDetail が渡されると詳細メッセージも描画する", () => {
    render(
      <SaveProgressOverlay
        {...baseProps}
        state="error"
        errorMessage="保存に失敗しました"
        errorDetail="サーバーエラー: 500 Internal Server Error"
      />,
    );
    expect(screen.getByText('保存に失敗しました')).toBeTruthy();
    expect(screen.getByText('サーバーエラー: 500 Internal Server Error')).toBeTruthy();
  });

  it("error: 「閉じる」クリックで onErrorClose が呼ばれる", async () => {
    const user = userEvent.setup();
    const onErrorClose = vi.fn();
    render(
      <SaveProgressOverlay
        {...baseProps}
        state="error"
        onErrorClose={onErrorClose}
      />,
    );
    await user.click(screen.getByRole('button', { name: '閉じる' }));
    expect(onErrorClose).toHaveBeenCalledTimes(1);
  });
});
