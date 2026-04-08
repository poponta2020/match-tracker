import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// API モック
vi.mock('../../api', () => ({
  matchAPI: { getAll: vi.fn(), getById: vi.fn() },
  playerAPI: { getAll: vi.fn() },
  practiceAPI: {
    getByDate: vi.fn(),
    getPlayerParticipations: vi.fn(),
  },
  pairingAPI: { getBySession: vi.fn() },
  byeActivityAPI: { getByPlayerAndDate: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'テスト選手' },
  }),
}));

import { playerAPI, practiceAPI, pairingAPI, byeActivityAPI } from '../../api';
import MatchForm from './MatchForm';

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

/**
 * MatchResultsView → MatchForm 遷移時の state 引き継ぎテスト
 *
 * navigate('/matches/new', { state: { matchDate, matchNumber } }) で遷移した際に、
 * MatchForm が以下を正しく処理することを検証する:
 * 1. practiceAPI.getByDate が渡された日付で呼ばれること
 * 2. matchNumber が初期値として反映されること（タブUIの選択状態）
 * 3. 参加登録ダイアログが当日遷移時のみ表示されること
 */

const renderWithRouter = (locationState = {}) => {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/matches/new', state: locationState }]}>
      <Routes>
        <Route path="/matches/new" element={<MatchForm />} />
      </Routes>
    </MemoryRouter>
  );
};

const setupDefaultMocks = (sessionData = null) => {
  playerAPI.getAll.mockResolvedValue({ data: [{ id: 2, name: '対戦相手' }] });
  practiceAPI.getByDate.mockResolvedValue({ data: sessionData });
  practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
  pairingAPI.getBySession.mockResolvedValue({ data: [] });
  byeActivityAPI.getByPlayerAndDate.mockResolvedValue({ data: [] });
};

describe('MatchResultsView → MatchForm 遷移時の state 引き継ぎ', () => {

  it('指定された日付で practiceAPI.getByDate が呼ばれる', async () => {
    setupDefaultMocks();
    renderWithRouter({ matchDate: '2025-12-15', matchNumber: 2 });

    await waitFor(() => {
      expect(practiceAPI.getByDate).toHaveBeenCalledWith('2025-12-15');
    });
  });

  it('state なしの場合は今日の日付で practiceAPI.getByDate が呼ばれる', async () => {
    setupDefaultMocks();
    const today = new Date().toISOString().split('T')[0];
    renderWithRouter({});

    await waitFor(() => {
      expect(practiceAPI.getByDate).toHaveBeenCalledWith(today);
    });
  });

  it('指定された試合番号がタブで選択状態になる', async () => {
    const sessionData = { id: 100, date: '2025-12-15', totalMatches: 5 };
    setupDefaultMocks(sessionData);
    renderWithRouter({ matchDate: '2025-12-15', matchNumber: 3 });

    await waitFor(() => {
      // 第3試合タブが選択状態（border-white text-white）であることを確認
      const tab3 = screen.getByText('第3試合');
      expect(tab3.className).toContain('border-white text-white');
    });
  });

  it('当日の練習セッションがあり未参加の場合に参加登録ダイアログが表示される', async () => {
    const today = new Date().toISOString().split('T')[0];
    const sessionData = { id: 100, date: today, totalMatches: 3 };

    setupDefaultMocks(sessionData);
    practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });

    renderWithRouter({ matchDate: today, matchNumber: 1 });

    await waitFor(() => {
      expect(screen.getByText(/参加登録/)).toBeInTheDocument();
    });
  });

  it('過去日の練習セッションでは参加登録ダイアログが表示されない', async () => {
    const pastDate = '2025-01-01';
    const sessionData = { id: 100, date: pastDate, totalMatches: 3 };

    setupDefaultMocks(sessionData);

    renderWithRouter({ matchDate: pastDate, matchNumber: 1 });

    // 初期ローディングが完了するまで待つ
    await waitFor(() => {
      expect(practiceAPI.getByDate).toHaveBeenCalledWith(pastDate);
    });

    // 少し待っても参加登録ダイアログが表示されないことを確認
    await new Promise(r => setTimeout(r, 100));
    expect(screen.queryByText(/参加登録/)).not.toBeInTheDocument();
  });
});
