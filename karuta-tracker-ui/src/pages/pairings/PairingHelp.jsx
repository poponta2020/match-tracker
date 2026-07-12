import { useState, useEffect, useRef } from 'react';
import { Info, X, Lock } from 'lucide-react';

const HELP_SEEN_KEY = 'pairingHelpSeen';

/**
 * 組み合わせ作成画面（PairingGenerator）の使い方ヘルプ（ⓘ ドロップダウン）。
 *
 * カレンダーの「記号の見方」（PracticeList）と同方式:
 *  - 初回訪問時のみ端末単位で自動表示し、localStorage('pairingHelpSeen') で既読管理する。
 *  - localStorage が使えない環境では「毎回自動表示」にフォールバックする。
 *  - パネルは外側タップおよび右上の ✕ で閉じる。
 *
 * @param {boolean} ready 親のローディングが完了したか（true になってから既読フラグを保存する）。
 *   ローディング中に保存すると、パネルを視認する前に離脱しても既読扱いになってしまうため。
 * @param {'default'|'header'} variant トリガーの配置・配色。'header' は緑ヘッダ（PageHeader の rightActions）に
 *   置く用で、白系トリガー＋外側の右寄せラッパを省く。ドロップダウンパネルの中身は不変。
 */
const PairingHelp = ({ ready = true, variant = 'default' }) => {
  const isHeader = variant === 'header';
  const [showHelp, setShowHelp] = useState(() => {
    try {
      return !localStorage.getItem(HELP_SEEN_KEY);
    } catch {
      // localStorage が使えない環境（プライベートモード等）では既読管理ができないため、
      // 要件どおり「最悪毎回開く」フォールバック（true）にして初回ユーザーに必ずヘルプを見せる
      return true;
    }
  });
  // ドロップダウンの外側タップ判定用
  const helpRef = useRef(null);

  // パネルが実際に表示された後（ローディング完了後）に既読フラグを保存する（端末単位）
  useEffect(() => {
    if (!ready || !showHelp) return;
    try {
      localStorage.setItem(HELP_SEEN_KEY, '1');
    } catch {
      // プライベートモード等で localStorage が使えない場合は既読管理を諦める（毎回自動表示されるが機能は阻害しない）
    }
  }, [ready, showHelp]);

  // パネルを開いている間だけ、画面外タップで閉じる（カレンダー凡例と同方式）
  useEffect(() => {
    if (!showHelp) return;
    const handleClickOutside = (e) => {
      if (helpRef.current && !helpRef.current.contains(e.target)) {
        setShowHelp(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showHelp]);

  return (
    <div className={isHeader ? 'flex-shrink-0' : 'flex justify-end'}>
      <div ref={helpRef} className="relative">
        <button
          type="button"
          onClick={() => setShowHelp((prev) => !prev)}
          aria-label="使い方を開く"
          aria-expanded={showHelp}
          className={`inline-flex items-center gap-1 text-xs px-2 py-1 rounded-md transition-colors ${
            isHeader
              ? 'text-white/90 hover:bg-[#3d5a4c]'
              : 'text-[#4a6b5a] hover:bg-[#eef2ef]'
          }`}
        >
          <Info className="w-4 h-4" />
          使い方
        </button>
        {showHelp && (
          <div className="absolute top-full right-0 mt-2 w-[280px] bg-white border border-gray-200 rounded-lg shadow-lg z-[60] p-3 text-left">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-semibold text-[#374151]">この画面の使い方</span>
              <button
                type="button"
                onClick={() => setShowHelp(false)}
                aria-label="使い方を閉じる"
                className="p-0.5 text-gray-400 hover:text-gray-600"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* 1. 選手の入れ替え方 */}
            <div className="mb-3">
              <div className="text-xs font-semibold text-[#4a6b5a] border-l-2 border-[#4a6b5a] pl-1.5 mb-1">
                選手の入れ替え方
              </div>
              <p className="text-xs text-[#374151] leading-relaxed">
                選手チップをドラッグ＆ドロップ、またはタップで選んでから移動先をタップすると、組み合わせ・待機中の選手を入れ替えできます。
              </p>
            </div>

            {/* 2. ロックの意味と使い方 */}
            <div className="mb-3">
              <div className="text-xs font-semibold text-[#4a6b5a] border-l-2 border-[#4a6b5a] pl-1.5 mb-1">
                ロックの意味と使い方
              </div>
              <p className="text-xs text-[#374151] leading-relaxed">
                <Lock className="w-3 h-3 inline-block align-text-bottom mr-0.5" />
                （鍵アイコン）を押すとその組をロックします。ロックした組は編集画面上で固定され、自動組み合わせ・回戦削除で変更・削除されません。解除は「解除」を押します。ロック・解除は「確定して保存」を押すまで保存されません（保存するとロック状態も保持されます）。
              </p>
            </div>

            {/* 3. 保存の流れ */}
            <div className="mb-3">
              <div className="text-xs font-semibold text-[#4a6b5a] border-l-2 border-[#4a6b5a] pl-1.5 mb-1">
                保存の流れ
              </div>
              <p className="text-xs text-[#374151] leading-relaxed">
                編集内容は「確定して保存」を押すまで保存されません。ある試合の編集中は、保存するまで他の試合は編集できません。
              </p>
            </div>

            {/* 4. 日付列の見方 */}
            <div>
              <div className="text-xs font-semibold text-[#4a6b5a] border-l-2 border-[#4a6b5a] pl-1.5 mb-1">
                日付列の見方
              </div>
              <p className="text-xs text-[#374151] leading-relaxed">
                各組の右の表示はその2人の直近対戦日です。「初」＝対戦記録なし、「⚠今日」＝今日すでに対戦済み、「MM/DD」＝直近対戦日。
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PairingHelp;
