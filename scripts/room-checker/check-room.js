const { chromium } = require("playwright");

const SITE_URL = "https://k2.p-kashikan.jp/kaderu27/index.php";
const TARGET_ROOM = "すずらん";

const TIME_LABELS = ["午前(9-12)", "午後(13-16)", "夜間(17-21)"];

/**
 * 指定した年月日のすずらんの空き状況を取得する
 * @param {number} year
 * @param {number} month
 * @param {number} day
 * @returns {Promise<{date: string, room: string, slots: Array<{label: string, status: string}>}>}
 */
async function checkRoom(year, month, day) {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    // 1. トップページ → 施設空き状況ページ
    await page.goto(SITE_URL);
    await page.waitForLoadState("networkidle");

    await Promise.all([
      page.waitForNavigation(),
      page.evaluate(() => gotoPage("srch_sst")),
    ]);
    await page.waitForTimeout(1500);

    // 2. 現在表示されている年月を確認し、必要なら月を切り替える
    const currentYM = await page.evaluate(() => {
      const input = document.querySelector('input[name="UseYM"]');
      return input ? input.value : null;
    });

    const targetYM = `${year}${String(month).padStart(2, "0")}`;
    if (currentYM !== targetYM) {
      // 月を切り替え
      await Promise.all([
        page.waitForNavigation(),
        page.evaluate(
          (y, m) => showCalendar(y, m),
          year,
          month
        ),
      ]);
      await page.waitForTimeout(1500);
    }

    // 3. 日付をクリック
    await Promise.all([
      page.waitForNavigation(),
      page.evaluate((d) => clickDay(String(d)), day),
    ]);
    await page.waitForTimeout(1500);

    // 4. すずらんの空き状況を取得
    const result = await page.evaluate((roomName) => {
      const rows = document.querySelectorAll("tr");
      for (const row of rows) {
        const nameCell = row.querySelector("td.name");
        if (!nameCell || !nameCell.textContent.includes(roomName)) continue;

        const cells = Array.from(row.querySelectorAll("td"));
        // スペーサーセル(白背景&nbsp;)を除外して、実データセルのみ取得
        const dataSlots = cells.slice(1).filter((c) => {
          // スペーサーセル(&nbsp;のみ、白背景)を除外
          const text = c.textContent.trim();
          const bg = c.style.backgroundColor;
          if (text === "" && bg === "rgb(255, 255, 255)") return false;
          if (text === "" && bg === "") return false;
          return true;
        });

        return dataSlots.map((c) => ({
          status: c.textContent.trim(),
          bg: c.style.backgroundColor,
        }));
      }
      return null;
    }, TARGET_ROOM);

    if (!result) {
      return {
        date: `${year}/${month}/${day}`,
        room: TARGET_ROOM,
        error: "部屋が見つかりませんでした",
        slots: [],
      };
    }

    const slots = result.map((s, i) => {
      let statusText;
      if (s.status === "○") statusText = "空き";
      else if (s.status === "×") statusText = "予約済";
      else if (s.status === "-") statusText = "利用不可";
      else if (s.status === "●") statusText = "要問合せ";
      else if (s.status === "休館") statusText = "休館";
      else statusText = s.status;

      return {
        label: TIME_LABELS[i] || `スロット${i}`,
        status: s.status,
        statusText,
        available: s.status === "○",
      };
    });

    return {
      date: `${year}/${String(month).padStart(2, "0")}/${String(day).padStart(2, "0")}`,
      room: TARGET_ROOM,
      slots,
    };
  } finally {
    await browser.close();
  }
}

// --- メイン実行 ---
async function main() {
  // コマンドライン引数: node check-room.js [YYYY-MM-DD]
  const dateArg = process.argv[2];
  let year, month, day;

  if (dateArg) {
    const parts = dateArg.split("-");
    year = parseInt(parts[0]);
    month = parseInt(parts[1]);
    day = parseInt(parts[2]);
    if (isNaN(year) || isNaN(month) || isNaN(day)) {
      console.error("日付の形式が正しくありません。YYYY-MM-DD で指定してください。");
      process.exit(1);
    }
  } else {
    // デフォルト: 今日
    const now = new Date();
    year = now.getFullYear();
    month = now.getMonth() + 1;
    day = now.getDate();
  }

  console.log(`\n📅 ${year}/${String(month).padStart(2, "0")}/${String(day).padStart(2, "0")} の ${TARGET_ROOM} 空き状況を確認中...\n`);

  const result = await checkRoom(year, month, day);

  if (result.error) {
    console.log(`❌ エラー: ${result.error}`);
    return;
  }

  console.log(`部屋: ${result.room}`);
  console.log(`日付: ${result.date}`);
  console.log("─".repeat(30));

  let hasAvailable = false;
  for (const slot of result.slots) {
    const icon = slot.available ? "🟢" : "🔴";
    console.log(`  ${slot.label}: ${icon} ${slot.statusText}`);
    if (slot.available) hasAvailable = true;
  }

  console.log("─".repeat(30));
  if (hasAvailable) {
    console.log("✅ 空きあり！");
  } else {
    console.log("❌ 空きなし");
  }
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  process.exit(1);
});
