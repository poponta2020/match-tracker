import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route, useNavigate } from 'react-router-dom';

// このテストは実 useNavigate / useSearchParams を使う（タブ切替=URL 書換の検証のため）。
vi.mock('../../api', () => ({
  matchAPI: {
    getByPlayerId: vi.fn(),
    getStatisticsByRank: vi.fn(),
  },
  playerAPI: {
    getById: vi.fn(),
    getAll: vi.fn(),
  },
  byeActivityAPI: {
    getByPlayer: vi.fn(),
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

vi.mock('../../components/FilterBottomSheet', () => ({ default: () => null }));
vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div data-testid="loading-screen">Loading...</div>,
}));

import { matchAPI, playerAPI, byeActivityAPI } from '../../api';
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
  myOtetsukiCount: null,
  result: '勝ち',
  scoreDifference: 3,
  ...overrides,
});

// 戻るボタン（replace 検証用に履歴を1つ戻す）
const Back = () => {
  const navigate = useNavigate();
  return <button onClick={() => navigate(-1)}>__back</button>;
};

const renderAt = (entries, initialIndex) =>
  render(
    <MemoryRouter initialEntries={entries} initialIndex={initialIndex}>
      <Back />
      <Routes>
        <Route path="/matches" element={<MatchList />} />
        <Route path="/other" element={<div>OTHER PAGE</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('MatchList - 2タブ（戦績確認 / カレンダー）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    matchAPI.getByPlayerId.mockResolvedValue({ data: [buildMatch()] });
    matchAPI.getStatisticsByRank.mockResolvedValue({ data: emptyRankStats });
    playerAPI.getById.mockResolvedValue({ data: { name: '対戦相手選手', kyuRank: 'B級' } });
    playerAPI.getAll.mockResolvedValue({ data: [] });
    byeActivityAPI.getByPlayer.mockResolvedValue({ data: [] });
    mentorRelationshipAPI.getMyMentees.mockResolvedValue({ data: [] });
  });
  afterEach(() => cleanup());

  it('AC-1: タブ帯が表示され、既定は戦績確認タブがアクティブ', async () => {
    renderAt(['/matches']);
    const recordTab = await screen.findByRole('button', { name: '戦績確認' });
    expect(screen.getByRole('button', { name: 'カレンダー' })).toBeInTheDocument();
    expect(recordTab.className).toContain('font-bold');
    expect(recordTab.className).toContain('border-[#4a6b5a]');
  });

  it('AC-2: カレンダータブ押下で ?view=calendar になり MatchCalendar が描画される', async () => {
    renderAt(['/matches']);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'カレンダー' }));

    // MatchCalendar 固有の月送りボタンが出る
    expect(await screen.findByRole('button', { name: '前の月' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '次の月' })).toBeInTheDocument();
  });

  it('AC-2: タブ切替は replace（戻るボタンでタブがトグルせず前ページへ戻る）', async () => {
    renderAt(['/other', '/matches'], 1);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'カレンダー' }));
    await screen.findByRole('button', { name: '前の月' });

    // replace なら履歴は積まれず、戻るで戦績確認タブではなく前ページ(/other)へ戻る
    await user.click(screen.getByText('__back'));
    expect(await screen.findByText('OTHER PAGE')).toBeInTheDocument();
  });

  it('AC-3: 他選手戦績(?playerId=5)からカレンダーを開くと playerId を無視して自分を表示', async () => {
    renderAt(['/matches?playerId=5&view=calendar']);
    await screen.findByRole('button', { name: '前の月' });

    // カレンダーは自分(id=1)の試合を取得する
    expect(matchAPI.getByPlayerId).toHaveBeenCalledWith(1);
    // カレンダータブのトップバーは自分の名前＋級
    expect(screen.getByText('テスト選手')).toBeInTheDocument();
    expect(screen.getByText('C級')).toBeInTheDocument();
  });

  it('AC-4: カレンダーから戦績確認へ戻ると直前の選手(playerId=5)の戦績が復元される', async () => {
    renderAt(['/matches?playerId=5&view=calendar']);
    await screen.findByRole('button', { name: '前の月' });
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '戦績確認' }));

    // 他選手(5)の戦績確認: トップバーに相手名＋「自分に戻す」
    expect(await screen.findByText('対戦相手選手')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '自分に戻す' })).toBeInTheDocument();
    expect(matchAPI.getByPlayerId).toHaveBeenCalledWith(5, expect.any(Object));
  });

  it('カレンダー表示中は戦績確認用の取得を行わず、自分の試合を1回だけ取得する', async () => {
    renderAt(['/matches?view=calendar']);
    await screen.findByRole('button', { name: '前の月' });

    // MatchCalendar による自分(id=1)の1回のみ。record 用 targetPlayerId 取得や統計/抜け番は走らない
    expect(matchAPI.getByPlayerId).toHaveBeenCalledTimes(1);
    expect(matchAPI.getByPlayerId).toHaveBeenCalledWith(1);
    expect(matchAPI.getStatisticsByRank).not.toHaveBeenCalled();
    expect(byeActivityAPI.getByPlayer).not.toHaveBeenCalled();
  });

  it('AC-5: カレンダータブのトップバーは自分の名前＋級で、選手検索ボタンは非表示', async () => {
    renderAt(['/matches?view=calendar']);
    await screen.findByRole('button', { name: '前の月' });

    expect(screen.getByText('テスト選手')).toBeInTheDocument();
    expect(screen.getByText('C級')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '選手を検索' })).toBeNull();
  });
});
