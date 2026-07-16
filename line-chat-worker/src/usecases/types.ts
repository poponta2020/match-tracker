import type { ResultStatus } from "../domain/types.js";

/**
 * usecase の実行結果。
 *
 * - `report: true`  … アプリAPI（POST /result）へ報告してよい状態遷移。
 * - `report: false` … 有効な状態遷移が無い（例: CANCEL_PENDING 中の認証失効は
 *   FAILED への遷移が許可されていない）ため、今回は何も報告せずサイクルを
 *   中断する。次サイクルで同じタスクが再度取得され、再試行される。
 *
 * `abortCycle: true` は「メインループを当サイクル中断」の合図（AC-7）。
 */
export type UsecaseOutcome = ReportOutcome | SkipReportOutcome;

export interface ReportOutcome {
  report: true;
  status: ResultStatus;
  errorCode?: string | null;
  errorMessage?: string | null;
  abortCycle?: boolean;
}

export interface SkipReportOutcome {
  report: false;
  abortCycle: true;
  reason: string;
}
