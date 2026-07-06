import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getCardRules, NONCE_PREFIX } from './cardRules';

const mocks = vi.hoisted(() => ({
  getByDate: vi.fn(),
  getByDateAndMatchNumber: vi.fn(),
  byeGetByDate: vi.fn(),
  nonceGetByDate: vi.fn(),
  nonceUpdate: vi.fn(),
}));

vi.mock('../../api/practices', () => ({
  practiceAPI: {
    getByDate: mocks.getByDate,
  },
}));

vi.mock('../../api/pairings', () => ({
  pairingAPI: {
    getByDateAndMatchNumber: mocks.getByDateAndMatchNumber,
  },
}));

vi.mock('../../api/byeActivities', () => ({
  byeActivityAPI: {
    getByDate: mocks.byeGetByDate,
  },
}));

vi.mock('../../api/cardRuleNonce', () => ({
  cardRuleNonceAPI: {
    getByDate: mocks.nonceGetByDate,
    update: mocks.nonceUpdate,
  },
}));

vi.mock('../../components/PageHeader', () => ({
  default: ({ title }) => <div data-testid="page-header">{title}</div>,
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div data-testid="loading">Loading...</div>,
}));

import PairingSummary from './PairingSummary';

const TODAY = '2026-06-09';

// 試合番号ごとに区別できるペア名（数字を含めず単一試合モードの包含/除外を明確に判定）
const NAMES = {
  1: ['田中', '佐藤'],
  2: ['鈴木', '高橋'],
  3: ['渡辺', '伊藤'],
};

const renderAt = (date = TODAY, matchNumber) => {
  const query = matchNumber == null ? `date=${date}` : `date=${date}&matchNumber=${matchNumber}`;
  return render(
    <MemoryRouter initialEntries={[`/pairings/summary?${query}`]}>
      <Routes>
        <Route path="/pairings/summary" element={<PairingSummary />} />
      </Routes>
    </MemoryRouter>
  );
};

const getValue = () => screen.getByRole('textbox').value;

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0)); // 2026-06-09 12:00 ローカル

  mocks.getByDate.mockResolvedValue({ data: { totalMatches: 3 } });
  mocks.getByDateAndMatchNumber.mockImplementation((date, num) =>
    Promise.resolve({ data: [{ player1Name: NAMES[num][0], player2Name: NAMES[num][1] }] })
  );
  mocks.byeGetByDate.mockResolvedValue({ data: [] });
  // 札ルール nonce の DB 共有 API（未登録日は 0）。update は成功を返す。
  mocks.nonceGetByDate.mockResolvedValue({ data: { nonce: 0 } });
  mocks.nonceUpdate.mockResolvedValue({ data: {} });
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  localStorage.clear();
});

describe('PairingSummary 札ルールの日付シード決定論化（Part A）', () => {
  it('同じ日を2回ロードすると同一テキストになる（決定論・再訪で不変）', async () => {
    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    const first = getValue();

    cleanup();
    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    const second = getValue();

    expect(second).toBe(first);
  });

  it('表示される札ルールは getCardRules(date, totalMatches) と一致する', async () => {
    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const rules = getCardRules(TODAY, 3); // nonce=0（未保存）
    const v = getValue();
    for (const rule of rules) {
      expect(v).toContain(rule.description);
    }
  });

  it('札ルール配列は localStorage に保存されない（nonce のみ管理）', async () => {
    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    // 旧形式の札ルール配列キーは書かれない
    for (let i = 0; i < localStorage.length; i++) {
      expect(localStorage.key(i)).not.toMatch(/^karuta-tracker:card-rules:/);
    }
  });

  it('過去日でも決定論的に再現でき、再訪で不変', async () => {
    const pastDate = '2026-06-08';
    renderAt(pastDate);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    const first = getValue();

    cleanup();
    renderAt(pastDate);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    expect(getValue()).toBe(first);
  });

  it('札再生成（confirm OK）で nonce が保存され、テキストが変わる', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    window.confirm = vi.fn().mockReturnValue(true);

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument());
    const before = getValue();

    await user.click(screen.getByRole('button', { name: /札を再生成/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    // handleRegenerate は非同期（DB更新を await）。nonce が +1 で DB更新・localStorage保存され、テキストが変わる
    await waitFor(() => {
      expect(mocks.nonceUpdate).toHaveBeenCalledWith(TODAY, 1);
      expect(localStorage.getItem(NONCE_PREFIX + TODAY)).toBe('1');
      expect(getValue()).not.toBe(before);
    });
  });

  it('札再生成（confirm キャンセル）では nonce を変更しない', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    window.confirm = vi.fn().mockReturnValue(false);

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument());
    const before = getValue();

    await user.click(screen.getByRole('button', { name: /札を再生成/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem(NONCE_PREFIX + TODAY)).toBeNull();
    expect(getValue()).toBe(before);
  });

  it('localStorage 保存に失敗しても「札を再生成」で画面の札ルールは変わる', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    window.confirm = vi.fn().mockReturnValue(true);

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument());
    const before = getValue();

    // saveNonce の localStorage.setItem を失敗させる（QuotaExceeded 等を模擬）
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    await user.click(screen.getByRole('button', { name: /札を再生成/ }));

    // 保存は失敗しても nextNonce で再計算されるため、画面の札ルールテキストは変わる（非同期）
    await waitFor(() => expect(getValue()).not.toBe(before));
    setItemSpy.mockRestore();
  });

  it('過去日（今日以外）では「札を再生成」ボタンが非表示（当日のみ再生成可）', async () => {
    renderAt('2026-06-08'); // 今日(2026-06-09)以外
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    expect(screen.queryByRole('button', { name: /札を再生成/ })).not.toBeInTheDocument();
    // テキスト自体は決定論の既定で表示される
    expect(getValue()).toContain('1試合目');
  });
});

describe('PairingSummary 単一試合モード（Part B）', () => {
  it('matchNumber 指定時は対象試合のブロックのみ表示（日付見出し＋N試合目＋札ルール＋ペア）', async () => {
    renderAt(TODAY, 2);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const v = getValue();
    const rules = getCardRules(TODAY, 3);
    // 日付見出し
    expect(v.startsWith('6月9日')).toBe(true);
    // 対象（2試合目）の見出し・札ルール・ペア
    expect(v).toContain('2試合目');
    expect(v).toContain(rules[1].description);
    expect(v).toContain('鈴木');
    expect(v).toContain('高橋');
    // 他の試合は含まれない
    expect(v).not.toContain('1試合目');
    expect(v).not.toContain('3試合目');
    expect(v).not.toContain('田中');
    expect(v).not.toContain('渡辺');
  });

  it('単一試合テキストは全試合テキストの該当ブロックと完全一致する', async () => {
    // 全試合モードでレンダリングして該当ブロックを抽出
    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    const fullText = getValue();
    const blocks = fullText.split('\n\n'); // ['6月9日\n1試合目…', '2試合目…', '3試合目…']
    const dateHeader = blocks[0].split('\n')[0]; // '6月9日'
    // 本番 generateText はドキュメント末尾を trimEnd するため、最終行の末尾パディングは除去される。
    // 同じ trimEnd を期待値にも適用して比較（可視内容の完全一致を検証）。
    const expectedSingle = `${dateHeader}\n${blocks[1]}`.trimEnd(); // '6月9日\n2試合目…'

    cleanup();

    // 単一試合モード（2試合目）
    renderAt(TODAY, 2);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    expect(getValue()).toBe(expectedSingle);
  });

  it('単一試合モードでは「札を再生成」ボタンが非表示', async () => {
    renderAt(TODAY, 2);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    expect(screen.queryByRole('button', { name: /札を再生成/ })).not.toBeInTheDocument();
    // コピーボタンは表示される
    expect(screen.getByRole('button', { name: /コピー/ })).toBeInTheDocument();
  });

  it('無効な matchNumber（totalMatches 超過）は全試合モードにフォールバック', async () => {
    renderAt(TODAY, 99);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const v = getValue();
    expect(v).toContain('1試合目');
    expect(v).toContain('2試合目');
    expect(v).toContain('3試合目');
    // 全試合モードなので再生成ボタンは表示される
    expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument();
  });

  it('無効な matchNumber（0 / 数値でない / 部分数値文字列 / 小数 / 指数表記 / 先頭ゼロ / 負値）は全試合モードにフォールバック', async () => {
    // parseInt なら '2abc'→2 / '1.5'→1 / '1e2'→1 と先頭を拾ってしまうが、
    // 文字列全体が正の整数でなければ単一試合モードに入らず全試合モードにする
    for (const invalid of [0, 'abc', '2abc', '1.5', '1e2', '01', '-1']) {
      renderAt(TODAY, invalid);
      await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
      const v = getValue();
      expect(v).toContain('1試合目');
      expect(v).toContain('2試合目');
      expect(v).toContain('3試合目');
      // 全試合モードなので再生成ボタンが表示される（単一試合モードでは非表示）
      expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument();
      cleanup();
    }
  });

  it('対象試合のペアが空でもエラーにせず、日付見出し＋N試合目＋札ルールを表示（URL直接アクセス防御）', async () => {
    mocks.getByDateAndMatchNumber.mockImplementation((date, num) =>
      Promise.resolve({ data: num === 2 ? [] : [{ player1Name: NAMES[num][0], player2Name: NAMES[num][1] }] })
    );

    renderAt(TODAY, 2);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const rules = getCardRules(TODAY, 3);
    const v = getValue();
    expect(v.startsWith('6月9日')).toBe(true);
    expect(v).toContain('2試合目');
    expect(v).toContain(rules[1].description);
    // ペアが無いので他試合の選手名は出ない
    expect(v).not.toContain('田中');
  });

  it('単一試合モードでも対象試合の読手行を含む（全試合ブロックと一致）', async () => {
    mocks.byeGetByDate.mockResolvedValue({
      data: [{ matchNumber: 2, playerName: '山田太郎', activityType: 'READING' }],
    });

    renderAt(TODAY, 2);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const v = getValue();
    expect(v).toContain('【読手：山田太郎】');
    // 読手行は対象試合のペアより前
    expect(v.indexOf('【読手：山田太郎】')).toBeLessThan(v.indexOf('鈴木'));
  });
});

describe('PairingSummary 読手（読みに設定された抜け番選手）の表示', () => {
  it('READING の抜け番選手が各試合に【読手：○○】として、ペアより前に出力される', async () => {
    mocks.getByDate.mockResolvedValue({ data: { totalMatches: 2 } });
    mocks.byeGetByDate.mockResolvedValue({
      data: [
        { matchNumber: 1, playerName: '山田太郎', activityType: 'READING' },
        { matchNumber: 2, playerName: '山田花子', activityType: 'READING' },
        { matchNumber: 1, playerName: '見学者', activityType: 'OBSERVING' },
      ],
    });

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const v = getValue();
    expect(v).toContain('【読手：山田太郎】');
    expect(v).toContain('【読手：山田花子】');
    // READING 以外（見学）の選手は読手に含めない
    expect(v).not.toContain('見学者');
    // 読手行は対象試合のペア（田中）より前に置かれる
    expect(v.indexOf('【読手：山田太郎】')).toBeLessThan(v.indexOf('田中'));
  });

  it('同一試合に読手が複数いる場合は「、」区切りで列挙される', async () => {
    mocks.getByDate.mockResolvedValue({ data: { totalMatches: 1 } });
    mocks.byeGetByDate.mockResolvedValue({
      data: [
        { matchNumber: 1, playerName: '山田太郎', activityType: 'READING' },
        { matchNumber: 1, playerName: '田中次郎', activityType: 'READING' },
      ],
    });

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    expect(getValue()).toContain('【読手：山田太郎、田中次郎】');
  });

  it('読手が未設定の試合には【読手：】行を出力しない', async () => {
    mocks.getByDate.mockResolvedValue({ data: { totalMatches: 1 } });
    mocks.byeGetByDate.mockResolvedValue({ data: [] });

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    expect(getValue()).not.toContain('【読手：');
  });

  it('抜け番活動の取得に失敗しても読手なしでテキストを生成する', async () => {
    mocks.getByDate.mockResolvedValue({ data: { totalMatches: 1 } });
    mocks.byeGetByDate.mockRejectedValue(new Error('network error'));

    renderAt(TODAY);
    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());

    const v = getValue();
    expect(v).toContain('田中');
    expect(v).not.toContain('【読手：');
  });
});
