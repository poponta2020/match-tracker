import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useParams } from 'react-router-dom';

// API モック
vi.mock('../../api', () => ({
  matchAPI: {
    getAll: vi.fn(),
    getById: vi.fn(),
    getCardRecord: vi.fn(() => Promise.resolve({ data: { cardPlacements: [], otetsukiDetails: [] } })),
    saveCardRecord: vi.fn(() => Promise.resolve({ data: {} })),
    getByPlayerDateAndMatchNumber: vi.fn(() => Promise.reject(new Error('not found'))),
    createDetailed: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
  playerAPI: { getAll: vi.fn() },
  practiceAPI: {
    getByDate: vi.fn(),
    getPlayerParticipations: vi.fn(),
  },
  pairingAPI: { getBySession: vi.fn(), getByDate: vi.fn(() => Promise.resolve({ data: [] })) },
  byeActivityAPI: {
    getByPlayerAndDate: vi.fn(),
    getByDate: vi.fn(() => Promise.resolve({ data: [] })),
    create: vi.fn(() => Promise.resolve({ data: {} })),
    update: vi.fn(() => Promise.resolve({ data: {} })),
  },
  cardRuleNonceAPI: {
    getByDate: vi.fn(() => Promise.resolve({ data: { nonce: 0 } })),
    update: vi.fn(() => Promise.resolve({ data: {} })),
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'テスト選手' },
  }),
}));

import { matchAPI, playerAPI, practiceAPI, pairingAPI, byeActivityAPI } from '../../api';
import MatchForm from './MatchForm';

// 遷移先の内容確認用ダミー画面
const MatchDetailStub = () => {
  const { id } = useParams();
  return <div>試合詳細ページ:{id}</div>;
};
const HomeStub = () => <div>ホーム画面</div>;

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
        <Route path="/matches/:id" element={<MatchDetailStub />} />
        <Route path="/" element={<HomeStub />} />
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
      // 第3試合タブ（「3試合目」）が選択状態（border-white text-white）であることを確認
      const tab3 = screen.getByText('3試合目');
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

/**
 * 個人結果入力（新規）の保存成功後の遷移先テスト
 *
 * C-11: 保存成功後は `/` ではなく `/matches/:id` へ遷移する
 * C-16（回帰）: 抜け番活動保存後の遷移（→`/`）は不変
 */
describe('個人結果入力（新規）保存後の遷移先', () => {
  const pastDate = '2025-01-01';

  it('対戦相手ありの新規試合保存に成功すると /matches/:id へ遷移する', async () => {
    const sessionData = { id: 100, date: pastDate, totalMatches: 1 };
    setupDefaultMocks(sessionData);
    // 自分を含むペアリングを1件返す → applyMatchData が opponentId を自動セットする
    pairingAPI.getByDate.mockResolvedValue({
      data: [
        { matchNumber: 1, player1Id: 1, player2Id: 2, player1Name: 'テスト選手', player2Name: '対戦相手さん' },
      ],
    });
    matchAPI.createDetailed.mockResolvedValue({ data: { id: 555 } });

    renderWithRouter({ matchDate: pastDate, matchNumber: 1 });

    // 対戦相手が自動反映されるまで待つ
    await waitFor(() => {
      expect(screen.getByText('対戦相手さん')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(matchAPI.createDetailed).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByText('試合詳細ページ:555')).toBeInTheDocument();
    });
  });

  it('（回帰）抜け番活動として保存した場合は / のまま（/matches/:id へは遷移しない）', async () => {
    const sessionData = { id: 100, date: pastDate, totalMatches: 1 };
    setupDefaultMocks(sessionData);
    // 自分以外のペアリングのみ → 抜け番判定（isByeMatch = true）
    pairingAPI.getByDate.mockResolvedValue({
      data: [
        { matchNumber: 1, player1Id: 3, player2Id: 4, player1Name: 'Xさん', player2Name: 'Yさん' },
      ],
    });
    byeActivityAPI.create.mockResolvedValue({ data: {} });

    renderWithRouter({ matchDate: pastDate, matchNumber: 1 });

    await waitFor(() => {
      expect(screen.getByText(/抜け番です/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('休み'));
    fireEvent.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(byeActivityAPI.create).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByText('ホーム画面')).toBeInTheDocument();
    });
    expect(screen.queryByText(/試合詳細ページ/)).not.toBeInTheDocument();
    expect(matchAPI.createDetailed).not.toHaveBeenCalled();
    expect(matchAPI.create).not.toHaveBeenCalled();
  });
});
