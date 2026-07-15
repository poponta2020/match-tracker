import { useRef, useState } from 'react';
import {
  DndContext,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  useDraggable,
  useDroppable,
} from '@dnd-kit/core';
import { kimariji, compareCardsByDecisionOrder } from '../../data/kimariji';
import { computeDrop, encodeCellId, POOL_ID, createTrailingClickGuard } from './torifudaDragLogic';
import './TorifudaRecord.css';

// 盤面の並び（design-spec 取り札記録・A案）:
// 敵陣は180°回転 → 行(上→下)=下段/中段/上段、列(左→右)=敵陣右/敵陣左。
// 自陣は自分視点そのまま → 行=上段/中段/下段、列=自陣左/自陣右。
const ENEMY_TIERS = ['BOTTOM', 'MIDDLE', 'TOP'];
const ENEMY_SIDES = ['RIGHT', 'LEFT'];
const OWN_TIERS = ['TOP', 'MIDDLE', 'BOTTOM'];
const OWN_SIDES = ['LEFT', 'RIGHT'];

// ドラッグ可能な札チップ。onClick（タップ操作）と useDraggable を併存させる。
function DraggableChip({ cardNo, className, onClick, children }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `card-${cardNo}`,
    data: { cardNo },
  });
  return (
    <span
      ref={setNodeRef}
      className={`${className}${isDragging ? ' dragging' : ''}`}
      onClick={onClick}
      {...listeners}
      {...attributes}
    >
      {children}
    </span>
  );
}

// ドロップ可能なマス半分（取った／取られた）。
// flexGrow は札数に比例（③ 少ない側の未使用横幅を多い側が吸収）。min-width は CSS で担保。
function DroppableHalf({ field, side, tier, takenBy, armed, chipCount, onClick, children }) {
  const { setNodeRef, isOver } = useDroppable({ id: encodeCellId(field, side, tier, takenBy) });
  return (
    <div
      ref={setNodeRef}
      className={`tr-half ${takenBy === 'SELF' ? 'take' : 'taken'}${armed ? ' armed' : ''}${isOver ? ' over' : ''}`}
      style={{ flexGrow: Math.max(chipCount, 1) }}
      onClick={onClick}
    >
      {children}
    </div>
  );
}

// ドロップ可能な不明プール（マス→不明の解除ドロップ先）。
function DroppablePool({ children }) {
  const { setNodeRef, isOver } = useDroppable({ id: POOL_ID });
  return (
    <div ref={setNodeRef} className={`tr-pool-wrap${isOver ? ' over' : ''}`}>
      {children}
    </div>
  );
}

/**
 * 取り札盤面＋不明プール。
 * @param cards number[] その試合の出札50枚（札番号昇順）
 * @param placements { [cardNo]: { takenBy, field, side, tier } } 配置済み（不明は含まない）
 * @param onChange (nextPlacements) => void
 * @param scoreDifference 枚数差（不明プールの母数=50-枚数差の算出用）
 * @param isLesson 指導試合なら母数は50にフォールバック
 * @param readOnly true のとき配置済みの札のみを操作不可で表示（不明プール・D&D・タップ・info を出さない）
 */
export default function TorifudaBoard({ cards, placements, onChange, scoreDifference, isLesson, readOnly = false }) {
  const [selected, setSelected] = useState(null); // 不明から選択中の札番号
  const [showInfo, setShowInfo] = useState(false);
  // 実ドラッグ直後の trailing click を1回だけ無視するガード（footgun 対策）
  const guardRef = useRef(null);
  if (guardRef.current == null) guardRef.current = createTrailingClickGuard();
  const guard = guardRef.current;

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 8 } }),
  );

  const placed = placements || {};
  const poolCards = cards.filter((c) => !placed[c]).sort(compareCardsByDecisionOrder);

  const sd = Number(scoreDifference);
  const placeable = (isLesson || scoreDifference == null || scoreDifference === '' || Number.isNaN(sd))
    ? 50
    : Math.max(0, 50 - sd);
  const placedCount = Object.keys(placed).length;
  const remaining = Math.max(0, placeable - placedCount);

  const place = (field, side, tier, takenBy) => {
    if (selected == null) return;
    onChange({ ...placed, [selected]: { takenBy, field, side, tier } });
    setSelected(null);
  };
  const unplace = (cardNo) => {
    const next = { ...placed };
    delete next[cardNo];
    onChange(next);
  };
  const toggleSelect = (cardNo) => setSelected((s) => (s === cardNo ? null : cardNo));

  const handleDragStart = () => guard.onDragStart();
  const handleDragEnd = ({ active, over }) => {
    const activeCardNo = active?.data?.current?.cardNo;
    const next = computeDrop({ activeCardNo, overId: over?.id ?? null, placements: placed });
    if (next) onChange(next);
    setSelected(null);
    guard.onDragEnd();
  };

  const cellCards = (field, side, tier, takenBy) =>
    cards.filter((c) => {
      const p = placed[c];
      return p && p.field === field && p.side === side && p.tier === tier && p.takenBy === takenBy;
    });

  const renderQuad = (field, side, tier) => (
    <div className="tr-quad" key={`${field}-${side}-${tier}`}>
      {['SELF', 'OPPONENT'].map((takenBy) => {
        const half = cellCards(field, side, tier, takenBy);
        if (readOnly) {
          // 読み取り専用: ドラッグ/タップ不可のプレーンチップのみ（見た目は踏襲）
          return (
            <div
              key={takenBy}
              className={`tr-half ${takenBy === 'SELF' ? 'take' : 'taken'}`}
              style={{ flexGrow: Math.max(half.length, 1) }}
            >
              {half.map((c) => (
                <span key={c} className="tr-chip">{kimariji(c)}</span>
              ))}
            </div>
          );
        }
        return (
          <DroppableHalf
            key={takenBy}
            field={field}
            side={side}
            tier={tier}
            takenBy={takenBy}
            armed={selected != null}
            chipCount={half.length}
            onClick={() => {
              if (guard.consumeClick()) return; // ドラッグ直後の合成 click を無視
              place(field, side, tier, takenBy);
            }}
          >
            {half.map((c) => (
              <DraggableChip
                key={c}
                cardNo={c}
                className="tr-chip"
                onClick={(e) => {
                  e.stopPropagation();
                  if (guard.consumeClick()) return; // ドラッグ移動/解除の trailing click を打ち消さない
                  // 札を選択中（arm状態）なら、既存チップの上をタップしても
                  // そのマスへ配置する（マスが埋まっていても隙間を狙う必要をなくす）。
                  // 非選択時は従来どおりタップした札を不明に戻す。
                  if (selected != null) place(field, side, tier, takenBy);
                  else unplace(c);
                }}
              >
                {kimariji(c)}
              </DraggableChip>
            ))}
          </DroppableHalf>
        );
      })}
    </div>
  );

  const renderField = (field, tiers, sides, label, extraClass) => (
    <div className={`tr-field ${extraClass}`}>
      <div className="tr-fld-hd"><span className="nm">{label}</span></div>
      <div className="tr-board">
        {tiers.flatMap((tier) => sides.map((side) => renderQuad(field, side, tier)))}
      </div>
    </div>
  );

  if (readOnly) {
    // 試合詳細（本人閲覧）での読み取り専用表示。不明プール・info・操作なし。
    return (
      <div className="tr">
        <div className="tr-sec">
          <div className="tr-sec-hd">
            <div className="tr-title"><h3>取り札</h3></div>
          </div>
          {renderField('ENEMY', ENEMY_TIERS, ENEMY_SIDES, '敵 陣', 'enemy')}
          {renderField('OWN', OWN_TIERS, OWN_SIDES, '自 陣', 'own')}
        </div>
      </div>
    );
  }

  return (
    <div className="tr">
      <div className="tr-sec">
        <div className="tr-sec-hd">
          <div className="tr-title">
            <h3>取り札</h3>
            <button
              type="button"
              className="tr-info-btn"
              onClick={() => setShowInfo((v) => !v)}
              aria-label="取り札の説明"
            >i</button>
          </div>
          <span className="opt">任意</span>
        </div>

        {showInfo && (
          <div className="tr-info-pop">
            盤面は<b>自分視点</b>。上が<b>敵陣（奥）</b>、下が<b>自陣（手前）</b>。<br />
            敵陣は<b>上段が手前・下段が奥</b>、左右も相手基準（<b>敵陣右＝画面の左</b>）。<br />
            各マスの左＝<span className="g">取った（緑）</span>／右＝<span className="r">取られた（赤）</span>。<br />
            「不明」の札を<b>タップして選ぶ→マスをタップ</b>、または<b>ドラッグして直接マスへ</b>置けます（マス内に札があってもその上でOK）。配置済みの札は<b>別マスへドラッグで移動</b>／<b>不明へドラッグで解除</b>、タップでも不明に戻せます。
          </div>
        )}

        <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
          {renderField('ENEMY', ENEMY_TIERS, ENEMY_SIDES, '敵 陣', 'enemy')}

          <div className="tr-pool">
            <div className="tr-pool-hd">
              <span className="t">不明</span>
              <span className="c">残り {remaining} / {placeable}枚</span>
              <span className="hint">札→マスで配置</span>
            </div>
            <DroppablePool>
              {poolCards.length === 0 ? (
                <span className="tr-pool-empty">配置できる札はすべて置きました</span>
              ) : (
                poolCards.map((c) => (
                  <DraggableChip
                    key={c}
                    cardNo={c}
                    className={`tr-chip${selected === c ? ' sel' : ''}`}
                    onClick={() => {
                      if (guard.consumeClick()) return;
                      toggleSelect(c);
                    }}
                  >
                    {kimariji(c)}
                  </DraggableChip>
                ))
              )}
            </DroppablePool>
          </div>

          {renderField('OWN', OWN_TIERS, OWN_SIDES, '自 陣', 'own')}
        </DndContext>
      </div>
    </div>
  );
}
