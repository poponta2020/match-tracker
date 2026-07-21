import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { Swords } from 'lucide-react';
import {
  HomeIcon as HomeOutline,
  CalendarDaysIcon as CalendarOutline,
  ChartBarIcon as ChartOutline,
  Cog6ToothIcon as CogOutline,
} from '@heroicons/react/24/outline';
import {
  HomeIcon as HomeSolid,
  CalendarDaysIcon as CalendarSolid,
  ChartBarIcon as ChartSolid,
  Cog6ToothIcon as CogSolid,
} from '@heroicons/react/24/solid';
import { useBottomNav } from '../context/BottomNavContext';
import { slotWidthOf, nearestIndex, capsuleCenterOf, clampCenter } from '../utils/bottomNav';

// ボトムナビゲーションの項目定義（aria-label はアイコンのみ化に伴うアクセシブル名）。
// 非アクティブ=アウトライン / アクティブ=ソリッド（塗り）。
// 対戦のみ lucide の剣（Heroicons に剣が無いため。アクティブ時は fill で塗る）、他は Heroicons のアウトライン/ソリッド対。
const bottomNavItems = [
  { name: 'Home', label: 'ホーム', href: '/', Outline: HomeOutline, Solid: HomeSolid, lucide: false },
  { name: 'Match', label: '対戦', href: '/matches/results', Outline: Swords, Solid: Swords, lucide: true },
  { name: 'Schedule', label: '練習', href: '/practice', Outline: CalendarOutline, Solid: CalendarSolid, lucide: false },
  { name: 'Record', label: '戦績', href: '/matches', Outline: ChartOutline, Solid: ChartSolid, lucide: false },
  { name: 'Settings', label: '設定', href: '/settings', Outline: CogOutline, Solid: CogSolid, lucide: false },
];

const COUNT = bottomNavItems.length;
const CAPSULE_W = 62; // カプセル幅(px) — 横長の楕円
const CAPSULE_H = 48; // カプセル高さ(px)

const Layout = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { isVisible } = useBottomNav();

  // ボトムナビゲーションのアクティブ判定（パスの前方一致も考慮）
  const isBottomNavActive = (href) => {
    if (href === '/') {
      return location.pathname === '/';
    }
    // 完全一致または特定のサブパスのみアクティブにする
    if (href === '/matches/results') {
      // /matches/results で始まる場合（/matches/results/:sessionId含む）
      return location.pathname.startsWith('/matches/results');
    }
    if (href === '/matches') {
      // /matches で始まるが /matches/new と /matches/results ではない場合
      return location.pathname.startsWith('/matches') &&
             location.pathname !== '/matches/new' &&
             !location.pathname.startsWith('/matches/results');
    }
    if (href === '/settings') {
      return location.pathname === '/settings';
    }
    return location.pathname.startsWith(href);
  };

  // スライドするカプセル用の計測・ドラッグ状態
  const trackRef = useRef(null);
  const pillRef = useRef(null);
  const pressRef = useRef({ startX: 0, moved: false, pointerId: null });
  const [trackWidth, setTrackWidth] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [dragCenter, setDragCenter] = useState(null); // ドラッグ中のカプセル中心(px)
  // インタラクション時の「膨らんで戻る」スケール（iOS26 の bubbly glass を近似）
  const [interacting, setInteracting] = useState(false);
  const pulseRef = useRef(null);

  useEffect(() => {
    const el = trackRef.current;
    if (!el) return undefined;
    const update = () => setTrackWidth(el.clientWidth);
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // タップ移動時はカプセルを一瞬だけ膨らませて戻す（掴んでいる間は onPointerDown/Up で保持）
  useEffect(() => () => { if (pulseRef.current) clearTimeout(pulseRef.current); }, []);
  const pulseInteract = () => {
    setInteracting(true);
    if (pulseRef.current) clearTimeout(pulseRef.current);
    pulseRef.current = setTimeout(() => setInteracting(false), 240);
  };

  // ピル全体の「膨らんで即戻る」一発パフ（止まらず流れる）。CSS animation を毎回リスタート
  const triggerPuff = () => {
    const el = pillRef.current;
    if (!el) return;
    el.style.animation = 'none';
    void el.offsetWidth; // reflow でアニメーションをリスタート
    el.style.animation = 'navPuff 240ms ease-out'; // 0.75倍速（ゆっくりめ）
  };

  const activeIndex = bottomNavItems.findIndex((it) => isBottomNavActive(it.href));

  const slotWidth = slotWidthOf(trackWidth, COUNT);
  // ドラッグ中は指の近傍アイコンを先取りでハイライト
  const highlightIndex =
    dragging && dragCenter != null ? nearestIndex(dragCenter, slotWidth, COUNT) : activeIndex;
  // カプセル中心(px)。ドラッグ中は指、通常はアクティブスロット中心
  const capsuleCenter =
    dragging && dragCenter != null ? dragCenter : capsuleCenterOf(activeIndex, slotWidth);

  const pointerToCenter = (clientX) => {
    const rect = trackRef.current.getBoundingClientRect();
    return clampCenter(clientX - rect.left, trackWidth, CAPSULE_W);
  };

  const commitSelect = (idx) => {
    navigate(bottomNavItems[idx].href);
  };

  // 追跡中のドラッグを「遷移させずに」破棄する（pointercancel・追跡外ポインター用）
  const resetPress = () => {
    pressRef.current = { startX: 0, moved: false, pointerId: null };
    setDragging(false);
    setDragCenter(null);
  };

  const onCapsulePointerDown = (e) => {
    if (slotWidth <= 0) return;
    pressRef.current = { startX: e.clientX, moved: false, pointerId: e.pointerId };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  };
  const onCapsulePointerMove = (e) => {
    // 追跡中のポインターのイベントのみ扱う（別の指・stale event を無視）
    if (pressRef.current.pointerId == null || e.pointerId !== pressRef.current.pointerId) return;
    // 4px 未満はタップ扱い（ドラッグ開始しない）
    if (!pressRef.current.moved && Math.abs(e.clientX - pressRef.current.startX) < 4) return;
    pressRef.current.moved = true;
    if (!dragging) setDragging(true);
    setDragCenter(pointerToCenter(e.clientX));
  };
  const onCapsulePointerUp = (e) => {
    // 追跡中ポインターの pointerup のみ確定処理する（別の指の up・stale event で誤遷移しない）
    if (pressRef.current.pointerId == null || e.pointerId !== pressRef.current.pointerId) return;
    const { moved } = pressRef.current;
    pressRef.current = { startX: 0, moved: false, pointerId: null };
    if (moved) {
      // ドラッグ確定: 遷移先は「解放イベントの座標」から確定する。
      // 描画追従用の dragCenter は React state で、素早いドラッグでは最後の
      // pointermove から解放までの指の移動を取りこぼし古い値になり得るため使わない。
      const center = pointerToCenter(e.clientX);
      commitSelect(nearestIndex(center, slotWidth, COUNT));
      setDragging(false);
      setDragCenter(null);
    } else {
      // カプセル上のタップ（移動なし）: 流れる一発パフ
      setDragging(false);
      setDragCenter(null);
      triggerPuff();
      pulseInteract();
    }
  };
  // pointercancel（OS割り込み・pointer capture 喪失など）: 遷移させずドラッグ状態だけ破棄する
  const onCapsulePointerCancel = (e) => {
    if (pressRef.current.pointerId != null && e.pointerId !== pressRef.current.pointerId) return;
    resetPress();
  };

  // アイコンのタップ: カプセル/ピルの膨らみ演出のみ。遷移は Link の通常挙動に任せる。
  const onItemClick = () => {
    pulseInteract(); // カプセルを一瞬膨らませて戻す
    triggerPuff();   // ピル全体を流れるように一発パフ
  };

  return (
    <div className="min-h-screen bg-[#f2ede6]" style={{ paddingBottom: 'calc(5rem + env(safe-area-inset-bottom, 0px))' }}>
      {/* ベースナビバー（各ページのナビバーが z-50 で上書きする。ローディング中のフォールバック） */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-40 px-4 py-4">
        <div className="max-w-7xl mx-auto h-7" />
      </div>

      {/* メインコンテンツ */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-8">
        {children}
      </main>

      {/* ボトムナビゲーション（リキッドグラス・浮いた角丸ピル）:
          - 外側 <nav> は fixed のまま transform を持たせない（iOS Safari で fixed が解除されるのを防ぐ）。
          - スライド用 transform と backdrop-filter は「同一要素（ピル本体）」に載せる。
            backdrop-filter は ancestor に transform があると背景を拾えず透明化するため、
            transform を親に置かず自分自身に持たせることで iOS でもすりガラスが成立する。
          - アクティブのカプセルは 1個だけ。translateX でスロット間をスライド（タップ=glide / ドラッグ=指追従）。 */}
      <nav
        className="fixed inset-x-0 bottom-0 z-50 pointer-events-none flex justify-center px-4"
        style={{ paddingBottom: 'calc(env(safe-area-inset-bottom, 0px) + 10px)' }}
        aria-hidden={!isVisible}
      >
        <div
          ref={pillRef}
          className={`relative w-full max-w-md overflow-hidden rounded-[28px] ${
            isVisible ? 'pointer-events-auto' : 'pointer-events-none'
          }`}
          style={{
            WebkitBackdropFilter: 'blur(18px) saturate(190%)',
            backdropFilter: 'blur(18px) saturate(190%)',
            background:
              'linear-gradient(to bottom, rgba(90,92,96,0.6), rgba(58,60,64,0.7))',
            border: '1px solid rgba(255,255,255,0.45)',
            boxShadow:
              'inset 0 1.5px 1px rgba(255,255,255,0.55), inset 0 -1px 2px rgba(0,0,0,0.18), 0 8px 22px rgba(0,0,0,0.22)',
            // スライド(translateY)は transform。膨らみは個別 scale プロパティで扱う:
            //  - ドラッグ中は scale を保持（膨らんで止まる）→ 離すと transition で戻る
            //  - タップは navPuff アニメで流れる一発パフ（scale を上書き）
            // 非表示時はピル高さ(100%)に加えて浮かせ量(safe-area+10px)ぶんも下げ、
            // safe-area の大きい iPhone でもピル上端が画面内に残らないよう完全に隠す。
            transform: isVisible
              ? 'translateY(0px)'
              : 'translateY(calc(100% + env(safe-area-inset-bottom, 0px) + 10px))',
            scale: dragging ? '1.035' : '1',
            transition: 'transform 300ms ease, scale 200ms ease-out',
          }}
        >
          {/* 斜めの鏡面スジ（sheen） */}
          <div
            className="pointer-events-none absolute inset-y-0 -left-[10%] w-[55%] -skew-x-[18deg]"
            style={{
              background:
                'linear-gradient(115deg, transparent, rgba(255,255,255,0.22) 45%, transparent 70%)',
            }}
          />
          <div ref={trackRef} className="relative flex h-16 items-center justify-around">
            {/* スライドするアクティブカプセル（ドラッグでタブ切替 / タップで該当位置へ glide） */}
            {capsuleCenter != null && (
              <span
                aria-hidden="true"
                data-testid="bottom-nav-capsule"
                onPointerDown={onCapsulePointerDown}
                onPointerMove={onCapsulePointerMove}
                onPointerUp={onCapsulePointerUp}
                onPointerCancel={onCapsulePointerCancel}
                className="absolute top-1/2 rounded-full"
                style={{
                  left: 0,
                  height: CAPSULE_H,
                  width: CAPSULE_W,
                  // 横位置(translate)＋膨らみ(scale)。ドラッグ中は保持で膨らみ・指追従のため transition なし。
                  // 移動速度は 0.75倍速（320→427ms）。
                  transform: `translate(${capsuleCenter - CAPSULE_W / 2}px, -50%) scale(${dragging || interacting ? 1.18 : 1})`,
                  transition: dragging ? 'none' : 'transform 427ms cubic-bezier(0.34, 1.56, 0.64, 1)',
                  touchAction: 'none',
                  cursor: dragging ? 'grabbing' : 'grab',
                  background: 'rgba(255,255,255,0.22)',
                  boxShadow: 'inset 0 1px 1px rgba(255,255,255,0.55), 0 1px 3px rgba(0,0,0,0.12)',
                  zIndex: 2,
                }}
              />
            )}
            {bottomNavItems.map((item, i) => {
              const active = i === highlightIndex;
              const Icon = active ? item.Solid : item.Outline;
              // lucide(剣): アクティブは fill で塗る。Heroicons: アウトライン時のみ線幅を lucide に寄せる
              const iconProps = item.lucide
                ? { strokeWidth: active ? 2.5 : 2, fill: active ? 'currentColor' : 'none' }
                : active
                  ? {}
                  : { strokeWidth: 1.8 };
              return (
                <Link
                  key={item.name}
                  to={item.href}
                  tabIndex={isVisible ? 0 : -1}
                  aria-label={item.label}
                  aria-current={active ? 'page' : undefined}
                  onClick={onItemClick}
                  className="relative z-[1] flex h-full flex-1 items-center justify-center"
                >
                  <Icon
                    className={`relative h-7 w-7 transition-colors ${
                      active ? 'text-white' : 'text-white/70'
                    }`}
                    {...iconProps}
                  />
                </Link>
              );
            })}
          </div>
        </div>
      </nav>
    </div>
  );
};

export default Layout;
