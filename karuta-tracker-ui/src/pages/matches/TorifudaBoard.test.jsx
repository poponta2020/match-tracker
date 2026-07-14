import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, cleanup, fireEvent } from '@testing-library/react';
import TorifudaBoard from './TorifudaBoard';

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('TorifudaBoard 札配置（マスが埋まっていても既存チップの上で配置できる）', () => {
  // 敵陣・右・下段・自分(取った) のマスに札5を配置済みの状態を作る
  const placedCell = { takenBy: 'SELF', field: 'ENEMY', side: 'RIGHT', tier: 'BOTTOM' };

  it('札を選択中に既存チップの上をタップすると、そのマスへ配置される（不明に戻さない）', () => {
    const onChange = vi.fn();
    const { container } = render(
      <TorifudaBoard
        cards={[5, 7]}
        placements={{ 5: placedCell }}
        onChange={onChange}
        scoreDifference={10}
      />
    );

    // 不明プールの札7をタップして選択（arm）
    const poolChip = container.querySelector('.tr-pool-wrap .tr-chip');
    expect(poolChip).toBeTruthy();
    fireEvent.click(poolChip);
    expect(onChange).not.toHaveBeenCalled(); // 選択だけでは配置しない

    // マス内に既に置かれている札5のチップの「上」をタップ
    const placedChip = container.querySelector('.tr-half .tr-chip');
    expect(placedChip).toBeTruthy();
    fireEvent.click(placedChip);

    // 選択中の札7が、そのマス（敵陣・右・下段・自分）へ配置される。札5はそのまま残る
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({
      5: placedCell,
      7: placedCell,
    });
  });

  it('札を選択していない時に既存チップをタップすると、その札を不明に戻す（従来動作）', () => {
    const onChange = vi.fn();
    const { container } = render(
      <TorifudaBoard
        cards={[5, 7]}
        placements={{ 5: placedCell }}
        onChange={onChange}
        scoreDifference={10}
      />
    );

    // 何も選択せずに、配置済みの札5のチップをタップ
    const placedChip = container.querySelector('.tr-half .tr-chip');
    fireEvent.click(placedChip);

    // 札5が取り除かれる（不明に戻る）
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({});
  });
});
