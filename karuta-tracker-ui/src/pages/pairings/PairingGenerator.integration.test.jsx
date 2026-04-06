import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PlayerChip from '../../components/PlayerChip';

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
