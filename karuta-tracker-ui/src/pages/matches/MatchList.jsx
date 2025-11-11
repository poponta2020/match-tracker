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

  useEffect(() => {
    const fetchMatches = async () => {
      try {
        const response = await matchAPI.getByPlayerId(currentPlayer.id);
        const sortedMatches = response.data.sort(
          (a, b) => new Date(b.matchDate) - new Date(a.matchDate)
        );
        setMatches(sortedMatches);
        setFilteredMatches(sortedMatches);
      } catch (error) {
        console.error('試合記録の取得に失敗しました:', error);
      } finally {
        setLoading(false);
      }
    };

    if (currentPlayer?.id) {
      fetchMatches();
    }
  }, [currentPlayer]);

  useEffect(() => {
    let filtered = matches;

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
  }, [searchTerm, filterResult, matches]);

  const getResultBadge = (result) => {
    const styles = {
      勝ち: 'bg-green-100 text-green-700',
      負け: 'bg-red-100 text-red-700',
      引き分け: 'bg-gray-100 text-gray-700',
    };
    return styles[result] || styles['引き分け'];
  };

  const stats = {
    total: matches.length,
    wins: matches.filter((m) => m.result === '勝ち').length,
    losses: matches.filter((m) => m.result === '負け').length,
    winRate:
      matches.length > 0
        ? Math.round(
            (matches.filter((m) => m.result === '勝ち').length /
              matches.length) *
              100
          )
        : 0,
  };

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
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
            <Trophy className="w-8 h-8 text-primary-600" />
            試合記録
          </h1>
          <p className="text-gray-600 mt-1">全ての試合記録を管理</p>
        </div>
        <Link
          to="/matches/new"
          className="flex items-center gap-2 bg-primary-600 text-white px-4 py-2 rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Plus className="w-5 h-5" />
          新規登録
        </Link>
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
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    日付
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    対戦相手
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    結果
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    枚数差
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    試合番号
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
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-2 text-sm text-gray-900">
                        <Calendar className="w-4 h-4 text-gray-400" />
                        {new Date(match.matchDate).toLocaleDateString('ja-JP')}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">
                        {match.opponentName}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getResultBadge(
                          match.result
                        )}`}
                      >
                        {match.result}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {match.scoreDifference > 0 ? '+' : ''}
                      {match.scoreDifference}枚
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      第{match.matchNumber}試合
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
