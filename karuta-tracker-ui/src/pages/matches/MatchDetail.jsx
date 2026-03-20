import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link, useSearchParams } from 'react-router-dom';
import { matchAPI } from '../../api';
import { useAuth } from '../../context/AuthContext';
import {
  Trophy,
  Calendar,
  User,
  Edit,
  Trash2,
  ArrowLeft,
  AlertCircle,
} from 'lucide-react';

const MatchDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [match, setMatch] = useState(null);
  const [loading, setLoading] = useState(true);
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const fetchMatch = async () => {
      try {
        const response = await matchAPI.getById(id);
        setMatch(response.data);
      } catch (error) {
        console.error('試合記録の取得に失敗しました:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchMatch();
  }, [id]);

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await matchAPI.delete(id);
      navigate('/matches');
    } catch (error) {
      console.error('削除に失敗しました:', error);
      alert('試合記録の削除に失敗しました');
      setDeleting(false);
      setDeleteConfirm(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-secondary"></div>
      </div>
    );
  }

  if (!match) {
    return (
      <div className="text-center py-12">
        <AlertCircle className="w-16 h-16 text-text-placeholder mx-auto mb-4" />
        <p className="text-text-muted text-lg">試合記録が見つかりません</p>
        <Link
          to="/matches"
          className="inline-block mt-4 text-secondary hover:text-secondary-hover"
        >
          試合記録一覧に戻る
        </Link>
      </div>
    );
  }

  const getResultColor = (result) => {
    switch (result) {
      case '勝ち':
        return 'text-status-success bg-status-success-surface border-status-success/20';
      case '負け':
        return 'text-status-danger bg-status-danger-surface border-status-danger/20';
      default:
        return 'text-text-muted bg-bg border-border-subtle';
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      {/* ヘッダー */}
      <div className="mb-6">
        <Link
          to="/matches"
          className="inline-flex items-center gap-2 text-text-muted hover:text-text mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          試合記録一覧に戻る
        </Link>
        <div className="flex justify-between items-start">
          <div>
            <p className="text-text-muted mt-1">
              {new Date(match.matchDate).toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
              })}
            </p>
          </div>
          <div className="flex gap-2">
            <Link
              to={`/matches/${id}/edit`}
              className="flex items-center gap-2 bg-surface text-text px-4 py-2 rounded-lg hover:bg-surface-disabled transition-colors"
            >
              <Edit className="w-4 h-4" />
              編集
            </Link>
            <button
              onClick={() => setDeleteConfirm(true)}
              className="flex items-center gap-2 bg-status-danger-surface text-status-danger px-4 py-2 rounded-lg hover:bg-red-100 transition-colors"
            >
              <Trash2 className="w-4 h-4" />
              削除
            </button>
          </div>
        </div>
      </div>

      {/* 試合結果カード */}
      <div className="bg-bg rounded-lg shadow-sm p-6 mb-6">
        <div className="text-center mb-6">
          <div
            className={`inline-block px-8 py-4 rounded-lg border-2 ${getResultColor(
              match.result
            )}`}
          >
            <p className="text-4xl font-bold">{match.result}</p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="text-center p-4 bg-bg rounded-lg">
            <p className="text-sm text-text-muted mb-2">対戦相手</p>
            <div className="flex items-center justify-center gap-2">
              <User className="w-5 h-5 text-text-placeholder" />
              {(() => {
                const opponentId = match.player1Id === currentPlayer?.id
                  ? match.player2Id
                  : match.player1Id;
                return opponentId && opponentId !== 0 ? (
                  <button
                    onClick={() => navigate(`/matches?playerId=${opponentId}`)}
                    className="text-xl font-semibold text-secondary hover:underline"
                  >
                    {match.opponentName}
                  </button>
                ) : (
                  <p className="text-xl font-semibold text-text">
                    {match.opponentName}
                  </p>
                );
              })()}
            </div>
          </div>

          <div className="text-center p-4 bg-bg rounded-lg">
            <p className="text-sm text-text-muted mb-2">枚数差</p>
            <p className="text-xl font-semibold text-text">
              {match.scoreDifference > 0 ? '+' : ''}
              {match.scoreDifference}枚
            </p>
          </div>
        </div>
      </div>

      {/* 詳細情報 */}
      <div className="bg-bg rounded-lg shadow-sm p-6 space-y-4">
        <h2 className="text-lg font-semibold text-text mb-4">詳細情報</h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="flex items-center gap-3">
            <Calendar className="w-5 h-5 text-text-placeholder" />
            <div>
              <p className="text-sm text-text-muted">試合日</p>
              <p className="font-medium text-text">
                {new Date(match.matchDate).toLocaleDateString('ja-JP')}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Trophy className="w-5 h-5 text-text-placeholder" />
            <div>
              <p className="text-sm text-text-muted">試合番号</p>
              <p className="font-medium text-text">
                第{match.matchNumber}試合
              </p>
            </div>
          </div>
        </div>

        {match.notes && (
          <div className="pt-4 border-t border-border-subtle">
            <p className="text-sm text-text-muted mb-2">メモ</p>
            <p className="text-text whitespace-pre-wrap">{match.notes}</p>
          </div>
        )}

        <div className="pt-4 border-t border-border-subtle">
          <div className="flex justify-between text-sm text-text-muted">
            <span>
              作成日:{' '}
              {new Date(match.createdAt).toLocaleDateString('ja-JP')}
            </span>
            {match.updatedAt !== match.createdAt && (
              <span>
                更新日:{' '}
                {new Date(match.updatedAt).toLocaleDateString('ja-JP')}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 削除確認モーダル */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-bg rounded-lg max-w-md w-full p-6">
            <h3 className="text-xl font-bold text-text mb-4">
              試合記録を削除
            </h3>
            <p className="text-text-muted mb-6">
              この試合記録を削除してもよろしいですか？
              <br />
              この操作は取り消せません。
            </p>
            <div className="flex gap-3">
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="flex-1 bg-red-600 text-text-inverse px-4 py-2 rounded-lg hover:bg-red-700 transition-colors disabled:bg-surface-disabled disabled:text-text-disabled disabled:cursor-not-allowed font-medium"
              >
                {deleting ? '削除中...' : '削除する'}
              </button>
              <button
                onClick={() => setDeleteConfirm(false)}
                disabled={deleting}
                className="flex-1 bg-surface text-text px-4 py-2 rounded-lg hover:bg-surface-disabled transition-colors font-medium"
              >
                キャンセル
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MatchDetail;
