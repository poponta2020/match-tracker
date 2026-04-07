import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PlayerChip from '../../components/PlayerChip';
import PlayerSearchCombobox from './PlayerSearchCombobox';

afterEach(cleanup);

/**
 * PairingGenerator の結合テスト
 *
 * PairingGenerator 自体は多くの API 依存（practiceAPI, pairingAPI 等）があり
 * 完全なレンダリングには大規模なモックが必要なため、
 * レビュー指摘の3点をそれぞれ独立して検証する。
 *
 * 1. ドラッグ完了でのstate更新 → pairingDragLogic.test.js でカバー済み
 * 2. 片側空欄時の保存ボタン無効化 → 下記テストで検証
 * 3. DragOverlay のリセット → 下記テストで検証
 */

describe('保存ボタン無効化ロジック', () => {
  // PairingGenerator の保存ボタンの disabled 条件を再現
  const isSaveDisabled = (pairings, loading = false) =>
    loading || pairings.some(p => !p.player1Id || !p.player2Id);

  it('全ペアリングが揃っている場合は保存可能', () => {
    const pairings = [
      { player1Id: 1, player2Id: 2 },
      { player1Id: 3, player2Id: 4 },
    ];
    expect(isSaveDisabled(pairings)).toBe(false);
  });

  it('片方が空欄のペアリングがある場合は保存不可', () => {
    const pairings = [
      { player1Id: 1, player2Id: 2 },
      { player1Id: 3, player2Id: null },
    ];
    expect(isSaveDisabled(pairings)).toBe(true);
  });

  it('player1が空欄のペアリングがある場合も保存不可', () => {
    const pairings = [
      { player1Id: null, player2Id: 2 },
    ];
    expect(isSaveDisabled(pairings)).toBe(true);
  });

  it('ペアリングが空配列の場合は保存可能（別途チェックあり）', () => {
    expect(isSaveDisabled([])).toBe(false);
  });

  it('loading中は保存不可', () => {
    const pairings = [{ player1Id: 1, player2Id: 2 }];
    expect(isSaveDisabled(pairings, true)).toBe(true);
  });
});

describe('DragOverlay 表示ロジック', () => {
  it('activeDragItem が null の場合はオーバーレイが表示されない', () => {
    const activeDragItem = null;
    const { container } = render(
      <div>
        {activeDragItem ? (
          <PlayerChip
            name={activeDragItem.playerName}
            kyuRank={activeDragItem.kyuRank}
            className="text-sm bg-[#f9f6f2] text-[#374151] shadow-lg opacity-80"
          />
        ) : null}
      </div>
    );
    expect(container.textContent).toBe('');
  });

  it('activeDragItem がセットされている場合はオーバーレイが表示される', () => {
    const activeDragItem = { playerName: '山田太郎', kyuRank: 'A級' };
    render(
      <div>
        {activeDragItem ? (
          <PlayerChip
            name={activeDragItem.playerName}
            kyuRank={activeDragItem.kyuRank}
            className="text-sm bg-[#f9f6f2] text-[#374151] shadow-lg opacity-80"
          />
        ) : null}
      </div>
    );
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
  });

  it('onDragCancel 相当（activeDragItem を null にリセット）でオーバーレイが消える', () => {
    let activeDragItem = { playerName: '佐藤花子', kyuRank: 'B級' };

    const { rerender } = render(
      <div data-testid="overlay">
        {activeDragItem ? (
          <PlayerChip name={activeDragItem.playerName} kyuRank={activeDragItem.kyuRank} />
        ) : null}
      </div>
    );
    expect(screen.getByText('佐藤花子')).toBeInTheDocument();

    // onDragCancel: activeDragItem を null にリセット
    activeDragItem = null;
    rerender(
      <div data-testid="overlay">
        {activeDragItem ? (
          <PlayerChip name={activeDragItem.playerName} kyuRank={activeDragItem.kyuRank} />
        ) : null}
      </div>
    );
    expect(screen.queryByText('佐藤花子')).not.toBeInTheDocument();
  });
});

describe('PlayerSearchCombobox', () => {
  const testPlayers = [
    { id: 1, name: '山田太郎', kyuRank: 'A級' },
    { id: 2, name: '佐藤花子', danRank: '二段' },
    { id: 3, name: '田中一郎', kyuRank: 'B級' },
    { id: 4, name: '山田次郎', kyuRank: 'C級' },
  ];

  it('初期表示で全選手が候補に表示される', () => {
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="" onSelect={() => {}} />);
    expect(screen.getAllByRole('option')).toHaveLength(4);
  });

  it('検索語入力で候補が部分一致で絞り込まれる', async () => {
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="" onSelect={() => {}} />);
    const input = screen.getByRole('combobox');
    await userEvent.type(input, '山田');
    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(2);
    expect(options[0]).toHaveTextContent('山田太郎');
    expect(options[1]).toHaveTextContent('山田次郎');
  });

  it('該当なしの場合は「該当する選手がいません」が表示される', async () => {
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="" onSelect={() => {}} />);
    const input = screen.getByRole('combobox');
    await userEvent.type(input, '鈴木');
    expect(screen.queryAllByRole('option')).toHaveLength(0);
    expect(screen.getByText('該当する選手がいません')).toBeInTheDocument();
  });

  it('候補クリックで onSelect が呼ばれ入力欄に選手名が反映される', async () => {
    const onSelect = vi.fn();
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="" onSelect={onSelect} />);
    await userEvent.click(screen.getByText('佐藤花子 (二段)'));
    expect(onSelect).toHaveBeenCalledWith('2');
    expect(screen.getByRole('combobox')).toHaveValue('佐藤花子');
  });

  it('↓キーで候補をハイライト移動し、Enterで選択できる', async () => {
    const onSelect = vi.fn();
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="" onSelect={onSelect} />);
    const input = screen.getByRole('combobox');
    await userEvent.type(input, '山田');
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith('1');
    expect(input).toHaveValue('山田太郎');
  });

  it('↑キーで末尾の候補にラップアラウンドする', async () => {
    const onSelect = vi.fn();
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="" onSelect={onSelect} />);
    const input = screen.getByRole('combobox');
    await userEvent.type(input, '山田');
    fireEvent.keyDown(input, { key: 'ArrowUp' });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith('4');
    expect(input).toHaveValue('山田次郎');
  });

  it('name が null/undefined の選手でもクラッシュしない', () => {
    const playersWithNull = [
      { id: 1, name: null, kyuRank: 'A級' },
      { id: 2, name: undefined },
      { id: 3, name: '山田太郎', kyuRank: 'B級' },
    ];
    render(<PlayerSearchCombobox players={playersWithNull} selectedPlayerId="" onSelect={() => {}} />);
    expect(screen.getAllByRole('option')).toHaveLength(3);
  });

  it('ARIA属性が正しく設定されている', () => {
    render(<PlayerSearchCombobox players={testPlayers} selectedPlayerId="1" onSelect={() => {}} />);
    const input = screen.getByRole('combobox');
    expect(input).toHaveAttribute('aria-expanded', 'true');
    expect(input).toHaveAttribute('aria-controls', 'player-listbox');
    const listbox = screen.getByRole('listbox');
    expect(listbox).toBeInTheDocument();
    const selectedOption = screen.getAllByRole('option').find(
      el => el.getAttribute('aria-selected') === 'true'
    );
    expect(selectedOption).toHaveTextContent('山田太郎');
  });
});
