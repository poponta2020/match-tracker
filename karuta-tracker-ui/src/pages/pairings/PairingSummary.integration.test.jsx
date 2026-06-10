import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { STORAGE_PREFIX, generateCardRules } from './cardRules';

const mocks = vi.hoisted(() => ({
  getByDate: vi.fn(),
  getByDateAndMatchNumber: vi.fn(),
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

vi.mock('../../components/PageHeader', () => ({
  default: ({ title }) => <div data-testid="page-header">{title}</div>,
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div data-testid="loading">Loading...</div>,
}));

import PairingSummary from './PairingSummary';

const TODAY = '2026-06-09';

const renderAtDate = (date = TODAY) =>
  render(
    <MemoryRouter initialEntries={[`/pairings/summary?date=${date}`]}>
      <Routes>
        <Route path="/pairings/summary" element={<PairingSummary />} />
      </Routes>
    </MemoryRouter>
  );

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0)); // 2026-06-09 12:00 ローカル

  mocks.getByDate.mockResolvedValue({ data: { totalMatches: 3 } });
  mocks.getByDateAndMatchNumber.mockResolvedValue({
    data: [{ player1Name: '田中', player2Name: '佐藤' }],
  });
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  localStorage.clear();
});

describe('PairingSummary 札ルール localStorage 永続化', () => {
  it('シナリオ(1): 初回ロードで今日のキーに札ルールが保存される', async () => {
    renderAtDate(TODAY);

    await waitFor(() => {
      expect(localStorage.getItem(STORAGE_PREFIX + TODAY)).not.toBeNull();
    });

    const stored = JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY));
    expect(Array.isArray(stored)).toBe(true);
    expect(stored).toHaveLength(3);
    expect(stored[0].type).toBe('ones');
    expect(stored[1].type).toBe('nuki');
    expect(stored[2].type).toBe('tens');
  });

  it('シナリオ(2): 同日リロードで localStorage の値がそのまま復元される', async () => {
    const stored = generateCardRules(3);
    localStorage.setItem(STORAGE_PREFIX + TODAY, JSON.stringify(stored));

    renderAtDate(TODAY);

    await waitFor(() => {
      const ta = screen.getByRole('textbox');
      expect(ta).toBeInTheDocument();
    });

    // localStorage の値は変わらない（上書きされていない）
    expect(JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY))).toEqual(stored);

    // テキストエリアに復元された札ルール description が表示される
    const ta = screen.getByRole('textbox');
    for (const rule of stored) {
      expect(ta.value).toContain(rule.description);
    }
  });

  it('過去日（today 以外）アクセスでは localStorage に保存されない', async () => {
    const pastDate = '2026-06-08';
    renderAtDate(pastDate);

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    expect(localStorage.getItem(STORAGE_PREFIX + pastDate)).toBeNull();
  });

  it('画面ロード時に今日以外の保存値が削除される（cleanupOldCardRules）', async () => {
    const oldRules = generateCardRules(3);
    localStorage.setItem(STORAGE_PREFIX + '2026-06-08', JSON.stringify(oldRules));
    localStorage.setItem(STORAGE_PREFIX + '2025-12-31', JSON.stringify(oldRules));

    renderAtDate(TODAY);

    await waitFor(() => {
      expect(localStorage.getItem(STORAGE_PREFIX + TODAY)).not.toBeNull();
    });

    expect(localStorage.getItem(STORAGE_PREFIX + '2026-06-08')).toBeNull();
    expect(localStorage.getItem(STORAGE_PREFIX + '2025-12-31')).toBeNull();
  });

  it('シナリオ(3): 保存値が短い（試合数 < totalMatches）→ 末尾追加＆上書き保存', async () => {
    const shorterStored = generateCardRules(2);
    localStorage.setItem(STORAGE_PREFIX + TODAY, JSON.stringify(shorterStored));
    mocks.getByDate.mockResolvedValue({ data: { totalMatches: 4 } });

    renderAtDate(TODAY);

    await waitFor(() => {
      const updated = JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY));
      expect(updated).toHaveLength(4);
    });

    const updated = JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY));
    expect(updated[0]).toEqual(shorterStored[0]);
    expect(updated[1]).toEqual(shorterStored[1]);
  });

  it('シナリオ(4): 保存値が長い（試合数 > totalMatches）→ 表示は切り詰め、localStorage は保持', async () => {
    const longerStored = generateCardRules(5);
    localStorage.setItem(STORAGE_PREFIX + TODAY, JSON.stringify(longerStored));
    mocks.getByDate.mockResolvedValue({ data: { totalMatches: 3 } });

    renderAtDate(TODAY);

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    // localStorage は 5 件のまま保持される
    const afterRender = JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY));
    expect(afterRender).toHaveLength(5);
    expect(afterRender).toEqual(longerStored);

    // 表示は 3 件分のみ
    const ta = screen.getByRole('textbox');
    expect(ta.value).toContain(longerStored[0].description);
    expect(ta.value).toContain(longerStored[1].description);
    expect(ta.value).toContain(longerStored[2].description);
    expect(ta.value).not.toContain(longerStored[3].description);
  });

  it('シナリオ(5a): 札再生成ボタンで confirm キャンセル → localStorage 不変', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const stored = generateCardRules(3);
    localStorage.setItem(STORAGE_PREFIX + TODAY, JSON.stringify(stored));

    window.confirm = vi.fn().mockReturnValue(false);

    renderAtDate(TODAY);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: /札を再生成/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY))).toEqual(stored);
  });

  it('シナリオ(5b): 札再生成ボタンで confirm OK → localStorage 上書き', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const stored = generateCardRules(3);
    localStorage.setItem(STORAGE_PREFIX + TODAY, JSON.stringify(stored));

    window.confirm = vi.fn().mockReturnValue(true);

    renderAtDate(TODAY);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: /札を再生成/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    const afterRegen = JSON.parse(localStorage.getItem(STORAGE_PREFIX + TODAY));
    expect(afterRegen).toHaveLength(3);
    expect(afterRegen[0].type).toBe('ones');
    expect(afterRegen[1].type).toBe('nuki');
    expect(afterRegen[2].type).toBe('tens');
    // 構造は同じだが、再生成なので元の stored とは別物（高確率で異なる）
    // 厳密一致は確率的に揺れるためチェックしない
  });

  it('過去日の再生成では localStorage に書き込まない', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const pastDate = '2026-06-08';
    window.confirm = vi.fn().mockReturnValue(true);

    renderAtDate(pastDate);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /札を再生成/ })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: /札を再生成/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem(STORAGE_PREFIX + pastDate)).toBeNull();
  });
});
