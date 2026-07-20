import { describe, expect, it, vi } from "vitest";
import { maybeWarnSsoExpiry, runCycle } from "./index.js";
import type { SsoWarnState } from "./index.js";
import type { AppApiClient } from "./appApi/client.js";
import type { ChatPage } from "./line/pages/ChatPage.js";
import type { AuthState, ReloginResult } from "./detect/authState.js";
import type { WorkerTask } from "./domain/types.js";

const pendingTask = (id: number): WorkerTask => ({
  id,
  broadcastGroupId: 1,
  sessionId: id,
  status: "PENDING",
  chatRoomId: "room-abc",
  chatRoomName: "北大かるた会 全体グループ",
  scheduledSendAt: "2026-07-18T08:00:00+09:00",
  messageText: "本日の札組をお知らせします。",
});

function createMockApi(tasks: WorkerTask[]): AppApiClient {
  return {
    getTasks: vi.fn().mockResolvedValue(tasks),
    reportResult: vi.fn().mockResolvedValue(tasks[0]),
    postSessionWarning: vi.fn().mockResolvedValue(undefined),
  };
}

function createMockPo(overrides: Partial<Record<keyof ChatPage, unknown>> = {}): ChatPage {
  const defaults: ChatPage = {
    openChat: vi.fn().mockResolvedValue(undefined),
    verifyTargetChat: vi.fn().mockResolvedValue(true),
    detectAuthWall: vi.fn().mockResolvedValue("OK" as AuthState),
    relogin: vi.fn().mockResolvedValue("SUCCEEDED" as ReloginResult),
    findDuplicateReservation: vi.fn().mockResolvedValue(false),
    inputMessage: vi.fn().mockResolvedValue(undefined),
    setScheduledDateTime: vi.fn().mockResolvedValue(undefined),
    confirmReservation: vi.fn().mockResolvedValue(undefined),
    verifyScheduledEntry: vi.fn().mockResolvedValue("MATCHED"),
    deleteReservation: vi.fn().mockResolvedValue("DELETED"),
    screenshot: vi.fn().mockResolvedValue(undefined),
  };
  return { ...defaults, ...overrides } as ChatPage;
}

const cancelTask = (id: number): WorkerTask => ({
  ...pendingTask(id),
  status: "CANCEL_PENDING",
});

const CYCLE_OPTS = { dryRun: false, artifactDir: "/tmp/artifacts", autoReloginEnabled: true };

describe("runCycle", () => {
  it("claims a PENDING task with RESERVING before running the reserve usecase, then reports the final outcome", async () => {
    const task = pendingTask(1);
    const api = createMockApi([task]);
    const po = createMockPo();

    await runCycle(api, po, CYCLE_OPTS);

    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    expect(reportResult).toHaveBeenNthCalledWith(1, 1, { status: "RESERVING" });
    expect(reportResult).toHaveBeenNthCalledWith(2, 1, {
      status: "RESERVED",
      errorCode: undefined,
      errorMessage: undefined,
    });
    // AC-9: 壁が無い通常時は再ログインを試みない（挙動不変）。
    expect(po.relogin as ReturnType<typeof vi.fn>).not.toHaveBeenCalled();
  });

  it("aborts the cycle on an auth wall with auto-relogin disabled (AC-7 legacy / kill switch): reports RESERVING then FAILED, never touches the next task, never reloging", async () => {
    const task1 = pendingTask(1);
    const task2 = pendingTask(2);
    const api = createMockApi([task1, task2]);
    const po = createMockPo({
      detectAuthWall: vi.fn().mockResolvedValue("LOGIN_REQUIRED" as AuthState),
    });

    await runCycle(api, po, { ...CYCLE_OPTS, autoReloginEnabled: false });

    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    // task1: claim (RESERVING) then FAILED/LINE_AUTH_EXPIRED
    expect(reportResult).toHaveBeenNthCalledWith(1, 1, { status: "RESERVING" });
    expect(reportResult).toHaveBeenNthCalledWith(2, 1, {
      status: "FAILED",
      errorCode: "LINE_AUTH_EXPIRED",
      errorMessage: expect.any(String),
    });
    // task2 must never be claimed or reported — the cycle stopped after task1's abort.
    expect(reportResult).toHaveBeenCalledTimes(2);
    expect(reportResult).not.toHaveBeenCalledWith(2, expect.anything());
    expect(po.relogin as ReturnType<typeof vi.fn>).not.toHaveBeenCalled();
  });

  it("does not abort the cycle when a task resolves without abortCycle (multiple PENDING tasks all processed)", async () => {
    const task1 = pendingTask(1);
    const task2 = pendingTask(2);
    const api = createMockApi([task1, task2]);
    const po = createMockPo();

    await runCycle(api, po, CYCLE_OPTS);

    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    // 2 tasks x (claim + final) = 4 calls
    expect(reportResult).toHaveBeenCalledTimes(4);
    expect(reportResult).toHaveBeenNthCalledWith(3, 2, { status: "RESERVING" });
  });
});

describe("runCycle: クリックスルー再ログイン統合", () => {
  it("AC-1/2/13: 認証壁→relogin SUCCEEDED→当該タスクを1回リトライして RESERVED（FAILEDを報告しない）", async () => {
    const task = pendingTask(1);
    const api = createMockApi([task]);
    // run1 の authBeforeOpen で壁、relogin 後の run2 は authBeforeOpen/authAfterOpen とも OK。
    const detectAuthWall = vi
      .fn()
      .mockResolvedValueOnce("LOGIN_REQUIRED" as AuthState)
      .mockResolvedValue("OK" as AuthState);
    const relogin = vi.fn().mockResolvedValue("SUCCEEDED" as ReloginResult);
    const po = createMockPo({ detectAuthWall, relogin });

    await runCycle(api, po, CYCLE_OPTS);

    expect(relogin).toHaveBeenCalledTimes(1);
    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    // claim は1回だけ（リトライで RESERVING を再送しない）。最終は RESERVED。
    expect(reportResult).toHaveBeenNthCalledWith(1, 1, { status: "RESERVING" });
    expect(reportResult).toHaveBeenNthCalledWith(2, 1, {
      status: "RESERVED",
      errorCode: undefined,
      errorMessage: undefined,
    });
    expect(reportResult).toHaveBeenCalledTimes(2);
    // FAILED は一度も報告されない（フォールバックpushを誘発しない）。
    expect(reportResult).not.toHaveBeenCalledWith(1, expect.objectContaining({ status: "FAILED" }));
  });

  it("AC-4: 認証壁→relogin SSO_EXPIRED→既存の FAILED/LINE_AUTH_EXPIRED＋abortCycle を維持する", async () => {
    const task1 = pendingTask(1);
    const task2 = pendingTask(2);
    const api = createMockApi([task1, task2]);
    const detectAuthWall = vi.fn().mockResolvedValue("LOGIN_REQUIRED" as AuthState);
    const relogin = vi.fn().mockResolvedValue("SSO_EXPIRED" as ReloginResult);
    const po = createMockPo({ detectAuthWall, relogin });

    await runCycle(api, po, CYCLE_OPTS);

    expect(relogin).toHaveBeenCalledTimes(1);
    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    expect(reportResult).toHaveBeenNthCalledWith(2, 1, {
      status: "FAILED",
      errorCode: "LINE_AUTH_EXPIRED",
      errorMessage: expect.any(String),
    });
    // abortCycle を維持: task2 は処理されない。
    expect(reportResult).toHaveBeenCalledTimes(2);
    expect(reportResult).not.toHaveBeenCalledWith(2, expect.anything());
  });

  it("AC-8: relogin SUCCEEDED でもリトライが再び壁なら FAILED を報告し、再ログインは当該タスクで1回だけ", async () => {
    const task = pendingTask(1);
    const api = createMockApi([task]);
    // run1・run2（リトライ）とも壁のまま。
    const detectAuthWall = vi.fn().mockResolvedValue("LOGIN_REQUIRED" as AuthState);
    const relogin = vi.fn().mockResolvedValue("SUCCEEDED" as ReloginResult);
    const po = createMockPo({ detectAuthWall, relogin });

    await runCycle(api, po, CYCLE_OPTS);

    // リトライは1回まで＝relogin は1回だけ（無限ループしない）。
    expect(relogin).toHaveBeenCalledTimes(1);
    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    expect(reportResult).toHaveBeenNthCalledWith(2, 1, {
      status: "FAILED",
      errorCode: "LINE_AUTH_EXPIRED",
      errorMessage: expect.any(String),
    });
  });

  it("AC-8: 1サイクルで再ログインは最大1回（先行タスクで使い切ったら後続の壁では再ログインしない）", async () => {
    const task1 = pendingTask(1);
    const task2 = pendingTask(2);
    const api = createMockApi([task1, task2]);
    // task1: run1壁→relogin→run2 OK,OK で成功。task2: run1壁（ここでは再ログインしない）。
    const detectAuthWall = vi
      .fn()
      .mockResolvedValueOnce("LOGIN_REQUIRED" as AuthState) // task1 run1
      .mockResolvedValueOnce("OK" as AuthState) // task1 run2 before
      .mockResolvedValueOnce("OK" as AuthState) // task1 run2 after
      .mockResolvedValue("LOGIN_REQUIRED" as AuthState); // task2 run1
    const relogin = vi.fn().mockResolvedValue("SUCCEEDED" as ReloginResult);
    const po = createMockPo({ detectAuthWall, relogin });

    await runCycle(api, po, CYCLE_OPTS);

    // 再ログインはサイクル通算で1回だけ。
    expect(relogin).toHaveBeenCalledTimes(1);
    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    // task1 は RESERVED、task2 は再ログインせず FAILED＋abort。
    expect(reportResult).toHaveBeenCalledWith(1, expect.objectContaining({ status: "RESERVED" }));
    expect(reportResult).toHaveBeenCalledWith(2, expect.objectContaining({ status: "FAILED" }));
  });

  it("CANCEL_PENDING でも認証壁→relogin SUCCEEDED→リトライで CANCELLED を報告する", async () => {
    const task = cancelTask(1);
    const api = createMockApi([task]);
    const detectAuthWall = vi
      .fn()
      .mockResolvedValueOnce("LOGIN_REQUIRED" as AuthState)
      .mockResolvedValue("OK" as AuthState);
    const relogin = vi.fn().mockResolvedValue("SUCCEEDED" as ReloginResult);
    const po = createMockPo({ detectAuthWall, relogin });

    await runCycle(api, po, CYCLE_OPTS);

    expect(relogin).toHaveBeenCalledTimes(1);
    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    // CANCEL_PENDING は claim しない＝最終報告のみ。
    expect(reportResult).toHaveBeenCalledTimes(1);
    expect(reportResult).toHaveBeenCalledWith(1, expect.objectContaining({ status: "CANCELLED" }));
  });
});

describe("maybeWarnSsoExpiry", () => {
  const DAY = 24 * 60 * 60 * 1000;
  const NOW = Date.UTC(2026, 7, 10, 3, 0, 0); // 2026-08-10 12:00 JST

  function mockApi(): AppApiClient {
    return {
      getTasks: vi.fn().mockResolvedValue([]),
      reportResult: vi.fn().mockResolvedValue(undefined),
      postSessionWarning: vi.fn().mockResolvedValue(undefined),
    };
  }

  it("AC-5/7: 失効が閾値以内なら残り日数つきで警告する（渡した実期限に従う）", async () => {
    const api = mockApi();
    const state: SsoWarnState = { lastWarnedJstDate: null };

    await maybeWarnSsoExpiry(api, {
      ssoExpiryMs: NOW + 2 * DAY,
      nowMs: NOW,
      thresholdDays: 3,
      state,
    });

    expect(api.postSessionWarning as ReturnType<typeof vi.fn>).toHaveBeenCalledTimes(1);
    expect(api.postSessionWarning as ReturnType<typeof vi.fn>).toHaveBeenCalledWith(2);
  });

  it("AC-7: 失効が閾値より先なら警告しない（ハードコードでなく渡した期限で判定）", async () => {
    const api = mockApi();
    const state: SsoWarnState = { lastWarnedJstDate: null };

    await maybeWarnSsoExpiry(api, {
      ssoExpiryMs: NOW + 10 * DAY,
      nowMs: NOW,
      thresholdDays: 3,
      state,
    });

    expect(api.postSessionWarning as ReturnType<typeof vi.fn>).not.toHaveBeenCalled();
  });

  it("AC-6: 同一JST日には1回しか警告しない（多重送信を間引く）", async () => {
    const api = mockApi();
    const state: SsoWarnState = { lastWarnedJstDate: null };
    const params = { ssoExpiryMs: NOW + 1 * DAY, nowMs: NOW, thresholdDays: 3, state };

    await maybeWarnSsoExpiry(api, params);
    await maybeWarnSsoExpiry(api, params); // 同日2回目
    await maybeWarnSsoExpiry(api, { ...params, nowMs: NOW + 5 * 60 * 1000 }); // 5分後（同日）

    expect(api.postSessionWarning as ReturnType<typeof vi.fn>).toHaveBeenCalledTimes(1);
  });

  it("AC-6: JST日付が変われば翌日は再度警告する", async () => {
    const api = mockApi();
    const state: SsoWarnState = { lastWarnedJstDate: null };

    await maybeWarnSsoExpiry(api, { ssoExpiryMs: NOW + 2 * DAY, nowMs: NOW, thresholdDays: 3, state });
    await maybeWarnSsoExpiry(api, {
      ssoExpiryMs: NOW + 2 * DAY,
      nowMs: NOW + 1 * DAY,
      thresholdDays: 3,
      state,
    });

    expect(api.postSessionWarning as ReturnType<typeof vi.fn>).toHaveBeenCalledTimes(2);
  });

  it("期限不明（Cookie不在/session cookie＝null）なら警告しない（偽アラート回避）", async () => {
    const api = mockApi();
    const state: SsoWarnState = { lastWarnedJstDate: null };

    await maybeWarnSsoExpiry(api, { ssoExpiryMs: null, nowMs: NOW, thresholdDays: 3, state });

    expect(api.postSessionWarning as ReturnType<typeof vi.fn>).not.toHaveBeenCalled();
  });
});
