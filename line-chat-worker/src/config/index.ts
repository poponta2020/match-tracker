export interface WorkerConfig {
  appBaseUrl: string;
  serviceToken: string;
  /**
   * OAM チャットのアカウントパス（ログイン後URL `https://chat.line.biz/U<...>` の `U<...>` 部分）。
   * ルームURL `https://chat.line.biz/<accountPath>/chat/<chatRoomId>` の構築に使う。
   * WorkerTask には含まれない per-OA 定数（v1は単一OA）。実DOM調査（タスク7）で構造確定。
   */
  oamAccountPath: string;
  pollIntervalMs: number;
  dryRun: boolean;
  storageStatePath: string;
  artifactDir: string;
  artifactRetentionDays: number;
  requestTimeoutMs: number;
  /**
   * 30日SSO Cookie（`__is_login_sso`）の失効がこの日数以内になったら管理者へ先回り警告する閾値（line-chat-auto-relogin）。
   */
  ssoWarningThresholdDays: number;
  /**
   * 認証壁検出時のクリックスルー自動再ログインを有効にするか（キルスイッチ・既定 true）。
   * false にすると従来どおり壁検出＝即 LINE_AUTH_EXPIRED でフォールバックpushに委ねる。
   */
  autoReloginEnabled: boolean;
}

const DEFAULT_POLL_INTERVAL_MS = 5 * 60 * 1000; // 5分
const DEFAULT_STORAGE_STATE_PATH = "./storage-state.json";
const DEFAULT_ARTIFACT_DIR = "./artifacts";
const DEFAULT_ARTIFACT_RETENTION_DAYS = 14;
const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
const DEFAULT_SSO_WARNING_THRESHOLD_DAYS = 3;

/**
 * 環境変数からワーカー設定を読み込む。
 * 【厳守】トークン等の秘匿値をログ・エラーメッセージに出力しない。
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): WorkerConfig {
  return {
    appBaseUrl: requireEnv(env, "APP_BASE_URL"),
    serviceToken: requireEnv(env, "LINE_CHAT_WORKER_TOKEN"),
    oamAccountPath: requireEnv(env, "LINE_OAM_ACCOUNT_PATH"),
    pollIntervalMs: parsePositiveInt(env.POLL_INTERVAL_MS, DEFAULT_POLL_INTERVAL_MS),
    dryRun: parseBoolean(env.DRY_RUN, false),
    storageStatePath: env.STORAGE_STATE_PATH ?? DEFAULT_STORAGE_STATE_PATH,
    artifactDir: env.ARTIFACT_DIR ?? DEFAULT_ARTIFACT_DIR,
    artifactRetentionDays: parsePositiveInt(env.ARTIFACT_RETENTION_DAYS, DEFAULT_ARTIFACT_RETENTION_DAYS),
    requestTimeoutMs: parsePositiveInt(env.REQUEST_TIMEOUT_MS, DEFAULT_REQUEST_TIMEOUT_MS),
    ssoWarningThresholdDays: parsePositiveInt(
      env.SSO_WARNING_THRESHOLD_DAYS,
      DEFAULT_SSO_WARNING_THRESHOLD_DAYS,
    ),
    autoReloginEnabled: parseBoolean(env.AUTO_RELOGIN_ENABLED, true),
  };
}

function requireEnv(env: NodeJS.ProcessEnv, key: string): string {
  const value = env[key];
  if (!value) {
    throw new Error(`missing required env: ${key}`);
  }
  return value;
}

function parsePositiveInt(value: string | undefined, fallback: number): number {
  if (value === undefined || value === "") {
    return fallback;
  }
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }
  return parsed;
}

function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value === undefined || value === "") {
    return fallback;
  }
  return value.toLowerCase() === "true";
}
