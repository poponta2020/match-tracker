import type { Page } from "playwright";
import type { AuthState } from "../../detect/authState.js";
import { jstBannerDateTime, jstDateInputValue, jstTimeInputValue } from "../../util/datetime.js";
import type {
  ChatPage,
  DeleteReservationResult,
  DuplicateCheck,
  ScheduledEntryCheck,
} from "./ChatPage.js";

/**
 * OAM 内部API `…/messages/scheduled` の1件分（2026-07-18 実測）。
 * 実サンプル: `{"scheduledMessageId":"agpx…","message":{"text":"…","type":"textV2"},
 *              "scheduledAt":1788217200000,"status":"SCHEDULED"}`
 */
interface ScheduledMessage {
  /** 送信予定時刻（epoch ミリ秒）。 */
  scheduledAt: number;
  /** "SCHEDULED" 以外（送信済み・取消済み等）は重複判定の対象外。 */
  status: string;
}

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

  /**
   * 予約一覧API1回あたりのハードタイムアウト。
   * `page.evaluate` にも browser の `fetch` にも既定のタイムアウトが無いため、
   * これが無いと応答を返さないサーバーで await が戻らず、ポーリングの期限判定に到達できない
   * （常駐ワーカーがタスクを占有したまま無期限に停止する）。
   */
  private static readonly SCHEDULED_API_TIMEOUT_MS = 10_000;
  /**
   * `scheduledAt` を epoch ミリ秒として妥当と見なす範囲（2000-01-01 〜 2100-01-01）。
   * epoch「秒」や単位違いの値は範囲外となり UNKNOWN に落ちる（1970年として解釈されて
   * 「日時が一致しない＝予約なし」と誤判定するのを防ぐ）。Date の表現範囲外による
   * `toISOString()` の RangeError もここで防いでいる。
   */
  private static readonly EPOCH_MS_MIN = 946_684_800_000;
  private static readonly EPOCH_MS_MAX = 4_102_444_800_000;

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

  /** 直近に openChat したルームID。予約一覧APIの組み立てに使う。 */
  private currentChatRoomId: string | null = null;

  private roomUrl(chatRoomId: string): string {
    return `https://chat.line.biz/${this.accountPath}/chat/${chatRoomId}`;
  }

  /**
   * 対象ルームの予約メッセージ一覧を OAM 内部APIから取得する（DOM 非依存）。
   *
   * 【なぜ DOM ではなく API か】「予約が無い」ことの確定は、バナー要素の不在では原理的に行えない。
   * SPA の描画は #editor の出現とは独立で、待ち時間を固定にすると重いルームで
   * 「まだ描画されていない」を「予約が無い」と誤認する（＝二重予約）。この経路は
   * ルーム表示時に OAM 自身が叩いている API で、応答は予約の有無そのものを表す。
   *
   * @returns 取得・解釈に成功した場合のみ配列。失敗（非200・JSON不正・想定外の形）は
   *          すべて `null`（＝UNKNOWN）を返す。**呼び出し側は null を「予約なし」と扱ってはならない。**
   */
  private async fetchScheduledMessages(): Promise<ScheduledMessage[] | null> {
    const chatRoomId = this.currentChatRoomId;
    if (chatRoomId === null) return null;
    const path = `/api/v1/bots/${this.accountPath}/chats/${chatRoomId}/messages/scheduled`;

    let raw: { status: number; body: string };
    try {
      raw = await this.page.evaluate(
        (arg) => {
          // AbortController でリクエスト（ボディ読み出しを含む）を必ず有限時間で打ち切る。
          const controller = new AbortController();
          const timer = setTimeout(() => controller.abort(), arg.timeoutMs);
          return fetch(arg.path, { credentials: "include", signal: controller.signal })
            .then((res) => res.text().then((body) => ({ status: res.status, body })))
            .finally(() => clearTimeout(timer));
        },
        { path, timeoutMs: OamChatPage.SCHEDULED_API_TIMEOUT_MS },
      );
    } catch {
      return null; // ページ遷移中・オリジン不一致・ネットワーク断・タイムアウトなど
    }
    if (raw.status !== 200) return null;

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw.body);
    } catch {
      return null;
    }
    const list = (parsed as { list?: unknown } | null)?.list;
    if (!Array.isArray(list)) return null;

    const entries: ScheduledMessage[] = [];
    for (const item of list) {
      const e = item as { scheduledAt?: unknown; status?: unknown };
      // 想定した形でない要素が1つでもあれば、一覧全体を「解釈できなかった」として扱う。
      // 一部だけ読めた前提で「一致なし＝予約なし」と結論づけると誤判定が二重予約になるため。
      // epoch ミリ秒として妥当な整数であることまで検証する（単位違い・異常値を素通しすると
      // 「1970年の予約」として日時が一致せず NONE に潰れ、二重予約に直結する）。
      if (
        typeof e?.scheduledAt !== "number" ||
        !Number.isInteger(e.scheduledAt) ||
        e.scheduledAt < OamChatPage.EPOCH_MS_MIN ||
        e.scheduledAt > OamChatPage.EPOCH_MS_MAX ||
        typeof e?.status !== "string"
      ) {
        return null;
      }
      entries.push({ scheduledAt: e.scheduledAt, status: e.status });
    }
    return entries;
  }

  /**
   * 一覧における「対象日時の予約」の状態（JST壁時計・分精度で比較）。
   *
   * 判定を**対象日時のエントリだけ**に絞っているのが要点。`SCHEDULED` 以外の状態値は
   * それが有効な予約を表すのか判断できないが、
   * - 対象日時に存在すれば `UNKNOWN`（＝重複かもしれないので予約を作らせない）
   * - 別日時にしか存在しなければ対象の判定に無関係なので `NONE` のままでよい
   *
   * 「一覧に1件でも未知の状態があれば全体を UNKNOWN」にはしない。送信済みエントリが一覧に
   * 残る仕様だった場合、前日の配信が残っているだけで以後の予約が永久に作れなくなるため
   * （実測では送信後の一覧は空だったが、状態値の全集合は未確認なので保守的にこの形にする）。
   */
  private reservationStateAt(entries: ScheduledMessage[], scheduledSendAt: string): DuplicateCheck {
    const expected = jstBannerDateTime(scheduledSendAt);
    const atTarget = entries.filter(
      (e) => jstBannerDateTime(new Date(e.scheduledAt).toISOString()) === expected,
    );
    if (atTarget.length === 0) return "NONE";
    if (atTarget.some((e) => e.status === "SCHEDULED")) return "FOUND";
    return "UNKNOWN";
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
    this.currentChatRoomId = chatRoomId;
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

  async findDuplicateReservation(scheduledSendAt: string, _textPrefix: string): Promise<DuplicateCheck> {
    // 予約一覧APIを唯一の根拠にする（バナーDOMは描画待ちのレースで「無い」と誤認しうる）。
    // 本文冒頭では絞らない: 同一日時で本文だけ異なる予約を「別物＝重複なし」と誤判定すると
    // 二重予約になるため、日時一致のみで保守的に重複とみなす。
    const entries = await this.fetchScheduledMessages();
    if (entries === null) return "UNKNOWN";
    return this.reservationStateAt(entries, scheduledSendAt);
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
    // 確定直後はサーバ反映に若干の遅れがあるため、一覧に現れるまでポーリングする。
    // 判定は findDuplicateReservation と同じ予約一覧APIで行い、DOM 描画の遅速に左右されないようにする。
    const deadline = Date.now() + OamChatPage.BANNER_TIMEOUT_MS;
    let lastEntries: ScheduledMessage[] | null = null;
    while (Date.now() < deadline) {
      const entries = await this.fetchScheduledMessages();
      if (entries !== null) {
        lastEntries = entries;
        if (this.reservationStateAt(entries, scheduledSendAt) === "FOUND") return "MATCHED";
      }
      await this.page.waitForTimeout(500);
    }
    // 予約自体は存在するのに対象日時と一致しない＝10分丸め等でずれた（サイレント誤送信を防ぐ）。
    if (lastEntries !== null && lastEntries.some((e) => e.status === "SCHEDULED")) return "MISMATCHED";
    // 一件も現れない、または一覧を取得できなかった＝結果不明。
    return "TIMEOUT";
  }

  async deleteReservation(scheduledSendAt: string, _textPrefix: string): Promise<DeleteReservationResult> {
    // 存在有無はまず予約一覧APIで確定する（DOM の描画待ちで NOT_FOUND と誤判定しないため）。
    const before = await this.fetchScheduledMessages();
    if (before === null) return "UNKNOWN";
    const state = this.reservationStateAt(before, scheduledSendAt);
    // 対象日時に解釈できない状態のエントリがある場合、削除すべきか判断できない。
    // 誤って別状態のメッセージを消さないよう、操作せず人手確認へ回す。
    if (state === "UNKNOWN") return "UNKNOWN";
    if (state === "NONE") return "NOT_FOUND";

    // ここから先は「予約が存在する」ことが確定している。削除操作にはバナーのUIが要るので、
    // 描画されるまで待つ（出ないのは想定外＝異常。NOT_FOUND ではなく UNKNOWN で人手確認へ）。
    const expected = jstBannerDateTime(scheduledSendAt);
    const banner = this.scheduledBanner(expected);
    try {
      await banner.first().waitFor({ state: "visible", timeout: OamChatPage.BANNER_TIMEOUT_MS });
    } catch {
      return "UNKNOWN";
    }

    // 対象バナーの ⋮（option）→ メニューの「削除」→ 確認モーダルの「削除」。
    await banner.first().getByRole("button", { name: "option" }).click();
    await this.page.getByRole("button", { name: "削除", exact: true }).click();
    await this.page
      .getByText("この予約メッセージを削除しますか")
      .waitFor({ state: "visible", timeout: 5_000 });
    // メニューは閉じ、残る「削除」は確認モーダルのボタン。
    await this.page.getByRole("button", { name: "削除", exact: true }).click();

    // 削除の成否も一覧APIで確定する（バナーの消失は描画都合で遅れうるため根拠にしない）。
    const deadline = Date.now() + OamChatPage.BANNER_TIMEOUT_MS;
    while (Date.now() < deadline) {
      const after = await this.fetchScheduledMessages();
      // 対象日時のエントリが完全に消えた（NONE）ときだけ削除成功と見なす。
      // UNKNOWN のまま期限切れなら、残っている可能性を否定できないので DELETED にはしない。
      if (after !== null && this.reservationStateAt(after, scheduledSendAt) === "NONE") return "DELETED";
      await this.page.waitForTimeout(500);
    }
    // 消えたことを確認できなかった＝LINE側に残っている可能性がある。CANCELLED を報告してはならない。
    return "UNKNOWN";
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
