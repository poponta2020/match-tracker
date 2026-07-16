import type { AuthState } from "../../detect/authState.js";

/**
 * 予約確定後にチャット画面上の「送信予定」表示を検証した結果。
 * - MATCHED: 表示された日時・本文冒頭が予約内容と一致
 * - MISMATCHED: 表示はあるが内容が不一致（本文/時刻がずれている）
 * - TIMEOUT: 一定時間内に確認できなかった（結果不明）
 */
export type ScheduledEntryCheck = "MATCHED" | "MISMATCHED" | "TIMEOUT";

/** 予約削除操作の結果。 */
export type DeleteReservationResult = "DELETED" | "NOT_FOUND";

/**
 * LINE Official Account Manager のチャット画面に対する操作インターフェース。
 *
 * 【重要】このインターフェースの実装（`OamChatPage`）は雛形であり、実DOMセレクタを
 * 一切含まない（タスク7で Phase 2 ローカルPoC を通じて確定する）。usecases/ 配下の
 * ロジックはこのインターフェース越しにのみ操作し、モック実装でユニットテストする。
 *
 * Playwright ロケーター方針（タスク7で実装する際に踏襲すること。要件書 §6 準拠）:
 * - getByRole / getByLabel / テキストベースのロケーターを優先する
 * - 自動生成されたクラス名（例: css-xxxxx, ハッシュ付きクラス）に依存しない
 * - 複数要素に一致するロケーターはエラーとして扱う（`.first()` 等で握りつぶさない）
 */
export interface ChatPage {
  /** 対象グループのチャットルームを開く。 */
  openChat(chatRoomName: string, chatRoomId: string): Promise<void>;

  /**
   * 開いているチャットが対象グループ（名称＋識別情報）と一致するか検証する。
   * 不一致の場合 false を返す（呼び出し側で TARGET_CHAT_MISMATCH として扱う）。
   */
  verifyTargetChat(chatRoomName: string, chatRoomId: string): Promise<boolean>;

  /**
   * ログイン画面・QR/二段階認証・CAPTCHA・本人確認画面の有無を検出する。
   * 認証の自動突破は行わない（検出のみ）。
   */
  detectAuthWall(): Promise<AuthState>;

  /**
   * 同一日時・同一本文冒頭の予約が既に存在するか確認する。
   * @param scheduledSendAt ISO8601（例 "2026-07-18T08:00:00+09:00"）
   * @param textPrefix 本文冒頭（重複判定用に切り出した文字列）
   */
  findDuplicateReservation(scheduledSendAt: string, textPrefix: string): Promise<boolean>;

  /** チャット入力欄に本文を入力する。 */
  inputMessage(text: string): Promise<void>;

  /** 予約日時を設定する。 */
  setScheduledDateTime(scheduledSendAt: string): Promise<void>;

  /** 予約を確定する（このメソッド呼び出し自体では成功を意味しない）。 */
  confirmReservation(): Promise<void>;

  /**
   * 予約確定後、チャット画面に「送信予定」が表示され、日時・本文が一致することを検証する。
   * 予約ボタン押下だけでは成功と判定しないための最終確認ステップ。
   */
  verifyScheduledEntry(scheduledSendAt: string, textPrefix: string): Promise<ScheduledEntryCheck>;

  /**
   * 該当する予約をLINE側から削除し、一覧から消えたことを確認する。
   * 旧予約を特定できない場合は NOT_FOUND を返す（呼び出し側で追加予約はしない）。
   */
  deleteReservation(scheduledSendAt: string, textPrefix: string): Promise<DeleteReservationResult>;

  /** 現在の画面のスクリーンショットを保存する（本文・Cookie を含む生ログは別途出力しないこと）。 */
  screenshot(path: string): Promise<void>;
}
