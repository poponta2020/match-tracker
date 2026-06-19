import { useState, useEffect, useRef, useCallback } from 'react';
import { matchVideoAPI, playerAPI } from '../../api';
import {
  Video,
  Plus,
  Search,
  X,
  User,
  ChevronDown,
  Loader2,
  AlertCircle,
} from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import VideoPlayerModal from '../../components/VideoPlayerModal';
import VideoRegisterModal from '../../components/VideoRegisterModal';

/**
 * 動画倉庫画面（/videos）
 *
 * 登録済みの試合動画をサムネイル付きの縦リストで一覧表示する。
 * 検索条件（選手・年月・自分が関わる動画）で絞り込み、
 * 「もっと見る」で次ページを追記読み込みする。
 *
 * - 一覧タップ → VideoPlayerModal（MatchVideoDto をそのまま渡す）
 * - 「動画を登録」 → VideoRegisterModal を試合選択モードで開く
 */

const PAGE_SIZE = 20;

// 試合日を「YYYY/MM/DD」形式で表示（matchDate は 'YYYY-MM-DD' 文字列）
const formatMatchDate = (dateStr) => {
  if (!dateStr) return '';
  return dateStr.replace(/-/g, '/');
};

// 結果テキスト（結果入力済みの場合のみ）: 「勝者名〇N」（N=枚数差。対戦一覧の表記に揃える）
const buildResultText = (video) => {
  if (video?.winnerId == null) return null;
  const winnerName =
    video.winnerId === video.player1Id
      ? video.player1Name
      : video.winnerId === video.player2Id
        ? video.player2Name
        : null;
  if (!winnerName) return null;
  const diff = video.scoreDifference != null ? Math.abs(video.scoreDifference) : null;
  return diff != null ? `${winnerName}〇${diff}` : `${winnerName}〇`;
};

// 年セレクトの選択肢（今年から過去6年分）
const buildYearOptions = () => {
  const thisYear = new Date().getFullYear();
  return Array.from({ length: 7 }, (_, i) => thisYear - i);
};

const VideoLibrary = () => {
  // 検索条件
  const [playerId, setPlayerId] = useState(null);
  const [selectedPlayerName, setSelectedPlayerName] = useState('');
  const [year, setYear] = useState('');
  const [month, setMonth] = useState('');
  const [mine, setMine] = useState(false);

  // 選手検索UI
  const [showPlayerSearch, setShowPlayerSearch] = useState(false);
  const [playerSearchText, setPlayerSearchText] = useState('');
  const [allPlayers, setAllPlayers] = useState([]);
  const [playerSearchResults, setPlayerSearchResults] = useState([]);
  const playerSearchRef = useRef(null);
  const playersLoadedRef = useRef(false);

  // 一覧データ
  const [videos, setVideos] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // ローディング/エラー
  const [initialLoading, setInitialLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);

  // 再生モーダル/登録モーダル
  const [selectedVideo, setSelectedVideo] = useState(null);
  const [showRegisterModal, setShowRegisterModal] = useState(false);

  // 選手一覧を遅延取得（検索フォーカス時に初めて取得）
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

  // 選手検索のフィルタリング（名前部分一致）
  useEffect(() => {
    if (playerSearchText.trim()) {
      const text = playerSearchText.toLowerCase();
      const filtered = allPlayers.filter((p) =>
        p.name.toLowerCase().includes(text)
      );
      setPlayerSearchResults(filtered.slice(0, 10));
    } else {
      setPlayerSearchResults([]);
    }
  }, [playerSearchText, allPlayers]);

  // 選手検索の外側クリックで閉じる
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (playerSearchRef.current && !playerSearchRef.current.contains(e.target)) {
        setShowPlayerSearch(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 検索パラメータを組み立てる
  const buildSearchParams = useCallback(
    (targetPage) => {
      const params = { page: targetPage, size: PAGE_SIZE };
      if (mine) {
        params.mine = true;
      } else if (playerId) {
        params.playerId = playerId;
      }
      if (year) {
        params.year = Number(year);
        if (month) params.month = Number(month);
      }
      return params;
    },
    [mine, playerId, year, month]
  );

  // 取得リクエストの連番。検索条件変更（page=0 再取得）と「もっと見る」（追記取得）の
  // どちらも発行ごとにインクリメントし、レスポンス反映時に最新連番と一致する場合のみ反映する。
  // これにより、検索条件を素早く切り替えた際に前リクエストの遅延レスポンスが新条件の一覧を
  // 上書き（古い一覧が残る／順序・重複が崩れる）するのを防ぐ。条件変更時の取得自体は抑止しない。
  const requestIdRef = useRef(0);

  // 条件変更時に page=0 から再検索（取得は抑止せず、古いレスポンスのみ無視する）
  useEffect(() => {
    const myRequestId = ++requestIdRef.current;

    const fetchFirstPage = async () => {
      setInitialLoading(true);
      // 進行中の「もっと見る」があれば連番不一致で破棄されるが、その finally は
      // setLoadingMore(false) をスキップするためここで明示的に解除しておく
      // （新条件のリスト表示後にボタンが「読み込み中...」のまま固まるのを防ぐ）。
      setLoadingMore(false);
      setError(null);
      try {
        const res = await matchVideoAPI.search(buildSearchParams(0));
        // 自分の発行後に新しい取得（条件変更/もっと見る）が走っていたら stale なので捨てる
        if (requestIdRef.current !== myRequestId) return;
        const data = res.data || {};
        setVideos(data.content || []);
        setPage(data.page ?? 0);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? 0);
      } catch (err) {
        if (requestIdRef.current !== myRequestId) return;
        console.error('動画一覧の取得に失敗しました:', err);
        setError('動画一覧の取得に失敗しました');
        setVideos([]);
        setTotalPages(0);
        setTotalElements(0);
      } finally {
        if (requestIdRef.current === myRequestId) setInitialLoading(false);
      }
    };

    fetchFirstPage();
  }, [buildSearchParams]);

  // 「もっと見る」: 次ページを追記読み込み（同一条件の二重クリックは loadingMore で抑止）
  const handleLoadMore = async () => {
    if (loadingMore) return;
    const nextPage = page + 1;
    const myRequestId = ++requestIdRef.current;
    setLoadingMore(true);
    try {
      const res = await matchVideoAPI.search(buildSearchParams(nextPage));
      // 取得中に条件が変わって page=0 再取得が走った場合、この追記は別条件への append に
      // なってしまうため捨てる（順序・重複の不整合を防ぐ）。
      if (requestIdRef.current !== myRequestId) return;
      const data = res.data || {};
      setVideos((prev) => [...prev, ...(data.content || [])]);
      setPage(data.page ?? nextPage);
      setTotalPages(data.totalPages ?? totalPages);
      setTotalElements(data.totalElements ?? totalElements);
    } catch (err) {
      if (requestIdRef.current !== myRequestId) return;
      console.error('追加読み込みに失敗しました:', err);
      setError('追加読み込みに失敗しました');
    } finally {
      if (requestIdRef.current === myRequestId) setLoadingMore(false);
    }
  };

  // 選手を選択（mine と排他）
  const handleSelectPlayer = (player) => {
    setPlayerId(player.id);
    setSelectedPlayerName(player.name);
    setPlayerSearchText('');
    setShowPlayerSearch(false);
  };

  // 選手絞り込みを解除
  const handleClearPlayer = () => {
    setPlayerId(null);
    setSelectedPlayerName('');
  };

  // 「自分が関わる動画」トグル（ON 時は選手絞り込みをクリア）
  const handleToggleMine = () => {
    setMine((prev) => {
      const next = !prev;
      if (next) {
        setPlayerId(null);
        setSelectedPlayerName('');
        setShowPlayerSearch(false);
      }
      return next;
    });
  };

  // 年変更（年をクリアしたら月もクリア）
  const handleYearChange = (value) => {
    setYear(value);
    if (!value) setMonth('');
  };

  // 登録成功時は一覧を再検索（page=0 から）
  const handleRegisterSuccess = () => {
    setShowRegisterModal(false);
    // 条件はそのままに 1ページ目から再取得。連番を発行し、古いレスポンスのみ無視する。
    const myRequestId = ++requestIdRef.current;
    matchVideoAPI
      .search(buildSearchParams(0))
      .then((res) => {
        if (requestIdRef.current !== myRequestId) return;
        const data = res.data || {};
        setVideos(data.content || []);
        setPage(data.page ?? 0);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? 0);
      })
      .catch((err) => {
        if (requestIdRef.current !== myRequestId) return;
        console.error('動画一覧の再取得に失敗しました:', err);
      });
  };

  const yearOptions = buildYearOptions();
  const hasMore = page + 1 < totalPages;

  return (
    <div className="space-y-4 pb-20">
      {/* ナビゲーションバー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <h1 className="text-xl font-bold text-white flex items-center gap-2">
            <Video className="w-5 h-5" />
            動画倉庫
          </h1>
          <button
            onClick={() => setShowRegisterModal(true)}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-white border border-white/60 px-3 py-1.5 rounded-lg hover:bg-white/20 transition-colors"
          >
            <Plus className="w-4 h-4" />
            動画を登録
          </button>
        </div>
      </div>

      {/* コンテンツ（上部パディング） */}
      <div className="pt-[52px] px-4 space-y-4">
        {/* 絞り込みパネル */}
        <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-3 space-y-3">
          {/* 自分が関わる動画トグル */}
          <button
            type="button"
            onClick={handleToggleMine}
            className="w-full flex items-center justify-between gap-2 text-sm"
          >
            <span className="font-medium text-[#5f3a2d]">自分が関わる動画</span>
            <span
              className={`relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full transition-colors ${
                mine ? 'bg-[#4a6b5a]' : 'bg-[#d0c5b8]'
              }`}
            >
              <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                  mine ? 'translate-x-6' : 'translate-x-1'
                }`}
              />
            </span>
          </button>

          {/* 選手絞り込み */}
          <div ref={playerSearchRef} className="relative">
            <p className="text-xs text-[#8a7568] mb-1.5">選手で絞り込み</p>
            {playerId ? (
              <div className="flex items-center justify-between gap-2 bg-white border border-[#e2d9d0] rounded-lg px-3 py-2">
                <span className="flex items-center gap-1.5 text-sm font-medium text-[#5f3a2d] truncate">
                  <User className="w-4 h-4 text-[#b0a093] flex-shrink-0" />
                  {selectedPlayerName}
                </span>
                <button
                  type="button"
                  onClick={handleClearPlayer}
                  className="text-[#8a7568] hover:text-[#5f3a2d] flex-shrink-0"
                  aria-label="選手絞り込みを解除"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#b0a093]" />
                  <input
                    type="text"
                    placeholder={mine ? '自分が関わる動画が優先されます' : '選手名で絞り込み...'}
                    value={playerSearchText}
                    disabled={mine}
                    onFocus={() => {
                      setShowPlayerSearch(true);
                      fetchPlayersIfNeeded();
                    }}
                    onChange={(e) => setPlayerSearchText(e.target.value)}
                    className="w-full pl-9 pr-8 py-2 text-sm bg-white border border-[#e2d9d0] rounded-lg focus:outline-none focus:ring-1 focus:ring-[#82655a] focus:border-[#82655a] text-[#5f3a2d] placeholder-[#b0a093] disabled:bg-gray-50 disabled:cursor-not-allowed"
                  />
                  {playerSearchText && (
                    <button
                      type="button"
                      onClick={() => setPlayerSearchText('')}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-[#b0a093]"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>
                {showPlayerSearch && !mine && playerSearchResults.length > 0 && (
                  <div className="absolute top-full mt-1 left-0 right-0 bg-white border border-[#e2d9d0] rounded-lg shadow-lg z-40 max-h-48 overflow-y-auto">
                    {playerSearchResults.map((p) => (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => handleSelectPlayer(p)}
                        className="block w-full text-left px-4 py-2.5 text-sm hover:bg-[#f0ece6] text-[#5f3a2d]"
                      >
                        {p.name}
                        <span className="ml-2 text-xs text-[#b0a093]">{p.kyuRank || '初心者'}</span>
                      </button>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>

          {/* 年月絞り込み */}
          <div>
            <p className="text-xs text-[#8a7568] mb-1.5">期間で絞り込み</p>
            <div className="grid grid-cols-2 gap-3">
              <div className="relative">
                <select
                  value={year}
                  onChange={(e) => handleYearChange(e.target.value)}
                  className="w-full appearance-none px-3 py-2 pr-8 text-sm bg-white border border-[#e2d9d0] rounded-lg focus:outline-none focus:ring-1 focus:ring-[#82655a] focus:border-[#82655a] text-[#5f3a2d]"
                >
                  <option value="">すべての年</option>
                  {yearOptions.map((y) => (
                    <option key={y} value={y}>
                      {y}年
                    </option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[#b0a093]" />
              </div>
              <div className="relative">
                <select
                  value={month}
                  onChange={(e) => setMonth(e.target.value)}
                  disabled={!year}
                  className="w-full appearance-none px-3 py-2 pr-8 text-sm bg-white border border-[#e2d9d0] rounded-lg focus:outline-none focus:ring-1 focus:ring-[#82655a] focus:border-[#82655a] text-[#5f3a2d] disabled:bg-gray-50 disabled:cursor-not-allowed"
                >
                  <option value="">すべての月</option>
                  {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                    <option key={m} value={m}>
                      {m}月
                    </option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[#b0a093]" />
              </div>
            </div>
          </div>
        </div>

        {/* エラー表示 */}
        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-red-700">
            <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span className="text-sm">{error}</span>
          </div>
        )}

        {/* 一覧 */}
        {initialLoading ? (
          <div className="py-16">
            <LoadingScreen />
          </div>
        ) : videos.length === 0 ? (
          <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-12 text-center">
            <Video className="w-16 h-16 text-[#d0c5b8] mx-auto mb-4" />
            <p className="text-[#5f3a2d] text-base mb-1">
              {mine || playerId || year
                ? '条件に合う動画がありません'
                : '動画がまだ登録されていません'}
            </p>
            <p className="text-sm text-[#8a7568]">
              {mine || playerId || year
                ? '絞り込み条件を変更してください'
                : '「動画を登録」から試合動画を追加できます'}
            </p>
          </div>
        ) : (
          <>
            <p className="text-xs text-[#8a7568] px-1">{totalElements}件の動画</p>
            <ul className="space-y-3">
              {videos.map((video) => {
                const resultText = buildResultText(video);
                return (
                  <li key={video.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedVideo(video)}
                      className="w-full flex gap-3 bg-[#f9f6f2] rounded-lg shadow-sm p-3 text-left hover:bg-[#f3eee7] transition-colors"
                    >
                      {/* サムネイル（16:9） */}
                      <div className="relative w-32 flex-shrink-0 rounded-md overflow-hidden bg-black" style={{ aspectRatio: '16 / 9' }}>
                        {video.youtubeVideoId ? (
                          <img
                            src={`https://i.ytimg.com/vi/${video.youtubeVideoId}/mqdefault.jpg`}
                            alt=""
                            loading="lazy"
                            className="absolute inset-0 w-full h-full object-cover"
                          />
                        ) : (
                          <div className="absolute inset-0 flex items-center justify-center">
                            <Video className="w-6 h-6 text-white/50" />
                          </div>
                        )}
                      </div>

                      {/* メタ情報 */}
                      <div className="min-w-0 flex-1">
                        <p className="text-xs text-[#8a7568]">
                          {formatMatchDate(video.matchDate)}
                          {video.matchNumber != null && `\u3000第${video.matchNumber}試合`}
                        </p>
                        <p className="mt-0.5 text-sm font-semibold text-[#5f3a2d] truncate">
                          {video.player1Name}{' '}
                          <span className="text-[#8a7568] font-normal">vs</span>{' '}
                          {video.player2Name}
                        </p>
                        {resultText && (
                          <p className="mt-1 text-xs text-[#3d5a4c] font-medium truncate">
                            {resultText}
                          </p>
                        )}
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>

            {/* もっと見る */}
            {hasMore && (
              <button
                type="button"
                onClick={handleLoadMore}
                disabled={loadingMore}
                className="w-full flex items-center justify-center gap-2 py-3 text-sm font-medium text-[#82655a] bg-[#f9f6f2] border border-[#e2d9d0] rounded-lg hover:bg-[#f3eee7] disabled:opacity-50 transition-colors"
              >
                {loadingMore && <Loader2 className="w-4 h-4 animate-spin" />}
                {loadingMore ? '読み込み中...' : 'もっと見る'}
              </button>
            )}
          </>
        )}
      </div>

      {/* 再生モーダル */}
      {selectedVideo && (
        <VideoPlayerModal video={selectedVideo} onClose={() => setSelectedVideo(null)} />
      )}

      {/* 登録モーダル（試合選択モード） */}
      {showRegisterModal && (
        <VideoRegisterModal
          selectMode
          onClose={() => setShowRegisterModal(false)}
          onSuccess={handleRegisterSuccess}
        />
      )}
    </div>
  );
};

export default VideoLibrary;
