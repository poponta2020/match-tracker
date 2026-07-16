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
