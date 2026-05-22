import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../api', () => ({
  matchAPI: {
    getByPlayerId: vi.fn(),
    getStatisticsByRank: vi.fn(),
  },
  playerAPI: {
    getById: vi.fn(),
    getAll: vi.fn(),
  },
}));

vi.mock('../../api/mentorRelationship', () => ({
  mentorRelationshipAPI: {
    getMyMentees: vi.fn(),
  },
}));

const currentPlayer = { id: 1, name: 'テスト選手', kyuRank: 'C級' };
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer }),
}));

vi.mock('../../components/FilterBottomSheet', () => ({
  default: () => null,
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div data-testid="loading-screen">Loading...</div>,
}));

import { matchAPI, playerAPI } from '../../api';
import { mentorRelationshipAPI } from '../../api/mentorRelationship';
import MatchList from './MatchList';

const today = new Date();
const todayDateStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-15`;

const emptyRankStats = {
  total: { total: 0, wins: 0, losses: 0, winRate: 0 },
  byRank: {
    'A級': { total: 0, wins: 0, losses: 0, winRate: 0 },
    'B級': { total: 0, wins: 0, losses: 0, winRate: 0 },
    'C級': { total: 0, wins: 0, losses: 0, winRate: 0 },
    'D級': { total: 0, wins: 0, losses: 0, winRate: 0 },
    'E級': { total: 0, wins: 0, losses: 0, winRate: 0 },
  },
};

const buildMatch = (overrides = {}) => ({
  id: 100,
  matchDate: todayDateStr,
  opponentName: '山田太郎',
  player1Id: 1,
  player2Id: 2,
  myPersonalNotes: '',
  menteePersonalNotes: '',
  myOtetsukiCount: null,
  menteeOtetsukiCount: null,
  result: '勝ち',
  scoreDifference: 3,
  ...overrides,
});

describe('MatchList - 同一ルート内ナビゲーション統合テスト（実useNavigate）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    matchAPI.getStatisticsByRank.mockResolvedValue({ data: emptyRankStats });
    playerAPI.getById.mockResolvedValue({ data: { name: '対戦相手選手', kyuRank: 'B級' } });
    playerAPI.getAll.mockResolvedValue({ data: [] });
    mentorRelationshipAPI.getMyMentees.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    cleanup();
  });

  it('対戦相手名タップで URL が変わり、新 playerId で matchAPI.getByPlayerId が再呼び出しされる', async () => {
    // 初期表示（自分=1の対戦一覧、対戦相手=2）
    matchAPI.getByPlayerId.mockImplementation((playerId) => {
      if (playerId === 1) {
        return Promise.resolve({
          data: [buildMatch({ player1Id: 1, player2Id: 2, opponentName: '山田太郎' })],
        });
      }
      if (playerId === 2) {
        return Promise.resolve({
          data: [buildMatch({ id: 200, player1Id: 2, player2Id: 3, opponentName: '佐藤花子' })],
        });
      }
      return Promise.resolve({ data: [] });
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/matches']}>
        <Routes>
          <Route path="/matches" element={<MatchList />} />
        </Routes>
      </MemoryRouter>
    );

    // 初期: 山田太郎が表示される
    const yamadaBtn = await screen.findByRole('button', { name: '山田太郎' });
    expect(matchAPI.getByPlayerId).toHaveBeenCalledWith(1, expect.any(Object));

    // 対戦相手名タップで URL が /matches?playerId=2 に切り替わる
    await user.click(yamadaBtn);

    // 新しい playerId=2 で matchAPI.getByPlayerId が呼ばれる
    await waitFor(() => {
      expect(matchAPI.getByPlayerId).toHaveBeenCalledWith(2, expect.any(Object));
    });

    // 他選手閲覧時はメンター関係 API も呼ばれる
    await waitFor(() => {
      expect(mentorRelationshipAPI.getMyMentees).toHaveBeenCalled();
    });

    // 旧データ（山田太郎の行）が残らず、新データ（佐藤花子の行）に切り替わる
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '山田太郎' })).toBeNull();
      expect(screen.getByRole('button', { name: '佐藤花子' })).toBeInTheDocument();
    });
  });
});
