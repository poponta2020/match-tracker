import { useState, useEffect, useMemo } from 'react';
import { X, Search } from 'lucide-react';
import { practiceAPI, playerAPI, lotteryAPI } from '../api';
import { sortPlayersByRank } from '../utils/playerSort';
import PlayerChip from './PlayerChip';

const MatchParticipantsEditModal = ({ session, matchNumber, onClose, onSave, onRefresh }) => {
  const [allPlayers, setAllPlayers] = useState([]);
  const [selectedPlayerIds, setSelectedPlayerIds] = useState([]);
  // A-1: 保存前の追加/削除サマリー算出用に、編集開始時点のWON/PENDING集合を保持する。
  const [initialPlayerIds, setInitialPlayerIds] = useState([]);
  // 管理者の手動繰り上げ用: キャンセル待ちの参加者一覧（participantId付き）。
  const [waitlist, setWaitlist] = useState([]);
  const [promotingId, setPromotingId] = useState(null);
  // 定員ガード: WON+OFFERED >= capacity のとき満員（繰り上げ不可）。capacity 未設定時は常に false。
  const [matchFull, setMatchFull] = useState(false);
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
      const playersResponse = await playerAPI.getAll();
      setAllPlayers(playersResponse.data);

      if (session?.matchParticipants) {
        const participants = session.matchParticipants[matchNumber];
        if (participants && Array.isArray(participants)) {
          // A-1: この編集は当選/参加確定者（WON/PENDING）のみを対象とする。
          // WAITLISTED/OFFERED/CANCELLED/DECLINED/WAITLIST_DECLINED は初期選択に含めない
          // （待機者の抽選なしWON昇格・キャンセル済みの復活を防ぐ）。
          // playerId 基準で特定し、同姓同名・改名でも取りこぼさない。
          const ids = participants
            .filter(p => typeof p !== 'string' && p.playerId != null
              && (p.status === 'WON' || p.status === 'PENDING'))
            .map(p => p.playerId);
          setSelectedPlayerIds(ids);
          setInitialPlayerIds(ids);

          // 管理者手動繰り上げ用: キャンセル待ち（WAITLISTED）を待ち番号順に抽出する。
          const wl = participants
            .filter(p => typeof p !== 'string' && p.participantId != null && p.status === 'WAITLISTED')
            .slice()
            .sort((a, b) => (a.waitlistNumber ?? Number.MAX_SAFE_INTEGER) - (b.waitlistNumber ?? Number.MAX_SAFE_INTEGER));
          setWaitlist(wl);

          // 定員判定（バックエンドの繰り上げガードと同じ WON+OFFERED >= capacity で満員）。
          const wonOffered = participants.filter(p =>
            typeof p !== 'string' && (p.status === 'WON' || p.status === 'OFFERED')).length;
          const cap = session.capacity;
          setMatchFull(cap != null && wonOffered >= cap);
        }
      }
    } catch (err) {
      setError('データの取得に失敗しました');
      console.error('Error fetching data:', err);
    } finally {
      setLoading(false);
    }
  };

  // 管理者による手動繰り上げ（キャンセル待ち → 当選）。
  // 繰り上げは即時サーバ反映し、onRefresh で親のセッションを再取得してモーダルを最新化する
  // （モーダルは開いたまま。連続で繰り上げできる）。
  const handlePromote = async (participant) => {
    if (!window.confirm(
      `${participant.name} さんを第${matchNumber}試合のキャンセル待ちから「当選（参加確定）」に繰り上げます。\n` +
      `よろしいですか？`
    )) {
      return;
    }
    try {
      setPromotingId(participant.participantId);
      setError('');
      await lotteryAPI.editParticipants({
        sessionId: session.id,
        matchNumber,
        statusChanges: [{ participantId: participant.participantId, newStatus: 'WON' }],
      });
      // 楽観的にモーダル内の一覧から除外しつつ、親の再取得で正となる状態へ更新する。
      setWaitlist(prev => prev.filter(w => w.participantId !== participant.participantId));
      if (onRefresh) {
        await onRefresh();
      }
    } catch (err) {
      // バックエンドの定員ガード等（400）のメッセージがあれば表示する
      setError(err?.response?.data?.message || '繰り上げに失敗しました');
      console.error('Error promoting participant:', err);
    } finally {
      setPromotingId(null);
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
    // A-1: 保存前に追加/削除人数の要約を確認ダイアログで提示する。
    const initialSet = new Set(initialPlayerIds);
    const selectedSet = new Set(selectedPlayerIds);
    const added = selectedPlayerIds.filter(id => !initialSet.has(id)).length;
    const removed = initialPlayerIds.filter(id => !selectedSet.has(id)).length;

    const summary =
      `第${matchNumber}試合の当選/参加確定者を更新します。\n` +
      `追加: ${added}名 / 削除: ${removed}名（削除した方はキャンセル扱いになります）\n\n` +
      `※この編集は当選/参加確定者（WON/PENDING）のみを対象とします。` +
      `キャンセル待ち・辞退・キャンセル済みの方には影響しません。`;
    if (!window.confirm(summary)) {
      return;
    }

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
            <p className="text-xs text-[#8a7568] mt-1">
              当選/参加確定者の編集と、キャンセル待ちの手動繰り上げができます
            </p>
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
                      <PlayerChip
                        key={player.id}
                        name={player.name}
                        kyuRank={player.kyuRank}
                        onClick={() => handleRemovePlayer(player.id)}
                        className="inline-flex items-center gap-1 text-sm bg-[#82655a] text-white hover:bg-[#6b5048] transition-colors"
                      >
                        <X size={12} className="text-white/70" />
                      </PlayerChip>
                    ))}
                  </div>
                </div>
              )}

              {/* キャンセル待ち（管理者手動繰り上げ） */}
              {waitlist.length > 0 && (
                <div className="mb-4">
                  <div className="text-xs text-[#8a7568] mb-2 flex items-center justify-between">
                    <span>キャンセル待ち ({waitlist.length}名)</span>
                    {matchFull && (
                      <span className="text-[10px] text-[#c0392b]">定員満（会場拡張が必要）</span>
                    )}
                  </div>
                  <div className="space-y-1.5">
                    {waitlist.map((w) => (
                      <div
                        key={w.participantId}
                        className="flex items-center justify-between bg-yellow-50 border border-yellow-200 rounded-lg px-2.5 py-1.5"
                      >
                        <span className="text-sm text-[#5f3a2d] flex items-center gap-1.5">
                          {w.waitlistNumber != null && (
                            <span className="text-[10px] text-[#b0956a] font-bold">{w.waitlistNumber}</span>
                          )}
                          {w.name}
                        </span>
                        <button
                          onClick={() => handlePromote(w)}
                          disabled={promotingId != null || matchFull}
                          title={matchFull ? '定員に空きがないため繰り上げできません' : undefined}
                          className="px-2.5 py-1 text-xs font-medium text-white bg-[#4a6b5a] rounded-full hover:bg-[#3d5a4c] disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex-shrink-0"
                        >
                          {promotingId === w.participantId ? '繰り上げ中...' : '繰り上げ'}
                        </button>
                      </div>
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
                  <PlayerChip
                    key={player.id}
                    name={player.name}
                    kyuRank={player.kyuRank}
                    onClick={() => handleTogglePlayer(player.id)}
                    className="text-sm text-[#8a7568] hover:text-[#5f3a2d] transition-colors"
                  />
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
