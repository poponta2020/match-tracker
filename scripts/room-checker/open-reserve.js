/**
 * かでる2・7 予約画面自動遷移スクリプト
 *
 * ログイン → 空き状況ページ → スロット選択 → 申込トレイまで自動遷移し、
 * ブラウザを開いたまま待機する。利用目的は未入力のまま（誤申込み防止）。
 *
 * Usage:
 *   node open-reserve.js --room すずらん --date 2026-04-10 --slot 0
 *
 * Options:
 *   --room   部屋名 (すずらん|はまなす|あかなら|えぞまつ)
 *   --date   日付 (YYYY-MM-DD)
 *   --slot   時間帯 (0=午前, 1=午後, 2=夜間)
 *
 * 環境変数:
 *   KADERU_USER_ID  - 利用者ID
 *   KADERU_PASSWORD - パスワード
 */

const { chromium } = require("playwright");

const SITE_URL = "https://k2.p-kashikan.jp/kaderu27/index.php";

const ROOM_CODES = {
  すずらん: "001|018|01|2|2|0",
  はまなす: "001|018|02|3|2|0",
  あかなら: "001|017|02|3|2|0",
  えぞまつ: "001|017|01|2|2|0",
};

const TIME_SLOTS = {
  0: { range: "09001200", label: "午前 (9:00-12:00)" },
  1: { range: "13001600", label: "午後 (13:00-16:00)" },
  2: { range: "17002100", label: "夜間 (17:00-21:00)" },
};

function parseArgs() {
  const args = process.argv.slice(2);
  const params = {};
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i].replace(/^--/, "");
    params[key] = args[i + 1];
  }

  const room = params.room;
  const date = params.date;
  const slot = parseInt(params.slot);

  if (!room || !ROOM_CODES[room]) {
    console.error(`部屋名が不正です: ${room}`);
    console.error(`有効な部屋名: ${Object.keys(ROOM_CODES).join(", ")}`);
    process.exit(1);
  }
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    console.error(`日付形式が不正です: ${date} (YYYY-MM-DD)`);
    process.exit(1);
  }
  if (isNaN(slot) || !TIME_SLOTS[slot]) {
    console.error(`時間帯が不正です: ${params.slot} (0=午前, 1=午後, 2=夜間)`);
    process.exit(1);
  }

  const userId = process.env.KADERU_USER_ID;
  const password = process.env.KADERU_PASSWORD;
  if (!userId || !password) {
    console.error("環境変数 KADERU_USER_ID, KADERU_PASSWORD を設定してください");
    process.exit(1);
  }

  return { room, date, slot, userId, password };
}

async function nav(page, action) {
  await Promise.all([
    page.waitForNavigation({ timeout: 15000 }).catch(() => {}),
    action(),
  ]);
  await page.waitForTimeout(1500);
}

async function openReserve({ room, date, slot, userId, password }) {
  const facilityCode = ROOM_CODES[room];
  const timeSlot = TIME_SLOTS[slot];
  const [yearStr, monthStr, dayStr] = date.split("-");
  const year = parseInt(yearStr);
  const month = parseInt(monthStr);
  const day = parseInt(dayStr);
  const dateFormatted = `${year}/${monthStr}/${dayStr}`;

  console.log(`予約対象: ${room} ${date} ${timeSlot.label}`);
  console.log(`施設コード: ${facilityCode}`);

  const browser = await chromium.launch({ headless: false });
  const page = await browser.newPage();

  try {
    // 1. ログイン
    console.log("ログイン中...");
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
      // ブラウザは閉じない（エラー確認用）
      console.log(JSON.stringify({ success: false, error: "LOGIN_FAILED" }));
      return { browser, success: false };
    }
    console.log("ログイン成功");

    // 2. 空き状況ページへ遷移
    console.log("空き状況ページへ遷移中...");
    await nav(page, () => page.evaluate(() => gotoPage("srch_sst")));

    // 3. 月を合わせる
    const currentYM = await page.evaluate(() => {
      const input = document.querySelector('input[name="UseYM"]');
      return input ? input.value : null;
    });
    const targetYM = `${year}${monthStr}`;
    if (currentYM !== targetYM) {
      console.log(`月を ${targetYM} に切り替え...`);
      await nav(page, () =>
        page.evaluate(({ y, m }) => showCalendar(y, m), { y: year, m: month })
      );
    }

    // 4. 日付をクリック
    console.log(`${month}/${day} を選択...`);
    await nav(page, () =>
      page.evaluate(({ d }) => clickDay(String(d)), { d: day })
    );

    // 5. 対象部屋の空きスロットを確認
    const slotStatus = await page.evaluate(
      ({ roomName, slotIdx }) => {
        const rows = document.querySelectorAll("tr");
        for (const row of rows) {
          const nameCell = row.querySelector("td.name");
          if (!nameCell || !nameCell.textContent.includes(roomName)) continue;

          const cells = Array.from(row.querySelectorAll("td"));
          const dataSlots = cells.slice(1).filter((c) => {
            const text = c.textContent.trim();
            return text !== "" && text !== "\u00a0";
          });

          if (dataSlots[slotIdx]) {
            return {
              text: dataSlots[slotIdx].textContent.trim(),
              onmousedown: dataSlots[slotIdx].getAttribute("onmousedown"),
            };
          }
        }
        return null;
      },
      { roomName: room, slotIdx: slot }
    );

    if (!slotStatus) {
      console.error(`${room} が見つかりませんでした`);
      console.log(JSON.stringify({ success: false, error: "ROOM_NOT_FOUND" }));
      return { browser, success: false };
    }

    if (slotStatus.text !== "○") {
      console.error(`${room} の ${timeSlot.label} は空きではありません (${slotStatus.text})`);
      console.log(
        JSON.stringify({
          success: false,
          error: "NOT_AVAILABLE",
          status: slotStatus.text,
        })
      );
      return { browser, success: false };
    }

    // 6. スロット選択
    console.log(`スロット選択: ${room} ${timeSlot.label}...`);
    await page.evaluate(
      ({ code, dateStr, slotIdx, timeRange }) => {
        setAppStatus(code, dateStr, slotIdx, timeRange, "#d1fafa", "#ff2c2c");
      },
      { code: facilityCode, dateStr: dateFormatted, slotIdx: slot, timeRange: timeSlot.range }
    );
    await page.waitForTimeout(500);

    // 7. 「申込トレイに入れる」をクリック
    console.log("申込トレイに追加中...");
    await nav(page, () => page.click('button[name="requestBtn"]'));
    await page.waitForTimeout(1000);

    // 8. 申込トレイ画面に到達したか確認
    const trayPage = await page.evaluate(() => {
      const bodyText = document.body.textContent;
      return {
        isTray: bodyText.includes("申込トレイ") && bodyText.includes("申込内容"),
        hasRoom: bodyText.includes("利用施設"),
        hasApplyBtn:
          !!document.querySelector('input[name="applyBtn"]'),
      };
    });

    if (trayPage.isTray && trayPage.hasApplyBtn) {
      console.log("申込トレイ画面に到達しました");
      console.log(
        JSON.stringify({
          success: true,
          room,
          date,
          timeSlot: timeSlot.label,
          message: "申込トレイ画面で待機中。利用目的を入力後「申込み」を押してください。",
        })
      );
      return { browser, success: true };
    } else {
      console.error("申込トレイ画面への遷移に失敗しました");
      console.log(JSON.stringify({ success: false, error: "TRAY_NAVIGATION_FAILED" }));
      return { browser, success: false };
    }
  } catch (err) {
    console.error(`エラー: ${err.message}`);
    console.log(JSON.stringify({ success: false, error: err.message }));
    return { browser, success: false };
  }
}

// --- メイン ---
async function main() {
  const params = parseArgs();
  const { browser, success } = await openReserve(params);

  if (!success) {
    console.log("openReserve failed. Closing browser and exiting.");
    await browser.close();
    process.exit(1);
  }

  // ブラウザを開いたまま待機（ユーザーが手動で操作）
  // Ctrl+C で終了
  console.log("\nブラウザを開いたまま待機中... Ctrl+C で終了");

  await new Promise((resolve) => {
    process.on("SIGINT", resolve);
    process.on("SIGTERM", resolve);
    // 30分でタイムアウト
    setTimeout(resolve, 30 * 60 * 1000);
  });

  await browser.close();
  console.log("ブラウザを閉じました");
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  process.exit(1);
});
