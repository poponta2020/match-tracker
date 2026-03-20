import { useEffect, useState, useRef } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI, playerAPI } from '../../api';
import FilterBottomSheet from '../../components/FilterBottomSheet';
import {
  Trophy,
  Plus,
  Search,
  Filter,
  X,
} from 'lucide-react';

const MatchList = () => {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryPlayerId = searchParams.get('playerId');
  const targetPlayerId = queryPlayerId ? Number(queryPlayerId) : currentPlayer?.id;
  const isOtherPlayer = targetPlayerId !== currentPlayer?.id;

  // 選手検索関連
  const [targetPlayerName, setTargetPlayerName] = useState('');
  const [targetPlayerKyuRank, setTargetPlayerKyuRank] = useState('');
  const [playerSearchText, setPlayerSearchText] = useState('');
  const [playerSearchResults, setPlayerSearchResults] = useState([]);
  const [showPlayerSearch, setShowPlayerSearch] = useState(false);
  const [allPlayers, setAllPlayers] = useState([]);
  const playerSearchRef = useRef(null);

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

  // 選手一覧を遅延取得（検索フォーカス時に初めて取得）
  const playersLoadedRef = useRef(false);
  const fetchPlayersIfNeeded = async () => {
    if (playersLoadedRef.current) return;
    playersLoadedRef.current = true;
    try {
      const res = await playerAPI.getAll();
      setAllPlayers(res.data || []);
    } catch (e) {
      playersLoadedRef.current = false;
      console.error('選手一覧の取得に失敗:', e);
    }
  };

  // ターゲット選手名の取得
  useEffect(() => {
    if (isOtherPlayer && targetPlayerId) {
      const fetchTargetPlayer = async () => {
        try {
          const res = await playerAPI.getById(targetPlayerId);
          setTargetPlayerName(res.data?.name || '');
          setTargetPlayerKyuRank(res.data?.kyuRank || '');
        } catch (e) {
          console.error('選手情報の取得に失敗:', e);
        }
      };
      fetchTargetPlayer();
    } else {
      setTargetPlayerName(currentPlayer?.name || '');
      setTargetPlayerKyuRank(currentPlayer?.kyuRank || '');
    }
  }, [targetPlayerId, isOtherPlayer, currentPlayer]);

  // 選手検索のフィルタリング
  useEffect(() => {
    if (playerSearchText.trim()) {
      const filtered = allPlayers.filter(p =>
        p.name.toLowerCase().includes(playerSearchText.toLowerCase()) &&
        p.id !== currentPlayer?.id
      );
      setPlayerSearchResults(filtered.slice(0, 10));
    } else {
      setPlayerSearchResults([]);
    }
  }, [playerSearchText, allPlayers, currentPlayer]);

  // 検索外クリックで閉じる
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (playerSearchRef.current && !playerSearchRef.current.contains(e.target)) {
        setShowPlayerSearch(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // StrictMode重複呼び出し防止用
  const fetchingMatchesRef = useRef(false);

  useEffect(() => {
    let cancelled = false;

    const fetchMatches = async () => {
      // StrictModeで2回目の呼び出しをスキップ
      if (fetchingMatchesRef.current) return;
      fetchingMatchesRef.current = true;

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
          matchAPI.getByPlayerId(targetPlayerId, matchParams),
          matchAPI.getStatisticsByRank(targetPlayerId, statsParams),
        ]);

        if (cancelled) return;

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
        if (!cancelled) {
          console.error('試合記録の取得に失敗しました:', error);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
        fetchingMatchesRef.current = false;
      }
    };

    if (targetPlayerId) {
      fetchMatches();
    }

    return () => {
      cancelled = true;
      fetchingMatchesRef.current = false;
    };
  }, [targetPlayerId, selectedYear, selectedMonth, filterKyuRank, filterGender, filterDominantHand]);

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
      勝ち: 'bg-status-success-surface text-status-success',
      負け: 'bg-status-danger-surface text-status-danger',
      引き分け: 'bg-surface text-text',
    };
    return styles[result] || styles['引き分け'];
  };

  // 日付をM/D形式でフォーマット
  const formatDate = (dateString) => {
    const [, m, d] = dateString.split('-');
    return `${Number(m)}/${Number(d)}`;
  };

  // 勝敗と枚数差を結合して表示
  const getResultDisplay = (result, scoreDifference) => {
    const symbol = result === '勝ち' ? '〇' : result === '負け' ? '×' : '△';
    return `${symbol}${scoreDifference}`;
  };

  // 勝敗に応じた色スタイル
  const getResultColor = (result) => {
    if (result === '勝ち') return 'text-status-success font-bold';
    if (result === '負け') return 'text-status-danger font-bold';
    return 'text-text-muted font-bold';
  };


  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-secondary"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-20">
      {/* ナビゲーションバー */}
      <div className="bg-surface border-b border-border-subtle shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-3">
        <div className="max-w-7xl mx-auto">
          <div className="flex items-start justify-between">
            {/* 左: 名前 + 年月 */}
            <div className="min-w-0 flex-1">
              <h1 className="text-xl font-bold text-text truncate flex items-baseline gap-2">
                <span>{isOtherPlayer ? targetPlayerName : currentPlayer?.name || ''}</span>
                {targetPlayerKyuRank && (
                  <span className="text-sm font-normal text-text-muted">{targetPlayerKyuRank}</span>
                )}
              </h1>
              <p className="text-sm text-text-muted mt-0.5">
                {selectedYear && selectedMonth
                  ? `${selectedYear}年 ${selectedMonth}月`
                  : selectedYear
                  ? `${selectedYear}年`
                  : '全期間'}
              </p>
            </div>
            {/* 右: アクションボタン */}
            <div className="flex items-center gap-2 ml-3 flex-shrink-0">
              {isOtherPlayer && (
                <button
                  onClick={() => navigate('/matches')}
                  className="text-xs text-secondary border border-secondary px-2 py-1 rounded hover:bg-secondary hover:text-text-inverse transition-colors"
                >
                  自分に戻す
                </button>
              )}
              <button
                onClick={() => { setShowPlayerSearch(!showPlayerSearch); if (!showPlayerSearch) fetchPlayersIfNeeded(); }}
                className={`p-2 rounded-full transition-colors ${showPlayerSearch ? 'bg-primary text-text-inverse' : 'text-text-muted hover:bg-surface'}`}
              >
                <Search className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* 選手検索バー（トグル表示） */}
          {showPlayerSearch && (
            <div className="relative mt-3" ref={playerSearchRef}>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-placeholder" />
                <input
                  type="text"
                  placeholder="選手名で検索..."
                  value={playerSearchText}
                  autoFocus
                  onChange={(e) => {
                    setPlayerSearchText(e.target.value);
                  }}
                  className="w-full pl-9 pr-8 py-2 text-sm bg-bg border border-border-subtle rounded-lg focus:outline-none focus:ring-1 focus:ring-focus"
                />
                {playerSearchText && (
                  <button
                    onClick={() => { setPlayerSearchText(''); }}
                    className="absolute right-3 top-1/2 -translate-y-1/2"
                  >
                    <X className="w-4 h-4 text-text-placeholder" />
                  </button>
                )}
              </div>
              {playerSearchResults.length > 0 && (
                <div className="absolute top-full mt-1 left-0 right-0 bg-bg border border-border-subtle rounded-lg shadow-lg z-40 max-h-48 overflow-y-auto">
                  {playerSearchResults.map((p) => (
                    <button
                      key={p.id}
                      onClick={() => {
                        navigate(`/matches?playerId=${p.id}`);
                        setPlayerSearchText('');
                        setShowPlayerSearch(false);
                      }}
                      className="block w-full text-left px-4 py-2.5 text-sm hover:bg-surface text-text"
                    >
                      {p.name}
                      {p.kyuRank && <span className="ml-2 text-xs text-text-placeholder">{p.kyuRank}</span>}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className={`${showPlayerSearch ? 'pt-32' : 'pt-20'} space-y-6 transition-all`}>
      {/* 統計 */}
      {rankStatistics && (
        <div className="space-y-3">
          {/* 総計カード */}
          <div className="bg-surface rounded-lg shadow-sm p-4">
            <div className="flex items-baseline justify-between mb-2">
              <div className="flex items-baseline gap-3">
                <span className="text-2xl font-bold text-text">{rankStatistics.total.total}<span className="text-sm font-normal text-text-placeholder ml-0.5">試合</span></span>
                <span className="text-sm"><span className="text-status-success font-semibold">{rankStatistics.total.wins}</span><span className="text-text-placeholder">勝</span></span>
                <span className="text-sm"><span className="text-status-danger font-semibold">{rankStatistics.total.losses}</span><span className="text-text-placeholder">敗</span></span>
              </div>
              <span className="text-xl font-bold text-text">{rankStatistics.total.winRate}%</span>
            </div>
            {/* 勝率バー */}
            <div className="w-full h-2 bg-red-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-green-500 rounded-full transition-all"
                style={{ width: `${rankStatistics.total.winRate}%` }}
              />
            </div>
          </div>

          {/* 級別リスト */}
          <div className="bg-surface rounded-lg shadow-sm divide-y divide-border-subtle">
            {['A級', 'B級', 'C級', 'D級', 'E級'].map((rank) => {
              const stats = rankStatistics.byRank[rank];
              const isEmpty = stats.total === 0;
              return (
                <div key={rank} className={`flex items-center px-4 py-2 ${isEmpty ? 'opacity-35' : ''}`}>
                  <span className="text-sm font-medium text-text w-12 flex-shrink-0">対{rank.charAt(0)}</span>
                  <span className="text-xs text-text-placeholder w-16 flex-shrink-0 text-right">
                    {isEmpty ? '—' : `${stats.wins}勝 ${stats.losses}敗`}
                  </span>
                  <div className="flex-1 mx-3 h-1.5 bg-border-subtle rounded-full overflow-hidden">
                    {!isEmpty && (
                      <div
                        className="h-full bg-secondary rounded-full transition-all"
                        style={{ width: `${stats.winRate}%` }}
                      />
                    )}
                  </div>
                  <span className={`text-sm font-semibold w-10 text-right flex-shrink-0 ${isEmpty ? 'text-text-placeholder' : 'text-text'}`}>
                    {isEmpty ? '—' : `${stats.winRate}%`}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 試合一覧 */}
      {filteredMatches.length === 0 ? (
        <div className="bg-surface rounded-lg shadow-sm p-12 text-center">
          <Trophy className="w-16 h-16 text-text-placeholder mx-auto mb-4" />
          <p className="text-text-muted text-lg mb-2">試合記録がありません</p>
          <p className="text-text-muted mb-6">
            {searchTerm || filterResult !== '全て'
              ? '検索条件を変更してください'
              : '最初の試合記録を登録しましょう'}
          </p>
          {!searchTerm && filterResult === '全て' && !isOtherPlayer && (
            <Link
              to="/matches/new"
              className="inline-flex items-center gap-2 bg-primary text-text-inverse px-6 py-3 rounded-lg hover:bg-primary-hover transition-colors"
            >
              <Plus className="w-5 h-5" />
              試合記録を登録
            </Link>
          )}
        </div>
      ) : (
        <div className="bg-surface rounded-lg shadow-sm overflow-hidden">
          <div className="divide-y divide-border-subtle">
                {filteredMatches.map((match) => (
                  <div
                    key={match.id}
                    className="flex items-center px-4 py-2 hover:bg-surface cursor-pointer transition-colors"
                    onClick={() => navigate(`/matches/${match.id}`)}
                  >
                    <span className="text-xs text-text-placeholder w-12 flex-shrink-0">{formatDate(match.matchDate)}</span>
                    <button
                      className="flex-1 min-w-0 text-sm font-medium text-text hover:text-secondary hover:underline text-left truncate"
                      onClick={(e) => {
                        e.stopPropagation();
                        const opponentId = match.player1Id === targetPlayerId ? match.player2Id : match.player1Id;
                        if (opponentId) navigate(`/matches?playerId=${opponentId}`);
                      }}
                    >
                      {match.opponentName}
                    </button>
                    <span className={`text-sm font-bold flex-shrink-0 ml-2 ${getResultColor(match.result)}`}>
                      {getResultDisplay(match.result, match.scoreDifference)}
                    </span>
                  </div>
                ))}
          </div>
        </div>
      )}

      {/* フローティングアクションボタン (FAB) */}
      <button
        onClick={() => setIsFilterOpen(true)}
        className="fixed right-4 z-20 bg-primary text-text-inverse p-4 rounded-full shadow-lg hover:bg-primary-hover transition-all hover:shadow-xl"
        style={{ bottom: 'calc(4.5rem + env(safe-area-inset-bottom, 0px))' }}
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
