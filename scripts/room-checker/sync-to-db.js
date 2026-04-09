const { chromium } = require("playwright");
const { Client } = require("pg");

const SITE_URL = "https://k2.p-kashikan.jp/kaderu27/index.php";

// 対象の4部屋
const TARGET_ROOMS = ["すずらん", "はまなす", "あかなら", "えぞまつ"];

// 夜間スロットのインデックス（午前=0, 午後=1, 夜間=2）
const EVENING_SLOT_INDEX = 2;

/**
 * 指定年月日の全対象部屋の夜間空き状況を取得する
 */
async function checkAllRooms(page, year, month, day) {
  // 現在表示されている年月を確認し、必要なら月を切り替える
  const currentYM = await page.evaluate(() => {
    const input = document.querySelector('input[name="UseYM"]');
    return input ? input.value : null;
  });

  const targetYM = `${year}${String(month).padStart(2, "0")}`;
  if (currentYM !== targetYM) {
    await Promise.all([
      page.waitForNavigation(),
      page.evaluate((y, m) => showCalendar(y, m), year, month),
    ]);
    await page.waitForTimeout(1500);
  }

  // 日付をクリック
  await Promise.all([
    page.waitForNavigation(),
    page.evaluate((d) => clickDay(String(d)), day),
  ]);
  await page.waitForTimeout(1500);

  // 全対象部屋の空き状況を取得
  const results = await page.evaluate((roomNames) => {
    const rows = document.querySelectorAll("tr");
    const roomResults = {};

    for (const row of rows) {
      const nameCell = row.querySelector("td.name");
      if (!nameCell) continue;

      const matchedRoom = roomNames.find((r) => nameCell.textContent.includes(r));
      if (!matchedRoom) continue;

      const cells = Array.from(row.querySelectorAll("td"));
      const dataSlots = cells.slice(1).filter((c) => {
        const text = c.textContent.trim();
        const bg = c.style.backgroundColor;
        if (text === "" && bg === "rgb(255, 255, 255)") return false;
        if (text === "" && bg === "") return false;
        return true;
      });

      roomResults[matchedRoom] = dataSlots.map((c) => c.textContent.trim());
    }

    return roomResults;
  }, TARGET_ROOMS);

  // 夜間スロットのみ抽出
  const eveningResults = {};
  for (const room of TARGET_ROOMS) {
    const slots = results[room];
    if (slots && slots.length > EVENING_SLOT_INDEX) {
      eveningResults[room] = slots[EVENING_SLOT_INDEX];
    } else {
      eveningResults[room] = null;
    }
  }

  return eveningResults;
}

/**
 * PostgreSQLにUPSERT
 */
async function upsertToDb(dbClient, roomName, targetDate, timeSlot, status) {
  const query = `
    INSERT INTO room_availability_cache (room_name, target_date, time_slot, status, checked_at)
    VALUES ($1, $2, $3, $4, NOW())
    ON CONFLICT (room_name, target_date, time_slot)
    DO UPDATE SET status = $4, checked_at = NOW()
  `;
  await dbClient.query(query, [roomName, targetDate, timeSlot, status]);
}

async function main() {
  // DB接続設定（環境変数から取得）
  const dbClient = new Client({
    connectionString: process.env.DATABASE_URL,
  });

  let browser;
  try {
    await dbClient.connect();
    console.log("DB接続成功");

    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();

    // トップページ → 施設空き状況ページ
    await page.goto(SITE_URL);
    await page.waitForLoadState("networkidle");
    await Promise.all([
      page.waitForNavigation(),
      page.evaluate(() => gotoPage("srch_sst")),
    ]);
    await page.waitForTimeout(1500);

    // 今日〜40日先の日付を対象
    const today = new Date();
    const endDate = new Date(today);
    endDate.setDate(endDate.getDate() + 40);

    let totalUpserted = 0;
    const current = new Date(today);

    while (current <= endDate) {
      const year = current.getFullYear();
      const month = current.getMonth() + 1;
      const day = current.getDate();
      const dateStr = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

      try {
        const results = await checkAllRooms(page, year, month, day);

        for (const [roomName, status] of Object.entries(results)) {
          if (status !== null) {
            await upsertToDb(dbClient, roomName, dateStr, "evening", status);
            totalUpserted++;
          }
        }

        console.log(`${dateStr}: ${Object.entries(results).map(([r, s]) => `${r}=${s || "N/A"}`).join(", ")}`);
      } catch (err) {
        console.error(`${dateStr}: エラー - ${err.message}`);
      }

      current.setDate(current.getDate() + 1);
    }

    console.log(`\n完了: ${totalUpserted}件 upsert`);
  } finally {
    if (browser) await browser.close();
    await dbClient.end();
  }
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  process.exit(1);
});
