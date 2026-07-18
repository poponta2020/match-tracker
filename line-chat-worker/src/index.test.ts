import { describe, expect, it, vi } from "vitest";
import { runCycle } from "./index.js";
import type { AppApiClient } from "./appApi/client.js";
import type { ChatPage } from "./line/pages/ChatPage.js";
import type { AuthState } from "./detect/authState.js";
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
  };
}

function createMockPo(overrides: Partial<Record<keyof ChatPage, unknown>> = {}): ChatPage {
  const defaults: ChatPage = {
    openChat: vi.fn().mockResolvedValue(undefined),
    verifyTargetChat: vi.fn().mockResolvedValue(true),
    detectAuthWall: vi.fn().mockResolvedValue("OK" as AuthState),
    findDuplicateReservation: vi.fn().mockResolvedValue("NONE"),
    inputMessage: vi.fn().mockResolvedValue(undefined),
    setScheduledDateTime: vi.fn().mockResolvedValue(undefined),
    confirmReservation: vi.fn().mockResolvedValue(undefined),
    verifyScheduledEntry: vi.fn().mockResolvedValue("MATCHED"),
    deleteReservation: vi.fn().mockResolvedValue("DELETED"),
    screenshot: vi.fn().mockResolvedValue(undefined),
  };
  return { ...defaults, ...overrides } as ChatPage;
}

describe("runCycle", () => {
  it("claims a PENDING task with RESERVING before running the reserve usecase, then reports the final outcome", async () => {
    const task = pendingTask(1);
    const api = createMockApi([task]);
    const po = createMockPo();

    await runCycle(api, po, { dryRun: false, artifactDir: "/tmp/artifacts" });

    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    expect(reportResult).toHaveBeenNthCalledWith(1, 1, { status: "RESERVING" });
    expect(reportResult).toHaveBeenNthCalledWith(2, 1, {
      status: "RESERVED",
      errorCode: undefined,
      errorMessage: undefined,
    });
  });

  it("aborts the cycle on an auth wall (AC-7): reports RESERVING then FAILED for the failing task, and never touches the next task", async () => {
    const task1 = pendingTask(1);
    const task2 = pendingTask(2);
    const api = createMockApi([task1, task2]);
    const po = createMockPo({
      detectAuthWall: vi.fn().mockResolvedValue("LOGIN_REQUIRED" as AuthState),
    });

    await runCycle(api, po, { dryRun: false, artifactDir: "/tmp/artifacts" });

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
  });

  it("does not abort the cycle when a task resolves without abortCycle (multiple PENDING tasks all processed)", async () => {
    const task1 = pendingTask(1);
    const task2 = pendingTask(2);
    const api = createMockApi([task1, task2]);
    const po = createMockPo();

    await runCycle(api, po, { dryRun: false, artifactDir: "/tmp/artifacts" });

    const reportResult = api.reportResult as ReturnType<typeof vi.fn>;
    // 2 tasks x (claim + final) = 4 calls
    expect(reportResult).toHaveBeenCalledTimes(4);
    expect(reportResult).toHaveBeenNthCalledWith(3, 2, { status: "RESERVING" });
  });
});
