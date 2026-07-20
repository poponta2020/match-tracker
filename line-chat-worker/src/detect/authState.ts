/**
 * LINE Official Account Manager 操作中に検出しうる認証状態。
 * 実DOM判定ロジックはタスク7で確定する（ここでは型と純粋な判定関数のみ）。
 */
export type AuthState =
  | "OK"
  | "LOGIN_REQUIRED"
  | "TWO_FACTOR"
  | "CAPTCHA"
  | "IDENTITY_CHECK";

/** OK 以外＝認証の壁（突破を試みず即中止する対象）。 */
export function isAuthWall(state: AuthState): boolean {
  return state !== "OK";
}

/**
 * クリックスルー再ログイン（line-chat-auto-relogin タスク1）の結果。
 * - SUCCEEDED   … 新セッションが張り直せた／そもそもセッションは有効だった（transient wall）。当該タスクを1回リトライしてよい。
 * - SSO_EXPIRED … 30日SSO失効（認証面で password欄/reCAPTCHA が出た・期待ボタンが不在）。突破せず既存フォールバックに委ねる。
 * - ERROR       … 想定外の異常（ナビ失敗・例外）。安全側に倒し、既存の auth-expired outcome をそのまま報告する。
 */
export type ReloginResult = "SUCCEEDED" | "SSO_EXPIRED" | "ERROR";

/**
 * 再ログイン試行中に観測した DOM/URL シグナルから {@link ReloginResult} を判定する純関数。
 *
 * 【最重要（requirements §3.2 A・AC-13）】「期待ボタン不在＝SSO失効」と判定してよいのは
 * <b>認証面に居るときだけ</b>。ナビ後に chat.line.biz へ帰着している（＝`editorMissingAfterOpen`
 * 由来の transient wall・セッションは有効）場合は、何もクリックせず {@code SUCCEEDED} を返す。
 * ここで誤って {@code SSO_EXPIRED} に倒すと、健全セッションで誤フォールバックpush（課金）＋偽アラートを誘発する。
 */
export function classifyReloginOutcome(signals: {
  /** ナビ後、chat.line.biz に帰着したか（＝認証面へリダイレクトされず・セッション有効な transient wall）。 */
  landedOnChatSurface: boolean;
  /** 認証面（account.line.biz / access.line.me）に居るか。 */
  landedOnAuthSurface: boolean;
  /** 認証面で password欄 or reCAPTCHA を検出したか（＝完全失効・絶対に突破しない）。 */
  credentialChallenge: boolean;
  /** 期待した2ボタン（「LINE account」→「Log in」）をすべてクリックできたか。 */
  buttonsClicked: boolean;
  /** 2クリック後、chat.line.biz へ帰着したか（＝新 `__Host-chat-ses` 発行）。 */
  returnedToChat: boolean;
}): ReloginResult {
  // transient wall: セッションは有効。クリックせずに成功（リトライで room を開き直せば通る）。
  if (signals.landedOnChatSurface) {
    return "SUCCEEDED";
  }
  // chat 面でも認証面でもない（about:blank・ネットワークエラー等）＝状態不明。安全側に ERROR。
  if (!signals.landedOnAuthSurface) {
    return "ERROR";
  }
  // 認証面: password欄/reCAPTCHA が出た＝30日SSO失効。突破せず失敗（既存フォールバックに委ねる）。
  if (signals.credentialChallenge) {
    return "SSO_EXPIRED";
  }
  // 認証面で期待ボタンが不在＝SSO失効（方式選択/許可ボタンが出ない）。
  if (!signals.buttonsClicked) {
    return "SSO_EXPIRED";
  }
  // 2クリック後に chat.line.biz へ帰着すれば新セッション発行。帰着しなければ失効扱い。
  return signals.returnedToChat ? "SUCCEEDED" : "SSO_EXPIRED";
}
