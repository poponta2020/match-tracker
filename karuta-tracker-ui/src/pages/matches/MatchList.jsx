import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI } from '../../api';
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

  // 期間フィルタ関連の状態
  const [viewMode, setViewMode] = useState('月ごと'); // '総計' | '年ごと' | '月ごと'
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1);
  const [availableYears, setAvailableYears] = useState([]);
  const [availableMonths, setAvailableMonths] = useState([]);

  useEffect(() => {
    const fetchMatches = async () => {
      try {
        const response = await matchAPI.getByPlayerId(currentPlayer.id);
        const sortedMatches = response.data.sort(
          (a, b) => new Date(b.matchDate) - new Date(a.matchDate)
        );
        setMatches(sortedMatches);
        setFilteredMatches(sortedMatches);

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

          // 選択中の月が利用可能な月に含まれていない場合、最新の月を選択
          if (months.length > 0 && !months.includes(selectedMonth)) {
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
  }, [currentPlayer, selectedYear]);

  useEffect(() => {
    let filtered = matches;

    // 期間でフィルタ
    if (viewMode === '年ごと') {
      filtered = filtered.filter((match) => {
        const matchYear = new Date(match.matchDate).getFullYear();
        return matchYear === selectedYear;
      });
    } else if (viewMode === '月ごと') {
      filtered = filtered.filter((match) => {
        // matchDateが "YYYY-MM-DD" 形式の文字列の場合、new Date()でタイムゾーン問題が発生する可能性がある
        // 文字列から直接年月を抽出する方が安全
        const [year, month] = match.matchDate.split('-').map(Number);
        return year === selectedYear && month === selectedMonth;
      });
    }
    // viewMode === '総計' の場合は全データを表示

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
  }, [searchTerm, filterResult, matches, viewMode, selectedYear, selectedMonth]);

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

  // 統計は filteredMatches から計算（期間フィルタのみ適用、検索・結果フィルタは除外）
  const getStatsData = () => {
    let statsMatches = matches;

    // 期間フィルタのみ適用
    if (viewMode === '年ごと') {
      statsMatches = statsMatches.filter((match) => {
        const matchYear = new Date(match.matchDate).getFullYear();
        return matchYear === selectedYear;
      });
    } else if (viewMode === '月ごと') {
      statsMatches = statsMatches.filter((match) => {
        // 文字列から直接年月を抽出（タイムゾーン問題を回避）
        const [year, month] = match.matchDate.split('-').map(Number);
        return year === selectedYear && month === selectedMonth;
      });
    }

    return {
      total: statsMatches.length,
      wins: statsMatches.filter((m) => m.result === '勝ち').length,
      losses: statsMatches.filter((m) => m.result === '負け').length,
      winRate:
        statsMatches.length > 0
          ? Math.round(
              (statsMatches.filter((m) => m.result === '勝ち').length /
                statsMatches.length) *
                100
            )
          : 0,
    };
  };

  const stats = getStatsData();

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* ヘッダー */}
      <div className="flex justify-end items-center">
        <Link
          to="/matches/new"
          className="flex items-center gap-2 bg-primary-600 text-white px-4 py-2 rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Plus className="w-5 h-5" />
          新規登録
        </Link>
      </div>

      {/* 期間フィルタタブ */}
      <div className="bg-white rounded-lg shadow-sm p-4">
        <div className="flex flex-col sm:flex-row gap-4">
          {/* タブ */}
          <div className="flex gap-2">
            {['総計', '年ごと', '月ごと'].map((mode) => (
              <button
                key={mode}
                onClick={() => setViewMode(mode)}
                className={`px-4 py-2 rounded-lg font-medium transition-colors ${
                  viewMode === mode
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {mode}
              </button>
            ))}
          </div>

          {/* 年選択（年ごと・月ごとの場合に表示） */}
          {(viewMode === '年ごと' || viewMode === '月ごと') && (
            <div className="flex items-center gap-2">
              <label className="text-sm font-medium text-gray-700">年:</label>
              <select
                value={selectedYear}
                onChange={(e) => setSelectedYear(Number(e.target.value))}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                {availableYears.map((year) => (
                  <option key={year} value={year}>
                    {year}年
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* 月選択（月ごとの場合に表示） */}
          {viewMode === '月ごと' && (
            <div className="flex items-center gap-2">
              <label className="text-sm font-medium text-gray-700">月:</label>
              <select
                value={selectedMonth}
                onChange={(e) => setSelectedMonth(Number(e.target.value))}
                className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                {availableMonths.map((month) => (
                  <option key={month} value={month}>
                    {month}月
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      {/* 統計カード */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-lg shadow-sm">
          <p className="text-sm text-gray-600 mb-1">総試合数</p>
          <p className="text-2xl font-bold text-gray-900">{stats.total}</p>
        </div>
        <div className="bg-white p-4 rounded-lg shadow-sm">
          <p className="text-sm text-gray-600 mb-1">勝利</p>
          <p className="text-2xl font-bold text-green-600 flex items-center gap-1">
            {stats.wins}
            <TrendingUp className="w-5 h-5" />
          </p>
        </div>
        <div className="bg-white p-4 rounded-lg shadow-sm">
          <p className="text-sm text-gray-600 mb-1">敗北</p>
          <p className="text-2xl font-bold text-red-600 flex items-center gap-1">
            {stats.losses}
            <TrendingDown className="w-5 h-5" />
          </p>
        </div>
        <div className="bg-white p-4 rounded-lg shadow-sm">
          <p className="text-sm text-gray-600 mb-1">勝率</p>
          <p className="text-2xl font-bold text-blue-600">{stats.winRate}%</p>
        </div>
      </div>

      {/* 検索とフィルタ */}
      <div className="bg-white p-4 rounded-lg shadow-sm">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* 検索 */}
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="対戦相手で検索..."
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          {/* 結果フィルタ */}
          <div className="relative">
            <Filter className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
            <select
              value={filterResult}
              onChange={(e) => setFilterResult(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent appearance-none bg-white"
            >
              <option value="全て">全ての結果</option>
              <option value="勝ち">勝ちのみ</option>
              <option value="負け">負けのみ</option>
              <option value="引き分け">引き分けのみ</option>
            </select>
          </div>
        </div>
      </div>

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
    </div>
  );
};

export default MatchList;
