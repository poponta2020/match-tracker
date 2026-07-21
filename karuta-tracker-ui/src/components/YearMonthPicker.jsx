import { useState, useEffect, useRef } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

/**
 * 年月グリッドピッカー（ドロップダウン型）
 *
 * @param {(year:number, month:number)=>number} [countForMonth] 指定時、各月セルに件数を表示する
 *   （month は 1-based）。未指定なら件数を出さない（従来どおり）。
 * @param {import('react').RefObject} [triggerRef] ピッカーを開閉するトリガー要素の ref。指定時は
 *   外側クリック検出から除外し、開いている時のトリガー再タップでトグル閉じできるようにする
 *   （除外しないと mousedown で閉じ→click で再オープンし閉じられない）。
 */
const YearMonthPicker = ({ currentYear, currentMonth, onSelect, onClose, countForMonth, triggerRef }) => {
  const showCounts = typeof countForMonth === 'function';
  const pickerRef = useRef(null);
  const [viewYear, setViewYear] = useState(currentYear);

  useEffect(() => {
    const handleClick = (e) => {
      const inPicker = pickerRef.current && pickerRef.current.contains(e.target);
      const inTrigger = triggerRef?.current && triggerRef.current.contains(e.target);
      if (!inPicker && !inTrigger) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [onClose, triggerRef]);

  return (
    <div
      ref={pickerRef}
      className="absolute top-full mt-2 left-1/2 -translate-x-1/2 bg-white border border-gray-200 rounded-lg shadow-lg z-[60] w-[240px] p-3"
    >
      <div className="flex items-center justify-between mb-2">
        <button onClick={() => setViewYear(viewYear - 1)} className="p-1 hover:bg-gray-100 rounded">
          <ChevronLeft className="w-4 h-4" />
        </button>
        <span className="font-semibold text-sm text-[#374151]">{viewYear}年</span>
        <button onClick={() => setViewYear(viewYear + 1)} className="p-1 hover:bg-gray-100 rounded">
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>

      <div className="grid grid-cols-3 gap-1">
        {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => {
          const isSelected = viewYear === currentYear && month === currentMonth;
          const count = showCounts ? countForMonth(viewYear, month) : 0;
          return (
            <button
              key={month}
              onClick={() => { onSelect(viewYear, month); onClose(); }}
              className={`text-sm rounded-lg transition-colors
                ${showCounts ? 'flex flex-col items-center justify-center min-h-[2.75rem] py-1' : 'py-2'}
                ${isSelected
                  ? 'bg-[#4a6b5a] text-white font-bold'
                  : 'text-[#374151] hover:bg-[#eef2ef]'
                }`}
            >
              <span>{month}月</span>
              {showCounts && count > 0 && (
                <span
                  className={`mt-0.5 text-[0.65rem] font-semibold leading-none
                    ${isSelected ? 'text-white/85' : 'text-[#4a6b5a]'}`}
                >
                  {count}試合
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default YearMonthPicker;
