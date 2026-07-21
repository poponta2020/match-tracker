import { describe, it, expect } from 'vitest';
import { getOrgShortName } from './organization';

describe('getOrgShortName', () => {
  it('override: hokudai は「北大」（フォールバックだと退行するため維持）', () => {
    expect(getOrgShortName({ code: 'hokudai', name: '北海道大学かるた会' })).toBe('北大');
  });

  it('override: wasura は「わすら」', () => {
    expect(getOrgShortName({ code: 'wasura', name: 'わすらもち会' })).toBe('わすら');
  });

  it('フォールバック: 未登録コードは name の先頭2文字', () => {
    expect(getOrgShortName({ code: 'myclub', name: '○○かるた会' })).toBe('○○');
  });

  it('null / undefined は空文字', () => {
    expect(getOrgShortName(null)).toBe('');
    expect(getOrgShortName(undefined)).toBe('');
  });

  it('name 欠落（フォールバック不能）は空文字', () => {
    expect(getOrgShortName({ code: 'unknown' })).toBe('');
  });
});
