/**
 * アプリ側API契約に対応する共有型。
 * 参照: docs/features/line-chat-reserve-broadcast/implementation-plan.md タスク3・タスク6
 */

/** GET /api/line-chat-worker/tasks が返すタスクの状態。 */
export type TaskStatus = "PENDING" | "CANCEL_PENDING";

/** POST /api/line-chat-worker/{id}/result で報告できる状態。 */
export type ResultStatus =
  | "RESERVING"
  | "RESERVED"
  | "FAILED"
  | "MANUAL_REVIEW_REQUIRED"
  | "DRY_RUN_SUCCEEDED"
  | "CANCELLED";

/** ワーカーが認識するエラーコード（アプリ側は任意文字列として受け取る）。 */
export type WorkerErrorCode =
  | "TARGET_CHAT_MISMATCH"
  | "LINE_AUTH_EXPIRED"
  | "DUPLICATE_RESERVATION_FOUND"
  | "CONFIRM_RESULT_UNKNOWN"
  | "OLD_RESERVATION_NOT_FOUND";

/** GET /api/line-chat-worker/tasks の1要素。 */
export interface WorkerTask {
  id: number;
  broadcastGroupId: number;
  sessionId: number;
  status: TaskStatus;
  chatRoomId: string | null;
  chatRoomName: string | null;
  /** ISO8601（例 "2026-07-18T08:00:00+09:00"）。 */
  scheduledSendAt: string;
  messageText: string;
}

/** POST /api/line-chat-worker/{id}/result のリクエストボディ。 */
export interface ResultReportBody {
  status: ResultStatus;
  errorCode?: string | null;
  errorMessage?: string | null;
}
