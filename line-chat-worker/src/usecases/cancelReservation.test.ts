import { describe, expect, it, vi } from "vitest";
import { cancelReservation } from "./cancelReservation.js";
import type { ChatPage, DeleteReservationResult } from "../line/pages/ChatPage.js";
import type { AuthState } from "../detect/authState.js";
import type { WorkerTask } from "../domain/types.js";

const baseTask: WorkerTask = {
  id: 99,
  broadcastGroupId: 1,
  sessionId: 2,
  status: "CANCEL_PENDING",
  chatRoomId: "room-abc",
  chatRoomName: "北大かるた会 全体グループ",
  scheduledSendAt: "2026-07-18T08:00:00+09:00",
  messageText: "本日の札組をお知らせします。",
};

function createMockPo(overrides: Partial<Record<keyof ChatPage, unknown>> = {}): ChatPage {
  const defaults: ChatPage = {
    openChat: vi.fn().mockResolvedValue(undefined),
    verifyTargetChat: vi.fn().mockResolvedValue(true),
    detectAuthWall: vi.fn().mockResolvedValue("OK" as AuthState),
    findDuplicateReservation: vi.fn().mockResolvedValue(false),
    inputMessage: vi.fn().mockResolvedValue(undefined),
    setScheduledDateTime: vi.fn().mockResolvedValue(undefined),
    confirmReservation: vi.fn().mockResolvedValue(undefined),
    verifyScheduledEntry: vi.fn().mockResolvedValue("MATCHED"),
    deleteReservation: vi.fn().mockResolvedValue("DELETED" as DeleteReservationResult),
    screenshot: vi.fn().mockResolvedValue(undefined),
  };
  return { ...defaults, ...overrides } as ChatPage;
}

describe("cancelReservation", () => {
  it("reports CANCELLED when the reservation is found and deleted", async () => {
    const po = createMockPo();

    const outcome = await cancelReservation(po, baseTask);

    expect(outcome).toEqual({ report: true, status: "CANCELLED" });
    expect(po.deleteReservation).toHaveBeenCalledWith(baseTask.scheduledSendAt, expect.any(String));
  });

  it("reports MANUAL_REVIEW_REQUIRED/OLD_RESERVATION_NOT_FOUND when the old reservation cannot be located", async () => {
    const po = createMockPo({ deleteReservation: vi.fn().mockResolvedValue("NOT_FOUND" as DeleteReservationResult) });

    const outcome = await cancelReservation(po, baseTask);

    expect(outcome).toMatchObject({
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "OLD_RESERVATION_NOT_FOUND",
    });
  });

  it("does not report a result and signals abortCycle when an auth wall is detected (CANCEL_PENDING has no valid FAILED transition)", async () => {
    const po = createMockPo({ detectAuthWall: vi.fn().mockResolvedValue("LOGIN_REQUIRED" as AuthState) });

    const outcome = await cancelReservation(po, baseTask);

    expect(outcome).toEqual({ report: false, abortCycle: true, reason: expect.any(String) });
    expect(po.deleteReservation).not.toHaveBeenCalled();
  });
});
