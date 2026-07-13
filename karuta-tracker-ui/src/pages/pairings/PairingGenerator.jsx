import { useState, useEffect, useLayoutEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { playerAPI } from '../../api/players';
import { byeActivityAPI } from '../../api/byeActivities';
import { Link } from 'react-router-dom';
import { AlertCircle, Trash2, Check, Plus, UserPlus, RefreshCw, ChevronDown, Pencil, FileText, Lock, Unlock, RotateCcw, Ban } from 'lucide-react';
import { sortPlayersByRank } from '../../utils/playerSort';
import PlayerChip from '../../components/PlayerChip';
import PageHeader from '../../components/PageHeader';
import { DndContext, DragOverlay, PointerSensor, TouchSensor, useSensor, useSensors } from '@dnd-kit/core';
import DraggablePlayerChip from './DraggablePlayerChip';
import DroppableSlot from './DroppableSlot';
import { computeDragResult } from './pairingDragLogic';
import { syncDraftAfterAddingPlayer, restoreDraftIfMatches } from './pairingDraftLogic';
import { computeLineTextAvailability, resolveLineTextTarget, buildSummaryUrl } from './lineTextTarget';
import { shouldShowParticipantSection, shouldShowAutoMatchButton, shouldShowReshuffleButton, reshuffleButtonLabel, hasAnyCancelled, materializeCancelledSlots, showsResultLockedRow, shouldHideRow } from './pairingDisplayLogic';
import PlayerSearchCombobox from './PlayerSearchCombobox';
import PairingHelp from './PairingHelp';
import { togglePairingLock, canLockPairing, canShowUnlock, buildSaveRequests, hasNothingToSave, hasBlockingIncompletePair, computeLockedPairsInput, buildAutoMatchBody } from './pairingLockLogic';
import { formatHeaderDate, resolveHeaderVenue } from './pairingHeader';


const PairingGenerator = () => {
  const [searchParams] = useSearchParams();
  // URLパラメータの日付があればそれを使用、なければ今日
  const today = new Date().toISOString().split('T')[0];
  // この画面では日付を変更しない（?date= クエリ or 当日デフォルトから受け取る）。日付ピッカーは廃止。
  const [sessionDate] = useState(searchParams.get('date') || today);
  const initialMatchNumber = parseInt(searchParams.get('matchNumber'), 10);
  const [matchNumber, setMatchNumber] = useState(initialMatchNumber > 0 ? initialMatchNumber : 1);
  // 戻り先: from クエリパラメータがあればそれを使う（結果入力画面からの遷移など）。無ければ従来どおり設定画面
  const backTo = searchParams.get('from') || '/settings';
  const [participants, setParticipants] = useState([]);
  const [pairings, setPairings] = useState([]);
  const [waitingPlayers, setWaitingPlayers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [allPlayers, setAllPlayers] = useState([]);
  const [showAddPlayer, setShowAddPlayer] = useState(false);
  const [selectedPlayerId, setSelectedPlayerId] = useState('');
  const [currentSession, setCurrentSession] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [matchExistsMap, setMatchExistsMap] = useState({});
  const [isEditingExisting, setIsEditingExisting] = useState(false);
  const [matchLoading, setMatchLoading] = useState(true);
  const [cacheVersion, setCacheVersion] = useState(0); // キャッシュ更新トリガー
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false); // 未保存の組み合わせがあるか
  const [isViewMode, setIsViewMode] = useState(false); // 既存組み合わせの閲覧モード
  const [waitingActivities, setWaitingActivities] = useState({}); // playerId -> { activityType, freeText }
  const [lineTextTarget, setLineTextTarget] = useState('all'); // LINE送信用テキストの対象: 'all'(全試合) | 'single'(選択中の試合)
  const [lineTextOpen, setLineTextOpen] = useState(false); // LINE送信用テキスト導線の開閉（初期は畳む）

  const ACTIVITY_TYPES = [
    { value: 'READING', label: '読み' },
    { value: 'SOLO_PICK', label: '一人取り' },
    { value: 'OBSERVING', label: '見学' },
    { value: 'ASSIST_OBSERVING', label: '見学対応' },
    { value: 'OTHER', label: 'その他' },
    { value: 'ABSENT', label: '休み' },
  ];

  // 各試合番号の組み合わせデータキャッシュ
  const pairingsCache = useRef({}); // matchNumber -> pairings array or null
  const fetchIdRef = useRef(0); // stale fetch防止用
  const unsavedDraft = useRef(null); // 未保存の編集中データ { matchNumber, pairings, waitingPlayers, isEditingExisting }

  // 現在の試合が閲覧専用か（他の試合に未保存の変更がある場合）
  const isReadOnly = hasUnsavedChanges && unsavedDraft.current?.matchNumber !== matchNumber;

  // 試合番号タブの「滑る cream ハイライト」：アクティブタブを実測して位置/幅を追従させる。
  // jsdom はレイアウトしない（offsetLeft/offsetWidth=0）ため、この視覚表現は verify で担保する。
  const tabRefs = useRef({}); // num -> button 要素
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  const [indicatorReady, setIndicatorReady] = useState(false); // 初回計測までは transition を無効化（マウント時の滑り込み防止）
  const measureIndicator = useCallback(() => {
    const el = tabRefs.current[matchNumber];
    if (!el) return;
    setIndicator({ left: el.offsetLeft, width: el.offsetWidth });
    setIndicatorReady(true);
  }, [matchNumber]);
  // アクティブタブ・タブ本数が変わったら再計測（レイアウト確定後・描画前に同期実行）
  useLayoutEffect(() => {
    measureIndicator();
  }, [measureIndicator, currentSession?.totalMatches]);
  // リサイズ・フォント読込での幅変化に追従
  useEffect(() => {
    window.addEventListener('resize', measureIndicator);
    return () => window.removeEventListener('resize', measureIndicator);
  }, [measureIndicator]);

  // ドラッグ＆ドロップ設定
  const pointerSensor = useSensor(PointerSensor, { activationConstraint: { distance: 8 } });
  const touchSensor = useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 5 } });
  const sensors = useSensors(pointerSensor, touchSensor);
  const [activeDragItem, setActiveDragItem] = useState(null);
  // タップ選択モード用: 選択中の選手 { playerId, playerName, source }
  const [selectedPlayer, setSelectedPlayer] = useState(null);

  // ペアの直近対戦履歴を取得し、該当ペアのrecentMatchesを更新するヘルパー
  const fetchPairHistory = useCallback(async (pairingIndex, player1Id, player2Id) => {
    if (!player1Id || !player2Id || !sessionDate) return;
    try {
      const res = await pairingAPI.getPairHistory(player1Id, player2Id, sessionDate, matchNumber);
      setPairings(prev => {
        const updated = [...prev];
        if (updated[pairingIndex] &&
            updated[pairingIndex].player1Id === player1Id &&
            updated[pairingIndex].player2Id === player2Id) {
          updated[pairingIndex] = { ...updated[pairingIndex], recentMatches: res.data || [] };
        }
        return updated;
      });
    } catch {
      // 履歴取得失敗は無視
    }
  }, [sessionDate, matchNumber]);

  // 未保存ドラフトを保存するヘルパー
  const saveDraft = useCallback((newPairings, newWaitingPlayers, editing) => {
    unsavedDraft.current = {
      matchNumber,
      pairings: newPairings,
      waitingPlayers: newWaitingPlayers,
      isEditingExisting: editing,
    };
  }, [matchNumber]);

  // 既存の組み合わせデータを編集可能な形式に変換してstateにロード
  const loadExistingPairingsToState = useCallback((existingData, matchParticipants) => {
    if (!existingData || existingData.length === 0) {
      setPairings([]);
      setWaitingPlayers([]);
      setIsEditingExisting(false);
      setIsViewMode(false);
      return;
    }
    const converted = existingData.map(p => ({
      id: p.id,
      player1Id: p.player1Id,
      player1Name: p.player1Name,
      player2Id: p.player2Id,
      player2Name: p.player2Name,
      recentMatches: p.recentMatches || [],
      hasResult: p.hasResult || false,
      locked: p.locked || false,
      winnerName: p.winnerName || null,
      scoreDifference: p.scoreDifference ?? null,
      matchId: p.matchId || null,
      // 対戦相手キャンセル（read-time・非破壊でAPIが付与）。閲覧モードでの取消線/タグ表示・
      // 編集モード切替時の「空き」実体化に使う。
      player1Cancelled: p.player1Cancelled || false,
      player2Cancelled: p.player2Cancelled || false,
    }));
    setPairings(converted);

    // 組み合わせに含まれない参加者を待機者としてセット
    if (matchParticipants && matchParticipants.length > 0) {
      const pairedIds = new Set(existingData.flatMap(p => [p.player1Id, p.player2Id]));
      const waiting = matchParticipants.filter(p => !pairedIds.has(p.id)).map(p => ({ id: p.id, name: p.name }));
      setWaitingPlayers(waiting);
    } else {
      setWaitingPlayers([]);
    }

    setIsEditingExisting(true);
    setIsViewMode(true);
  }, []);

  // 組み合わせ対象となるアクティブ参加者名を取得
  // - 抽選あり運用 (MONTHLY + 締め切りあり): WON のみ
  // - 抽選なし運用 (SAME_DAY もしくは MONTHLY + 締め切りなし): WON + PENDING
  // バックエンドが返す `pairingIncludesPending` フラグで判定する。
  const getActiveParticipantNamesForMatch = useCallback((session, targetMatchNumber) => {
    if (!session) return [];
    const includesPending = session.pairingIncludesPending === true;
    const entries = session.matchParticipants?.[String(targetMatchNumber)] || [];
    return entries
      .filter(p => {
        if (typeof p === 'string') return true;
        if (p.status === 'WON') return true;
        if (p.status === 'PENDING') return includesPending;
        return false;
      })
      .map(p => typeof p === 'string' ? p : p.name);
  }, []);

  // 試合番号に対応する参加者をセッションデータから取得してstateに反映
  const updateParticipantsForMatch = useCallback((session, matchNum) => {
    if (!session) {
      setParticipants([]);
      return;
    }
    const matchParticipantNames = getActiveParticipantNamesForMatch(session, matchNum);
    const sessionParticipants = session.participants || [];
    // 試合番号ごとの登録参加者のみを表示（0人なら0人）
    const filtered = sessionParticipants.filter(p => matchParticipantNames.includes(p.name));
    setParticipants(filtered);
  }, [getActiveParticipantNamesForMatch]);

  // 日付変更時: セッション・参加者・全試合の組み合わせを一括取得
  useEffect(() => {
    if (!sessionDate) return;

    // stale fetch防止: このfetchのIDを記録
    const thisId = ++fetchIdRef.current;

    const fetchAllData = async () => {
      // 一括ローディング開始
      setMatchLoading(true);
      pairingsCache.current = {};
      setMatchExistsMap({});
      setPairings([]);
      setWaitingPlayers([]);
      setIsEditingExisting(false);
      setHasUnsavedChanges(false);
      unsavedDraft.current = null;
      setParticipants([]);
      setNotice('');

      try {
        // セッション情報と全組み合わせデータを並列で取得
        const [sessionResponse, allPairingsResponse] = await Promise.all([
          practiceAPI.getByDate(sessionDate),
          pairingAPI.getByDate(sessionDate).catch(() => ({ data: [] })),
        ]);

        // staleチェック: 後から別のfetchが走っていたらこの結果は無視
        if (fetchIdRef.current !== thisId) return;

        if (sessionResponse.data) {
          const session = sessionResponse.data;
          setCurrentSession(session);

          // 全組み合わせデータを試合番号ごとにグループ化
          const totalMatches = session.totalMatches || 10;
          const allPairings = allPairingsResponse.data || [];
          const pairingsByMatch = {};
          allPairings.forEach(p => {
            const num = p.matchNumber;
            if (!pairingsByMatch[num]) pairingsByMatch[num] = [];
            pairingsByMatch[num].push(p);
          });

          const newExistsMap = {};
          for (let num = 1; num <= totalMatches; num++) {
            const matchPairings = pairingsByMatch[num] || null;
            newExistsMap[num] = matchPairings !== null && matchPairings.length > 0;
            pairingsCache.current[num] = matchPairings;
          }
          setMatchExistsMap(newExistsMap);
          // URLパラメータの matchNumber が totalMatches を超えている場合はクランプ
          setMatchNumber(prev => Math.max(1, Math.min(prev, totalMatches)));
          // キャッシュ更新をトリガー → matchNumber useEffectが再実行される
          setCacheVersion(v => v + 1);
        } else {
          setCurrentSession(null);
          setParticipants([]);
          setMatchExistsMap({});
        }
      } catch (err) {
        if (fetchIdRef.current !== thisId) return;
        console.error('Failed to fetch data:', err);
        if (err.response && err.response.status === 404) {
          setCurrentSession(null);
          setParticipants([]);
          setMatchExistsMap({});
          setError('');
        } else {
          setError('データの取得に失敗しました');
        }
      } finally {
        if (fetchIdRef.current === thisId) {
          setMatchLoading(false);
        }
      }
    };

    fetchAllData();
  }, [sessionDate]);

  // 試合番号 or キャッシュ更新時: キャッシュから即座に表示 + 参加者も切替
  useEffect(() => {
    // 参加者を試合番号に応じて切替し、結果を取得
    updateParticipantsForMatch(currentSession, matchNumber);

    // この試合番号の参加者を算出（loadExistingPairingsToStateに渡す用）
    const getMatchParticipants = () => {
      if (!currentSession) return [];
      const names = getActiveParticipantNamesForMatch(currentSession, matchNumber);
      const allParticipants = currentSession.participants || [];
      // 試合番号ごとの登録参加者のみを返す（0人なら空配列）
      return allParticipants.filter(p => names.includes(p.name));
    };

    // 未保存ドラフトの試合に戻ってきた場合はドラフトを復元
    const restored = restoreDraftIfMatches(unsavedDraft.current, matchNumber);
    if (restored) {
      setPairings(restored.pairings);
      setWaitingPlayers(restored.waitingPlayers);
      setIsEditingExisting(restored.isEditingExisting);
      setIsViewMode(restored.isViewMode);
      return;
    }

    // 組み合わせをキャッシュから反映
    const cached = pairingsCache.current[matchNumber];
    if (cached !== undefined) {
      if (cached) {
        loadExistingPairingsToState(cached, getMatchParticipants());
      } else {
        setPairings([]);
        setWaitingPlayers([]);
        setIsEditingExisting(false);
        setIsViewMode(false);
      }
    } else {
      // キャッシュ未取得（初回ロード中等）の場合はクリア
      setPairings([]);
      setWaitingPlayers([]);
      setIsEditingExisting(false);
      setIsViewMode(false);
    }
  }, [matchNumber, cacheVersion, currentSession, getActiveParticipantNamesForMatch, loadExistingPairingsToState, updateParticipantsForMatch]);

  // 選手一覧を遅延取得（選手追加モーダル表示時に初めて取得）
  const playersLoadedRef = useRef(false);
  const fetchPlayersIfNeeded = async () => {
    if (playersLoadedRef.current) return;
    playersLoadedRef.current = true;
    try {
      const response = await playerAPI.getAll();
      setAllPlayers(response.data);
    } catch (err) {
      playersLoadedRef.current = false;
      console.error('Failed to fetch all players:', err);
    }
  };

  // 自動組み合わせ／再シャッフルの共通実行。
  //  - lockedPairs === undefined（新規作成＝「対戦編集」）: body に lockedPairs を含めず送る。
  //    バックエンドは従来どおり DB の hasResult / locked から保持組を導出する（後方互換）。
  //  - lockedPairs が配列（空配列含む・再シャッフル）: 常に body に lockedPairs を入れて送る。
  //    バックエンドは手動ロックをクライアント指定で判定（DB の locked は無視。結果入力済みは常に保護）。
  //    ※ [] は truthy だが「非null＝クライアント権威」を明示するため undefined 判定で分岐する。
  const runAutoMatch = async (lockedPairs) => {
    if (participants.length === 0) {
      setError('参加者がいません');
      return;
    }

    setLoading(true);
    setError('');
    setNotice('');

    try {
      const response = await pairingAPI.autoMatch(buildAutoMatchBody(sessionDate, matchNumber, lockedPairs));

      // ロック済みペアリング（結果入力済み or 手動ロック）を先頭に配置し、新規ペアリングを後ろに追加。
      // DTO の hasResult / locked をそのまま尊重する（hasResult:true 固定にしない）。
      const locked = (response.data.lockedPairings || []).map(p => ({
        ...p,
        hasResult: p.hasResult || false,
        locked: p.locked || false,
      }));
      const newPairings = (response.data.pairings || []).map(p => ({
        ...p,
        hasResult: false,
        locked: false,
      }));
      const combined = [...locked, ...newPairings];
      setPairings(combined);
      setWaitingPlayers(response.data.waitingPlayers);
      setHasUnsavedChanges(true);
      setIsViewMode(false);
      saveDraft(combined, response.data.waitingPlayers, false);

      const lockedCount = locked.length * 2;
      const usedParticipantCount =
        lockedCount + (newPairings.length || 0) * 2 + (response.data.waitingPlayers?.length || 0);
      if (usedParticipantCount !== participants.length) {
        setNotice(
          `表示中の参加者数(${participants.length}名)と最新の組み合わせ対象参加者数(${usedParticipantCount}名)が異なるため、最新の組み合わせ対象で生成しました。`
        );
      }
      if (locked.length > 0) {
        const resultLockedCount = locked.filter(p => p.hasResult).length;
        const manualLockedCount = locked.filter(p => !p.hasResult && p.locked).length;
        const parts = [];
        if (resultLockedCount > 0) parts.push(`結果入力済みの${resultLockedCount}組`);
        if (manualLockedCount > 0) parts.push(`手動ロックの${manualLockedCount}組`);
        if (parts.length > 0) {
          setNotice(prev => (prev ? prev + ' ' : '') + `${parts.join('・')}はロックされています。`);
        }
      }
    } catch (err) {
      console.error('Auto matching failed:', err);
      setError('自動組み合わせに失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // 新規作成用（従来の「対戦編集」）: ロック指定なしで実行（後方互換・挙動不変）。
  const handleAutoMatch = () => runAutoMatch();

  // ロック以外を再シャッフル: 確認ダイアログ後、現在の画面状態のロック組を lockedPairs として送る。
  // キャンセル時は何も変えない（AC-6）。ロック0件のときは全参加者を組み直す（AC-5）。
  const handleReshuffle = () => {
    const label = reshuffleButtonLabel(pairings);
    const hasLocks = pairings.some((p) => p && (p.hasResult || p.locked));
    const detail = hasLocks
      ? 'ロック済みの組は保持し、それ以外を組み直します。'
      : '全参加者を組み直します。';
    if (!window.confirm(`${label}しますか？\n${detail}`)) return;
    runAutoMatch(computeLockedPairsInput(pairings));
  };

  const handleSave = async () => {
    // 完成した組（両選手あり・結果未入力。手動ロック組も含む）が1つも無く、待機者もいなければ保存対象なし
    if (hasNothingToSave(pairings, waitingPlayers)) {
      setError('保存する組み合わせがありません');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // 結果入力済み組のみ送信対象から除外（バックエンドで保護される）。
      // 手動ロック組は locked=true で送信し、保存時に永続化する（解除は locked=false で反映）。
      const requests = buildSaveRequests(pairings);

      const waitingIds = waitingPlayers.map((p) => p.id);
      await pairingAPI.createBatch(sessionDate, matchNumber, requests, waitingIds);

      // 抜け番活動を保存（空でも必ず呼ぶことで古いレコードを削除する）
      const byeItems = waitingPlayers
        .filter(p => waitingActivities[p.id]?.activityType)
        .map(p => ({
          playerId: p.id,
          activityType: waitingActivities[p.id].activityType,
          freeText: waitingActivities[p.id].activityType === 'OTHER' ? waitingActivities[p.id].freeText : null,
        }));
      await byeActivityAPI.createBatch(sessionDate, matchNumber, byeItems).catch(err => {
        console.warn('抜け番活動の保存に失敗:', err);
      });

      // 待機活動をリセット
      setWaitingActivities({});

      // キャッシュとマップを更新
      const pairingsRes = await pairingAPI.getByDateAndMatchNumber(sessionDate, matchNumber);
      pairingsCache.current[matchNumber] = pairingsRes.data;
      // キャンセル空き組のみで空保存した場合は再取得結果が空になり得るため、実データ件数で判定する
      // （固定 true だとタブの完了チェックや LINE 送信導線が「完成済み」と誤判定する）。
      setMatchExistsMap(prev => ({ ...prev, [matchNumber]: (pairingsRes.data?.length || 0) > 0 }));
      setHasUnsavedChanges(false);
      unsavedDraft.current = null;

      // 次の試合番号に自動遷移
      const nextMatchNumber = matchNumber + 1;
      const maxMatches = currentSession?.totalMatches || 10;

      if (nextMatchNumber <= maxMatches) {
        // matchNumberを変更 → useEffectがキャッシュ・参加者から適切にstateをセット
        setMatchNumber(nextMatchNumber);
      } else {
        // 最終試合の場合もそのまま画面に留まる（テキスト生成ボタンで遷移可能）
      }
    } catch (err) {
      console.error('Save failed:', err);
      // サーバーが返す具体的な理由（例: 対象セッションの参加者でない選手は…）を優先表示する。
      // 固定文言だけだと「どの選手が・なぜ」弾かれたか分からず、原因不明のリトライを招く（Issue #958）。
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteExisting = async () => {
    const lockedCount = pairings.filter(p => p.hasResult || p.locked).length;
    const msg = lockedCount > 0
      ? `ロック済みの${lockedCount}組を除く組み合わせを削除しますか？\n（結果入力済み・手動ロックの組は保持されます。結果入力済みは個別にリセット、手動ロックは解除してください）`
      : '既存の組み合わせを削除しますか？';
    if (!window.confirm(msg)) {
      return;
    }

    setLoading(true);
    setError('');

    try {
      await pairingAPI.deleteByDateAndMatchNumber(sessionDate, matchNumber);
      // 削除後にキャッシュを再取得（ロック済みペアが残っている可能性がある）
      const pairingsRes = await pairingAPI.getByDateAndMatchNumber(sessionDate, matchNumber);
      const remaining = pairingsRes.data || [];
      pairingsCache.current[matchNumber] = remaining.length > 0 ? remaining : null;
      setMatchExistsMap(prev => ({ ...prev, [matchNumber]: remaining.length > 0 }));
      if (remaining.length > 0) {
        // ロック済みペアが残っている場合
        setCacheVersion(v => v + 1);
      } else {
        setPairings([]);
        setWaitingPlayers([]);
        setIsEditingExisting(false);
        setIsViewMode(false);
      }
      setHasUnsavedChanges(false);
      unsavedDraft.current = null;
    } catch (err) {
      console.error('Delete failed:', err);
      setError('削除に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // ロック済みペアリングのリセット
  const handleResetPairing = async (pairing) => {
    if (hasUnsavedChanges) {
      if (!window.confirm('未保存の変更があります。リセットすると未保存の変更は失われます。続行しますか？')) return;
    }
    const msg = `この組み合わせの結果をリセットしますか？\n\n${pairing.player1Name} vs ${pairing.player2Name}\n勝者: ${pairing.winnerName || '不明'}${pairing.scoreDifference != null ? `（${pairing.scoreDifference}枚差）` : ''}\n\n対戦組み合わせと試合結果の両方が削除されます。`;
    if (!window.confirm(msg)) return;

    setLoading(true);
    setError('');
    try {
      await pairingAPI.resetWithResult(pairing.id);
      // キャッシュを更新して再表示
      const pairingsRes = await pairingAPI.getByDateAndMatchNumber(sessionDate, matchNumber);
      pairingsCache.current[matchNumber] = pairingsRes.data;
      const hasData = pairingsRes.data && pairingsRes.data.length > 0;
      setMatchExistsMap(prev => ({ ...prev, [matchNumber]: hasData }));
      setCacheVersion(v => v + 1);
      setHasUnsavedChanges(false);
      unsavedDraft.current = null;
    } catch (err) {
      console.error('Reset failed:', err);
      setError('リセットに失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // 組を手動ロック（ローカル状態のトグルのみ。DB反映は「確定して保存」時）。
  // サーバ通信せず locked=true にして未保存ドラフトへ反映する。
  const handleLockPairing = (index) => {
    if (!canLockPairing(pairings[index])) {
      setError('両方の選手が揃っている組のみロックできます');
      return;
    }
    setError('');
    const updated = togglePairingLock(pairings, index, true);
    setPairings(updated);
    setHasUnsavedChanges(true);
    saveDraft(updated, waitingPlayers, isEditingExisting);
  };

  // 組の手動ロックを解除（ローカル状態のトグルのみ。DB反映は「確定して保存」時）。
  // 保存済み（id あり）・未保存（id なし）いずれの組もローカルで locked=false にできる。
  const handleUnlockPairing = (index) => {
    if (!pairings[index]) return;
    setError('');
    const updated = togglePairingLock(pairings, index, false);
    setPairings(updated);
    setHasUnsavedChanges(true);
    saveDraft(updated, waitingPlayers, isEditingExisting);
  };

  // 配置実行（ドラッグ＆ドロップとタップ選択モードで共通）
  const executePlacement = useCallback((source, dest, draggedPlayerId, draggedPlayerName) => {
    if (!source || !dest) return;

    // ロック済みペアリング（結果入力済み or 手動ロック）へのドロップを防止
    if (dest.slotType?.startsWith('pairing-') && (pairings[dest.pairingIndex]?.hasResult || pairings[dest.pairingIndex]?.locked)) return;
    // ロック済みペアリング（結果入力済み or 手動ロック）からのドラッグを防止
    if (source.type === 'pairing' && (pairings[source.pairingIndex]?.hasResult || pairings[source.pairingIndex]?.locked)) return;

    const result = computeDragResult({
      source,
      dest,
      draggedPlayerId,
      draggedPlayerName,
      pairings,
      waitingPlayers,
    });

    if (!result) return;

    setPairings(result.pairings);
    setWaitingPlayers(result.waitingPlayers);
    setHasUnsavedChanges(true);
    saveDraft(result.pairings, result.waitingPlayers, isEditingExisting);

    // Fetch pair history for affected pairings (deduplicate indices)
    [...new Set(result.affectedPairingIndices)].forEach(idx => {
      if (result.pairings[idx] && result.pairings[idx].player1Id && result.pairings[idx].player2Id) {
        fetchPairHistory(idx, result.pairings[idx].player1Id, result.pairings[idx].player2Id);
      }
    });
  }, [pairings, waitingPlayers, saveDraft, isEditingExisting, fetchPairHistory]);

  // ドラッグ＆ドロップハンドラー
  const handleDragStart = (event) => {
    setSelectedPlayer(null);
    setActiveDragItem(event.active.data.current);
  };

  const handleDragEnd = (event) => {
    const { active, over } = event;
    setActiveDragItem(null);

    if (!over) return;

    executePlacement(
      active.data.current.source,
      over.data.current,
      active.data.current.playerId,
      active.data.current.playerName,
    );
  };

  const handleDragCancel = () => {
    setActiveDragItem(null);
  };

  // タップ選択モード: チップタップ
  const handleChipClick = useCallback((playerId, playerName, source) => {
    if (isReadOnly || isViewMode) return;
    // ロック済みペアリング（結果入力済み or 手動ロック）のチップは選択・操作不可
    if (source.type === 'pairing' && (pairings[source.pairingIndex]?.hasResult || pairings[source.pairingIndex]?.locked)) return;

    // 未選択 → 選択開始
    if (!selectedPlayer) {
      setSelectedPlayer({ playerId, playerName, source });
      return;
    }

    // 同じプレイヤー再タップ → 選択解除
    if (selectedPlayer.playerId === playerId) {
      setSelectedPlayer(null);
      return;
    }

    // 別プレイヤーのチップ → source からスワップ先を導出
    let dest;
    if (source.type === 'pairing') {
      dest = {
        slotType: source.position === 1 ? 'pairing-player1' : 'pairing-player2',
        pairingIndex: source.pairingIndex,
      };
    } else if (source.type === 'waiting') {
      dest = { slotType: 'waiting-list' };
    }

    if (dest) {
      executePlacement(selectedPlayer.source, dest, selectedPlayer.playerId, selectedPlayer.playerName);
    }
    setSelectedPlayer(null);
  }, [selectedPlayer, isReadOnly, isViewMode, pairings, executePlacement]);

  // タップ選択モード: スロットタップ
  const handleSlotClick = useCallback((slotData, e) => {
    if (isReadOnly || isViewMode) return;
    if (!selectedPlayer) return;
    // 待機行の活動プルダウン等、フォーム要素由来のクリックは誤配置防止のため無視
    if (e?.target?.closest?.('select, input, textarea, option')) return;
    // ロック済みペアリング（結果入力済み or 手動ロック）への配置を防止
    if (slotData.slotType?.startsWith('pairing-') && (pairings[slotData.pairingIndex]?.hasResult || pairings[slotData.pairingIndex]?.locked)) return;

    executePlacement(selectedPlayer.source, slotData, selectedPlayer.playerId, selectedPlayer.playerName);
    setSelectedPlayer(null);
  }, [selectedPlayer, isReadOnly, isViewMode, pairings, executePlacement]);

  // 選択中は画面他領域タップで選択解除
  useEffect(() => {
    if (!selectedPlayer) return;
    const handleDocClick = () => setSelectedPlayer(null);
    document.addEventListener('click', handleDocClick);
    return () => document.removeEventListener('click', handleDocClick);
  }, [selectedPlayer]);

  const handleAddPlayer = async () => {
    if (!selectedPlayerId) {
      setError('選手を選択してください');
      return;
    }

    const playerId = Number(selectedPlayerId);

    // 既に参加者リストに含まれているかチェック
    if (participants.some(p => p.id === playerId)) {
      setError('この選手は既に参加者リストに含まれています');
      return;
    }

    // 既に待機リストに含まれているかチェック
    if (waitingPlayers.some(p => p.id === playerId)) {
      setError('この選手は既に待機リストに含まれています');
      return;
    }

    // 既に組み合わせに含まれているかチェック
    const isInPairings = pairings.some(
      p => p.player1Id === playerId || p.player2Id === playerId
    );
    if (isInPairings) {
      setError('この選手は既に組み合わせに含まれています');
      return;
    }

    const player = allPlayers.find(p => p.id === playerId);
    if (!player) {
      setError('選手が見つかりませんでした');
      return;
    }

    try {
      // DBに参加者を追加
      await practiceAPI.addParticipantToMatch(sessionDate, matchNumber, playerId);

      // セッションを再取得して参加者リストを更新
      const sessionRes = await practiceAPI.getByDate(sessionDate);
      if (sessionRes.data) {
        setCurrentSession(sessionRes.data);
        updateParticipantsForMatch(sessionRes.data, matchNumber);
      }

      // 待機リストに追加。未保存ドラフトがあれば同期更新（useEffectのドラフト復元で上書きされるのを防ぐ）
      const { newWaiting, newDraft } = syncDraftAfterAddingPlayer({
        waitingPlayers,
        newPlayer: { id: player.id, name: player.name },
        currentDraft: unsavedDraft.current,
        matchNumber,
        pairings,
        isEditingExisting,
      });
      setWaitingPlayers(newWaiting);
      unsavedDraft.current = newDraft;
      setSelectedPlayerId('');
      setShowAddPlayer(false);
      setError('');
    } catch (err) {
      console.error('Failed to add participant:', err);
      setError('参加者の追加に失敗しました');
    }
  };

  // DBからセッション・参加者データを再取得
  const handleRefresh = async () => {
    setRefreshing(true);
    setNotice('');
    try {
      const [sessionResponse, allPairingsResponse] = await Promise.all([
        practiceAPI.getByDate(sessionDate),
        pairingAPI.getByDate(sessionDate).catch(() => ({ data: [] })),
      ]);

      if (sessionResponse.data) {
        const session = sessionResponse.data;
        setCurrentSession(session);
        updateParticipantsForMatch(session, matchNumber);

        const totalMatches = session.totalMatches || 10;
        const allPairings = allPairingsResponse.data || [];
        const pairingsByMatch = {};
        allPairings.forEach(p => {
          const num = p.matchNumber;
          if (!pairingsByMatch[num]) pairingsByMatch[num] = [];
          pairingsByMatch[num].push(p);
        });

        const newExistsMap = {};
        for (let num = 1; num <= totalMatches; num++) {
          const matchPairings = pairingsByMatch[num] || null;
          newExistsMap[num] = matchPairings !== null && matchPairings.length > 0;
          pairingsCache.current[num] = matchPairings;
        }
        setMatchExistsMap(newExistsMap);
        setCacheVersion(v => v + 1);
      }
    } catch (err) {
      console.error('Refresh failed:', err);
      setError('データの更新に失敗しました');
    } finally {
      setRefreshing(false);
    }
  };

  // 既に参加している選手を除外（ロック済みペアの選手も除外）
  const availablePlayers = allPlayers.filter(player => {
    const isParticipant = participants.some(p => p.id === player.id);
    const isWaiting = waitingPlayers.some(p => p.id === player.id);
    const isInPairings = pairings.some(p => p.player1Id === player.id || p.player2Id === player.id);
    return !isParticipant && !isWaiting && !isInPairings;
  });

  // ヘッダに表示する「日付 会場名」。日付はこの画面では変更しない（表示のみ）。
  // 会場名（currentSession.venueName）が無ければ日付のみにフォールバックする（pairingHeader の純粋関数で判定）。
  const headerDate = formatHeaderDate(sessionDate);
  const headerVenue = resolveHeaderVenue(currentSession?.venueName);
  const headerTitle = (
    <>
      <span className="font-bold tabular-nums">{headerDate}</span>
      {headerVenue ? (
        <span className="ml-1.5 font-normal">{headerVenue}</span>
      ) : null}
    </>
  );

  if (matchLoading) {
    return (
      <>
        <PageHeader title={headerTitle} backTo={backTo} />
        <div className="flex flex-col items-center justify-center py-20">
          <div className="animate-spin rounded-full h-10 w-10 border-b-[3px] border-[#4a6b5a] mb-4"></div>
          <p className="text-[#8a8275] text-sm">データを読み込み中...</p>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={headerTitle}
        backTo={backTo}
        rightActions={<PairingHelp ready={!matchLoading} variant="header" />}
      />
      <div className="space-y-4">
      {/* LINE送信用テキスト生成導線（トグル格納）。可用性判定は従来どおり（挙動不変） */}
      {currentSession && (() => {
        const { allComplete, singleComplete } = computeLineTextAvailability(
          currentSession.totalMatches, matchExistsMap, matchNumber
        );
        // 希望ターゲットを有効性で解決（無効なら有効な方へフォールバック、両方無効なら非表示）
        const target = resolveLineTextTarget(lineTextTarget, allComplete, singleComplete);
        if (!target) return null;
        const to = buildSummaryUrl(sessionDate, matchNumber, target);
        return (
          <div>
            <button
              type="button"
              onClick={() => setLineTextOpen((o) => !o)}
              aria-expanded={lineTextOpen}
              className="flex items-center gap-2 w-full text-[11px] font-bold tracking-wider uppercase text-[#8a8275] py-1"
            >
              <FileText className="w-[15px] h-[15px]" />
              <span>LINE送信用テキスト</span>
              <ChevronDown className={`w-4 h-4 ml-auto text-[#b3ac9e] transition-transform ${lineTextOpen ? 'rotate-180' : ''}`} />
            </button>
            {lineTextOpen && (
              <div className="mt-3 space-y-2.5">
                {/* セグメントトグル: [ 全試合 | {matchNumber}試合目 ] */}
                <div className="grid grid-cols-2 gap-[3px] p-[3px] bg-[#e7e0d4] rounded-[10px] text-sm font-semibold">
                  <button
                    type="button"
                    onClick={() => setLineTextTarget('all')}
                    disabled={!allComplete}
                    className={`py-2 rounded-lg transition-colors ${
                      target === 'all'
                        ? 'bg-[#fffdf9] text-[#33463c] shadow-sm'
                        : allComplete
                          ? 'text-[#8a8275]'
                          : 'text-[#bcb5a6] cursor-not-allowed'
                    }`}
                  >
                    全試合
                  </button>
                  <button
                    type="button"
                    onClick={() => setLineTextTarget('single')}
                    disabled={!singleComplete}
                    className={`py-2 rounded-lg transition-colors ${
                      target === 'single'
                        ? 'bg-[#fffdf9] text-[#33463c] shadow-sm'
                        : singleComplete
                          ? 'text-[#8a8275]'
                          : 'text-[#bcb5a6] cursor-not-allowed'
                    }`}
                  >
                    {matchNumber}試合目
                  </button>
                </div>
                {/* 生成ボタン（選択中セグメントに応じて遷移） */}
                <Link
                  to={to}
                  className="flex items-center justify-center gap-2 w-full border border-[#4a6b5a] text-[#33463c] px-4 py-2.5 rounded-[10px] font-semibold text-sm hover:bg-[#4a6b5a]/10 transition-colors"
                >
                  <FileText className="w-[17px] h-[17px]" />
                  テキストを生成
                </Link>
              </div>
            )}
          </div>
        );
      })()}

      {/* 試合番号タブ（同一フットプリント＋滑る cream ハイライト）＋ 連結パネル */}
      <div>
        <div className="relative flex items-end gap-1 px-1 overflow-x-auto">
          {/* 滑る cream ハイライト（アクティブタブ位置を実測して追従。下辺=bottom-0 で連結パネルにシームレス接続） */}
          <span
            aria-hidden="true"
            className={`absolute left-0 top-1 bottom-0 z-0 rounded-t-lg bg-[#ebe4d8] ${
              indicatorReady ? 'transition-[transform,width] duration-200 ease-out' : ''
            }`}
            style={{ transform: `translateX(${indicator.left}px)`, width: `${indicator.width}px` }}
          />
          {Array.from(
            { length: currentSession?.totalMatches || 10 },
            (_, i) => i + 1
          ).map((num) => {
            const isActive = matchNumber === num;
            const isUnsaved = hasUnsavedChanges && unsavedDraft.current?.matchNumber === num;
            const exists = matchExistsMap[num];
            return (
              <button
                key={num}
                ref={(el) => { tabRefs.current[num] = el; }}
                onClick={() => setMatchNumber(num)}
                className={`relative z-10 flex-none leading-none px-3.5 pt-2.5 pb-2.5 text-[15px] whitespace-nowrap transition-colors ${
                  isActive
                    ? 'text-[#1A3654] font-bold'
                    : `font-semibold ${
                        isUnsaved ? 'text-[#b45309]' : exists ? 'text-[#33463c]' : 'text-[#9a9183]'
                      }`
                }`}
              >
                {num}
                {exists && !isActive && (
                  <span className="absolute top-0.5 left-1/2 -ml-[15px] w-[13px] h-[13px] bg-[#4a6b5a] rounded-full flex items-center justify-center">
                    <Check className="w-2 h-2 text-white" />
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* 連結パネル（この試合の内容。タブに連結。上辺はフラットにし、任意位置の cream ハイライトと接続） */}
        <div className="bg-[#ebe4d8] rounded-b-xl px-5 py-4 space-y-4">
          {/* この連結パネルが第N試合のものであることを静的表示（タブから外した「N試合目」を保持。切替でレイアウトは動かない） */}
          <div className="text-[13px] font-semibold text-[#8a8275]">{matchNumber}試合目</div>

      {/* 参加者セクション（新規作成時のみ＝pairings.length===0。折りたたみは廃止し常時展開。判定は pairingDisplayLogic に集約） */}
      {shouldShowParticipantSection(pairings) && (
        <div className="space-y-3">
          <div className="flex items-baseline justify-between gap-2">
            <span className="inline-flex items-baseline gap-[7px] text-[11px] font-bold tracking-wider uppercase text-[#8a8275]">
              全 <span className="text-[20px] font-bold normal-case tracking-normal text-[#1A2744]">{participants.length}</span>名
            </span>
            <div className="inline-flex gap-1">
              <button
                onClick={handleRefresh}
                disabled={refreshing}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#5b5446] px-2 py-1.5 rounded-lg hover:bg-black/5 disabled:opacity-50 transition-colors"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
                {refreshing ? '更新中...' : '更新'}
              </button>
              <button
                onClick={() => { setShowAddPlayer(true); fetchPlayersIfNeeded(); }}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#33463c] px-2 py-1.5 rounded-lg hover:bg-black/5 transition-colors"
              >
                <UserPlus className="w-3.5 h-3.5" />
                追加
              </button>
            </div>
          </div>
          {participants.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {sortPlayersByRank(participants).map((p) => (
                <PlayerChip
                  key={p.id}
                  name={p.name}
                  kyuRank={p.kyuRank}
                  className="text-sm bg-[#f9f6f2] text-[#374151]"
                />
              ))}
            </div>
          ) : (
            <p className="text-sm text-[#8a8275]">
              参加者なし - 更新ボタンまたは選手追加で参加者を登録してください
            </p>
          )}
        </div>
      )}

      {/* 主アクション（対戦編集＝従来の自動組み合わせ。文言のみ変更・挙動不変。新規作成時のみ表示） */}
      {shouldShowAutoMatchButton({ isReadOnly, sessionDate, participants, pairings }) && (
        <div className="flex justify-end">
          <button
            onClick={handleAutoMatch}
            disabled={loading}
            className="inline-flex items-center gap-2 bg-[#1A3654] text-white px-6 py-3 rounded-[10px] font-bold text-[15px] tracking-wide hover:bg-[#122740] transition-colors disabled:bg-gray-400"
          >
            {loading ? '生成中...' : '対戦編集'}
          </button>
        </div>
      )}

      {error && (
        <div className="flex items-start gap-2 rounded-[10px] px-3 py-2.5 text-[13px] leading-relaxed bg-[#fdf0ee] border border-[#f2c9c2] text-[#b3403a]">
          <AlertCircle className="w-[17px] h-[17px] flex-shrink-0 mt-px" />
          <span>{error}</span>
        </div>
      )}

      {notice && (
        <div className="flex items-start gap-2 rounded-[10px] px-3 py-2.5 text-[13px] leading-relaxed bg-[#fbf3df] border border-[#ecd9a8] text-[#8a5a12]">
          <AlertCircle className="w-[17px] h-[17px] flex-shrink-0 mt-px" />
          <span>{notice}</span>
        </div>
      )}

      {pairings.length > 0 && (
        <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={handleDragCancel}>
        <div className="space-y-4">
          <div className="flex items-baseline justify-between gap-2">
            <span className="inline-flex items-baseline gap-[7px] text-[11px] font-bold tracking-wider uppercase text-[#8a8275]">
              組み合わせ <span className="text-[20px] font-bold normal-case tracking-normal text-[#1A2744]">{pairings.length}</span>組
            </span>
            <div className="inline-flex items-center gap-1">
              {/* ロック以外を再シャッフル（編集可能状態のみ）。文言はロック有無で動的に切替。 */}
              {shouldShowReshuffleButton({ isReadOnly, isViewMode, pairings }) && (
                <button
                  onClick={handleReshuffle}
                  disabled={loading}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#33463c] px-1.5 py-1 rounded-md hover:bg-black/5 disabled:opacity-50 transition-colors"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  {reshuffleButtonLabel(pairings)}
                </button>
              )}
              {!isReadOnly && !isViewMode && isEditingExisting && (
                <button
                  onClick={handleDeleteExisting}
                  disabled={loading}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#a3564e] px-1.5 py-1 rounded-md hover:bg-black/5 disabled:opacity-50 transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  全削除
                </button>
              )}
              {!isReadOnly && isViewMode && (
                <button
                  onClick={() => {
                    // 編集モードへ入る際、キャンセルスロットを実体化（両キャンセル組は除去・
                    // 片キャンセル選手は「空き」に）してから切り替える。これにより編集モードの
                    // 描画・ドラッグ・保存（buildSaveRequests は両選手揃った組のみ送信）は既存ロジックのまま動く。
                    const materialized = materializeCancelledSlots(pairings);
                    setPairings(materialized);
                    setIsViewMode(false);
                    setHasUnsavedChanges(true);
                    saveDraft(materialized, waitingPlayers, isEditingExisting);
                  }}
                  className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-[#5b5446] border border-[#d8cfbf] px-3 py-1.5 rounded-[9px] hover:bg-black/5 transition-colors"
                >
                  <Pencil className="w-3.5 h-3.5" />
                  編集
                </button>
              )}
            </div>
          </div>

          <div className="divide-y divide-[#ddd3c2]">
            {pairings.map((pairing, index) => (
              // 両方キャンセルの組は閲覧モードで非表示（試合として成立しないため。結果入力済みは結果を残す）。
              // インデックスを保持するため filter ではなく map 内で null を返す。
              shouldHideRow(pairing) ? null : (
              <div key={index} className="py-3">
                {showsResultLockedRow(pairing) ? (
                  /* ロック/結果表示（結果入力済み、またはキャンセルなしの手動ロック）。
                     片方キャンセルのある手動ロック組はここに来ず、下のキャンセル表示/空き化に回る。
                     脱カード: 塗り行・塗りバッジをやめ、名前を muted・ラベルをフラット色文字にする。 */
                  <div className="flex items-center gap-2">
                    <div className="flex-1 flex items-center justify-center gap-3">
                      <span className="font-medium text-[#9a9183] text-sm">{pairing.player1Name}</span>
                      <span className="text-[#cfc7b6] text-xs">vs</span>
                      <span className="font-medium text-[#9a9183] text-sm">{pairing.player2Name}</span>
                    </div>
                    {pairing.hasResult && (
                      <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#1d4ed8] whitespace-nowrap">
                        <Lock className="w-3 h-3" />
                        結果入力済
                      </span>
                    )}
                    {pairing.locked && !pairing.hasResult && (
                      <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#b45309] whitespace-nowrap">
                        <Lock className="w-3 h-3" />
                        ロック
                      </span>
                    )}
                    {!isReadOnly && !isViewMode && pairing.id && pairing.hasResult && (
                      <button
                        onClick={() => handleResetPairing(pairing)}
                        disabled={loading}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-[#c2453b] hover:opacity-80 disabled:opacity-50 whitespace-nowrap"
                        title="組み合わせと結果をリセット"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        リセット
                      </button>
                    )}
                    {canShowUnlock({ isReadOnly, isViewMode, pairing }) && (
                      <button
                        onClick={() => handleUnlockPairing(index)}
                        disabled={loading}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-[#b45309] hover:opacity-80 disabled:opacity-50 whitespace-nowrap"
                        title="手動ロックを解除"
                      >
                        <Unlock className="w-3.5 h-3.5" />
                        解除
                      </button>
                    )}
                  </div>
                ) : (isReadOnly || isViewMode) ? (
                  hasAnyCancelled(pairing) ? (
                    /* 閲覧モード（片方キャンセル）: 結果入力済の行と同一構造。
                       取消線が付いた方がキャンセルした選手。タグは常に右端。 */
                    <div className="flex items-center gap-2">
                      <div className="flex-1 flex items-center justify-center gap-3">
                        <span className={`font-medium text-sm ${pairing.player1Cancelled ? 'text-[#9ca3af] line-through' : 'text-[#374151]'}`}>{pairing.player1Name}</span>
                        <span className="text-[#cfc7b6] text-xs">vs</span>
                        <span className={`font-medium text-sm ${pairing.player2Cancelled ? 'text-[#9ca3af] line-through' : 'text-[#374151]'}`}>{pairing.player2Name}</span>
                      </div>
                      <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#6b7280] whitespace-nowrap">
                        <Ban className="w-3 h-3" />
                        キャンセル
                      </span>
                    </div>
                  ) : (
                    /* 閲覧モード（通常）: プレーン中央寄せ名（現行踏襲・チップ化しない） */
                    <div className="flex items-center justify-center gap-3">
                      <span className="font-medium text-[#374151] text-sm">{pairing.player1Name}</span>
                      <span className="text-[#cfc7b6] text-xs">vs</span>
                      <span className="font-medium text-[#374151] text-sm">{pairing.player2Name}</span>
                    </div>
                  )
                ) : (
                  /* 編集モード: ドラッグ＆ドロップ（選手チップは元のピル形式を維持） */
                  <div className="flex items-center gap-2">
                    <DroppableSlot
                      id={`slot-pairing-${index}-player1`}
                      data={{ slotType: 'pairing-player1', pairingIndex: index }}
                      isDragActive={!!activeDragItem || !!selectedPlayer}
                      onClick={(e) => handleSlotClick({ slotType: 'pairing-player1', pairingIndex: index }, e)}
                    >
                      {pairing.player1Id ? (
                        <DraggablePlayerChip
                          id={`pairing-${index}-player1`}
                          data={{ playerId: pairing.player1Id, playerName: pairing.player1Name, kyuRank: participants.find(p => p.id === pairing.player1Id)?.kyuRank, source: { type: 'pairing', pairingIndex: index, position: 1 } }}
                          onClick={() => handleChipClick(pairing.player1Id, pairing.player1Name, { type: 'pairing', pairingIndex: index, position: 1 })}
                          isSelected={selectedPlayer?.playerId === pairing.player1Id}
                        />
                      ) : (
                        <div className="px-2.5 py-1 rounded-full border-2 border-dashed border-[#cabfa9] text-[#9a9183] text-sm text-center">空き</div>
                      )}
                    </DroppableSlot>
                    <span className="text-[#cfc7b6] text-xs flex-shrink-0">vs</span>
                    <DroppableSlot
                      id={`slot-pairing-${index}-player2`}
                      data={{ slotType: 'pairing-player2', pairingIndex: index }}
                      isDragActive={!!activeDragItem || !!selectedPlayer}
                      onClick={(e) => handleSlotClick({ slotType: 'pairing-player2', pairingIndex: index }, e)}
                    >
                      {pairing.player2Id ? (
                        <DraggablePlayerChip
                          id={`pairing-${index}-player2`}
                          data={{ playerId: pairing.player2Id, playerName: pairing.player2Name, kyuRank: participants.find(p => p.id === pairing.player2Id)?.kyuRank, source: { type: 'pairing', pairingIndex: index, position: 2 } }}
                          onClick={() => handleChipClick(pairing.player2Id, pairing.player2Name, { type: 'pairing', pairingIndex: index, position: 2 })}
                          isSelected={selectedPlayer?.playerId === pairing.player2Id}
                        />
                      ) : (
                        <div className="px-2.5 py-1 rounded-full border-2 border-dashed border-[#cabfa9] text-[#9a9183] text-sm text-center">空き</div>
                      )}
                    </DroppableSlot>
                    <span className="text-xs flex-shrink-0 w-14 text-right">
                      {pairing.recentMatches === null
                        ? <span className="text-[#cfc7b6]">...</span>
                        : pairing.recentMatches && pairing.recentMatches.length > 0
                          ? pairing.recentMatches[0].matchDate === sessionDate
                            ? <span className="text-[#dc2626] font-bold">⚠今日</span>
                            : <span className="text-[#8a8275]">{pairing.recentMatches[0].matchDate.split('-').slice(1).join('/')}</span>
                          : <span className="text-[#4a6b5a] font-semibold">初</span>
                      }
                    </span>
                    {pairing.player1Id && pairing.player2Id && (
                      <button
                        onClick={() => handleLockPairing(index)}
                        disabled={loading}
                        aria-label="ロック"
                        className="flex items-center p-1 text-[#7d8a82] hover:text-[#4a6b5a] disabled:opacity-50 flex-shrink-0"
                        title="この組をロック（自動組み合わせ・回戦削除から保護。保存時にロック状態を反映）"
                      >
                        <Lock className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                )}
              </div>
              )
            ))}
          </div>

          {!isReadOnly && !isViewMode && (waitingPlayers.length > 0 || selectedPlayer?.source?.type === 'waiting') && (
            <DroppableSlot
              id="slot-new-pairing"
              data={{ slotType: 'new-pairing' }}
              isDragActive={!!activeDragItem || !!selectedPlayer}
              onClick={(e) => handleSlotClick({ slotType: 'new-pairing' }, e)}
            >
              <div className={`border border-dashed rounded-[10px] p-3 text-center text-xs transition-colors ${(activeDragItem?.source?.type === 'waiting' || selectedPlayer?.source?.type === 'waiting') ? 'border-[#4a6b5a] bg-[#4a6b5a]/10 text-[#4a6b5a]' : 'border-[#cabfa9] text-[#9a9183]'}`}>
                ここにドロップして新しい組み合わせを作成
              </div>
            </DroppableSlot>
          )}

          {!isReadOnly && !isViewMode && (
            <div className="pt-1">
              <div className="flex items-baseline justify-between gap-2 mb-1">
                <span className="text-[11px] font-bold tracking-wider uppercase text-[#8a8275]">
                  待機中{waitingPlayers.length > 0 && <span className="normal-case tracking-normal text-[#1A2744] text-[15px] font-bold ml-1.5">{waitingPlayers.length}名</span>}
                </span>
                <button
                  onClick={() => { setShowAddPlayer(true); fetchPlayersIfNeeded(); }}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#33463c] px-2 py-1.5 rounded-lg hover:bg-black/5 transition-colors"
                >
                  <UserPlus className="w-3.5 h-3.5" />
                  選手追加
                </button>
              </div>
              <DroppableSlot
                id="slot-waiting-list"
                data={{ slotType: 'waiting-list' }}
                isDragActive={!!activeDragItem || !!selectedPlayer}
                onClick={(e) => handleSlotClick({ slotType: 'waiting-list' }, e)}
              >
                {waitingPlayers.length > 0 ? (
                  <div className="divide-y divide-[#ddd3c2]">
                    {sortPlayersByRank(waitingPlayers).map((player) => (
                      <div key={player.id} className="flex items-center gap-2 py-2.5">
                        <DraggablePlayerChip
                          id={`waiting-${player.id}`}
                          data={{ playerId: player.id, playerName: player.name, kyuRank: player.kyuRank || participants.find(p => p.id === player.id)?.kyuRank, source: { type: 'waiting' } }}
                          onClick={() => handleChipClick(player.id, player.name, { type: 'waiting' })}
                          isSelected={selectedPlayer?.playerId === player.id}
                        />
                        <select
                          value={waitingActivities[player.id]?.activityType || ''}
                          onChange={(e) => setWaitingActivities(prev => ({
                            ...prev,
                            [player.id]: { ...prev[player.id], activityType: e.target.value, freeText: e.target.value !== 'OTHER' ? '' : (prev[player.id]?.freeText || '') },
                          }))}
                          className="flex-1 text-xs bg-transparent border border-[#d8cfbf] rounded-lg px-2.5 py-1.5 text-[#5b5446] focus:ring-0 focus:border-[#4a6b5a]"
                        >
                          <option value="">活動を選択</option>
                          {ACTIVITY_TYPES.map(t => (
                            <option key={t.value} value={t.value}>{t.label}</option>
                          ))}
                        </select>
                        {waitingActivities[player.id]?.activityType === 'OTHER' && (
                          <input
                            type="text"
                            value={waitingActivities[player.id]?.freeText || ''}
                            onChange={(e) => setWaitingActivities(prev => ({
                              ...prev,
                              [player.id]: { ...prev[player.id], freeText: e.target.value },
                            }))}
                            placeholder="内容"
                            className="w-24 text-xs bg-transparent border border-[#d8cfbf] rounded-lg px-2.5 py-1.5 text-[#5b5446] focus:ring-0 focus:border-[#4a6b5a]"
                          />
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-[#8a8275]">待機中の選手はいません</p>
                )}
              </DroppableSlot>
            </div>
          )}

          {isReadOnly ? (
            <div className="rounded-[10px] px-3 py-2.5 text-center bg-[#fbf3df] border border-[#ecd9a8]">
              <p className="text-[13px] text-[#8a5a12]">
                第{unsavedDraft.current?.matchNumber}試合に未保存の組み合わせがあります
              </p>
            </div>
          ) : isViewMode ? (
            null
          ) : (
            <div className="space-y-2 pt-1">
              {hasUnsavedChanges && (
                <p className="text-xs text-[#b45309] text-center">
                  保存するまで他の試合の編集はできません
                </p>
              )}
              <div className="flex justify-end items-center gap-2.5">
                <button
                  onClick={() => {
                    // キャッシュから元の状態に復元
                    const cached = pairingsCache.current[matchNumber];
                    if (cached) {
                      // 既存の組み合わせがあった場合は閲覧モードに戻す
                      const getMatchParticipants = () => {
                        if (!currentSession) return [];
                        const names = getActiveParticipantNamesForMatch(currentSession, matchNumber);
                        const allP = currentSession.participants || [];
                        return allP.filter(p => names.includes(p.name));
                      };
                      loadExistingPairingsToState(cached, getMatchParticipants());
                    } else {
                      // 新規生成だった場合はクリア
                      setPairings([]);
                      setWaitingPlayers([]);
                      setIsEditingExisting(false);
                      setIsViewMode(false);
                    }
                    setHasUnsavedChanges(false);
                    unsavedDraft.current = null;
                    setError('');
                  }}
                  disabled={loading}
                  className="text-[#5b5446] px-4 py-3 rounded-[10px] hover:bg-black/5 transition-colors font-semibold"
                >
                  キャンセル
                </button>
                <button
                  onClick={handleSave}
                  disabled={loading || hasBlockingIncompletePair(pairings)}
                  className="inline-flex items-center gap-2 bg-[#1A3654] text-white px-6 py-3 rounded-[10px] hover:bg-[#122740] transition-colors disabled:bg-gray-400 font-bold text-[15px]"
                >
                  <Check className="w-5 h-5" />
                  {loading ? '保存中...' : '確定して保存'}
                </button>
              </div>
            </div>
          )}
        </div>
        <DragOverlay>
          {activeDragItem ? (
            <PlayerChip
              name={activeDragItem.playerName}
              kyuRank={activeDragItem.kyuRank}
              className="text-sm bg-[#f9f6f2] text-[#374151] shadow-lg opacity-80"
            />
          ) : null}
        </DragOverlay>
        </DndContext>
      )}
        </div>{/* /連結パネル */}
      </div>{/* /試合番号タブ＋パネル */}

      {/* 選手追加モーダル */}
      {showAddPlayer && (
        <div className="fixed inset-0 bg-[#1A2744]/[.42] flex items-center justify-center z-50 px-6">
          <div className="bg-[#fffdf9] border border-[#e7e0d4] rounded-[14px] p-5 max-w-md w-full shadow-[0_18px_40px_rgba(26,39,68,0.22)]">
            <h2 className="text-[17px] font-bold text-[#1A2744] mb-3.5 flex items-center gap-2">
              <UserPlus className="w-5 h-5 text-[#4a6b5a]" />
              当日参加者を追加
            </h2>

            <PlayerSearchCombobox
              players={availablePlayers}
              selectedPlayerId={selectedPlayerId}
              onSelect={setSelectedPlayerId}
            />

            {error && (
              <div className="mb-4 rounded-[10px] px-3 py-2.5 flex items-start gap-2 text-[13px] bg-[#fdf0ee] border border-[#f2c9c2] text-[#b3403a]">
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-px" />
                <span>{error}</span>
              </div>
            )}

            <div className="flex justify-end gap-2.5">
              <button
                onClick={() => {
                  setShowAddPlayer(false);
                  setSelectedPlayerId('');
                  setError('');
                }}
                className="px-4 py-2 text-[#5b5446] font-semibold rounded-[10px] hover:bg-black/5 transition-colors"
              >
                キャンセル
              </button>
              <button
                onClick={handleAddPlayer}
                className="px-4 py-2 bg-[#4a6b5a] text-white font-bold rounded-[10px] hover:bg-[#3d5a4c] transition-colors flex items-center gap-2"
              >
                <Plus className="w-4 h-4" />
                追加
              </button>
            </div>
          </div>
        </div>
      )}
      </div>
    </>
  );
};

export default PairingGenerator;
