---
name: ship-pr1150-third-party-deployment-enablement
description: 配布可能化（初期データseed＋フロント団体汎用化）を出荷（PR #1150、親#1146子#1147#1148#1149）
type: project
category: ship
tags: [seed, deployment, third-party, frontend, react, organizations, sql]
---

# PR #1150 出荷記録 — third-party-deployment-enablement

- PR: https://github.com/poponta2020/match-tracker/pull/1150
- Issue: 親 #1146 ／ 子 #1147（Task1 seed）#1148（Task2 締切汎用化）#1149（Task3 一括編集ボタン）— PR 本文の closing keyword で子を自動クローズ
- 要件書: `docs/features/third-party-deployment-enablement/requirements.md`
- 設計/実装の詳細は harness memory [[impl_third_party_deployment_enablement]]・[[feature_third_party_seed_initial_data]]
- 出荷日: 2026-07-21

## 何を変えたか（3タスク直列・全 main 直実装・BE改修/スキーマ変更/本番migrationなし）

- **Task1(#1147) `database/seed_initial.sql`（新規）＋`scripts/seed_data.sql`（削除）**: 空DB（schema.sql 適用済み）へ最初の団体・SUPER_ADMIN を投入する冪等テンプレート。最小2行（organizations×1・players SUPER_ADMIN×1）・平文PW→起動時 `PasswordHashMigrationRunner` がBCrypt化・`ON CONFLICT DO NOTHING`・id採番委譲・`ical_feed_token=md5(random())`。冒頭コメントに順序制約（seed→(再)起動）とカスタマイズ箇所（CHANGEME_*）を明記。陳腐化した旧 seed（存在しないテーブル/列参照）を削除。`docs/design/db.md` にポインタ追記。
- **Task2(#1148) `karuta-tracker-ui/src/utils/organization.js`（新規）＋`PracticeParticipation`**: `getOrgShortName` を共有util化（override `{wasura,hokudai}`＋name先頭2文字fallback）。締切取得を `code==='hokudai'` 決め打ちから全所属団体ループ（`deadlineInfo`→`deadlines`配列・`Promise.all`並列）へ、バナーラベルを略称化。
- **Task3(#1149) `PlayerBulkEdit`**: hokudai/wasura 決め打ちの useMemo・クイック追加ボタンを `organizations.map` 動的生成へ（行ごと `＋略称`・一括 `全員に略称を追加`）。任意コードの団体で機能。
- test: `organization.test.js`（新規）・`PracticeParticipation.test.jsx`（非hokudai締切バナー＋hokudai非退行の2件追加）・`PlayerBulkEdit.test.jsx`（fork団体 行/一括の2件追加）。

## 設計の肝

- **本番非退行**: 略称 override 維持で hokudai=「北大」等を保持（撤去すると "北海道大学かるた会"→"北海" に退行）。締切は「設定を持つ団体のみ表示」で現状（hokudai のみ表示）を団体非依存に再現。
- **seed を隔離Dockerで実app起動まで1周検証（本番非到達）**: `docker run postgres:18`（別ポート5433）→`docker exec -i psql`で schema→seed 適用→bootRun（DB_URL=localhost:5433・SPRING_PROFILES≠render）→login 200+token→password `$2a$10$…`→再適用 `INSERT 0 0` 冪等・ハッシュ後再適用も非上書き。ログRender参照0件で隔離確証。

## auto-review（Codex CLI・effort high）

**1R pass・偽陽性ゼロ**（34.5k/500k tok）。`database/**`（review-extra 高リスク）＋705行差分で high 判定。意図設計7項目（平文PWは起動時ハッシュ化・略称override非退行・disabled撤去理由・冪等ON CONFLICT 等）をプロンプトに先回り明記しFP抑制、中立cwd+stdin踏襲。

## テスト・AC

- FE 全804テスト green（1件 `BulkResultInput.swipe.test.jsx` 並列フレーク→`--no-file-parallelism` で green 確認）・lint 0 error・BE `./gradlew test` BUILD SUCCESSFUL（database/スコープの DoD ゲート用）。
- 全10 AC クリア（AC-1〜3 verify=隔離Docker実app検証、AC-4〜7/9/10 auto-test、AC-8 manual=冒頭コメント）。
- DoD: A1=PASS（CI委譲）・A2 lint=PASS・A3=SKIP・B1 CI=PASS・C1 レビュー=PASS（r1 pass）・D1 memory=PASS・D2 docs=PASS。

## ship 後の確認事項（未検証）

- 実機 `/practice` 締切バナー（非hokudai団体で略称表示）・`/players` 一括編集の動的ボタンの目視は未実施（テストで代替、標準lean flow）。
- seed テンプレは新規fork環境専用。既存本番DBには適用しない（Non-goal）。
