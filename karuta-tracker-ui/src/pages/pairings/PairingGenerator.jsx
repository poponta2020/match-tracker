import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { playerAPI } from '../../api/players';
import { AlertCircle, Users, Shuffle, Trash2, Calendar, Check, Plus, UserPlus, RefreshCw, X, ChevronDown, ChevronUp } from 'lucide-react';
import { isAdmin } from '../../utils/auth';

const PairingGenerator = () => {
  const navigate = useNavigate();
  // デフォルトを今日に設定
  const today = new Date().toISOString().split('T')[0];
  const [sessionDate, setSessionDate] = useState(today);
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
  const [syncing, setSyncing] = useState(false);
  const [showUrlModal, setShowUrlModal] = useState(false);
  const [densukeUrlInput, setDensukeUrlInput] = useState('');
  const [syncMessage, setSyncMessage] = useState(null);
  const [unmatchedNames, setUnmatchedNames] = useState([]);
  const [showUnmatchedModal, setShowUnmatchedModal] = useState(false);
  const [removedPlayers, setRemovedPlayers] = useState([]);
  const [showRemovedModal, setShowRemovedModal] = useState(false);
  const [showParticipantList, setShowParticipantList] = useState(true);
  const [matchExistsMap, setMatchExistsMap] = useState({});
  const [isEditingExisting, setIsEditingExisting] = useState(false);
  const [matchLoading, setMatchLoading] = useState(true);
  const [cacheVersion, setCacheVersion] = useState(0); // キャッシュ更新トリガー
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false); // 未保存の組み合わせがあるか

  // 各試合番号の組み合わせデータキャッシュ
  const pairingsCache = useRef({}); // matchNumber -> pairings array or null
  const fetchIdRef = useRef(0); // stale fetch防止用
  const unsavedDraft = useRef(null); // 未保存の編集中データ { matchNumber, pairings, waitingPlayers, isEditingExisting }

  // 現在の試合が閲覧専用か（他の試合に未保存の変更がある場合）
  const isReadOnly = hasUnsavedChanges && unsavedDraft.current?.matchNumber !== matchNumber;

  // 未保存ドラフトを保存するヘルパー
  const saveDraft = useCallback((newPairings, newWaitingPlayers, editing) => {
    unsavedDraft.current = {
      matchNumber,
      pairings: newPairings,
      waitingPlayers: newWaitingPlayers,
      isEditingExisting: editing,
    };
  }, [matchNumber]);

  // マウント時にlocalStorageから同期結果を復元
  useEffect(() => {
    try {
      const saved = localStorage.getItem('densukeSyncResult');
      if (saved) {
        const { page, data } = JSON.parse(saved);
        if (page === '/pairings') {
          localStorage.removeItem('densukeSyncResult');
          applySyncResult(data);
        }
      }
    } catch (e) {
      localStorage.removeItem('densukeSyncResult');
    }
  }, []);

  // 既存の組み合わせデータを編集可能な形式に変換してstateにロード
  const loadExistingPairingsToState = useCallback((existingData) => {
    if (!existingData || existingData.length === 0) {
      setPairings([]);
      setWaitingPlayers([]);
      setIsEditingExisting(false);
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
    setWaitingPlayers([]);
    setIsEditingExisting(true);
  }, []);

  // 試合番号に対応する参加者をセッションデータから取得してstateに反映
  const updateParticipantsForMatch = useCallback((session, matchNum) => {
    if (!session) {
      setParticipants([]);
      return;
    }
    // matchParticipants: { "1" -> [playerName, ...], "2" -> [...] }
    // JSONのキーは文字列なのでString()で変換
    const matchParticipantNames = session.matchParticipants?.[String(matchNum)] || [];
    const sessionParticipants = session.participants || [];
    if (matchParticipantNames.length > 0) {
      // 名前ベースでフィルタ
      const filtered = sessionParticipants.filter(p => matchParticipantNames.includes(p.name));
      setParticipants(filtered);
    } else {
      // matchParticipantsが空の場合はセッション全体の参加者を使う（後方互換）
      setParticipants(sessionParticipants);
    }
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
        const response = await practiceAPI.getByDate(sessionDate);
        // staleチェック: 後から別のfetchが走っていたらこの結果は無視
        if (fetchIdRef.current !== thisId) return;

        if (response.data) {
          const session = response.data;
          setCurrentSession(session);

          // 全試合番号の組み合わせを並列で一括取得
          const totalMatches = session.totalMatches || 10;
          const fetchPromises = Array.from({ length: totalMatches }, (_, i) => i + 1).map(async (num) => {
            try {
              const existsRes = await pairingAPI.exists(sessionDate, num);
              if (existsRes.data) {
                const pairingsRes = await pairingAPI.getByDateAndMatchNumber(sessionDate, num);
                return { num, exists: true, data: pairingsRes.data };
              }
              return { num, exists: false, data: null };
            } catch {
              return { num, exists: false, data: null };
            }
          });

          const results = await Promise.all(fetchPromises);
          // staleチェック
          if (fetchIdRef.current !== thisId) return;

          const newExistsMap = {};
          results.forEach(({ num, exists, data }) => {
            newExistsMap[num] = exists;
            pairingsCache.current[num] = data;
          });
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
    // 参加者を試合番号に応じて切替
    updateParticipantsForMatch(currentSession, matchNumber);

    // 未保存ドラフトの試合に戻ってきた場合はドラフトを復元
    if (unsavedDraft.current && unsavedDraft.current.matchNumber === matchNumber) {
      setPairings(unsavedDraft.current.pairings);
      setWaitingPlayers(unsavedDraft.current.waitingPlayers);
      setIsEditingExisting(unsavedDraft.current.isEditingExisting);
      return;
    }

    // 組み合わせをキャッシュから反映
    const cached = pairingsCache.current[matchNumber];
    if (cached !== undefined) {
      if (cached) {
        loadExistingPairingsToState(cached);
      } else {
        setPairings([]);
        setWaitingPlayers([]);
        setIsEditingExisting(false);
      }
    } else {
      // キャッシュ未取得（初回ロード中等）の場合はクリア
      setPairings([]);
      setWaitingPlayers([]);
      setIsEditingExisting(false);
    }
  }, [matchNumber, cacheVersion, currentSession, loadExistingPairingsToState, updateParticipantsForMatch]);

  useEffect(() => {
    const fetchAllPlayers = async () => {
      try {
        const response = await playerAPI.getAll();
        setAllPlayers(response.data);
      } catch (err) {
        console.error('Failed to fetch all players:', err);
      }
    };

    fetchAllPlayers();
  }, []);

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

      await pairingAPI.createBatch(sessionDate, matchNumber, requests);

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
        // 次の試合番号のキャッシュを確認して先にstateをセット
        const nextCached = pairingsCache.current[nextMatchNumber];
        if (nextCached) {
          loadExistingPairingsToState(nextCached);
        } else {
          setPairings([]);
          setWaitingPlayers([]);
          setIsEditingExisting(false);
        }
        // matchNumberを変更（useEffectが走るが、stateは既に正しい）
        setMatchNumber(nextMatchNumber);
      } else {
        // 最終試合の場合はLINE送信用テキスト画面に遷移
        navigate(`/pairings/summary?date=${sessionDate}`);
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

    setPairings(newPairings);
    setHasUnsavedChanges(true);
    saveDraft(newPairings, updatedWaiting, isEditingExisting);
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
      recentMatches: [],
    };

    const newPairings = [...pairings, newPairing];
    const newWaiting = waitingPlayers.slice(2);
    setPairings(newPairings);
    setWaitingPlayers(newWaiting);
    setHasUnsavedChanges(true);
    saveDraft(newPairings, newWaiting, isEditingExisting);
    setError('');
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

  // 同期結果をUIに反映（localStorage復元時にも使用）
  const applySyncResult = async (data) => {
    setSyncMessage(`同期完了: ${data.createdSessionCount}件作成, ${data.registeredCount}名登録`);
    // セッションを再取得して参加者リストを更新
    try {
      const sessionRes = await practiceAPI.getByDate(sessionDate);
      if (sessionRes.data) {
        setCurrentSession(sessionRes.data);
        updateParticipantsForMatch(sessionRes.data, matchNumber);
      }
    } catch (e) {
      // ignore
    }
    const hasUnmatched = data.unmatchedNames && data.unmatchedNames.length > 0;
    const hasRemoved = data.removedPlayers && data.removedPlayers.length > 0;
    if (hasUnmatched) {
      setUnmatchedNames(data.unmatchedNames);
      setShowUnmatchedModal(true);
    }
    if (hasRemoved) {
      setRemovedPlayers(data.removedPlayers);
      if (!hasUnmatched) {
        setShowRemovedModal(true);
      }
    }
    if (!hasUnmatched && !hasRemoved) {
      localStorage.removeItem('densukeSyncResult');
    }
  };

  // 同期結果を処理（共通） — UIに反映し、localStorageにも保存
  const handleSyncResult = async (result) => {
    const data = result.data;
    try {
      localStorage.setItem('densukeSyncResult', JSON.stringify({ page: '/pairings', data }));
    } catch (e) { /* ignore */ }
    await applySyncResult(data);
  };

  // 伝助同期
  const handleSyncDensuke = async () => {
    const date = new Date(sessionDate);
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    setSyncing(true);
    setSyncMessage(null);
    try {
      const urlRes = await practiceAPI.getDensukeUrl(year, month);
      if (!urlRes.data || !urlRes.data.url) {
        setSyncing(false);
        setShowUrlModal(true);
        return;
      }
      const result = await practiceAPI.syncDensuke(year, month);
      await handleSyncResult(result);
    } catch (err) {
      if (err.response?.status === 204) {
        setSyncing(false);
        setShowUrlModal(true);
        return;
      }
      setSyncMessage('同期に失敗しました');
      console.error('Densuke sync error:', err);
    } finally {
      setSyncing(false);
    }
  };

  const handleSaveUrlAndSync = async () => {
    const date = new Date(sessionDate);
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    if (!densukeUrlInput.trim()) return;
    try {
      await practiceAPI.saveDensukeUrl(year, month, densukeUrlInput.trim());
      setShowUrlModal(false);
      setDensukeUrlInput('');
      setSyncing(true);
      const result = await practiceAPI.syncDensuke(year, month);
      await handleSyncResult(result);
    } catch (err) {
      setSyncMessage('同期に失敗しました');
      console.error('Densuke save/sync error:', err);
    } finally {
      setSyncing(false);
    }
  };

  // 未登録者を登録して再同期
  const handleRegisterAndSync = async () => {
    const date = new Date(sessionDate);
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    setSyncing(true);
    setShowUnmatchedModal(false);
    try {
      const result = await practiceAPI.registerAndSyncDensuke(unmatchedNames, year, month);
      await handleSyncResult(result);
      setUnmatchedNames([]);
    } catch (err) {
      setSyncMessage('登録・同期に失敗しました');
      console.error('Register and sync error:', err);
    } finally {
      setSyncing(false);
    }
  };

  // 未登録者モーダルを閉じた後に消えた参加者モーダルを表示
  const handleCloseUnmatchedModal = () => {
    setShowUnmatchedModal(false);
    if (removedPlayers.length > 0) {
      setShowRemovedModal(true);
    } else {
      localStorage.removeItem('densukeSyncResult');
    }
  };

  // 参加者を試合から外す
  const handleRemoveParticipant = async (player) => {
    try {
      await practiceAPI.removeParticipantFromMatch(player.sessionId, player.matchNumber, player.playerId);
      setRemovedPlayers(prev => prev.filter(p =>
        !(p.playerId === player.playerId && p.sessionId === player.sessionId && p.matchNumber === player.matchNumber)
      ));
      // セッションを再取得して参加者リストを更新
      const sessionRes = await practiceAPI.getByDate(sessionDate);
      if (sessionRes.data) {
        setCurrentSession(sessionRes.data);
        updateParticipantsForMatch(sessionRes.data, matchNumber);
      }
    } catch (err) {
      console.error('Remove participant error:', err);
      setSyncMessage('参加者の削除に失敗しました');
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
                    ? 'bg-[#4a6b5a] text-white shadow-md'
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

        {syncMessage && (
          <div className="bg-[#e5ebe7] border border-[#a5b4aa] p-3 rounded-lg flex justify-between items-center text-sm text-[#374151]">
            <span>{syncMessage}</span>
            <button onClick={() => setSyncMessage(null)} className="text-[#6b7280] hover:text-[#374151]">
              <X size={16} />
            </button>
          </div>
        )}
      </div>

      {/* 参加者セクション */}
      <div className="bg-white rounded-lg shadow-sm overflow-hidden">
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
            {isAdmin() && (
              <button
                onClick={handleSyncDensuke}
                disabled={syncing}
                className="flex items-center gap-1.5 bg-white text-[#4a6b5a] border border-[#a5b4aa] px-3 py-1.5 rounded-lg hover:bg-[#f9f6f2] transition-colors text-sm disabled:opacity-50"
              >
                <RefreshCw className={`w-4 h-4 ${syncing ? 'animate-spin' : ''}`} />
                {syncing ? '同期中...' : '伝助同期'}
              </button>
            )}
            <button
              onClick={() => setShowAddPlayer(true)}
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
                {participants.map((p) => (
                  <span
                    key={p.id}
                    className="inline-flex items-center px-3 py-1.5 rounded-full text-sm bg-[#f9f6f2] border border-[#d4ddd7] text-[#374151]"
                  >
                    {p.name}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-sm text-[#6b7280]">
                事前登録なし - 伝助同期または当日参加者を追加してください
              </p>
            )}
          </div>
        )}
      </div>

      {/* 自動組み合わせボタン（組み合わせ未生成時のみ表示、閲覧モードでは非表示） */}
      {!isReadOnly && sessionDate && participants.length > 0 && pairings.length === 0 && (
        <div className="flex justify-center">
          <button
            onClick={handleAutoMatch}
            disabled={loading}
            className="flex items-center gap-2 bg-[#4a6b5a] text-white px-8 py-3 rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:bg-gray-400 text-lg font-medium shadow-md"
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
            {!isReadOnly && (
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
                <button
                  onClick={handleAutoMatch}
                  disabled={loading}
                  className="text-sm text-[#4a6b5a] hover:text-[#3d5a4c] flex items-center gap-1"
                >
                  <Shuffle className="w-3.5 h-3.5" />
                  再生成
                </button>
              </div>
            )}
          </div>

          <div className="space-y-3">
            {pairings.map((pairing, index) => (
              <div key={index} className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
                {isReadOnly ? (
                  /* 閲覧モード: シンプルな表示 */
                  <div className="flex items-center justify-center gap-4 py-2">
                    <span className="font-medium text-[#374151]">{pairing.player1Name}</span>
                    <span className="text-[#6b7280] text-sm">vs</span>
                    <span className="font-medium text-[#374151]">{pairing.player2Name}</span>
                  </div>
                ) : (
                  /* 編集モード */
                  <>
                    <div className="grid grid-cols-2 gap-4 mb-3">
                      <div className="bg-gray-50 p-3 rounded">
                        <select
                          value={pairing.player1Id}
                          onChange={(e) => handleSwapPlayer(index, 1, Number(e.target.value))}
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                        >
                          <option value={pairing.player1Id}>{pairing.player1Name}</option>
                          <optgroup label="待機中の選手">
                            {waitingPlayers.map((player) => (
                              <option key={player.id} value={player.id}>
                                {player.name}
                              </option>
                            ))}
                          </optgroup>
                          <optgroup label="他の組み合わせの選手">
                            {pairings
                              .filter((_, idx) => idx !== index)
                              .flatMap((p) => [
                                { id: p.player1Id, name: p.player1Name },
                                { id: p.player2Id, name: p.player2Name },
                              ])
                              .filter((p) => p.id !== pairing.player1Id && p.id !== pairing.player2Id)
                              .map((player) => (
                                <option key={player.id} value={player.id}>
                                  {player.name}
                                </option>
                              ))}
                          </optgroup>
                        </select>
                      </div>
                      <div className="bg-gray-50 p-3 rounded">
                        <select
                          value={pairing.player2Id}
                          onChange={(e) => handleSwapPlayer(index, 2, Number(e.target.value))}
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                        >
                          <option value={pairing.player2Id}>{pairing.player2Name}</option>
                          <optgroup label="待機中の選手">
                            {waitingPlayers.map((player) => (
                              <option key={player.id} value={player.id}>
                                {player.name}
                              </option>
                            ))}
                          </optgroup>
                          <optgroup label="他の組み合わせの選手">
                            {pairings
                              .filter((_, idx) => idx !== index)
                              .flatMap((p) => [
                                { id: p.player1Id, name: p.player1Name },
                                { id: p.player2Id, name: p.player2Name },
                              ])
                              .filter((p) => p.id !== pairing.player1Id && p.id !== pairing.player2Id)
                              .map((player) => (
                                <option key={player.id} value={player.id}>
                                  {player.name}
                                </option>
                              ))}
                          </optgroup>
                        </select>
                      </div>
                    </div>

                    <div className="flex items-center justify-between">
                      <div className="text-sm text-gray-600">
                        {pairing.recentMatches && pairing.recentMatches.length > 0 ? (
                          <span>直近の試合：{pairing.recentMatches[0].matchDate.split('-').slice(1).join('/')}</span>
                        ) : (
                          <span className="text-green-600 font-medium">初対戦</span>
                        )}
                      </div>
                      <button
                        onClick={() => handleRemovePair(index)}
                        className="text-red-600 hover:text-red-700 p-1 flex items-center gap-1"
                        title="この組み合わせを削除"
                      >
                        <Trash2 className="w-4 h-4" />
                        <span className="text-xs">削除</span>
                      </button>
                    </div>
                  </>
                )}
              </div>
            ))}
          </div>

          {!isReadOnly && waitingPlayers.length > 0 && (
            <div className="bg-yellow-50 border border-yellow-200 p-4 rounded-lg">
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-medium text-gray-900">待機中の選手</h3>
                {waitingPlayers.length >= 2 && (
                  <button
                    onClick={handleAddPairing}
                    className="flex items-center gap-1 text-sm bg-[#4a6b5a] text-white px-3 py-1 rounded hover:bg-[#3d5a4c]"
                  >
                    <Plus className="w-4 h-4" />
                    組み合わせを追加
                  </button>
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {waitingPlayers.map((player) => (
                  <span
                    key={player.id}
                    className="bg-white px-3 py-1 rounded-full border border-yellow-300 text-sm"
                  >
                    {player.name}
                  </span>
                ))}
              </div>
              <p className="text-xs text-gray-600 mt-2">
                ※各組み合わせのドロップダウンから選手を入れ替えることができます
              </p>
            </div>
          )}

          {isReadOnly ? (
            <div className="bg-[#fef3c7] border border-[#fbbf24] p-3 rounded-lg text-center">
              <p className="text-sm text-[#92400e]">
                第{unsavedDraft.current?.matchNumber}試合に未保存の組み合わせがあります
              </p>
            </div>
          ) : (
            <div className="space-y-2">
              {hasUnsavedChanges && (
                <p className="text-xs text-[#b45309] text-center">
                  保存するまで他の試合の編集はできません
                </p>
              )}
              <div className="flex justify-end gap-3">
                <button
                  onClick={handleSave}
                  disabled={loading}
                  className="flex items-center gap-2 bg-[#4a6b5a] text-white px-8 py-3 rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:bg-gray-400 font-medium text-lg shadow-md"
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
                    {player.name} ({player.kyuRank || player.danRank || '未設定'})
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
      {/* 伝助URL入力モーダル */}
      {showUrlModal && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setShowUrlModal(false)}
        >
          <div
            className="bg-white rounded-lg p-6 max-w-md w-full mx-4 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="text-lg font-bold text-gray-900 mb-4">
              {(() => { const d = new Date(sessionDate); return `${d.getFullYear()}年${d.getMonth() + 1}月`; })()}の伝助URL
            </h2>
            <p className="text-sm text-gray-600 mb-3">この月の伝助URLを入力してください</p>
            <input
              type="url"
              value={densukeUrlInput}
              onChange={(e) => setDensukeUrlInput(e.target.value)}
              placeholder="https://densuke.biz/list?cd=..."
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#4a6b5a] mb-4"
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowUrlModal(false)}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
              >
                キャンセル
              </button>
              <button
                onClick={handleSaveUrlAndSync}
                disabled={!densukeUrlInput.trim()}
                className="px-4 py-2 text-white bg-[#4a6b5a] rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:opacity-50"
              >
                保存して同期
              </button>
            </div>
          </div>
        </div>
      )}
      {/* 未登録者確認モーダル */}
      {showUnmatchedModal && unmatchedNames.length > 0 && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={handleCloseUnmatchedModal}
        >
          <div
            className="bg-white rounded-lg p-6 max-w-md w-full mx-4 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex justify-between items-start mb-4">
              <h2 className="text-lg font-bold text-gray-900">未登録のユーザーがいます</h2>
              <button onClick={handleCloseUnmatchedModal} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>
            <p className="text-sm text-gray-600 mb-3">
              以下の{unmatchedNames.length}名がアプリに未登録です。登録しますか？
            </p>
            <div className="flex flex-wrap gap-2 mb-3">
              {unmatchedNames.map((name, idx) => (
                <span key={idx} className="text-sm text-gray-800 bg-gray-100 px-3 py-1 rounded-full">
                  {name}
                </span>
              ))}
            </div>
            <p className="text-xs text-gray-500 mb-4">
              パスワード: pppppppp / 性別: その他 で登録されます
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={handleCloseUnmatchedModal}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
              >
                スキップ
              </button>
              <button
                onClick={handleRegisterAndSync}
                className="px-4 py-2 text-white bg-[#4a6b5a] rounded-lg hover:bg-[#3d5a4c] transition-colors"
              >
                登録して同期
              </button>
            </div>
          </div>
        </div>
      )}
      {/* 伝助から消えた参加者モーダル */}
      {showRemovedModal && removedPlayers.length > 0 && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setShowRemovedModal(false)}
        >
          <div
            className="bg-white rounded-lg p-6 max-w-md w-full mx-4 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex justify-between items-start mb-4">
              <h2 className="text-lg font-bold text-gray-900">伝助から消えた参加者</h2>
              <button onClick={() => setShowRemovedModal(false)} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>
            <p className="text-sm text-gray-600 mb-3">
              以下の参加者が伝助上に見つかりません。参加を外しますか？
            </p>
            <div className="space-y-2 mb-4">
              {removedPlayers.map((p, idx) => (
                <div key={idx} className="flex items-center justify-between bg-gray-50 px-3 py-2 rounded-lg">
                  <div>
                    <span className="text-sm font-medium text-gray-900">{p.playerName}</span>
                    <span className="text-xs text-gray-500 ml-2">
                      {p.sessionDate} 第{p.matchNumber}試合
                    </span>
                  </div>
                  <button
                    onClick={() => handleRemoveParticipant(p)}
                    className="text-xs text-red-600 border border-red-400 px-2 py-1 rounded hover:bg-red-600 hover:text-white transition-colors"
                  >
                    外す
                  </button>
                </div>
              ))}
            </div>
            <button
              onClick={() => { setShowRemovedModal(false); setRemovedPlayers([]); localStorage.removeItem('densukeSyncResult'); }}
              className="w-full py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
            >
              閉じる
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default PairingGenerator;
