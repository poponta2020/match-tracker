import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { pairingAPI } from '../../api/pairings';
import { practiceAPI } from '../../api/practices';
import { playerAPI } from '../../api/players';
import { AlertCircle, Users, Shuffle, Trash2, Calendar, Check, Plus, UserPlus } from 'lucide-react';

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
  const [existingPairings, setExistingPairings] = useState(null);
  const [allPlayers, setAllPlayers] = useState([]);
  const [showAddPlayer, setShowAddPlayer] = useState(false);
  const [selectedPlayerId, setSelectedPlayerId] = useState('');
  const [currentSession, setCurrentSession] = useState(null);

  useEffect(() => {
    const fetchParticipants = async () => {
      if (!sessionDate) return;

      try {
        const response = await practiceAPI.getByDate(sessionDate);
        if (response.data) {
          setCurrentSession(response.data);
          const participantsRes = await practiceAPI.getParticipants(response.data.id);
          setParticipants(participantsRes.data);
        } else {
          setCurrentSession(null);
          setParticipants([]);
        }

        const existsRes = await pairingAPI.exists(sessionDate, matchNumber);
        if (existsRes.data) {
          const pairingsRes = await pairingAPI.getByDateAndMatchNumber(sessionDate, matchNumber);
          setExistingPairings(pairingsRes.data);
        } else {
          setExistingPairings(null);
        }
      } catch (err) {
        console.error('Failed to fetch participants:', err);
        // 404エラー(練習会が存在しない)の場合は空配列を設定
        if (err.response && err.response.status === 404) {
          setCurrentSession(null);
          setParticipants([]);
          setError('');
        } else {
          setError('Failed to fetch participants');
        }
      }
    };

    fetchParticipants();
  }, [sessionDate, matchNumber]);

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
      const requests = pairings.map((p) => ({
        player1Id: p.player1Id,
        player2Id: p.player2Id,
      }));

      await pairingAPI.createBatch(sessionDate, matchNumber, requests);
      alert('組み合わせを保存しました');

      const pairingsRes = await pairingAPI.getByDateAndMatchNumber(sessionDate, matchNumber);
      setExistingPairings(pairingsRes.data);

      // 次の試合番号に自動遷移
      const nextMatchNumber = matchNumber + 1;
      const maxMatches = currentSession?.totalMatches || 10;

      if (nextMatchNumber <= maxMatches) {
        // 試合番号を更新（useEffectが再実行される）
        setMatchNumber(nextMatchNumber);
        // 画面をクリア
        setPairings([]);
        setWaitingPlayers([]);
      } else {
        // 最終試合の場合は一括入力画面に遷移
        if (currentSession && currentSession.id) {
          navigate(`/matches/bulk-input/${currentSession.id}`);
        }
      }
    } catch (err) {
      console.error('Save failed:', err);
      setError('保存に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteExisting = async () => {
    if (!window.confirm('既存の組み合わせを削除しますか?')) {
      return;
    }

    setLoading(true);
    setError('');

    try {
      await pairingAPI.deleteByDateAndMatchNumber(sessionDate, matchNumber);
      setExistingPairings(null);
      alert('既存の組み合わせを削除しました');
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

    setWaitingPlayers([
      ...waitingPlayers,
      { id: removed.player1Id, name: removed.player1Name },
      { id: removed.player2Id, name: removed.player2Name },
    ]);

    setPairings(newPairings);
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
      const newWaitingPlayers = waitingPlayers.filter((p) => p.id !== newPlayerId);
      newWaitingPlayers.push(oldPlayer);
      setWaitingPlayers(newWaitingPlayers);
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

    setPairings([...pairings, newPairing]);
    setWaitingPlayers(waitingPlayers.slice(2));
    setError('');
  };

  const handleAddPlayer = () => {
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

    // 待機リストに追加
    setWaitingPlayers([...waitingPlayers, { id: player.id, name: player.name }]);
    setParticipants([...participants, player]);
    setSelectedPlayerId('');
    setShowAddPlayer(false);
    setError('');
  };

  // 既に参加している選手を除外
  const availablePlayers = allPlayers.filter(player => {
    const isParticipant = participants.some(p => p.id === player.id);
    const isWaiting = waitingPlayers.some(p => p.id === player.id);
    const isInPairings = pairings.some(p => p.player1Id === player.id || p.player2Id === player.id);
    return !isParticipant && !isWaiting && !isInPairings;
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
          <Users className="w-8 h-8 text-primary-600" />
          対戦組み合わせ生成
        </h1>
        <p className="text-gray-600 mt-1">参加者から自動的に対戦組み合わせを生成します</p>
      </div>

      <div className="bg-white p-6 rounded-lg shadow-sm space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              <Calendar className="w-4 h-4 inline mr-1" />
              日付
            </label>
            <div className="flex gap-2">
              <input
                type="date"
                value={sessionDate}
                onChange={(e) => setSessionDate(e.target.value)}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
              <button
                onClick={() => setSessionDate(today)}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
              >
                今日
              </button>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              試合番号
            </label>
            <select
              value={matchNumber}
              onChange={(e) => setMatchNumber(Number(e.target.value))}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            >
              {Array.from(
                { length: currentSession?.totalMatches || 10 },
                (_, i) => i + 1
              ).map((num) => (
                <option key={num} value={num}>
                  試合 {num}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="bg-blue-50 p-4 rounded-lg flex items-center justify-between">
          <p className="text-blue-900 font-medium">
            参加者: {participants.length}名
            {participants.length === 0 && (
              <span className="text-sm text-blue-700 ml-2">
                (事前登録なし - 当日参加者を追加してください)
              </span>
            )}
          </p>
          <button
            onClick={() => setShowAddPlayer(true)}
            className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm"
          >
            <UserPlus className="w-4 h-4" />
            当日参加者を追加
          </button>
        </div>

        {existingPairings && (
          <div className="bg-yellow-50 border border-yellow-200 p-4 rounded-lg flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-yellow-900 font-medium mb-2">
                この試合の組み合わせは既に存在します
              </p>
              <div className="flex gap-2">
                <button
                  onClick={handleDeleteExisting}
                  className="text-sm text-red-700 hover:text-red-900 underline"
                  disabled={loading}
                >
                  削除して再作成
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {sessionDate && participants.length > 0 && (
        <div className="flex justify-center">
          <button
            onClick={handleAutoMatch}
            disabled={loading}
            className="flex items-center gap-2 bg-primary-600 text-white px-8 py-3 rounded-lg hover:bg-primary-700 transition-colors disabled:bg-gray-400 text-lg font-medium"
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
            <h2 className="text-xl font-bold text-gray-900">生成された組み合わせ</h2>
            <div className="text-sm text-gray-600">
              組み合わせを編集後、確定ボタンで保存してください
            </div>
          </div>

          <div className="space-y-3">
            {pairings.map((pairing, index) => (
              <div key={index} className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-medium text-gray-900">組み合わせ {index + 1}</h3>
                  <button
                    onClick={() => handleRemovePair(index)}
                    className="text-red-600 hover:text-red-700 p-1 flex items-center gap-1"
                    title="この組み合わせを削除"
                  >
                    <Trash2 className="w-4 h-4" />
                    <span className="text-xs">削除</span>
                  </button>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="bg-gray-50 p-3 rounded">
                    <p className="text-sm text-gray-600 mb-2">選手1</p>
                    <select
                      value={pairing.player1Id}
                      onChange={(e) => handleSwapPlayer(index, 1, Number(e.target.value))}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
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
                    <p className="text-sm text-gray-600 mb-2">選手2</p>
                    <select
                      value={pairing.player2Id}
                      onChange={(e) => handleSwapPlayer(index, 2, Number(e.target.value))}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
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

                {pairing.recentMatches && pairing.recentMatches.length > 0 && (
                  <div className="mt-3 text-sm text-gray-600">
                    <p className="font-medium mb-1">過去30日間の対戦履歴:</p>
                    <ul className="list-disc list-inside space-y-1">
                      {pairing.recentMatches.map((match, idx) => (
                        <li key={idx}>
                          {match.matchDate} ({match.daysAgo}日前)
                        </li>
                      ))}
                    </ul>
                    <p className="mt-1 text-xs text-gray-500">
                      スコア: {pairing.score?.toFixed(1)} ポイント
                    </p>
                  </div>
                )}

                {pairing.recentMatches && pairing.recentMatches.length === 0 && (
                  <div className="mt-3 text-sm text-green-600 font-medium">
                    初対戦
                  </div>
                )}
              </div>
            ))}
          </div>

          {waitingPlayers.length > 0 && (
            <div className="bg-yellow-50 border border-yellow-200 p-4 rounded-lg">
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-medium text-gray-900">待機中の選手</h3>
                {waitingPlayers.length >= 2 && (
                  <button
                    onClick={handleAddPairing}
                    className="flex items-center gap-1 text-sm bg-primary-600 text-white px-3 py-1 rounded hover:bg-primary-700"
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

          <div className="flex justify-end gap-3">
            <button
              onClick={handleSave}
              disabled={loading}
              className="flex items-center gap-2 bg-green-600 text-white px-8 py-3 rounded-lg hover:bg-green-700 transition-colors disabled:bg-gray-400 font-medium text-lg shadow-md"
            >
              <Check className="w-5 h-5" />
              {loading ? '保存中...' : '確定して保存'}
            </button>
          </div>
        </div>
      )}

      {/* 選手追加モーダル */}
      {showAddPlayer && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4 shadow-xl">
            <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
              <UserPlus className="w-6 h-6 text-primary-600" />
              当日参加者を追加
            </h2>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                選手を選択
              </label>
              <select
                value={selectedPlayerId}
                onChange={(e) => setSelectedPlayerId(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
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
                className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors flex items-center gap-2"
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
