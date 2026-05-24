import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link, useSearchParams } from 'react-router-dom';
import { matchAPI, matchCommentsAPI } from '../../api';
import { mentorRelationshipAPI } from '../../api/mentorRelationship';
import MatchCommentThread from './MatchCommentThread';
import { useAuth } from '../../context/AuthContext';
import { Edit, Trash2, AlertCircle } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import PageHeader from '../../components/PageHeader';

const MatchDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [match, setMatch] = useState(null);
  const [loading, setLoading] = useState(true);
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [searchParams] = useSearchParams();
  const queryPlayerId = searchParams.get('playerId');
  const isOtherPlayer = queryPlayerId && Number(queryPlayerId) !== currentPlayer?.id;
  const [hasMentorRelation, setHasMentorRelation] = useState(false);
  const [menteeIdForComments, setMenteeIdForComments] = useState(null);
  const [commentsByOthersExist, setCommentsByOthersExist] = useState(false);

  useEffect(() => {
    const fetchMatch = async () => {
      try {
        const params = queryPlayerId ? { playerId: queryPlayerId } : {};
        const response = await matchAPI.getById(id, params);
        setMatch(response.data);
      } catch (error) {
        console.error('試合記録の取得に失敗しました:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchMatch();
  }, [id, queryPlayerId]);

  // メンター関係の確認
  useEffect(() => {
    const checkMentorRelation = async () => {
      try {
        if (isOtherPlayer) {
          const res = await mentorRelationshipAPI.getMyMentees();
          const isMentee = res.data.some(r => r.menteeId === Number(queryPlayerId) && r.status === 'ACTIVE');
          setHasMentorRelation(isMentee);
          if (isMentee) setMenteeIdForComments(Number(queryPlayerId));
        } else {
          // メンティー本人は常にコメントスレッドを表示（解除後も閲覧可能）
          setHasMentorRelation(true);
          setMenteeIdForComments(currentPlayer?.id);
        }
      } catch {
        // ignore
      }
    };
    checkMentorRelation();
  }, [isOtherPlayer, queryPlayerId, currentPlayer]);

  // メンティー本人画面でのコメント欄表示判定: 自分以外のコメントが1件以上あるか
  useEffect(() => {
    if (!menteeIdForComments) {
      setCommentsByOthersExist(false);
      return;
    }
    matchCommentsAPI.getComments(Number(id), menteeIdForComments)
      .then(res => {
        const hasOthers = res.data.some(c => c.authorId !== currentPlayer?.id);
        setCommentsByOthersExist(hasOthers);
      })
      .catch(() => setCommentsByOthersExist(false));
  }, [id, menteeIdForComments, currentPlayer?.id]);

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
      <>
        <PageHeader title="試合詳細" backTo="/matches" />
        <LoadingScreen />
      </>
    );
  }

  if (!match) {
    return (
      <>
        <PageHeader title="試合詳細" backTo="/matches" />
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
      </>
    );
  }

  const getResultMark = (result) => {
    switch (result) {
      case '勝ち':
        return '○';
      case '負け':
        return '×';
      case '引き分け':
        return '△';
      default:
        return result;
    }
  };

  const getResultTextColor = (result) => {
    switch (result) {
      case '勝ち':
        return 'text-green-600';
      case '負け':
        return 'text-red-600';
      default:
        return 'text-gray-600';
    }
  };

  // メンター閲覧時は queryPlayerId（メンティー）視点で対戦相手を計算する
  const perspectivePlayerId = queryPlayerId ? Number(queryPlayerId) : currentPlayer?.id;
  const opponentId = match.player1Id === perspectivePlayerId
    ? match.player2Id
    : match.player1Id;
  const canNavigateOpponent = opponentId && opponentId !== 0;

  // メンター閲覧時はメンティーのメモ、本人閲覧時は自分のメモを表示
  const otetsukiCount = isOtherPlayer ? match.menteeOtetsukiCount : match.myOtetsukiCount;
  const personalNotes = isOtherPlayer ? match.menteePersonalNotes : match.myPersonalNotes;
  const hasNotes = otetsukiCount != null || personalNotes;

  return (
    <>
      <PageHeader title="試合詳細" backTo="/matches" />
      <div className="max-w-3xl mx-auto">
      {/* ヘッダー */}
      <div className="mb-6">
        <div className="flex justify-between items-start">
          <div>
            <p className="text-gray-600 mt-1">
              {new Date(match.matchDate).toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
              })}
            </p>
          </div>
          {!isOtherPlayer && (
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
          )}
        </div>
      </div>

      {/* 統合カード（試合結果 + 詳細情報 + メモ） */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6 space-y-4">
        {/* 上段: 試合結果サマリ */}
        <div className="text-center">
          {canNavigateOpponent ? (
            <button
              onClick={() => navigate(`/matches?playerId=${opponentId}`)}
              className="text-2xl font-semibold text-[#4a6b5a] hover:underline"
            >
              {match.opponentName}
            </button>
          ) : (
            <span className="text-2xl font-semibold text-gray-900">
              {match.opponentName}
            </span>
          )}
          <span className={`text-2xl font-bold ml-3 ${getResultTextColor(match.result)}`}>
            {getResultMark(match.result)} {Math.abs(match.scoreDifference)}
          </span>
        </div>

        {/* 中段: 詳細情報1行
            whitespace-pre-wrap: 半角スペース2個を視覚的にそのまま反映するため */}
        <div className="text-sm text-gray-600 text-center whitespace-pre-wrap">
          {`${new Date(match.matchDate).toLocaleDateString('ja-JP')}  第${match.matchNumber}試合  ${match.venueName || '—'}`}
        </div>

        {/* 下段: お手付き・メモ */}
        {hasNotes && (
          <div className="pt-4 border-t border-gray-200 space-y-2">
            {otetsukiCount != null && (
              <div className="text-gray-900">
                <span className="text-gray-600">お手付き:</span> {otetsukiCount} 回
              </div>
            )}
            {personalNotes && (
              <div className="text-gray-900">
                <span className="text-gray-600">メモ:</span>{' '}
                <span className="whitespace-pre-wrap">{personalNotes}</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* コメントスレッド
          - メンター閲覧時: メンター関係(ACTIVE)があれば常に表示
          - メンティー本人: 自分以外のコメントが1件以上あるときのみ表示 */}
      {menteeIdForComments &&
        ((isOtherPlayer && hasMentorRelation) ||
          (!isOtherPlayer && commentsByOthersExist)) && (
        <MatchCommentThread matchId={Number(id)} menteeId={menteeIdForComments} />
      )}

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
    </>
  );
};

export default MatchDetail;
