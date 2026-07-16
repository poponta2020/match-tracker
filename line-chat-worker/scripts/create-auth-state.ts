/**
 * ローカルPCで headed ブラウザを起動し、手動でLINE Official Account Managerへログインして
 * storageState（Cookie・セッション情報）を書き出すスクリプト。
 *
 * 使い方:
 *   cd line-chat-worker
 *   npx tsx scripts/create-auth-state.ts [出力先パス（省略時 ./storage-state.json）]
 *
 * 手順:
 *   1. このスクリプトを実行するとブラウザが開く
 *   2. 手動でLINE Official Account Managerにログインする（対象OAのチャットが見える状態まで）
 *   3. ログイン完了後、ターミナルで Enter を押す
 *   4. storageState ファイルが書き出される → RUNBOOK.md の手順でVMへ配置する
 *
 * 【厳守】出力される storageState ファイルにはログインセッション情報が含まれる。
 * 絶対にコミットしない（.gitignore 済み）。VMへの配置は安全なチャネル（SCP等）で行うこと。
 */
import { chromium } from "playwright";
import path from "node:path";

const LINE_OAM_URL = "https://manager.line.biz/";

async function main(): Promise<void> {
  const outputPath = process.argv[2] ?? path.resolve(process.cwd(), "storage-state.json");

  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(LINE_OAM_URL);

  console.log("ブラウザでログインを完了してください。");
  console.log("完了したらこのターミナルで Enter を押してください。");
  await waitForEnter();

  await context.storageState({ path: outputPath });
  console.log(`storageState を書き出しました: ${outputPath}`);

  await browser.close();
}

function waitForEnter(): Promise<void> {
  return new Promise((resolve) => {
    process.stdin.resume();
    process.stdin.once("data", () => {
      process.stdin.pause();
      resolve();
    });
  });
}

main().catch((err) => {
  console.error("create-auth-state failed:", err instanceof Error ? err.message : "unknown error");
  process.exitCode = 1;
});
