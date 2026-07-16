import type { ChatPage } from "../line/pages/ChatPage.js";
import type { WorkerTask } from "../domain/types.js";
import { textPrefix } from "../util/text.js";
import type { UsecaseOutcome } from "./types.js";

export interface ReserveMessageOptions {
  /** true の場合、確定ボタンを押さずスクリーンショットのみ保存する（AC-8）。 */
  dryRun: boolean;
  /** dry-run 時のスクリーンショット保存先ディレクトリ。 */
  artifactDir: string;
}

/**
 * PENDING タスクの予約フロー（要件書 §3.2「予約の登録」）。
 * Page Object インターフェース越しにのみ操作する純粋なロジック層。
 * 呼び出し側（index.ts）が PENDING→RESERVING の claim 報告を行った後に呼ぶ想定。
 */
export async function reserveMessage(
  po: ChatPage,
  task: WorkerTask,
  opts: ReserveMessageOptions,
): Promise<UsecaseOutcome> {
  const chatRoomName = task.chatRoomName ?? "";
  const chatRoomId = task.chatRoomId ?? "";
  const prefix = textPrefix(task.messageText);

  // b. ログイン画面・二段階認証・CAPTCHA・本人確認画面の検出（AC-7）。
  //    突破を試みず即中止し、当サイクルを中断する。
  const authBeforeOpen = await po.detectAuthWall();
  if (authBeforeOpen !== "OK") {
    return authExpiredOutcome();
  }

  // a. 対象グループのチャットを開く→名称＋識別情報を照合する。
  await po.openChat(chatRoomName, chatRoomId);

  const authAfterOpen = await po.detectAuthWall();
  if (authAfterOpen !== "OK") {
    return authExpiredOutcome();
  }

  const matched = await po.verifyTargetChat(chatRoomName, chatRoomId);
  if (!matched) {
    return {
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "TARGET_CHAT_MISMATCH",
      errorMessage: "対象グループのチャットと名称・識別情報が一致しませんでした",
    };
  }

  // c. 既存の同一予約（同一日時・同一本文冒頭）の重複確認。
  const duplicate = await po.findDuplicateReservation(task.scheduledSendAt, prefix);
  if (duplicate) {
    return {
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "DUPLICATE_RESERVATION_FOUND",
      errorMessage: "同一日時・同一本文冒頭の予約が既に存在します",
    };
  }

  // d. 本文入力→予約日時設定。
  await po.inputMessage(task.messageText);
  await po.setScheduledDateTime(task.scheduledSendAt);

  // f. dry-run: 確定ボタンを押さずスクリーンショットのみ保存して終了する。
  if (opts.dryRun) {
    await po.screenshot(dryRunScreenshotPath(opts.artifactDir, task.id));
    return { report: true, status: "DRY_RUN_SUCCEEDED" };
  }

  // d続き. 予約確定→「送信予定」表示と日時・本文の照合を経て初めて RESERVED を報告する。
  await po.confirmReservation();

  const check = await po.verifyScheduledEntry(task.scheduledSendAt, prefix);
  if (check === "MATCHED") {
    return { report: true, status: "RESERVED" };
  }

  // e. 確定後の結果不明（タイムアウト・不一致）は自動再試行せず manual_review_required とする。
  return {
    report: true,
    status: "MANUAL_REVIEW_REQUIRED",
    errorCode: "CONFIRM_RESULT_UNKNOWN",
    errorMessage: `予約確定後の「送信予定」表示を検証できませんでした（結果: ${check}）`,
  };
}

function authExpiredOutcome(): UsecaseOutcome {
  return {
    report: true,
    status: "FAILED",
    errorCode: "LINE_AUTH_EXPIRED",
    errorMessage: "ログイン画面・追加認証・CAPTCHA・本人確認画面を検出したため中止しました",
    abortCycle: true,
  };
}

function dryRunScreenshotPath(artifactDir: string, taskId: number): string {
  return `${artifactDir}/dry-run-task-${taskId}.png`;
}
