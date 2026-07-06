import { ALL_KIMARIJI } from '../../data/kimariji';
import './TorifudaRecord.css';

const TYPES = [
  { key: 'HIKKAKE', label: 'ひっかけ' },
  { key: 'ANKI_MISS', label: '暗記間違え' },
  { key: 'MISHEARING', label: '聞き間違い' },
  { key: 'OTHER', label: 'その他' },
];

const HIKKAKE_TARGETS = [
  { key: 'OWN_LEFT_TOP', label: '自陣 左上' },
  { key: 'OWN_RIGHT_TOP', label: '自陣 右上' },
  { key: 'ENEMY_LEFT_TOP', label: '敵陣 左上' },
  { key: 'ENEMY_RIGHT_TOP', label: '敵陣 右上' },
];

const ANKI_DIRS = [
  { key: 'SENT_TO_ENEMY_TOUCHED_OWN', label: '敵陣に送った札を、自陣（元の位置）で触った' },
  { key: 'RECEIVED_FROM_ENEMY_TOUCHED_ENEMY', label: '自陣に送られた札を、敵陣（送り元）で触った' },
];

/**
 * お手付き詳細フォーム。お手付き回数(count)分の枠を出し、種類ごとに項目を出し分ける。
 * @param count お手付き回数（枠数）
 * @param details 詳細配列（各要素 { type, hikkakeTarget, ankiDirection, mishearingReadCardNo, mishearingTouchedCardNo, otherText }）
 * @param onChange (nextDetails) => void
 */
export default function OtetsukiDetails({ count, details, onChange }) {
  if (!count || count < 1) return null;

  const list = Array.from({ length: count }, (_, i) => details[i] || { type: null });

  const update = (i, patch) => {
    onChange(list.map((d, idx) => (idx === i ? { ...d, ...patch } : d)));
  };

  const setType = (i, type) => {
    // 種類変更時は種類別項目をリセット
    update(i, {
      type,
      hikkakeTarget: null,
      ankiDirection: null,
      mishearingReadCardNo: null,
      mishearingTouchedCardNo: null,
      otherText: null,
    });
  };

  return (
    <div className="tr">
      <div className="tr-sec">
        <div className="tr-sec-hd">
          <div className="tr-title"><h3>お手付きの内容</h3></div>
          <span className="opt">{count}回ぶん</span>
        </div>

        {list.map((d, i) => (
          <div className="tr-ote" key={i}>
            <div className="tr-ote-hd"><span className="no">{i + 1}</span><span className="t">種類を選択</span></div>
            <div className="tr-types">
              {TYPES.map((t) => (
                <button
                  type="button"
                  key={t.key}
                  className={`tr-type${d.type === t.key ? ' on' : ''}`}
                  onClick={() => setType(i, t.key)}
                >{t.label}</button>
              ))}
            </div>

            {d.type === 'HIKKAKE' && (
              <>
                <div className="tr-dl">払おうとした上段は？</div>
                <div className="tr-pos4">
                  {HIKKAKE_TARGETS.map((h) => (
                    <button
                      type="button"
                      key={h.key}
                      className={`tr-pos${d.hikkakeTarget === h.key ? ' on' : ''}`}
                      onClick={() => update(i, { hikkakeTarget: h.key })}
                    >{h.label}</button>
                  ))}
                </div>
              </>
            )}

            {d.type === 'ANKI_MISS' && (
              <>
                <div className="tr-dl">どちらの間違い？</div>
                <div className="tr-dir2">
                  {ANKI_DIRS.map((a) => (
                    <button
                      type="button"
                      key={a.key}
                      className={`tr-dir${d.ankiDirection === a.key ? ' on' : ''}`}
                      onClick={() => update(i, { ankiDirection: a.key })}
                    >{a.label}</button>
                  ))}
                </div>
              </>
            )}

            {d.type === 'MISHEARING' && (
              <div className="tr-cardpick">
                <div className="tr-cp">
                  <span className="k">読まれた札</span>
                  <select
                    value={d.mishearingReadCardNo ?? ''}
                    onChange={(e) => update(i, { mishearingReadCardNo: e.target.value === '' ? null : Number(e.target.value) })}
                  >
                    <option value="">選択</option>
                    {ALL_KIMARIJI.map((k) => <option key={k.no} value={k.no}>{k.kimariji}</option>)}
                  </select>
                </div>
                <div className="tr-cp">
                  <span className="k">触った札</span>
                  <select
                    value={d.mishearingTouchedCardNo ?? ''}
                    onChange={(e) => update(i, { mishearingTouchedCardNo: e.target.value === '' ? null : Number(e.target.value) })}
                  >
                    <option value="">選択</option>
                    {ALL_KIMARIJI.map((k) => <option key={k.no} value={k.no}>{k.kimariji}</option>)}
                  </select>
                </div>
              </div>
            )}

            {d.type === 'OTHER' && (
              <input
                className="tr-otetext"
                type="text"
                value={d.otherText ?? ''}
                placeholder="内容を入力…"
                onChange={(e) => update(i, { otherText: e.target.value })}
              />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
