// 和紙繊維テクスチャ seamless タイル生成スクリプト（決定論的・使い捨て）
//
// design-md-anti-slop 機能の Task3。design-spec.md §3 の確定パラメータ（v15）を
// seed 固定の PRNG で描画し、seamless にタイリングできる透過 PNG を1枚生成する。
// 生成物は karuta-tracker-ui/src/assets/washi-fiber-tile.png にコミット済み。
// Home はこの静的 PNG を `background: #f2ede6 url(...) repeat` で参照するだけで、
// 実行時に Math.random の canvas は一切走らせない（AC-13）。
//
// 実行方法（このスクリプトの依存は FE の package.json に載せない＝一回限りのため）:
//   npm i -D @napi-rs/canvas   # または任意の場所に隔離インストール
//   node scripts/washi-tile/generate-washi-tile.mjs
//
// タイル寸法 460×460 は 4（簀の目間隔）と 46（糸目間隔）の公倍数なので、
// 罫線はタイル境界をまたいでも間隔が保たれる。繊維・塵はトーラス（±W/±H）に
// 複製描画して端の切れ目を消す。

import { createCanvas } from '@napi-rs/canvas';
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---- 決定論的 PRNG（mulberry32・seed 固定）----
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(20260716); // design-screen v15 決着日を seed に

// ---- タイル寸法 ----
const W = 460; // 460 = 46 × 10（糸目が割り切れる）
const H = 460; // 460 = 4 × 115（簀の目が割り切れる）

// 参照数量は 344×745 相当。タイル面積に比例させる。
const AREA_RATIO = (W * H) / (344 * 745);

const canvas = createCanvas(W, H);
const ctx = canvas.getContext('2d');
// 背景は塗らない（透過）。CSS 側で #f2ede6 の上に repeat 合成する。

// ---- 繊維トーン重み（design-spec §3）----
const FIBER_TONES = [
  ...Array(7).fill([255, 254, 250]),
  ...Array(7).fill([250, 247, 241]),
  [246, 243, 235],
  [240, 231, 216],
  [229, 216, 196],
  [221, 206, 183],
];
const pickTone = () => FIBER_TONES[Math.floor(rand() * FIBER_TONES.length)];

// トーラス複製オフセット（seamless 化）
const OFFSETS = [-1, 0, 1];

// 1本の繊維を（トーラス複製込みで）描く
function drawFiber({ x, y, len, alpha, lineWidth, tone }) {
  const angle = rand() * Math.PI * 2;
  const dx = Math.cos(angle);
  const dy = Math.sin(angle);
  const nx = -dy; // 法線
  const ny = dx;
  const x1 = x + dx * len;
  const y1 = y + dy * len;
  // 経路 33% / 66% 位置を法線方向に ±len*0.5*rand オフセットした3次ベジェ（カール）
  const o1 = (rand() - 0.5) * len;
  const o2 = (rand() - 0.5) * len;
  const cx1 = x + dx * (len * 0.33) + nx * o1;
  const cy1 = y + dy * (len * 0.33) + ny * o1;
  const cx2 = x + dx * (len * 0.66) + nx * o2;
  const cy2 = y + dy * (len * 0.66) + ny * o2;

  const [r, g, b] = tone;
  ctx.strokeStyle = `rgba(${r},${g},${b},${alpha})`;
  ctx.lineWidth = lineWidth;
  ctx.lineCap = 'round';
  for (const ox of OFFSETS) {
    for (const oy of OFFSETS) {
      const sx = ox * W;
      const sy = oy * H;
      ctx.beginPath();
      ctx.moveTo(x + sx, y + sy);
      ctx.bezierCurveTo(cx1 + sx, cy1 + sy, cx2 + sx, cy2 + sy, x1 + sx, y1 + sy);
      ctx.stroke();
    }
  }
}

function fiberPass(count, lenBase, lenRand, alphaBase, alphaRand, lineWidth) {
  const n = Math.round(count * AREA_RATIO);
  for (let i = 0; i < n; i++) {
    drawFiber({
      x: rand() * W,
      y: rand() * H,
      len: lenBase + rand() * lenRand,
      alpha: alphaBase + rand() * alphaRand,
      lineWidth,
      tone: pickTone(),
    });
  }
}

// 短繊維 → 中繊維 → 長繊維（design-spec §3 の数量・len・alpha・lineWidth）
fiberPass(860, 4.8, 12, 0.3, 0.38, 0.7);
fiberPass(286, 19.2, 25.6, 0.4, 0.4, 0.85);
fiberPass(43, 46.4, 36.8, 0.46, 0.36, 0.95);

// ---- 簀の目: 横4px間隔の細線 ----
ctx.strokeStyle = 'rgba(255,253,248,0.05)';
ctx.lineWidth = 1;
for (let y = 0; y < H; y += 4) {
  ctx.beginPath();
  ctx.moveTo(0, y + 0.5);
  ctx.lineTo(W, y + 0.5);
  ctx.stroke();
}

// ---- 糸目: 縦46px間隔（開始24px）の細線 ----
ctx.strokeStyle = 'rgba(255,253,248,0.08)';
ctx.lineWidth = 1;
for (let x = 24; x < W; x += 46) {
  ctx.beginPath();
  ctx.moveTo(x + 0.5, 0);
  ctx.lineTo(x + 0.5, H);
  ctx.stroke();
}

// ---- 塵: 樹皮片の小さな茶斑（トーラス複製）----
{
  const n = Math.round(66 * AREA_RATIO);
  for (let i = 0; i < n; i++) {
    const x = rand() * W;
    const y = rand() * H;
    const radius = 0.45 + rand() * 0.8;
    const alpha = 0.13 + rand() * 0.15;
    ctx.fillStyle = `rgba(118,90,64,${alpha})`;
    for (const ox of OFFSETS) {
      for (const oy of OFFSETS) {
        ctx.beginPath();
        ctx.arc(x + ox * W, y + oy * H, radius, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }
}

// ---- 出力 ----
const outDir = resolve(__dirname, '../../karuta-tracker-ui/src/assets');
mkdirSync(outDir, { recursive: true });
const outPath = resolve(outDir, 'washi-fiber-tile.png');
writeFileSync(outPath, canvas.toBuffer('image/png'));
console.log(`washi tile written: ${outPath} (${W}x${H}, area ratio ${AREA_RATIO.toFixed(3)})`);
