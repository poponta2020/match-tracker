import { describe, expect, it, vi } from "vitest";
import { reserveMessage } from "./reserveMessage.js";
import type { ChatPage, DeleteReservationResult, ScheduledEntryCheck } from "../line/pages/ChatPage.js";
import type { AuthState } from "../detect/authState.js";
import type { WorkerTask } from "../domain/types.js";

const baseTask: WorkerTask = {
  id: 42,
  broadcastGroupId: 1,
  sessionId: 2,
  status: "PENDING",
  chatRoomId: "room-abc",
  chatRoomName: "北大かるた会 全体グループ",
  scheduledSendAt: "2026-07-18T08:00:00+09:00",
  messageText: "本日の札組をお知らせします。第1試合: ...",
};

const opts = { dryRun: false, artifactDir: "/tmp/artifacts" };

function createMockPo(overrides: Partial<Record<keyof ChatPage, unknown>> = {}): ChatPage {
  const defaults: ChatPage = {
    openChat: vi.fn().mockResolvedValue(undefined),
    verifyTargetChat: vi.fn().mockResolvedValue(true),
    detectAuthWall: vi.fn().mockResolvedValue("OK" as AuthState),
    findDuplicateReservation: vi.fn().mockResolvedValue(false),
    inputMessage: vi.fn().mockResolvedValue(undefined),
    setScheduledDateTime: vi.fn().mockResolvedValue(undefined),
    confirmReservation: vi.fn().mockResolvedValue(undefined),
    verifyScheduledEntry: vi.fn().mockResolvedValue("MATCHED" as ScheduledEntryCheck),
    deleteReservation: vi.fn().mockResolvedValue("DELETED" as DeleteReservationResult),
    screenshot: vi.fn().mockResolvedValue(undefined),
  };
  return { ...defaults, ...overrides } as ChatPage;
}

describe("reserveMessage", () => {
  it("reports RESERVED only after 送信予定 verification matches", async () => {
    const po = createMockPo();

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toEqual({ report: true, status: "RESERVED" });
    expect(po.confirmReservation).toHaveBeenCalledTimes(1);
    expect(po.verifyScheduledEntry).toHaveBeenCalledTimes(1);
  });

  it("returns MANUAL_REVIEW_REQUIRED/CONFIRM_RESULT_UNKNOWN on post-confirm timeout, without retrying", async () => {
    const po = createMockPo({
      verifyScheduledEntry: vi.fn().mockResolvedValue("TIMEOUT" as ScheduledEntryCheck),
    });

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toMatchObject({
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "CONFIRM_RESULT_UNKNOWN",
    });
    expect(po.confirmReservation).toHaveBeenCalledTimes(1);
    expect(po.verifyScheduledEntry).toHaveBeenCalledTimes(1);
  });

  it("returns MANUAL_REVIEW_REQUIRED/CONFIRM_RESULT_UNKNOWN when the confirmed entry mismatches", async () => {
    const po = createMockPo({
      verifyScheduledEntry: vi.fn().mockResolvedValue("MISMATCHED" as ScheduledEntryCheck),
    });

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toMatchObject({
      report: true,
      status: "MANUAL_REVIEW_REQUIRED",
      errorCode: "CONFIRM_RESULT_UNKNOWN",
    });
  });

  it("aborts immediately with FAILED/LINE_AUTH_EXPIRED when a login wall is detected before opening the chat", async () => {
    const po = createMockPo({
      detectAuthWall: vi.fn().mockResolvedValue("LOGIN_REQUIRED" as AuthState),
    });

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toEqual({
      report: true,
      status: "FAILED",
      errorCode: "LINE_AUTH_EXPIRED",
      errorMessage: expect.any(String),
      abortCycle: true,
    });
    expect(po.openChat).not.toHaveBeenCalled();
  });

  it("aborts with FAILED/LINE_AUTH_EXPIRED when CAPTCHA appears after opening the chat", async () => {
    const detectAuthWall = vi
      .fn()
      .mockResolvedValueOnce("OK" as AuthState)
      .mockResolvedValueOnce("CAPTCHA" as AuthState);
    const po = createMockPo({ detectAuthWall });

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toMatchObject({ status: "FAILED", errorCode: "LINE_AUTH_EXPIRED", abortCycle: true });
    expect(po.verifyTargetChat).not.toHaveBeenCalled();
  });

  it("returns MANUAL_REVIEW_REQUIRED/TARGET_CHAT_MISMATCH when the opened chat does not match", async () => {
    const po = createMockPo({ verifyTargetChat: vi.fn().mockResolvedValue(false) });

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toMatchObject({ status: "MANUAL_REVIEW_REQUIRED", errorCode: "TARGET_CHAT_MISMATCH" });
    expect(po.findDuplicateReservation).not.toHaveBeenCalled();
  });

  it("returns MANUAL_REVIEW_REQUIRED/DUPLICATE_RESERVATION_FOUND when a duplicate reservation exists", async () => {
    const po = createMockPo({ findDuplicateReservation: vi.fn().mockResolvedValue(true) });

    const outcome = await reserveMessage(po, baseTask, opts);

    expect(outcome).toMatchObject({ status: "MANUAL_REVIEW_REQUIRED", errorCode: "DUPLICATE_RESERVATION_FOUND" });
    expect(po.inputMessage).not.toHaveBeenCalled();
  });

  it("dry-run: does not call confirmReservation, saves a screenshot, and reports DRY_RUN_SUCCEEDED", async () => {
    const po = createMockPo();

    const outcome = await reserveMessage(po, baseTask, { dryRun: true, artifactDir: "/tmp/artifacts" });

    expect(outcome).toEqual({ report: true, status: "DRY_RUN_SUCCEEDED" });
    expect(po.confirmReservation).not.toHaveBeenCalled();
    expect(po.verifyScheduledEntry).not.toHaveBeenCalled();
    expect(po.screenshot).toHaveBeenCalledTimes(1);
    expect(po.inputMessage).toHaveBeenCalledWith(baseTask.messageText);
    expect(po.setScheduledDateTime).toHaveBeenCalledWith(baseTask.scheduledSendAt);
  });
});
