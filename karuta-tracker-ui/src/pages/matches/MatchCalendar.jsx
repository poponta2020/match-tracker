import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, ChevronDown } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { matchAPI } from '../../api';
import YearMonthPicker from '../../components/YearMonthPicker';
import {
  WEEKDAYS,
  isoDate,
  buildWeeks,
  groupMatchesByDate,
  countMatchesInMonth,
  formatMonthDay,
  opponentKyuChar,
  venueOfDay,
} from '../../utils/matchCalendar';

/**
 * 戦績確認画面「カレンダー」タブの本体。
 *
 * 常に自分（currentPlayer）の試合を月カレンダーで俯瞰し、日を選ぶとその日の試合
 * （勝敗・相手・お手つき・個人メモ）を確認して試合詳細へ遷移する。データは戦績確認タブの
 * playerId とは独立に、常に自分の試合を自前 fetch する。抜け番（読み/一人取り）は含めない。
 *
 * @param {Date} [referenceDate] 「今日」の基準日。テストで固定するためのシーム（既定=現在時刻）
 */
const MatchCalendar = ({ referenceDate = new Date() }) => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const selfId = currentPlayer?.id;

  const [matches, setMatches] = useState([]);

  const todayIso = isoDate(
    referenceDate.getFullYear(),
    referenceDate.getMonth(),
    referenceDate.getDate()
  );

  const [viewYear, setViewYear] = useState(referenceDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(referenceDate.getMonth()); // 0-based
  const [selectedDate, setSelectedDate] = useState(todayIso);
  const [showPicker, setShowPicker] = useState(false);

  // 自分の全試合を取得（戦績確認タブの playerId とは独立に常に自分）
  useEffect(() => {
    if (!selfId) return undefined;
    let cancelled = false;
    matchAPI
      .getByPlayerId(selfId)
      .then((res) => {
        if (!cancelled) setMatches(res.data || []);
      })
      .catch((e) => {
        if (!cancelled) {
          console.error('カレンダー用の試合記録の取得に失敗しました:', e);
          setMatches([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [selfId]);

  // 日付 -> その日の試合（試合番号昇順）
  const matchesByDate = useMemo(() => groupMatchesByDate(matches), [matches]);
  // 表示中の月の総試合数（月ラベル下のバッジ）
  const monthTotal = useMemo(
    () => countMatchesInMonth(matches, viewYear, viewMonth),
    [matches, viewYear, viewMonth]
  );
  // カレンダーグリッド（前月・翌月のはみ出しセルを含む）
  const weeks = useMemo(() => buildWeeks(viewYear, viewMonth), [viewYear, viewMonth]);

  const changeMonth = (delta) => {
    const nd = new Date(viewYear, viewMonth + delta, 1);
    setViewYear(nd.getFullYear());
    setViewMonth(nd.getMonth());
  };

  const selectedMatches = matchesByDate[selectedDate] || [];
  // 会場は日付に対し一意 → 見出しに1回だけ表示する
  const selectedVenue = venueOfDay(selectedMatches);

  const resultSymbol = (m) => (m.result === '勝ち' ? '〇' : m.result === '負け' ? '×' : '△');
  const resultColor = (m) =>
    m.result === '勝ち' ? 'text-green-600' : m.result === '負け' ? 'text-red-600' : 'text-gray-600';

  const weekdayColor = (dow) => (dow === 0 ? 'text-red-500' : dow === 6 ? 'text-blue-500' : 'text-[#374151]');

  return (
    <div className="pb-24">
      {/* 月送りナビ（月ラベル押下で年月ピッカー・下に月間試合数バッジ） */}
      <div className="relative px-4 py-3">
        <div className="flex items-center justify-between">
          <button onClick={() => changeMonth(-1)} className="p-2 text-[#4a6b5a]" aria-label="前の月">
            <ChevronLeft className="w-5 h-5" />
          </button>
          <button
            type="button"
            onClick={() => setShowPicker((v) => !v)}
            className="flex flex-col items-center px-3 py-1 rounded-lg hover:bg-[#eef2ef] transition-colors"
          >
            <span className="flex items-center gap-1 text-lg font-bold text-[#374151]">
              {viewYear}年{viewMonth + 1}月
              <ChevronDown className="w-4 h-4 text-[#6b7280]" aria-hidden="true" />
            </span>
            <span className="mt-0.5 text-xs font-semibold text-[#4a6b5a] bg-[#eef2ef] rounded-full px-2 py-0.5">
              {monthTotal}試合
            </span>
          </button>
          <button onClick={() => changeMonth(1)} className="p-2 text-[#4a6b5a]" aria-label="次の月">
            <ChevronRight className="w-5 h-5" />
          </button>
        </div>
        {showPicker && (
          <YearMonthPicker
            currentYear={viewYear}
            currentMonth={viewMonth + 1}
            onSelect={(y, m) => {
              setViewYear(y);
              setViewMonth(m - 1);
            }}
            onClose={() => setShowPicker(false)}
          />
        )}
      </div>

      {/* 曜日ヘッダ */}
      <div className="grid grid-cols-7 px-2">
        {WEEKDAYS.map((w, i) => (
          <div
            key={w}
            className={`text-center text-sm py-1 ${
              i === 0 ? 'text-red-500' : i === 6 ? 'text-blue-500' : 'text-[#6b7280]'
            }`}
          >
            {w}
          </div>
        ))}
      </div>

      {/* 日セル */}
      <div className="px-2">
        {weeks.map((week, wi) => (
          <div key={wi} className="grid grid-cols-7 gap-0.5">
            {week.map((cell, ci) => {
              if (!cell.inMonth) {
                return (
                  <div
                    key={ci}
                    className="h-14 flex items-start justify-center pt-1.5 text-sm text-gray-300 select-none"
                  >
                    {cell.day}
                  </div>
                );
              }
              const count = (matchesByDate[cell.date] || []).length;
              const isToday = cell.date === todayIso;
              const isSelected = cell.date === selectedDate;
              let numCls = 'w-7 h-7 flex items-center justify-center rounded-full text-sm ';
              if (isToday) numCls += 'border border-[#4a6b5a] text-[#4a6b5a] font-bold';
              else if (isSelected) numCls += 'text-[#374151] font-bold';
              else numCls += weekdayColor(ci);
              return (
                <button
                  key={ci}
                  type="button"
                  aria-label={`${viewMonth + 1}月${cell.day}日`}
                  onClick={() => setSelectedDate(cell.date)}
                  className={`h-14 rounded-lg border-2 flex flex-col items-center pt-1.5 transition-colors ${
                    isSelected ? 'border-[#4a6b5a] bg-[#eef2ef]' : 'border-transparent hover:bg-[#f4f7f5]'
                  }`}
                >
                  <span className={numCls}>{cell.day}</span>
                  {count > 0 && (
                    <span className="mt-0.5 min-w-[1.15rem] h-[1.15rem] px-1 flex items-center justify-center rounded-full bg-[#4a6b5a] text-white text-[0.65rem] font-bold leading-none">
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </div>

      {/* 選択日の見出し帯（M/D 会場名。会場は日付に一意） */}
      <div className="mt-3 bg-[#4a6b5a] text-white px-4 py-2 font-bold">
        {formatMonthDay(selectedDate)}
        {selectedVenue ? '　' + selectedVenue : ''}
      </div>

      {/* 選択日の試合リスト */}
      {selectedMatches.length === 0 ? (
        <div className="bg-[#f9f6f2] py-16 text-center text-gray-500">記録がありません</div>
      ) : (
        <div className="bg-[#f9f6f2] divide-y divide-[#e5e0da]">
          {selectedMatches.map((m) => {
            const kyu = opponentKyuChar(m, selfId);
            return (
              <button
                key={m.id}
                type="button"
                onClick={() => navigate(`/matches/${m.id}`)}
                className="w-full text-left px-4 py-3 hover:bg-[#f2ede5] transition-colors"
              >
                <div className="flex items-baseline gap-1.5 flex-wrap text-sm">
                  <span className="text-[#374151]">{m.matchNumber}試合目：</span>
                  {m.isLesson ? (
                    <span className="font-bold text-gray-500">指導</span>
                  ) : (
                    <span className={`font-bold ${resultColor(m)}`}>
                      {resultSymbol(m)}{m.scoreDifference}
                    </span>
                  )}
                  <span className="font-medium text-[#374151]">
                    {m.opponentName}
                    {kyu ? `（${kyu}）` : ''}
                  </span>
                  {m.myOtetsukiCount != null && (
                    <span className="text-[#6b7280]">お手{m.myOtetsukiCount}</span>
                  )}
                </div>
                {m.myPersonalNotes && (
                  <div className="text-sm text-[#6b7280] mt-0.5 whitespace-pre-wrap break-words">
                    {m.myPersonalNotes}
                  </div>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default MatchCalendar;
