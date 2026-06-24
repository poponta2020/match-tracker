import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import MatchCarousel from './MatchCarousel';

/**
 * MatchCarousel（指追従カルーセル）の挙動テスト
 *
 * happy-dom はレイアウトを計算しないため offsetWidth は 0 を返す。
 * 確定閾値（コンテナ幅×25%）の判定に幅が必要なため、offsetWidth をモックする。
 */

const WIDTH = 300; // 確定閾値 = 300 * 0.25 = 75px
let offsetWidthSpy;

beforeEach(() => {
  offsetWidthSpy = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(WIDTH);
});

afterEach(() => {
  offsetWidthSpy.mockRestore();
  cleanup();
  vi.clearAllMocks();
});

const renderCarousel = (props = {}) => {
  const onChange = vi.fn();
  const renderPanel = (n) => <div data-testid={`panel-${n}`}>パネル{n}</div>;
  render(
    <MatchCarousel
      totalMatches={3}
      currentMatchNumber={2}
      onChange={onChange}
      renderPanel={renderPanel}
      {...props}
    />
  );
  return { onChange };
};

/** 横スワイプを発火する。dx>0で右（prev）、dx<0で左（next）へ */
const swipe = (el, dx, { dy = 0 } = {}) => {
  const startX = 200;
  const startY = 200;
  fireEvent.touchStart(el, { touches: [{ clientX: startX, clientY: startY }] });
  fireEvent.touchMove(el, { touches: [{ clientX: startX + dx, clientY: startY + dy }] });
  fireEvent.touchEnd(el, { changedTouches: [{ clientX: startX + dx, clientY: startY + dy }] });
};

describe('MatchCarousel', () => {
  it('現在パネルと前後パネルを描画する（中間の試合）', () => {
    renderCarousel({ currentMatchNumber: 2 });
    expect(screen.getByTestId('panel-2')).toBeInTheDocument();
    expect(screen.getByTestId('panel-1')).toBeInTheDocument(); // 前
    expect(screen.getByTestId('panel-3')).toBeInTheDocument(); // 次
  });

  it('最初の試合では前パネルを描画しない', () => {
    renderCarousel({ currentMatchNumber: 1 });
    expect(screen.queryByTestId('panel-0')).not.toBeInTheDocument();
    expect(screen.getByTestId('panel-1')).toBeInTheDocument();
    expect(screen.getByTestId('panel-2')).toBeInTheDocument();
  });

  it('最後の試合では次パネルを描画しない', () => {
    renderCarousel({ currentMatchNumber: 3 });
    expect(screen.queryByTestId('panel-4')).not.toBeInTheDocument();
    expect(screen.getByTestId('panel-3')).toBeInTheDocument();
    expect(screen.getByTestId('panel-2')).toBeInTheDocument();
  });

  it('左スワイプ（指を右→左）で次の試合番号へ onChange が呼ばれる', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 2 });
    swipe(screen.getByTestId('match-carousel'), -120); // 閾値75px超
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(3));
  });

  it('右スワイプ（指を左→右）で前の試合番号へ onChange が呼ばれる', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 2 });
    swipe(screen.getByTestId('match-carousel'), 120);
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(1));
  });

  it('閾値未満のゆっくりしたスワイプでは onChange が呼ばれない（元の試合へ戻る）', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 2 });
    const el = screen.getByTestId('match-carousel');
    // 移動量50px（閾値75px未満）かつ低速（フリックでない）→ 確定しない
    fireEvent.touchStart(el, { touches: [{ clientX: 200, clientY: 200 }] });
    fireEvent.touchMove(el, { touches: [{ clientX: 150, clientY: 200 }] }); // dx=-50
    await new Promise((r) => setTimeout(r, 200)); // ゆっくり離す（低速 → フリック扱いにしない）
    fireEvent.touchEnd(el, { changedTouches: [{ clientX: 150, clientY: 200 }] });
    await new Promise((r) => setTimeout(r, 250));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('素早いフリックなら小さな移動でも確定する', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 2 });
    // 同期発火＝ほぼ瞬間移動＝高速フリック。移動量40px（閾値未満）でも確定
    swipe(screen.getByTestId('match-carousel'), -40);
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(3));
  });

  it('最初の試合で前へスワイプしても何も起きない（端で止まる）', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 1 });
    swipe(screen.getByTestId('match-carousel'), 120); // prev方向
    await new Promise((r) => setTimeout(r, 250));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('最後の試合で次へスワイプしても何も起きない（端で止まる）', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 3 });
    swipe(screen.getByTestId('match-carousel'), -120); // next方向
    await new Promise((r) => setTimeout(r, 250));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('縦移動が優勢なときは横スワイプを発動しない（縦スクロール）', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 2 });
    swipe(screen.getByTestId('match-carousel'), -120, { dy: 200 }); // 縦優勢
    await new Promise((r) => setTimeout(r, 250));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('試合数が1以下のときはスワイプ無効', async () => {
    const { onChange } = renderCarousel({ currentMatchNumber: 1, totalMatches: 1 });
    swipe(screen.getByTestId('match-carousel'), -120);
    await new Promise((r) => setTimeout(r, 250));
    expect(onChange).not.toHaveBeenCalled();
  });
});
