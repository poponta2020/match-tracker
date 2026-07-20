import { existsSync } from "node:fs";
import { chromium } from "playwright";
import type { BrowserContext } from "playwright";
import { loadConfig } from "./config/index.js";
import { createAppApiClient } from "./appApi/client.js";
import { OamChatPage } from "./line/pages/OamChatPage.js";
import { reserveMessage } from "./usecases/reserveMessage.js";
import { cancelReservation } from "./usecases/cancelReservation.js";
import type { AppApiClient } from "./appApi/client.js";
import type { WorkerTask } from "./domain/types.js";
import type { ChatPage } from "./line/pages/ChatPage.js";
import type { UsecaseOutcome } from "./usecases/types.js";

/** 1サイクルの実行オプション。 */
export interface CycleOptions {
  /** true の場合、確定ボタンを押さずスクリーンショットのみ保存する。 */
  dryRun: boolean;
  /** dry-run 時のスクリーンショット保存先ディレクトリ。 */
  artifactDir: string;
  /** 認証壁時のクリックスルー自動再ログインを有効にするか（未指定＝有効）。 */
  autoReloginEnabled?: boolean;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const SSO_COOKIE_NAME = "__is_login_sso";

/**
 * 1サイクル分の処理: タスク取得→直列処理。
 * 認証の壁を検出したタスクがあれば、以降のタスクを処理せず当サイクルを打ち切る（AC-7）。
 *
 * 認証壁を検出したサイクルでは、同一 context 内のクリックスルー再ログインを最大1回試み、
 * 成功したら当該タスクを1回だけリトライする（line-chat-auto-relogin・AC-1/2/8/13）。
 */
export async function runCycle(
  api: AppApiClient,
  po: ChatPage,
  opts: CycleOptions,
): Promise<void> {
  const tasks = await api.getTasks();
  // サイクルスコープの再ログイン状態（1サイクル最大1回・AC-8）。
  const cycleState = { reloginUsed: false };

  for (const task of tasks) {
    const abort = await processTask(api, po, task, opts, cycleState);
    if (abort) {
      return;
    }
  }
}

/** @returns true の場合、当サイクルを中断する（呼び出し元でループを打ち切る）。 */
async function processTask(
  api: AppApiClient,
  po: ChatPage,
  task: WorkerTask,
  opts: CycleOptions,
  cycleState: { reloginUsed: boolean },
): Promise<boolean> {
  if (task.status === "PENDING") {
    // claim: 処理開始を宣言する（リトライ時も RESERVING の再送はしない＝claimは1回だけ）。
    await api.reportResult(task.id, { status: "RESERVING" });

    const outcome = await runWithRelogin(po, opts, cycleState, () => reserveMessage(po, task, opts));
    if (outcome.report) {
      await api.reportResult(task.id, {
        status: outcome.status,
        errorCode: outcome.errorCode,
        errorMessage: outcome.errorMessage,
      });
    }
    return Boolean(outcome.abortCycle);
  }

  if (task.status === "CANCEL_PENDING") {
    const outcome = await runWithRelogin(po, opts, cycleState, () => cancelReservation(po, task));
    if (outcome.report) {
      await api.reportResult(task.id, {
        status: outcome.status,
        errorCode: outcome.errorCode,
        errorMessage: outcome.errorMessage,
      });
    }
    return Boolean(outcome.abortCycle);
  }

  return false;
}

/** usecase の結果が認証失効（LINE_AUTH_EXPIRED）か。 */
function isAuthExpired(outcome: UsecaseOutcome): boolean {
  return outcome.report && outcome.errorCode === "LINE_AUTH_EXPIRED";
}

/**
 * usecase を実行し、認証失効なら1回だけクリックスルー再ログインを挟んでリトライする。
 *
 * - 再ログインは1サイクル最大1回（`cycleState.reloginUsed`）・成功後のリトライは当該タスク最大1回（AC-8）。
 * - `relogin()` が SUCCEEDED を返したときだけ usecase を1回だけ再実行し、その outcome を報告する
 *   （transient wall も新セッション発行も同じく SUCCEEDED＝リトライで room を開き直す・AC-1/13）。
 * - SSO失効（SSO_EXPIRED）・異常（ERROR）・再ログイン無効/使用済みのときは、元の auth-expired outcome を
 *   そのまま返す（＝既存 FAILED＋フォールバックpush を維持・AC-2/4）。
 */
async function runWithRelogin(
  po: ChatPage,
  opts: CycleOptions,
  cycleState: { reloginUsed: boolean },
  run: () => Promise<UsecaseOutcome>,
): Promise<UsecaseOutcome> {
  const outcome = await run();
  if (!isAuthExpired(outcome)) {
    return outcome;
  }
  if (opts.autoReloginEnabled === false || cycleState.reloginUsed) {
    return outcome;
  }
  cycleState.reloginUsed = true;
  const result = await po.relogin();
  if (result !== "SUCCEEDED") {
    return outcome;
  }
  // 1回だけリトライ。再runがまた失効しても再ログインはしない（この outcome をそのまま報告）。
  return run();
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** ワーカー側 in-memory の SSO警告 throttle 状態（サイクルをまたいで保持）。 */
export interface SsoWarnState {
  /** 最後に警告を送った JST 日付（"YYYY-MM-DD"）。当日は再送しない（AC-6）。 */
  lastWarnedJstDate: string | null;
}

/** unix ミリ秒を JST の "YYYY-MM-DD" に変換する（1日1回 throttle のキー）。 */
function jstDateString(ms: number): string {
  const jst = new Date(ms + 9 * 60 * 60 * 1000);
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${jst.getUTCFullYear()}-${pad(jst.getUTCMonth() + 1)}-${pad(jst.getUTCDate())}`;
}

/**
 * SSO Cookie の失効が閾値以内なら管理者へ先回り警告を送る（1日1回・AC-5/6/7）。
 *
 * 期限は毎回渡された実 Cookie 由来の値を使う（ハードコードしない）＝手動再ログイン＋restart で
 * 新 storageState の新期限に自己追従する。期限不明（Cookie不在/session cookie）のときは警告しない
 * （反応型フォールバックが実失敗を拾うため、偽アラートを避ける）。
 */
export async function maybeWarnSsoExpiry(
  api: AppApiClient,
  params: {
    /** SSO Cookie の失効時刻（unix ミリ秒）。不明なら null。 */
    ssoExpiryMs: number | null;
    /** 現在時刻（unix ミリ秒）。 */
    nowMs: number;
    /** 失効の何日前から警告するか。 */
    thresholdDays: number;
    /** in-memory throttle 状態（副作用で更新する）。 */
    state: SsoWarnState;
  },
): Promise<void> {
  const { ssoExpiryMs, nowMs, thresholdDays, state } = params;
  if (ssoExpiryMs === null) {
    return;
  }
  // 「失効まで N 日以内」を厳密に表すため切り上げる（`ceil(remaining) <= N ⟺ remaining <= N`）。
  // floor だと 3日23時間残っていても 3 と扱われ、閾値3日の窓が実質4日に広がって約1日早く通知される。
  const daysRemaining = Math.ceil((ssoExpiryMs - nowMs) / MS_PER_DAY);
  if (daysRemaining > thresholdDays) {
    return;
  }
  const todayJst = jstDateString(nowMs);
  if (state.lastWarnedJstDate === todayJst) {
    return;
  }
  await api.postSessionWarning(daysRemaining);
  state.lastWarnedJstDate = todayJst;
}

/**
 * context の `__is_login_sso` Cookie 失効時刻（unix ミリ秒）を読む。
 * 不在・session cookie（`expires<=0`）・読み取り失敗のときは null（＝警告しない）。
 */
async function readSsoExpiryMs(context: BrowserContext): Promise<number | null> {
  try {
    const cookies = await context.cookies();
    const sso = cookies.find((c) => c.name === SSO_COOKIE_NAME);
    if (!sso || typeof sso.expires !== "number" || sso.expires <= 0) {
      return null;
    }
    return sso.expires * 1000; // Playwright の expires は unix 秒。
  } catch {
    return null;
  }
}

async function main(): Promise<void> {
  const config = loadConfig();
  const api = createAppApiClient(config);

  // storageState（LINEログインセッション）が未配置なら、Playwright の不明瞭な例外より前に
  // パスを含む明示的なエラーで停止する（運用者が RUNBOOK の初回ログイン手順で作成・配置するための検知）。
  if (!existsSync(config.storageStatePath)) {
    throw new Error(
      `storageState が見つかりません: ${config.storageStatePath}。` +
        `RUNBOOK の初回ログイン手順（scripts/create-auth-state.ts）で作成し、VMの永続ボリュームに配置してください。`,
    );
  }

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ storageState: config.storageStatePath });
  const page = await context.newPage();
  const po = new OamChatPage(page, config.oamAccountPath);
  // SSO先回り警告の throttle 状態（プロセス存続中は保持・restart でリセット＝新期限に自己追従）。
  const ssoWarnState: SsoWarnState = { lastWarnedJstDate: null };

  // eslint-disable-next-line no-constant-condition
  while (true) {
    try {
      await runCycle(api, po, {
        dryRun: config.dryRun,
        artifactDir: config.artifactDir,
        autoReloginEnabled: config.autoReloginEnabled,
      });
    } catch (err) {
      // 本文・Cookie・トークンを含みうる詳細は出力しない。
      console.error("line-chat-worker: cycle failed:", err instanceof Error ? err.message : "unknown error");
    }
    // storageState は実行毎に再エクスポートしてローリング更新する。
    await context.storageState({ path: config.storageStatePath });
    // 30日SSOの失効が近ければ管理者へ先回り警告（1日1回・実 Cookie 期限を毎回読む）。
    try {
      const ssoExpiryMs = await readSsoExpiryMs(context);
      await maybeWarnSsoExpiry(api, {
        ssoExpiryMs,
        nowMs: Date.now(),
        thresholdDays: config.ssoWarningThresholdDays,
        state: ssoWarnState,
      });
    } catch (err) {
      console.error("line-chat-worker: sso warning failed:", err instanceof Error ? err.message : "unknown error");
    }
    await sleep(config.pollIntervalMs);
  }
}

const isMainModule = process.argv[1] !== undefined && import.meta.url === `file://${process.argv[1]}`;
if (isMainModule) {
  main().catch((err) => {
    console.error("line-chat-worker: fatal error:", err instanceof Error ? err.message : "unknown error");
    process.exitCode = 1;
  });
}
