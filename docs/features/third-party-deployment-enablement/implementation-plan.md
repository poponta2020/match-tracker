---
status: completed
---
# 配布可能化（初期データseed＋フロント団体汎用化） 実装手順書

要件: [requirements.md](requirements.md)（AC は §4）。BE改修・スキーマ変更・本番migration なし。

## 実装タスク

### タスク1: A-2 初期データseed テンプレート＋陳腐化ファイル削除
- [ ] 完了
- **目的:** 空DB（schema.sql 適用済み）へ最初の団体・SUPER_ADMIN を投入する冪等な SQLテンプレートを整備し、鶏卵問題を解消する。陳腐化した旧seedを除去する。
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-8
- **主な変更領域:** `database/seed_initial.sql`（新規）、`scripts/seed_data.sql`（削除）
- **依存タスク:** なし（フロントと完全に直交）
- **必要なテスト:** 自動テストは無し（PostgreSQL固有の `md5`/`ON CONFLICT` を含み、検証は隔離PGでの実適用＋起動＝verify）。AC-4 は `scripts/seed_data.sql` の不在で判定。
- **実装内容:**
  - `database/seed_initial.sql` を新規作成:
    - **冒頭コメント（AC-8）**: 用途、カスタマイズ箇所（団体コード/名・色・管理者名・パスワード）、**順序制約（seed 適用は起動前、または適用後にアプリ再起動）**、適用コマンド例（`psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f database/seed_initial.sql`）を明記。
    - `INSERT INTO organizations (code, name, color, deadline_type, created_at, updated_at) VALUES ('CHANGEME_ORG_CODE', 'CHANGEME 団体名', '#22c55e', 'SAME_DAY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (code) DO NOTHING;`（**id 省略**）
    - `INSERT INTO players (name, password, gender, dominant_hand, role, require_password_change, ical_feed_token, created_at, updated_at) VALUES ('CHANGEME_ADMIN', 'CHANGEME_PASSWORD', 'その他', '右', 'SUPER_ADMIN', true, md5(random()::text), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (name) DO NOTHING;`（**id 省略・平文PW**）
  - `scripts/seed_data.sql` を削除。
- **完了条件（verify・実装時に main が1周）:** 隔離PG（使い捨て `postgres:18` コンテナ等、A-1 と同方式）に `schema.sql`→`seed_initial.sql` を `ON_ERROR_STOP=1` で適用してエラー0・2行投入を確認。続けてアプリを当該DB向けに起動→ `POST /api/players/login`（平文PW）が 200＋トークン→ `players.password` が `$2[aby]$…` に変換済みを確認。再適用で重複エラーが出ないこと（冪等）も確認。`scripts/seed_data.sql` が存在しないこと。
- **対応Issue:** #1147

### タスク2: D-2 略称ユーティリティ新設＋練習参加の締切汎用化
- [ ] 完了
- **目的:** 締切表示の `code==='hokudai'` 依存とバナーの `（北大）` 直書きを、団体データ駆動へ置き換える。略称ロジックを共有util化する（タスク3と共有）。
- **対応AC:** AC-5, AC-6, AC-9, AC-10
- **主な変更領域:** `karuta-tracker-ui/src/utils/organization.js`（新規）、`karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`、`karuta-tracker-ui/src/pages/practice/PracticeParticipation.test.jsx`
- **依存タスク:** なし（共有utilを新設する側）
- **必要なテスト（テストファースト）:** `PracticeParticipation.test.jsx` に、非hokudai団体（例 `{code:'myclub', name:'○○会'}`）が締切設定を持つ場合にバナーがその団体略称付きで表示されるケースを追加。既存の締切null（バナー非表示）ケースが green のままであること。util の `getOrgShortName` 単体テスト（override＝北大/わすら、フォールバック＝name先頭2文字、null/name欠落）。
- **実装内容:**
  - `utils/organization.js`: `export const getOrgShortName = (org) => org ? ({wasura:'わすら', hokudai:'北大'}[org.code] || (org.name ? org.name.substring(0,2) : '')) : '';`
  - `PracticeParticipation.jsx`:
    - 締切取得（現 :75-84）を、`playerOrgsRes.data` の各団体について `getDeadline(year, month, org.id)` を引き、**有効な締切を持つ団体だけ**を `[{org, info}]` に集める形へ変更（state を `deadlineInfo`→`deadlines` 配列に）。`.catch(()=>({data:null}))` は踏襲。
    - 既存のローカル `getOrgShortName(session)`（現 :260-269）を共有util利用に置換（`getOrgShortName(orgMap[session.organizationId])`）。
    - 締切バナー（現 :337-350）を `deadlines` の各要素で描画するループに変更。ゲート（`!info.noDeadline && info.deadline && beforeDeadline && diffDays>=0`）は踏襲。ラベルを `（{getOrgShortName(org)}）` に。
    - `deadlineInfo` の他参照が無いことを確認して置換（同ファイル内 grep）。
- **完了条件:** 上記テスト green・`git grep "code === 'hokudai'" src/pages/practice/PracticeParticipation.jsx` = 0件・lint 通過。
- **対応Issue:** #1148

### タスク3: D-2 選手一括編集のクイック追加ボタン動的化
- [ ] 完了
- **目的:** `hokudai`/`wasura` 決め打ちの行/一括クイック追加ボタンを、団体一覧からの動的生成へ置き換える。
- **対応AC:** AC-7, AC-9, AC-10
- **主な変更領域:** `karuta-tracker-ui/src/pages/players/PlayerBulkEdit.jsx`、`karuta-tracker-ui/src/pages/players/PlayerBulkEdit.test.jsx`
- **依存タスク:** タスク2（`utils/organization.js` の `getOrgShortName` を import して使用するため）
- **必要なテスト（テストファースト）:** `PlayerBulkEdit.test.jsx` に、fork団体（例 `{id:30, code:'myclub', name:'○○かるた会'}`）を含む orgs で `全員に○○を追加` ボタンが生成され押下で `addOrganizationIds` に当該idが入るケースを追加。既存の `全員に北大を追加`（hokudai=10）アサートが green のまま。
- **実装内容:**
  - `hokudai`/`wasura` の useMemo（現 :57-58）を削除。
  - `renderOrgAddButtons(row)`（現 :147-166）を `organizations.map(org => …)` の動的描画へ。ラベル `＋{getOrgShortName(org)}`、`disabled={rowHasOrg(row, org.id)}`、`onClick={() => addOrgToRow(row.id, org)}`。
  - 一括ボタン群（現 :265-282）を同様に `organizations.map(org => …)` へ。ラベル `全員に{getOrgShortName(org)}を追加`、`onClick={() => bulkAddOrg(org)}`。
  - 描画順は `organizations` の順序（hokudai,wasura の並びで既存ラベル順を維持）。
- **完了条件:** 上記テスト green・`git grep -nE "'hokudai'|'wasura'" src/pages/players/PlayerBulkEdit.jsx` = 0件・lint 通過。
- **対応Issue:** #1149

## 実装順序（Wave = 並行実装できるタスクの組）
- **Wave 1: タスク1, タスク2**（タスク1=`database/`・`scripts/`、タスク2=`karuta-tracker-ui/src/utils`・`src/pages/practice`。変更領域が完全に直交 → 並行可）
- **Wave 2: タスク3**（タスク2 が新設する `utils/organization.js` に依存）

## 備考
- 本 feature は BE 改修・スキーマ変更を含まないため**本番DBマイグレーション適用は不要**（seed は新規fork環境専用テンプレートで既存本番には流さない）。DoD の D2 ドキュメント整合は requirements/implementation-plan 本体でカバー。
- タスク1の起動確認（seed→(再)起動→ログイン）の1周は、そのまま A-4 セットアップ手順書の原稿になる（別feature）。
