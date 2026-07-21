import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

// isVisible をテストごとに制御するため BottomNavContext をモックする（AC-6 回帰の検証に必要）。
let mockIsVisible = true;
vi.mock('../context/BottomNavContext', () => ({
  useBottomNav: () => ({ isVisible: mockIsVisible, setVisible: () => {} }),
}));

import Layout from './Layout';

const TRACK_WIDTH = 375; // happy-dom はレイアウトを持たないため clientWidth をスタブしてカプセルを描画させる
let originalClientWidth;

const NAV_ITEMS = [
  { label: 'ホーム', href: '/' },
  { label: '対戦', href: '/matches/results' },
  { label: '練習', href: '/practice' },
  { label: '戦績', href: '/matches' },
  { label: '設定', href: '/settings' },
];

let locationRef = null;
const LocationProbe = () => {
  locationRef = useLocation();
  return null;
};

const renderAt = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <LocationProbe />
              <Layout>content</Layout>
            </>
          }
        />
      </Routes>
    </MemoryRouter>
  );

beforeEach(() => {
  mockIsVisible = true;
  locationRef = null;
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  );
  // clientWidth を prototype に一時上書き（元の記述子を保存して afterEach で厳密に復元）
  originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
    configurable: true,
    get: () => TRACK_WIDTH,
  });
});

afterEach(() => {
  cleanup();
  if (originalClientWidth) {
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
  } else {
    delete HTMLElement.prototype.clientWidth;
  }
  vi.unstubAllGlobals();
});

describe('Layout ボトムナビ（リキッドグラス）', () => {
  it('5項目を現状の順序・遷移先・aria-label で描画する (AC-1, AC-3)', () => {
    renderAt('/');
    const nav = screen.getByRole('navigation');
    const links = within(nav).getAllByRole('link');
    expect(links).toHaveLength(5);
    links.forEach((link, i) => {
      expect(link).toHaveAttribute('href', NAV_ITEMS[i].href);
      expect(link).toHaveAccessibleName(NAV_ITEMS[i].label);
    });
  });

  it('アイコンのみで、可視テキストラベルを描画しない (AC-2)', () => {
    renderAt('/');
    // 新 aria-label 文言はテキストノードとしては存在しない
    NAV_ITEMS.forEach(({ label }) => {
      expect(screen.queryByText(label)).toBeNull();
    });
    // 旧英語ラベルも残っていない
    ['Home', 'Match', 'Schedule', 'Record', 'Settings'].forEach((t) => {
      expect(screen.queryByText(t)).toBeNull();
    });
  });

  const activeCases = [
    { path: '/', label: 'ホーム' },
    { path: '/matches/results', label: '対戦' },
    { path: '/matches/results/abc123', label: '対戦' }, // 前方一致（AC-4）
    { path: '/practice', label: '練習' },
    { path: '/practice/2026-07', label: '練習' }, // 前方一致（AC-4）
    { path: '/matches', label: '戦績' },
    { path: '/settings', label: '設定' },
  ];

  it.each(activeCases)('$path で $label が唯一アクティブになる (AC-4)', ({ path, label }) => {
    renderAt(path);
    const activeLink = screen.getByRole('link', { name: label });
    expect(activeLink).toHaveAttribute('aria-current', 'page');
    screen
      .getAllByRole('link')
      .filter((l) => l !== activeLink)
      .forEach((l) => expect(l).not.toHaveAttribute('aria-current'));
  });

  it.each(activeCases)('$path でアクティブはソリッド・非アクティブはアウトライン (AC-14)', ({ path, label }) => {
    renderAt(path);
    screen.getAllByRole('link').forEach((link) => {
      const svg = link.querySelector('svg');
      if (link.getAttribute('aria-label') === label) {
        expect(svg).toHaveAttribute('fill', 'currentColor'); // solid
      } else {
        expect(svg).toHaveAttribute('fill', 'none'); // outline
      }
    });
  });

  it('カプセルは1個だけ、アクティブ項目位置に描画される (AC-5, AC-15)', () => {
    renderAt('/practice');
    expect(screen.getAllByTestId('bottom-nav-capsule')).toHaveLength(1);
  });

  it('どの項目にも一致しないパス（/matches/new）ではアクティブもカプセルも無い (AC-4, AC-5)', () => {
    renderAt('/matches/new');
    expect(screen.queryByTestId('bottom-nav-capsule')).toBeNull();
    screen.getAllByRole('link').forEach((l) => expect(l).not.toHaveAttribute('aria-current'));
  });

  it('isVisible=false でナビがスライドアウトする: aria-hidden / tabIndex=-1 / pointer-events-none (AC-6 回帰)', () => {
    mockIsVisible = false;
    renderAt('/');
    const nav = document.querySelector('nav'); // aria-hidden=true のため role 検索では拾えない
    expect(nav).toHaveAttribute('aria-hidden', 'true');
    const links = nav.querySelectorAll('a');
    expect(links).toHaveLength(5);
    links.forEach((l) => expect(l).toHaveAttribute('tabindex', '-1'));
    const pill = nav.querySelector('div');
    expect(pill.className).toContain('pointer-events-none');
  });

  it('isVisible=true ではナビが操作可能: aria-hidden=false / tabIndex=0 (AC-6 回帰)', () => {
    renderAt('/');
    const nav = screen.getByRole('navigation');
    expect(nav).toHaveAttribute('aria-hidden', 'false');
    within(nav)
      .getAllByRole('link')
      .forEach((l) => expect(l).toHaveAttribute('tabindex', '0'));
  });

  // カプセルのドラッグ確定/中断（Pointer Events）。実ドラッグの見た目は verify/manual だが、
  // 「確定→遷移／中断→非遷移」の分岐は誤遷移バグの回帰ガードとして固定する。
  const dragCapsule = () => {
    const capsule = screen.getByTestId('bottom-nav-capsule');
    fireEvent.pointerDown(capsule, { clientX: 40, pointerId: 1 });
    fireEvent.pointerMove(capsule, { clientX: 340, pointerId: 1 }); // 最終スロット付近まで移動
    return capsule;
  };

  it('ドラッグ後の pointerup で最寄り項目（設定）へ遷移する (AC-16)', () => {
    renderAt('/'); // active=ホーム(0)
    const capsule = dragCapsule();
    fireEvent.pointerUp(capsule, { clientX: 340, pointerId: 1 });
    expect(locationRef.pathname).toBe('/settings');
  });

  it('ドラッグ中に pointercancel が来ても遷移しない (誤遷移回帰)', () => {
    renderAt('/');
    const capsule = dragCapsule();
    fireEvent.pointerCancel(capsule, { pointerId: 1 });
    expect(locationRef.pathname).toBe('/');
  });

  it('追跡外ポインター（別の指）の pointerup では遷移しない (誤遷移回帰)', () => {
    renderAt('/');
    const capsule = dragCapsule();
    fireEvent.pointerUp(capsule, { clientX: 340, pointerId: 2 });
    expect(locationRef.pathname).toBe('/');
  });
});
