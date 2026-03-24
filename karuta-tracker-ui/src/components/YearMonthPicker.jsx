import { useState, useEffect, useRef } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

/**
 * 年月グリッドピッカー（ドロップダウン型）
 *
 * @param {number} currentYear - 現在選択中の年
 * @param {number} currentMonth - 現在選択中の月 (1-12)
 * @param {(year: number, month: number) => void} onSelect - 年月選択時のコールバック
 * @param {() => void} onClose - ピッカーを閉じるコールバック
 */
const YearMonthPicker = ({ currentYear, currentMonth, onSelect, onClose }) => {
  const pickerRef = useRef(null);
  const [viewYear, setViewYear] = useState(currentYear);

  useEffect(() => {
    const handleClick = (e) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [onClose]);

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
          return (
            <button
              key={month}
              onClick={() => { onSelect(viewYear, month); onClose(); }}
              className={`py-2 text-sm rounded-lg transition-colors
                ${isSelected
                  ? 'bg-[#4a6b5a] text-white font-bold'
                  : 'text-[#374151] hover:bg-[#eef2ef]'
                }`}
            >
              {month}月
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default YearMonthPicker;
