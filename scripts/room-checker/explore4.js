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

  // 12日に切り替え（空きデータがある日）
  await Promise.all([
    page.waitForNavigation(),
    page.evaluate(() => clickDay("12")),
  ]);
  await page.waitForTimeout(2000);

  // テーブル全体の構造を解析
  const fullTable = await page.evaluate(() => {
    const tables = document.querySelectorAll("table");
    for (const table of tables) {
      if (!table.textContent.includes("すずらん")) continue;

      const rows = Array.from(table.querySelectorAll("tr"));
      return rows.map((row, rowIdx) => {
        const cells = Array.from(row.querySelectorAll("td, th"));
        return {
          rowIdx,
          cells: cells.map((cell) => ({
            tag: cell.tagName,
            text: cell.textContent.trim().substring(0, 40),
            colspan: cell.getAttribute("colspan"),
            rowspan: cell.getAttribute("rowspan"),
            className: cell.className,
            bgColor: cell.style.backgroundColor,
          })),
        };
      }).filter((r) =>
        // ヘッダー行、すずらん行、花行（比較用）を含む
        r.cells.some((c) =>
          c.text.includes("すずらん") ||
          c.text.includes("施設") ||
          c.text.includes("花(")
        )
      );
    }
    return null;
  });

  console.log("=== テーブル構造（ヘッダー・すずらん・花） ===");
  if (fullTable) {
    fullTable.forEach((row) => {
      console.log(`\nRow ${row.rowIdx}:`);
      row.cells.forEach((c, i) => {
        const colspanStr = c.colspan ? ` colspan=${c.colspan}` : "";
        const rowspanStr = c.rowspan ? ` rowspan=${c.rowspan}` : "";
        const bgStr = c.bgColor ? ` bg=${c.bgColor}` : "";
        console.log(
          `  [${i}] <${c.tag}${colspanStr}${rowspanStr}${bgStr}> "${c.text}" class="${c.className}"`
        );
      });
    });
  }

  // すずらん行のHTMLをまるごと取得
  console.log("\n=== すずらん行のHTML全体 ===");
  const suzuranHtml = await page.evaluate(() => {
    const rows = document.querySelectorAll("tr");
    for (const row of rows) {
      if (row.textContent.includes("すずらん") && !row.textContent.includes("施設")) {
        return row.innerHTML;
      }
    }
    return null;
  });
  console.log(suzuranHtml);

  await browser.close();
  console.log("\n完了");
})();
