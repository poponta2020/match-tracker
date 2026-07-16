import type { Page } from "playwright";
import type { AuthState } from "../../detect/authState.js";
import type {
  ChatPage,
  DeleteReservationResult,
  ScheduledEntryCheck,
} from "./ChatPage.js";

const NOT_IMPLEMENTED_MESSAGE = "not implemented — task7 で実DOM確定";

/**
 * `ChatPage` の Playwright 実装（雛形）。
 *
 * 【厳守】このファイルには実DOMセレクタを一切書かない。すべてのメソッドは
 * `not implemented` を throw する。タスク7（Phase 2 ローカルPoC）で
 * chat.line.biz の実DOMを調査してから実装する。
 *
 * Playwright ロケーター方針（実装時に踏襲すること。ChatPage.ts のコメントも参照）:
 * - getByRole / getByLabel / テキストベースのロケーターを優先する
 * - 自動生成クラス名に依存しない
 * - 複数一致するロケーターはエラーとして扱う
 */
export class OamChatPage implements ChatPage {
  constructor(private readonly page: Page) {}

  async openChat(_chatRoomName: string, _chatRoomId: string): Promise<void> {
    void this.page;
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async verifyTargetChat(_chatRoomName: string, _chatRoomId: string): Promise<boolean> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async detectAuthWall(): Promise<AuthState> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async findDuplicateReservation(_scheduledSendAt: string, _textPrefix: string): Promise<boolean> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async inputMessage(_text: string): Promise<void> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async setScheduledDateTime(_scheduledSendAt: string): Promise<void> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async confirmReservation(): Promise<void> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async verifyScheduledEntry(_scheduledSendAt: string, _textPrefix: string): Promise<ScheduledEntryCheck> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async deleteReservation(_scheduledSendAt: string, _textPrefix: string): Promise<DeleteReservationResult> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }

  async screenshot(_path: string): Promise<void> {
    throw new Error(NOT_IMPLEMENTED_MESSAGE);
  }
}
