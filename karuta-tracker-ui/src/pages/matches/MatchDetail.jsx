import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { matchAPI } from '../../api';
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
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  if (!match) {
    return (
      <div className="text-center py-12">
        <AlertCircle className="w-16 h-16 text-gray-300 mx-auto mb-4" />
        <p className="text-gray-600 text-lg">試合記録が見つかりません</p>
        <Link
          to="/matches"
          className="inline-block mt-4 text-primary-600 hover:text-primary-700"
        >
          試合記録一覧に戻る
        </Link>
      </div>
    );
  }

  const getResultColor = (result) => {
    switch (result) {
      case '勝ち':
        return 'text-green-600 bg-green-50 border-green-200';
      case '負け':
        return 'text-red-600 bg-red-50 border-red-200';
      default:
        return 'text-gray-600 bg-gray-50 border-gray-200';
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      {/* ヘッダー */}
      <div className="mb-6">
        <Link
          to="/matches"
          className="inline-flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          試合記録一覧に戻る
        </Link>
        <div className="flex justify-between items-start">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
              <Trophy className="w-8 h-8 text-primary-600" />
              試合詳細
            </h1>
            <p className="text-gray-600 mt-1">
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
              className="flex items-center gap-2 bg-gray-100 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-200 transition-colors"
            >
              <Edit className="w-4 h-4" />
              編集
            </Link>
            <button
              onClick={() => setDeleteConfirm(true)}
              className="flex items-center gap-2 bg-red-50 text-red-600 px-4 py-2 rounded-lg hover:bg-red-100 transition-colors"
            >
              <Trash2 className="w-4 h-4" />
              削除
            </button>
          </div>
        </div>
      </div>

      {/* 試合結果カード */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
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
          <div className="text-center p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-600 mb-2">対戦相手</p>
            <div className="flex items-center justify-center gap-2">
              <User className="w-5 h-5 text-gray-400" />
              <p className="text-xl font-semibold text-gray-900">
                {match.opponentName}
              </p>
            </div>
          </div>

          <div className="text-center p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-600 mb-2">枚数差</p>
            <p className="text-xl font-semibold text-gray-900">
              {match.scoreDifference > 0 ? '+' : ''}
              {match.scoreDifference}枚
            </p>
          </div>
        </div>
      </div>

      {/* 詳細情報 */}
      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">詳細情報</h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="flex items-center gap-3">
            <Calendar className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-600">試合日</p>
              <p className="font-medium text-gray-900">
                {new Date(match.matchDate).toLocaleDateString('ja-JP')}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Trophy className="w-5 h-5 text-gray-400" />
            <div>
              <p className="text-sm text-gray-600">試合番号</p>
              <p className="font-medium text-gray-900">
                第{match.matchNumber}試合
              </p>
            </div>
          </div>
        </div>

        {match.notes && (
          <div className="pt-4 border-t border-gray-200">
            <p className="text-sm text-gray-600 mb-2">メモ</p>
            <p className="text-gray-900 whitespace-pre-wrap">{match.notes}</p>
          </div>
        )}

        <div className="pt-4 border-t border-gray-200">
          <div className="flex justify-between text-sm text-gray-500">
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
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <h3 className="text-xl font-bold text-gray-900 mb-4">
              試合記録を削除
            </h3>
            <p className="text-gray-600 mb-6">
              この試合記録を削除してもよろしいですか？
              <br />
              この操作は取り消せません。
            </p>
            <div className="flex gap-3">
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="flex-1 bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-medium"
              >
                {deleting ? '削除中...' : '削除する'}
              </button>
              <button
                onClick={() => setDeleteConfirm(false)}
                disabled={deleting}
                className="flex-1 bg-gray-100 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-200 transition-colors font-medium"
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
