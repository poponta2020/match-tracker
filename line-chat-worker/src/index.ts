import { existsSync } from "node:fs";
import { chromium } from "playwright";
import { loadConfig } from "./config/index.js";
import { createAppApiClient } from "./appApi/client.js";
import { OamChatPage } from "./line/pages/OamChatPage.js";
import { reserveMessage } from "./usecases/reserveMessage.js";
import { cancelReservation } from "./usecases/cancelReservation.js";
import type { AppApiClient } from "./appApi/client.js";
import type { WorkerTask } from "./domain/types.js";
import type { ChatPage } from "./line/pages/ChatPage.js";

/**
 * 1サイクル分の処理: タスク取得→直列処理。
 * 認証の壁を検出したタスクがあれば、以降のタスクを処理せず当サイクルを打ち切る（AC-7）。
 */
export async function runCycle(
  api: AppApiClient,
  po: ChatPage,
  opts: { dryRun: boolean; artifactDir: string },
): Promise<void> {
  const tasks = await api.getTasks();

  for (const task of tasks) {
    const abort = await processTask(api, po, task, opts);
    if (abort) {
      return;
    }
  }
}

/** @returns true の場合、当サイクルを中断する（呼び出し元でループを打ち切る）。 */
async function processTask(
  api: AppApiClient,
  po: ChatPage,
  task: WorkerTask,
  opts: { dryRun: boolean; artifactDir: string },
): Promise<boolean> {
  if (task.status === "PENDING") {
    // claim: 処理開始を宣言する。
    await api.reportResult(task.id, { status: "RESERVING" });

    const outcome = await reserveMessage(po, task, opts);
    if (outcome.report) {
      await api.reportResult(task.id, {
        status: outcome.status,
        errorCode: outcome.errorCode,
        errorMessage: outcome.errorMessage,
      });
    }
    return Boolean(outcome.abortCycle);
  }

  if (task.status === "CANCEL_PENDING") {
    const outcome = await cancelReservation(po, task);
    if (outcome.report) {
      await api.reportResult(task.id, {
        status: outcome.status,
        errorCode: outcome.errorCode,
        errorMessage: outcome.errorMessage,
      });
    }
    return Boolean(outcome.abortCycle);
  }

  return false;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main(): Promise<void> {
  const config = loadConfig();
  const api = createAppApiClient(config);

  // storageState（LINEログインセッション）が未配置なら、Playwright の不明瞭な例外より前に
  // パスを含む明示的なエラーで停止する（運用者が RUNBOOK の初回ログイン手順で作成・配置するための検知）。
  if (!existsSync(config.storageStatePath)) {
    throw new Error(
      `storageState が見つかりません: ${config.storageStatePath}。` +
        `RUNBOOK の初回ログイン手順（scripts/create-auth-state.ts）で作成し、VMの永続ボリュームに配置してください。`,
    );
  }

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ storageState: config.storageStatePath });
  const page = await context.newPage();
  const po = new OamChatPage(page, config.oamAccountPath);

  // eslint-disable-next-line no-constant-condition
  while (true) {
    try {
      await runCycle(api, po, { dryRun: config.dryRun, artifactDir: config.artifactDir });
    } catch (err) {
      // 本文・Cookie・トークンを含みうる詳細は出力しない。
      console.error("line-chat-worker: cycle failed:", err instanceof Error ? err.message : "unknown error");
    }
    // storageState は実行毎に再エクスポートしてローリング更新する。
    await context.storageState({ path: config.storageStatePath });
    await sleep(config.pollIntervalMs);
  }
}

const isMainModule = process.argv[1] !== undefined && import.meta.url === `file://${process.argv[1]}`;
if (isMainModule) {
  main().catch((err) => {
    console.error("line-chat-worker: fatal error:", err instanceof Error ? err.message : "unknown error");
    process.exitCode = 1;
  });
}
