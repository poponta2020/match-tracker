/**
 * Phase 2 ローカルPoC ランナー（タスク7）。
 *
 * 実ワーカーコード（OamChatPage + usecases/reserveMessage・cancelReservation）を、
 * 合成 WorkerTask に対して実チャット（テストグループ）で駆動する。
 * アプリAPI・本番DBを介さず、実DOMコードパスだけを検証する。
 *
 * 順に実行:
 *   1. dry-run（`設定` を押さずスクショ → DRY_RUN_SUCCEEDED）
 *   2. 本予約（→ 「送信予定」バナー照合 → RESERVED）
 *   3. 重複検出（同一日時で再予約 → DUPLICATE_RESERVATION_FOUND / MANUAL_REVIEW_REQUIRED）
 *   4. 取消（CANCEL_PENDING → LINE側予約を削除 → CANCELLED）
 *
 * 使い方:
 *   cd line-chat-worker
 *   LINE_OAM_ACCOUNT_PATH=U... POC_CHAT_ROOM_ID=C... POC_CHAT_ROOM_NAME=テスト \
 *     [HEADED=1] [POC_SCHEDULED_SEND_AT=2026-07-18T08:00:00+09:00] npx tsx scripts/poc-run.ts
 *
 * storageState は scripts/create-auth-state.ts で作成しておくこと（既定 ./storage-state.json）。
 */
import { chromium } from "playwright";
import { existsSync, mkdirSync } from "node:fs";
import { OamChatPage } from "../src/line/pages/OamChatPage.js";
import { reserveMessage } from "../src/usecases/reserveMessage.js";
import { cancelReservation } from "../src/usecases/cancelReservation.js";
import type { WorkerTask } from "../src/domain/types.js";

function requireEnv(key: string): string {
  const v = process.env[key];
  if (!v) throw new Error(`missing required env: ${key}`);
  return v;
}

/** 既定の予約日時: 翌日 08:00 JST（10分境界・当日中に発火しない）。 */
function defaultScheduledSendAt(): string {
  const jstNow = new Date(Date.now() + 9 * 3_600_000);
  const t = new Date(Date.UTC(jstNow.getUTCFullYear(), jstNow.getUTCMonth(), jstNow.getUTCDate() + 1));
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}T08:00:00+09:00`;
}

async function main(): Promise<void> {
  const accountPath = requireEnv("LINE_OAM_ACCOUNT_PATH");
  const chatRoomId = requireEnv("POC_CHAT_ROOM_ID");
  const chatRoomName = requireEnv("POC_CHAT_ROOM_NAME");
  const storageStatePath = process.env.STORAGE_STATE_PATH ?? "./storage-state.json";
  const artifactDir = process.env.ARTIFACT_DIR ?? "./poc-artifacts";
  const scheduledSendAt = process.env.POC_SCHEDULED_SEND_AT ?? defaultScheduledSendAt();
  const headed = process.env.HEADED === "1";

  if (!existsSync(storageStatePath)) {
    throw new Error(`storageState が見つかりません: ${storageStatePath}（create-auth-state.ts で作成）`);
  }
  mkdirSync(artifactDir, { recursive: true });

  const task: WorkerTask = {
    id: 9001,
    broadcastGroupId: 1,
    sessionId: 1,
    status: "PENDING",
    chatRoomId,
    chatRoomName,
    scheduledSendAt,
    messageText: [
      "【札分けPoC・自動テスト】",
      `送信予定 ${scheduledSendAt}`,
      "1試合目 8:00 開始",
      "札分けA組: 1, 2, 3",
      "札分けB組: 4, 5, 6",
    ].join("\n"),
  };

  console.log(`room=${chatRoomName}(${chatRoomId}) scheduledSendAt=${scheduledSendAt}`);

  const browser = await chromium.launch({ headless: !headed });
  try {
    const context = await browser.newContext({ storageState: storageStatePath });
    const page = await context.newPage();
    const po = new OamChatPage(page, accountPath);

    // 1. dry-run
    const dry = await reserveMessage(po, task, { dryRun: true, artifactDir });
    console.log("1) DRY-RUN   :", JSON.stringify(dry));

    // 2. 本予約
    const reserved = await reserveMessage(po, task, { dryRun: false, artifactDir });
    console.log("2) RESERVE   :", JSON.stringify(reserved));
    await po.screenshot(`${artifactDir}/poc-2-reserved.png`);

    // 3. 重複検出（同一日時で再予約）
    const dup = await reserveMessage(po, task, { dryRun: false, artifactDir });
    console.log("3) DUPLICATE :", JSON.stringify(dup));

    // 4. 取消
    const cancel = await cancelReservation(po, { ...task, status: "CANCEL_PENDING" });
    console.log("4) CANCEL    :", JSON.stringify(cancel));
    await po.screenshot(`${artifactDir}/poc-4-after-cancel.png`);

    // 判定サマリ
    const ok =
      dry.status === "DRY_RUN_SUCCEEDED" &&
      reserved.status === "RESERVED" &&
      dup.status === "MANUAL_REVIEW_REQUIRED" &&
      dup.errorCode === "DUPLICATE_RESERVATION_FOUND" &&
      cancel.status === "CANCELLED";
    console.log(ok ? "\nPOC_RESULT: PASS" : "\nPOC_RESULT: FAIL");
    if (!ok) process.exitCode = 1;
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error("poc-run failed:", e instanceof Error ? e.message : String(e));
  process.exitCode = 1;
});
