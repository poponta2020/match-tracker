import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within, cleanup } from '@testing-library/react';
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
 * MatchForm 対戦相手選択モデル（match-form-redesign）テスト
 * - プルダウン母集団 = 当日参加者（自分を除く）＋「抜け番」
 * - 「未参加から検索」= 全選手 − 参加者 − 自分
 * - 抜け番選択で bye モードへ
 * - 確定相手の級 "(A)" 表示
 */

const PAST_DATE = '2026-01-15'; // 当日でない日付（参加登録ダイアログを回避）

// セッション参加者: 自分(1)・佐藤(2,A級)・田中(3,B級)
const SESSION = {
  id: 100,
  sessionDate: PAST_DATE,
  totalMatches: 3,
  venueName: '近江勧学館',
  participants: [
    { id: 1, name: '自分', kyuRank: 'B級' },
    { id: 2, name: '佐藤 美咲', kyuRank: 'A級' },
    { id: 3, name: '田中 一郎', kyuRank: 'B級' },
  ],
};

// 全選手: 参加者 + 未参加の鈴木(4,C級)
const ALL_PLAYERS = [
  { id: 1, name: '自分', kyuRank: 'B級' },
  { id: 2, name: '佐藤 美咲', kyuRank: 'A級' },
  { id: 3, name: '田中 一郎', kyuRank: 'B級' },
  { id: 4, name: '鈴木 花子', kyuRank: 'C級' },
];

beforeEach(() => {
  playerAPI.getAll.mockResolvedValue({ data: ALL_PLAYERS });
  practiceAPI.getByDate.mockResolvedValue({ data: SESSION });
  practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
  pairingAPI.getByDate.mockResolvedValue({ data: [] }); // ペアリングなし → 簡易入力
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
  matchAPI.getByPlayerDateAndMatchNumber.mockRejectedValue(new Error('not found'));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const renderForm = () =>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/matches/new', state: { matchDate: PAST_DATE, matchNumber: 1 } }]}>
      <Routes>
        <Route path="/matches/new" element={<MatchForm />} />
        <Route path="/matches" element={<div>matches</div>} />
      </Routes>
    </MemoryRouter>
  );

const opponentSelect = () => screen.getByRole('combobox', { name: '対戦相手' });

describe('MatchForm 対戦相手プルダウンの母集団', () => {
  it('当日参加者（自分を除く）＋「抜け番」を出し、自分は出さない', async () => {
    renderForm();
    const select = await waitFor(() => opponentSelect());

    // 参加者（自分以外）が級つきで並ぶ
    expect(within(select).getByRole('option', { name: '佐藤 美咲（A）' })).toBeInTheDocument();
    expect(within(select).getByRole('option', { name: '田中 一郎（B）' })).toBeInTheDocument();
    // 「抜け番」がある
    expect(within(select).getByRole('option', { name: '抜け番' })).toBeInTheDocument();
    // 自分は出ない
    expect(within(select).queryByRole('option', { name: /自分/ })).toBeNull();
    // 未参加の鈴木はプルダウンに出ない（検索専用）
    expect(within(select).queryByRole('option', { name: /鈴木/ })).toBeNull();
  });
});

describe('MatchForm 未参加から検索', () => {
  it('全選手 − 参加者 − 自分（未参加者のみ）を検索結果に出す', async () => {
    renderForm();
    await waitFor(() => opponentSelect());

    fireEvent.click(screen.getByRole('button', { name: '未参加の選手から検索' }));

    // 未参加の鈴木のみがリストに出る
    expect(await screen.findByRole('button', { name: /鈴木 花子/ })).toBeInTheDocument();
    // 参加者・自分は検索結果に出ない
    expect(screen.queryByRole('button', { name: /佐藤 美咲/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^自分/ })).toBeNull();
  });

  it('名前で絞り込める', async () => {
    renderForm();
    await waitFor(() => opponentSelect());
    fireEvent.click(screen.getByRole('button', { name: '未参加の選手から検索' }));
    await screen.findByRole('button', { name: /鈴木 花子/ });

    fireEvent.change(screen.getByPlaceholderText('名前で検索…'), { target: { value: '存在しない' } });
    expect(screen.getByText('該当する選手がいません')).toBeInTheDocument();
  });

  it('検索から選ぶと確定相手としてセットされる', async () => {
    renderForm();
    await waitFor(() => opponentSelect());
    fireEvent.click(screen.getByRole('button', { name: '未参加の選手から検索' }));
    fireEvent.click(await screen.findByRole('button', { name: /鈴木 花子/ }));

    // 確定相手として名前が表示される（変更ボタン化された名前）
    expect(await screen.findByRole('button', { name: '鈴木 花子' })).toBeInTheDocument();
  });
});

describe('MatchForm 抜け番プルダウン一本化', () => {
  it('プルダウンで「抜け番」を選ぶと抜け番モードへ遷移する', async () => {
    renderForm();
    const select = await waitFor(() => opponentSelect());

    fireEvent.change(select, { target: { value: '__bye__' } });

    expect(await screen.findByText(/抜け番です/)).toBeInTheDocument();
    expect(screen.getByText('活動内容')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /読み/ })).toBeInTheDocument();
  });
});

describe('MatchForm 相手の級表示', () => {
  it('確定相手の級を "(A)" 形式で表示する', async () => {
    renderForm();
    const select = await waitFor(() => opponentSelect());

    fireEvent.change(select, { target: { value: '2' } }); // 佐藤 美咲（A級）

    expect(await screen.findByRole('button', { name: '佐藤 美咲' })).toBeInTheDocument();
    expect(screen.getByText('(A)')).toBeInTheDocument();
  });
});

describe('MatchForm 非アクティブ参加者の除外', () => {
  // matchParticipants でステータスが分かるセッション（キャンセル太郎=CANCELLED）
  const SESSION_WITH_STATUS = {
    id: 100,
    sessionDate: PAST_DATE,
    totalMatches: 3,
    venueName: '近江勧学館',
    participants: [
      { id: 1, name: '自分', kyuRank: 'B級' },
      { id: 2, name: '佐藤 美咲', kyuRank: 'A級' },
      { id: 3, name: '田中 一郎', kyuRank: 'B級' },
      { id: 5, name: 'キャンセル 太郎', kyuRank: 'C級' },
    ],
    matchParticipants: {
      1: [
        { name: '自分', status: 'WON' },
        { name: '佐藤 美咲', status: 'WON' },
        { name: '田中 一郎', status: 'PENDING' },
        { name: 'キャンセル 太郎', status: 'CANCELLED' },
      ],
    },
  };
  const PLAYERS_WITH_CANCELLED = [
    ...ALL_PLAYERS,
    { id: 5, name: 'キャンセル 太郎', kyuRank: 'C級' },
  ];

  it('CANCELLED 参加者はプルダウンに出さず、未参加検索に出す', async () => {
    playerAPI.getAll.mockResolvedValue({ data: PLAYERS_WITH_CANCELLED });
    practiceAPI.getByDate.mockResolvedValue({ data: SESSION_WITH_STATUS });

    renderForm();
    const select = await waitFor(() => opponentSelect());

    // アクティブ（WON/PENDING）のみプルダウンに出る
    expect(within(select).getByRole('option', { name: '佐藤 美咲（A）' })).toBeInTheDocument();
    expect(within(select).getByRole('option', { name: '田中 一郎（B）' })).toBeInTheDocument();
    // CANCELLED はプルダウンに出ない
    expect(within(select).queryByRole('option', { name: /キャンセル/ })).toBeNull();

    // 未参加検索には出る（登録済みだが当日非アクティブ）
    fireEvent.click(screen.getByRole('button', { name: '未参加の選手から検索' }));
    expect(await screen.findByRole('button', { name: /キャンセル 太郎/ })).toBeInTheDocument();
  });
});

describe('MatchForm 抜け番モードのタブ移動リセット', () => {
  it('抜け番選択後に別試合（ペアリングあり）へ移動すると抜け番モードが解除される', async () => {
    // 2試合目に自分のペアリング（相手=佐藤）を持たせる
    pairingAPI.getByDate.mockResolvedValue({
      data: [
        { id: 10, matchNumber: 2, player1Id: 1, player2Id: 2, player1Name: '自分', player2Name: '佐藤 美咲' },
      ],
    });

    renderForm();
    const select = await waitFor(() => opponentSelect()); // 1試合目 = ペアリングなし → プルダウン

    // 抜け番を選択 → 抜け番モード
    fireEvent.change(select, { target: { value: '__bye__' } });
    expect(await screen.findByText('活動内容')).toBeInTheDocument();

    // 2試合目へ移動（dirty のため確認 → 移動する）
    fireEvent.click(screen.getByRole('button', { name: /2試合目/ }));
    fireEvent.click(await screen.findByRole('button', { name: '移動する' }));

    // 抜け番モードは解除され、ペアリング相手（佐藤）の通常入力になる
    await waitFor(() => expect(screen.getByRole('button', { name: '佐藤 美咲' })).toBeInTheDocument());
    expect(screen.queryByText('活動内容')).toBeNull();
  });
});

describe('MatchForm 入力済み試合の相手読み取り専用', () => {
  it('入力済み試合では対戦相手の変更・検索UIを出さない', async () => {
    practiceAPI.getByDate.mockResolvedValue({ data: { ...SESSION, totalMatches: 1 } });
    const existing = {
      matchNumber: 1, player1Id: 1, player2Id: 2, opponentName: '佐藤 美咲',
      result: '勝ち', scoreDifference: 5, isLesson: false, myOtetsukiCount: null, myPersonalNotes: '',
    };
    matchAPI.getByPlayerDateAndMatchNumber.mockImplementation((pid, date, num) =>
      num === 1 ? Promise.resolve({ data: existing }) : Promise.reject(new Error('nf'))
    );

    renderForm();

    // 入力済み通知が出る
    expect(await screen.findByText(/入力済みの試合です/)).toBeInTheDocument();
    // 相手名は表示されるが、変更（ボタン化された名前・▽）・検索UIは出さない
    expect(screen.getByText('佐藤 美咲')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '佐藤 美咲' })).toBeNull();
    expect(screen.queryByRole('button', { name: '対戦相手を変更' })).toBeNull();
    expect(screen.queryByRole('button', { name: '未参加の選手から検索' })).toBeNull();
  });
});
