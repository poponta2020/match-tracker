/**
 * 横スワイプジェスチャ判定（純粋関数モジュール）
 *
 * 試合番号タブの左右スワイプ移動で使用する。UIから切り離して単体テスト可能にする
 * （byePlayersLogic.js / pairingDragLogic.js と同じ方針）。
 *
 * 座標系・方向の約束:
 *   dx = 現在のX座標 - タッチ開始X座標
 *   - 左スワイプ（指を右→左, dx < 0）= 次の試合番号へ（'next', +1）
 *   - 右スワイプ（指を左→右, dx > 0）= 前の試合番号へ（'prev', -1）
 */

/**
 * 横スワイプとして扱うかどうかを判定する。
 * 横移動量が活性化閾値を超え、かつ縦移動より優勢なときだけ true。
 * （縦スクロールやタップとの誤発動を防ぐ）
 *
 * @param {number} dx 横移動量（現在X - 開始X）
 * @param {number} dy 縦移動量（現在Y - 開始Y）
 * @param {number} [activationPx=10] 活性化閾値(px)。これを超える横移動が必要
 * @returns {boolean}
 */
export function isHorizontalSwipe(dx, dy, activationPx = 10) {
  return Math.abs(dx) > activationPx && Math.abs(dx) > Math.abs(dy);
}

/**
 * 指を離したときに、隣の試合へスナップ確定するか・その方向を判定する。
 * 移動量がコンテナ幅の commitRatio 以上、または素早いフリック（速度が flickVelocity 以上）
 * のとき確定。確定しない場合は null（元の試合へ戻す）。
 *
 * 端（最初/最後）の判定は呼び出し側で行う（この関数は方向のみ返す）。
 *
 * @param {Object} params
 * @param {number} params.dx 横移動量
 * @param {number} params.containerWidth コンテナ幅(px)
 * @param {number} [params.commitRatio=0.25] 確定に必要な移動量の割合
 * @param {number} [params.velocity=0] 速度(px/ms)。フリック判定に使用
 * @param {number} [params.flickVelocity=0.5] フリックとみなす速度(px/ms)
 * @returns {'prev'|'next'|null}
 */
export function resolveSwipe({ dx, containerWidth, commitRatio = 0.25, velocity = 0, flickVelocity = 0.5 }) {
  if (!dx) return null;
  const distanceThreshold = containerWidth > 0 ? containerWidth * commitRatio : Infinity;
  const committed = Math.abs(dx) >= distanceThreshold || Math.abs(velocity) >= flickVelocity;
  if (!committed) return null;
  return dx < 0 ? 'next' : 'prev';
}

/**
 * 指追従カルーセルの表示用オフセットを返す。端方向への動きは抑制（0に固定）して
 * 「端で止まる」挙動にする。
 *
 * @param {number} dx 横移動量
 * @param {Object} [edges]
 * @param {boolean} [edges.atFirst=false] 最初の試合を表示中か
 * @param {boolean} [edges.atLast=false]  最後の試合を表示中か
 * @returns {number} 表示用オフセット(px)
 */
export function clampOffset(dx, { atFirst = false, atLast = false } = {}) {
  if (atFirst && dx > 0) return 0; // これ以上「前」へは行けない
  if (atLast && dx < 0) return 0;  // これ以上「次」へは行けない
  return dx;
}
