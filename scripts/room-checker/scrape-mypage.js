/**
 * かでる2・7 マイページ「予約申込一覧」スクレイピングスクリプト
 *
 * ログイン → マイページ → 予約申込一覧 を開き、
 * 予約済みの日付・部屋・時間帯を取得してJSONで出力する。
 *
 * Usage:
 *   node scrape-mypage.js [--months 2]
 *
 * Options:
 *   --months  取得する月数（今月を含む）。デフォルト2（今月+来月）
 *
 * 環境変数:
 *   KADERU_USER_ID  - 利用者ID
 *   KADERU_PASSWORD - パスワード
 *
 * 出力:
 *   JSON配列をstdoutに出力:
 *   [
 *     { "date": "2026-04-10", "room": "すずらん", "timeSlot": "evening", "status": "承認" },
 *     ...
 *   ]
 */

const { chromium } = require("playwright");

const SITE_URL = "https://k2.p-kashikan.jp/kaderu27/index.php";

// 対象のかでる和室名（樹=えぞまつ+あかなら、花=はまなす+すずらん の拡張名も含む）
const TARGET_ROOMS = ["すずらん", "はまなす", "あかなら", "えぞまつ", "樹", "花"];

function parseArgs() {
  const args = process.argv.slice(2);
  const params = { months: 2 };
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i].replace(/^--/, "");
    params[key] = args[i + 1];
  }
  params.months = parseInt(params.months) || 2;

  const userId = process.env.KADERU_USER_ID;
  const password = process.env.KADERU_PASSWORD;
  if (!userId || !password) {
    console.error(
      "環境変数 KADERU_USER_ID, KADERU_PASSWORD を設定してください"
    );
    process.exit(1);
  }

  return { months: params.months, userId, password };
}

/**
 * ページ遷移を伴う操作のヘルパー
 */
async function nav(page, action) {
  await Promise.all([
    page.waitForNavigation({ timeout: 15000 }).catch(() => {}),
    action(),
  ]);
  await page.waitForTimeout(1500);
}

/**
 * 利用日時の文字列から日付と時間帯を抽出する
 * 実際の形式: "2026(令和 8)年 4月13日 (月)17:00-21:00"
 */
function parseDateTimeCell(text) {
  if (!text) return null;

  // 日付部分を抽出: YYYY(...)年 M月D日
  const dateMatch = text.match(/(\d{4})\([^)]*\)年\s*(\d{1,2})月\s*(\d{1,2})日/);
  if (!dateMatch) return null;

  const year = dateMatch[1];
  const month = dateMatch[2].padStart(2, "0");
  const day = dateMatch[3].padStart(2, "0");
  const date = `${year}-${month}-${day}`;

  // 時間帯を判定
  let timeSlot = "unknown";
  if (text.includes("17:00") || text.includes("17：00")) {
    timeSlot = "evening";
  } else if (text.includes("13:00") || text.includes("13：00")) {
    timeSlot = "afternoon";
  } else if (text.includes("09:00") || text.includes("9:00") || text.includes("09：00")) {
    timeSlot = "morning";
  }

  return { date, timeSlot };
}

/**
 * 利用施設の文字列から対象の部屋名を抽出する
 * 実際の形式: "北海道立道民活動センターえぞまつ(24人)"
 */
function extractRoomName(text) {
  if (!text) return null;
  for (const room of TARGET_ROOMS) {
    if (text.includes(room)) return room;
  }
  return null;
}

/**
 * 指定年月の予約申込一覧テーブルをパースする
 */
async function scrapeMonth(page, year, month) {
  // ラジオボタンで年月を切り替え（onchange="this.form.submit()" でフォーム送信される）
  const monthStr = String(month).padStart(2, "0");
  const yearId = `#year${year}`;
  const monthId = `#month${monthStr}`;

  // 現在選択中の年月を確認し、異なる場合のみ切り替える
  const currentYear = await page.evaluate(() => {
    const checked = document.querySelector('input[name="DispYear"]:checked');
    return checked ? checked.value : null;
  });
  const currentMonth = await page.evaluate(() => {
    const checked = document.querySelector('input[name="DispMonth"]:checked');
    return checked ? checked.value : null;
  });

  if (currentYear !== String(year)) {
    await Promise.all([
      page.waitForNavigation({ timeout: 15000 }).catch(() => {}),
      page.click(yearId),
    ]);
    await page.waitForTimeout(1500);
  }

  if (currentMonth !== monthStr) {
    await Promise.all([
      page.waitForNavigation({ timeout: 15000 }).catch(() => {}),
      page.click(monthId),
    ]);
    await page.waitForTimeout(1500);
  }

  // テーブルの行をパース
  const rows = await page.evaluate(() => {
    const results = [];
    const tables = document.querySelectorAll("table");
    for (const table of tables) {
      const trs = table.querySelectorAll("tr");
      for (const tr of trs) {
        const tds = Array.from(tr.querySelectorAll("td, th"));
        if (tds.length >= 4) {
          results.push(tds.map((td) => td.textContent.trim()));
        }
      }
    }
    return results;
  });

  const reservations = [];
  for (const cells of rows) {
    // 実際のテーブル構造: 状況(0) | 申請番号(1) | 利用日時(2) | 利用施設(3) | (空)(4)
    if (cells.length < 4) continue;

    const statusText = cells[0]; // "利用済み", "予約", "取消" 等
    const dateTimeText = cells[2]; // "2026(令和 8)年 4月13日 (月)17:00-21:00"
    const facilityText = cells[3]; // "北海道立道民活動センターえぞまつ(24人)"

    // ヘッダー行はスキップ
    if (statusText.includes("状況") || dateTimeText.includes("利用日時")) continue;

    const parsed = parseDateTimeCell(dateTimeText);
    if (!parsed) continue;

    const room = extractRoomName(facilityText);
    if (!room) continue;

    reservations.push({
      date: parsed.date,
      room,
      timeSlot: parsed.timeSlot,
      status: statusText,
    });
  }

  return reservations;
}

async function main() {
  const { months, userId, password } = parseArgs();

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    // 1. ログイン
    console.error("ログイン中...");
    await page.goto(SITE_URL);
    await page.waitForLoadState("networkidle");
    await nav(page, () => page.evaluate(() => gotoPage("my_page")));
    await page.fill('input[name="loginID"]', userId);
    await page.fill('input[name="loginPwd"]', password);
    await nav(page, () => page.click('button[name="loginBtn"]'));

    // ログイン成功確認
    const isLoggedIn = await page.evaluate(() =>
      document.body.textContent.includes("マイページ") ||
      document.body.textContent.includes("ログアウト")
    );
    if (!isLoggedIn) {
      console.error("ログインに失敗しました");
      process.exit(1);
    }
    console.error("ログイン成功");

    // 2. マイページへ遷移（ログイン直後のページには予約申込一覧リンクがない）
    console.error("マイページへ遷移中...");
    await nav(page, () => page.evaluate(() => gotoPage("my_page")));

    // 3. 予約申込一覧へ遷移（gotoPage('rsv_list')）
    console.error("予約申込一覧へ遷移中...");
    await nav(page, () => page.evaluate(() => gotoPage("rsv_list")));
    await page.waitForLoadState("networkidle").catch(() => {});

    // 3. 対象月をスクレイピング
    const jstNow = new Date(
      new Date().toLocaleString("en-US", { timeZone: "Asia/Tokyo" })
    );
    const currentYear = jstNow.getFullYear();
    const currentMonth = jstNow.getMonth() + 1;

    const allReservations = [];

    for (let i = 0; i < months; i++) {
      let targetMonth = currentMonth + i;
      let targetYear = currentYear;
      if (targetMonth > 12) {
        targetMonth -= 12;
        targetYear++;
      }

      console.error(`${targetYear}年${targetMonth}月をスクレイピング中...`);
      const monthReservations = await scrapeMonth(page, targetYear, targetMonth);
      allReservations.push(...monthReservations);
      console.error(`  → ${monthReservations.length}件の予約を検出`);
    }

    // 4. 夜間のみフィルタして出力
    const eveningOnly = allReservations.filter((r) => r.timeSlot === "evening");
    console.error(
      `\n合計: ${allReservations.length}件中、夜間: ${eveningOnly.length}件`
    );

    // stdoutにJSON出力
    console.log(JSON.stringify(eveningOnly, null, 2));
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  process.exit(1);
});
