import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI } from '../../api';
import FilterBottomSheet from '../../components/FilterBottomSheet';
import {
  Trophy,
  Plus,
  Search,
  Calendar,
  Filter,
  TrendingUp,
  TrendingDown,
} from 'lucide-react';

const MatchList = () => {
  const { currentPlayer } = useAuth();
  const [matches, setMatches] = useState([]);
  const [filteredMatches, setFilteredMatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterResult, setFilterResult] = useState('全て');

  // 新しいフィルタ状態
  const [filterKyuRank, setFilterKyuRank] = useState('');
  const [filterGender, setFilterGender] = useState('');
  const [filterDominantHand, setFilterDominantHand] = useState('');

  // 級別統計データ
  const [rankStatistics, setRankStatistics] = useState(null);

  // 期間フィルタ関連の状態
  const today = new Date();
  const [selectedYear, setSelectedYear] = useState(today.getFullYear()); // デフォルトは今年
  const [selectedMonth, setSelectedMonth] = useState(today.getMonth() + 1); // デフォルトは今月（1-12）
  const [availableYears, setAvailableYears] = useState([]);
  const [availableMonths, setAvailableMonths] = useState([]);

  // ボトムシート表示状態
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  useEffect(() => {
    const fetchMatches = async () => {
      try {
        // 試合一覧取得用のパラメータ（級フィルタ含む）
        const matchParams = {};
        if (filterKyuRank) matchParams.kyuRank = filterKyuRank;
        if (filterGender) matchParams.gender = filterGender;
        if (filterDominantHand) matchParams.dominantHand = filterDominantHand;

        // 統計取得用のパラメータ（級フィルタ除外、期間フィルタ追加）
        const statsParams = {};
        if (filterGender) statsParams.gender = filterGender;
        if (filterDominantHand) statsParams.dominantHand = filterDominantHand;

        // 期間フィルタを追加
        if (selectedYear && selectedMonth) {
          // 年と月が両方選択されている場合
          const year = Number(selectedYear);
          const month = String(selectedMonth).padStart(2, '0');
          const lastDay = new Date(year, Number(selectedMonth), 0).getDate();
          statsParams.startDate = `${year}-${month}-01`;
          statsParams.endDate = `${year}-${month}-${lastDay}`;
        } else if (selectedYear && !selectedMonth) {
          // 年のみ選択されている場合
          statsParams.startDate = `${selectedYear}-01-01`;
          statsParams.endDate = `${selectedYear}-12-31`;
        }
        // 両方未選択の場合は期間フィルタなし（総計）

        // 並列で取得
        const [matchesResponse, statsResponse] = await Promise.all([
          matchAPI.getByPlayerId(currentPlayer.id, matchParams),
          matchAPI.getStatisticsByRank(currentPlayer.id, statsParams),
        ]);

        const sortedMatches = matchesResponse.data.sort(
          (a, b) => new Date(b.matchDate) - new Date(a.matchDate)
        );
        setMatches(sortedMatches);
        setFilteredMatches(sortedMatches);
        setRankStatistics(statsResponse.data);

        // 利用可能な年と月を抽出（文字列パースでタイムゾーン問題を回避）
        const years = [...new Set(sortedMatches.map(m => {
          const [year] = m.matchDate.split('-').map(Number);
          return year;
        }))].sort((a, b) => b - a);
        setAvailableYears(years);

        // 選択中の年の月を抽出
        if (selectedYear) {
          const months = [...new Set(
            sortedMatches
              .filter(m => {
                const [year] = m.matchDate.split('-').map(Number);
                return year === selectedYear;
              })
              .map(m => {
                const [, month] = m.matchDate.split('-').map(Number);
                return month;
              })
          )].sort((a, b) => b - a);
          setAvailableMonths(months);

          // 選択中の月が利用可能な月に含まれていない場合のみ、最新の月を選択
          // ただし、初期表示時は今月を優先
          if (months.length > 0 && selectedMonth && !months.includes(Number(selectedMonth))) {
            setSelectedMonth(months[0]);
          }
        }
      } catch (error) {
        console.error('試合記録の取得に失敗しました:', error);
      } finally {
        setLoading(false);
      }
    };

    if (currentPlayer?.id) {
      fetchMatches();
    }
  }, [currentPlayer, selectedYear, selectedMonth, filterKyuRank, filterGender, filterDominantHand]);

  useEffect(() => {
    let filtered = matches;

    // 期間でフィルタ
    if (selectedYear && selectedMonth) {
      // 年と月が両方選択されている場合
      filtered = filtered.filter((match) => {
        const [year, month] = match.matchDate.split('-').map(Number);
        return year === Number(selectedYear) && month === Number(selectedMonth);
      });
    } else if (selectedYear && !selectedMonth) {
      // 年のみ選択されている場合
      filtered = filtered.filter((match) => {
        const [year] = match.matchDate.split('-').map(Number);
        return year === Number(selectedYear);
      });
    }
    // 両方未選択の場合はフィルタなし（全データを表示）

    // 結果でフィルタ
    if (filterResult !== '全て') {
      filtered = filtered.filter((match) => match.result === filterResult);
    }

    // 検索語でフィルタ
    if (searchTerm) {
      filtered = filtered.filter((match) =>
        match.opponentName.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    setFilteredMatches(filtered);
  }, [searchTerm, filterResult, matches, selectedYear, selectedMonth]);

  const getResultBadge = (result) => {
    const styles = {
      勝ち: 'bg-green-100 text-green-700',
      負け: 'bg-red-100 text-red-700',
      引き分け: 'bg-gray-100 text-gray-700',
    };
    return styles[result] || styles['引き分け'];
  };

  // 日付をMM/DD形式でフォーマット
  const formatDate = (dateString) => {
    const date = new Date(dateString);
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${month}/${day}`;
  };

  // 勝敗と枚数差を結合して表示
  const getResultDisplay = (result, scoreDifference) => {
    const symbol = result === '勝ち' ? '〇' : result === '負け' ? '×' : '△';
    return `${symbol}${scoreDifference}`;
  };

  // 勝敗に応じた色スタイル
  const getResultColor = (result) => {
    if (result === '勝ち') return 'text-green-600 font-bold';
    if (result === '負け') return 'text-red-600 font-bold';
    return 'text-gray-600 font-bold';
  };


  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-20">
      {/* ナビゲーションバー */}
      <div className="bg-white shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto text-center">
          <h1 className="text-lg font-semibold text-gray-900">
            {selectedYear && selectedMonth
              ? `${selectedYear}年 ${selectedMonth}月`
              : selectedYear
              ? `${selectedYear}年`
              : '試合結果'}
          </h1>
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-16 space-y-6">
      {/* 級別統計テーブル */}
      {rankStatistics && (
        <div className="bg-white rounded-lg shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">

                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    試合数
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    勝ち
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    負け
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    勝率
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {/* 総計行 */}
                <tr className="bg-blue-50 font-semibold">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    総計
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-900">
                    {rankStatistics.total.total}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-green-600">
                    {rankStatistics.total.wins}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-red-600">
                    {rankStatistics.total.losses}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-blue-600">
                    {rankStatistics.total.winRate}%
                  </td>
                </tr>

                {/* 級別行 */}
                {['A級', 'B級', 'C級', 'D級', 'E級'].map((rank) => {
                  const stats = rankStatistics.byRank[rank];
                  return (
                    <tr key={rank} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        対{rank}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                        {stats.total}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-green-600">
                        {stats.wins}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-red-600">
                        {stats.losses}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-blue-600">
                        {stats.winRate}%
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 試合一覧 */}
      {filteredMatches.length === 0 ? (
        <div className="bg-white rounded-lg shadow-sm p-12 text-center">
          <Trophy className="w-16 h-16 text-gray-300 mx-auto mb-4" />
          <p className="text-gray-600 text-lg mb-2">試合記録がありません</p>
          <p className="text-gray-500 mb-6">
            {searchTerm || filterResult !== '全て'
              ? '検索条件を変更してください'
              : '最初の試合記録を登録しましょう'}
          </p>
          {!searchTerm && filterResult === '全て' && (
            <Link
              to="/matches/new"
              className="inline-flex items-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors"
            >
              <Plus className="w-5 h-5" />
              試合記録を登録
            </Link>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    日付
                  </th>
                  <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    対戦相手
                  </th>
                  <th className="px-3 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
                    勝敗
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {filteredMatches.map((match) => (
                  <tr
                    key={match.id}
                    className="hover:bg-gray-50 cursor-pointer transition-colors"
                    onClick={() =>
                      (window.location.href = `/matches/${match.id}`)
                    }
                  >
                    <td className="px-3 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-1 text-sm text-gray-900">
                        <Calendar className="w-4 h-4 text-gray-400" />
                        {formatDate(match.matchDate)}
                      </div>
                    </td>
                    <td className="px-3 py-4">
                      <div className="text-sm font-medium text-gray-900">
                        {match.opponentName}
                      </div>
                    </td>
                    <td className="px-3 py-4 whitespace-nowrap text-center">
                      <span className={`text-base ${getResultColor(match.result)}`}>
                        {getResultDisplay(match.result, match.scoreDifference)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* フローティングアクションボタン (FAB) */}
      <button
        onClick={() => setIsFilterOpen(true)}
        className="fixed bottom-20 right-4 z-20 bg-primary-600 text-white p-4 rounded-full shadow-lg hover:bg-primary-700 transition-all hover:shadow-xl"
      >
        <Filter className="w-6 h-6" />
      </button>

      {/* ボトムシート */}
      <FilterBottomSheet
        isOpen={isFilterOpen}
        onClose={() => setIsFilterOpen(false)}
        selectedYear={selectedYear}
        setSelectedYear={setSelectedYear}
        selectedMonth={selectedMonth}
        setSelectedMonth={setSelectedMonth}
        availableYears={availableYears}
        availableMonths={availableMonths}
        filterKyuRank={filterKyuRank}
        setFilterKyuRank={setFilterKyuRank}
        filterGender={filterGender}
        setFilterGender={setFilterGender}
        filterDominantHand={filterDominantHand}
        setFilterDominantHand={setFilterDominantHand}
        searchTerm={searchTerm}
        setSearchTerm={setSearchTerm}
        filterResult={filterResult}
        setFilterResult={setFilterResult}
      />
      </div>
    </div>
  );
};

export default MatchList;
