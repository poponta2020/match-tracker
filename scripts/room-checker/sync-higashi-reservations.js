/**
 * 札幌市東区民センター マイページ予約 → 練習日自動登録スクリプト
 *
 * マイページの「申込履歴・結果」からスクレイピングした予約データを元に、
 * アプリの練習日（practice_sessions）に自動登録する。
 *
 * 処理フロー:
 *   1. scrape-higashi-history.js を実行して予約一覧JSONを取得
 *   2. 日付ごとに予約された部屋をグルーピング
 *   3. 会場を決定:
 *        - 和室全室 or (さくら+かっこう) → 東全室 (venue_id=10)
 *        - さくら のみ                    → 東🌸  (venue_id=6)
 *        - かっこう のみ                  → 対象外（警告ログのみ）
 *   4. 既存セッションがあれば拡張（東🌸 → 東全室 への昇格のみ）、無ければ新規作成
 *
 * Usage:
 *   node sync-higashi-reservations.js [--months 2] [--dry-run]
 *
 * Options:
 *   --months   取得月数（デフォルト2）
 *   --dry-run  DB書き込みを行わず、処理内容のみ表示
 *
 * 環境変数:
 *   SAPPORO_COMMUNITY_USER_ID  - 利用者ID
 *   SAPPORO_COMMUNITY_PASSWORD - パスワード
 *   DATABASE_URL or DB_URL + DB_USERNAME + DB_PASSWORD - PostgreSQL接続情報
 */

const { execFileSync } = require("child_process");
const { Client } = require("pg");
const path = require("path");

// ============================================================
// 定数
// ============================================================

/** 対象組織コード */
const TARGET_ORGANIZATION_CODE = "hokudai";

/** 東🌸（さくら単室）の Venue ID */
const SAKURA_VENUE_ID = 6;

/** 東全室（さくら+かっこう or 和室全室）の Venue ID */
const ALL_ROOM_VENUE_ID = 10;

/** 東区民センターで利用する全 Venue ID */
const HIGASHI_VENUE_IDS = [SAKURA_VENUE_ID, ALL_ROOM_VENUE_ID];

/** Kaderu（道民活動センター）系の Venue ID — 同日併存時は東側から触らない */
const KADERU_VENUE_IDS = new Set([3, 4, 7, 8, 9, 11]);

/** 固定時刻（夜間枠） */
const FIXED_START_TIME = "18:00";
const FIXED_END_TIME = "21:00";

/** システムユーザーID（DensukeImportService.SYSTEM_USER_ID と同じ） */
const SYSTEM_USER_ID = 0;

// ============================================================
// DB接続
// ============================================================

function buildConnectionString() {
  let connectionString = process.env.DATABASE_URL;
  if (!connectionString && process.env.DB_URL) {
    const dbUrl = process.env.DB_URL.replace(/^jdbc:/, "");
    const username = process.env.DB_USERNAME;
    const password = process.env.DB_PASSWORD;
    if (username && password) {
      const url = new URL(dbUrl);
      url.username = username;
      url.password = password;
      connectionString = url.toString();
    } else {
      connectionString = dbUrl;
    }
  }
  if (!connectionString) {
    throw new Error(
      "DATABASE_URL または DB_URL 環境変数が設定されていません"
    );
  }
  return connectionString;
}

// ============================================================
// スクレイピング実行
// ============================================================

function runScraper(months) {
  const scraperPath = path.join(__dirname, "scrape-higashi-history.js");
  console.log("スクレイピング実行中...");

  const output = execFileSync(
    "node",
    [scraperPath, "--months", String(months)],
    {
      env: process.env,
      encoding: "utf8",
      timeout: 180_000,
      stdio: ["pipe", "pipe", "inherit"],
    }
  );

  const reservations = JSON.parse(output.trim());
  console.log(`スクレイピング完了: ${reservations.length}件の夜間予約を取得`);
  return reservations;
}

// ============================================================
// ビジネスロジック
// ============================================================

/**
 * 予約リストを日付ごとにグルーピングし、会場を決定する
 *
 * @param {Array} reservations - スクレイピング結果
 * @returns {Map<string, {rooms: string[], resolvedVenueId: number|null}>}
 */
function groupByDateAndResolveVenue(reservations) {
  // scrape-higashi-history.js 側で取消済は除外済みだが念のため
  const active = reservations.filter((r) => !String(r.status || "").includes("取消"));
  if (active.length !== reservations.length) {
    console.log(
      `ステータスフィルタ: ${reservations.length}件 → ${active.length}件（取消${reservations.length - active.length}件除外）`
    );
  }

  // 日付ごとに部屋をグルーピング
  const dateRoomMap = new Map();
  for (const r of active) {
    if (!dateRoomMap.has(r.date)) {
      dateRoomMap.set(r.date, new Set());
    }
    dateRoomMap.get(r.date).add(r.room);
  }

  // 日付ごとに会場を決定
  const result = new Map();
  for (const [date, roomSet] of dateRoomMap) {
    const rooms = Array.from(roomSet).sort();
    let resolvedVenueId = null;

    if (roomSet.has("和室全室")) {
      // 和室全室の予約があれば東全室扱い
      resolvedVenueId = ALL_ROOM_VENUE_ID;
    } else if (roomSet.has("さくら") && roomSet.has("かっこう")) {
      // さくら + かっこう 同日 → 東全室
      resolvedVenueId = ALL_ROOM_VENUE_ID;
    } else if (roomSet.has("さくら")) {
      // さくら のみ → 東🌸
      resolvedVenueId = SAKURA_VENUE_ID;
    } else {
      // かっこう のみ → 運用対象外
      console.warn(
        `${date}: かっこう単独の予約は運用対象外のためスキップします (rooms=${rooms.join(", ")})`
      );
      resolvedVenueId = null;
    }

    result.set(date, { rooms, resolvedVenueId });
  }

  return result;
}

/**
 * 既存セッションに対して昇格（東🌸 → 東全室）が必要か判定する
 *
 * @param {number} existingVenueId - 既存セッションの Venue ID
 * @param {number} resolvedVenueId - 予約から算出した Venue ID
 * @returns {{ shouldExpand: boolean, expandedVenueId: number|null }}
 */
function checkExpansion(existingVenueId, resolvedVenueId) {
  // 同一 → 何もしない
  if (existingVenueId === resolvedVenueId) {
    return { shouldExpand: false, expandedVenueId: null };
  }
  // 既存が東全室 → ダウングレードしない
  if (existingVenueId === ALL_ROOM_VENUE_ID) {
    return { shouldExpand: false, expandedVenueId: null };
  }
  // 既存が東🌸 + 算出が東全室 → 昇格
  if (
    existingVenueId === SAKURA_VENUE_ID &&
    resolvedVenueId === ALL_ROOM_VENUE_ID
  ) {
    return { shouldExpand: true, expandedVenueId: ALL_ROOM_VENUE_ID };
  }
  // Kaderu 系など他の会場は触らない
  return { shouldExpand: false, expandedVenueId: null };
}

// ============================================================
// DB操作
// ============================================================

async function syncToDb(dbClient, dateVenueMap, dryRun) {
  // hokudai の organization_id を取得
  const orgResult = await dbClient.query(
    "SELECT id FROM organizations WHERE code = $1",
    [TARGET_ORGANIZATION_CODE]
  );
  if (orgResult.rows.length === 0) {
    throw new Error(`組織 '${TARGET_ORGANIZATION_CODE}' が見つかりません`);
  }
  const organizationId = Number(orgResult.rows[0].id);
  console.log(`組織ID: ${organizationId} (${TARGET_ORGANIZATION_CODE})`);

  // Venue情報を一括取得
  const venueResult = await dbClient.query(
    "SELECT id, name, default_match_count, capacity FROM venues WHERE id = ANY($1::int[])",
    [HIGASHI_VENUE_IDS]
  );
  const venueMap = new Map();
  for (const row of venueResult.rows) {
    venueMap.set(Number(row.id), {
      name: row.name,
      defaultMatchCount: row.default_match_count,
      capacity: row.capacity,
    });
  }
  for (const vid of HIGASHI_VENUE_IDS) {
    if (!venueMap.has(vid)) {
      throw new Error(`venues テーブルに必要な会場 id=${vid} がありません`);
    }
  }

  const stats = { created: 0, expanded: 0, skipped: 0 };
  const details = [];
  const now = new Date().toISOString();

  // JST基準の今日
  const jstNow = new Date(
    new Date().toLocaleString("en-US", { timeZone: "Asia/Tokyo" })
  );
  const today = `${jstNow.getFullYear()}-${String(jstNow.getMonth() + 1).padStart(2, "0")}-${String(jstNow.getDate()).padStart(2, "0")}`;

  for (const [date, { rooms, resolvedVenueId }] of dateVenueMap) {
    // 過去日付はスキップ
    if (date < today) {
      details.push(`${date}: 過去の日付のためスキップ`);
      stats.skipped++;
      continue;
    }

    // かっこう単独などで会場が決定しなかった場合はスキップ
    if (resolvedVenueId == null) {
      details.push(`${date}: 運用対象外の部屋構成のためスキップ (${rooms.join(", ")})`);
      stats.skipped++;
      continue;
    }

    // 既存セッションを確認
    const existingResult = await dbClient.query(
      "SELECT id, venue_id FROM practice_sessions WHERE session_date = $1 AND organization_id = $2",
      [date, organizationId]
    );

    if (existingResult.rows.length > 0) {
      const existing = existingResult.rows[0];

      // venue_id が NULL の場合、算出会場で補完
      if (existing.venue_id == null) {
        const venue = venueMap.get(resolvedVenueId);
        const msg = `${date}: 会場未設定のセッションに会場を設定 → ${venue?.name || resolvedVenueId}`;
        if (dryRun) {
          details.push(`[DRY-RUN] ${msg}`);
        } else {
          await dbClient.query(
            `UPDATE practice_sessions
             SET venue_id = $1, capacity = $2, updated_by = $3, updated_at = $4
             WHERE id = $5`,
            [
              resolvedVenueId,
              venue?.capacity || null,
              SYSTEM_USER_ID,
              now,
              existing.id,
            ]
          );
          details.push(msg);
        }
        stats.expanded++;
        continue;
      }

      const existingVenueId = Number(existing.venue_id);

      // Kaderu 系会場に既に紐付いている場合は触らない
      if (KADERU_VENUE_IDS.has(existingVenueId)) {
        details.push(
          `${date}: 既存セッションがかでる系会場（venue_id=${existingVenueId}）のためスキップ`
        );
        stats.skipped++;
        continue;
      }

      // 東🌸 → 東全室 の昇格判定
      const { shouldExpand, expandedVenueId } = checkExpansion(
        existingVenueId,
        resolvedVenueId
      );
      if (shouldExpand && expandedVenueId) {
        const expandedVenue = venueMap.get(expandedVenueId);
        const fromVenue = venueMap.get(existingVenueId);
        const msg = `${date}: 会場拡張 ${fromVenue?.name || existingVenueId} → ${expandedVenue?.name || expandedVenueId}`;
        if (dryRun) {
          details.push(`[DRY-RUN] ${msg}`);
        } else {
          await dbClient.query(
            `UPDATE practice_sessions
             SET venue_id = $1, capacity = $2, updated_by = $3, updated_at = $4
             WHERE id = $5`,
            [
              expandedVenueId,
              expandedVenue?.capacity || null,
              SYSTEM_USER_ID,
              now,
              existing.id,
            ]
          );
          details.push(msg);
        }
        stats.expanded++;
      } else {
        const venueName = venueMap.get(existingVenueId)?.name || existingVenueId;
        details.push(`${date}: 既存セッションあり（${venueName}）、スキップ`);
        stats.skipped++;
      }
      continue;
    }

    // 新規作成
    const venue = venueMap.get(resolvedVenueId);
    const totalMatches = venue?.defaultMatchCount || 2;
    const capacity = venue?.capacity || null;
    const venueName = venue?.name || `Venue#${resolvedVenueId}`;
    const msg = `${date}: 練習日を作成（会場: ${venueName}, ${totalMatches}試合）`;

    if (dryRun) {
      details.push(`[DRY-RUN] ${msg}`);
    } else {
      const insertResult = await dbClient.query(
        `INSERT INTO practice_sessions
           (session_date, total_matches, venue_id, start_time, end_time,
            capacity, organization_id, created_by, updated_by, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $8, $9, $9)
         ON CONFLICT (session_date, organization_id) DO NOTHING`,
        [
          date,
          totalMatches,
          resolvedVenueId,
          FIXED_START_TIME,
          FIXED_END_TIME,
          capacity,
          organizationId,
          SYSTEM_USER_ID,
          now,
        ]
      );
      if (insertResult.rowCount === 0) {
        details.push(`${date}: 競合のためスキップ（別プロセスが先に作成）`);
        stats.skipped++;
        continue;
      }
      details.push(msg);
    }
    stats.created++;
  }

  return { stats, details };
}

// ============================================================
// メイン
// ============================================================

async function main() {
  const args = process.argv.slice(2);
  let months = 2;
  let dryRun = false;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--dry-run") {
      dryRun = true;
    } else if (args[i] === "--months" && args[i + 1]) {
      months = parseInt(args[i + 1]) || 2;
      i++;
    }
  }

  if (dryRun) {
    console.log("=== DRY-RUN モード（DB書き込みなし）===\n");
  }

  // 1. スクレイピング
  const reservations = runScraper(months);
  if (reservations.length === 0) {
    console.log("東区民センターの夜間予約は見つかりませんでした。終了します。");
    return;
  }

  // 2. 日付ごとにグルーピング・会場決定
  const dateVenueMap = groupByDateAndResolveVenue(reservations);
  console.log(`\n処理対象: ${dateVenueMap.size}日分\n`);

  // 3. DB同期
  const connectionString = buildConnectionString();
  const dbClient = new Client({
    connectionString,
    ssl: { rejectUnauthorized: false },
  });

  try {
    await dbClient.connect();
    console.log("DB接続成功");

    const { stats, details } = await syncToDb(dbClient, dateVenueMap, dryRun);

    // 結果サマリー
    console.log("\n========================================");
    console.log("処理結果:");
    console.log(`  新規作成: ${stats.created}件`);
    console.log(`  会場拡張: ${stats.expanded}件`);
    console.log(`  スキップ: ${stats.skipped}件`);
    console.log("========================================");
    console.log("\n詳細:");
    for (const d of details) {
      console.log(`  ${d}`);
    }
  } finally {
    await dbClient.end();
  }
}

main().catch((err) => {
  console.error("実行エラー:", err.message);
  process.exit(1);
});
