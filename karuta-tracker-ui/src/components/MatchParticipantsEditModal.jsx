import { useState, useEffect, useMemo } from 'react';
import { X, Search } from 'lucide-react';
import { practiceAPI } from '../api';
import apiClient from '../api/client';
import { sortPlayersByRank } from '../utils/playerSort';

const MatchParticipantsEditModal = ({ session, matchNumber, onClose, onSave }) => {
  const [allPlayers, setAllPlayers] = useState([]);
  const [selectedPlayerIds, setSelectedPlayerIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    fetchData();
  }, [session, matchNumber]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const playersResponse = await apiClient.get('/players');
      setAllPlayers(playersResponse.data);

      if (session?.matchParticipants) {
        const participants = session.matchParticipants[matchNumber];
        if (participants && Array.isArray(participants)) {
          const nameToIdMap = {};
          playersResponse.data.forEach(player => {
            nameToIdMap[player.name] = player.id;
          });
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

  const handleRemovePlayer = (playerId) => {
    setSelectedPlayerIds(prev => prev.filter(id => id !== playerId));
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

  const isSelected = (id) => selectedPlayerIds.includes(id);

  // 選択済みプレイヤー（級位→段位→名前順）
  const selectedPlayers = useMemo(() =>
    sortPlayersByRank(allPlayers.filter(p => selectedPlayerIds.includes(p.id))),
    [allPlayers, selectedPlayerIds]
  );

  // 未選択で検索にマッチするプレイヤー（級位→段位→名前順）
  const filteredUnselected = useMemo(() => {
    const unselected = allPlayers.filter(p => !selectedPlayerIds.includes(p.id));
    const filtered = !searchQuery.trim()
      ? unselected
      : unselected.filter(p => p.name.toLowerCase().includes(searchQuery.toLowerCase()));
    return sortPlayersByRank(filtered);
  }, [allPlayers, selectedPlayerIds, searchQuery]);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
      <div className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full max-h-[80vh] overflow-hidden flex flex-col">
        {/* ヘッダー */}
        <div className="px-6 pt-5 pb-3 flex justify-between items-start flex-shrink-0">
          <div>
            <h2 className="text-lg font-bold text-[#5f3a2d]">
              第{matchNumber}試合の参加者
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-[#8a7568] hover:text-[#5f3a2d] -mt-1"
          >
            <X size={20} />
          </button>
        </div>

        {/* コンテンツ */}
        <div className="flex-1 overflow-y-auto px-6 pb-4">
          {error && (
            <div className="mb-3 p-3 bg-red-50 text-red-600 text-sm rounded-lg">
              {error}
            </div>
          )}

          {loading ? (
            <div className="text-center py-8 text-[#8a7568]">読み込み中...</div>
          ) : (
            <>
              {/* 選択済みエリア */}
              {selectedPlayers.length > 0 && (
                <div className="mb-4">
                  <div className="text-xs text-[#8a7568] mb-2">
                    選択中 ({selectedPlayers.length}名)
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedPlayers.map((player) => (
                      <button
                        key={player.id}
                        type="button"
                        onClick={() => handleRemovePlayer(player.id)}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-sm bg-[#82655a] text-white hover:bg-[#6b5048] transition-colors"
                      >
                        {player.name}
                        <X size={12} className="text-white/70" />
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* 検索 + 一括操作 */}
              <div className="mb-3 flex items-center gap-2">
                <div className="flex-1 relative">
                  <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#b0a093]" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="名前で検索..."
                    className="w-full pl-8 pr-3 py-1.5 text-sm bg-white border border-[#e2d9d0] rounded-full focus:outline-none focus:border-[#82655a] text-[#5f3a2d] placeholder-[#b0a093]"
                  />
                </div>
                <button
                  onClick={handleSelectAll}
                  className="px-2.5 py-1.5 text-xs text-[#82655a] border border-[#82655a] rounded-full hover:bg-[#82655a] hover:text-white transition-colors flex-shrink-0"
                >
                  全選択
                </button>
                <button
                  onClick={handleDeselectAll}
                  className="px-2.5 py-1.5 text-xs text-[#8a7568] border border-[#c5b8ab] rounded-full hover:bg-[#e2d9d0] transition-colors flex-shrink-0"
                >
                  全解除
                </button>
              </div>

              {/* 未選択チップ */}
              <div className="flex flex-wrap gap-1.5">
                {filteredUnselected.map((player) => (
                  <button
                    key={player.id}
                    type="button"
                    onClick={() => handleTogglePlayer(player.id)}
                    className="px-2.5 py-1 rounded-full text-sm text-[#8a7568] border border-[#c5b8ab] hover:border-[#82655a] hover:text-[#5f3a2d] transition-colors"
                  >
                    {player.name}
                  </button>
                ))}
                {filteredUnselected.length === 0 && searchQuery && (
                  <p className="text-sm text-[#b0a093] py-2">該当なし</p>
                )}
              </div>
            </>
          )}
        </div>

        {/* フッター */}
        <div className="px-6 py-4 border-t border-[#e2d9d0] flex justify-end gap-2 flex-shrink-0">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-[#8a7568] border border-[#c5b8ab] rounded-lg hover:bg-[#e2d9d0] transition-colors"
            disabled={saving}
          >
            キャンセル
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 text-sm font-medium text-white bg-[#82655a] rounded-lg hover:bg-[#6b5048] disabled:opacity-50 transition-colors"
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
