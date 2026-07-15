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

describe('TorifudaBoard 不明プールの決まり字順ソート（C-7: 並び替えはメンバー・計数を変えない）', () => {
  it('不明プールの表示順が変わっても、メンバー集合と「残り X / 母数」の計数は変わらない', () => {
    // 順不同の札番号配列。43(あい)/1(あきの)/87(む)/18(す) は不明のまま、
    // 5(おく) だけ配置済みにする
    const cards = [43, 1, 87, 18, 5];
    const placements = { 5: { takenBy: 'SELF', field: 'OWN', side: 'LEFT', tier: 'TOP' } };
    const onChange = vi.fn();

    const { container } = render(
      <TorifudaBoard
        cards={cards}
        placements={placements}
        onChange={onChange}
        scoreDifference={0}
      />
    );

    // (a) 不明プールのメンバー集合 = 配置されていない札の集合
    const poolChips = Array.from(container.querySelectorAll('.tr-pool-wrap .tr-chip'));
    expect(poolChips).toHaveLength(4);
    const expectedUnplaced = cards.filter((c) => !placements[c]);
    expect(poolChips).toHaveLength(expectedUnplaced.length);

    // 決まり字順（む→す→…→あ）にソートされて表示される
    expect(poolChips.map((el) => el.textContent)).toEqual(['む', 'す', 'あい', 'あきの']);

    // (b) 「残り X / 母数」の計数は配置数からの算出どおり（並び替えの影響を受けない）
    const countText = container.querySelector('.tr-pool-hd .c').textContent;
    const placeable = Math.max(0, 50 - 0);
    const remaining = Math.max(0, placeable - Object.keys(placements).length);
    expect(countText).toBe(`残り ${remaining} / ${placeable}枚`);
  });
});
