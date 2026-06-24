/**
 * 試合番号タブバーの自動スクロールヘルパー。
 *
 * スワイプ／タブタップで選択中タブが変わったとき、横スクロール可能なタブバー内で
 * アクティブタブが画面内に見えるよう、タブバー自身の scrollLeft だけを調整する。
 * ページ全体の縦スクロールには一切影響しない。
 *
 * アクティブなタブには `data-active="true"` を付与しておくこと。
 *
 * @param {HTMLElement|null} tabBarEl 横スクロールするタブバー要素
 * @param {number} [margin=16] アクティブタブの両脇に確保する余白(px)
 */
export function scrollActiveTabIntoView(tabBarEl, margin = 16) {
  if (!tabBarEl) return;
  const active = tabBarEl.querySelector('[data-active="true"]');
  if (!active) return;
  const barRect = tabBarEl.getBoundingClientRect();
  const aRect = active.getBoundingClientRect();
  if (aRect.left < barRect.left) {
    tabBarEl.scrollLeft -= (barRect.left - aRect.left) + margin;
  } else if (aRect.right > barRect.right) {
    tabBarEl.scrollLeft += (aRect.right - barRect.right) + margin;
  }
}
