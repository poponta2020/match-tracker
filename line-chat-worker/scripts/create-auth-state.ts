/**
 * ローカルPCで headed ブラウザを起動し、手動でLINE Official Account Manager（チャット）へ
 * ログインして storageState（Cookie・セッション情報）を書き出すスクリプト。
 *
 * 使い方:
 *   cd line-chat-worker
 *   npx tsx scripts/create-auth-state.ts [出力先パス（省略時 ./storage-state.json）]
 *
 * 手順:
 *   1. 実行するとブラウザが chat.line.biz を開く（未ログインなら LINE Business ID ログインへ）
 *   2. 対象OA（例:「北大かるた会お知らせ」）でログインする（2FAがあれば通す）
 *   3. **チャット画面（chat.line.biz/U... ）に到達すると自動で storageState を保存**して終了する
 *      （最大10分待機。Enter 押下は不要）
 *
 * 【重要・タスク7で確定】ワーカーが操作するのは chat.line.biz。storageState はこのドメインの
 * セッションを含む必要があるため、必ず chat.line.biz へ到達した状態で保存する（manager.line.biz
 * だけでは不十分な場合がある）。
 *
 * 【厳守】出力される storageState にはログインセッション情報が含まれる。絶対にコミットしない
 * （.gitignore 済み）。VMへの配置は安全なチャネル（SCP等）で行うこと。
 */
import { chromium, type Page } from "playwright";
import path from "node:path";

// 環境変数で対象チャットURLを差し替え可能。既定はルート（ログイン後にOAチャットへリダイレクト）。
const CHAT_URL = process.env.LINE_OAM_CHAT_URL ?? "https://chat.line.biz/";
const LOGIN_TIMEOUT_MS = 600_000; // 10分

/** chat.line.biz（かつ /oauth2 コールバックでない）に居れば、ログイン済みとみなす。 */
function isLoggedIn(page: Page): boolean {
  try {
    const u = new URL(page.url());
    return u.host === "chat.line.biz" && !u.pathname.startsWith("/oauth2");
  } catch {
    return false;
  }
}

async function main(): Promise<void> {
  const outputPath = process.argv[2] ?? path.resolve(process.cwd(), "storage-state.json");

  const browser = await chromium.launch({ headless: false });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(CHAT_URL, { waitUntil: "commit", timeout: 60_000 }).catch(() => undefined);
    await page.waitForTimeout(5_000); // リダイレクトが落ち着くのを待つ

    if (!isLoggedIn(page)) {
      console.log("ブラウザでLINE Business IDにログインしてください（対象OAのチャット）。");
      console.log("チャット画面に到達すると自動で storageState を保存します（最大10分待機）。");
    }

    const deadline = Date.now() + LOGIN_TIMEOUT_MS;
    while (!isLoggedIn(page)) {
      if (Date.now() > deadline) {
        throw new Error("ログイン完了を検知できませんでした（タイムアウト）");
      }
      await page.waitForTimeout(2_000);
    }

    await page.waitForTimeout(5_000); // チャットのセッションCookie確立を待つ
    await context.storageState({ path: outputPath });
    console.log(`storageState を書き出しました: ${outputPath}`);
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error("create-auth-state failed:", err instanceof Error ? err.message : "unknown error");
  process.exitCode = 1;
});
