/**
 * 札幌市東区民センター「かっこう（和室）」夜間(18-21)の空き状況を
 * room_availability_cache に同期するスクリプト。
 *
 * サイト仕様・遷移フロー・DOM構造の詳細は以下を参照:
 *   docs/operations/higashi-availability-scraping-report.md
 *
 * 同期内容:
 *   room_name  = 'かっこう'
 *   time_slot  = 'evening'
 *   status     = '○' (利用可) / '×' (予約済) / '-' (不明/対象外)
 *   checked_at = NOW()
 *
 * Usage:
 *   node sync-higashi-availability-to-db.js [--months 2]
 *
 * 環境変数:
 *   DATABASE_URL or DB_URL + DB_USERNAME + DB_PASSWORD
 */
const { chromium } = require("playwright");
const { Client } = require("pg");

const START_URL = "https://sapporo-community.jp/UserWebApp/Form/UserMenu.aspx";

const FACILITY_CODE = "103"; // 札幌市東区民センター
const ROOM_CODE = "041";     // かっこう（和室）
const ROOM_NAME_DB = "かっこう";
const TIME_SLOT = "evening";

/** 列インデックス（月表示テーブル）: 0=日付, 1=曜日, 2=午前, 3=昼食, 4=午後, 5=夕食, 6=夜間, 7=延長 */
const NIGHT_COL_INDEX = 6;

function parseArgs(argv) {
  const args = { months: 2 };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === "--months" && argv[i + 1]) {
      args.months = parseInt(argv[++i], 10);
    }
  }
  if (!Number.isInteger(args.months) || args.months < 1) {
    throw new Error("--months は 1 以上の整数で指定してください");
  }
  return args;
}

function buildConnectionString() {
  let cs = process.env.DATABASE_URL;
  if (!cs && process.env.DB_URL) {
    const dbUrl = process.env.DB_URL.replace(/^jdbc:/, "");
    const username = process.env.DB_USERNAME;
    const password = process.env.DB_PASSWORD;
    if (username && password) {
      const url = new URL(dbUrl);
      url.username = username;
      url.password = password;
      cs = url.toString();
    } else {
      cs = dbUrl;
    }
  }
  if (!cs) {
    throw new Error("DATABASE_URL または DB_URL 環境変数が設定されていません");
  }
  return cs;
}

function jstToday() {
  const jstNow = new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Tokyo" }));
  return { year: jstNow.getFullYear(), month: jstNow.getMonth() + 1 };
}

function nextMonth(year, month) {
  return month === 12 ? { year: year + 1, month: 1 } : { year, month: month + 1 };
}

/** 月表示ページへの遷移（UserMenu → SearchType → Facilities → facility/room select → RoomSelect forward） */
async function navigateToMonthView(page) {
  await page.goto(START_URL, { waitUntil: "networkidle" });

  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.click("#ctl00_cphMain_wucImgBtnVacantRoomsSearchLogout_imgbtnMain"),
  ]);

  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.click("#ctl00_cphMain_WucImageButton1_imgbtnMain"),
  ]);

  // 施設選択（AutoPostBack）
  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.selectOption("#ctl00_cphMain_WucFacilitySelect_ddlFacilities", FACILITY_CODE),
  ]);
  await page.waitForTimeout(1000);

  // 部屋選択（AutoPostBack）
  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.selectOption("#ctl00_cphMain_wucRoomSelect_ddlRooms", ROOM_CODE),
  ]);
  await page.waitForTimeout(1000);

  // 部屋単位の月表示へ
  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.click("#ctl00_cphMain_btFwdRoomSelect"),
  ]);
  await page.waitForTimeout(1000);

  if (!page.url().includes("SsfSvrRoomAvailabilityMonth")) {
    throw new Error(`月表示ページへの遷移に失敗: ${page.url()}`);
  }
}

/** 現在表示されている月の全日付の夜間ステータスを抽出 */
async function extractMonthStatuses(page) {
  return await page.evaluate((nightColIdx) => {
    const table = document.querySelector("#ctl00_cphMain_tblMain");
    if (!table) return [];
    const out = [];
    for (const row of Array.from(table.rows)) {
      const cells = Array.from(row.cells);
      if (cells.length === 0) continue;
      const dayText = (cells[0].textContent || "").trim();
      if (!/^\d{1,2}$/.test(dayText)) continue;
      const day = parseInt(dayText, 10);
      // 休館日・祝日は列がマージされて 8列未満になるため、夜間列が無い場合は "-"
      if (cells.length < nightColIdx + 1) {
        out.push({ day, status: "-" });
        continue;
      }
      const nightCell = cells[nightColIdx];
      const cls = nightCell.className || "";
      const text = (nightCell.textContent || "").trim();
      let status;
      if (cls.includes("Reserved")) {
        status = "×";
      } else if (cls.includes("Available") && text === "○") {
        status = "○";
      } else {
        // NA / 予約不可 / バッファ時間帯
        status = "-";
      }
      out.push({ day, status });
    }
    return out;
  }, NIGHT_COL_INDEX);
}

/** 翌月へ遷移（__doPostBack なのでヘッダの月表記が切り替わるまで待機） */
async function goToNextMonth(page) {
  const prevHeader = await page.evaluate(() => {
    const t = document.querySelector("#ctl00_cphMain_tblMain");
    const headerTable = t ? t.closest("table.DefaultLayoutTable") || t.parentElement : null;
    // 「前月 令和XX年YY月 翌月」を含むセルのテキストを取得
    const cells = document.querySelectorAll("td");
    for (const c of cells) {
      const text = (c.textContent || "").trim();
      if (/前月\s*令和\d+年\d+月\s*翌月/.test(text)) return text;
    }
    return null;
  });

  await page.click("#ctl00_cphMain_lbtNextMonth");
  // postback 後にヘッダの月表記が変わるまで待つ
  await page.waitForFunction(
    (prev) => {
      const cells = document.querySelectorAll("td");
      for (const c of cells) {
        const text = (c.textContent || "").trim();
        if (/前月\s*令和\d+年\d+月\s*翌月/.test(text) && text !== prev) return true;
      }
      return false;
    },
    prevHeader,
    { timeout: 15000 }
  );
  await page.waitForLoadState("networkidle");
}

async function upsertStatus(db, targetDate, status) {
  const query = `
    INSERT INTO room_availability_cache (room_name, target_date, time_slot, status, checked_at)
    VALUES ($1, $2, $3, $4, NOW())
    ON CONFLICT (room_name, target_date, time_slot)
    DO UPDATE SET status = EXCLUDED.status, checked_at = NOW()
  `;
  await db.query(query, [ROOM_NAME_DB, targetDate, TIME_SLOT, status]);
}

async function main() {
  const { months } = parseArgs(process.argv);
  const connectionString = buildConnectionString();

  const db = new Client({ connectionString, ssl: { rejectUnauthorized: false } });
  await db.connect();
  console.log("DB接続成功");

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  });
  const page = await context.newPage();

  let totalUpserted = 0;
  try {
    await navigateToMonthView(page);

    let { year, month } = jstToday();
    for (let i = 0; i < months; i++) {
      const rows = await extractMonthStatuses(page);
      for (const { day, status } of rows) {
        const dateStr = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
        await upsertStatus(db, dateStr, status);
        totalUpserted++;
      }
      console.log(`${year}-${String(month).padStart(2, "0")}: ${rows.length}件 (${rows.filter(r => r.status === "○").length}空き / ${rows.filter(r => r.status === "×").length}予約済)`);

      if (i < months - 1) {
        await goToNextMonth(page);
        ({ year, month } = nextMonth(year, month));
      }
    }

    console.log(`\n完了: ${totalUpserted}件 upsert`);
  } finally {
    await browser.close();
    await db.end();
  }
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  console.error(err.stack);
  process.exit(1);
});
