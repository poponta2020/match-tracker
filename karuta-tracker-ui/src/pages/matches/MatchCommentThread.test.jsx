import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../../api/matchComments', () => ({
  matchCommentsAPI: {
    getComments: vi.fn().mockResolvedValue({ data: [] }),
    createComment: vi.fn().mockResolvedValue({ data: {} }),
    updateComment: vi.fn().mockResolvedValue({ data: {} }),
    deleteComment: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'テスト選手' },
  }),
}));

import { matchCommentsAPI } from '../../api/matchComments';
import MatchCommentThread from './MatchCommentThread';

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MatchCommentThread', () => {
  const renderComponent = () =>
    render(<MatchCommentThread matchId={1} menteeId={1} />);

  it('Enterキーでコメントが送信される', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.type(textarea, 'テストコメント');
    await user.keyboard('{Enter}');

    expect(matchCommentsAPI.createComment).toHaveBeenCalledWith(
      1,
      1,
      'テストコメント'
    );
  });

  it('Shift+Enterで改行が入力される（送信されない）', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.type(textarea, '1行目');
    await user.keyboard('{Shift>}{Enter}{/Shift}');
    await user.type(textarea, '2行目');

    expect(matchCommentsAPI.createComment).not.toHaveBeenCalled();
    expect(textarea.value).toContain('1行目');
    expect(textarea.value).toContain('2行目');
  });

  it('送信後に入力欄がクリアされる', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.type(textarea, 'テストコメント');
    await user.keyboard('{Enter}');

    await vi.waitFor(() => {
      expect(textarea.value).toBe('');
    });
  });
});
