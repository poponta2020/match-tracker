import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AppApiError, createAppApiClient } from "./client.js";
import type { WorkerTask } from "../domain/types.js";

const sampleTask: WorkerTask = {
  id: 1,
  broadcastGroupId: 10,
  sessionId: 100,
  status: "PENDING",
  chatRoomId: "room-1",
  chatRoomName: "北大かるた会",
  scheduledSendAt: "2026-07-18T08:00:00+09:00",
  messageText: "本日の札組です。",
};

describe("createAppApiClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    global.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it("getTasks() sends X-Service-Token and returns the parsed task array", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      new Response(JSON.stringify([sampleTask]), { status: 200 }),
    );

    const client = createAppApiClient({
      appBaseUrl: "https://example.com",
      serviceToken: "secret-token",
      requestTimeoutMs: 5000,
    });

    const tasks = await client.getTasks();

    expect(tasks).toEqual([sampleTask]);
    expect(global.fetch).toHaveBeenCalledTimes(1);
    const [url, init] = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe("https://example.com/api/line-chat-worker/tasks");
    expect(init.method).toBe("GET");
    const headers = init.headers as Record<string, string>;
    expect(headers["X-Service-Token"]).toBe("secret-token");
  });

  it("reportResult() POSTs the body with X-Service-Token and returns the updated task", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      new Response(JSON.stringify({ ...sampleTask, status: "RESERVING" }), { status: 200 }),
    );

    const client = createAppApiClient({
      appBaseUrl: "https://example.com",
      serviceToken: "secret-token",
      requestTimeoutMs: 5000,
    });

    const result = await client.reportResult(1, { status: "RESERVING" });

    expect(result.status).toBe("RESERVING");
    const [url, init] = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe("https://example.com/api/line-chat-worker/1/result");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ status: "RESERVING" });
    const headers = init.headers as Record<string, string>;
    expect(headers["X-Service-Token"]).toBe("secret-token");
  });

  it("throws AppApiError with the http status on a non-ok response (e.g. 409 invalid transition)", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(new Response("conflict", { status: 409 }));

    const client = createAppApiClient({
      appBaseUrl: "https://example.com",
      serviceToken: "secret-token",
      requestTimeoutMs: 5000,
    });

    await expect(client.reportResult(1, { status: "RESERVED" })).rejects.toMatchObject({
      name: "AppApiError",
      httpStatus: 409,
    });
  });

  it("throws AppApiError with 401 when the token is missing/rejected", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(new Response("unauthorized", { status: 401 }));

    const client = createAppApiClient({
      appBaseUrl: "https://example.com",
      serviceToken: "",
      requestTimeoutMs: 5000,
    });

    await expect(client.getTasks()).rejects.toBeInstanceOf(AppApiError);
  });

  it("wraps network-level failures in AppApiError without leaking raw error internals as a status code", async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValue(new TypeError("network down"));

    const client = createAppApiClient({
      appBaseUrl: "https://example.com",
      serviceToken: "secret-token",
      requestTimeoutMs: 5000,
    });

    await expect(client.getTasks()).rejects.toBeInstanceOf(AppApiError);
  });
});
