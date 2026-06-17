import { useState, useEffect, useRef, useCallback } from 'react';
import {
  X,
  Loader2,
  AlertCircle,
  Youtube,
  ChevronLeft,
  Search,
  CalendarDays,
  User,
} from 'lucide-react';
import { matchVideoAPI, pairingAPI, matchAPI, playerAPI } from '../api';
import { isValidYoutubeUrl } from '../utils/youtube';

/**
 * 試合動画 登録/編集モーダル
 *
 * 2つのモードを持つ:
 *
 * 1. 対象試合固定モード（既存・試合詳細画面などから利用）
 *    - props.match に対象試合を渡して開く。URL 入力ステップのみ。
 *
 * 2. 試合選択モード（動画倉庫から利用。props.selectMode = true）
 *    - ①試合選択ステップ → ②URL 入力ステップ の2段構成。
 *    - ①では「日付起点」「選手起点」のタブで候補を絞り込み、1件選択する。
 *    - ②は固定モードと同じ URL 入力ステップを再利用する（①へ戻れる）。
 *
 * 送信ペイロードの自然キーは、固定モードは props.match、選択モードは
 * 選択した試合（内部 state selectedMatch）から組み立てる。
 *
 * props:
 *   - match: 対象試合（固定モード）
 *       { matchDate, matchNumber, player1Id, player2Id, player1Name, player2Name }
 *   - selectMode: true で試合選択モード（match 不要）
 *   - video: 編集対象の既存動画（MatchVideoDto）。渡された場合は編集モード（固定モードのみ）。
 *   - onClose: 閉じるコールバック
 *   - onSuccess: 登録/更新成功時のコールバック（API レスポンスの data を引数に渡す）
 */

const formatMatchDate = (dateStr) => {
  if (!dateStr) return '';
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('ja-JP', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  });
};

const INVALID_URL_MESSAGE = 'YouTubeのURLを入力してください';

// 選手ペアの正規化キー（動画側は player1Id < player2Id に正規化済み）
const pairKey = (a, b) => `${Math.min(a, b)}-${Math.max(a, b)}`;
const candidateKey = (matchNumber, p1, p2) => `${matchNumber}:${pairKey(p1, p2)}`;
// 日付をまたぐ起点（選手起点）用の自然キー。
// 日付・試合番号・正規化選手ペアで一意化する（動画側は p1<p2 に正規化済み）。
const naturalKey = (date, matchNumber, p1, p2) => `${date}:${candidateKey(matchNumber, p1, p2)}`;

/**
 * 試合結果（matches）とペアリング（pairings）を自然キーで統合・重複排除するヘルパー。
 *
 * 同一自然キーが matches と pairings の両方に存在する場合は matches を優先する
 * （結果・動画情報を持つため）。選手起点（PlayerSourceSelect）で使用する。
 * 日付起点（DateSourceSelect）は単一日・別の登録済み判定（videoKeys）を使うため
 * 従来どおりの専用ロジックを維持し、本ヘルパーは使わない。
 *
 * @param {Object} params
 * @param {Array} params.matches  正規化済みスロット（matches 由来。優先）
 * @param {Array} params.pairings 正規化済みスロット（pairings 由来）
 *   各スロットの形状: { key, candidate }（key=自然キー文字列, candidate=表示・選択用オブジェクト）
 * @returns {Array} 重複排除後の candidate 配列（matches を先に登録するため matches 優先）
 */
const mergeCandidates = ({ matches, pairings }) => {
  const map = new Map();
  // matches を先に登録（同一キーで上書きしないため、結果・動画を持つ matches が優先される）
  matches.forEach((slot) => {
    if (!map.has(slot.key)) map.set(slot.key, slot.candidate);
  });
  pairings.forEach((slot) => {
    if (!map.has(slot.key)) map.set(slot.key, slot.candidate);
  });
  return [...map.values()];
};

const VideoRegisterModal = ({ match, selectMode = false, video, onClose, onSuccess }) => {
  const isEdit = Boolean(video);

  // 選択モードで選ばれた試合（固定モードでは null のまま props.match を使う）
  const [selectedMatch, setSelectedMatch] = useState(null);

  // 現在のステップ: 'select'（試合選択） or 'input'（URL入力）
  // 選択モードかつ未選択かつ新規登録のときのみ 'select' から開始
  const startInSelect = selectMode && !match && !isEdit;
  const [step, setStep] = useState(startInSelect ? 'select' : 'input');

  // URL 入力ステップの状態
  const [videoUrl, setVideoUrl] = useState(video?.videoUrl || '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [urlError, setUrlError] = useState(null);

  // 送信に使う対象試合（選択モードは selectedMatch、固定モードは props.match）
  const targetMatch = selectedMatch || match;

  const handleSubmit = async () => {
    const trimmed = videoUrl.trim();

    // クライアント側バリデーション
    if (!trimmed || !isValidYoutubeUrl(trimmed)) {
      setUrlError(INVALID_URL_MESSAGE);
      return;
    }
    if (!targetMatch) {
      setError('対象の試合が選択されていません');
      return;
    }
    setUrlError(null);
    setError(null);
    setSubmitting(true);

    try {
      let res;
      if (isEdit) {
        res = await matchVideoAPI.update(video.id, { videoUrl: trimmed });
      } else {
        res = await matchVideoAPI.register({
          matchDate: targetMatch.matchDate,
          matchNumber: targetMatch.matchNumber,
          player1Id: targetMatch.player1Id,
          player2Id: targetMatch.player2Id,
          videoUrl: trimmed,
        });
      }
      onSuccess?.(res.data);
      onClose();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          (isEdit ? '動画の更新に失敗しました' : '動画の登録に失敗しました')
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleUrlChange = (e) => {
    setVideoUrl(e.target.value);
    if (urlError) setUrlError(null);
  };

  // 試合を選択して URL 入力ステップへ進む
  const handleSelectMatch = (m) => {
    setSelectedMatch(m);
    setError(null);
    setUrlError(null);
    setStep('input');
  };

  // URL 入力ステップから試合選択ステップへ戻る
  const handleBackToSelect = () => {
    setStep('select');
    setError(null);
    setUrlError(null);
  };

  // 対象試合カード（固定モード・選択後の確認表示で共用）
  const renderTargetMatch = () => {
    if (!targetMatch) return null;
    return (
      <div className="bg-white border border-[#e2d9d0] rounded-lg px-4 py-3">
        <p className="text-xs text-[#8a7568]">
          {formatMatchDate(targetMatch.matchDate)}
          {targetMatch.matchNumber != null && `\u3000第${targetMatch.matchNumber}試合`}
        </p>
        <p className="mt-1 text-sm font-semibold text-[#5f3a2d]">
          {targetMatch.player1Name} <span className="text-[#8a7568] font-normal">vs</span>{' '}
          {targetMatch.player2Name}
        </p>
      </div>
    );
  };

  const inSelectStep = step === 'select';

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
      <div className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full max-h-[90vh] overflow-hidden flex flex-col">
        {/* ヘッダー */}
        <div className="px-6 pt-5 pb-3 flex justify-between items-start flex-shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            {/* 選択モードで URL 入力中は戻るボタン */}
            {selectMode && !inSelectStep && !isEdit && (
              <button
                onClick={handleBackToSelect}
                disabled={submitting}
                className="text-[#8a7568] hover:text-[#5f3a2d] -ml-1 disabled:opacity-50"
                aria-label="試合選択に戻る"
              >
                <ChevronLeft size={20} />
              </button>
            )}
            <h2 className="text-lg font-bold text-[#5f3a2d] truncate">
              {isEdit ? '試合動画を編集' : inSelectStep ? '試合を選択' : '試合動画を登録'}
            </h2>
          </div>
          <button
            onClick={onClose}
            disabled={submitting}
            className="text-[#8a7568] hover:text-[#5f3a2d] -mt-1 disabled:opacity-50 flex-shrink-0 ml-2"
            aria-label="閉じる"
          >
            <X size={20} />
          </button>
        </div>

        {/* コンテンツ */}
        {inSelectStep ? (
          <MatchSelectStep onSelect={handleSelectMatch} />
        ) : (
          <>
            <div className="flex-1 overflow-y-auto px-6 pb-4">
              {error && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-red-700">
                  <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                  <span className="text-sm">{error}</span>
                </div>
              )}

              {/* 対象試合 */}
              <div className="mb-4">
                <p className="text-xs text-[#8a7568] mb-1.5">対象の試合</p>
                {renderTargetMatch()}
              </div>

              {/* URL 入力 */}
              <div>
                <label className="block text-sm font-medium text-[#5f3a2d] mb-1">
                  YouTube URL <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <Youtube
                    size={16}
                    className="absolute left-3 top-1/2 -translate-y-1/2 text-[#b0a093]"
                  />
                  <input
                    type="url"
                    inputMode="url"
                    value={videoUrl}
                    onChange={handleUrlChange}
                    disabled={submitting}
                    placeholder="https://youtu.be/..."
                    className={`w-full pl-9 pr-3 py-2 text-sm bg-white border rounded-lg focus:outline-none focus:ring-1 text-[#5f3a2d] placeholder-[#b0a093] disabled:bg-gray-50 ${
                      urlError
                        ? 'border-red-400 focus:ring-red-400 focus:border-red-400'
                        : 'border-[#e2d9d0] focus:ring-[#82655a] focus:border-[#82655a]'
                    }`}
                  />
                </div>
                {urlError ? (
                  <p className="mt-1.5 text-xs text-red-600">{urlError}</p>
                ) : (
                  <p className="mt-1.5 text-xs text-[#8a7568]">
                    限定公開動画の URL を貼り付けてください
                  </p>
                )}
              </div>
            </div>

            {/* フッター */}
            <div className="px-6 py-4 border-t border-[#e2d9d0] flex justify-end gap-2 flex-shrink-0">
              <button
                onClick={onClose}
                disabled={submitting}
                className="px-4 py-2 text-sm font-medium text-[#8a7568] border border-[#c5b8ab] rounded-lg hover:bg-[#e2d9d0] disabled:opacity-50 transition-colors"
              >
                キャンセル
              </button>
              <button
                onClick={handleSubmit}
                disabled={submitting}
                className="px-4 py-2 flex items-center justify-center gap-1.5 text-sm font-medium text-white bg-[#82655a] rounded-lg hover:bg-[#6b5048] disabled:opacity-50 transition-colors"
              >
                {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                {submitting
                  ? isEdit
                    ? '更新中...'
                    : '登録中...'
                  : isEdit
                    ? '更新'
                    : '登録'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

/**
 * 試合選択ステップ
 *
 * 「日付起点」「選手起点」のタブで候補を切り替える。
 * 既に動画登録済みの試合は選択不可（グレーアウト・「登録済み」表示）。
 *
 * props:
 *   - onSelect: 選択した試合（{ matchDate, matchNumber, player1Id, player2Id,
 *     player1Name, player2Name }）を渡すコールバック
 */
const MatchSelectStep = ({ onSelect }) => {
  const [sourceTab, setSourceTab] = useState('date'); // 'date' | 'player'

  return (
    <div className="flex-1 overflow-y-auto px-6 pb-5">
      {/* 起点タブ */}
      <div className="flex gap-2 mb-4">
        <button
          type="button"
          onClick={() => setSourceTab('date')}
          className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-sm font-medium rounded-lg border transition-colors ${
            sourceTab === 'date'
              ? 'bg-[#82655a] text-white border-[#82655a]'
              : 'bg-white text-[#82655a] border-[#e2d9d0] hover:bg-[#f3eee7]'
          }`}
        >
          <CalendarDays className="w-4 h-4" />
          日付から
        </button>
        <button
          type="button"
          onClick={() => setSourceTab('player')}
          className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-sm font-medium rounded-lg border transition-colors ${
            sourceTab === 'player'
              ? 'bg-[#82655a] text-white border-[#82655a]'
              : 'bg-white text-[#82655a] border-[#e2d9d0] hover:bg-[#f3eee7]'
          }`}
        >
          <User className="w-4 h-4" />
          選手から
        </button>
      </div>

      {sourceTab === 'date' ? (
        <DateSourceSelect onSelect={onSelect} />
      ) : (
        <PlayerSourceSelect onSelect={onSelect} />
      )}
    </div>
  );
};

// 候補リストの1行（選択可能/登録済み）
const CandidateRow = ({ candidate, onSelect }) => {
  const disabled = candidate.registered || candidate.disabled;
  const content = (
    <>
      <div className="min-w-0">
        <p className="text-xs text-[#8a7568]">
          {formatMatchDate(candidate.matchDate)}
          {candidate.matchNumber != null && `\u3000第${candidate.matchNumber}試合`}
        </p>
        <p className="mt-0.5 text-sm font-semibold text-[#5f3a2d] truncate">
          {candidate.player1Name} <span className="text-[#8a7568] font-normal">vs</span>{' '}
          {candidate.player2Name}
        </p>
      </div>
      {candidate.registered && (
        <span className="flex-shrink-0 text-xs text-[#8a7568] bg-[#ece4db] px-2 py-0.5 rounded-full">
          登録済み
        </span>
      )}
      {!candidate.registered && candidate.disabled && (
        <span className="flex-shrink-0 text-xs text-[#8a7568] bg-[#ece4db] px-2 py-0.5 rounded-full">
          {candidate.disabledLabel || '登録不可'}
        </span>
      )}
    </>
  );

  if (disabled) {
    return (
      <div className="w-full flex items-center justify-between gap-2 bg-white border border-[#e2d9d0] rounded-lg px-4 py-3 opacity-50 cursor-not-allowed">
        {content}
      </div>
    );
  }
  return (
    <button
      type="button"
      onClick={() => onSelect(candidate.match)}
      className="w-full flex items-center justify-between gap-2 bg-white border border-[#e2d9d0] rounded-lg px-4 py-3 text-left hover:bg-[#f3eee7] transition-colors"
    >
      {content}
    </button>
  );
};

/**
 * 日付起点の試合選択
 *
 * 指定日のペアリング・試合結果・登録済み動画を取得し、
 * (matchNumber, 正規化選手ペア) をキーに統合・重複排除した候補を表示する。
 */
const DateSourceSelect = ({ onSelect }) => {
  const [date, setDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchCandidates = useCallback(async (targetDate) => {
    setLoading(true);
    setError(null);
    try {
      const [pairingsRes, matchesRes, videosRes] = await Promise.all([
        pairingAPI.getByDate(targetDate, { light: true }).catch(() => ({ data: [] })),
        matchAPI.getByDate(targetDate).catch(() => ({ data: [] })),
        matchVideoAPI.getByDate(targetDate).catch(() => ({ data: [] })),
      ]);

      const pairings = pairingsRes.data || [];
      const matches = matchesRes.data || [];
      const videos = videosRes.data || [];

      // 登録済み動画のキー集合
      const videoKeys = new Set(
        videos.map((v) => candidateKey(v.matchNumber, v.player1Id, v.player2Id))
      );

      // 試合結果を (matchNumber, ペア) で引けるように索引化
      const matchByKey = new Map();
      matches.forEach((m) => {
        matchByKey.set(candidateKey(m.matchNumber, m.player1Id, m.player2Id), m);
      });

      const map = new Map();

      // ペアリングから候補を構築
      pairings.forEach((p) => {
        const key = candidateKey(p.matchNumber, p.player1Id, p.player2Id);
        if (map.has(key)) return;
        const result = matchByKey.get(key);
        map.set(key, {
          key,
          matchDate: targetDate,
          matchNumber: p.matchNumber,
          player1Name: p.player1Name,
          player2Name: p.player2Name,
          registered: videoKeys.has(key),
          hasResult: Boolean(result),
          match: {
            matchDate: targetDate,
            matchNumber: p.matchNumber,
            player1Id: p.player1Id,
            player2Id: p.player2Id,
            player1Name: p.player1Name,
            player2Name: p.player2Name,
          },
        });
      });

      // 試合結果のみ存在する（ペアリングがない）試合も候補に追加
      matches.forEach((m) => {
        const key = candidateKey(m.matchNumber, m.player1Id, m.player2Id);
        if (map.has(key)) return;
        map.set(key, {
          key,
          matchDate: targetDate,
          matchNumber: m.matchNumber,
          player1Name: m.player1Name,
          player2Name: m.player2Name,
          registered: videoKeys.has(key),
          hasResult: true,
          match: {
            matchDate: targetDate,
            matchNumber: m.matchNumber,
            player1Id: m.player1Id,
            player2Id: m.player2Id,
            player1Name: m.player1Name,
            player2Name: m.player2Name,
          },
        });
      });

      // 試合番号 → 選手名 でソート
      const list = [...map.values()].sort(
        (a, b) =>
          (a.matchNumber ?? 0) - (b.matchNumber ?? 0) ||
          (a.player1Name || '').localeCompare(b.player1Name || '', 'ja')
      );
      setCandidates(list);
    } catch (err) {
      console.error('候補の取得に失敗しました:', err);
      setError('候補の取得に失敗しました');
      setCandidates([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (date) fetchCandidates(date);
  }, [date, fetchCandidates]);

  return (
    <div className="space-y-3">
      {/* 日付選択 */}
      <div>
        <label className="block text-xs text-[#8a7568] mb-1.5">日付</label>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="w-full px-3 py-2 text-sm bg-white border border-[#e2d9d0] rounded-lg focus:outline-none focus:ring-1 focus:ring-[#82655a] focus:border-[#82655a] text-[#5f3a2d]"
        />
      </div>

      {error && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-red-700">
          <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <span className="text-sm">{error}</span>
        </div>
      )}

      {loading ? (
        <div className="flex items-center justify-center py-10">
          <Loader2 className="w-6 h-6 animate-spin text-[#82655a]" />
        </div>
      ) : candidates.length === 0 ? (
        <p className="py-10 text-center text-sm text-[#8a7568]">
          この日の対戦が見つかりません
        </p>
      ) : (
        <div className="space-y-2">
          {candidates.map((c) => (
            <CandidateRow key={c.key} candidate={c} onSelect={onSelect} />
          ))}
        </div>
      )}
    </div>
  );
};

// 選手起点で表示する候補の最大件数（ペアリングAPIの「直近30件」に揃える）
const PLAYER_CANDIDATE_LIMIT = 30;

/**
 * 選手起点の試合選択
 *
 * 選手を選び、その選手の直近の対戦を候補に表示する。
 * 完了済み試合（matches）だけでなく、結果未入力の組み合わせ（pairings）も含めて
 * 候補に出す（日付起点と同じ考え方）。
 *
 * 候補構築:
 * - matchAPI.getByPlayerId（完了済み matches）と pairingAPI.getByPlayerId（組み合わせ）を
 *   並行取得し、自然キー (日付, 試合番号, 正規化選手ペア) で統合・重複排除する
 *   （同一キーは結果・動画を持つ matches を優先）。
 * - 登録済み判定:
 *   - matches 側: MatchDto.video != null
 *   - pairings 側（対応する match が無いスロット）: matchVideoAPI.search でその選手の
 *     登録済み動画一覧を取得し、自然キーの Set で照合する。
 *   matches の .video と動画 Set のいずれかに該当すれば「登録済み」表示＋選択不可。
 * - 相手がシステム未登録（相手 id が 0 / null）の試合は登録不可表示。
 * - 並びは日付の新しい順（同日は試合番号の大きい順）。
 */
const PlayerSourceSelect = ({ onSelect }) => {
  const [selectedPlayer, setSelectedPlayer] = useState(null);
  const [allPlayers, setAllPlayers] = useState([]);
  const [searchText, setSearchText] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [showSearch, setShowSearch] = useState(false);
  const playersLoadedRef = useRef(false);
  const searchRef = useRef(null);

  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

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

  // 選手検索フィルタ
  useEffect(() => {
    if (searchText.trim()) {
      const text = searchText.toLowerCase();
      setSearchResults(
        allPlayers.filter((p) => p.name.toLowerCase().includes(text)).slice(0, 10)
      );
    } else {
      setSearchResults([]);
    }
  }, [searchText, allPlayers]);

  // 外側クリックで検索を閉じる
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setShowSearch(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 選手の直近試合を取得して候補に整形
  useEffect(() => {
    if (!selectedPlayer) {
      setCandidates([]);
      return;
    }
    let cancelled = false;
    const fetchCandidates = async () => {
      setLoading(true);
      setError(null);
      try {
        // 完了済み試合・組み合わせ・登録済み動画一覧を並行取得
        const [matchesRes, pairingsRes, videosRes] = await Promise.all([
          matchAPI.getByPlayerId(selectedPlayer.id).catch(() => ({ data: [] })),
          pairingAPI.getByPlayerId(selectedPlayer.id).catch(() => ({ data: [] })),
          matchVideoAPI
            .search({ playerId: selectedPlayer.id, size: 100 })
            .catch(() => ({ data: { content: [] } })),
        ]);
        if (cancelled) return;

        const matches = matchesRes.data || [];
        const pairings = pairingsRes.data || [];
        const videos = videosRes.data?.content || [];
        const selfName = selectedPlayer.name;

        // 登録済み動画の自然キー集合（pairings 側の登録済み判定に使用。
        // matches 側は MatchDto.video でも判定するため OR で扱う）
        const videoKeys = new Set(
          videos.map((v) => naturalKey(v.matchDate, v.matchNumber, v.player1Id, v.player2Id))
        );

        // matches 由来スロット（結果・動画を持つため統合時に優先）
        const matchSlots = matches.map((m) => {
          // 対象選手を起点に表示の向きを保つ（self が実際に居る側を維持）
          const isP1 = m.player1Id === selectedPlayer.id;
          const opponentId = isP1 ? m.player2Id : m.player1Id;
          const opponentName = m.opponentName;
          const player1Name = isP1 ? selfName : opponentName;
          const player2Name = isP1 ? opponentName : selfName;
          const opponentUnregistered = !opponentId || opponentId === 0;
          const key = naturalKey(m.matchDate, m.matchNumber, m.player1Id, m.player2Id);
          const registered = m.video != null || videoKeys.has(key);
          return {
            key,
            candidate: {
              key,
              matchDate: m.matchDate,
              matchNumber: m.matchNumber,
              player1Name,
              player2Name,
              registered,
              disabled: opponentUnregistered,
              disabledLabel: '相手未登録',
              match: {
                matchDate: m.matchDate,
                matchNumber: m.matchNumber,
                player1Id: m.player1Id,
                player2Id: m.player2Id,
                player1Name,
                player2Name,
              },
            },
          };
        });

        // pairings 由来スロット（結果未入力の組み合わせ。matches に無いものだけ採用される）
        const pairingSlots = pairings.map((p) => {
          // sessionDate を matchDate にマップ。表示の向きは self を維持
          const matchDate = p.sessionDate;
          const isP1 = p.player1Id === selectedPlayer.id;
          const opponentId = isP1 ? p.player2Id : p.player1Id;
          const opponentName = isP1 ? p.player2Name : p.player1Name;
          const player1Name = isP1 ? selfName : opponentName;
          const player2Name = isP1 ? opponentName : selfName;
          const opponentUnregistered = !opponentId || opponentId === 0;
          const key = naturalKey(matchDate, p.matchNumber, p.player1Id, p.player2Id);
          return {
            key,
            candidate: {
              key,
              matchDate,
              matchNumber: p.matchNumber,
              player1Name,
              player2Name,
              registered: videoKeys.has(key),
              disabled: opponentUnregistered,
              disabledLabel: '相手未登録',
              match: {
                matchDate,
                matchNumber: p.matchNumber,
                player1Id: p.player1Id,
                player2Id: p.player2Id,
                player1Name,
                player2Name,
              },
            },
          };
        });

        // 統合・重複排除（同一自然キーは matches 優先）→ 日付の新しい順（同日は試合番号降順）
        const list = mergeCandidates({ matches: matchSlots, pairings: pairingSlots })
          .sort(
            (a, b) =>
              new Date(b.matchDate) - new Date(a.matchDate) ||
              (b.matchNumber ?? 0) - (a.matchNumber ?? 0)
          )
          .slice(0, PLAYER_CANDIDATE_LIMIT);
        setCandidates(list);
      } catch (err) {
        if (!cancelled) {
          console.error('試合一覧の取得に失敗しました:', err);
          setError('試合一覧の取得に失敗しました');
          setCandidates([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchCandidates();
    return () => {
      cancelled = true;
    };
  }, [selectedPlayer]);

  return (
    <div className="space-y-3">
      {/* 選手選択 */}
      <div ref={searchRef} className="relative">
        <label className="block text-xs text-[#8a7568] mb-1.5">選手</label>
        {selectedPlayer ? (
          <div className="flex items-center justify-between gap-2 bg-white border border-[#e2d9d0] rounded-lg px-3 py-2">
            <span className="flex items-center gap-1.5 text-sm font-medium text-[#5f3a2d] truncate">
              <User className="w-4 h-4 text-[#b0a093] flex-shrink-0" />
              {selectedPlayer.name}
            </span>
            <button
              type="button"
              onClick={() => {
                setSelectedPlayer(null);
                setShowSearch(true);
              }}
              className="text-xs text-[#82655a] underline underline-offset-2 flex-shrink-0"
            >
              変更
            </button>
          </div>
        ) : (
          <>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#b0a093]" />
              <input
                type="text"
                placeholder="選手名で絞り込み..."
                value={searchText}
                onFocus={() => {
                  setShowSearch(true);
                  fetchPlayersIfNeeded();
                }}
                onChange={(e) => setSearchText(e.target.value)}
                className="w-full pl-9 pr-8 py-2 text-sm bg-white border border-[#e2d9d0] rounded-lg focus:outline-none focus:ring-1 focus:ring-[#82655a] focus:border-[#82655a] text-[#5f3a2d] placeholder-[#b0a093]"
              />
              {searchText && (
                <button
                  type="button"
                  onClick={() => setSearchText('')}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[#b0a093]"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
            {showSearch && searchResults.length > 0 && (
              <div className="absolute top-full mt-1 left-0 right-0 bg-white border border-[#e2d9d0] rounded-lg shadow-lg z-40 max-h-44 overflow-y-auto">
                {searchResults.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => {
                      setSelectedPlayer(p);
                      setSearchText('');
                      setShowSearch(false);
                    }}
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

      {error && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-red-700">
          <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <span className="text-sm">{error}</span>
        </div>
      )}

      {!selectedPlayer ? (
        <p className="py-10 text-center text-sm text-[#8a7568]">
          選手を選ぶと直近の試合が表示されます
        </p>
      ) : loading ? (
        <div className="flex items-center justify-center py-10">
          <Loader2 className="w-6 h-6 animate-spin text-[#82655a]" />
        </div>
      ) : candidates.length === 0 ? (
        <p className="py-10 text-center text-sm text-[#8a7568]">
          この選手の試合が見つかりません
        </p>
      ) : (
        <div className="space-y-2">
          {candidates.map((c) => (
            <CandidateRow key={c.key} candidate={c} onSelect={onSelect} />
          ))}
        </div>
      )}
    </div>
  );
};

export default VideoRegisterModal;
