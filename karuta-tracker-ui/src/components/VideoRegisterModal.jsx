import { useState } from 'react';
import { X, Loader2, AlertCircle, Youtube } from 'lucide-react';
import { matchVideoAPI } from '../api';
import { isValidYoutubeUrl } from '../utils/youtube';

/**
 * 試合動画 登録/編集モーダル
 *
 * このタスクでは「対象試合固定モード」のみを実装する
 * （試合詳細画面などから、対象の試合が決まった状態で開く形）。
 *
 * 動画倉庫から開く「試合選択ステップ」はタスク8で本コンポーネントに追加する。
 * そのため、対象試合の表示部（renderTargetMatch）と URL 入力部を分離し、
 * 試合選択 UI を差し込みやすい構造にしている。
 *
 * props:
 *   - match: 対象試合（固定）
 *       { matchDate, matchNumber, player1Id, player2Id, player1Name, player2Name }
 *   - video: 編集対象の既存動画（MatchVideoDto）。渡された場合は編集モード。
 *   - onClose: 閉じるコールバック
 *   - onSuccess: 登録/更新成功時のコールバック（API レスポンスの data を引数に渡す）
 */

const formatMatchDate = (dateStr) => {
  if (!dateStr) return '';
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('ja-JP', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  });
};

const INVALID_URL_MESSAGE = 'YouTubeのURLを入力してください';

const VideoRegisterModal = ({ match, video, onClose, onSuccess }) => {
  const isEdit = Boolean(video);
  const [videoUrl, setVideoUrl] = useState(video?.videoUrl || '');
  const [submitting, setSubmitting] = useState(false);
  // フォーム全体のエラー（API由来・必須未入力など）
  const [error, setError] = useState(null);
  // URL欄のインラインバリデーションエラー
  const [urlError, setUrlError] = useState(null);

  const handleSubmit = async () => {
    const trimmed = videoUrl.trim();

    // クライアント側バリデーション
    if (!trimmed || !isValidYoutubeUrl(trimmed)) {
      setUrlError(INVALID_URL_MESSAGE);
      return;
    }
    setUrlError(null);
    setError(null);
    setSubmitting(true);

    try {
      let res;
      if (isEdit) {
        res = await matchVideoAPI.update(video.id, { videoUrl: trimmed });
      } else {
        res = await matchVideoAPI.register({
          matchDate: match.matchDate,
          matchNumber: match.matchNumber,
          player1Id: match.player1Id,
          player2Id: match.player2Id,
          videoUrl: trimmed,
        });
      }
      onSuccess?.(res.data);
      onClose();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          (isEdit ? '動画の更新に失敗しました' : '動画の登録に失敗しました')
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleUrlChange = (e) => {
    setVideoUrl(e.target.value);
    if (urlError) setUrlError(null);
  };

  // 対象試合の表示部（タスク8では、ここを試合選択 UI に切り替えられる）
  const renderTargetMatch = () => (
    <div className="bg-white border border-[#e2d9d0] rounded-lg px-4 py-3">
      <p className="text-xs text-[#8a7568]">
        {formatMatchDate(match.matchDate)}
        {match.matchNumber != null && `\u3000第${match.matchNumber}試合`}
      </p>
      <p className="mt-1 text-sm font-semibold text-[#5f3a2d]">
        {match.player1Name} <span className="text-[#8a7568] font-normal">vs</span>{' '}
        {match.player2Name}
      </p>
    </div>
  );

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
      <div className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full max-h-[90vh] overflow-hidden flex flex-col">
        {/* ヘッダー */}
        <div className="px-6 pt-5 pb-3 flex justify-between items-start flex-shrink-0">
          <h2 className="text-lg font-bold text-[#5f3a2d]">
            {isEdit ? '試合動画を編集' : '試合動画を登録'}
          </h2>
          <button
            onClick={onClose}
            disabled={submitting}
            className="text-[#8a7568] hover:text-[#5f3a2d] -mt-1 disabled:opacity-50"
            aria-label="閉じる"
          >
            <X size={20} />
          </button>
        </div>

        {/* コンテンツ */}
        <div className="flex-1 overflow-y-auto px-6 pb-4">
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-red-700">
              <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <span className="text-sm">{error}</span>
            </div>
          )}

          {/* 対象試合 */}
          <div className="mb-4">
            <p className="text-xs text-[#8a7568] mb-1.5">対象の試合</p>
            {renderTargetMatch()}
          </div>

          {/* URL 入力 */}
          <div>
            <label className="block text-sm font-medium text-[#5f3a2d] mb-1">
              YouTube URL <span className="text-red-500">*</span>
            </label>
            <div className="relative">
              <Youtube
                size={16}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-[#b0a093]"
              />
              <input
                type="url"
                inputMode="url"
                value={videoUrl}
                onChange={handleUrlChange}
                disabled={submitting}
                placeholder="https://youtu.be/..."
                className={`w-full pl-9 pr-3 py-2 text-sm bg-white border rounded-lg focus:outline-none focus:ring-1 text-[#5f3a2d] placeholder-[#b0a093] disabled:bg-gray-50 ${
                  urlError
                    ? 'border-red-400 focus:ring-red-400 focus:border-red-400'
                    : 'border-[#e2d9d0] focus:ring-[#82655a] focus:border-[#82655a]'
                }`}
              />
            </div>
            {urlError ? (
              <p className="mt-1.5 text-xs text-red-600">{urlError}</p>
            ) : (
              <p className="mt-1.5 text-xs text-[#8a7568]">
                限定公開動画の URL を貼り付けてください
              </p>
            )}
          </div>
        </div>

        {/* フッター */}
        <div className="px-6 py-4 border-t border-[#e2d9d0] flex justify-end gap-2 flex-shrink-0">
          <button
            onClick={onClose}
            disabled={submitting}
            className="px-4 py-2 text-sm font-medium text-[#8a7568] border border-[#c5b8ab] rounded-lg hover:bg-[#e2d9d0] disabled:opacity-50 transition-colors"
          >
            キャンセル
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-4 py-2 flex items-center justify-center gap-1.5 text-sm font-medium text-white bg-[#82655a] rounded-lg hover:bg-[#6b5048] disabled:opacity-50 transition-colors"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {submitting
              ? isEdit
                ? '更新中...'
                : '登録中...'
              : isEdit
                ? '更新'
                : '登録'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default VideoRegisterModal;
