const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  await page.goto("https://k2.p-kashikan.jp/kaderu27/index.php");
  await page.waitForLoadState("networkidle");

  await Promise.all([
    page.waitForNavigation(),
    page.evaluate(() => gotoPage("srch_sst")),
  ]);
  await page.waitForTimeout(2000);

  // 12日に切り替え
  await Promise.all([
    page.waitForNavigation(),
    page.evaluate(() => clickDay("12")),
  ]);
  await page.waitForTimeout(2000);

  // ヘッダー行の完全な構造
  const headerHtml = await page.evaluate(() => {
    const tables = document.querySelectorAll("table");
    for (const table of tables) {
      if (!table.textContent.includes("すずらん")) continue;
      // テーブルのすべての行を確認
      const rows = Array.from(table.querySelectorAll("tr"));
      const result = [];
      // 最初の数行を取得
      for (let i = 0; i < Math.min(5, rows.length); i++) {
        result.push(rows[i].innerHTML.substring(0, 500));
      }
      return result;
    }
  });

  console.log("=== テーブルの最初の行 ===");
  headerHtml.forEach((html, i) => console.log(`Row ${i}:\n${html}\n`));

  // 全施設の空き状況を取得（すずらんだけでなく比較用に複数）
  const allRooms = await page.evaluate(() => {
    const rows = document.querySelectorAll("tr");
    const results = [];
    for (const row of rows) {
      const nameCell = row.querySelector("td.name");
      if (!nameCell) continue;

      const cells = Array.from(row.querySelectorAll("td"));
      const timeSlots = cells.slice(1).filter((c) => {
        // スペーサーセル（白背景・空）を除外
        return c.textContent.trim() !== "" || c.style.backgroundColor !== "rgb(255, 255, 255)";
      });

      results.push({
        name: nameCell.textContent.trim(),
        slots: timeSlots.map((c) => ({
          text: c.textContent.trim(),
          bg: c.style.backgroundColor,
          id: c.id,
          mousedown: c.getAttribute("onmousedown"),
        })),
      });
    }
    return results;
  });

  console.log("=== 全施設の空き状況 ===");
  allRooms.forEach((room) => {
    const slots = room.slots
      .map((s) => {
        // 時間帯を抽出
        const timeMatch = s.mousedown?.match(/'(\d{8})'/);
        const time = timeMatch ? timeMatch[1] : "?";
        return `${s.text}(${time})`;
      })
      .join(" | ");
    console.log(`${room.name}: ${slots}`);
  });

  // すずらんのスロット詳細
  const suzuran = allRooms.find((r) => r.name.includes("すずらん"));
  if (suzuran) {
    console.log("\n=== すずらん詳細 ===");
    suzuran.slots.forEach((s, i) => {
      console.log(`  slot ${i}: text="${s.text}" bg="${s.bg}" id="${s.id}"`);
      if (s.mousedown) console.log(`    mousedown: ${s.mousedown}`);
    });
  }

  await browser.close();
  console.log("\n完了");
})();
