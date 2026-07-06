import { useState } from 'react';
import { kimariji } from '../../data/kimariji';
import './TorifudaRecord.css';

// 盤面の並び（design-spec 取り札記録・A案）:
// 敵陣は180°回転 → 行(上→下)=下段/中段/上段、列(左→右)=敵陣右/敵陣左。
// 自陣は自分視点そのまま → 行=上段/中段/下段、列=自陣左/自陣右。
const ENEMY_TIERS = ['BOTTOM', 'MIDDLE', 'TOP'];
const ENEMY_SIDES = ['RIGHT', 'LEFT'];
const OWN_TIERS = ['TOP', 'MIDDLE', 'BOTTOM'];
const OWN_SIDES = ['LEFT', 'RIGHT'];

/**
 * 取り札盤面＋不明プール。
 * @param cards number[] その試合の出札50枚（札番号昇順）
 * @param placements { [cardNo]: { takenBy, field, side, tier } } 配置済み（不明は含まない）
 * @param onChange (nextPlacements) => void
 * @param scoreDifference 枚数差（不明プールの母数=50-枚数差の算出用）
 * @param isLesson 指導試合なら母数は50にフォールバック
 */
export default function TorifudaBoard({ cards, placements, onChange, scoreDifference, isLesson }) {
  const [selected, setSelected] = useState(null); // 不明から選択中の札番号
  const [showInfo, setShowInfo] = useState(false);

  const placed = placements || {};
  const poolCards = cards.filter((c) => !placed[c]);

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

  const cellCards = (field, side, tier, takenBy) =>
    cards.filter((c) => {
      const p = placed[c];
      return p && p.field === field && p.side === side && p.tier === tier && p.takenBy === takenBy;
    });

  const renderQuad = (field, side, tier) => (
    <div className="tr-quad" key={`${field}-${side}-${tier}`}>
      {['SELF', 'OPPONENT'].map((takenBy) => (
        <div
          key={takenBy}
          className={`tr-half ${takenBy === 'SELF' ? 'take' : 'taken'}${selected != null ? ' armed' : ''}`}
          onClick={() => place(field, side, tier, takenBy)}
        >
          {cellCards(field, side, tier, takenBy).map((c) => (
            <span
              key={c}
              className="tr-chip"
              onClick={(e) => { e.stopPropagation(); unplace(c); }}
            >
              {kimariji(c)}
            </span>
          ))}
        </div>
      ))}
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
            「不明」の札をタップ → 置きたいマスをタップで配置。配置済みの札をタップで不明に戻す。
          </div>
        )}

        {renderField('ENEMY', ENEMY_TIERS, ENEMY_SIDES, '敵 陣', 'enemy')}

        <div className="tr-pool">
          <div className="tr-pool-hd">
            <span className="t">不明</span>
            <span className="c">残り {remaining} / {placeable}枚</span>
            <span className="hint">札→マスで配置</span>
          </div>
          <div className="tr-pool-wrap">
            {poolCards.length === 0 ? (
              <span className="tr-pool-empty">配置できる札はすべて置きました</span>
            ) : (
              poolCards.map((c) => (
                <span
                  key={c}
                  className={`tr-chip${selected === c ? ' sel' : ''}`}
                  onClick={() => toggleSelect(c)}
                >
                  {kimariji(c)}
                </span>
              ))
            )}
          </div>
        </div>

        {renderField('OWN', OWN_TIERS, OWN_SIDES, '自 陣', 'own')}
      </div>
    </div>
  );
}
