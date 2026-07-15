import { describe, it, expect } from 'vitest';
import { compareCardsByDecisionOrder, compareKimariji } from './kimariji';

describe('決まり字順ソート', () => {
  it('C-6: 決まり字1文字目が むすめふさほせ… の順に並ぶ', () => {
    // 87(む), 18(す), 57(め), 22(ふ), 70(さ), 81(ほ), 77(せ) をシャッフルして与える
    const shuffled = [77, 22, 87, 81, 18, 70, 57];
    const sorted = [...shuffled].sort(compareCardsByDecisionOrder);
    expect(sorted).toEqual([87, 18, 57, 22, 70, 81, 77]);
  });

  it('同一1文字目内は決まり字の五十音順（ユーザー例: あい→あきか→あきの→あし）', () => {
    // 43(あい), 79(あきか), 1(あきの), 3(あし) をシャッフルして与える
    const shuffled = [3, 43, 1, 79];
    const sorted = [...shuffled].sort(compareCardsByDecisionOrder);
    expect(sorted).toEqual([43, 79, 1, 3]);
  });

  it('共札は「・」を除去して比較する（わた・こ が わた・や より先）', () => {
    const shuffled = [11, 76]; // 11=わた・や, 76=わた・こ
    const sorted = [...shuffled].sort(compareCardsByDecisionOrder);
    expect(sorted).toEqual([76, 11]);
  });

  it('未知/範囲外の札番号でもクラッシュせず末尾に安定ソートされる', () => {
    // 999 はマスター未定義 → kimariji は "999" を返し1文字目が並び順表に無い＝末尾
    const sorted = [...[999, 43, 3]].sort(compareCardsByDecisionOrder);
    expect(sorted).toEqual([43, 3, 999]); // あい(43) → あし(3) → 999
  });

  it('compareKimariji の比較結果（負・正・0）', () => {
    expect(compareKimariji('あい', 'あきの')).toBeLessThan(0);
    expect(compareKimariji('あきの', 'あい')).toBeGreaterThan(0);
    expect(compareKimariji('あい', 'あい')).toBe(0);
  });
});
