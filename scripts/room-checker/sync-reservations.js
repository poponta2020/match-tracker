/**
 * かでる2・7 マイページ予約 → 練習日自動登録スクリプト
 *
 * マイページの「予約申込一覧」からスクレイピングした予約データを元に、
 * アプリの練習日（practice_sessions）に自動登録する。
 *
 * 処理フロー:
 *   1. scrape-mypage.js を実行して予約一覧JSONを取得
 *   2. 日付ごとに予約された部屋をグルーピング
 *   3. 隣室ペアが揃っていれば拡張会場として登録
 *   4. 既存セッションがあればスキップ（隣室追加の場合は拡張）
 *
 * Usage:
 *   node sync-reservations.js [--months 2] [--dry-run]
 *
 * Options:
 *   --months   取得月数（デフォルト2）
 *   --dry-run  DB書き込みを行わず、処理内容のみ表示
 *
 * 環境変数:
 *   KADERU_USER_ID  - 利用者ID
 *   KADERU_PASSWORD - パスワード
 *   DATABASE_URL or DB_URL + DB_USERNAME + DB_PASSWORD - PostgreSQL接続情報
 */

const { execFileSync } = require("child_process");
const { Client } = require("pg");
const path = require("path");

// ============================================================
// 定数
// ============================================================

/** 部屋名 → Venue ID（樹/花は拡張会場に直接マッピング） */
const ROOM_VENUE_MAP = {
  すずらん: 3,
  はまなす: 11,
  あかなら: 4,
  えぞまつ: 8,
  樹: 9, // えぞまつ+あかなら（拡張会場）
  花: 7, // はまなす+すずらん（拡張会場）
};

/** 拡張会場のVenue ID（直接予約された場合に判定用） */
const EXPANDED_VENUE_IDS = new Set([7, 9]);

/** 単室ペア → 拡張会場ID（キーはソート済みVenue IDのカンマ区切り） */
const ADJACENT_EXPANDED = {
  "3,11": 7, // すずらん + はまなす → すずらん・はまなす
  "4,8": 9, // あかなら + えぞまつ → あかなら・えぞまつ
};

/** 単室 → 隣室のVenue ID */
const ADJACENT_MAP = {
  3: 11,
  11: 3,
  4: 8,
  8: 4,
};

/** 単室 → 拡張会場のVenue ID */
const EXPAND_MAP = {
  3: 7,
  11: 7,
  4: 9,
  8: 9,
};

/** 拡張会場に含まれる単室のセット */
const EXPANDED_INCLUDES = {
  7: new Set([3, 11]),
  9: new Set([4, 8]),
};

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
  const scraperPath = path.join(__dirname, "scrape-mypage.js");
  console.log("スクレイピング実行中...");

  const output = execFileSync(
    "node",
    [scraperPath, "--months", String(months)],
    {
      env: process.env,
      encoding: "utf8",
      timeout: 120_000,
      // stderrは親プロセスに継承してログ表示
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
 * @returns {Map<string, {venueIds: number[], resolvedVenueId: number}>}
 */
function groupByDateAndResolveVenue(reservations) {
  // 「取消」ステータスを除外
  const active = reservations.filter((r) => r.status !== "取消");
  console.log(
    `ステータスフィルタ: ${reservations.length}件 → ${active.length}件（取消${reservations.length - active.length}件除外）`
  );

  // 日付ごとに部屋をグルーピング
  const dateRoomMap = new Map();
  for (const r of active) {
    if (!dateRoomMap.has(r.date)) {
      dateRoomMap.set(r.date, new Set());
    }
    const venueId = ROOM_VENUE_MAP[r.room];
    if (venueId) {
      dateRoomMap.get(r.date).add(venueId);
    }
  }

  // 日付ごとに会場を決定
  const result = new Map();
  for (const [date, venueIds] of dateRoomMap) {
    const ids = Array.from(venueIds).sort((a, b) => a - b);

    // 拡張会場ID（樹=9, 花=7）が直接含まれている場合はそれを優先
    const expandedId = ids.find((id) => EXPANDED_VENUE_IDS.has(id));
    if (expandedId) {
      result.set(date, {
        venueIds: ids,
        resolvedVenueId: expandedId,
      });
    } else if (ids.length === 1) {
      // 単室のみ
      result.set(date, {
        venueIds: ids,
        resolvedVenueId: ids[0],
      });
    } else {
      // 隣室ペアが揃っているか確認
      const key = ids.join(",");
      if (ADJACENT_EXPANDED[key]) {
        result.set(date, {
          venueIds: ids,
          resolvedVenueId: ADJACENT_EXPANDED[key],
        });
      } else {
        // 複数の独立した部屋がある場合（例: すずらん+あかなら）
        // → 最初の部屋で登録（通常このケースは発生しない想定）
        result.set(date, {
          venueIds: ids,
          resolvedVenueId: ids[0],
        });
        console.warn(
          `${date}: 複数の独立した部屋が予約されています: ${ids.join(", ")}。最初の部屋で登録します。`
        );
      }
    }
  }

  return result;
}

/**
 * 既存セッションに対して隣室拡張が必要か判定する
 *
 * @param {number} existingVenueId - 既存セッションのVenue ID
 * @param {number} resolvedVenueId - 予約から算出したVenue ID
 * @param {number[]} reservedVenueIds - 予約されている全部屋のVenue ID
 * @returns {{ shouldExpand: boolean, expandedVenueId: number|null }}
 */
function checkExpansion(existingVenueId, resolvedVenueId, reservedVenueIds) {
  // 既に拡張済み
  if (existingVenueId === resolvedVenueId) {
    return { shouldExpand: false, expandedVenueId: null };
  }

  // 既に拡張会場の場合
  if (EXPANDED_INCLUDES[existingVenueId]) {
    return { shouldExpand: false, expandedVenueId: null };
  }

  // resolvedVenueId が拡張会場で、既存の単室がその拡張に含まれる場合 → 拡張
  // （花/樹 が直接予約された場合: resolvedVenueId=7/9）
  if (EXPANDED_INCLUDES[resolvedVenueId]?.has(existingVenueId)) {
    return { shouldExpand: true, expandedVenueId: resolvedVenueId };
  }

  // 既存が単室で、その隣室が今回の予約に含まれている場合 → 拡張
  const adjacentId = ADJACENT_MAP[existingVenueId];
  if (adjacentId && reservedVenueIds.includes(adjacentId)) {
    const expandedId = EXPAND_MAP[existingVenueId];
    if (expandedId) {
      return { shouldExpand: true, expandedVenueId: expandedId };
    }
  }

  return { shouldExpand: false, expandedVenueId: null };
}

// ============================================================
// DB操作
// ============================================================

async function syncToDb(dbClient, dateVenueMap, dryRun) {
  // hokudai の organization_id を取得
  const orgResult = await dbClient.query(
    "SELECT id FROM organizations WHERE code = 'hokudai'"
  );
  if (orgResult.rows.length === 0) {
    throw new Error("組織 'hokudai' が見つかりません");
  }
  const organizationId = Number(orgResult.rows[0].id);
  console.log(`組織ID: ${organizationId} (hokudai)`);

  // Venue情報を一括取得
  const venueResult = await dbClient.query(
    "SELECT id, name, default_match_count, capacity FROM venues WHERE id IN (3, 4, 7, 8, 9, 11)"
  );
  const venueMap = new Map();
  for (const row of venueResult.rows) {
    venueMap.set(Number(row.id), {
      name: row.name,
      defaultMatchCount: row.default_match_count,
      capacity: row.capacity,
    });
  }

  const stats = { created: 0, expanded: 0, skipped: 0 };
  const details = [];
  const now = new Date().toISOString();

  for (const [date, { venueIds, resolvedVenueId }] of dateVenueMap) {
    // 今日より前の日付はスキップ
    const jstNow = new Date(
      new Date().toLocaleString("en-US", { timeZone: "Asia/Tokyo" })
    );
    const today = `${jstNow.getFullYear()}-${String(jstNow.getMonth() + 1).padStart(2, "0")}-${String(jstNow.getDate()).padStart(2, "0")}`;
    if (date < today) {
      details.push(`${date}: 過去の日付のためスキップ`);
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
      const existingVenueId = Number(existing.venue_id);

      // 隣室拡張の判定
      const { shouldExpand, expandedVenueId } = checkExpansion(
        existingVenueId,
        resolvedVenueId,
        venueIds
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
    const totalMatches = venue?.defaultMatchCount || 7;
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
         VALUES ($1, $2, $3, '17:00', '21:00', $4, $5, $6, $6, $7, $7)
         ON CONFLICT (session_date, organization_id) DO NOTHING`,
        [
          date,
          totalMatches,
          resolvedVenueId,
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
    console.log("夜間予約が見つかりませんでした。終了します。");
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
