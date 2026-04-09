const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  // 1. トップページ → 施設空き状況ページ
  await page.goto("https://k2.p-kashikan.jp/kaderu27/index.php");
  await page.waitForLoadState("networkidle");

  // 施設の空きを見る (クリックでPOST遷移)
  await Promise.all([
    page.waitForNavigation(),
    page.evaluate(() => gotoPage("srch_sst")),
  ]);
  await page.waitForTimeout(2000);

  console.log("=== 今日の空き状況テーブル解析 ===");

  // テーブルヘッダー（時間帯）を取得
  const headerData = await page.evaluate(() => {
    const tables = document.querySelectorAll("table");
    for (const table of tables) {
      const rows = Array.from(table.querySelectorAll("tr"));
      for (const row of rows) {
        if (row.textContent.includes("施設") && row.textContent.includes("9")) {
          const cells = Array.from(row.querySelectorAll("td, th"));
          return cells.map((c) => c.textContent.trim());
        }
      }
    }
    return null;
  });
  console.log("ヘッダー:", headerData);

  // すずらん行の詳細
  const suzuranDetail = await page.evaluate(() => {
    const rows = document.querySelectorAll("tr");
    for (const row of rows) {
      if (!row.textContent.includes("すずらん")) continue;

      const cells = Array.from(row.querySelectorAll("td"));
      return cells.map((cell, i) => {
        const imgs = Array.from(cell.querySelectorAll("img")).map((img) => ({
          src: img.src,
          alt: img.alt,
          title: img.title,
        }));
        const anchors = Array.from(cell.querySelectorAll("a")).map((a) => ({
          text: a.textContent.trim(),
          onclick: a.getAttribute("onclick"),
        }));
        return {
          index: i,
          text: cell.textContent.trim().substring(0, 30),
          className: cell.className,
          innerHTML: cell.innerHTML.substring(0, 300),
          imgs,
          anchors,
        };
      });
    }
    return null;
  });

  console.log("\n=== すずらん行の詳細 ===");
  if (suzuranDetail) {
    suzuranDetail.forEach((cell) => {
      console.log(
        `  cell[${cell.index}]: text="${cell.text}" class="${cell.className}"`
      );
      console.log(`    innerHTML: ${cell.innerHTML}`);
      if (cell.imgs.length > 0) console.log(`    imgs:`, cell.imgs);
      if (cell.anchors.length > 0) console.log(`    anchors:`, cell.anchors);
    });
  }

  // 別の日付 (12日)
  console.log("\n=== 12日に切り替え ===");
  await Promise.all([
    page.waitForNavigation(),
    page.evaluate(() => clickDay("12")),
  ]);
  await page.waitForTimeout(2000);

  const suzuranDay12 = await page.evaluate(() => {
    const rows = document.querySelectorAll("tr");
    for (const row of rows) {
      if (!row.textContent.includes("すずらん")) continue;
      const cells = Array.from(row.querySelectorAll("td"));
      return cells.map((cell, i) => ({
        index: i,
        text: cell.textContent.trim().substring(0, 30),
        className: cell.className,
        innerHTML: cell.innerHTML.substring(0, 300),
      }));
    }
    return null;
  });
  console.log("12日のすずらん:");
  if (suzuranDay12) {
    suzuranDay12.forEach((cell) => {
      console.log(`  cell[${cell.index}]: text="${cell.text}" class="${cell.className}"`);
      console.log(`    innerHTML: ${cell.innerHTML}`);
    });
  }

  await browser.close();
  console.log("\n完了");
})();
