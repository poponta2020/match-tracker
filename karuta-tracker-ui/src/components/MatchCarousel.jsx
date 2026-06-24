import { useRef, useState, useEffect, useCallback } from 'react';
import { isHorizontalSwipe, resolveSwipe, clampOffset } from '../pages/matches/swipeGesture';

/**
 * 試合番号パネルを指追従で左右スライドさせる共通カルーセル。
 * 結果一覧（MatchResultsView）・一括入力（BulkResultInput）で共用する。
 *
 * - 現在±1のパネルのみ描画（端ではその方向のパネルを描画しない）
 * - touch でドラッグ追従（translateX）。離したときに resolveSwipe で
 *   スナップ確定／元に戻すを判定。端ではその方向へは確定しない（端で止まる）。
 * - 縦スクロールが優勢なときは横スワイプを発動しない（touchAction: pan-y）。
 * - 高さは表示中（中央）のパネルに合わせる。隣のパネルは絶対配置で高さに影響しない。
 *
 * @param {Object} props
 * @param {number} props.totalMatches      その日の試合数
 * @param {number} props.currentMatchNumber 現在表示中の試合番号（1始まり）
 * @param {(matchNumber:number)=>void} props.onChange 試合番号確定時のコールバック
 * @param {(matchNumber:number)=>React.ReactNode} props.renderPanel 各試合のパネル描画
 */
const SLIDE_MS = 200;

export default function MatchCarousel({ totalMatches, currentMatchNumber, onChange, renderPanel }) {
  const viewportRef = useRef(null);
  const [offset, setOffset] = useState(0);          // 指追従の表示オフセット(px)
  const [animating, setAnimating] = useState(false); // CSSトランジションを効かせるか

  // タッチ追跡（再描画を避けるため ref で保持）
  const gesture = useRef({ startX: 0, startY: 0, startTime: 0, axis: null, dx: 0, active: false });
  const lockRef = useRef(false); // スナップ確定アニメ中は新規スワイプを無視

  const atFirst = currentMatchNumber <= 1;
  const atLast = currentMatchNumber >= totalMatches;

  const commit = useCallback((dir) => {
    const width = viewportRef.current?.offsetWidth || 0;
    lockRef.current = true;
    setAnimating(true);
    setOffset(dir === 'next' ? -width : width);
    window.setTimeout(() => {
      // アニメ完了後にパネルを差し替える。新パネルは同じ画面位置に来るため見た目は連続。
      setAnimating(false);
      setOffset(0);
      onChange(dir === 'next' ? currentMatchNumber + 1 : currentMatchNumber - 1);
      lockRef.current = false;
    }, SLIDE_MS);
  }, [currentMatchNumber, onChange]);

  const snapBack = useCallback(() => {
    setAnimating(true);
    setOffset(0);
    window.setTimeout(() => setAnimating(false), SLIDE_MS);
  }, []);

  // touchmove で preventDefault するため、passive:false のネイティブリスナーで登録する
  useEffect(() => {
    const el = viewportRef.current;
    if (!el) return;

    const onTouchStart = (e) => {
      if (lockRef.current || totalMatches <= 1) return;
      const t = e.touches[0];
      gesture.current = {
        startX: t.clientX, startY: t.clientY, startTime: e.timeStamp,
        axis: null, dx: 0, active: true,
      };
      setAnimating(false);
    };

    const onTouchMove = (e) => {
      const g = gesture.current;
      if (!g.active) return;
      const t = e.touches[0];
      const dx = t.clientX - g.startX;
      const dy = t.clientY - g.startY;
      if (g.axis === null) {
        if (isHorizontalSwipe(dx, dy)) {
          g.axis = 'h';
        } else if (Math.abs(dy) > 10) {
          g.axis = 'v'; // 縦スクロールとして扱い、以降は無視
          return;
        } else {
          return; // まだ方向未確定（タップの可能性）
        }
      }
      if (g.axis !== 'h') return;
      e.preventDefault(); // 横スワイプ確定後はブラウザの縦スクロールを抑止
      g.dx = dx;
      setOffset(clampOffset(dx, { atFirst, atLast }));
    };

    const onTouchEnd = (e) => {
      const g = gesture.current;
      if (!g.active) return;
      g.active = false;
      if (g.axis !== 'h') return;
      const width = el.offsetWidth || 0;
      const dt = e.timeStamp - g.startTime;
      const velocity = dt > 0 ? g.dx / dt : 0;
      const clamped = clampOffset(g.dx, { atFirst, atLast });
      const dir = resolveSwipe({ dx: clamped, containerWidth: width, velocity });
      if (dir === 'next' && !atLast) commit('next');
      else if (dir === 'prev' && !atFirst) commit('prev');
      else snapBack();
    };

    el.addEventListener('touchstart', onTouchStart, { passive: true });
    el.addEventListener('touchmove', onTouchMove, { passive: false });
    el.addEventListener('touchend', onTouchEnd, { passive: true });
    el.addEventListener('touchcancel', onTouchEnd, { passive: true });
    return () => {
      el.removeEventListener('touchstart', onTouchStart);
      el.removeEventListener('touchmove', onTouchMove);
      el.removeEventListener('touchend', onTouchEnd);
      el.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [atFirst, atLast, totalMatches, commit, snapBack]);

  return (
    <div
      ref={viewportRef}
      data-testid="match-carousel"
      className="relative overflow-hidden"
      style={{ touchAction: 'pan-y' }}
    >
      <div
        className="relative"
        style={{
          transform: `translateX(${offset}px)`,
          transition: animating ? `transform ${SLIDE_MS}ms ease-out` : 'none',
          willChange: 'transform',
        }}
      >
        {/* 前の試合（左にチラ見え）。高さに影響しないよう絶対配置。
            チラ見え用のプレビューなので inert でフォーカス・操作対象から外す（a11y） */}
        {!atFirst && (
          <div
            className="absolute top-0 left-0 w-full"
            style={{ transform: 'translateX(-100%)', pointerEvents: 'none' }}
            aria-hidden="true"
            inert
          >
            {renderPanel(currentMatchNumber - 1)}
          </div>
        )}
        {/* 現在の試合（高さの基準） */}
        <div className="w-full">{renderPanel(currentMatchNumber)}</div>
        {/* 次の試合（右にチラ見え）。同上、inert で操作対象から外す */}
        {!atLast && (
          <div
            className="absolute top-0 left-0 w-full"
            style={{ transform: 'translateX(100%)', pointerEvents: 'none' }}
            aria-hidden="true"
            inert
          >
            {renderPanel(currentMatchNumber + 1)}
          </div>
        )}
      </div>
    </div>
  );
}
