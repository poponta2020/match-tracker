import { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { playerAPI } from '../../api/players';
import { byeActivityAPI } from '../../api/byeActivities';
import { Link } from 'react-router-dom';
import { AlertCircle, Users, Shuffle, Trash2, Calendar, Check, Plus, UserPlus, RefreshCw, ChevronDown, ChevronUp, Pencil, FileText } from 'lucide-react';
import { sortPlayersByRank } from '../../utils/playerSort';
import PlayerChip from '../../components/PlayerChip';


const PairingGenerator = () => {
  const [searchParams] = useSearchParams();
  // URLパラメータの日付があればそれを使用、なければ今日
  const today = new Date().toISOString().split('T')[0];
  const [sessionDate, setSessionDate] = useState(searchParams.get('date') || today);
  const [matchNumber, setMatchNumber] = useState(1);
  const [participants, setParticipants] = useState([]);
  const [pairings, setPairings] = useState([]);
  const [waitingPlayers, setWaitingPlayers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
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

  const ACTIVITY_TYPES = [
    { value: 'READING', label: '読み' },
    { value: 'SOLO_PICK', label: '一人取り' },
    { value: 'OBSERVING', label: '見学' },
    { value: 'ASSIST_OBSERVING', label: '見学対応' },
    { value: 'OTHER', label: 'その他' },
  ];

  // 各試合番号の組み合わせデータキャッシュ
  const pairingsCache = useRef({}); // matchNumber -> pairings array or null
  const fetchIdRef = useRef(0); // stale fetch防止用
  const unsavedDraft = useRef(null); // 未保存の編集中データ { matchNumber, pairings, waitingPlayers, isEditingExisting }

  // 現在の試合が閲覧専用か（他の試合に未保存の変更がある場合）
  const isReadOnly = hasUnsavedChanges && unsavedDraft.current?.matchNumber !== matchNumber;

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
    } catch (e) {
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
      player1Id: p.player1Id,
      player1Name: p.player1Name,
      player2Id: p.player2Id,
      player2Name: p.player2Name,
      recentMatches: p.recentMatches || [],
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

  // 試合番号に対応する参加者をセッションデータから取得してstateに反映
  const updateParticipantsForMatch = useCallback((session, matchNum) => {
    if (!session) {
      setParticipants([]);
      return;
    }
    // matchParticipants: { "1" -> [{name, kyuRank}, ...], "2" -> [...] }
    // JSONのキーは文字列なのでString()で変換
    const matchParticipantEntries = session.matchParticipants?.[String(matchNum)] || [];
    const matchParticipantNames = matchParticipantEntries.map(p => typeof p === 'string' ? p : p.name);
    const sessionParticipants = session.participants || [];
    // 試合番号ごとの登録参加者のみを表示（0人なら0人）
    const filtered = sessionParticipants.filter(p => matchParticipantNames.includes(p.name));
    setParticipants(filtered);
  }, []);

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
      const entries = currentSession.matchParticipants?.[String(matchNumber)] || [];
      const names = entries.map(p => typeof p === 'string' ? p : p.name);
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
  }, [matchNumber, cacheVersion, currentSession, loadExistingPairingsToState, updateParticipantsForMatch]);

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

    try {
      const participantIds = participants.map((p) => p.id);
      const response = await pairingAPI.autoMatch({
        sessionDate,
        matchNumber,
        participantIds,
      });

      setPairings(response.data.pairings);
      setWaitingPlayers(response.data.waitingPlayers);
      setHasUnsavedChanges(true);
      setIsViewMode(false);
      saveDraft(response.data.pairings, response.data.waitingPlayers, false);
    } catch (err) {
      console.error('Auto matching failed:', err);
      setError('自動組み合わせに失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (pairings.length === 0) {
      setError('保存する組み合わせがありません');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // 既存の組み合わせを編集していた場合は先に削除
      if (isEditingExisting) {
        await pairingAPI.deleteByDateAndMatchNumber(sessionDate, matchNumber);
      }

      const requests = pairings.map((p) => ({
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
    if (!window.confirm('既存の組み合わせを削除しますか？')) {
      return;
    }

    setLoading(true);
    setError('');

    try {
      await pairingAPI.deleteByDateAndMatchNumber(sessionDate, matchNumber);
      pairingsCache.current[matchNumber] = null;
      setMatchExistsMap(prev => ({ ...prev, [matchNumber]: false }));
      setPairings([]);
      setWaitingPlayers([]);
      setIsEditingExisting(false);
      setIsViewMode(false);
      setHasUnsavedChanges(false);
      unsavedDraft.current = null;
    } catch (err) {
      console.error('Delete failed:', err);
      setError('削除に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleRemovePair = (index) => {
    const newPairings = [...pairings];
    const removed = newPairings.splice(index, 1)[0];

    const newWaiting = [
      ...waitingPlayers,
      { id: removed.player1Id, name: removed.player1Name },
      { id: removed.player2Id, name: removed.player2Name },
    ];

    setPairings(newPairings);
    setWaitingPlayers(newWaiting);
    setHasUnsavedChanges(true);
    saveDraft(newPairings, newWaiting, isEditingExisting);
  };

  const handleSwapPlayer = (pairingIndex, playerPosition, newPlayerId) => {
    const newPairings = [...pairings];
    const pairing = newPairings[pairingIndex];

    // 現在の選手
    const oldPlayer = playerPosition === 1
      ? { id: pairing.player1Id, name: pairing.player1Name }
      : { id: pairing.player2Id, name: pairing.player2Name };

    // 選択された選手が待機リストにいるか確認
    const waitingPlayer = waitingPlayers.find((p) => p.id === newPlayerId);

    let updatedWaiting = waitingPlayers;
    if (waitingPlayer) {
      // 待機リストから選手を選んだ場合
      if (playerPosition === 1) {
        pairing.player1Id = waitingPlayer.id;
        pairing.player1Name = waitingPlayer.name;
      } else {
        pairing.player2Id = waitingPlayer.id;
        pairing.player2Name = waitingPlayer.name;
      }

      // 待機リストを更新
      updatedWaiting = waitingPlayers.filter((p) => p.id !== newPlayerId);
      updatedWaiting.push(oldPlayer);
      setWaitingPlayers(updatedWaiting);
    } else {
      // 他の組み合わせから選手を選んだ場合
      const otherPairingIndex = newPairings.findIndex(
        (p, idx) => idx !== pairingIndex && (p.player1Id === newPlayerId || p.player2Id === newPlayerId)
      );

      if (otherPairingIndex !== -1) {
        const otherPairing = newPairings[otherPairingIndex];

        // 選択された選手が相手の組み合わせのどちらか確認
        if (otherPairing.player1Id === newPlayerId) {
          // 入れ替え
          otherPairing.player1Id = oldPlayer.id;
          otherPairing.player1Name = oldPlayer.name;
        } else {
          otherPairing.player2Id = oldPlayer.id;
          otherPairing.player2Name = oldPlayer.name;
        }

        // 現在の組み合わせを更新
        if (playerPosition === 1) {
          pairing.player1Id = newPlayerId;
          pairing.player1Name = participants.find((p) => p.id === newPlayerId)?.name || '';
        } else {
          pairing.player2Id = newPlayerId;
          pairing.player2Name = participants.find((p) => p.id === newPlayerId)?.name || '';
        }
      }
    }

    // 変更されたペアのrecentMatchesを即座にクリア
    newPairings[pairingIndex] = { ...newPairings[pairingIndex], recentMatches: null };
    // 他の組み合わせから選手を入れ替えた場合、そちらもクリア
    const otherIdx = newPairings.findIndex(
      (p, idx) => idx !== pairingIndex && !waitingPlayer &&
        (p.player1Id === oldPlayer?.id || p.player2Id === oldPlayer?.id)
    );
    if (otherIdx !== -1) {
      newPairings[otherIdx] = { ...newPairings[otherIdx], recentMatches: null };
    }

    setPairings(newPairings);
    setHasUnsavedChanges(true);
    saveDraft(newPairings, updatedWaiting, isEditingExisting);

    // API呼び出しで対戦履歴を取得
    const updatedPairing = newPairings[pairingIndex];
    fetchPairHistory(pairingIndex, updatedPairing.player1Id, updatedPairing.player2Id);
    if (otherIdx !== -1) {
      const otherP = newPairings[otherIdx];
      fetchPairHistory(otherIdx, otherP.player1Id, otherP.player2Id);
    }
  };

  const handleAddPairing = () => {
    if (waitingPlayers.length < 2) {
      setError('組み合わせを作成するには2名以上の待機選手が必要です');
      return;
    }

    const newPairing = {
      player1Id: waitingPlayers[0].id,
      player1Name: waitingPlayers[0].name,
      player2Id: waitingPlayers[1].id,
      player2Name: waitingPlayers[1].name,
      recentMatches: null,
    };

    const newPairings = [...pairings, newPairing];
    const newWaiting = waitingPlayers.slice(2);
    setPairings(newPairings);
    setWaitingPlayers(newWaiting);
    setHasUnsavedChanges(true);
    saveDraft(newPairings, newWaiting, isEditingExisting);
    setError('');

    // 追加したペアの対戦履歴を取得
    fetchPairHistory(newPairings.length - 1, newPairing.player1Id, newPairing.player2Id);
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

  // 既に参加している選手を除外
  const availablePlayers = allPlayers.filter(player => {
    const isParticipant = participants.some(p => p.id === player.id);
    const isWaiting = waitingPlayers.some(p => p.id === player.id);
    const isInPairings = pairings.some(p => p.player1Id === player.id || p.player2Id === player.id);
    return !isParticipant && !isWaiting && !isInPairings;
  });

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

      {/* 参加者セクション（組み合わせ未作成時のみ表示） */}
      {pairings.length === 0 && <div className="bg-white rounded-lg shadow-sm overflow-hidden">
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

      {/* 自動組み合わせボタン（組み合わせ未生成時のみ表示、閲覧モードでは非表示） */}
      {!isReadOnly && sessionDate && participants.length > 0 && pairings.length === 0 && (
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

      {pairings.length > 0 && (
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
              <div key={index} className="px-3 py-2.5">
                {(isReadOnly || isViewMode) ? (
                  /* 閲覧モード */
                  <div className="flex items-center justify-center gap-3">
                    <span className="font-medium text-[#374151] text-sm">{pairing.player1Name}</span>
                    <span className="text-[#a5b4aa] text-xs">vs</span>
                    <span className="font-medium text-[#374151] text-sm">{pairing.player2Name}</span>
                  </div>
                ) : (
                  /* 編集モード: コンパクト1行 */
                  <div className="flex items-center gap-2">
                    <select
                      value={pairing.player1Id}
                      onChange={(e) => handleSwapPlayer(index, 1, Number(e.target.value))}
                      className="flex-1 min-w-0 px-2 py-1.5 text-sm border border-gray-200 rounded bg-[#f9f6f2] focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a]"
                    >
                      <option value={pairing.player1Id}>{pairing.player1Name}</option>
                      <optgroup label="待機中">
                        {waitingPlayers.map((player) => (
                          <option key={player.id} value={player.id}>{player.name}</option>
                        ))}
                      </optgroup>
                      <optgroup label="他の組み合わせ">
                        {pairings
                          .filter((_, idx) => idx !== index)
                          .flatMap((p) => [
                            { id: p.player1Id, name: p.player1Name },
                            { id: p.player2Id, name: p.player2Name },
                          ])
                          .filter((p) => p.id !== pairing.player1Id && p.id !== pairing.player2Id)
                          .map((player) => (
                            <option key={player.id} value={player.id}>{player.name}</option>
                          ))}
                      </optgroup>
                    </select>

                    <span className="text-[#a5b4aa] text-xs flex-shrink-0">vs</span>

                    <select
                      value={pairing.player2Id}
                      onChange={(e) => handleSwapPlayer(index, 2, Number(e.target.value))}
                      className="flex-1 min-w-0 px-2 py-1.5 text-sm border border-gray-200 rounded bg-[#f9f6f2] focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a]"
                    >
                      <option value={pairing.player2Id}>{pairing.player2Name}</option>
                      <optgroup label="待機中">
                        {waitingPlayers.map((player) => (
                          <option key={player.id} value={player.id}>{player.name}</option>
                        ))}
                      </optgroup>
                      <optgroup label="他の組み合わせ">
                        {pairings
                          .filter((_, idx) => idx !== index)
                          .flatMap((p) => [
                            { id: p.player1Id, name: p.player1Name },
                            { id: p.player2Id, name: p.player2Name },
                          ])
                          .filter((p) => p.id !== pairing.player1Id && p.id !== pairing.player2Id)
                          .map((player) => (
                            <option key={player.id} value={player.id}>{player.name}</option>
                          ))}
                      </optgroup>
                    </select>

                    <span className="text-xs text-[#6b7280] flex-shrink-0 w-12 text-right">
                      {pairing.recentMatches === null
                        ? <span className="text-gray-300">...</span>
                        : pairing.recentMatches && pairing.recentMatches.length > 0
                          ? pairing.recentMatches[0].matchDate.split('-').slice(1).join('/')
                          : <span className="text-[#4a6b5a]">初</span>
                      }
                    </span>

                    <button
                      onClick={() => handleRemovePair(index)}
                      className="text-[#d1d5db] hover:text-red-500 flex-shrink-0 transition-colors"
                      title="この組み合わせを削除"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>

          {!isReadOnly && !isViewMode && (
            <div className={`${waitingPlayers.length > 0 ? 'bg-yellow-50 border border-yellow-200' : 'bg-gray-50 border border-gray-200'} p-4 rounded-lg`}>
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-medium text-gray-900">
                  待機中の選手{waitingPlayers.length > 0 && `（${waitingPlayers.length}名）`}
                </h3>
                <div className="flex items-center gap-2">
                  {waitingPlayers.length >= 2 && (
                    <button
                      onClick={handleAddPairing}
                      className="flex items-center gap-1 text-sm bg-[#4a6b5a] text-white px-3 py-1 rounded hover:bg-[#3d5a4c]"
                    >
                      <Plus className="w-4 h-4" />
                      組み合わせを追加
                    </button>
                  )}
                  <button
                    onClick={() => { setShowAddPlayer(true); fetchPlayersIfNeeded(); }}
                    className="flex items-center gap-1 text-sm text-[#4a6b5a] border border-[#a5b4aa] px-3 py-1 rounded hover:bg-[#e5ebe7] transition-colors"
                  >
                    <UserPlus className="w-3.5 h-3.5" />
                    選手追加
                  </button>
                </div>
              </div>
              {waitingPlayers.length > 0 ? (
                <>
                  <div className="space-y-2">
                    {sortPlayersByRank(waitingPlayers).map((player) => (
                      <div key={player.id} className="flex items-center gap-2 bg-white rounded-lg px-3 py-2">
                        <PlayerChip
                          name={player.name}
                          kyuRank={player.kyuRank}
                          className="text-sm bg-[#f9f6f2] text-[#374151]"
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
                  <p className="text-xs text-gray-600 mt-2">
                    ※各組み合わせのドロップダウンから選手を入れ替えることができます
                  </p>
                </>
              ) : (
                <p className="text-xs text-[#6b7280]">待機中の選手はいません</p>
              )}
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
                        const names = currentSession.matchParticipants?.[String(matchNumber)] || [];
                        const allP = currentSession.participants || [];
                        return names.length > 0 ? allP.filter(p => names.includes(p.name)) : allP;
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
                  disabled={loading}
                  className="flex items-center gap-2 bg-[#1A3654] text-white px-8 py-3 rounded-lg hover:bg-[#122740] transition-colors disabled:bg-gray-400 font-medium text-lg shadow-md"
                >
                  <Check className="w-5 h-5" />
                  {loading ? '保存中...' : '確定して保存'}
                </button>
              </div>
            </div>
          )}
        </div>
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
              <select
                value={selectedPlayerId}
                onChange={(e) => setSelectedPlayerId(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
              >
                <option value="">選手を選択してください</option>
                {availablePlayers.map((player) => (
                  <option key={player.id} value={player.id}>
                    {player.name} ({player.kyuRank || player.danRank || '初心者'})
                  </option>
                ))}
              </select>
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
