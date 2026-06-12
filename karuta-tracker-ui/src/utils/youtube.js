/**
 * YouTube URL ユーティリティ
 *
 * 試合動画機能で扱う YouTube URL の検証・動画ID抽出を集約する。
 * バックエンドのバリデーション（4形式）とフロントの即時バリデーションで
 * 同じ判定にするため、共通化している。
 *
 * 受け付ける形式（www. / m. / http(s) 許容）:
 *   - https://www.youtube.com/watch?v=VIDEOID
 *   - https://youtu.be/VIDEOID
 *   - https://m.youtube.com/watch?v=VIDEOID
 *   - https://www.youtube.com/shorts/VIDEOID
 *
 * 動画IDは 11 文字の [A-Za-z0-9_-]。
 */

const VIDEO_ID = '[A-Za-z0-9_-]{11}';

// watch?v= 形式（youtube.com / m.youtube.com、クエリ順不同を許容）
const WATCH_RE = new RegExp(
  `^https?://(?:www\\.|m\\.)?youtube\\.com/watch\\?(?:[^#]*&)?v=(${VIDEO_ID})(?:[&#].*)?$`
);

// youtu.be/ 短縮形式
const SHORT_RE = new RegExp(
  `^https?://youtu\\.be/(${VIDEO_ID})(?:[?#].*)?$`
);

// shorts/ 形式
const SHORTS_RE = new RegExp(
  `^https?://(?:www\\.|m\\.)?youtube\\.com/shorts/(${VIDEO_ID})(?:[?#].*)?$`
);

/**
 * YouTube URL から動画IDを抽出する。
 * @param {string} url
 * @returns {string|null} 11文字の動画ID。抽出できなければ null。
 */
export function extractYoutubeVideoId(url) {
  if (!url || typeof url !== 'string') return null;
  const trimmed = url.trim();
  const match =
    trimmed.match(WATCH_RE) ||
    trimmed.match(SHORT_RE) ||
    trimmed.match(SHORTS_RE);
  return match ? match[1] : null;
}

/**
 * YouTube URL として妥当か。
 * @param {string} url
 * @returns {boolean}
 */
export function isValidYoutubeUrl(url) {
  return extractYoutubeVideoId(url) !== null;
}

/**
 * youtube-nocookie の埋め込みURLを生成する。
 * @param {string} videoId 11文字の動画ID
 * @returns {string}
 */
export function buildYoutubeEmbedUrl(videoId) {
  return `https://www.youtube-nocookie.com/embed/${videoId}`;
}
