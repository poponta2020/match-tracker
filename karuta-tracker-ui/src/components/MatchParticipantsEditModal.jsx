import { useState, useEffect } from 'react';
import { X } from 'lucide-react';
import { practiceAPI } from '../api';
import apiClient from '../api/client';

const MatchParticipantsEditModal = ({ session, matchNumber, onClose, onSave }) => {
  const [allPlayers, setAllPlayers] = useState([]);
  const [selectedPlayerIds, setSelectedPlayerIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchData();
  }, [session, matchNumber]);

  const fetchData = async () => {
    try {
      setLoading(true);
      // 全選手を取得
      const playersResponse = await apiClient.get('/players');
      setAllPlayers(playersResponse.data);

      // 現在の参加者を取得
      if (session?.matchParticipants) {
        // matchParticipantsは { "1": ["選手A", "選手B"], "2": [...] } の形式
        const participants = session.matchParticipants[matchNumber];
        if (participants && Array.isArray(participants)) {
          // 参加者名のリストから、allPlayersと照合してIDを取得
          // まず選手名からIDへのマップを作成
          const nameToIdMap = {};
          playersResponse.data.forEach(player => {
            nameToIdMap[player.name] = player.id;
          });

          // 参加者名をIDに変換
          const ids = participants
            .map(name => nameToIdMap[name])
            .filter(id => id !== undefined);
          setSelectedPlayerIds(ids);
        }
      }
    } catch (err) {
      setError('データの取得に失敗しました');
      console.error('Error fetching data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleTogglePlayer = (playerId) => {
    setSelectedPlayerIds(prev =>
      prev.includes(playerId)
        ? prev.filter(id => id !== playerId)
        : [...prev, playerId]
    );
  };

  const handleSelectAll = () => {
    setSelectedPlayerIds(allPlayers.map(p => p.id));
  };

  const handleDeselectAll = () => {
    setSelectedPlayerIds([]);
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      setError('');
      await practiceAPI.setMatchParticipants(session.id, matchNumber, selectedPlayerIds);
      onSave();
      onClose();
    } catch (err) {
      setError('保存に失敗しました');
      console.error('Error saving participants:', err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[80vh] overflow-hidden">
        {/* ヘッダー */}
        <div className="flex justify-between items-center p-4 border-b">
          <h2 className="text-xl font-bold">
            試合{matchNumber}の参加者編集
          </h2>
          <button
            onClick={onClose}
            className="p-1 hover:bg-gray-100 rounded"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* コンテンツ */}
        <div className="p-4 overflow-y-auto max-h-[60vh]">
          {error && (
            <div className="mb-4 p-3 bg-red-100 border border-red-400 text-red-700 rounded">
              {error}
            </div>
          )}

          {loading ? (
            <div className="text-center py-8">読み込み中...</div>
          ) : (
            <>
              {/* 一括選択ボタン */}
              <div className="mb-4 flex gap-2">
                <button
                  onClick={handleSelectAll}
                  className="px-3 py-1 text-sm bg-blue-100 text-blue-700 rounded hover:bg-blue-200"
                >
                  全選択
                </button>
                <button
                  onClick={handleDeselectAll}
                  className="px-3 py-1 text-sm bg-gray-100 text-gray-700 rounded hover:bg-gray-200"
                >
                  全解除
                </button>
                <span className="ml-auto text-sm text-gray-600">
                  {selectedPlayerIds.length}名選択中
                </span>
              </div>

              {/* 選手リスト */}
              <div className="space-y-2">
                {allPlayers.map((player) => (
                  <label
                    key={player.id}
                    className="flex items-center p-3 border rounded hover:bg-gray-50 cursor-pointer"
                  >
                    <input
                      type="checkbox"
                      checked={selectedPlayerIds.includes(player.id)}
                      onChange={() => handleTogglePlayer(player.id)}
                      className="mr-3 h-4 w-4"
                    />
                    <div className="flex-1">
                      <div className="font-medium">{player.name}</div>
                      <div className="text-sm text-gray-500">
                        {player.danRank || player.kyuRank || ''}
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            </>
          )}
        </div>

        {/* フッター */}
        <div className="flex justify-end gap-2 p-4 border-t bg-gray-50">
          <button
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-100"
            disabled={saving}
          >
            キャンセル
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50"
            disabled={saving || loading}
          >
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default MatchParticipantsEditModal;
