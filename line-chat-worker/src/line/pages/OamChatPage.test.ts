import { describe, expect, it, vi } from "vitest";
import type { Page } from "playwright";
import { OamChatPage } from "./OamChatPage.js";

const ACCOUNT = "U16c48919ba75082b834935062155dbba";
const ROOM = "C432cd420dfface41b6201aaca8fd15f3";
const SEND_AT = "2026-09-01T08:00:00+09:00";

/**
 * 2026-07-18 に実際の OAM から採取した `…/messages/scheduled` の応答。
 * `scheduledAt` は epoch ミリ秒で、この値が JST 2026/09/01 08:00 に対応する。
 */
const REAL_PAYLOAD = {
  list: [
    {
      scheduledMessageId: "agpxkn3ln62vquly3e4ccnwpai",
      bizId: "c53c03d0-5c82-11ea-bd9a-fa163eaabe5f",
      message: { text: "【調査用・自動テスト】\nこの予約は直後に削除されます。", type: "textV2" },
      scheduledAt: 1788217200000,
      status: "SCHEDULED",
    },
  ],
};

/**
 * `openChat` → `findDuplicateReservation` を通せる最小の Page スタブ。
 * `evaluate` が予約一覧APIの応答（status/body）を返す前提で組み立てる。
 */
function createPage(response: { status: number; body: string } | Error): Page {
  const evaluate = vi.fn().mockImplementation(() => {
    if (response instanceof Error) return Promise.reject(response);
    return Promise.resolve(response);
  });
  return {
    goto: vi.fn().mockResolvedValue(undefined),
    url: () => `https://chat.line.biz/${ACCOUNT}/chat/${ROOM}`,
    locator: () => ({ count: () => Promise.resolve(1) }),
    waitForTimeout: vi.fn().mockResolvedValue(undefined),
    evaluate,
  } as unknown as Page;
}

async function checkDuplicate(response: { status: number; body: string } | Error): Promise<string> {
  const page = createPage(response);
  const po = new OamChatPage(page, ACCOUNT);
  await po.openChat("グループ名", ROOM);
  return po.findDuplicateReservation(SEND_AT, "本文冒頭");
}

const ok = (body: unknown): { status: number; body: string } => ({ status: 200, body: JSON.stringify(body) });

describe("OamChatPage.findDuplicateReservation", () => {
  it("detects an existing reservation at the same JST datetime (real captured payload)", async () => {
    expect(await checkDuplicate(ok(REAL_PAYLOAD))).toBe("FOUND");
  });

  it("returns NONE only when the list is definitively empty", async () => {
    expect(await checkDuplicate(ok({ list: [] }))).toBe("NONE");
  });

  it("returns NONE when reservations exist but none match the target datetime", async () => {
    // 取消→再予約で送信時刻が変わった場合、別時刻の予約が残っていても重複ではない。
    const other = { list: [{ ...REAL_PAYLOAD.list[0], scheduledAt: 1788217200000 + 3_600_000 }] };
    expect(await checkDuplicate(ok(other))).toBe("NONE");
  });

  it("ignores entries that are not SCHEDULED (already sent or cancelled)", async () => {
    const sent = { list: [{ ...REAL_PAYLOAD.list[0], status: "SENT" }] };
    expect(await checkDuplicate(ok(sent))).toBe("NONE");
  });

  // ここから下が本バグの本体。いずれも「予約なし(NONE)」に潰れると二重予約に直結する。
  it("returns UNKNOWN (never NONE) on a non-200 response", async () => {
    expect(await checkDuplicate({ status: 500, body: "" })).toBe("UNKNOWN");
  });

  it("returns UNKNOWN (never NONE) when the body is not JSON", async () => {
    expect(await checkDuplicate({ status: 200, body: "<html>error</html>" })).toBe("UNKNOWN");
  });

  it("returns UNKNOWN (never NONE) when the response has no list array", async () => {
    expect(await checkDuplicate(ok({ items: [] }))).toBe("UNKNOWN");
  });

  it("returns UNKNOWN (never NONE) when an entry has an unexpected shape", async () => {
    // 一部だけ読めた前提で「一致なし＝予約なし」と結論づけない。
    const broken = { list: [{ scheduledAt: "2026-09-01T08:00:00+09:00", status: "SCHEDULED" }] };
    expect(await checkDuplicate(ok(broken))).toBe("UNKNOWN");
  });

  it("returns UNKNOWN (never NONE) when the request itself fails", async () => {
    expect(await checkDuplicate(new Error("net::ERR_ABORTED"))).toBe("UNKNOWN");
  });
});

describe("OamChatPage.deleteReservation", () => {
  /** 削除操作のクリック経路まで通せる Page スタブ。予約一覧は常に REAL_PAYLOAD（＝消えない）を返す。 */
  function createDeletePage(): Page {
    const clickable = { click: () => Promise.resolve(), waitFor: () => Promise.resolve() };
    const banner = {
      first: () => ({ ...clickable, getByRole: () => clickable }),
      getByRole: () => clickable,
    };
    // page.getByRole は「バナー（filter でしぼる）」と「削除ボタン（そのまま click）」の両方に使われる。
    const filterable = { filter: () => filterable, ...banner, ...clickable };
    return {
      goto: vi.fn().mockResolvedValue(undefined),
      url: () => `https://chat.line.biz/${ACCOUNT}/chat/${ROOM}`,
      locator: () => ({ count: () => Promise.resolve(1) }),
      // ループ内の待機で偽クロックを進め、実時間を待たずにタイムアウトへ到達させる。
      waitForTimeout: vi.fn().mockImplementation((ms: number) => {
        vi.advanceTimersByTime(ms);
        return Promise.resolve();
      }),
      getByRole: () => filterable,
      getByText: () => clickable,
      evaluate: vi.fn().mockResolvedValue({ status: 200, body: JSON.stringify(REAL_PAYLOAD) }),
    } as unknown as Page;
  }

  // 回帰: 削除操作をしても一覧から消えない場合、CANCELLED を報告してはならない。
  // DELETED を返すと、LINE側に予約が残ったまま「取消済み」と記録され誤配信に気づけなくなる。
  it("returns UNKNOWN (never DELETED) when the reservation still appears after the delete action", async () => {
    vi.useFakeTimers();
    try {
      const po = new OamChatPage(createDeletePage(), ACCOUNT);
      await po.openChat("グループ名", ROOM);

      expect(await po.deleteReservation(SEND_AT, "")).toBe("UNKNOWN");
    } finally {
      vi.useRealTimers();
    }
  });
});
