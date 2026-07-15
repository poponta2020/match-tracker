import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { kimariji } from '../../data/kimariji';

// API モック
vi.mock('../../api', () => ({
  matchAPI: {
    getById: vi.fn(),
    getCardRecord: vi.fn(),
    delete: vi.fn(),
  },
  matchCommentsAPI: { getComments: vi.fn(() => Promise.resolve({ data: [] })) },
  matchVideoAPI: { remove: vi.fn() },
}));
vi.mock('../../api/mentorRelationship', () => ({
  mentorRelationshipAPI: { getMyMentees: vi.fn(() => Promise.resolve({ data: [] })) },
}));
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 1, name: '本人', role: 'PLAYER' } }),
}));
// 子コンポーネントは本テストの対象外・依存を避けるためスタブ化
vi.mock('./MatchCommentThread', () => ({ default: () => null }));
vi.mock('../../components/VideoRegisterModal', () => ({ default: () => null }));

import { matchAPI, matchCommentsAPI } from '../../api';
import { mentorRelationshipAPI } from '../../api/mentorRelationship';
import MatchDetail from './MatchDetail';

const MATCH = {
  id: 5,
  matchDate: '2026-01-15',
  matchNumber: 1,
  player1Id: 1,
  player2Id: 2,
  opponentName: '対戦相手',
  result: '勝ち',
  scoreDifference: 5,
  isLesson: false,
};

const renderDetail = (search = '') =>
  render(
    <MemoryRouter initialEntries={[`/matches/5${search}`]}>
      <Routes>
        <Route path="/matches/:id" element={<MatchDetail />} />
      </Routes>
    </MemoryRouter>
  );

beforeEach(() => {
  matchAPI.getById.mockResolvedValue({ data: MATCH });
  matchCommentsAPI.getComments.mockResolvedValue({ data: [] });
  mentorRelationshipAPI.getMyMentees.mockResolvedValue({ data: [] });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MatchDetail - 取り札・お手付き詳細の読み取り専用表示', () => {
  it('C-12: 本人閲覧で配置があると読み取り専用盤面が表示され、不明プールは出ない', async () => {
    matchAPI.getCardRecord.mockResolvedValue({
      data: {
        cardPlacements: [{ cardNo: 17, takenBy: 'SELF', field: 'ENEMY', side: 'LEFT', tier: 'TOP' }],
        otetsukiDetails: [],
      },
    });
    renderDetail();

    await screen.findByText('敵 陣');
    expect(screen.getByText('自 陣')).toBeInTheDocument();
    // 配置済みの札（決まり字）が表示される
    expect(screen.getByText(kimariji(17))).toBeInTheDocument();
    // 不明プール・操作系（info）は出ない
    expect(document.querySelector('.tr-pool')).toBeNull();
    expect(screen.queryByLabelText('取り札の説明')).toBeNull();
  });

  it('C-13: 本人閲覧でお手付き詳細が読み取り専用で表示される', async () => {
    matchAPI.getCardRecord.mockResolvedValue({
      data: {
        cardPlacements: [],
        otetsukiDetails: [{ type: 'HIKKAKE', hikkakeTarget: 'OWN_RIGHT_TOP' }],
      },
    });
    renderDetail();

    expect(await screen.findByText('お手付きの内容')).toBeInTheDocument();
    expect(screen.getByText('ひっかけ')).toBeInTheDocument();
    expect(screen.getByText(/払おうとした上段/)).toBeInTheDocument();
    // 種類選択ボタン（操作系）は出ない
    expect(screen.queryByRole('button', { name: '暗記間違え' })).toBeNull();
  });

  it('C-14: メンター閲覧（?playerId=）では getCardRecord を呼ばず何も表示しない', async () => {
    matchAPI.getCardRecord.mockResolvedValue({
      data: { cardPlacements: [{ cardNo: 17, takenBy: 'SELF', field: 'ENEMY', side: 'LEFT', tier: 'TOP' }], otetsukiDetails: [] },
    });
    renderDetail('?playerId=2');

    await screen.findByText(/第1試合/);
    await new Promise((r) => setTimeout(r, 50));
    expect(matchAPI.getCardRecord).not.toHaveBeenCalled();
    expect(document.querySelector('.tr-field')).toBeNull();
    expect(screen.queryByText('お手付きの内容')).toBeNull();
  });

  it('当事者でないユーザーが /matches/:id を直接開いても取得・表示しない（本人性ガード）', async () => {
    // ログインユーザー(1)が当事者でない試合（2 vs 3）を、?playerId= なしで直接開く
    matchAPI.getById.mockResolvedValue({ data: { ...MATCH, player1Id: 2, player2Id: 3 } });
    matchAPI.getCardRecord.mockResolvedValue({
      data: { cardPlacements: [{ cardNo: 17, takenBy: 'SELF', field: 'ENEMY', side: 'LEFT', tier: 'TOP' }], otetsukiDetails: [] },
    });
    renderDetail();

    await screen.findByText(/第1試合/);
    await new Promise((r) => setTimeout(r, 50));
    expect(matchAPI.getCardRecord).not.toHaveBeenCalled();
    expect(document.querySelector('.tr-field')).toBeNull();
  });

  it('C-15: 記録が無ければ盤面・お手付きセクションを出さない', async () => {
    matchAPI.getCardRecord.mockResolvedValue({ data: { cardPlacements: [], otetsukiDetails: [] } });
    renderDetail();

    await screen.findByText(/第1試合/);
    await waitFor(() => expect(matchAPI.getCardRecord).toHaveBeenCalled());
    expect(document.querySelector('.tr-field')).toBeNull();
    expect(screen.queryByText('お手付きの内容')).toBeNull();
  });
});
