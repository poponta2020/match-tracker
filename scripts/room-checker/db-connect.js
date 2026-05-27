/**
 * pg.Client を Render PostgreSQL 向けの推奨設定で生成し、接続失敗時に
 * 指数バックオフでリトライする共通ユーティリティ。
 *
 * 背景:
 *   GitHub Actions Runner (Azure) → Render PostgreSQL (AWS) の経路は、
 *   TLS handshake 途中で "Connection terminated unexpectedly" が間欠的に
 *   発生する。リトライ無しでスクリプトを即時失敗させていたため、cron が
 *   10日以上 100% 失敗していた。
 *
 *   keepAlive と connectionTimeoutMillis を明示し、handshake 失敗は
 *   1s / 2s / 4s / 8s / 16s でリトライする (合計最大 ~31秒待機)。
 */

const { Client } = require("pg");

/**
 * 接続済みの pg.Client を返す。最大 maxAttempts 回まで指数バックオフでリトライする。
 *
 * @param {string} connectionString - PostgreSQL接続URL
 * @param {object} [options]
 * @param {number} [options.maxAttempts=5] - 最大試行回数 (1=リトライなし)
 * @param {number} [options.baseDelayMs=1000] - 初回リトライまでの待機 (倍々に増える)
 * @returns {Promise<import('pg').Client>} - 接続済みクライアント
 */
async function connectWithRetry(connectionString, options = {}) {
  const maxAttempts = options.maxAttempts ?? 5;
  const baseDelayMs = options.baseDelayMs ?? 1000;

  let lastError = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const client = new Client({
      connectionString,
      ssl: { rejectUnauthorized: false },
      keepAlive: true,
      connectionTimeoutMillis: 10_000,
    });

    try {
      await client.connect();
      if (attempt > 1) {
        console.log(`[db-connect] retry succeeded on attempt ${attempt}/${maxAttempts}`);
      }
      return client;
    } catch (err) {
      lastError = err;
      // 接続失敗時は半分開いた client を片付ける
      try { await client.end(); } catch { /* ignore */ }

      if (attempt === maxAttempts) {
        break;
      }
      const delayMs = baseDelayMs * Math.pow(2, attempt - 1);
      console.warn(
        `[db-connect] connect failed (attempt ${attempt}/${maxAttempts}): ${err.message}. retrying in ${delayMs}ms...`
      );
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }

  throw new Error(
    `DB接続に${maxAttempts}回失敗しました: ${lastError?.message ?? "unknown"}`
  );
}

module.exports = { connectWithRetry };
