const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  console.log("=== 1. トップページにアクセス ===");
  await page.goto("https://k2.p-kashikan.jp/kaderu27/index.php");
  await page.waitForLoadState("networkidle");

  // ページタイトル
  const title = await page.title();
  console.log("タイトル:", title);

  // すべてのリンクを取得
  const links = await page.evaluate(() => {
    return Array.from(document.querySelectorAll("a")).map((a) => ({
      text: a.textContent.trim().substring(0, 50),
      href: a.href,
    }));
  });
  console.log("\n=== リンク一覧 ===");
  links.forEach((l) => console.log(`  [${l.text}] -> ${l.href}`));

  // 「施設の空きを見る」系のリンクを探す
  console.log("\n=== 2. 施設検索ページへ遷移 ===");
  const shisetsuLink = links.find(
    (l) =>
      l.text.includes("施設") ||
      l.text.includes("空き") ||
      l.text.includes("空室")
  );
  if (shisetsuLink) {
    console.log("施設リンク発見:", shisetsuLink.text, shisetsuLink.href);
    await page.click(`a:has-text("${shisetsuLink.text.substring(0, 20)}")`);
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(2000);

    // 遷移後のURL
    console.log("遷移後URL:", page.url());

    // 施設一覧を取得
    const facilityOptions = await page.evaluate(() => {
      // select要素を探す
      const selects = Array.from(document.querySelectorAll("select"));
      const result = {};
      selects.forEach((sel, i) => {
        result[`select_${i}_name=${sel.name || sel.id}`] = Array.from(
          sel.options
        ).map((o) => ({
          value: o.value,
          text: o.textContent.trim(),
        }));
      });

      // リンクやボタンも確認
      const allLinks = Array.from(document.querySelectorAll("a")).map((a) => ({
        text: a.textContent.trim().substring(0, 80),
        href: a.href,
      }));

      // テーブルの内容も確認
      const tables = Array.from(document.querySelectorAll("table")).map(
        (t) => t.textContent.trim().substring(0, 500)
      );

      return { selects: result, links: allLinks, tables };
    });

    console.log("\n=== select要素 ===");
    console.log(JSON.stringify(facilityOptions.selects, null, 2));

    console.log("\n=== リンク ===");
    facilityOptions.links.forEach((l) =>
      console.log(`  [${l.text}] -> ${l.href}`)
    );

    // すずらんを探す
    const suzuranLink = facilityOptions.links.find((l) =>
      l.text.includes("すずらん")
    );
    if (suzuranLink) {
      console.log("\n★ すずらん発見！:", suzuranLink);
    } else {
      console.log("\n--- すずらんがリンクに見つかりません。ページ全体を確認 ---");
      const bodyText = await page.evaluate(
        () => document.body.textContent
      );
      if (bodyText.includes("すずらん")) {
        console.log("ページテキスト内に「すずらん」あり");
        // すずらん前後のテキストを表示
        const idx = bodyText.indexOf("すずらん");
        console.log(
          "周辺テキスト:",
          bodyText.substring(Math.max(0, idx - 100), idx + 100)
        );
      } else {
        console.log("このページには「すずらん」は見つかりません");
      }

      console.log("\n=== テーブル内容 ===");
      facilityOptions.tables.forEach((t, i) =>
        console.log(`table ${i}: ${t.substring(0, 300)}`)
      );
    }
  }

  // 全ページのテキストからすずらんを検索
  console.log("\n=== 3. ページ全体のHTML構造 ===");
  const pageContent = await page.evaluate(() => {
    // フォーム要素
    const forms = Array.from(document.querySelectorAll("form")).map((f) => ({
      action: f.action,
      method: f.method,
      id: f.id,
    }));

    // input要素
    const inputs = Array.from(document.querySelectorAll("input")).map((i) => ({
      type: i.type,
      name: i.name,
      value: i.value,
      id: i.id,
    }));

    // iframeを確認
    const iframes = Array.from(document.querySelectorAll("iframe")).map(
      (f) => f.src
    );

    return { forms, inputs, iframes };
  });
  console.log("forms:", JSON.stringify(pageContent.forms, null, 2));
  console.log("inputs:", JSON.stringify(pageContent.inputs, null, 2));
  console.log("iframes:", pageContent.iframes);

  // スクリーンショットを保存
  await page.screenshot({
    path: "c:/Users/popon/match-tracker/scripts/room-checker/screenshot.png",
    fullPage: true,
  });
  console.log("\nスクリーンショットを保存しました");

  // 10秒待ってからブラウザを閉じる（手動確認用）
  console.log("\n10秒後にブラウザを閉じます...");
  await page.waitForTimeout(10000);
  await browser.close();
})();
