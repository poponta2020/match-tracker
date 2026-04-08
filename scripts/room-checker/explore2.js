const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  console.log("=== 1. トップページにアクセス ===");
  await page.goto("https://k2.p-kashikan.jp/kaderu27/index.php");
  await page.waitForLoadState("networkidle");

  // onclick属性を持つ要素を調べる
  console.log("\n=== onclick属性を持つ要素 ===");
  const onclickElements = await page.evaluate(() => {
    return Array.from(document.querySelectorAll("[onclick]")).map((el) => ({
      tag: el.tagName,
      text: el.textContent.trim().substring(0, 50),
      onclick: el.getAttribute("onclick"),
    }));
  });
  onclickElements.forEach((e) =>
    console.log(`  <${e.tag}> [${e.text}] onclick="${e.onclick}"`)
  );

  // JavaScriptの関数定義を調べる
  console.log("\n=== script要素の内容 ===");
  const scripts = await page.evaluate(() => {
    return Array.from(document.querySelectorAll("script"))
      .map((s) => s.textContent.trim())
      .filter((s) => s.length > 0);
  });
  scripts.forEach((s, i) => console.log(`script ${i}: ${s.substring(0, 500)}`));

  // 「施設の空きを見る」ボタンをクリック
  console.log("\n=== 2. 「施設の空きを見る」をクリック ===");

  // ネットワークリクエストを監視
  page.on("request", (req) => {
    if (req.method() === "POST") {
      console.log(`  POST: ${req.url()}`);
      console.log(`  Body: ${req.postData()}`);
    }
  });

  // 「施設」を含むテキストのリンクをクリック
  const clicked = await page.evaluate(() => {
    const links = Array.from(document.querySelectorAll("a"));
    for (const link of links) {
      if (link.textContent.includes("施設") && link.textContent.includes("空き")) {
        link.click();
        return link.getAttribute("onclick") || "clicked (no onclick attr)";
      }
    }
    return null;
  });
  console.log("クリック結果:", clicked);

  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(3000);

  console.log("遷移後URL:", page.url());

  // 遷移後のページ内容を確認
  const afterContent = await page.evaluate(() => {
    const selects = Array.from(document.querySelectorAll("select")).map((s) => ({
      name: s.name || s.id,
      options: Array.from(s.options).map((o) => ({
        value: o.value,
        text: o.textContent.trim(),
      })),
    }));

    const links = Array.from(document.querySelectorAll("a")).map((a) => ({
      text: a.textContent.trim().substring(0, 80),
      href: a.href,
      onclick: a.getAttribute("onclick"),
    }));

    const bodyText = document.body.textContent;
    const hasSuzuran = bodyText.includes("すずらん");

    // フォーム
    const forms = Array.from(document.querySelectorAll("form")).map((f) => ({
      action: f.action,
      method: f.method,
    }));

    // hidden inputs
    const hiddens = Array.from(
      document.querySelectorAll('input[type="hidden"]')
    ).map((i) => ({
      name: i.name,
      value: i.value,
    }));

    return { selects, links: links.slice(0, 30), hasSuzuran, forms, hiddens };
  });

  console.log("\n=== 遷移後ページ ===");
  console.log("すずらん含む:", afterContent.hasSuzuran);
  console.log("forms:", JSON.stringify(afterContent.forms, null, 2));
  console.log("hiddens:", JSON.stringify(afterContent.hiddens, null, 2));
  console.log("selects:", JSON.stringify(afterContent.selects, null, 2));
  console.log("\nリンク:");
  afterContent.links.forEach((l) =>
    console.log(`  [${l.text}] href=${l.href} onclick=${l.onclick}`)
  );

  await page.screenshot({
    path: "c:/Users/popon/match-tracker/scripts/room-checker/screenshot2.png",
    fullPage: true,
  });
  console.log("\nスクリーンショット2を保存しました");

  // すずらんが見つかったら、その周辺を調べる
  if (afterContent.hasSuzuran) {
    console.log("\n=== すずらん周辺の情報 ===");
    const suzuranInfo = await page.evaluate(() => {
      const body = document.body.textContent;
      const idx = body.indexOf("すずらん");
      return body.substring(Math.max(0, idx - 200), idx + 200);
    });
    console.log(suzuranInfo);
  }

  await page.waitForTimeout(3000);
  await browser.close();
})();
