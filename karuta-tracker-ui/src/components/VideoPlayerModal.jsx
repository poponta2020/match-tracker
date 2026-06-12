import { useNavigate } from 'react-router-dom';
import { X, ExternalLink, ChevronRight } from 'lucide-react';
import { buildYoutubeEmbedUrl } from '../utils/youtube';

/**
 * 試合動画 再生モーダル（全画面共通）
 *
 * props:
 *   - video: MatchVideoDto 相当のオブジェクト
 *       { id, matchDate, matchNumber, player1Name, player2Name, videoUrl,
 *         youtubeVideoId, title, matchId, winnerId, player1Id, player2Id,
 *         scoreDifference }
 *   - onClose: 閉じるコールバック
 *
 * 背景クリック / × / 閉じるボタンで閉じる。
 */

// 試合日を「YYYY年M月D日(曜)」形式で表示
const formatMatchDate = (dateStr) => {
  if (!dateStr) return '';
  return new Date(`${dateStr}T00:00:00`).toLocaleDateString('ja-JP', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  });
};

// 結果テキスト（結果入力済みの場合のみ）: 「○○の勝ち（N枚差）」
const buildResultText = (video) => {
  if (video?.winnerId == null) return null;
  const winnerName =
    video.winnerId === video.player1Id
      ? video.player1Name
      : video.winnerId === video.player2Id
        ? video.player2Name
        : null;
  if (!winnerName) return null;
  const diff = video.scoreDifference != null ? Math.abs(video.scoreDifference) : null;
  return diff != null ? `${winnerName}の勝ち（${diff}枚差）` : `${winnerName}の勝ち`;
};

const VideoPlayerModal = ({ video, onClose }) => {
  const navigate = useNavigate();

  if (!video) return null;

  const resultText = buildResultText(video);

  const handleViewMatchDetail = () => {
    onClose();
    navigate(`/matches/${video.matchId}`);
  };

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4"
      onClick={onClose}
    >
      <div
        className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ヘッダー */}
        <div className="px-5 pt-4 pb-3 flex justify-between items-start flex-shrink-0">
          <div className="min-w-0">
            <h2 className="text-base font-bold text-[#5f3a2d] truncate">
              {video.player1Name} <span className="text-[#8a7568] font-normal">vs</span>{' '}
              {video.player2Name}
            </h2>
            <p className="text-sm text-[#8a7568] mt-0.5">
              {formatMatchDate(video.matchDate)}
              {video.matchNumber != null && `\u3000第${video.matchNumber}試合`}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-[#8a7568] hover:text-[#5f3a2d] -mt-0.5 flex-shrink-0 ml-2"
            aria-label="閉じる"
          >
            <X size={20} />
          </button>
        </div>

        {/* スクロール領域 */}
        <div className="flex-1 overflow-y-auto px-5 pb-4">
          {/* YouTube 埋め込み（16:9 レスポンシブ） */}
          <div className="relative w-full rounded-lg overflow-hidden bg-black" style={{ paddingTop: '56.25%' }}>
            {video.youtubeVideoId ? (
              <iframe
                className="absolute inset-0 w-full h-full"
                src={buildYoutubeEmbedUrl(video.youtubeVideoId)}
                title={video.title || '試合動画'}
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowFullScreen
                referrerPolicy="strict-origin-when-cross-origin"
              />
            ) : (
              <div className="absolute inset-0 flex items-center justify-center text-white/70 text-sm px-4 text-center">
                動画を読み込めませんでした
              </div>
            )}
          </div>

          {/* 動画タイトル */}
          {video.title && (
            <p className="mt-3 text-sm font-medium text-[#5f3a2d] break-words">
              {video.title}
            </p>
          )}

          {/* 結果（入力済みのみ） */}
          {resultText && (
            <div className="mt-3 inline-flex items-center px-3 py-1.5 bg-[#eef3ee] text-[#3d5a4c] text-sm font-semibold rounded-full">
              {resultText}
            </div>
          )}

          {/* リンク群 */}
          <div className="mt-4 flex flex-col gap-2">
            <a
              href={video.videoUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium text-[#82655a] bg-white border border-[#82655a] rounded-lg hover:bg-[#e2d9d0] transition-colors"
            >
              <ExternalLink size={16} />
              YouTube で開く
            </a>

            {video.matchId != null && (
              <button
                onClick={handleViewMatchDetail}
                className="w-full flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium text-white bg-[#82655a] rounded-lg hover:bg-[#6b5048] transition-colors"
              >
                試合詳細を見る
                <ChevronRight size={16} />
              </button>
            )}
          </div>
        </div>

        {/* フッター */}
        <div className="px-5 py-3 border-t border-[#e2d9d0] flex justify-end flex-shrink-0">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-[#8a7568] border border-[#c5b8ab] rounded-lg hover:bg-[#e2d9d0] transition-colors"
          >
            閉じる
          </button>
        </div>
      </div>
    </div>
  );
};

export default VideoPlayerModal;
