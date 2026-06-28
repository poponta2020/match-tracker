import { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { playerAPI } from '../../api/players';
import { byeActivityAPI } from '../../api/byeActivities';
import { Link } from 'react-router-dom';
import { AlertCircle, Users, Shuffle, Trash2, Calendar, Check, Plus, UserPlus, RefreshCw, ChevronDown, ChevronUp, Pencil, FileText, Lock, Unlock, RotateCcw } from 'lucide-react';
import { sortPlayersByRank } from '../../utils/playerSort';
import { isAdmin, isSuperAdmin } from '../../utils/auth';
import PlayerChip from '../../components/PlayerChip';
import PageHeader from '../../components/PageHeader';
import { DndContext, DragOverlay, PointerSensor, TouchSensor, useSensor, useSensors } from '@dnd-kit/core';
import DraggablePlayerChip from './DraggablePlayerChip';
import DroppableSlot from './DroppableSlot';
import { computeDragResult } from './pairingDragLogic';
import { syncDraftAfterAddingPlayer, restoreDraftIfMatches } from './pairingDraftLogic';
import { computeLineTextAvailability, resolveLineTextTarget, buildSummaryUrl } from './lineTextTarget';
import { shouldShowParticipantSection, shouldShowAutoMatchButton } from './pairingDisplayLogic';
import PlayerSearchCombobox from './PlayerSearchCombobox';
import PairingHelp from './PairingHelp';
import { togglePairingLock, canLockPairing, canShowUnlock, buildSaveRequests, hasNothingToSave } from './pairingLockLogic';


const PairingGenerator = () => {
  const [searchParams] = useSearchParams();
  // URLパラメータの日付があればそれを使用、なければ今日
  const today = new Date().toISOString().split('T')[0];
  const [sessionDate, setSessionDate] = useState(searchParams.get('date') || today);
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
  const [showParticipantList, setShowParticipantList] = useState(true);
  const [matchExistsMap, setMatchExistsMap] = useState({});
  const [isEditingExisting, setIsEditingExisting] = useState(false);
  const [matchLoading, setMatchLoading] = useState(true);
  const [cacheVersion, setCacheVersion] = useState(0); // キャッシュ更新トリガー
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false); // 未保存の組み合わせがあるか
  const [isViewMode, setIsViewMode] = useState(false); // 既存組み合わせの閲覧モード
  const [waitingActivities, setWaitingActivities] = useState({}); // playerId -> { activityType, freeText }
  const [lineTextTarget, setLineTextTarget] = useState('all'); // LINE送信用テキストの対象: 'all'(全試合) | 'single'(選択中の試合)

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

  const handleAutoMatch = async () => {
    if (participants.length === 0) {
      setError('参加者がいません');
      return;
    }

    setLoading(true);
    setError('');
    setNotice('');

    try {
      const response = await pairingAPI.autoMatch({
        sessionDate,
        matchNumber,
      });

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
      setMatchExistsMap(prev => ({ ...prev, [matchNumber]: true }));
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
      setError('保存に失敗しました');
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

  if (matchLoading) {
    return (
      <>
        <PageHeader title="組み合わせ作成" backTo={backTo} />
        <div className="flex flex-col items-center justify-center py-20">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-[#4a6b5a] mb-4"></div>
          <p className="text-[#6b7280] text-sm">データを読み込み中...</p>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader title="組み合わせ作成" backTo={backTo} />
      <div className="space-y-6">
      {/* 使い方ヘルプ（ⓘ）。PageHeader 直下・右寄せ。初回訪問時のみ端末単位で自動表示 */}
      <PairingHelp ready={!matchLoading} />
      {/* 日付選択 */}
      <div className="bg-white p-6 rounded-lg shadow-sm space-y-4">
        <div>
          <label className="block text-sm font-medium text-[#374151] mb-2">
            <Calendar className="w-4 h-4 inline mr-1" />
            日付
          </label>
          <div className="flex gap-2">
            <input
              type="date"
              value={sessionDate}
              onChange={(e) => setSessionDate(e.target.value)}
              disabled={hasUnsavedChanges}
              className={`flex-1 px-4 py-2 border border-[#c5cec8] rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent ${hasUnsavedChanges ? 'opacity-50 cursor-not-allowed' : ''}`}
            />
            <button
              onClick={() => setSessionDate(today)}
              disabled={hasUnsavedChanges}
              className={`px-4 py-2 bg-[#e5ebe7] text-[#374151] rounded-lg transition-colors ${hasUnsavedChanges ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#d4ddd7]'}`}
            >
              今日
            </button>
          </div>
        </div>

        {/* 試合番号タブ */}
        <div>
          <label className="block text-sm font-medium text-[#374151] mb-2">
            試合番号
          </label>
          <div className="flex flex-wrap gap-2">
            {Array.from(
              { length: currentSession?.totalMatches || 10 },
              (_, i) => i + 1
            ).map((num) => (
              <button
                key={num}
                onClick={() => setMatchNumber(num)}
                className={`relative px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  matchNumber === num
                    ? 'bg-[#1A3654] text-white shadow-md'
                    : hasUnsavedChanges && unsavedDraft.current?.matchNumber === num
                      ? 'bg-[#fef3c7] text-[#b45309] border border-[#fbbf24] hover:bg-[#fde68a]'
                      : matchExistsMap[num]
                        ? 'bg-[#e5ebe7] text-[#4a6b5a] border border-[#a5b4aa] hover:bg-[#d4ddd7]'
                        : 'bg-gray-100 text-[#6b7280] border border-gray-200 hover:bg-gray-200'
                }`}
              >
                {num}
                {matchExistsMap[num] && matchNumber !== num && (
                  <Check className="w-3 h-3 absolute -top-1 -right-1 text-white bg-[#4a6b5a] rounded-full p-0.5" />
                )}
              </button>
            ))}
          </div>
        </div>

        {/* LINE送信用テキスト生成導線（全試合／単一試合のセグメントトグル＋生成ボタン） */}
        {currentSession && (() => {
          const { allComplete, singleComplete } = computeLineTextAvailability(
            currentSession.totalMatches, matchExistsMap, matchNumber
          );
          // 希望ターゲットを有効性で解決（無効なら有効な方へフォールバック、両方無効なら非表示）
          const target = resolveLineTextTarget(lineTextTarget, allComplete, singleComplete);
          if (!target) return null;
          const to = buildSummaryUrl(sessionDate, matchNumber, target);
          return (
            <div className="space-y-3">
              {/* セグメントトグル: [ 全試合 | {matchNumber}試合目 ] */}
              <div className="grid grid-cols-2 rounded-lg border border-[#a5b4aa] overflow-hidden text-sm font-medium">
                <button
                  type="button"
                  onClick={() => setLineTextTarget('all')}
                  disabled={!allComplete}
                  className={`py-2 transition-colors ${
                    target === 'all'
                      ? 'bg-[#4a6b5a] text-white'
                      : allComplete
                        ? 'bg-white text-[#4a6b5a] hover:bg-[#e5ebe7]'
                        : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                  }`}
                >
                  全試合
                </button>
                <button
                  type="button"
                  onClick={() => setLineTextTarget('single')}
                  disabled={!singleComplete}
                  className={`py-2 border-l border-[#a5b4aa] transition-colors ${
                    target === 'single'
                      ? 'bg-[#4a6b5a] text-white'
                      : singleComplete
                        ? 'bg-white text-[#4a6b5a] hover:bg-[#e5ebe7]'
                        : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                  }`}
                >
                  {matchNumber}試合目
                </button>
              </div>
              {/* 生成ボタン（選択中セグメントに応じて遷移） */}
              <Link
                to={to}
                className="flex items-center justify-center gap-2 w-full bg-[#2d4a3e] text-white px-6 py-3 rounded-lg hover:bg-[#1e3a2e] transition-colors font-medium text-base shadow-md"
              >
                <FileText className="w-5 h-5" />
                LINE送信用テキスト生成
              </Link>
            </div>
          );
        })()}

      </div>

      {/* 参加者セクション（組み合わせが1つも無い＝新規作成時のみ表示。既存の組み合わせがあれば結果の有無に関わらず非表示にして閲覧表示と一貫させる。判定は pairingDisplayLogic に集約） */}
      {shouldShowParticipantSection(pairings) && <div className="bg-white rounded-lg shadow-sm overflow-hidden">
        <div className="bg-[#e5ebe7] px-6 py-3 flex items-center justify-between">
          <button
            onClick={() => setShowParticipantList(!showParticipantList)}
            className="flex items-center gap-2 text-[#374151] font-medium"
          >
            <Users className="w-4 h-4 text-[#4a6b5a]" />
            参加者: {participants.length}名
            {showParticipantList ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
          <div className="flex gap-2">
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="flex items-center gap-1.5 bg-white text-[#4a6b5a] border border-[#a5b4aa] px-3 py-1.5 rounded-lg hover:bg-[#f9f6f2] transition-colors text-sm disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
              {refreshing ? '更新中...' : '更新'}
            </button>
            <button
              onClick={() => { setShowAddPlayer(true); fetchPlayersIfNeeded(); }}
              className="flex items-center gap-1.5 bg-[#4a6b5a] text-white px-3 py-1.5 rounded-lg hover:bg-[#3d5a4c] transition-colors text-sm"
            >
              <UserPlus className="w-4 h-4" />
              追加
            </button>
          </div>
        </div>

        {showParticipantList && (
          <div className="px-6 py-4">
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
              <p className="text-sm text-[#6b7280]">
                参加者なし - 更新ボタンまたは選手追加で参加者を登録してください
              </p>
            )}
          </div>
        )}
      </div>}

      {/* 自動組み合わせボタン（組み合わせが1つも無い＝新規作成時のみ表示、閲覧モードでは非表示。判定は pairingDisplayLogic に集約） */}
      {shouldShowAutoMatchButton({ isReadOnly, sessionDate, participants, pairings }) && (
        <div className="flex justify-center">
          <button
            onClick={handleAutoMatch}
            disabled={loading}
            className="flex items-center gap-2 bg-[#1A3654] text-white px-8 py-3 rounded-lg hover:bg-[#122740] transition-colors disabled:bg-gray-400 text-lg font-medium shadow-md"
          >
            <Shuffle className="w-5 h-5" />
            {loading ? '生成中...' : '自動組み合わせ'}
          </button>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 p-4 rounded-lg flex items-center gap-2 text-red-700">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {notice && (
        <div className="bg-amber-50 border border-amber-200 p-4 rounded-lg flex items-center gap-2 text-amber-800">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span>{notice}</span>
        </div>
      )}

      {pairings.length > 0 && (
        <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={handleDragCancel}>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold text-[#374151]">
              {isEditingExisting ? `第${matchNumber}試合の組み合わせ` : '生成された組み合わせ'}
            </h2>
            {!isReadOnly && !isViewMode && (isAdmin() || isSuperAdmin()) && (
              <div className="flex items-center gap-3">
                {isEditingExisting && (
                  <button
                    onClick={handleDeleteExisting}
                    className="text-sm text-red-600 hover:text-red-700 flex items-center gap-1"
                    disabled={loading}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    全削除
                  </button>
                )}
              </div>
            )}
            {!isReadOnly && isViewMode && (
              <button
                onClick={() => { setIsViewMode(false); setHasUnsavedChanges(true); saveDraft(pairings, waitingPlayers, isEditingExisting); }}
                className="flex items-center gap-1.5 text-sm text-[#4a6b5a] border border-[#a5b4aa] px-3 py-1.5 rounded-lg hover:bg-[#e5ebe7] transition-colors"
              >
                <Pencil className="w-3.5 h-3.5" />
                編集
              </button>
            )}
          </div>

          <div className="bg-white rounded-lg shadow-sm border border-gray-200 divide-y divide-gray-100">
            {pairings.map((pairing, index) => (
              <div key={index} className={`px-3 py-2.5 ${(pairing.hasResult || pairing.locked) ? 'bg-gray-50' : ''}`}>
                {(pairing.hasResult || pairing.locked) ? (
                  /* ロック済み（結果入力済み or 手動ロック）表示 */
                  <div className="flex items-center gap-2">
                    <div className="flex-1 flex items-center justify-center gap-3">
                      <span className="font-medium text-gray-400 text-sm">{pairing.player1Name}</span>
                      <span className="text-gray-300 text-xs">vs</span>
                      <span className="font-medium text-gray-400 text-sm">{pairing.player2Name}</span>
                    </div>
                    {pairing.hasResult && (
                      <span className="flex items-center gap-1 text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full whitespace-nowrap">
                        <Lock className="w-3 h-3" />
                        結果入力済
                      </span>
                    )}
                    {pairing.locked && !pairing.hasResult && (
                      <span className="flex items-center gap-1 text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full whitespace-nowrap">
                        <Lock className="w-3 h-3" />
                        ロック
                      </span>
                    )}
                    {!isReadOnly && !isViewMode && pairing.id && pairing.hasResult && (isAdmin() || isSuperAdmin()) && (
                      <button
                        onClick={() => handleResetPairing(pairing)}
                        disabled={loading}
                        className="flex items-center gap-1 text-xs text-red-500 hover:text-red-700 disabled:opacity-50 whitespace-nowrap"
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
                        className="flex items-center gap-1 text-xs text-amber-600 hover:text-amber-800 disabled:opacity-50 whitespace-nowrap"
                        title="手動ロックを解除"
                      >
                        <Unlock className="w-3.5 h-3.5" />
                        解除
                      </button>
                    )}
                  </div>
                ) : (isReadOnly || isViewMode) ? (
                  /* 閲覧モード */
                  <div className="flex items-center justify-center gap-3">
                    <span className="font-medium text-[#374151] text-sm">{pairing.player1Name}</span>
                    <span className="text-[#a5b4aa] text-xs">vs</span>
                    <span className="font-medium text-[#374151] text-sm">{pairing.player2Name}</span>
                  </div>
                ) : (
                  /* 編集モード: ドラッグ＆ドロップ */
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
                        <div className="px-2.5 py-1 rounded-full border-2 border-dashed border-gray-300 text-gray-400 text-sm text-center">空き</div>
                      )}
                    </DroppableSlot>
                    <span className="text-[#a5b4aa] text-xs flex-shrink-0">vs</span>
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
                        <div className="px-2.5 py-1 rounded-full border-2 border-dashed border-gray-300 text-gray-400 text-sm text-center">空き</div>
                      )}
                    </DroppableSlot>
                    <span className="text-xs flex-shrink-0 w-14 text-right">
                      {pairing.recentMatches === null
                        ? <span className="text-gray-300">...</span>
                        : pairing.recentMatches && pairing.recentMatches.length > 0
                          ? pairing.recentMatches[0].matchDate === sessionDate
                            ? <span className="text-red-600 font-bold">⚠今日</span>
                            : <span className="text-[#6b7280]">{pairing.recentMatches[0].matchDate.split('-').slice(1).join('/')}</span>
                          : <span className="text-[#4a6b5a]">初</span>
                      }
                    </span>
                    {pairing.player1Id && pairing.player2Id && (
                      <button
                        onClick={() => handleLockPairing(index)}
                        disabled={loading}
                        aria-label="ロック"
                        className="flex items-center p-1 text-[#4a6b5a] hover:text-[#3a5446] disabled:opacity-50 flex-shrink-0"
                        title="この組をロック（自動組み合わせ・回戦削除から保護。保存時にロック状態を反映）"
                      >
                        <Lock className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>

          {!isReadOnly && !isViewMode && (waitingPlayers.length > 0 || selectedPlayer?.source?.type === 'waiting') && (
            <DroppableSlot
              id="slot-new-pairing"
              data={{ slotType: 'new-pairing' }}
              isDragActive={!!activeDragItem || !!selectedPlayer}
              onClick={(e) => handleSlotClick({ slotType: 'new-pairing' }, e)}
            >
              <div className={`border-2 border-dashed rounded-lg p-4 text-center text-sm transition-colors ${(activeDragItem?.source?.type === 'waiting' || selectedPlayer?.source?.type === 'waiting') ? 'border-[#4a6b5a] bg-[#e5ebe7] text-[#4a6b5a]' : 'border-gray-300 text-gray-400'}`}>
                ここにドロップして新しい組み合わせを作成
              </div>
            </DroppableSlot>
          )}

          {!isReadOnly && !isViewMode && (
            <div className={`${waitingPlayers.length > 0 ? 'bg-yellow-50 border border-yellow-200' : 'bg-gray-50 border border-gray-200'} p-4 rounded-lg`}>
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-medium text-gray-900">
                  待機中の選手{waitingPlayers.length > 0 && `（${waitingPlayers.length}名）`}
                </h3>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => { setShowAddPlayer(true); fetchPlayersIfNeeded(); }}
                    className="flex items-center gap-1 text-sm text-[#4a6b5a] border border-[#a5b4aa] px-3 py-1 rounded hover:bg-[#e5ebe7] transition-colors"
                  >
                    <UserPlus className="w-3.5 h-3.5" />
                    選手追加
                  </button>
                </div>
              </div>
              <DroppableSlot
                id="slot-waiting-list"
                data={{ slotType: 'waiting-list' }}
                isDragActive={!!activeDragItem || !!selectedPlayer}
                onClick={(e) => handleSlotClick({ slotType: 'waiting-list' }, e)}
              >
                {waitingPlayers.length > 0 ? (
                  <div className="space-y-2">
                    {sortPlayersByRank(waitingPlayers).map((player) => (
                      <div key={player.id} className="flex items-center gap-2 bg-white rounded-lg px-3 py-2">
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
                          className="flex-1 text-xs bg-gray-50 border border-gray-200 rounded px-2 py-1 focus:ring-0 focus:border-[#4a6b5a]"
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
                            className="w-24 text-xs bg-gray-50 border border-gray-200 rounded px-2 py-1 focus:ring-0 focus:border-[#4a6b5a]"
                          />
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-[#6b7280]">待機中の選手はいません</p>
                )}
              </DroppableSlot>
            </div>
          )}

          {isReadOnly ? (
            <div className="bg-[#fef3c7] border border-[#fbbf24] p-3 rounded-lg text-center">
              <p className="text-sm text-[#92400e]">
                第{unsavedDraft.current?.matchNumber}試合に未保存の組み合わせがあります
              </p>
            </div>
          ) : isViewMode ? (
            null
          ) : (
            <div className="space-y-2">
              {hasUnsavedChanges && (
                <p className="text-xs text-[#b45309] text-center">
                  保存するまで他の試合の編集はできません
                </p>
              )}
              <div className="flex justify-end gap-3">
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
                  className="flex items-center gap-2 text-[#6b7280] bg-gray-100 px-6 py-3 rounded-lg hover:bg-gray-200 transition-colors font-medium text-lg"
                >
                  キャンセル
                </button>
                <button
                  onClick={handleSave}
                  disabled={loading || pairings.some(p => !(p.hasResult || p.locked) && (!p.player1Id || !p.player2Id))}
                  className="flex items-center gap-2 bg-[#1A3654] text-white px-8 py-3 rounded-lg hover:bg-[#122740] transition-colors disabled:bg-gray-400 font-medium text-lg shadow-md"
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

      {/* 選手追加モーダル */}
      {showAddPlayer && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4 shadow-xl">
            <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
              <UserPlus className="w-6 h-6 text-[#4a6b5a]" />
              当日参加者を追加
            </h2>

            <PlayerSearchCombobox
              players={availablePlayers}
              selectedPlayerId={selectedPlayerId}
              onSelect={setSelectedPlayerId}
            />

            {error && (
              <div className="mb-4 bg-red-50 border border-red-200 p-3 rounded-lg flex items-center gap-2 text-red-700 text-sm">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="flex justify-end gap-3">
              <button
                onClick={() => {
                  setShowAddPlayer(false);
                  setSelectedPlayerId('');
                  setError('');
                }}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
              >
                キャンセル
              </button>
              <button
                onClick={handleAddPlayer}
                className="px-4 py-2 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors flex items-center gap-2"
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
