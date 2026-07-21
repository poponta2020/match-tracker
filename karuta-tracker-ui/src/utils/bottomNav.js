// ボトムナビ（リキッドグラス）のカプセル位置計算・ドラッグ判定の純ロジック。
// jsdom では実ドラッグ（Pointer Events + pointer capture）を再現できないため、
// 位置→最寄りスロット等の決定的なロジックを DOM から切り出して単体テスト可能にする。

// スロット幅(px) = トラック幅 / 項目数。無効値では 0 を返す。
export const slotWidthOf = (trackWidth, count) =>
  trackWidth > 0 && count > 0 ? trackWidth / count : 0;

// カプセル中心(px)から最寄りスロットの index を返す（両端はクランプ）。
// centerPx / slotWidth はスロット境界基準の連続位置。-0.5 して丸めるとスロット中心に最も近い index になる。
export const nearestIndex = (centerPx, slotWidth, count) => {
  if (slotWidth <= 0 || count <= 0) return 0;
  return Math.max(0, Math.min(count - 1, Math.round(centerPx / slotWidth - 0.5)));
};

// アクティブ index に対応するカプセル中心(px)。無効時は null（＝カプセル非表示）。
export const capsuleCenterOf = (index, slotWidth) =>
  index >= 0 && slotWidth > 0 ? slotWidth * (index + 0.5) : null;

// ポインタの相対X(トラック左端からのpx)を、カプセルが端からはみ出さないよう半幅ぶんクランプする。
export const clampCenter = (x, trackWidth, capsuleWidth) =>
  Math.max(capsuleWidth / 2, Math.min(trackWidth - capsuleWidth / 2, x));
