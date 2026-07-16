import type { ChatPage } from "../line/pages/ChatPage.js";
import type { WorkerTask } from "../domain/types.js";
import { textPrefix } from "../util/text.js";
import type { UsecaseOutcome } from "./types.js";

/**
 * CANCEL_PENDING タスクの取消フロー（要件書 §3.2「変更・取消」）。
 * RESERVING への claim は行わない（プロトコル仕様どおり）。
 */
export async function cancelReservation(po: ChatPage, task: WorkerTask): Promise<UsecaseOutcome> {
  const prefix = textPrefix(task.messageText);

  // 認証の壁を検出した場合、CANCEL_PENDING→FAILED は許可されていない状態遷移のため
  // 結果報告はせず（report:false）、当サイクルを中断して次サイクルの再試行に委ねる。
  const authState = await po.detectAuthWall();
  if (authState !== "OK") {
    return {
      report: false,
      abortCycle: true,
      reason: "認証の壁を検出したため取消フローを中止しました（次サイクルで再試行）",
    };
  }

  const result = await po.deleteReservation(task.scheduledSendAt, prefix);

  if (result === "DELETED") {
    return { report: true, status: "CANCELLED" };
  }

  // 旧予約を特定できない場合は manual_review_required（勝手に追加予約しない）。
  return {
    report: true,
    status: "MANUAL_REVIEW_REQUIRED",
    errorCode: "OLD_RESERVATION_NOT_FOUND",
    errorMessage: "取消対象の予約をLINE側で特定できませんでした",
  };
}
