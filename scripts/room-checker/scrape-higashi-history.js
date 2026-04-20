/**
 * 札幌市東区民センター マイページ「申込履歴・結果」スクレイピングスクリプト
 *
 * ログイン → 申込履歴・結果 を開き、東区民センターの夜間予約を
 * 日付・部屋・時間帯付きのJSONで標準出力に出力する。
 *
 * Usage:
 *   node scrape-higashi-history.js [--months 2]
 *
 * Options:
 *   --months  取得する月数（今月を含む）。デフォルト2（今月+来月）
 *
 * 環境変数:
 *   SAPPORO_COMMUNITY_USER_ID  - 利用者ID
 *   SAPPORO_COMMUNITY_PASSWORD - パスワード
 *
 * 出力:
 *   JSON配列をstdoutに出力:
 *   [
 *     {
 *       "date": "2026-05-21",
 *       "room": "さくら",
 *       "status": "予約済",
 *       "startTime": "18:00",
 *       "endTime": "21:00",
 *       "rawContent": "札幌市東区民センターさくら（和室） 利用申込"
 *     },
 *     ...
 *   ]
 */

const { chromium } = require("playwright");

const LOGIN_URL = "https://sapporo-community.jp/UserWebApp/Form/UserLogin.aspx";

// 対象施設（申込内容のテキストに含まれるかで判定）
const TARGET_FACILITY = "札幌市東区民センター";
// 対象部屋（正規化後）
const TARGET_ROOMS = ["さくら", "かっこう", "和室全室"];

function parseArgs() {
  const args = process.argv.slice(2);
  const params = { months: 2 };
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i].replace(/^--/, "");
    params[key] = args[i + 1];
  }
  params.months = parseInt(params.months) || 2;

  const userId = process.env.SAPPORO_COMMUNITY_USER_ID;
  const password = process.env.SAPPORO_COMMUNITY_PASSWORD;
  if (!userId || !password) {
    console.error(
      "環境変数 SAPPORO_COMMUNITY_USER_ID, SAPPORO_COMMUNITY_PASSWORD を設定してください"
    );
    process.exit(1);
  }

  return { months: params.months, userId, password };
}

/**
 * サービス時間外・エラーページ遷移を検知してプロセス終了する
 */
function assertNotErrorPage(page) {
  const url = page.url();
  if (url.includes("OutsideServiceTime.html")) {
    console.error("サービス時間外のため取得できません:", url);
    process.exit(1);
  }
  if (url.includes("HttpClientError.html")) {
    console.error("HTTPエラーページに遷移しました:", url);
    process.exit(1);
  }
}

/**
 * ページ遷移を伴う操作のヘルパー
 */
async function nav(page, action) {
  await Promise.all([
    page.waitForNavigation({ timeout: 20000 }).catch(() => {}),
    action(),
  ]);
  await page.waitForTimeout(1200);
  assertNotErrorPage(page);
}

/**
 * 和暦文字列 "令和YY年MM月DD日（曜）" を西暦 "YYYY-MM-DD" に変換する
 *
 * - 令和元年 = 2019 なので 令和N年 = 2018 + N
 * - 他の元号は現時点では未対応（東区民センターは令和固定）
 */
function convertJpEraToIsoDate(text) {
  if (!text) return null;
  const m = text.match(/令和\s*(\d{1,2})年\s*(\d{1,2})月\s*(\d{1,2})日/);
  if (!m) return null;
  const year = 2018 + parseInt(m[1], 10);
  const month = String(parseInt(m[2], 10)).padStart(2, "0");
  const day = String(parseInt(m[3], 10)).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * 申込内容テキストから部屋名を正規化して抽出する
 *
 * 例:
 *   "札幌市東区民センターさくら（和室） 利用申込"    → "さくら"
 *   "札幌市東区民センターかっこう（和室） 利用申込"  → "かっこう"
 *   "札幌市東区民センター和室全室 利用申込"          → "和室全室"
 */
function extractRoomName(text) {
  if (!text || !text.includes(TARGET_FACILITY)) return null;
  // 長いキーから先に判定することで "和室全室" が "さくら（和室）" に誤マッチしないようにする
  if (text.includes("和室全室")) return "和室全室";
  for (const room of TARGET_ROOMS) {
    if (room === "和室全室") continue;
    if (text.includes(room)) return room;
  }
  return null;
}

/**
 * 時刻文字列 "HH:MM" を分単位に変換。不正な場合は null。
 */
function toMinutes(hhmm) {
  if (!hhmm || !/^\d{1,2}:\d{2}$/.test(hhmm)) return null;
  const [h, m] = hhmm.split(":").map((v) => parseInt(v, 10));
  return h * 60 + m;
}

/**
 * 履歴テーブルのHTMLから1ページ分のデータ行を抽出する
 *
 * 実構造（調査報告書 §5）:
 *   td[0] 状態 / td[1] 利用日 / td[2] 開始時刻 / td[3] "～" / td[4] 終了時刻 /
 *   td[5] 申込内容 / td[6] 申込日
 *
 * 時刻フォーマットでヘッダー行・ページャー行を弾く。
 */
async function scrapeCurrentPage(page) {
  const rawRows = await page.$$eval(
    '#ctl00_cphMain_gvView tr',
    (trs) =>
      trs.map((tr) =>
        Array.from(tr.querySelectorAll("td")).map((td) => td.textContent.trim())
      )
  );

  const timeRe = /^\d{1,2}:\d{2}$/;
  const results = [];
  for (const cells of rawRows) {
    if (cells.length !== 7) continue;
    if (!timeRe.test(cells[2]) || !timeRe.test(cells[4])) continue;
    results.push({
      statusText: cells[0],
      dateText: cells[1],
      startTime: cells[2],
      endTime: cells[4],
      rawContent: cells[5],
    });
  }
  return results;
}

/**
 * 現在のページに「次ページ」リンクが存在する場合、クリックしてページ遷移する。
 *
 * ASP.NET GridView のページャーは `javascript:__doPostBack('ctl00$cphMain$gvView','Page$N')` 形式。
 * 現在ページ（リンクではなく span）の次の兄弟リンクを探して押下する。
 *
 * @returns {Promise<boolean>} 次ページに遷移した場合は true、無ければ false
 */
async function goToNextPageIfAny(page) {
  // 現在ページ番号（span.text）と、次ページリンク（a.text）を同時に取得
  const next = await page.evaluate(() => {
    const gv = document.getElementById("ctl00_cphMain_gvView");
    if (!gv) return null;
    // ページャー行は tr 内に td > table > tr > td > (a|span) の形
    // 単純にテーブル全体から「数字のspan」と「数字のa」を集めて判断
    const spans = Array.from(gv.querySelectorAll("span")).filter((s) =>
      /^\d+$/.test(s.textContent.trim())
    );
    const anchors = Array.from(gv.querySelectorAll("a")).filter((a) =>
      /Page\$\d+/.test(a.getAttribute("href") || "")
    );
    if (spans.length === 0) return { currentPage: 1, anchors: anchors.map((a) => a.textContent.trim()) };
    const currentPage = parseInt(spans[0].textContent.trim(), 10);
    const nextText = String(currentPage + 1);
    const nextLink = anchors.find((a) => a.textContent.trim() === nextText);
    return { currentPage, hasNext: Boolean(nextLink), nextText };
  });

  if (!next || !next.hasNext) return false;

  await nav(page, async () => {
    await page.evaluate((nextText) => {
      const gv = document.getElementById("ctl00_cphMain_gvView");
      const anchors = Array.from(gv.querySelectorAll("a")).filter((a) =>
        /Page\$\d+/.test(a.getAttribute("href") || "")
      );
      const link = anchors.find((a) => a.textContent.trim() === nextText);
      if (link) link.click();
    }, next.nextText);
  });

  return true;
}

async function main() {
  const { months, userId, password } = parseArgs();

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    acceptDownloads: false,
  });
  const page = await context.newPage();

  try {
    // 1. ログイン画面へ遷移
    console.error("ログイン画面へ遷移中...");
    await page.goto(LOGIN_URL, { timeout: 20000 });
    await page.waitForLoadState("networkidle").catch(() => {});
    assertNotErrorPage(page);

    // 2. ログイン情報を入力して送信
    console.error("ログイン中...");
    await page.fill("#ctl00_cphMain_tbUserno", userId);
    await page.fill("#ctl00_cphMain_tbPassword", password);
    await nav(page, () => page.click("#ctl00_cphMain_btnReg"));

    // ログイン成功判定: メニュー画面の「申込履歴・結果」ボタンが存在するか
    const historyBtn = await page.$("#ctl00_cphMain_WucImgBtnHistory_imgbtnMain");
    if (!historyBtn) {
      console.error("ログインに失敗しました（メニュー画面に到達できませんでした）");
      process.exit(1);
    }
    console.error("ログイン成功");

    // 3. 申込履歴・結果へ遷移
    console.error("申込履歴画面へ遷移中...");
    await nav(page, () => historyBtn.click());

    // 4. 全ページをスクレイピング
    const allRows = [];
    let pageIndex = 1;
    while (true) {
      console.error(`履歴ページ ${pageIndex} をスクレイピング中...`);
      const rows = await scrapeCurrentPage(page);
      allRows.push(...rows);
      console.error(`  → ${rows.length}件のデータ行を抽出`);
      const moved = await goToNextPageIfAny(page);
      if (!moved) break;
      pageIndex++;
    }

    // 5. フィルタ・整形
    const reservations = [];
    for (const row of allRows) {
      // 取消済は除外
      if (row.statusText.includes("取消")) continue;
      // 東区民センター以外は除外（+ 部屋名正規化）
      const room = extractRoomName(row.rawContent);
      if (!room) continue;
      // 日付変換
      const date = convertJpEraToIsoDate(row.dateText);
      if (!date) continue;
      // 夜間フィルタ: 開始時刻 17:00 以降
      const startMin = toMinutes(row.startTime);
      if (startMin == null || startMin < 17 * 60) continue;

      reservations.push({
        date,
        room,
        status: row.statusText,
        startTime: row.startTime,
        endTime: row.endTime,
        rawContent: row.rawContent,
      });
    }

    // 6. 対象月フィルタ（JST基準で今月〜(months-1)月後まで）
    const jstNow = new Date(
      new Date().toLocaleString("en-US", { timeZone: "Asia/Tokyo" })
    );
    const rangeStartYear = jstNow.getFullYear();
    const rangeStartMonth = jstNow.getMonth() + 1;
    const rangeEnd = new Date(rangeStartYear, rangeStartMonth - 1 + months, 1);
    const rangeStartStr = `${rangeStartYear}-${String(rangeStartMonth).padStart(2, "0")}-01`;
    const rangeEndStr = `${rangeEnd.getFullYear()}-${String(rangeEnd.getMonth() + 1).padStart(2, "0")}-01`;
    const filtered = reservations.filter(
      (r) => r.date >= rangeStartStr && r.date < rangeEndStr
    );

    console.error(
      `\n合計: 抽出${allRows.length}件 → 東区民センター夜間${reservations.length}件 → 対象期間内${filtered.length}件`
    );

    // stdoutにJSON出力
    console.log(JSON.stringify(filtered, null, 2));
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  process.exit(1);
});
