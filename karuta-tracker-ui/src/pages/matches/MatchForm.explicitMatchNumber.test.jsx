import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../api', () => ({
  matchAPI: {
    getAll: vi.fn(),
    getById: vi.fn(),
    getByPlayerDateAndMatchNumber: vi.fn(),
    getCardRecord: vi.fn(() => Promise.resolve({ data: { cardPlacements: [], otetsukiDetails: [] } })),
    saveCardRecord: vi.fn(() => Promise.resolve({ data: {} })),
  },
  playerAPI: { getAll: vi.fn() },
  practiceAPI: {
    getByDate: vi.fn(),
    getPlayerParticipations: vi.fn(),
  },
  pairingAPI: { getByDate: vi.fn() },
  byeActivityAPI: { getByDate: vi.fn() },
  cardRuleNonceAPI: {
    getByDate: vi.fn(() => Promise.resolve({ data: { nonce: 0 } })),
    update: vi.fn(() => Promise.resolve({ data: {} })),
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 1, name: '自分' } }),
}));

import { matchAPI, playerAPI, practiceAPI, pairingAPI, byeActivityAPI } from '../../api';
import MatchForm from './MatchForm';

/**
 * 対戦組み合わせ画面（MatchResultsView）のFABから明示的にmatchNumberを指定して
 * 遷移した場合、1〜6試合目が未記録でも7試合目が選択状態のまま維持されることを検証する。
 * （修正前は「未記録の試合を優先表示」ロジックが明示指定を無視し、1試合目に戻っていた）
 */

const PAST_DATE = '2026-01-15';

const SESSION = {
  id: 100,
  sessionDate: PAST_DATE,
  totalMatches: 10,
  venueName: '近江勧学館',
  participants: [
    { id: 1, name: '自分', kyuRank: 'B級' },
    { id: 2, name: '佐藤 美咲', kyuRank: 'A級' },
  ],
};

beforeEach(() => {
  playerAPI.getAll.mockResolvedValue({ data: [{ id: 2, name: '佐藤 美咲', kyuRank: 'A級' }] });
  practiceAPI.getByDate.mockResolvedValue({ data: SESSION });
  practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
  pairingAPI.getByDate.mockResolvedValue({ data: [] });
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
  // 1〜10試合目すべて未記録（自分の結果を一度も入力していない状態）
  matchAPI.getByPlayerDateAndMatchNumber.mockRejectedValue(new Error('not found'));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const renderWithMatchNumber = (matchNumber) =>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/matches/new', state: { matchDate: PAST_DATE, matchNumber } }]}>
      <Routes>
        <Route path="/matches/new" element={<MatchForm />} />
      </Routes>
    </MemoryRouter>
  );

describe('対戦組み合わせ画面のFABから明示的な試合番号で遷移した場合', () => {
  it('1〜6試合目が未記録でも、指定された7試合目が選択状態のまま維持される', async () => {
    renderWithMatchNumber(7);

    await waitFor(() => {
      const tab7 = screen.getByText('7試合目');
      expect(tab7.className).toContain('border-white text-white');
    });

    const tab1 = screen.getByText('1試合目');
    expect(tab1.className).not.toContain('border-white text-white');
  });
});
