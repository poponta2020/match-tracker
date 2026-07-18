import type { Page } from "playwright";
import type { AuthState } from "../../detect/authState.js";
import { jstBannerDateTime, jstDateInputValue, jstTimeInputValue } from "../../util/datetime.js";
import type {
  ChatPage,
  DeleteReservationResult,
  ScheduledEntryCheck,
} from "./ChatPage.js";

/**
 * `ChatPage` の Playwright 実装。
 *
 * 実DOMセレクタはタスク7（Phase 2 ローカルPoC）で chat.line.biz を実調査して確定した。
 * 詳細な根拠は docs/features/line-chat-reserve-broadcast/phase2-dom-findings.md を参照。
 *
 * ロケーター方針（要件書 §6）:
 * - getByRole / getByLabel / テキスト・安定属性（`#editor`, `aria-label`）を優先し、自動生成クラス名に依存しない
 * - 認証情報・本文は stdout に出力しない（スクショは artifact のみ）
 */
export class OamChatPage implements ChatPage {
  /** ログイン後の認証面ホスト（ここに居たら壁とみなす）。 */
  private static readonly AUTH_HOSTS = new Set(["account.line.biz", "access.line.me"]);
  private static readonly SPA_SETTLE_MS = 1_500;
  private static readonly ROOM_READY_TIMEOUT_MS = 20_000;
  private static readonly BANNER_TIMEOUT_MS = 15_000;

  private static readonly SEL_EDITOR = "#editor";
  private static readonly SEL_SCHEDULE_TOGGLE = 'a[aria-label="oa.chat.button.scheduledmessages"]';
  private static readonly SEL_CUSTOM_RADIO = "#date3";
  private static readonly SEL_DATE_INPUT = 'input[type="date"]';
  private static readonly SEL_TIME_INPUT = 'input[type="time"]';
  private static readonly BANNER_KEYWORD = "送信されます";

  constructor(
    private readonly page: Page,
    /** OAM チャットのアカウントパス（例 "U186..."）。config.oamAccountPath 由来。 */
    private readonly accountPath: string,
  ) {}

  /**
   * openChat 後に #editor が現れなかった（＝chat.line.biz 上でルームを開けない異常）を表すフラグ。
   * 認証面リダイレクトを伴わない失効（認証中断・セッション不完全・エラー画面）も壁として検知する。
   * openChat が実行毎に false へリセットするため、常に直近の openChat 結果を反映する。
   */
  private editorMissingAfterOpen = false;

  private roomUrl(chatRoomId: string): string {
    return `https://chat.line.biz/${this.accountPath}/chat/${chatRoomId}`;
  }

  private currentHost(): string {
    try {
      return new URL(this.page.url()).host;
    } catch {
      return "";
    }
  }

  /** 認証面（account.line.biz / access.line.me）に居るか。about:blank・chat.line.biz は false。 */
  private isOnAuthSurface(): boolean {
    return OamChatPage.AUTH_HOSTS.has(this.currentHost());
  }

  async openChat(_chatRoomName: string, chatRoomId: string): Promise<void> {
    this.editorMissingAfterOpen = false;
    // commit で即解決（重いSPAで domcontentloaded がタイムアウトするのを避ける）。失敗しても続行し
    // 後段の待機で状態を確定する。
    await this.page
      .goto(this.roomUrl(chatRoomId), { waitUntil: "commit", timeout: 60_000 })
      .catch(() => undefined);

    // ルーム準備（#editor 出現）か認証面リダイレクトのどちらかが確定するまで待つ。
    const deadline = Date.now() + OamChatPage.ROOM_READY_TIMEOUT_MS;
    while (Date.now() < deadline) {
      if (this.isOnAuthSurface()) return; // host ベースの壁判定（detectAuthWall）に委ねる
      if ((await this.page.locator(OamChatPage.SEL_EDITOR).count()) > 0) {
        await this.page.waitForTimeout(OamChatPage.SPA_SETTLE_MS);
        return;
      }
      await this.page.waitForTimeout(500);
    }
    // 認証面リダイレクトも無いのに #editor が出ない＝chat.line.biz 上でルームを開けない異常
    // （認証中断・セッション不完全・エラー画面等）。壁として扱い detectAuthWall で再ログインを促す。
    this.editorMissingAfterOpen = true;
  }

  async verifyTargetChat(chatRoomName: string, chatRoomId: string): Promise<boolean> {
    const urlOk = this.page.url().includes(chatRoomId);
    // チャットヘッダーのグループ名（level=4 の見出し）。
    const nameOk =
      (await this.page.getByRole("heading", { level: 4, name: chatRoomName, exact: true }).count()) > 0;
    return urlOk && nameOk;
  }

  async detectAuthWall(): Promise<AuthState> {
    // positive判定のみ: 認証面に居る、または openChat 後に #editor が出なかった場合だけ壁とする。
    // openChat 前の初回は page=about:blank かつ editorMissingAfterOpen=false のため OK を返し、
    // 誤ってサイクルを止めない（openChat が実行毎にフラグをリセットする）。
    return this.isOnAuthSurface() || this.editorMissingAfterOpen ? "LOGIN_REQUIRED" : "OK";
  }

  async findDuplicateReservation(scheduledSendAt: string, _textPrefix: string): Promise<boolean> {
    // 対象日時と一致する「送信予定」バナーが既に存在するか（バナーは日時のみ含む・本文は含まない）。
    const expected = jstBannerDateTime(scheduledSendAt);
    return (await this.scheduledBanner(expected).count()) > 0;
  }

  async inputMessage(text: string): Promise<void> {
    const editor = this.page.locator(OamChatPage.SEL_EDITOR);
    await editor.click();
    // 既存ドラフトを消してから入力（サイクル間の残留防止）。
    await this.page.keyboard.press("Control+A");
    await this.page.keyboard.press("Delete");

    // Enter=送信 なので改行は Shift+Enter。行内は type。
    const lines = text.split("\n");
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].length > 0) {
        await this.page.keyboard.type(lines[i]);
      }
      if (i < lines.length - 1) {
        await this.page.keyboard.press("Shift+Enter");
      }
    }

    // echo検証（本文の改行・文字化けを確定前に検出）。textarea-ex.value は配列を返すことがある。
    const raw = await editor.evaluate((el) => (el as unknown as { value: unknown }).value);
    const candidates = Array.isArray(raw)
      ? [raw.join(""), raw.join("\n")]
      : [String(raw ?? "")];
    if (!candidates.some((c) => c === text)) {
      throw new Error("INPUT_ECHO_MISMATCH: 入力欄の内容が意図した本文と一致しませんでした");
    }
  }

  async setScheduledDateTime(scheduledSendAt: string): Promise<void> {
    // 予約モーダル（送信ボタン横の分割ドロップダウン）を開く。
    await this.page.locator(OamChatPage.SEL_SCHEDULE_TOGGLE).first().click();
    await this.page.locator(OamChatPage.SEL_CUSTOM_RADIO).waitFor({ state: "attached", timeout: 10_000 });

    // 任意日時(CUSTOM)を選択。隠しradioへの通常clickは効かないため実DOM clickイベントを dispatch。
    const dateInput = this.page.locator(OamChatPage.SEL_DATE_INPUT);
    await this.page.locator(OamChatPage.SEL_CUSTOM_RADIO).dispatchEvent("click");
    // 有効化を待つ（フォールバックでラベル左端のラジオ円をクリック）。
    if (!(await this.waitEnabled(dateInput, 3_000))) {
      await this.page
        .locator('label[for="date3"]')
        .click({ position: { x: 8, y: 12 } })
        .catch(() => undefined);
      await this.waitEnabled(dateInput, 3_000);
    }

    // native の date/time 入力へ投入（time は step=600＝10分単位。非境界はスナップされる）。
    await dateInput.fill(jstDateInputValue(scheduledSendAt));
    await this.page.locator(OamChatPage.SEL_TIME_INPUT).fill(jstTimeInputValue(scheduledSendAt));
    // 【dry-run はここで screenshot して終了する（`設定` を押さない）】
  }

  async confirmReservation(): Promise<void> {
    await this.page.getByRole("button", { name: "設定", exact: true }).click();
    await this.page.waitForTimeout(OamChatPage.SPA_SETTLE_MS);
  }

  async verifyScheduledEntry(scheduledSendAt: string, _textPrefix: string): Promise<ScheduledEntryCheck> {
    const expected = jstBannerDateTime(scheduledSendAt);
    // 確定後、いずれかの「送信予定」バナーが出るまで待つ（出なければ TIMEOUT）。
    const anyBanner = this.page.getByText(new RegExp(OamChatPage.BANNER_KEYWORD));
    try {
      await anyBanner.first().waitFor({ state: "visible", timeout: OamChatPage.BANNER_TIMEOUT_MS });
    } catch {
      return "TIMEOUT";
    }
    // 対象日時と一致するバナーがあれば MATCHED、バナーはあるが日時不一致なら MISMATCHED。
    return (await this.scheduledBanner(expected).count()) > 0 ? "MATCHED" : "MISMATCHED";
  }

  async deleteReservation(scheduledSendAt: string, _textPrefix: string): Promise<DeleteReservationResult> {
    const expected = jstBannerDateTime(scheduledSendAt);
    const banner = this.scheduledBanner(expected);
    if ((await banner.count()) === 0) {
      return "NOT_FOUND";
    }

    // 対象バナーの ⋮（option）→ メニューの「削除」→ 確認モーダルの「削除」。
    await banner.first().getByRole("button", { name: "option" }).click();
    await this.page.getByRole("button", { name: "削除", exact: true }).click();
    await this.page
      .getByText("この予約メッセージを削除しますか")
      .waitFor({ state: "visible", timeout: 5_000 });
    // メニューは閉じ、残る「削除」は確認モーダルのボタン。
    await this.page.getByRole("button", { name: "削除", exact: true }).click();

    // 対象バナーが消える（hidden/detached）まで待つ。固定待ち＋即時countだと OAM の削除反映が
    // 遅れた場合に、実際は削除進行中でも NOT_FOUND と誤判定するため（消失を明示的に待つ）。
    try {
      await banner.first().waitFor({ state: "hidden", timeout: OamChatPage.BANNER_TIMEOUT_MS });
    } catch {
      return "NOT_FOUND";
    }
    return "DELETED";
  }

  async screenshot(path: string): Promise<void> {
    await this.page.screenshot({ path, fullPage: false });
  }

  /** 指定日時を含む「送信予定」バナー（role=alert かつ "…送信されます" かつ日時一致）。 */
  private scheduledBanner(expectedDateTime: string) {
    return this.page
      .getByRole("alert")
      .filter({ hasText: OamChatPage.BANNER_KEYWORD })
      .filter({ hasText: expectedDateTime });
  }

  private async waitEnabled(locator: ReturnType<Page["locator"]>, timeoutMs: number): Promise<boolean> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (await locator.isEnabled().catch(() => false)) return true;
      await this.page.waitForTimeout(200);
    }
    return false;
  }
}
