import { describe, it, expect } from 'vitest';
import { KIMARIJI, kimariji, ALL_KIMARIJI } from './kimariji';

describe('決まり字マスター', () => {
  it('100枚ぶん定義されている（1〜100）', () => {
    for (let n = 1; n <= 100; n++) {
      expect(typeof KIMARIJI[n]).toBe('string');
      expect(KIMARIJI[n].length).toBeGreaterThan(0);
    }
    expect(Object.keys(KIMARIJI)).toHaveLength(100);
  });

  it('決まり字はすべて一意（重複なし）', () => {
    const values = Object.values(KIMARIJI);
    expect(new Set(values).size).toBe(values.length);
  });

  it('決まり字は最大4文字（縦書きチップに収める）', () => {
    for (const v of Object.values(KIMARIJI)) {
      expect(v.length).toBeLessThanOrEqual(4);
    }
  });

  it('参照ファイルの補正が反映されている（041/068/082）', () => {
    expect(KIMARIJI[41]).toBe('こひ');
    expect(KIMARIJI[68]).toBe('こころに');
    expect(KIMARIJI[82]).toBe('おも');
  });

  it('共札の記法（共通字・区別字）', () => {
    expect(KIMARIJI[11]).toBe('わた・や');
    expect(KIMARIJI[76]).toBe('わた・こ');
    expect(KIMARIJI[15]).toBe('きみ・は');
    expect(KIMARIJI[50]).toBe('きみ・お');
    expect(KIMARIJI[83]).toBe('よの・よ');
    expect(KIMARIJI[93]).toBe('よの・は');
    expect(KIMARIJI[31]).toBe('あさぼあ');
    expect(KIMARIJI[64]).toBe('あさぼう');
  });

  it('kimariji(n) は該当決まり字、未定義は番号文字列', () => {
    expect(kimariji(87)).toBe('む');
    expect(kimariji(999)).toBe('999');
  });

  it('ALL_KIMARIJI は 100件 {no, kimariji} 番号順', () => {
    expect(ALL_KIMARIJI).toHaveLength(100);
    expect(ALL_KIMARIJI[0]).toEqual({ no: 1, kimariji: 'あきの' });
    expect(ALL_KIMARIJI[99]).toEqual({ no: 100, kimariji: 'もも' });
  });
});
