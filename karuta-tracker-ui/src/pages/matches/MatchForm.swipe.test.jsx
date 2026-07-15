import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, within } from '@testing-library/react';
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
  useAuth: () => ({ currentPlayer: { id: 1, name: 'テスト選手' } }),
}));

import { matchAPI, playerAPI, practiceAPI, pairingAPI, byeActivityAPI } from '../../api';
import MatchForm from './MatchForm';

/**
 * MatchForm スワイプ移動 + 未保存警告 + dirty追跡テスト
 *
 * - 未入力ならスワイプ／タブタップで即切替
 * - 入力途中（dirty）ならスワイプ／タブタップで確認ダイアログ → OKで切替・キャンセルで据え置き
 * - 端では切り替わらない
 */

const PAST_DATE = '2026-01-15'; // 当日でない日付（参加登録ダイアログを回避）
const SESSION = { id: 100, sessionDate: PAST_DATE, totalMatches: 3 };

const WIDTH = 300;
let offsetWidthSpy;

beforeEach(() => {
  offsetWidthSpy = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(WIDTH);
  playerAPI.getAll.mockResolvedValue({ data: [{ id: 2, name: '対戦相手A' }, { id: 3, name: '対戦相手B' }] });
  practiceAPI.getByDate.mockResolvedValue({ data: SESSION });
  practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
  pairingAPI.getByDate.mockResolvedValue({ data: [] }); // ペアリングなし → 通常入力フォーム
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
  // 全試合 未記録（exists:false）
  matchAPI.getByPlayerDateAndMatchNumber.mockRejectedValue(new Error('not found'));
});

afterEach(() => {
  offsetWidthSpy.mockRestore();
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

const tab = (n) => screen.getByRole('button', { name: new RegExp(`${n}試合目`) });

const swipeArea = () => screen.getByTestId('matchform-swipe-area');
const swipe = (dx) => {
  const el = swipeArea();
  fireEvent.touchStart(el, { touches: [{ clientX: 200, clientY: 200 }] });
  fireEvent.touchEnd(el, { changedTouches: [{ clientX: 200 + dx, clientY: 200 }] });
};

// dirty にする（結果ボタンをタップ）
const makeDirty = async () => {
  const loseBtn = await screen.findByRole('button', { name: /負け/ });
  fireEvent.click(loseBtn);
};

describe('MatchForm - 試合番号スワイプ移動と未保存警告', () => {
  it('未入力ならタブタップで確認なしに即切替', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    fireEvent.click(tab(2));
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(screen.queryByText(/入力中の内容は破棄されます/)).not.toBeInTheDocument();
  });

  it('未入力なら左スワイプで次の試合へ即切替', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    swipe(-120);
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
  });

  it('入力途中でタブタップすると確認ダイアログが出る（OKで切替）', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    await makeDirty();
    fireEvent.click(tab(2));

    await waitFor(() => expect(screen.getByText(/入力中の内容は破棄されます/)).toBeInTheDocument());
    expect(tab(1)).toHaveAttribute('data-active', 'true'); // まだ切り替わっていない

    fireEvent.click(screen.getByRole('button', { name: '移動する' }));
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(screen.queryByText(/入力中の内容は破棄されます/)).not.toBeInTheDocument();
  });

  it('入力途中でタブタップ → キャンセルすると据え置き', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    await makeDirty();
    fireEvent.click(tab(2));
    await waitFor(() => expect(screen.getByText(/入力中の内容は破棄されます/)).toBeInTheDocument());

    // フォーム本体にも「キャンセル」があるため、確認モーダル内のキャンセルに限定する
    const dialog = screen.getByText(/入力中の内容は破棄されます/).closest('div.bg-white');
    fireEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }));
    await waitFor(() => expect(screen.queryByText(/入力中の内容は破棄されます/)).not.toBeInTheDocument());
    expect(tab(1)).toHaveAttribute('data-active', 'true');
  });

  it('入力途中で左スワイプしても確認ダイアログが出る', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    await makeDirty();
    swipe(-120);
    await waitFor(() => expect(screen.getByText(/入力中の内容は破棄されます/)).toBeInTheDocument());
  });

  it('最後の試合で左スワイプしても切り替わらない（端で止まる）', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    fireEvent.click(tab(3));
    await waitFor(() => expect(tab(3)).toHaveAttribute('data-active', 'true'));

    swipe(-120); // next方向だが端
    await new Promise((r) => setTimeout(r, 100));
    expect(tab(3)).toHaveAttribute('data-active', 'true');
    expect(screen.queryByText(/入力中の内容は破棄されます/)).not.toBeInTheDocument();
  });
});

describe('MatchForm - 盤面ドラッグはスワイプ切替を誘発しない（C-5）', () => {
  it('data-swipe-ignore 配下（盤面）起点の横タッチでは試合番号が切り替わらない', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    // 「取り札・お手付きを記録」折りたたみを展開して盤面を出す
    fireEvent.click(screen.getByRole('button', { name: /取り札・お手付きを記録/ }));
    await screen.findByText('敵 陣');

    // 盤面内の要素（data-swipe-ignore 配下）起点で「次の試合」相当の横スワイプ
    const board = document.querySelector('.tr-board');
    expect(board).toBeTruthy();
    fireEvent.touchStart(board, { touches: [{ clientX: 200, clientY: 200 }] });
    fireEvent.touchEnd(board, { changedTouches: [{ clientX: 80, clientY: 200 }] }); // dx=-120（本来 next 方向）

    // 盤面起点は除外されるため切り替わらない
    await new Promise((r) => setTimeout(r, 50));
    expect(tab(1)).toHaveAttribute('data-active', 'true');
  });
});

describe('MatchForm - スワイプ操作ヒント', () => {
  it('新規入力でタブ2件以上なら案内テキストを表示する', async () => {
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
    expect(screen.getByText(/スワイプで試合を切替/)).toBeInTheDocument();
  });

  it('新規入力でも1試合のみなら案内テキストを表示しない', async () => {
    practiceAPI.getByDate.mockResolvedValue({ data: { ...SESSION, totalMatches: 1 } });
    renderForm();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
    expect(screen.queryByText(/スワイプで試合を切替/)).toBeNull();
  });

  it('編集モードでは案内テキストを表示しない', async () => {
    matchAPI.getById.mockResolvedValue({
      data: {
        id: 555, matchDate: PAST_DATE, matchNumber: 1,
        player1Id: 1, player2Id: 2, opponentName: '対戦相手A',
        result: '勝ち', scoreDifference: 3, isLesson: false,
      },
    });
    render(
      <MemoryRouter initialEntries={[{ pathname: '/matches/555/edit', state: { matchDate: PAST_DATE } }]}>
        <Routes>
          <Route path="/matches/:id/edit" element={<MatchForm />} />
          <Route path="/matches" element={<div>matches</div>} />
        </Routes>
      </MemoryRouter>
    );
    await screen.findByRole('button', { name: '更新する' });
    expect(screen.queryByText(/スワイプで試合を切替/)).toBeNull();
  });
});
