/**
 * 札幌市東区民センター 予約フロー探索スクリプト
 *
 * 目的: higashi-reservation-proxy 機能の要件定義に必要な技術情報
 * （URL / DOM構造 / ViewState挙動 / 申込トレイ画面の有無 等）を
 * 実機探索で取得する。
 *
 * 仕様:
 *   docs/features/higashi-reservation-flow-exploration/requirements.md
 *   docs/features/higashi-reservation-flow-exploration/implementation-plan.md
 *
 * 【重要】申込確定ボタンのクリック処理はコード上に一切書かない。
 *         最終申込ボタンが視認できたらセレクタをログに記録して即終了する。
 *         絶対に追加しないこと。
 *
 * Usage:
 *   node explore-higashi-reservation.js --confirm-no-submit \
 *        [--room さくら|かっこう] [--date YYYY-MM-DD] \
 *        [--slot morning|afternoon|night] [--output-dir <path>]
 *
 * 環境変数:
 *   SAPPORO_COMMUNITY_USER_ID  - 利用者ID
 *   SAPPORO_COMMUNITY_PASSWORD - パスワード
 */

const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
// playwright は preflightCheck 通過後に require する（preflight を依存無しで実行可能にするため）

const LOGIN_URL = "https://sapporo-community.jp/UserWebApp/Form/UserLogin.aspx";

const FACILITY_CODE = "103"; // 札幌市東区民センター
const ROOM_CODES = {
  "さくら": "042",
  "かっこう": "041",
};

// 月表示テーブルの列インデックス: 0=日付,1=曜日,2=午前,3=昼食,4=午後,5=夕食,6=夜間,7=延長
const SLOT_COL_INDEX = {
  morning: 2,
  afternoon: 4,
  night: 6,
};

// 「申込確定」系ボタンのキーワード。検出したら絶対クリックせず記録だけ行う。
// 東区民センター (sapporo-community.jp) の確認画面は「申込内容でよろしいですか？」
// ダイアログ形式で、確定ボタンの value="はい" となっている。
const FINAL_SUBMIT_KEYWORDS = [
  "申込確定",
  "申込を確定",
  "予約確定",
  "申込する",
  "予約する",
  "申込完了",
  "送信",
  "確定する",
  "はい",
];

// 前進系ボタンのキーワード。これに該当するボタンはクリック対象。
const FORWARD_KEYWORDS = [
  "次へ",
  "次に進む",
  "確認画面",
  "進む",
  "選択",
];

const MAX_EXPLORATION_STEPS = 15;

function parseArgs(argv) {
  const args = {
    confirmNoSubmit: false,
    room: "さくら",
    date: null, // デフォルトは実行日+3日
    slot: "night",
    outputDir: null,
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--confirm-no-submit") {
      args.confirmNoSubmit = true;
    } else if (a === "--room" && argv[i + 1]) {
      args.room = argv[++i];
    } else if (a === "--date" && argv[i + 1]) {
      args.date = argv[++i];
    } else if (a === "--slot" && argv[i + 1]) {
      args.slot = argv[++i];
    } else if (a === "--output-dir" && argv[i + 1]) {
      args.outputDir = argv[++i];
    }
  }
  return args;
}

function jstNow() {
  return new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Tokyo" }));
}

function toJstDateOnly(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split("-").map((v) => parseInt(v, 10));
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + days);
  const ny = date.getUTCFullYear();
  const nm = String(date.getUTCMonth() + 1).padStart(2, "0");
  const nd = String(date.getUTCDate()).padStart(2, "0");
  return `${ny}-${nm}-${nd}`;
}

function formatTimestampDir() {
  const now = jstNow();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  const hh = String(now.getHours()).padStart(2, "0");
  const mm = String(now.getMinutes()).padStart(2, "0");
  const ss = String(now.getSeconds()).padStart(2, "0");
  return `higashi-reservation-${y}${m}${d}-${hh}${mm}${ss}`;
}

function sha256(s) {
  return crypto.createHash("sha256").update(s, "utf8").digest("hex");
}

function fileSafeStepName(name) {
  return name.replace(/[^\w.-]+/g, "-").replace(/^-+|-+$/g, "");
}

async function preflightCheck(args) {
  const errors = [];

  if (!args.confirmNoSubmit) {
    errors.push(
      "--confirm-no-submit フラグが必要です。実申込防止のためのガードです。"
    );
  }

  if (!process.env.SAPPORO_COMMUNITY_USER_ID || !process.env.SAPPORO_COMMUNITY_PASSWORD) {
    errors.push(
      "環境変数 SAPPORO_COMMUNITY_USER_ID / SAPPORO_COMMUNITY_PASSWORD が未設定です。"
    );
  }

  if (!Object.prototype.hasOwnProperty.call(ROOM_CODES, args.room)) {
    errors.push(
      `--room の値が不正です: '${args.room}' (有効値: ${Object.keys(ROOM_CODES).join(" / ")})`
    );
  }

  if (!Object.prototype.hasOwnProperty.call(SLOT_COL_INDEX, args.slot)) {
    errors.push(
      `--slot の値が不正です: '${args.slot}' (有効値: ${Object.keys(SLOT_COL_INDEX).join(" / ")})`
    );
  }

  if (args.date != null && !/^\d{4}-\d{2}-\d{2}$/.test(args.date)) {
    errors.push(`--date の形式が不正です: '${args.date}' (YYYY-MM-DD)`);
  }

  if (errors.length > 0) {
    for (const e of errors) console.error(`[エラー] ${e}`);
    process.exit(1);
  }

  // 日付のデフォルト値（実行日+3日）と当日警告
  const today = toJstDateOnly(jstNow());
  if (!args.date) {
    args.date = addDays(today, 3);
    console.log(`[情報] --date 未指定のため ${args.date} を使用します（実行日+3日）`);
  }
  if (args.date === today) {
    console.warn(
      "[警告] 探索日が実行日と同日です。実申込発生リスクを避けるため数日後を推奨します。"
    );
  }

  // 出力ディレクトリ
  const outputDir =
    args.outputDir ||
    path.join(__dirname, "exploration-output", formatTimestampDir());
  try {
    await fs.mkdir(outputDir, { recursive: true });
  } catch (e) {
    console.error(`[エラー] 出力ディレクトリを作成できません: ${outputDir}: ${e.message}`);
    process.exit(1);
  }
  args.outputDir = outputDir;

  return args;
}

/**
 * 現ページのフォーム構造と ViewState 系 hidden の情報を取得する。
 * ViewState 本体は保存せず、長さ・先頭末尾20文字・SHA256 のみ。
 */
async function capturePageMeta(page) {
  return await page.evaluate(() => {
    const forms = Array.from(document.querySelectorAll("form")).map((f) => ({
      id: f.id || null,
      name: f.name || null,
      action: f.action || null,
      method: f.method || null,
      inputs: Array.from(f.querySelectorAll("input")).map((i) => ({
        name: i.name || null,
        id: i.id || null,
        type: i.type || null,
        value: i.type === "hidden" ? `[hidden:len=${(i.value || "").length}]` : (i.value || ""),
      })),
      selects: Array.from(f.querySelectorAll("select")).map((s) => ({
        name: s.name || null,
        id: s.id || null,
        selected: s.value || null,
        optionCount: s.options.length,
      })),
      buttons: Array.from(f.querySelectorAll("button, input[type=submit], input[type=image], input[type=button]")).map((b) => ({
        tag: b.tagName,
        id: b.id || null,
        name: b.name || null,
        type: b.type || null,
        value: b.value || null,
        alt: b.getAttribute("alt") || null,
        text: (b.textContent || "").trim().substring(0, 80),
      })),
    }));

    const hiddenOf = (name) => {
      const el = document.querySelector(`input[name="${name}"]`);
      return el ? el.value || "" : null;
    };

    const viewStates = {
      viewState: hiddenOf("__VIEWSTATE"),
      eventValidation: hiddenOf("__EVENTVALIDATION"),
      viewStateGenerator: hiddenOf("__VIEWSTATEGENERATOR"),
      eventTarget: hiddenOf("__EVENTTARGET"),
      eventArgument: hiddenOf("__EVENTARGUMENT"),
    };

    const cookieKeys = document.cookie
      .split(";")
      .map((c) => c.trim().split("=")[0])
      .filter(Boolean);

    const actionButtonCandidates = Array.from(
      document.querySelectorAll("a, button, input[type=submit], input[type=image], input[type=button]")
    ).map((el) => ({
      tag: el.tagName,
      id: el.id || null,
      name: el.name || null,
      alt: el.getAttribute("alt") || null,
      value: el.value || null,
      text: (el.textContent || "").trim().substring(0, 80),
      href: el.getAttribute ? (el.getAttribute("href") || null) : null,
      onclick: el.getAttribute ? (el.getAttribute("onclick") || null) : null,
    })).filter((b) => b.id || b.alt || b.value || (b.text && b.text.length > 0));

    return { forms, viewStates, cookieKeys, actionButtonCandidates };
  });
}

function summarizeViewStateField(value, previousHash) {
  if (value == null) {
    return { present: false };
  }
  const hash = sha256(value);
  return {
    present: true,
    length: value.length,
    head20: value.substring(0, 20),
    tail20: value.slice(-20),
    sha256: hash,
    changedFromPrevious: previousHash != null && previousHash !== hash,
  };
}

/**
 * ステップを記録する。HTML / PNG / JSON の3点セットを保存し summary に追加する。
 */
async function recordStep(ctx, stepNumber, stepName, nextActionHint) {
  const { page, outputDir, summary, previousViewStateHashes } = ctx;

  const url = page.url();
  const title = await page.title();
  const html = await page.content();
  const screenshot = await page.screenshot({ fullPage: true });
  const meta = await capturePageMeta(page);

  const stepPad = String(stepNumber).padStart(2, "0");
  const safeName = fileSafeStepName(stepName);
  const basename = `step-${stepPad}-${safeName}`;

  await fs.writeFile(path.join(outputDir, `${basename}.html`), html, "utf8");
  await fs.writeFile(path.join(outputDir, `${basename}.png`), screenshot);

  const vsSummary = {
    viewState: summarizeViewStateField(meta.viewStates.viewState, previousViewStateHashes.viewState),
    eventValidation: summarizeViewStateField(meta.viewStates.eventValidation, previousViewStateHashes.eventValidation),
    viewStateGenerator: summarizeViewStateField(meta.viewStates.viewStateGenerator, previousViewStateHashes.viewStateGenerator),
    eventTarget: meta.viewStates.eventTarget,
    eventArgument: meta.viewStates.eventArgument,
  };

  // 次回比較用にハッシュを保存
  previousViewStateHashes.viewState = vsSummary.viewState.sha256 || previousViewStateHashes.viewState;
  previousViewStateHashes.eventValidation = vsSummary.eventValidation.sha256 || previousViewStateHashes.eventValidation;
  previousViewStateHashes.viewStateGenerator = vsSummary.viewStateGenerator.sha256 || previousViewStateHashes.viewStateGenerator;

  const record = {
    step: stepNumber,
    name: stepName,
    url,
    title,
    timestamp: new Date().toISOString(),
    nextActionHint,
    forms: meta.forms,
    viewStates: vsSummary,
    cookieKeys: meta.cookieKeys,
    actionButtonCandidates: meta.actionButtonCandidates,
    files: {
      html: `${basename}.html`,
      png: `${basename}.png`,
    },
  };

  await fs.writeFile(
    path.join(outputDir, `${basename}.json`),
    JSON.stringify(record, null, 2),
    "utf8"
  );

  summary.push(record);

  console.log(`\n[step ${stepPad}] ${stepName}`);
  console.log(`  title: ${title}`);
  console.log(`  url  : ${url}`);
  if (vsSummary.viewState.present) {
    const changed = vsSummary.viewState.changedFromPrevious ? " (変化あり)" : " (変化なし)";
    console.log(`  __VIEWSTATE: len=${vsSummary.viewState.length} sha256=${vsSummary.viewState.sha256.substring(0, 12)}...${changed}`);
  } else {
    console.log(`  __VIEWSTATE: なし`);
  }
  if (nextActionHint) {
    console.log(`  次の操作: ${nextActionHint}`);
  }
}

/**
 * エラーページ（サービス時間外・HTTPエラー）を検出したら即時終了する。
 */
function assertNotErrorPage(page) {
  const url = page.url();
  if (url.includes("OutsideServiceTime.html")) {
    console.error(`[エラー] サービス時間外: ${url}`);
    process.exit(1);
  }
  if (url.includes("HttpClientError.html")) {
    console.error(`[エラー] HTTPエラーページ: ${url}`);
    process.exit(1);
  }
}

/**
 * 指定日付が月表示テーブルに表示されるまで __doPostBack で翌月へ遷移する。
 */
async function navigateToTargetMonth(page, targetDate) {
  const [targetYear, targetMonth] = targetDate.split("-").map((v) => parseInt(v, 10));
  const today = jstNow();
  const currentYear = today.getFullYear();
  const currentMonth = today.getMonth() + 1;
  const diff =
    (targetYear - currentYear) * 12 + (targetMonth - currentMonth);

  if (diff < 0) {
    throw new Error(`対象日付 ${targetDate} は過去の月です`);
  }
  if (diff === 0) return;

  for (let i = 0; i < diff; i++) {
    const prevHeader = await page.evaluate(() => {
      const cells = document.querySelectorAll("td");
      for (const c of cells) {
        const text = (c.textContent || "").trim();
        if (/前月\s*令和\d+年\d+月\s*翌月/.test(text)) return text;
      }
      return null;
    });
    await page.click("#ctl00_cphMain_lbtNextMonth");
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
    await page.waitForLoadState("networkidle").catch(() => {});
  }
}

/**
 * 月表示テーブル上で、指定日のスロットセルの class / text / clickable-a を確認する。
 */
async function inspectTargetCell(page, day, colIdx) {
  return await page.evaluate(
    ({ day, colIdx }) => {
      const table = document.querySelector("#ctl00_cphMain_tblMain");
      if (!table) return { found: false, reason: "#ctl00_cphMain_tblMain not found" };
      for (const row of Array.from(table.rows)) {
        const cells = Array.from(row.cells);
        if (cells.length === 0) continue;
        const dayText = (cells[0].textContent || "").trim();
        if (!/^\d{1,2}$/.test(dayText)) continue;
        if (parseInt(dayText, 10) !== day) continue;
        if (cells.length < colIdx + 1) {
          return { found: true, available: false, reason: "列数が足りない（休館日の可能性）" };
        }
        const cell = cells[colIdx];
        const cls = cell.className || "";
        const text = (cell.textContent || "").trim();
        const anchor = cell.querySelector("a");
        return {
          found: true,
          available: cls.includes("Available") && text === "○",
          className: cls,
          text,
          onclick: anchor ? anchor.getAttribute("onclick") : null,
          href: anchor ? anchor.getAttribute("href") : null,
        };
      }
      return { found: false, reason: `day=${day} の行が月表示に見つからない` };
    },
    { day, colIdx }
  );
}

async function clickTargetCell(page, day, colIdx) {
  const result = await page.evaluate(
    ({ day, colIdx }) => {
      const table = document.querySelector("#ctl00_cphMain_tblMain");
      if (!table) return { clicked: false, reason: "table not found" };
      for (const row of Array.from(table.rows)) {
        const cells = Array.from(row.cells);
        if (cells.length === 0) continue;
        const dayText = (cells[0].textContent || "").trim();
        if (!/^\d{1,2}$/.test(dayText)) continue;
        if (parseInt(dayText, 10) !== day) continue;
        if (cells.length < colIdx + 1) {
          return { clicked: false, reason: "no such column (休館日か)" };
        }
        const cell = cells[colIdx];
        const anchor = cell.querySelector("a");
        if (!anchor) return { clicked: false, reason: "anchor not found in cell" };
        anchor.click();
        return { clicked: true };
      }
      return { clicked: false, reason: "row not found" };
    },
    { day, colIdx }
  );
  if (!result.clicked) {
    throw new Error(`○セルのクリックに失敗: ${result.reason}`);
  }
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(1500);
}

/**
 * ページ上の「申込確定系」ボタンを検出する。
 * 該当したら絶対クリックしない — selector を記録して終了するのみ。
 */
async function findFinalSubmitButton(page) {
  return await page.evaluate((keywords) => {
    const candidates = Array.from(
      document.querySelectorAll("a, button, input[type=submit], input[type=image], input[type=button]")
    );
    for (const el of candidates) {
      const text = ((el.textContent || "") + " " + (el.value || "") + " " + (el.getAttribute("alt") || "")).trim();
      for (const kw of keywords) {
        if (text.includes(kw)) {
          return {
            matched: true,
            keyword: kw,
            tag: el.tagName,
            id: el.id || null,
            name: el.name || null,
            value: el.value || null,
            alt: el.getAttribute("alt") || null,
            text: text.substring(0, 120),
            selector: el.id
              ? `#${el.id}`
              : el.name
              ? `[name="${el.name}"]`
              : null,
          };
        }
      }
    }
    return { matched: false };
  }, FINAL_SUBMIT_KEYWORDS);
}

/**
 * 「前進系」ボタンを1つ検出する。
 * 申込確定キーワードに1つでも引っかかるものは返さない（安全側）。
 */
async function findForwardButton(page) {
  return await page.evaluate(
    ({ forwardKeywords, dangerKeywords }) => {
      const candidates = Array.from(
        document.querySelectorAll("a, button, input[type=submit], input[type=image], input[type=button]")
      );
      for (const el of candidates) {
        const text = ((el.textContent || "") + " " + (el.value || "") + " " + (el.getAttribute("alt") || "")).trim();
        if (!text) continue;
        // 危険ワードに1つでも引っかかるものはスキップ（二重安全装置）
        if (dangerKeywords.some((kw) => text.includes(kw))) continue;
        for (const kw of forwardKeywords) {
          if (text.includes(kw)) {
            return {
              matched: true,
              keyword: kw,
              tag: el.tagName,
              id: el.id || null,
              name: el.name || null,
              value: el.value || null,
              alt: el.getAttribute("alt") || null,
              text: text.substring(0, 120),
              selector: el.id
                ? `#${el.id}`
                : el.name
                ? `[name="${el.name}"]`
                : null,
            };
          }
        }
      }
      return { matched: false };
    },
    { forwardKeywords: FORWARD_KEYWORDS, dangerKeywords: FINAL_SUBMIT_KEYWORDS }
  );
}

async function clickBySelector(page, selector) {
  await Promise.all([
    page.waitForLoadState("networkidle").catch(() => {}),
    page.click(selector, { timeout: 15000 }),
  ]);
  await page.waitForTimeout(1500);
}

/**
 * 利用申込画面 (SsfrApplyForUseEntry.aspx) のフォームをダミー値で入力する。
 * ここで入力する値は「確認画面 (=申込確定ボタンの1画面前) に到達させる」ためだけの値。
 * 確認画面に到達したらスクリプトの FINAL_SUBMIT_KEYWORDS 検出機構が働いて申込確定前で停止する。
 * 実際の申込は絶対に発生させない設計。
 */
async function fillApplicationForm(page, slot) {
  console.log(`\n  [フォーム入力] ダミー値で入力中...`);

  const slotCheckboxId = {
    morning: "#ctl00_cphMain_wucTimeKbn_cbTimeAm",
    afternoon: "#ctl00_cphMain_wucTimeKbn_cbTimePm",
    night: "#ctl00_cphMain_wucTimeKbn_cbTimeNt",
  }[slot];
  if (!slotCheckboxId) throw new Error(`未対応のスロット: ${slot}`);

  // 時間区分チェックボックスは AutoPostBack を発火するため、クリック後に
  // networkidle まで待ってから次のフィールドへ進む。
  await Promise.all([
    page.waitForLoadState("networkidle").catch(() => {}),
    page.check(slotCheckboxId),
  ]);
  await page.waitForTimeout(1000);

  // 利用人数（半角数字、maxlength=3）
  await page.fill("#ctl00_cphMain_tbUserNumber", "2");

  // 担当者氏名（全角、苗字と名の間に全角スペース）
  await page.fill("#ctl00_cphMain_tbContactName", "テスト　太郎");

  // 電話番号（3分割）
  await page.fill("#ctl00_cphMain_tbTelno1", "090");
  await page.fill("#ctl00_cphMain_tbTelno2", "0000");
  await page.fill("#ctl00_cphMain_tbTelno3", "0000");

  // メールアドレス
  await page.fill("#ctl00_cphMain_tbMailAddress", "test@example.com");

  // 利用目的（value="16" = 会議・会合）
  // ddlIntendedUse も AutoPostBack の可能性があるため安全側で待機
  await Promise.all([
    page.waitForLoadState("networkidle").catch(() => {}),
    page.selectOption("#ctl00_cphMain_ddlIntendedUse", "16"),
  ]);
  await page.waitForTimeout(1000);

  console.log(`  [フォーム入力] 完了`);
}

async function saveFailureSnapshot(ctx, stepNumber, label, error) {
  try {
    await recordStep(ctx, stepNumber, `error-${label}`, `エラー発生: ${error?.message || error}`);
  } catch (e) {
    console.error(`[警告] エラースナップショット保存にも失敗: ${e.message}`);
  }
}

async function main() {
  const rawArgs = parseArgs(process.argv);
  const args = await preflightCheck(rawArgs);

  console.log("===== 東区民センター 予約フロー探索 =====");
  console.log(`部屋     : ${args.room} (${ROOM_CODES[args.room]})`);
  console.log(`日付     : ${args.date}`);
  console.log(`スロット : ${args.slot} (col=${SLOT_COL_INDEX[args.slot]})`);
  console.log(`出力先   : ${args.outputDir}`);
  console.log("========================================");

  let chromium;
  try {
    ({ chromium } = require("playwright"));
  } catch (e) {
    console.error(`[エラー] playwright モジュールが見つかりません: ${e.message}`);
    console.error(`        scripts/room-checker ディレクトリで 'npm install' を実行してください。`);
    process.exit(1);
  }

  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    userAgent:
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  });
  const page = await context.newPage();

  const summary = [];
  const previousViewStateHashes = {
    viewState: null,
    eventValidation: null,
    viewStateGenerator: null,
  };
  const ctx = { page, outputDir: args.outputDir, summary, previousViewStateHashes };

  let exitCode = 0;
  let finalSubmitInfo = null;
  let terminationReason = "unknown";

  try {
    // ---- step01: ログイン画面表示 ----
    await page.goto(LOGIN_URL, { waitUntil: "networkidle" });
    assertNotErrorPage(page);
    await recordStep(ctx, 1, "login-page", "ID/PW 入力して btnReg をクリック");

    // ---- step02: ログイン送信 ----
    await page.fill("#ctl00_cphMain_tbUserno", process.env.SAPPORO_COMMUNITY_USER_ID);
    await page.fill("#ctl00_cphMain_tbPassword", process.env.SAPPORO_COMMUNITY_PASSWORD);
    await Promise.all([
      page.waitForLoadState("networkidle").catch(() => {}),
      page.click("#ctl00_cphMain_btnReg"),
    ]);
    await page.waitForTimeout(1500);
    assertNotErrorPage(page);
    await recordStep(ctx, 2, "login-submitted", "ログイン成功判定 → 空室検索メニューへ");

    // ---- step03: ログイン成功確認 ----
    const historyBtn = await page.$("#ctl00_cphMain_WucImgBtnHistory_imgbtnMain");
    if (!historyBtn) {
      throw new Error("ログインに失敗しました（メニュー画面に到達できません）");
    }
    await recordStep(ctx, 3, "menu-after-login", "空室検索 (VacantRoomsSearchLogout) をクリック");

    // ---- step04: 空室検索 → 施設選択 → 部屋選択 → 月表示 ----
    // 4a: 空室検索ボタン（ログイン済メニューは ...Login 側 ID。Logout 側は未ログイン用）
    await Promise.all([
      page.waitForLoadState("networkidle").catch(() => {}),
      page.click("#ctl00_cphMain_wucImgBtnVacantRoomsSearchLogin_imgbtnMain"),
    ]);
    await page.waitForTimeout(1000);
    assertNotErrorPage(page);
    await recordStep(ctx, 4, "vacant-rooms-search-top", "「施設から検索」をクリック");

    // 4b: 施設から検索
    await Promise.all([
      page.waitForLoadState("networkidle").catch(() => {}),
      page.click("#ctl00_cphMain_WucImageButton1_imgbtnMain"),
    ]);
    await page.waitForTimeout(1000);
    assertNotErrorPage(page);
    await recordStep(ctx, 5, "facility-search-page", `施設 ${FACILITY_CODE} (東区民センター) を選択`);

    // 4c: 施設選択 (AutoPostBack)
    await Promise.all([
      page.waitForLoadState("networkidle").catch(() => {}),
      page.selectOption("#ctl00_cphMain_WucFacilitySelect_ddlFacilities", FACILITY_CODE),
    ]);
    await page.waitForTimeout(1000);
    assertNotErrorPage(page);
    await recordStep(ctx, 6, "facility-selected", `部屋 ${args.room} (${ROOM_CODES[args.room]}) を選択`);

    // 4d: 部屋選択 (AutoPostBack)
    await Promise.all([
      page.waitForLoadState("networkidle").catch(() => {}),
      page.selectOption("#ctl00_cphMain_wucRoomSelect_ddlRooms", ROOM_CODES[args.room]),
    ]);
    await page.waitForTimeout(1000);
    assertNotErrorPage(page);
    await recordStep(ctx, 7, "room-selected", "部屋単位の月表示ページへ遷移 (btFwdRoomSelect)");

    // 4e: 月表示ページへ
    await Promise.all([
      page.waitForLoadState("networkidle").catch(() => {}),
      page.click("#ctl00_cphMain_btFwdRoomSelect"),
    ]);
    await page.waitForTimeout(1500);
    assertNotErrorPage(page);
    if (!page.url().includes("SsfSvrRoomAvailabilityMonth")) {
      throw new Error(`月表示ページへの遷移に失敗: ${page.url()}`);
    }
    await recordStep(ctx, 8, "month-view", `対象日 ${args.date} が表示される月へ遷移`);

    // ---- step05: 指定日×スロット のセルをクリック ----
    await navigateToTargetMonth(page, args.date);
    await recordStep(ctx, 9, "target-month-view", "対象日の ○ セルを検証");

    const day = parseInt(args.date.slice(-2), 10);
    const colIdx = SLOT_COL_INDEX[args.slot];
    const cellInfo = await inspectTargetCell(page, day, colIdx);
    if (!cellInfo.found) {
      throw new Error(`対象日付セルが見つかりません: ${cellInfo.reason}`);
    }
    if (!cellInfo.available) {
      console.error(`[エラー] 対象日付のスロットが空きではありません: class="${cellInfo.className}" text="${cellInfo.text}"`);
      console.error(`  別の日付・スロットで再試行してください。`);
      throw new Error("対象スロットが空きではない");
    }
    console.log(`  対象セル: class="${cellInfo.className}" text="${cellInfo.text}" onclick=${cellInfo.onclick}`);

    await clickTargetCell(page, day, colIdx);
    assertNotErrorPage(page);
    await recordStep(ctx, 10, "after-cell-click", "○セルクリック後の画面 — 利用申込フォームが出ていればダミー値で入力");

    // ---- step11-12: 利用申込フォームが出ていたらダミー値で入力 → 確認画面へ ----
    let stepCounter = 11;
    if (page.url().includes("SsfrApplyForUseEntry")) {
      await fillApplicationForm(page, args.slot);
      await recordStep(
        ctx,
        stepCounter++,
        "form-filled",
        "ダミー値で入力完了 — 「利用申込確認」ボタンをクリックして確認画面へ"
      );

      await Promise.all([
        page.waitForLoadState("networkidle").catch(() => {}),
        page.click("#ctl00_cphMain_btnReg"),
      ]);
      await page.waitForTimeout(2000);
      assertNotErrorPage(page);
      await recordStep(
        ctx,
        stepCounter++,
        "after-apply-confirm-click",
        "利用申込確認ボタン押下後の画面 — 申込確定ボタンを検出して停止することを期待"
      );
    }

    // ---- 探索ループ（申込確定系ボタン検出で停止） ----
    for (let i = 0; i < MAX_EXPLORATION_STEPS; i++) {
      // 申込確定系ボタンの検出 — 見つけたら絶対クリックしない
      const finalBtn = await findFinalSubmitButton(page);
      if (finalBtn.matched) {
        console.log(`\n[検出] 申込確定系ボタンを発見しました: "${finalBtn.text}" (keyword="${finalBtn.keyword}")`);
        console.log(`  selector: ${finalBtn.selector || "(id/name なし)"}`);
        console.log(`  【重要】クリックせずに終了します。`);
        finalSubmitInfo = finalBtn;
        await recordStep(
          ctx,
          stepCounter++,
          "final-submit-visible",
          `申込確定ボタン検出: selector=${finalBtn.selector} keyword=${finalBtn.keyword} — クリックせず終了`
        );
        terminationReason = "final-submit-detected";
        break;
      }

      // 前進ボタン検索
      const fwd = await findForwardButton(page);
      if (!fwd.matched) {
        console.log(`\n[停止] 前進ボタンが見つかりません。dead-end として終了します。`);
        await recordStep(
          ctx,
          stepCounter++,
          "dead-end",
          "前進ボタンが見つからない。探索はここで終了。"
        );
        terminationReason = "dead-end-no-forward-button";
        break;
      }

      if (!fwd.selector) {
        console.log(`\n[停止] 前進ボタン候補 "${fwd.text}" は id/name がなくセレクタ決定不可。探索を終了します。`);
        await recordStep(
          ctx,
          stepCounter++,
          "forward-button-unselectable",
          `前進ボタン候補: "${fwd.text}" は selector を特定できない`
        );
        terminationReason = "forward-button-unselectable";
        break;
      }

      console.log(`\n  前進: "${fwd.text}" (keyword="${fwd.keyword}") → ${fwd.selector}`);
      try {
        await clickBySelector(page, fwd.selector);
      } catch (e) {
        console.error(`[エラー] 前進ボタンクリック失敗: ${e.message}`);
        await saveFailureSnapshot(ctx, stepCounter++, "forward-click-failed", e);
        terminationReason = "forward-click-failed";
        throw e;
      }
      assertNotErrorPage(page);
      await recordStep(
        ctx,
        stepCounter++,
        `forward-step-${i + 1}`,
        `前進ボタン "${fwd.text}" クリック後`
      );
    }

    if (!finalSubmitInfo && terminationReason === "unknown") {
      console.log(`\n[停止] 最大ステップ数 ${MAX_EXPLORATION_STEPS} に到達。ここで終了します。`);
      terminationReason = "max-steps-reached";
    }
  } catch (e) {
    exitCode = 1;
    terminationReason = terminationReason === "unknown" ? `exception: ${e.message}` : terminationReason;
    console.error(`\n[実行エラー] ${e.message}`);
    console.error(e.stack);
    await saveFailureSnapshot(ctx, summary.length + 1, "exception", e);
  } finally {
    // summary.json を出力
    try {
      const summaryJson = {
        executedAt: new Date().toISOString(),
        args: {
          room: args.room,
          date: args.date,
          slot: args.slot,
          outputDir: args.outputDir,
        },
        terminationReason,
        finalSubmitButton: finalSubmitInfo,
        stepCount: summary.length,
        steps: summary,
      };
      await fs.writeFile(
        path.join(args.outputDir, "summary.json"),
        JSON.stringify(summaryJson, null, 2),
        "utf8"
      );
      console.log(`\nsummary.json を書き出しました: ${path.join(args.outputDir, "summary.json")}`);
    } catch (e) {
      console.error(`[警告] summary.json 書き出しに失敗: ${e.message}`);
    }

    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }

  process.exit(exitCode);
}

main().catch((err) => {
  console.error("[予期せぬエラー]", err?.stack || err);
  process.exit(1);
});
