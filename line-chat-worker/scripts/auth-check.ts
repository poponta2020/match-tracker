/**
 * storageState だけで（＝実行時ログイン操作なしで）認証済みで対象チャットに入れるかを確認する
 * ops 診断。予約の作成・削除など副作用は一切行わない。
 *
 * ワーカー本体と同じ headless + storageState + OamChatPage の経路をたどり、
 *   - openChat（ルームURLへ直接ナビ）
 *   - detectAuthWall → "OK"（認証面に飛ばされていない）
 *   - verifyTargetChat → true（対象グループのチャットに入れている）
 * を検証する。セッション失効の有無を人手ログインなしで判定できる。
 *
 * 使い方:
 *   cd line-chat-worker
 *   LINE_OAM_ACCOUNT_PATH=U... POC_CHAT_ROOM_ID=C... POC_CHAT_ROOM_NAME=テスト \
 *     [HEADED=1] npx tsx scripts/auth-check.ts
 *
 * 出力: `AUTH_OK`（セッション有効・入室成功）/ `AUTH_WALL:<state>`（要再ログイン）/ `TARGET_MISMATCH`。
 */
import { chromium } from "playwright";
import { existsSync } from "node:fs";
import { OamChatPage } from "../src/line/pages/OamChatPage.js";

function requireEnv(key: string): string {
  const v = process.env[key];
  if (!v) throw new Error(`missing required env: ${key}`);
  return v;
}

async function main(): Promise<void> {
  const accountPath = requireEnv("LINE_OAM_ACCOUNT_PATH");
  const chatRoomId = requireEnv("POC_CHAT_ROOM_ID");
  const chatRoomName = requireEnv("POC_CHAT_ROOM_NAME");
  const storageStatePath = process.env.STORAGE_STATE_PATH ?? "./storage-state.json";
  const headed = process.env.HEADED === "1";

  if (!existsSync(storageStatePath)) {
    throw new Error(`storageState が見つかりません: ${storageStatePath}（create-auth-state.ts で作成）`);
  }

  // ワーカー本体と同じ headless 起動（実行時ログイン操作なし）。
  const browser = await chromium.launch({ headless: !headed });
  try {
    const context = await browser.newContext({ storageState: storageStatePath });
    const page = await context.newPage();
    const po = new OamChatPage(page, accountPath);

    await po.openChat(chatRoomName, chatRoomId);
    const auth = await po.detectAuthWall();
    if (auth !== "OK") {
      console.log(`AUTH_WALL:${auth}  (要再ログイン: create-auth-state.ts を再実行)`);
      process.exitCode = 1;
      return;
    }
    const target = await po.verifyTargetChat(chatRoomName, chatRoomId);
    if (!target) {
      console.log("TARGET_MISMATCH  (認証は通ったが対象チャットに入れていない)");
      process.exitCode = 1;
      return;
    }
    console.log("AUTH_OK  (storageState のみで認証済み・対象チャット入室成功／実行時ログイン操作なし)");
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error("auth-check failed:", e instanceof Error ? e.message : String(e));
  process.exitCode = 1;
});
