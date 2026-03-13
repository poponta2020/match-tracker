import { X, Filter, Search } from 'lucide-react';

const FilterBottomSheet = ({
  isOpen,
  onClose,
  selectedYear,
  setSelectedYear,
  selectedMonth,
  setSelectedMonth,
  availableYears,
  availableMonths,
  filterKyuRank,
  setFilterKyuRank,
  filterGender,
  setFilterGender,
  filterDominantHand,
  setFilterDominantHand,
  searchTerm,
  setSearchTerm,
  filterResult,
  setFilterResult
}) => {
  if (!isOpen) return null;

  return (
    <>
      {/* オーバーレイ */}
      <div
        className="fixed inset-0 bg-black bg-opacity-50 z-40"
        onClick={onClose}
      />

      {/* ボトムシート */}
      <div className="fixed bottom-0 left-0 right-0 bg-white rounded-t-2xl shadow-xl z-50 animate-slide-up max-h-[80vh] overflow-y-auto">
        {/* ヘッダー */}
        <div className="sticky top-0 bg-white border-b border-gray-200 px-4 py-4 flex items-center justify-between rounded-t-2xl">
          <h2 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
            <Filter className="w-5 h-5" />
            絞り込み
          </h2>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-100 rounded-full transition-colors"
          >
            <X className="w-5 h-5 text-gray-600" />
          </button>
        </div>

        {/* フィルタ内容 */}
        <div className="p-4 space-y-4">
          {/* 年月選択 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">期間</label>
            <div className="grid grid-cols-2 gap-3">
              {/* 年選択 */}
              <select
                value={selectedYear}
                onChange={(e) => setSelectedYear(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
              >
                <option value="">すべての年</option>
                {availableYears.map((year) => (
                  <option key={year} value={year}>
                    {year}年
                  </option>
                ))}
              </select>

              {/* 月選択 */}
              <select
                value={selectedMonth}
                onChange={(e) => setSelectedMonth(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
              >
                <option value="">すべての月</option>
                {availableMonths.map((month) => (
                  <option key={month} value={month}>
                    {month}月
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* 検索 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">対戦相手</label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="対戦相手で検索..."
                className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>
          </div>

          {/* 結果フィルタ */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">試合結果</label>
            <select
              value={filterResult}
              onChange={(e) => setFilterResult(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
            >
              <option value="全て">全ての結果</option>
              <option value="勝ち">勝ちのみ</option>
              <option value="負け">負けのみ</option>
              <option value="引き分け">引き分けのみ</option>
            </select>
          </div>

          {/* 対戦相手フィルタ */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">対戦相手の条件</label>
            <div className="grid grid-cols-3 gap-3">
              {/* 級フィルタ */}
              <select
                value={filterKyuRank}
                onChange={(e) => setFilterKyuRank(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
              >
                <option value="">全ての級</option>
                <option value="A級">A級</option>
                <option value="B級">B級</option>
                <option value="C級">C級</option>
                <option value="D級">D級</option>
                <option value="E級">E級</option>
              </select>

              {/* 性別フィルタ */}
              <select
                value={filterGender}
                onChange={(e) => setFilterGender(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
              >
                <option value="">性別</option>
                <option value="男性">男性</option>
                <option value="女性">女性</option>
                <option value="その他">その他</option>
              </select>

              {/* 利き手フィルタ */}
              <select
                value={filterDominantHand}
                onChange={(e) => setFilterDominantHand(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
              >
                <option value="">利き手</option>
                <option value="右">右</option>
                <option value="左">左</option>
                <option value="両">両</option>
              </select>
            </div>
          </div>

          {/* 適用ボタン */}
          <button
            onClick={onClose}
            className="w-full bg-primary-600 text-white py-3 rounded-lg hover:bg-primary-700 transition-colors font-medium"
          >
            適用
          </button>
        </div>
      </div>

      <style>{`
        @keyframes slide-up {
          from {
            transform: translateY(100%);
          }
          to {
            transform: translateY(0);
          }
        }
        .animate-slide-up {
          animation: slide-up 0.3s ease-out;
        }
      `}</style>
    </>
  );
};

export default FilterBottomSheet;
