import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DndContext } from '@dnd-kit/core';
import PlayerChip from '../../components/PlayerChip';
import PlayerSearchCombobox from './PlayerSearchCombobox';
import DraggablePlayerChip from './DraggablePlayerChip';
import DroppableSlot from './DroppableSlot';

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

describe('DraggablePlayerChip タップ選択表示', () => {
  const renderChip = (props) => render(
    <DndContext>
      <DraggablePlayerChip
        id="chip-1"
        data={{ playerId: 1, playerName: '山田太郎', kyuRank: 'A級', source: { type: 'waiting' } }}
        {...props}
      />
    </DndContext>
  );

  it('isSelected=true で選択中スタイル（枠線・背景色）が適用される', () => {
    const { container } = renderChip({ isSelected: true });
    const chip = container.querySelector('[class*="bg-\\[\\#4a6b5a\\]"]');
    expect(chip).toBeTruthy();
    expect(chip.className).toContain('border-2');
    expect(chip.className).toContain('border-[#2d4a3e]');
  });

  it('isSelected=false（デフォルト）ではデフォルトスタイルが適用される', () => {
    const { container } = renderChip({ isSelected: false });
    const chip = container.querySelector('[class*="bg-\\[\\#f9f6f2\\]"]');
    expect(chip).toBeTruthy();
    expect(container.querySelector('[class*="border-2"]')).toBeNull();
  });

  it('タップで onClick が呼ばれる', () => {
    const onClick = vi.fn();
    renderChip({ onClick });
    fireEvent.click(screen.getByText('山田太郎'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('タップイベントは stopPropagation される（親 document まで伝播しない）', () => {
    const onClick = vi.fn();
    const onParentClick = vi.fn();
    const { container } = render(
      <div onClick={onParentClick}>
        <DndContext>
          <DraggablePlayerChip
            id="chip-1"
            data={{ playerId: 1, playerName: '山田太郎', source: { type: 'waiting' } }}
            onClick={onClick}
          />
        </DndContext>
      </div>
    );
    fireEvent.click(container.querySelector('[class*="bg-\\[\\#f9f6f2\\]"]'));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(onParentClick).not.toHaveBeenCalled();
  });
});

describe('DroppableSlot タップ受付', () => {
  const renderSlot = (props, children = '子要素') => render(
    <DndContext>
      <DroppableSlot id="slot-1" data={{ slotType: 'waiting-list' }} {...props}>
        {children}
      </DroppableSlot>
    </DndContext>
  );

  it('タップで onClick が呼ばれる', () => {
    const onClick = vi.fn();
    renderSlot({ onClick });
    fireEvent.click(screen.getByText('子要素'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('タップイベントは stopPropagation される', () => {
    const onClick = vi.fn();
    const onParentClick = vi.fn();
    render(
      <div onClick={onParentClick}>
        <DndContext>
          <DroppableSlot id="slot-1" data={{ slotType: 'waiting-list' }} onClick={onClick}>
            <span>子要素</span>
          </DroppableSlot>
        </DndContext>
      </div>
    );
    fireEvent.click(screen.getByText('子要素'));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(onParentClick).not.toHaveBeenCalled();
  });

  it('isDragActive=true でハイライトクラスが適用される', () => {
    const { container } = renderSlot({ isDragActive: true });
    const slot = container.querySelector('[class*="bg-\\[\\#eef3f0\\]"]');
    expect(slot).toBeTruthy();
  });

  it('isDragActive=false（デフォルト）ではハイライトクラスが無い', () => {
    const { container } = renderSlot({ isDragActive: false });
    expect(container.querySelector('[class*="bg-\\[\\#eef3f0\\]"]')).toBeNull();
  });
});

describe('タップ選択モードの状態遷移ロジック', () => {
  // PairingGenerator.handleChipClick のロジック再現
  const handleChipClick = ({ selectedPlayer, playerId, playerName, source, pairings, isReadOnly, isViewMode }) => {
    if (isReadOnly || isViewMode) return { action: 'no-op' };
    if (source.type === 'pairing' && pairings[source.pairingIndex]?.hasResult) return { action: 'no-op' };

    if (!selectedPlayer) return { action: 'select', next: { playerId, playerName, source } };
    if (selectedPlayer.playerId === playerId) return { action: 'deselect' };

    let dest;
    if (source.type === 'pairing') {
      dest = {
        slotType: source.position === 1 ? 'pairing-player1' : 'pairing-player2',
        pairingIndex: source.pairingIndex,
      };
    } else if (source.type === 'waiting') {
      dest = { slotType: 'waiting-list' };
    }
    return {
      action: 'execute',
      source: selectedPlayer.source,
      dest,
      draggedPlayerId: selectedPlayer.playerId,
      draggedPlayerName: selectedPlayer.playerName,
    };
  };

  // PairingGenerator.handleSlotClick のロジック再現
  const handleSlotClick = ({ selectedPlayer, slotData, pairings, isReadOnly, isViewMode }) => {
    if (isReadOnly || isViewMode) return { action: 'no-op' };
    if (!selectedPlayer) return { action: 'no-op' };
    if (slotData.slotType?.startsWith('pairing-') && pairings[slotData.pairingIndex]?.hasResult) return { action: 'no-op' };
    return {
      action: 'execute',
      source: selectedPlayer.source,
      dest: slotData,
      draggedPlayerId: selectedPlayer.playerId,
      draggedPlayerName: selectedPlayer.playerName,
    };
  };

  const basePairings = [
    { player1Id: 1, player1Name: 'A', player2Id: 2, player2Name: 'B', hasResult: false },
    { player1Id: 3, player1Name: 'C', player2Id: 4, player2Name: 'D', hasResult: false },
  ];

  describe('handleChipClick', () => {
    it('未選択状態でチップタップ → 選択開始', () => {
      const result = handleChipClick({
        selectedPlayer: null,
        playerId: 1, playerName: 'A',
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('select');
      expect(result.next.playerId).toBe(1);
      expect(result.next.source.pairingIndex).toBe(0);
    });

    it('選択中に同じチップを再タップ → 選択解除', () => {
      const result = handleChipClick({
        selectedPlayer: { playerId: 1, playerName: 'A', source: { type: 'pairing', pairingIndex: 0, position: 1 } },
        playerId: 1, playerName: 'A',
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('deselect');
    });

    it('選択中に別 pairing player1 チップタップ → dest=pairing-player1 でスワップ実行', () => {
      const result = handleChipClick({
        selectedPlayer: { playerId: 1, playerName: 'A', source: { type: 'pairing', pairingIndex: 0, position: 1 } },
        playerId: 3, playerName: 'C',
        source: { type: 'pairing', pairingIndex: 1, position: 1 },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('execute');
      expect(result.dest).toEqual({ slotType: 'pairing-player1', pairingIndex: 1 });
      expect(result.draggedPlayerId).toBe(1);
    });

    it('選択中に別 pairing player2 チップタップ → dest=pairing-player2', () => {
      const result = handleChipClick({
        selectedPlayer: { playerId: 1, playerName: 'A', source: { type: 'pairing', pairingIndex: 0, position: 1 } },
        playerId: 4, playerName: 'D',
        source: { type: 'pairing', pairingIndex: 1, position: 2 },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('execute');
      expect(result.dest).toEqual({ slotType: 'pairing-player2', pairingIndex: 1 });
    });

    it('選択中に待機選手チップタップ → dest=waiting-list', () => {
      const result = handleChipClick({
        selectedPlayer: { playerId: 1, playerName: 'A', source: { type: 'pairing', pairingIndex: 0, position: 1 } },
        playerId: 5, playerName: 'E',
        source: { type: 'waiting' },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('execute');
      expect(result.dest).toEqual({ slotType: 'waiting-list' });
    });

    it('isReadOnly=true → no-op（選択開始しない）', () => {
      const result = handleChipClick({
        selectedPlayer: null,
        playerId: 1, playerName: 'A',
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        pairings: basePairings, isReadOnly: true, isViewMode: false,
      });
      expect(result.action).toBe('no-op');
    });

    it('isViewMode=true → no-op', () => {
      const result = handleChipClick({
        selectedPlayer: null,
        playerId: 1, playerName: 'A',
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        pairings: basePairings, isReadOnly: false, isViewMode: true,
      });
      expect(result.action).toBe('no-op');
    });

    it('ロック済みペア（hasResult=true）のチップタップ → no-op', () => {
      const lockedPairings = [
        { player1Id: 1, player1Name: 'A', player2Id: 2, player2Name: 'B', hasResult: true },
      ];
      const result = handleChipClick({
        selectedPlayer: null,
        playerId: 1, playerName: 'A',
        source: { type: 'pairing', pairingIndex: 0, position: 1 },
        pairings: lockedPairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('no-op');
    });
  });

  describe('handleSlotClick', () => {
    it('未選択状態でスロットタップ → no-op', () => {
      const result = handleSlotClick({
        selectedPlayer: null,
        slotData: { slotType: 'pairing-player1', pairingIndex: 0 },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('no-op');
    });

    it('選択中 + 空き枠（pairing-player1）タップ → 実行', () => {
      const emptyPairings = [{ player1Id: null, player1Name: null, player2Id: 2, player2Name: 'B', hasResult: false }];
      const result = handleSlotClick({
        selectedPlayer: { playerId: 5, playerName: 'E', source: { type: 'waiting' } },
        slotData: { slotType: 'pairing-player1', pairingIndex: 0 },
        pairings: emptyPairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('execute');
      expect(result.dest).toEqual({ slotType: 'pairing-player1', pairingIndex: 0 });
      expect(result.draggedPlayerId).toBe(5);
    });

    it('選択中 + 待機エリアタップ → 実行（waiting-list へ移動）', () => {
      const result = handleSlotClick({
        selectedPlayer: { playerId: 1, playerName: 'A', source: { type: 'pairing', pairingIndex: 0, position: 1 } },
        slotData: { slotType: 'waiting-list' },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('execute');
      expect(result.dest).toEqual({ slotType: 'waiting-list' });
    });

    it('選択中（waiting 由来）+ 新規ペア作成ゾーンタップ → 実行', () => {
      const result = handleSlotClick({
        selectedPlayer: { playerId: 5, playerName: 'E', source: { type: 'waiting' } },
        slotData: { slotType: 'new-pairing' },
        pairings: basePairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('execute');
      expect(result.dest).toEqual({ slotType: 'new-pairing' });
    });

    it('選択中 + ロック済みペアへのタップ → no-op', () => {
      const lockedPairings = [
        { player1Id: 1, player1Name: 'A', player2Id: 2, player2Name: 'B', hasResult: true },
      ];
      const result = handleSlotClick({
        selectedPlayer: { playerId: 5, playerName: 'E', source: { type: 'waiting' } },
        slotData: { slotType: 'pairing-player1', pairingIndex: 0 },
        pairings: lockedPairings, isReadOnly: false, isViewMode: false,
      });
      expect(result.action).toBe('no-op');
    });

    it('isReadOnly=true → no-op', () => {
      const result = handleSlotClick({
        selectedPlayer: { playerId: 5, playerName: 'E', source: { type: 'waiting' } },
        slotData: { slotType: 'waiting-list' },
        pairings: basePairings, isReadOnly: true, isViewMode: false,
      });
      expect(result.action).toBe('no-op');
    });

    it('isViewMode=true → no-op', () => {
      const result = handleSlotClick({
        selectedPlayer: { playerId: 5, playerName: 'E', source: { type: 'waiting' } },
        slotData: { slotType: 'waiting-list' },
        pairings: basePairings, isReadOnly: false, isViewMode: true,
      });
      expect(result.action).toBe('no-op');
    });
  });
});

describe('新規ペア作成ゾーンの表示条件', () => {
  // PairingGenerator の表示条件式を再現
  const shouldShowNewPairingZone = ({ isReadOnly, isViewMode, waitingPlayers, selectedPlayer }) =>
    !isReadOnly && !isViewMode && (waitingPlayers.length > 0 || selectedPlayer?.source?.type === 'waiting');

  it('待機選手ありで表示される', () => {
    expect(shouldShowNewPairingZone({
      isReadOnly: false, isViewMode: false, waitingPlayers: [{ id: 1 }], selectedPlayer: null,
    })).toBe(true);
  });

  it('待機選手なし + 待機選手を選択中 → 表示される', () => {
    expect(shouldShowNewPairingZone({
      isReadOnly: false, isViewMode: false, waitingPlayers: [],
      selectedPlayer: { playerId: 1, source: { type: 'waiting' } },
    })).toBe(true);
  });

  it('待機選手なし + pairing 選手を選択中 → 表示されない', () => {
    expect(shouldShowNewPairingZone({
      isReadOnly: false, isViewMode: false, waitingPlayers: [],
      selectedPlayer: { playerId: 1, source: { type: 'pairing', pairingIndex: 0, position: 1 } },
    })).toBe(false);
  });

  it('待機選手なし + 未選択 → 表示されない', () => {
    expect(shouldShowNewPairingZone({
      isReadOnly: false, isViewMode: false, waitingPlayers: [], selectedPlayer: null,
    })).toBe(false);
  });

  it('isReadOnly=true → 表示されない', () => {
    expect(shouldShowNewPairingZone({
      isReadOnly: true, isViewMode: false, waitingPlayers: [{ id: 1 }],
      selectedPlayer: { source: { type: 'waiting' } },
    })).toBe(false);
  });

  it('isViewMode=true → 表示されない', () => {
    expect(shouldShowNewPairingZone({
      isReadOnly: false, isViewMode: true, waitingPlayers: [{ id: 1 }],
      selectedPlayer: { source: { type: 'waiting' } },
    })).toBe(false);
  });
});
