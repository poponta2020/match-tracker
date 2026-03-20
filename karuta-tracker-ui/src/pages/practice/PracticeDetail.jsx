import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { practiceAPI } from '../../api';
import PlayerChip, { getKyuBorderColor } from '../../components/PlayerChip';

const PracticeDetail = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  useEffect(() => {
    fetchSession();
  }, [id]);

  const fetchSession = async () => {
    try {
      setLoading(true);
      const response = await practiceAPI.getById(id);
      setSession(response.data);
    } catch (err) {
      setError('練習記録の取得に失敗しました');
      console.error('Error fetching session:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    try {
      await practiceAPI.delete(id);
      navigate('/practice');
    } catch (err) {
      setError('削除に失敗しました');
      console.error('Error deleting session:', err);
    }
    setShowDeleteModal(false);
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ja-JP', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long'
    });
  };

  const formatTime = (time) => {
    if (!time) return '';
    return time.substring(0, 5); // "HH:MM:SS" -> "HH:MM"
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-text-muted">読み込み中...</div>
      </div>
    );
  }

  if (error || !session) {
    return (
      <div className="max-w-4xl mx-auto">
        <div className="p-4 bg-status-danger-surface border border-status-danger/20 text-status-danger rounded-lg">
          {error || '練習記録が見つかりません'}
        </div>
        <button
          onClick={() => navigate('/practice')}
          className="mt-4 text-status-info hover:text-status-info"
        >
          ← 一覧に戻る
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="flex justify-end items-center mb-6">
        <div className="space-x-4">
          <button
            onClick={() => navigate(`/practice/${id}/edit`)}
            className="px-4 py-2 bg-status-info text-text-inverse rounded-lg hover:bg-status-info transition-colors"
          >
            編集
          </button>
          <button
            onClick={() => setShowDeleteModal(true)}
            className="px-4 py-2 bg-status-danger text-text-inverse rounded-lg hover:bg-status-danger transition-colors"
          >
            削除
          </button>
        </div>
      </div>

      <div className="bg-bg shadow-md rounded-lg p-6 space-y-6">
        {/* 基本情報 */}
        <div className="border-b border-border-subtle pb-4">
          <h2 className="text-xl font-semibold text-text mb-4">基本情報</h2>
          <dl className="grid grid-cols-2 gap-4">
            <div>
              <dt className="text-sm font-medium text-text-muted">練習日</dt>
              <dd className="mt-1 text-lg text-text">{formatDate(session.sessionDate)}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-text-muted">会場</dt>
              <dd className="mt-1 text-lg text-text">{session.venueName || '-'}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-text-muted">参加者数</dt>
              <dd className="mt-1 text-lg text-text">{session.participantCount || 0}名</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-text-muted">試合数</dt>
              <dd className="mt-1 text-lg text-text">
                {session.completedMatches || 0} / {session.totalMatches}
                <span className="text-sm text-text-muted ml-2">
                  ({session.totalMatches ? Math.round((session.completedMatches || 0) / session.totalMatches * 100) : 0}%)
                </span>
              </dd>
            </div>
          </dl>
        </div>

        {/* 試合時間割 */}
        {session.venueSchedules && session.venueSchedules.length > 0 && (
          <div className="border-b border-border-subtle pb-4">
            <h2 className="text-xl font-semibold text-text mb-4">試合時間割</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
              {session.venueSchedules.map((schedule) => {
                const participantCount = session.matchParticipantCounts?.[schedule.matchNumber] || 0;
                const participants = session.matchParticipants?.[schedule.matchNumber] || [];
                return (
                  <div key={schedule.id} className="bg-bg border border-border-subtle rounded-lg p-3">
                    <div className="flex justify-between items-center mb-2">
                      <div className="font-medium text-text">第{schedule.matchNumber}試合</div>
                      <div className="text-sm text-text-muted">
                        {formatTime(schedule.startTime)} - {formatTime(schedule.endTime)}
                      </div>
                    </div>
                    <div className="text-sm text-text-muted">
                      参加者: {participantCount}名
                    </div>
                    {participants.length > 0 && (
                      <div className="mt-1.5 flex flex-wrap gap-1">
                        {participants.map((p, idx) => (
                          <PlayerChip
                            key={idx}
                            name={typeof p === 'string' ? p : p.name}
                            kyuRank={typeof p === 'string' ? undefined : p.kyuRank}
                            className="text-xs text-text-muted bg-bg"
                          />
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 参加者リスト */}
        <div className="border-b border-border-subtle pb-4">
          <h2 className="text-xl font-semibold text-text mb-4">参加者</h2>
          {session.participants && session.participants.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {session.participants.map((participant) => (
                <div
                  key={participant.id}
                  className={`px-4 py-2 bg-bg border ${getKyuBorderColor(participant.kyuRank)} rounded-lg text-center hover:bg-surface cursor-pointer`}
                  onClick={() => navigate(`/players/${participant.id}`)}
                >
                  <div className="text-sm font-medium text-text">{participant.name}</div>
                  {participant.rank && (
                    <div className="text-xs text-text-muted">{participant.rank}</div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <p className="text-text-muted">参加者情報がありません</p>
          )}
        </div>

        {/* メモ */}
        {session.notes && (
          <div>
            <h2 className="text-xl font-semibold text-text mb-4">メモ</h2>
            <div className="bg-bg p-4 rounded-lg">
              <p className="text-text whitespace-pre-wrap">{session.notes}</p>
            </div>
          </div>
        )}

        {/* 戻るボタン */}
        <div className="pt-4">
          <button
            onClick={() => navigate('/practice')}
            className="text-status-info hover:text-status-info"
          >
            ← 一覧に戻る
          </button>
        </div>
      </div>

      {/* 削除確認モーダル */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-bg rounded-lg p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold text-text mb-4">
              練習記録の削除
            </h3>
            <p className="text-text-muted mb-6">
              この練習記録を削除してもよろしいですか？<br />
              この操作は取り消せません。
            </p>
            <div className="flex justify-end space-x-4">
              <button
                onClick={() => setShowDeleteModal(false)}
                className="px-4 py-2 border border-border-strong text-text rounded-lg hover:bg-bg"
              >
                キャンセル
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 bg-status-danger text-text-inverse rounded-lg hover:bg-status-danger"
              >
                削除
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PracticeDetail;
