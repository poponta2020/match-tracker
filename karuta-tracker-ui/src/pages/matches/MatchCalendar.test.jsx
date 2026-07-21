import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

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
  },
}));

const currentPlayer = { id: 1, name: '山田太郎', kyuRank: 'A級' };
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer }),
}));

import { matchAPI } from '../../api';
import MatchCalendar from './MatchCalendar';

// 「今日」を 2026-07-21 に固定する
const REFERENCE_DATE = new Date(2026, 6, 21);

const MATCHES = [
  // 今日(7/21): 3試合・会場は日付に一意(土居家)
  { id: 101, player1Id: 1, matchDate: '2026-07-21', matchNumber: 1, result: '負け', scoreDifference: 13,
    opponentName: 'ざきお', player2KyuRank: 'A級', myOtetsukiCount: 1, venueName: '土居家',
    myPersonalNotes: '敵陣の別れは取れている' },
  { id: 102, player1Id: 1, matchDate: '2026-07-21', matchNumber: 2, result: '勝ち', scoreDifference: 7,
    opponentName: 'さとう', player2KyuRank: 'B級', myOtetsukiCount: 0, venueName: '土居家', myPersonalNotes: '' },
  { id: 103, player1Id: 1, matchDate: '2026-07-21', matchNumber: 3, result: '負け', scoreDifference: 2,
    opponentName: 'たなか', player2KyuRank: 'A級', myOtetsukiCount: 2, venueName: '土居家' },
  // 7/3: ゲスト（相手級なし・お手つき不明）
  { id: 104, player1Id: 1, matchDate: '2026-07-03', matchNumber: 1, result: '勝ち', scoreDifference: 20,
    opponentName: 'ゲスト選手', player2KyuRank: null, myOtetsukiCount: null, venueName: '東区民' },
  // 7/12: 指導試合
  { id: 106, player1Id: 1, matchDate: '2026-07-12', matchNumber: 2, isLesson: true,
    opponentName: 'こばやし', player2KyuRank: 'D級', venueName: 'クラ館', myPersonalNotes: '指導試合' },
  // 抜け番相当（matchDate なし）: 集計・リストに出ないこと
  { id: 999, player1Id: 1, matchDate: null, matchNumber: 1, opponentName: '抜け番' },
];

const renderCalendar = () =>
  render(
    <MemoryRouter>
      <MatchCalendar referenceDate={REFERENCE_DATE} />
    </MemoryRouter>
  );

const dayButton = (label) => screen.getByRole('button', { name: label });

describe('MatchCalendar（カレンダータブ本体）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    matchAPI.getByPlayerId.mockResolvedValue({ data: MATCHES });
  });
  afterEach(() => cleanup());

  it('自分の試合を getByPlayerId(self) で取得し、日セルに試合数バッジを出す（抜け番/matchDate無しは除外）', async () => {
    renderCalendar();
    // 初期選択=今日の1試合目が出るまで待つ（データ取得完了）
    await screen.findByText('ざきお(A)');
    expect(matchAPI.getByPlayerId).toHaveBeenCalledWith(1);

    // 今日(7/21)のセルに試合数バッジ 3
    const todayCell = dayButton('7月21日');
    expect(within(todayCell).getByText('3')).toBeInTheDocument();
    // matchDate 無しの「抜け番」は描画されない
    expect(screen.queryByText('抜け番')).toBeNull();
  });

  it('初期選択は今日で、見出しは「M/D 会場名」（会場は日付に一意で1回）', async () => {
    renderCalendar();
    const heading = await screen.findByText(/7\/21/);
    expect(heading.textContent).toContain('7/21');
    expect(heading.textContent).toContain('土居家');
  });

  it('選択日リストは試合番号昇順で 1行=「N試合目： 勝敗記号+枚数差 相手名（級） お手N」、2行目にメモ', async () => {
    renderCalendar();
    // 今日の3試合が昇順で並ぶ
    await screen.findByText('ざきお(A)');
    expect(screen.getByText('×13')).toBeInTheDocument(); // 1試合目 負け13
    expect(screen.getByText('〇7')).toBeInTheDocument(); // 2試合目 勝ち7
    expect(screen.getByText('さとう(B)')).toBeInTheDocument();
    expect(screen.getByText('たなか(A)')).toBeInTheDocument();
    expect(screen.getByText('お手1')).toBeInTheDocument();
    // メモ本文
    expect(screen.getByText('敵陣の別れは取れている')).toBeInTheDocument();
  });

  it('級なし＝括弧なし・お手つき不明＝お手なし（ゲスト試合）', async () => {
    renderCalendar();
    await screen.findByText('ざきお(A)');
    const user = userEvent.setup();
    await user.click(dayButton('7月3日'));

    expect(screen.getByText('ゲスト選手')).toBeInTheDocument(); // 括弧なし
    // 級なし＝相手名要素に括弧を出さない（見出しの曜日 (金) と混同しないよう相手名要素で検証）
    expect(screen.getByText('ゲスト選手').textContent).not.toContain('(');
    expect(screen.queryByText(/お手/)).toBeNull(); // お手つき不明は出さない
    expect(screen.getByText('〇20')).toBeInTheDocument();
  });

  it('指導試合は勝敗の代わりに「指導」表示', async () => {
    renderCalendar();
    await screen.findByText('ざきお(A)');
    const user = userEvent.setup();
    await user.click(dayButton('7月12日'));

    expect(screen.getByText('指導')).toBeInTheDocument();
    expect(screen.getByText('こばやし(D)')).toBeInTheDocument();
  });

  it('試合が無い日は「記録がありません」・登録ボタンなし', async () => {
    renderCalendar();
    await screen.findByText('ざきお(A)');
    const user = userEvent.setup();
    await user.click(dayButton('7月5日'));

    expect(screen.getByText('記録がありません')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /登録/ })).toBeNull();
  });

  it('月ラベル下に当月の総試合数バッジ、年月ピッカーで別月へジャンプすると連動更新', async () => {
    renderCalendar();
    await screen.findByText('ざきお(A)');
    // 7月は 3+1+1 = 5試合
    expect(screen.getByText('5試合')).toBeInTheDocument();

    const user = userEvent.setup();
    // 年月ラベル押下でピッカーを開く
    await user.click(screen.getByText('2026年7月').closest('button'));
    // 9月へジャンプ（試合なし → 0試合）
    await user.click(screen.getByRole('button', { name: '9月' }));

    expect(screen.getByText('2026年9月')).toBeInTheDocument();
    expect(screen.getByText('0試合')).toBeInTheDocument();
  });

  it('試合項目タップで /matches/:id へ遷移する', async () => {
    renderCalendar();
    await screen.findByText('ざきお(A)');
    const user = userEvent.setup();

    await user.click(screen.getByText('ざきお(A)').closest('button'));
    expect(mockNavigate).toHaveBeenCalledWith('/matches/101');
  });
});
