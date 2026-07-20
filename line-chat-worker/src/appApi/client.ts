import type { ResultReportBody, WorkerTask } from "../domain/types.js";

export interface AppApiClientConfig {
  appBaseUrl: string;
  serviceToken: string;
  requestTimeoutMs: number;
}

export class AppApiError extends Error {
  readonly httpStatus?: number;

  constructor(message: string, httpStatus?: number) {
    super(message);
    this.name = "AppApiError";
    this.httpStatus = httpStatus;
  }
}

export interface AppApiClient {
  getTasks(): Promise<WorkerTask[]>;
  reportResult(id: number, body: ResultReportBody): Promise<WorkerTask>;
  /**
   * 30日SSO Cookie の失効が近い旨の先回り警告をアプリへ送る（line-chat-auto-relogin タスク2/3）。
   * アプリ側は有効な配信グループの各団体の管理者へリレーする（本エンドポイントは空ボディ200を返す）。
   */
  postSessionWarning(daysRemaining: number): Promise<void>;
}

/**
 * アプリ側ワーカーAPI（/api/line-chat-worker/**）のクライアント。
 * 全リクエストに X-Service-Token を付与する。
 * 【厳守】トークン・レスポンス本文をログに出力しない。
 */
export function createAppApiClient(config: AppApiClientConfig): AppApiClient {
  async function request<T>(path: string, init: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), config.requestTimeoutMs);

    try {
      const response = await fetch(`${config.appBaseUrl}${path}`, {
        ...init,
        headers: {
          "Content-Type": "application/json",
          "X-Service-Token": config.serviceToken,
          ...(init.headers ?? {}),
        },
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new AppApiError(`line-chat-worker API request failed: ${response.status} ${path}`, response.status);
      }

      // 空ボディ200（session-warning 等の void エンドポイント）を許容する。
      const text = await response.text();
      return (text ? JSON.parse(text) : undefined) as T;
    } catch (err) {
      if (err instanceof AppApiError) {
        throw err;
      }
      if (err instanceof Error && err.name === "AbortError") {
        throw new AppApiError(`line-chat-worker API request timed out: ${path}`);
      }
      throw new AppApiError(
        `line-chat-worker API request errored: ${path} (${err instanceof Error ? err.message : String(err)})`,
      );
    } finally {
      clearTimeout(timeout);
    }
  }

  return {
    getTasks(): Promise<WorkerTask[]> {
      return request<WorkerTask[]>("/api/line-chat-worker/tasks", { method: "GET" });
    },

    reportResult(id: number, body: ResultReportBody): Promise<WorkerTask> {
      return request<WorkerTask>(`/api/line-chat-worker/${id}/result`, {
        method: "POST",
        body: JSON.stringify(body),
      });
    },

    async postSessionWarning(daysRemaining: number): Promise<void> {
      await request<void>("/api/line-chat-worker/session-warning", {
        method: "POST",
        body: JSON.stringify({ daysRemaining }),
      });
    },
  };
}
