import { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { playerAPI } from '../../api/players';
import { byeActivityAPI } from '../../api/byeActivities';
import { Link } from 'react-router-dom';
import { AlertCircle, Users, Shuffle, Trash2, Calendar, Check, Plus, UserPlus, RefreshCw, ChevronDown, ChevronUp, Pencil, FileText, Lock, RotateCcw } from 'lucide-react';
import { sortPlayersByRank } from '../../utils/playerSort';
import PlayerChip from '../../components/PlayerChip';
import { DndContext, DragOverlay, PointerSensor, TouchSensor, useSensor, useSensors } from '@dnd-kit/core';
import DraggablePlayerChip from './DraggablePlayerChip';
import DroppableSlot from './DroppableSlot';
import { computeDragResult } from './pairingDragLogic';


const PairingGenerator = () => {
  const [searchParams] = useSearchParams();
  // URLパラメータの日付があればそれを使用、なければ今日
  const today = new Date().toISOString().split('T')[0];
  const [sessionDate, setSessionDate] = useState(searchParams.get('date') || today);
  const initialMatchNumber = parseInt(searchParams.get('matchNumber'), 10);
  const [matchNumber, setMatchNumber] = useState(initialMatchNumber > 0 ? initialMatchNumber : 1);
  const [participants, setParticipants] = useState([]);
  const [pairings, setPairings] = useState([]);
  const [waitingPlayers, setWaitingPlayers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [allPlayers, setAllPlayers] = useState([]);
  const [showAddPlayer, setShowAddPlayer] = useState(false);
  const [selectedPlayerId, setSelectedPlayerId] = useState('');
  const [playerSearchText, setPlayerSearchText] = useState('');
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

  const getWonParticipantNamesForMatch = useCallback((session, targetMatchNumber) => {
    if (!session) return [];
    const entries = session.matchParticipants?.[String(targetMatchNumber)] || [];
    return entries
      .filter(p => (typeof p === 'string') || p.status === 'WON')
      .map(p => typeof p === 'string' ? p : p.name);
  }, []);

  // 試合番号に対応する参加者をセッションデータから取得してstateに反映
  const updateParticipantsForMatch = useCallback((session, matchNum) => {
    if (!session) {
      setParticipants([]);
      return;
    }
    const matchParticipantNames = getWonParticipantNamesForMatch(session, matchNum);
    const sessionParticipants = session.participants || [];
    // 試合番号ごとの登録参加者のみを表示（0人なら0人）
    const filtered = sessionParticipants.filter(p => matchParticipantNames.includes(p.name));
    setParticipants(filtered);
  }, [getWonParticipantNamesForMatch]);

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
      const names = getWonParticipantNamesForMatch(currentSession, matchNumber);
      const allParticipants = currentSession.participants || [];
      // 試合番号ごとの登録参加者のみを返す（0人なら空配列）
      return allParticipants.filter(p => names.includes(p.name));
    };

    // 未保存ドラフトの試合に戻ってきた場合はドラフトを復元
    if (unsavedDraft.current && unsavedDraft.current.matchNumber === matchNumber) {
      setPairings(unsavedDraft.current.pairings);
      setWaitingPlayers(unsavedDraft.current.waitingPlayers);
      setIsEditingExisting(unsavedDraft.current.isEditingExisting);
      setIsViewMode(false); // ドラフトは編集中なので閲覧モードではない
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
  }, [matchNumber, cacheVersion, currentSession, getWonParticipantNamesForMatch, loadExistingPairingsToState, updateParticipantsForMatch]);

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

      // ロック済みペアリングを先頭に配置し、新規ペアリングを後ろに追加
      const locked = (response.data.lockedPairings || []).map(p => ({
        ...p,
        hasResult: true,
      }));
      const newPairings = response.data.pairings || [];
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
          `表示中の参加者数(${participants.length}名)と最新WON参加者数(${usedParticipantCount}名)が異なるため、最新WONで生成しました。`
        );
      }
      if (locked.length > 0) {
        setNotice(prev => (prev ? prev + ' ' : '') + `結果入力済みの${locked.length}組はロックされています。`);
      }
    } catch (err) {
      console.error('Auto matching failed:', err);
      setError('自動組み合わせに失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    const unlockedPairings = pairings.filter(p => !p.hasResult);
    if (unlockedPairings.length === 0 && waitingPlayers.length === 0) {
      setError('保存する組み合わせがありません');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // ロック済みペアリングは送信対象から除外（バックエンド側でも保護されるが、明示的に除外）
      const requests = pairings
        .filter((p) => !p.hasResult)
        .map((p) => ({
          player1Id: p.player1Id,
          player2Id: p.player2Id,
        }));

      const waitingIds = waitingPlayers.map((p) => p.id);
      await pairingAPI.createBatch(sessionDate, matchNumber, requests, waitingIds);

      // 抜け番活動も保存（活動が選択されている場合のみ）
      const byeItems = waitingPlayers
        .filter(p => waitingActivities[p.id]?.activityType)
        .map(p => ({
          playerId: p.id,
          activityType: waitingActivities[p.id].activityType,
          freeText: waitingActivities[p.id].activityType === 'OTHER' ? waitingActivities[p.id].freeText : null,
        }));
      if (byeItems.length > 0) {
        await byeActivityAPI.createBatch(sessionDate, matchNumber, byeItems).catch(err => {
          console.warn('抜け番活動の保存に失敗:', err);
        });
      }

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
    const lockedCount = pairings.filter(p => p.hasResult).length;
    const msg = lockedCount > 0
      ? `結果入力済みの${lockedCount}組を除く組み合わせを削除しますか？\n（結果入力済みの組み合わせは個別にリセットしてください）`
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

  // ドラッグ＆ドロップハンドラー
  const handleDragStart = (event) => {
    setActiveDragItem(event.active.data.current);
  };

  const handleDragEnd = (event) => {
    const { active, over } = event;
    setActiveDragItem(null);

    if (!over) return;

    const source = active.data.current.source;
    const dest = over.data.current;

    if (!source || !dest) return;

    // ロック済みペアリングへのドロップを防止
    if (dest.slotType?.startsWith('pairing-') && pairings[dest.pairingIndex]?.hasResult) return;
    // ロック済みペアリングからのドラッグを防止
    if (source.type === 'pairing' && pairings[source.pairingIndex]?.hasResult) return;

    const result = computeDragResult({
      source,
      dest,
      draggedPlayerId: active.data.current.playerId,
      draggedPlayerName: active.data.current.playerName,
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
  };

  const handleDragCancel = () => {
    setActiveDragItem(null);
  };

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

      // 待機リストに追加
      setWaitingPlayers([...waitingPlayers, { id: player.id, name: player.name }]);
      setSelectedPlayerId('');
      setPlayerSearchText('');
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

  // ロック済みでないペアリングが存在するか
  const hasUnlockedPairings = pairings.some(p => !p.hasResult);

  if (matchLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-[#4a6b5a] mb-4"></div>
        <p className="text-[#6b7280] text-sm">データを読み込み中...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
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

        {/* 全試合の組み合わせが揃った場合にテキスト生成ボタンを表示 */}
        {currentSession && (() => {
          const total = currentSession.totalMatches || 10;
          const allComplete = total > 0 && Array.from({ length: total }, (_, i) => i + 1).every(num => matchExistsMap[num]);
          return allComplete ? (
            <Link
              to={`/pairings/summary?date=${sessionDate}`}
              className="flex items-center justify-center gap-2 w-full bg-[#2d4a3e] text-white px-6 py-3 rounded-lg hover:bg-[#1e3a2e] transition-colors font-medium text-base shadow-md"
            >
              <FileText className="w-5 h-5" />
              LINE送信用テキスト生成
            </Link>
          ) : null;
        })()}

      </div>

      {/* 参加者セクション（未ロックの組み合わせが未作成時のみ表示） */}
      {!hasUnlockedPairings && <div className="bg-white rounded-lg shadow-sm overflow-hidden">
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

      {/* 自動組み合わせボタン（未ロックの組み合わせ未生成時のみ表示、閲覧モードでは非表示） */}
      {!isReadOnly && sessionDate && participants.length > 0 && !hasUnlockedPairings && (
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
            {!isReadOnly && !isViewMode && (
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
              <div key={index} className={`px-3 py-2.5 ${pairing.hasResult ? 'bg-gray-50' : ''}`}>
                {pairing.hasResult ? (
                  /* ロック済み（結果入力済み）表示 */
                  <div className="flex items-center gap-2">
                    <div className="flex-1 flex items-center justify-center gap-3">
                      <span className="font-medium text-gray-400 text-sm">{pairing.player1Name}</span>
                      <span className="text-gray-300 text-xs">vs</span>
                      <span className="font-medium text-gray-400 text-sm">{pairing.player2Name}</span>
                    </div>
                    <span className="flex items-center gap-1 text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full whitespace-nowrap">
                      <Lock className="w-3 h-3" />
                      結果入力済
                    </span>
                    {!isReadOnly && !isViewMode && pairing.id && (
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
                    <DroppableSlot id={`slot-pairing-${index}-player1`} data={{ slotType: 'pairing-player1', pairingIndex: index }} isDragActive={!!activeDragItem}>
                      {pairing.player1Id ? (
                        <DraggablePlayerChip
                          id={`pairing-${index}-player1`}
                          data={{ playerId: pairing.player1Id, playerName: pairing.player1Name, kyuRank: participants.find(p => p.id === pairing.player1Id)?.kyuRank, source: { type: 'pairing', pairingIndex: index, position: 1 } }}
                        />
                      ) : (
                        <div className="px-2.5 py-1 rounded-full border-2 border-dashed border-gray-300 text-gray-400 text-sm text-center">空き</div>
                      )}
                    </DroppableSlot>
                    <span className="text-[#a5b4aa] text-xs flex-shrink-0">vs</span>
                    <DroppableSlot id={`slot-pairing-${index}-player2`} data={{ slotType: 'pairing-player2', pairingIndex: index }} isDragActive={!!activeDragItem}>
                      {pairing.player2Id ? (
                        <DraggablePlayerChip
                          id={`pairing-${index}-player2`}
                          data={{ playerId: pairing.player2Id, playerName: pairing.player2Name, kyuRank: participants.find(p => p.id === pairing.player2Id)?.kyuRank, source: { type: 'pairing', pairingIndex: index, position: 2 } }}
                        />
                      ) : (
                        <div className="px-2.5 py-1 rounded-full border-2 border-dashed border-gray-300 text-gray-400 text-sm text-center">空き</div>
                      )}
                    </DroppableSlot>
                    <span className="text-xs text-[#6b7280] flex-shrink-0 w-12 text-right">
                      {pairing.recentMatches === null
                        ? <span className="text-gray-300">...</span>
                        : pairing.recentMatches && pairing.recentMatches.length > 0
                          ? pairing.recentMatches[0].matchDate.split('-').slice(1).join('/')
                          : <span className="text-[#4a6b5a]">初</span>
                      }
                    </span>
                  </div>
                )}
              </div>
            ))}
          </div>

          {!isReadOnly && !isViewMode && waitingPlayers.length > 0 && (
            <DroppableSlot id="slot-new-pairing" data={{ slotType: 'new-pairing' }} isDragActive={!!activeDragItem}>
              <div className={`border-2 border-dashed rounded-lg p-4 text-center text-sm transition-colors ${activeDragItem?.source?.type === 'waiting' ? 'border-[#4a6b5a] bg-[#e5ebe7] text-[#4a6b5a]' : 'border-gray-300 text-gray-400'}`}>
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
              <DroppableSlot id="slot-waiting-list" data={{ slotType: 'waiting-list' }} isDragActive={!!activeDragItem}>
                {waitingPlayers.length > 0 ? (
                  <div className="space-y-2">
                    {sortPlayersByRank(waitingPlayers).map((player) => (
                      <div key={player.id} className="flex items-center gap-2 bg-white rounded-lg px-3 py-2">
                        <DraggablePlayerChip
                          id={`waiting-${player.id}`}
                          data={{ playerId: player.id, playerName: player.name, kyuRank: player.kyuRank || participants.find(p => p.id === player.id)?.kyuRank, source: { type: 'waiting' } }}
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
                        const names = getWonParticipantNamesForMatch(currentSession, matchNumber);
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
                  disabled={loading || pairings.some(p => !p.hasResult && (!p.player1Id || !p.player2Id))}
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

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                選手を選択
              </label>
              <input
                type="text"
                value={playerSearchText}
                onChange={(e) => {
                  setPlayerSearchText(e.target.value);
                  setSelectedPlayerId('');
                }}
                placeholder="選手名を入力して検索..."
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                autoFocus
              />
              <div className="mt-2 max-h-48 overflow-y-auto border border-gray-200 rounded-lg">
                {availablePlayers
                  .filter(player => player.name.includes(playerSearchText))
                  .map((player) => (
                    <button
                      key={player.id}
                      type="button"
                      onClick={() => {
                        setSelectedPlayerId(String(player.id));
                        setPlayerSearchText(player.name);
                      }}
                      className={`w-full text-left px-4 py-2 text-sm hover:bg-[#e8f0eb] transition-colors ${
                        String(player.id) === selectedPlayerId ? 'bg-[#e8f0eb] font-semibold text-[#4a6b5a]' : 'text-gray-700'
                      }`}
                    >
                      {player.name} ({player.kyuRank || player.danRank || '初心者'})
                    </button>
                  ))}
                {availablePlayers.filter(player => player.name.includes(playerSearchText)).length === 0 && (
                  <div className="px-4 py-3 text-sm text-gray-400 text-center">
                    該当する選手がいません
                  </div>
                )}
              </div>
            </div>

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
                  setPlayerSearchText('');
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
  );
};

export default PairingGenerator;
