import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// homeAPI.getData をモック（テストごとに戻り値を差し替える）
const getData = vi.fn();
vi.mock('../api', () => ({
  homeAPI: { getData: (...args) => getData(...args) },
}));

// currentPlayer は id=1（TOP3 の playerId 10/11/12 の圏外＝自分の行が出る条件）
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 1, name: 'ログインユーザー名' } }),
}));

vi.mock('../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

import Home from './Home';

const renderHome = async (dto) => {
  getData.mockResolvedValue({ data: dto });
  render(
    <MemoryRouter>
      <Home />
    </MemoryRouter>
  );
  // ローディング解除を待つ
  await waitFor(() => expect(screen.queryByText('Loading...')).not.toBeInTheDocument());
};

const baseGroups = [];

afterEach(() => {
  cleanup();
  getData.mockReset();
});
beforeEach(() => {
  getData.mockReset();
});

describe('Home 脱カード・リデザイン', () => {
  it('TODAY・登録済み: 日付/人数/CTA(対戦確認画面へ→/pairings) を表示し、確定参加者のみ数える', async () => {
    await renderHome({
      nextPractice: {
        today: true,
        registered: true,
        sessionDate: '2026-07-16',
        venueName: 'かでる',
        startTime: '10:00:00',
        endTime: '12:00:00',
        matchNumbers: [1, 2],
        participants: [
          { id: 1, name: '甲', status: 'CONFIRMED' },
          { id: 2, name: '乙', status: 'CONFIRMED' },
          { id: 3, name: '丙', status: 'WAITLISTED' }, // 除外
          { id: 4, name: '丁', status: 'CANCELLED' }, // 除外
        ],
      },
      participationGroups: baseGroups,
      hasPendingOffer: true, // バナーは表示されてはいけない
      unreadNotificationCount: 0,
    });

    expect(screen.getByText('TODAY')).toBeInTheDocument();
    expect(screen.getByText('7/16')).toBeInTheDocument();
    // 確定参加者は 2 名（WAITLISTED/CANCELLED を除外）
    expect(screen.getByText('1、2試合目に参加予定 ・ 2名')).toBeInTheDocument();

    // CTA: 対戦確認画面へ → /pairings?date=
    const cta = screen.getByText('対戦確認画面へ').closest('a');
    expect(cta).toHaveAttribute('href', '/pairings?date=2026-07-16');
  });

  it('未登録・非当日: CTA は参加登録→/practice/participation、人数のみ表示', async () => {
    await renderHome({
      nextPractice: {
        today: false,
        registered: false,
        sessionDate: '2026-07-20',
        venueName: '東区民',
        startTime: '13:00:00',
        endTime: '15:00:00',
        matchNumbers: [],
        participants: [{ id: 9, name: '戊', status: 'CONFIRMED' }],
      },
      participationGroups: baseGroups,
      hasPendingOffer: false,
    });

    expect(screen.getByText('NEXT')).toBeInTheDocument();
    // 未登録なので試合目情報は出さず、人数のみ
    expect(screen.getByText('1名')).toBeInTheDocument();
    const cta = screen.getByText('参加登録').closest('a');
    expect(cta).toHaveAttribute('href', '/practice/participation');
  });

  it('nextPractice が null: 空状態メッセージを出し CTA は無い', async () => {
    await renderHome({ nextPractice: null, participationGroups: baseGroups });

    expect(screen.getByText('次の練習の予定はまだありません')).toBeInTheDocument();
    expect(screen.queryByText('対戦確認画面へ')).not.toBeInTheDocument();
    expect(screen.queryByText('参加登録')).not.toBeInTheDocument();
  });

  it('参加率: 月見出し・行の N試合/率%・自分の行(あなた) を表示（率は Math.round(rate*100)）', async () => {
    await renderHome({
      nextPractice: null,
      participationGroups: [
        {
          organizationId: null,
          organizationName: '全体',
          top3: [
            { playerId: 10, playerName: '一位', rate: 0.8, participatedMatches: 8, totalScheduledMatches: 10 },
            { playerId: 11, playerName: '二位', rate: 0.6, participatedMatches: 6, totalScheduledMatches: 10 },
            { playerId: 12, playerName: '三位', rate: 0.5, participatedMatches: 5, totalScheduledMatches: 10 },
          ],
          myRate: { rate: 0.3, participatedMatches: 3, totalScheduledMatches: 10 },
        },
      ],
    });

    // 月見出し（◯月の参加率）＋ TOP 3
    expect(screen.getByText(/月の参加率/)).toBeInTheDocument();
    expect(screen.getByText('TOP 3')).toBeInTheDocument();
    // 単一団体なので団体名は出さず総試合数のみ
    expect(screen.getByText('10試合')).toBeInTheDocument();
    // 行: 名前・参加試合数・率（回帰: 0.8→80%, 0.3→30%）
    expect(screen.getByText('一位')).toBeInTheDocument();
    expect(screen.getByText('8試合')).toBeInTheDocument();
    expect(screen.getByText('80%')).toBeInTheDocument();
    // 自分の行（TOP3圏外）: "あなた"・茶の率。"YOU" ラベルは出さない
    expect(screen.getByText('あなた')).toBeInTheDocument();
    expect(screen.getByText('30%')).toBeInTheDocument();
    expect(screen.queryByText('YOU')).not.toBeInTheDocument();
  });

  it('複数団体: 団体名（総試合数）を出す', async () => {
    await renderHome({
      nextPractice: null,
      participationGroups: [
        {
          organizationId: null,
          organizationName: '全体',
          top3: [{ playerId: 10, playerName: '一位', rate: 1, participatedMatches: 4, totalScheduledMatches: 4 }],
          myRate: null,
        },
        {
          organizationId: 5,
          organizationName: '北大かるた会',
          top3: [{ playerId: 20, playerName: '甲', rate: 0.5, participatedMatches: 2, totalScheduledMatches: 6 }],
          myRate: null,
        },
      ],
    });

    // 団体名（明朝・見出しより小さく）と総試合数（taupe）は別要素に分離
    expect(screen.getByText('全体')).toBeInTheDocument();
    expect(screen.getByText('北大かるた会')).toBeInTheDocument();
    expect(screen.getByText('（6試合）')).toBeInTheDocument();
  });

  it('脱カード不変条件: トップバー(ユーザー名)・参加者チップ・繰り上げバナーを描画しない', async () => {
    await renderHome({
      nextPractice: {
        today: true,
        registered: true,
        sessionDate: '2026-07-16',
        venueName: 'かでる',
        startTime: '10:00:00',
        endTime: '12:00:00',
        matchNumbers: [1],
        participants: [{ id: 2, name: '参加者チップ名', status: 'CONFIRMED' }],
      },
      participationGroups: baseGroups,
      hasPendingOffer: true,
    });

    // NavigationMenu 撤去 → ログインユーザー名は出ない
    expect(screen.queryByText('ログインユーザー名')).not.toBeInTheDocument();
    // PlayerChip 撤去 → 参加者名はチップとして出ない（人数に集約）
    expect(screen.queryByText('参加者チップ名')).not.toBeInTheDocument();
    // 繰り上げオファーバナー撤去
    expect(screen.queryByText('繰り上げ参加のお知らせ')).not.toBeInTheDocument();
  });
});
