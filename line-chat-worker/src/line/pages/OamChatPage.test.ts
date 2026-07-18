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

  // 対象日時に SCHEDULED 以外のエントリがある場合、その状態値が有効な予約を表すのか
  // 判断できない。「SCHEDULED でないから予約なし」と決めつけると二重予約になりうる。
  it("returns UNKNOWN when a non-SCHEDULED entry occupies the target datetime", async () => {
    const sent = { list: [{ ...REAL_PAYLOAD.list[0], status: "SENT" }] };
    expect(await checkDuplicate(ok(sent))).toBe("UNKNOWN");
  });

  // 一方、別日時の未知状態エントリは対象の判定に無関係。ここまで UNKNOWN にすると、
  // 送信済みエントリが一覧に残る仕様だった場合に以後の予約が永久に作れなくなる。
  it("returns NONE when a non-SCHEDULED entry exists only at a different datetime", async () => {
    const otherDay = {
      list: [{ ...REAL_PAYLOAD.list[0], status: "SENT", scheduledAt: 1788217200000 - 86_400_000 }],
    };
    expect(await checkDuplicate(ok(otherDay))).toBe("NONE");
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

  // scheduledAt が epoch ミリ秒でない値を素通しすると 1970年の予約として解釈され、
  // 「日時が一致しない＝予約なし」に潰れて二重予約になる。単位違いは UNKNOWN に落とす。
  it("returns UNKNOWN (never NONE) when scheduledAt looks like epoch seconds, not milliseconds", async () => {
    const seconds = { list: [{ ...REAL_PAYLOAD.list[0], scheduledAt: 1788217200 }] };
    expect(await checkDuplicate(ok(seconds))).toBe("UNKNOWN");
  });

  it("returns UNKNOWN (never NONE) when scheduledAt is out of the representable range", async () => {
    // 範囲外の値をそのまま new Date(...).toISOString() に渡すと RangeError で
    // サイクル全体が落ち、タスクが RESERVING のまま滞留する。
    const huge = { list: [{ ...REAL_PAYLOAD.list[0], scheduledAt: 1e20 }] };
    expect(await checkDuplicate(ok(huge))).toBe("UNKNOWN");
  });

  it("returns UNKNOWN (never NONE) when scheduledAt is not an integer", async () => {
    const fractional = { list: [{ ...REAL_PAYLOAD.list[0], scheduledAt: 1788217200000.5 }] };
    expect(await checkDuplicate(ok(fractional))).toBe("UNKNOWN");
  });

  // page.evaluate にも browser の fetch にも既定のタイムアウトが無いため、応答を返さない
  // サーバーでは await が戻らずポーリングの期限判定に到達できない（ワーカーが無期限に停止する）。
  // 「応答しない fetch が期限到来で確実に中断されること」を実際に abort させて確かめる。
  it("aborts the scheduled-messages request when the timeout elapses", async () => {
    const page = createPage(ok({ list: [] }));
    const po = new OamChatPage(page, ACCOUNT);
    await po.openChat("グループ名", ROOM);
    await po.findDuplicateReservation(SEND_AT, "");

    // evaluate に渡されたページ内関数を取り出し、応答しない fetch のもとで実行する。
    const [pageFn, arg] = (page.evaluate as unknown as { mock: { calls: unknown[][] } }).mock.calls[0] as [
      (a: { path: string; timeoutMs: number }) => Promise<unknown>,
      { path: string; timeoutMs: number },
    ];
    expect(arg.timeoutMs).toBeGreaterThan(0);

    let seenSignal: AbortSignal | undefined;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = ((_url: string, init: RequestInit) => {
      const signal = init.signal as AbortSignal;
      seenSignal = signal;
      // 決して解決しない＝サーバーが応答を完了しない状況。abort でのみ終わる。
      return new Promise((_resolve, reject) => {
        signal.addEventListener("abort", () => reject(new Error("AbortError")));
      });
    }) as typeof fetch;

    vi.useFakeTimers();
    try {
      const pending = pageFn(arg);
      const rejects = expect(pending).rejects.toThrow("AbortError");
      // 期限までタイマーを進めると abort が発火し、await が有限時間で終わる。
      await vi.advanceTimersByTimeAsync(arg.timeoutMs);
      await rejects;
      expect(seenSignal?.aborted).toBe(true);
    } finally {
      vi.useRealTimers();
      globalThis.fetch = originalFetch;
    }
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
