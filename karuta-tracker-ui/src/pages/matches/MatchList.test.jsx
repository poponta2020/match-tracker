import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

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
  default: () => <div>Loading...</div>,
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

const setupDefaultMocks = ({ matches, mentees, mentorPromise } = {}) => {
  matchAPI.getByPlayerId.mockResolvedValue({ data: matches ?? [buildMatch()] });
  matchAPI.getStatisticsByRank.mockResolvedValue({ data: emptyRankStats });
  playerAPI.getById.mockResolvedValue({ data: { name: '対戦相手選手', kyuRank: 'B級' } });
  playerAPI.getAll.mockResolvedValue({ data: [] });
  if (mentorPromise) {
    mentorRelationshipAPI.getMyMentees.mockReturnValue(mentorPromise);
  } else {
    mentorRelationshipAPI.getMyMentees.mockResolvedValue({ data: mentees ?? [] });
  }
};

const renderMatchList = (path = '/matches') => {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/matches" element={<MatchList />} />
      </Routes>
    </MemoryRouter>
  );
};

const queryDetailButton = () => screen.queryByRole('button', { name: '対戦詳細を見る' });

describe('MatchList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('自分閲覧時: 対戦相手名タップで /matches?playerId=<opponentId> に遷移し、メモアイコンタップで /matches/<id> に遷移する', async () => {
    setupDefaultMocks({ matches: [buildMatch({ player1Id: 1, player2Id: 2 })] });

    const user = userEvent.setup();
    renderMatchList('/matches');

    const opponentBtn = await screen.findByRole('button', { name: '山田太郎' });
    await user.click(opponentBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/matches?playerId=2');

    const detailBtn = await waitFor(() => {
      const btn = queryDetailButton();
      expect(btn).not.toBeNull();
      return btn;
    });
    await user.click(detailBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/matches/100');
  });

  it('メンター閲覧時（ACTIVE）: 対戦相手名タップで相手対戦一覧、メモアイコンタップで詳細（playerIdクエリ付き）に遷移する', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ player1Id: 2, player2Id: 3, menteePersonalNotes: 'memo' })],
      mentees: [{ menteeId: 2, status: 'ACTIVE' }],
    });

    const user = userEvent.setup();
    renderMatchList('/matches?playerId=2');

    const opponentBtn = await screen.findByRole('button', { name: '山田太郎' });
    await user.click(opponentBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/matches?playerId=3');

    const detailBtn = await waitFor(() => {
      const btn = queryDetailButton();
      expect(btn).not.toBeNull();
      return btn;
    });
    await user.click(detailBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/matches/100?playerId=2');
  });

  it('他人閲覧時（非メンティー）: 対戦相手名タップで遷移可能、メモアイコンは表示されない', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ player1Id: 2, player2Id: 3 })],
      mentees: [],
    });

    const user = userEvent.setup();
    renderMatchList('/matches?playerId=2');

    const opponentBtn = await screen.findByRole('button', { name: '山田太郎' });
    await user.click(opponentBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/matches?playerId=3');

    await waitFor(() => {
      expect(mentorRelationshipAPI.getMyMentees).toHaveBeenCalled();
    });
    expect(queryDetailButton()).toBeNull();
  });

  it('他人閲覧時（INACTIVE メンティー）: メモアイコンは表示されない', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ player1Id: 2, player2Id: 3 })],
      mentees: [{ menteeId: 2, status: 'INACTIVE' }],
    });

    renderMatchList('/matches?playerId=2');

    await screen.findByRole('button', { name: '山田太郎' });
    await waitFor(() => {
      expect(mentorRelationshipAPI.getMyMentees).toHaveBeenCalled();
    });
    expect(queryDetailButton()).toBeNull();
  });

  it('ゲスト選手（player2Id=0）: 対戦相手名はリンクにならず、タップしても遷移しない', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ player1Id: 1, player2Id: 0, opponentName: 'ゲスト' })],
    });

    const user = userEvent.setup();
    renderMatchList('/matches');

    await screen.findByText('ゲスト');
    expect(screen.queryByRole('button', { name: 'ゲスト' })).toBeNull();

    await user.click(screen.getByText('ゲスト'));
    const opponentNavCalls = mockNavigate.mock.calls.filter((call) =>
      typeof call[0] === 'string' && call[0].startsWith('/matches?playerId=')
    );
    expect(opponentNavCalls).toHaveLength(0);
  });

  it('メモ有り行: メモアイコンに text-gray-600 が適用される', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ myPersonalNotes: 'メモ本文' })],
    });

    renderMatchList('/matches');

    const detailBtn = await waitFor(() => {
      const btn = queryDetailButton();
      expect(btn).not.toBeNull();
      return btn;
    });
    expect(detailBtn.className).toContain('text-gray-600');
    expect(detailBtn.className).not.toContain('text-gray-300');
  });

  it('メモ無し行: メモアイコンに text-gray-300 が適用される', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ myPersonalNotes: '' })],
    });

    renderMatchList('/matches');

    const detailBtn = await waitFor(() => {
      const btn = queryDetailButton();
      expect(btn).not.toBeNull();
      return btn;
    });
    expect(detailBtn.className).toContain('text-gray-300');
    expect(detailBtn.className).not.toContain('text-gray-600');
  });

  it('メンター関係 API ロード中: メモアイコンが描画されない', async () => {
    const pendingPromise = new Promise(() => {});
    setupDefaultMocks({
      matches: [buildMatch({ player1Id: 2, player2Id: 3 })],
      mentorPromise: pendingPromise,
    });

    renderMatchList('/matches?playerId=2');

    await screen.findByRole('button', { name: '山田太郎' });
    await waitFor(() => {
      expect(mentorRelationshipAPI.getMyMentees).toHaveBeenCalled();
    });
    expect(queryDetailButton()).toBeNull();
  });

  it('行内表示: 日付 M/D と「会場名 N試合目」がそれぞれ独立した要素として表示される', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ matchDate: '2026-05-23', venueName: '本郷', matchNumber: 3 })],
    });

    renderMatchList('/matches');

    await screen.findByRole('button', { name: '山田太郎' });
    expect(screen.getByText('5/23')).toBeInTheDocument();
    expect(screen.getByText('本郷 3試合目')).toBeInTheDocument();
    // 旧フォーマット `M/D 会場名(N)` の括弧表記が混在していないこと
    expect(screen.queryByText(/\(3\)/)).toBeNull();
  });

  it('行内表示: venueName が空の場合は「N試合目」のみが表示される', async () => {
    setupDefaultMocks({
      matches: [buildMatch({ matchDate: '2026-05-23', venueName: null, matchNumber: 5 })],
    });

    renderMatchList('/matches');

    await screen.findByRole('button', { name: '山田太郎' });
    expect(screen.getByText('5/23')).toBeInTheDocument();
    expect(screen.getByText('5試合目')).toBeInTheDocument();
    // 旧フォーマット `M/D (N)` の括弧表記が混在していないこと
    expect(screen.queryByText(/\(5\)/)).toBeNull();
  });

  it('メンター関係 API 失敗時: メモアイコンが描画されず、画面はクラッシュしない', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    setupDefaultMocks({
      matches: [buildMatch({ player1Id: 2, player2Id: 3 })],
      mentorPromise: Promise.reject(new Error('network error')),
    });

    renderMatchList('/matches?playerId=2');

    await screen.findByRole('button', { name: '山田太郎' });
    await waitFor(() => {
      expect(consoleErrorSpy).toHaveBeenCalled();
    });
    expect(queryDetailButton()).toBeNull();

    consoleErrorSpy.mockRestore();
  });
});
