/**
 * 札幌市東区民センター「かっこう（和室）」月表示空き状況ページの探査スクリプト
 *
 * 目的:
 *   - SsfSvrRoomAvailabilityMonth.aspx の URL/遷移フロー
 *   - DOM 構造（月テーブル、日付×時間帯セル）
 *   - 空き記号（○/×/- 等）
 * を実地で確認し、タスク3の本番スクレイパ実装に備える。
 *
 * ログイン不要（公開ページ）。
 *
 * Usage:
 *   node explore-higashi-availability.js
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const TOP_URL = "https://sapporo-community.jp/UserWebApp/Form/TopMenu.aspx";

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const dump = (label, obj) => {
    console.log(`\n===== ${label} =====`);
    console.log(typeof obj === "string" ? obj : JSON.stringify(obj, null, 2));
  };

  console.log("\n[1] トップページ (ログイン不要):", TOP_URL);
  await page.goto(TOP_URL, { waitUntil: "networkidle" });
  console.log("  title:", await page.title());
  console.log("  url  :", page.url());

  // 空き状況検索（ログインなし）ボタンを探す
  const topButtons = await page.evaluate(() => {
    return Array.from(document.querySelectorAll("a, input[type=image], img, button"))
      .map((el) => ({
        tag: el.tagName,
        id: el.id || null,
        alt: el.getAttribute("alt") || null,
        text: (el.textContent || "").trim().substring(0, 60),
        href: el.getAttribute("href") || null,
      }))
      .filter((x) => x.id || x.alt || x.text);
  });
  dump("Top page buttons/links", topButtons);

  // 「空き状況検索」ボタン
  const vacantBtn = await page.$("#ctl00_cphMain_wucImgBtnVacantRoomsSearch_imgbtnMain, #ctl00_cphMain_wucImgBtnVacantRoomsSearchLogin_imgbtnMain");
  if (!vacantBtn) {
    console.error("空き状況検索ボタンが見つからない");
    await browser.close();
    process.exit(1);
  }
  await vacantBtn.click();
  await page.waitForLoadState("networkidle");
  console.log("\n[2] 空き状況検索: url =", page.url());

  // 「施設から検索」
  const byFacility = await page.$("#ctl00_cphMain_WucImageButton1_imgbtnMain");
  if (byFacility) {
    await byFacility.click();
    await page.waitForLoadState("networkidle");
    console.log("\n[3] 施設から検索: url =", page.url());
  }

  // 施設選択 dropdown
  const selects = await page.evaluate(() => {
    return Array.from(document.querySelectorAll("select")).map((s) => ({
      id: s.id,
      name: s.name,
      options: Array.from(s.options).slice(0, 50).map((o) => ({ value: o.value, text: o.textContent.trim() })),
    }));
  });
  dump("Selects on facility-search page", selects);

  // 札幌市東区民センター = 103 を選択
  const facilitySelect = await page.$("#ctl00_cphMain_WucFacilitySelect_ddlFacilities");
  if (facilitySelect) {
    await facilitySelect.selectOption("103");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1500);
    console.log("\n[4] 東区民センター選択後: url =", page.url());
  }

  // 施設選択後の部屋dropdownの有無
  const selectsAfterFacility = await page.evaluate(() => {
    return Array.from(document.querySelectorAll("select")).map((s) => ({
      id: s.id,
      name: s.name,
      options: Array.from(s.options).slice(0, 60).map((o) => ({ value: o.value, text: o.textContent.trim() })),
    }));
  });
  dump("Selects after facility=103", selectsAfterFacility);

  // 部屋選択: かっこう（041）
  const roomSelect = await page.$("#ctl00_cphMain_wucRoomSelect_ddlRooms");
  if (roomSelect) {
    await roomSelect.selectOption("041");
    await page.waitForTimeout(1500);
    console.log("\n[5] かっこう選択後: url =", page.url());
  }

  // 「決定」/「次へ」ボタン
  const nextBtn = await page.$("#ctl00_cphMain_btnDecide, #ctl00_cphMain_btnNext, input[id*=btnDecide], input[id*=btnNext]");
  if (nextBtn) {
    console.log("決定ボタン発見、クリック");
    await nextBtn.click();
    await page.waitForLoadState("networkidle");
    console.log("\n[6] 決定後: url =", page.url());
  } else {
    // alt もしくは入力系から探す
    const allBtns = await page.evaluate(() => {
      return Array.from(document.querySelectorAll("input[type=image], input[type=submit], input[type=button], a, img"))
        .map((el) => ({
          tag: el.tagName,
          id: el.id || null,
          alt: el.getAttribute("alt") || null,
          value: el.value || null,
          text: (el.textContent || "").trim().substring(0, 60),
        }))
        .filter((x) => x.id || x.alt || x.value || x.text);
    });
    dump("Page buttons (after room selection)", allBtns);
  }

  const pageHtml = await page.content();
  const htmlPath = path.join(__dirname, "higashi-availability-page.html");
  fs.writeFileSync(htmlPath, pageHtml, "utf8");
  console.log("\nHTMLダンプ:", htmlPath);

  // 月表示ページのDOM抽出
  console.log("\n[7] 月表示ページのDOM分析");
  const monthPageUrl = page.url();
  const tableInfo = await page.evaluate(() => {
    const tables = Array.from(document.querySelectorAll("table"));
    return tables.map((t, idx) => ({
      idx,
      id: t.id,
      className: t.className,
      rowCount: t.rows.length,
      colCount: t.rows[0] ? t.rows[0].cells.length : 0,
      text: t.textContent.trim().substring(0, 300),
    }));
  });
  dump("Tables on month-view page", tableInfo);

  // 最も大きなテーブル（空き状況カレンダー）を詳細分析
  const mainTable = await page.evaluate(() => {
    const tables = Array.from(document.querySelectorAll("table"));
    let best = null;
    for (const t of tables) {
      if (!best || t.rows.length > best.rows.length) best = t;
    }
    if (!best) return null;
    const rows = Array.from(best.rows).map((r) =>
      Array.from(r.cells).map((c) => ({
        text: c.textContent.trim().substring(0, 30),
        html: c.innerHTML.substring(0, 200),
        className: c.className,
      }))
    );
    return { id: best.id, className: best.className, rows: rows.slice(0, 10) };
  });
  dump("Main table (first 10 rows)", mainTable);

  console.log("\n[探査完了] 月表示ページURL:", monthPageUrl);
  await browser.close();
})().catch((e) => {
  console.error("ERROR:", e);
  process.exit(1);
});
