import type { ChatPage } from "../line/pages/ChatPage.js";
import type { WorkerTask } from "../domain/types.js";
import { textPrefix } from "../util/text.js";
import type { UsecaseOutcome } from "./types.js";

/**
 * CANCEL_PENDING タスクの取消フロー（要件書 §3.2「変更・取消」）。
 * RESERVING への claim は行わない（プロトコル仕様どおり）。
 *
 * 予約フローと同様に、削除の前に必ず対象チャットを開いて認証壁を確認し、名称＋識別情報を照合する。
 * これをしないと、同一サイクルの直前タスクで開いていた別グループのチャットに対して
 * 予約削除を試みる恐れがある（LINE側予約の誤削除）。
 */
export async function cancelReservation(po: ChatPage, task: WorkerTask): Promise<UsecaseOutcome> {
  const chatRoomName = task.chatRoomName ?? "";
  const chatRoomId = task.chatRoomId ?? "";
  const prefix = textPrefix(task.messageText);

  // 認証の壁を検出した場合は突破を試みず中止する（AC-7）。CANCEL_PENDING→FAILED は許可されていないため、
  // 許可遷移 CANCEL_PENDING→MANUAL_REVIEW_REQUIRED で LINE_AUTH_EXPIRED を報告し（無通知の滞留を防ぐ）、
  // 当サイクルを中断する。
  const authBeforeOpen = await po.detectAuthWall();
  if (authBeforeOpen !== "OK") {
    return authExpiredOutcome();
  }

  // 対象グループのチャットを開く→（開いた後にも認証壁を再確認）→名称＋識別情報を照合する。
  await po.openChat(chatRoomName, chatRoomId);

  const authAfterOpen = await po.detectAuthWall();
  if (authAfterOpen !== "OK") {
    return authExpiredOutcome();
  }

  const matched = await po.verifyTargetChat(chatRoomName, chatRoomId);
  if (!matched) {
    // 対象チャットと一致しない場合は絶対に削除しない（誤チャット操作の防止）。
    return {
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "TARGET_CHAT_MISMATCH",
      errorMessage: "対象グループのチャットと名称・識別情報が一致しませんでした",
    };
  }

  const result = await po.deleteReservation(task.scheduledSendAt, prefix);

  if (result === "DELETED") {
    return { report: true, status: "CANCELLED" };
  }

  // 削除できたことを確認できない限り CANCELLED は報告しない（LINE側に旧予約が残ったまま
  // 「取消済み」と記録されると、意図しない配信に気づけなくなる）。
  if (result === "UNKNOWN") {
    return {
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "CANCEL_RESULT_UNKNOWN",
      errorMessage: "予約の有無・削除結果を確認できませんでした（LINE側に予約が残っている可能性があります）",
    };
  }

  // 旧予約が存在しないことを確定できた場合（既に削除済み等）。勝手に追加予約はしない。
  return {
    report: true,
    status: "MANUAL_REVIEW_REQUIRED",
    errorCode: "OLD_RESERVATION_NOT_FOUND",
    errorMessage: "取消対象の予約をLINE側で特定できませんでした",
  };
}

/**
 * 取消フロー中の認証失効の報告。CANCEL_PENDING からは FAILED へ遷移できないため
 * MANUAL_REVIEW_REQUIRED＋errorCode=LINE_AUTH_EXPIRED を用いる（許可遷移・AC-7）。
 */
function authExpiredOutcome(): UsecaseOutcome {
  return {
    report: true,
    status: "MANUAL_REVIEW_REQUIRED",
    errorCode: "LINE_AUTH_EXPIRED",
    errorMessage: "ログイン画面・追加認証・CAPTCHA・本人確認画面を検出したため取消フローを中止しました",
    abortCycle: true,
  };
}
