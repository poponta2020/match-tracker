/**
 * 戦績確認画面「カレンダー」タブの純ロジック。
 *
 * 週グリッド生成・日付/月集計・表示整形を、jsdom や実日付に依存しない純関数として提供する。
 * （プロジェクトの utils/date.js・utils/rank.js と同様、表示コンポーネントからロジックを切り出して
 * 単体テストを容易にする方針）
 */

// 2桁ゼロ埋め
const pad2 = (n) => String(n).padStart(2, '0');

/** 曜日ヘッダ（日曜始まり） */
export const WEEKDAYS = ['日', '月', '火', '水', '木', '金', '土'];

/**
 * 年・月(0-based)・日から `YYYY-MM-DD` を組み立てる。
 * @param {number} year
 * @param {number} month0 0-based の月
 * @param {number} day
 * @returns {string} `YYYY-MM-DD`
 */
export const isoDate = (year, month0, day) => `${year}-${pad2(month0 + 1)}-${pad2(day)}`;

/**
 * 月カレンダーの週グリッドを生成する。
 *
 * 前月末・翌月頭のはみ出しセルを含み、はみ出しセルは `inMonth:false`（当月セルのみ `date` を持つ）。
 * `PracticeList.generateCalendar` と同型（月初の曜日ぶん前月末で埋め、末尾は翌月頭で7の倍数に整える）。
 * @param {number} year
 * @param {number} month0 0-based の月
 * @returns {Array<Array<{day:number, inMonth:boolean, date?:string}>>} 週配列（各週7セル）
 */
export const buildWeeks = (year, month0) => {
  const startDow = new Date(year, month0, 1).getDay();
  const daysInMonth = new Date(year, month0 + 1, 0).getDate();
  const prevMonthDays = new Date(year, month0, 0).getDate();
  const cells = [];
  for (let i = 0; i < startDow; i++) {
    cells.push({ day: prevMonthDays - startDow + 1 + i, inMonth: false });
  }
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ day: d, inMonth: true, date: isoDate(year, month0, d) });
  }
  let nextDay = 1;
  while (cells.length % 7 !== 0) {
    cells.push({ day: nextDay++, inMonth: false });
  }
  const weeks = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
  return weeks;
};

/**
 * 試合配列を日付ごとにグループ化する（各日は試合番号昇順）。`matchDate` が無い試合は除外。
 * @param {Array} matches
 * @returns {Object<string, Array>} 日付(`YYYY-MM-DD`) -> 試合配列
 */
export const groupMatchesByDate = (matches = []) => {
  const map = {};
  for (const m of matches) {
    if (!m || !m.matchDate) continue;
    if (!map[m.matchDate]) map[m.matchDate] = [];
    map[m.matchDate].push(m);
  }
  for (const k of Object.keys(map)) {
    map[k].sort((a, b) => (a.matchNumber ?? 0) - (b.matchNumber ?? 0));
  }
  return map;
};

/**
 * 指定年月(0-based)に属する試合数を数える（月ラベル下の総試合数バッジ用）。
 * @param {Array} matches
 * @param {number} year
 * @param {number} month0 0-based の月
 * @returns {number}
 */
export const countMatchesInMonth = (matches = [], year, month0) =>
  matches.filter((m) => {
    if (!m || !m.matchDate) return false;
    const [y, mo] = m.matchDate.split('-').map(Number);
    return y === year && mo === month0 + 1;
  }).length;

/**
 * `YYYY-MM-DD` を `M/D`（十の位が0なら省略）に整形する。
 * @param {string} iso `YYYY-MM-DD`
 * @returns {string} 例 '7/3'。空入力時は ''
 */
export const formatMonthDay = (iso) => {
  if (!iso) return '';
  const [, m, d] = iso.split('-').map(Number);
  return `${m}/${d}`;
};

/**
 * `YYYY-MM-DD` を `M/D(曜)` に整形する（曜日は日本語1字・半角括弧。選択日見出し用）。
 * @param {string} iso `YYYY-MM-DD`
 * @returns {string} 例 '7/21(火)'。空入力時は ''
 */
export const formatMonthDayDow = (iso) => {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  const dow = WEEKDAYS[new Date(y, m - 1, d).getDay()];
  return `${m}/${d}(${dow})`;
};

/**
 * 自分視点の試合から相手の級（単字）を返す。self でない側の kyuRank から「級」を除いた頭文字。
 * 級が未設定（null/空/文字列以外）なら空文字を返す（呼び出し側で括弧を出さない）。
 * @param {Object} match
 * @param {number} selfId ログインユーザーの player id
 * @returns {string} 例 'A'。未設定時は ''
 */
export const opponentKyuChar = (match, selfId) => {
  if (!match) return '';
  const rank = match.player1Id === selfId ? match.player2KyuRank : match.player1KyuRank;
  if (typeof rank !== 'string') return '';
  return rank.replace('級', '').trim();
};

/**
 * 選択日の見出しに出す会場名を返す。会場は日付に一意の前提で、その日の試合から最初の `venueName` を採る。
 * @param {Array} dayMatches その日の試合配列
 * @returns {string} 会場名。無ければ ''
 */
export const venueOfDay = (dayMatches = []) =>
  dayMatches.find((m) => m && m.venueName)?.venueName || '';
