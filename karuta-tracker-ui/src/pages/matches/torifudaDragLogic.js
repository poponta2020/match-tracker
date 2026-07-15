// 取り札盤面の D&D 純ロジック（UI / @dnd-kit から切り離してテスト可能にする）。
// - droppable id のエンコード/パース
// - ドロップ結果の placements 計算（配置 / 移動 / 解除）
// - 実ドラッグ直後の「合成 click」を1回だけ無視する trailing-click ガード

export const POOL_ID = 'pool';

/** マスの droppable id を作る（cell:FIELD:SIDE:TIER:TAKENBY）。 */
export function encodeCellId(field, side, tier, takenBy) {
  return `cell:${field}:${side}:${tier}:${takenBy}`;
}

/** droppable id を解釈する。cell→{type,...}／pool→{type:'pool'}／不正→null。 */
export function parseDroppableId(id) {
  if (id == null) return null;
  if (id === POOL_ID) return { type: 'pool' };
  const s = String(id);
  if (!s.startsWith('cell:')) return null;
  const [, field, side, tier, takenBy] = s.split(':');
  if (!field || !side || !tier || !takenBy) return null;
  return { type: 'cell', field, side, tier, takenBy };
}

/**
 * ドラッグ結果から次の placements を計算する。変化がなければ null を返す。
 * @param {object} p
 * @param {number} p.activeCardNo ドラッグした札番号
 * @param {string|null} p.overId ドロップ先 droppable id（枠外なら null）
 * @param {object} p.placements 現在の配置 { [cardNo]: {takenBy,field,side,tier} }
 * @returns {object|null} 次の placements、または変化なし/不正なら null
 */
export function computeDrop({ activeCardNo, overId, placements }) {
  if (activeCardNo == null) return null;
  const target = parseDroppableId(overId);
  if (!target) return null; // 枠外・不正なドロップ
  const placed = placements || {};
  const current = placed[activeCardNo];

  if (target.type === 'pool') {
    // マス→不明（配置解除）。もともと未配置なら変化なし。
    if (!current) return null;
    const next = { ...placed };
    delete next[activeCardNo];
    return next;
  }

  // マスへ配置（不明→マス）/ 移動（マス→別マス）。同一マスなら変化なし。
  const { field, side, tier, takenBy } = target;
  if (current && current.field === field && current.side === side
      && current.tier === tier && current.takenBy === takenBy) {
    return null;
  }
  return { ...placed, [activeCardNo]: { takenBy, field, side, tier } };
}

/**
 * 実ドラッグ直後にブラウザが合成する click を1回だけ無視するためのガード。
 * 同一チップに onClick と useDraggable を併用すると、ドラッグ（移動/解除）直後の
 * trailing click が onClick（例: 配置済み札の unplace）を誤発火し、ドラッグ結果を
 * 打ち消す（ブラウザ/センサー依存の footgun）。onDragStart で立て、直後の1クリックで
 * 下ろす。合成 click が来ないケースに備え、backstop タイマーでも自動解除する。
 * setTimeout/clearTimeout を DI 可能にしてテスト容易化。
 */
export function createTrailingClickGuard(setTimer = setTimeout, clearTimer = clearTimeout, ms = 300) {
  let armed = false;
  let timer = null;
  const disarm = () => {
    armed = false;
    if (timer != null) { clearTimer(timer); timer = null; }
  };
  return {
    onDragStart() {
      armed = true;
      if (timer != null) { clearTimer(timer); timer = null; }
    },
    onDragEnd() {
      if (timer != null) clearTimer(timer);
      timer = setTimer(() => { armed = false; timer = null; }, ms);
    },
    /** 合成 click を食うべきなら true を返してガードを解除する。 */
    consumeClick() {
      if (armed) { disarm(); return true; }
      return false;
    },
  };
}
