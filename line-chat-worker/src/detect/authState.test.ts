import { describe, expect, it } from "vitest";
import { classifyReloginOutcome, isAuthWall } from "./authState.js";

describe("isAuthWall", () => {
  it("treats OK as no wall and everything else as a wall", () => {
    expect(isAuthWall("OK")).toBe(false);
    expect(isAuthWall("LOGIN_REQUIRED")).toBe(true);
    expect(isAuthWall("CAPTCHA")).toBe(true);
  });
});

describe("classifyReloginOutcome", () => {
  const base = {
    landedOnChatSurface: false,
    landedOnAuthSurface: false,
    credentialChallenge: false,
    buttonsClicked: false,
    returnedToChat: false,
  };

  it("AC-13: transient wall (landed back on chat.line.biz) → SUCCEEDED without clicking", () => {
    // editorMissingAfterOpen 由来の壁。ナビし直すと chat 面に帰着＝セッション有効。
    expect(
      classifyReloginOutcome({ ...base, landedOnChatSurface: true }),
    ).toBe("SUCCEEDED");
  });

  it("chat 面優先: 認証面フラグが立っていても chat 面に帰着していれば SUCCEEDED", () => {
    expect(
      classifyReloginOutcome({
        ...base,
        landedOnChatSurface: true,
        landedOnAuthSurface: true,
      }),
    ).toBe("SUCCEEDED");
  });

  it("chat 面でも認証面でもない（about:blank/ナビ失敗）→ ERROR（安全側）", () => {
    expect(classifyReloginOutcome({ ...base })).toBe("ERROR");
  });

  it("AC-3: 認証面で password欄/reCAPTCHA を検出 → SSO_EXPIRED（突破しない）", () => {
    expect(
      classifyReloginOutcome({
        ...base,
        landedOnAuthSurface: true,
        credentialChallenge: true,
      }),
    ).toBe("SSO_EXPIRED");
  });

  it("認証面で期待ボタンが不在 → SSO_EXPIRED", () => {
    expect(
      classifyReloginOutcome({
        ...base,
        landedOnAuthSurface: true,
        buttonsClicked: false,
      }),
    ).toBe("SSO_EXPIRED");
  });

  it("認証面で2ボタンをクリックしたが chat 面へ帰着しない → SSO_EXPIRED", () => {
    expect(
      classifyReloginOutcome({
        ...base,
        landedOnAuthSurface: true,
        buttonsClicked: true,
        returnedToChat: false,
      }),
    ).toBe("SSO_EXPIRED");
  });

  it("認証面で2ボタンをクリックし chat 面へ帰着 → SUCCEEDED（新セッション発行）", () => {
    expect(
      classifyReloginOutcome({
        ...base,
        landedOnAuthSurface: true,
        buttonsClicked: true,
        returnedToChat: true,
      }),
    ).toBe("SUCCEEDED");
  });

  it("最終帰着を優先: chat 面へ帰着していれば buttonsClicked に依らず SUCCEEDED（認可画面省略フロー）", () => {
    // 1クリック後に認可画面が省略され chat.line.biz へ自動遷移＝2つ目のボタンは押していないが成功。
    expect(
      classifyReloginOutcome({
        ...base,
        landedOnAuthSurface: true,
        buttonsClicked: false,
        returnedToChat: true,
      }),
    ).toBe("SUCCEEDED");
  });
});
