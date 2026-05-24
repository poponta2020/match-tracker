---
status: completed
---
# Kaderu予約取り込みマルチ団体対応 要件定義書

## 1. 概要

### 目的
かでる2・7（Kaderu 2.7）からの予約自動取り込み機能を、現在対応している北大かるた会だけでなく、わすらもち会でも利用可能にする。両団体はそれぞれ別の Kaderu アカウントで同じ施設の予約を行っているため、団体ごとに認証情報を切り替えて取り込み、対応する `organization_id` 付きで `practice_sessions` に登録する。

### 背景・動機
- 30分ごとに動く `sync-kaderu-reservations.yml` は `KADERU_USER_ID` / `KADERU_PASSWORD` の単一アカウントでログインし、`scripts/room-checker/sync-reservations.js` が `organization='hokudai'` をハードコードで参照している
- そのため、わすらもち会が自団体の Kaderu アカウントで取った予約は `practice_sessions` に自動反映されず、管理者が手動で作成する運用になっている
- DB スキーマは `practice_sessions (session_date, organization_id) UNIQUE` まで対応済みで、伝助連携も `densuke-multi-org` 機能で団体別 URL 管理が完了している。残るは Kaderu 取り込み側のみ

## 2. ユーザーストーリー

### 対象ユーザー
- **わすらもち会 ADMIN / PLAYER:** わすらもち会名義で取った Kaderu 予約が、特に何もしなくても練習一覧・伝助連携に反映されることを期待
- **北大かるた会 ADMIN / PLAYER:** 現状の動作が壊れないこと
- **運用担当（SUPER_ADMIN）:** GitHub Secrets に2団体分のアカウントを登録するだけで、両団体の Kaderu 同期が動く

### 利用シナリオ
1. わすらもち会の幹事が Kaderu でわすらもち会アカウントを使って「はまなす 17:00-21:00」を予約
2. 30分以内に `sync-kaderu-reservations.yml` が走り、hokudai → wasura の順に2回 `sync-reservations.js` が実行される
3. wasura 実行時、`WASURA_KADERU_*` でログイン → 予約一覧をスクレイピング → わすらもち会の `practice_sessions` レコードが自動作成される
4. わすらもち会の練習一覧・伝助管理画面で当該日付が表示され、伝助ページ作成や参加管理が以後通常通り行える

## 3. 機能要件

### 3.1 動作仕様

#### sync-reservations.js
- 新規必須引数 `--org <code>` を追加（例: `--org hokudai`, `--org wasura`）
- `--org` を省略した場合はエラー終了（明示性を優先）
- 引数で受け取った `<code>` を `organizations.code` として検索し、`organization_id` を取得
  - 該当する組織が見つからない場合はエラー終了
- 認証情報は環境変数 `KADERU_USER_ID` / `KADERU_PASSWORD` から取得（既存と同じ）。ワークフロー側で団体ごとに Secrets を切り替えて注入する
- ログ出力に `[<org_code>]` のプレフィックスを付与してどちらの実行か追跡できるようにする
- 既存の隣室拡張・夜間（17:00-21:00）フィルタ・取消ステータス除外などのロジックは organization をまたがず、各 organization スコープで独立に動作する

#### GitHub Actions workflow
- `sync-kaderu-reservations.yml` を hokudai → wasura の順で2ステップ実行する構造に変更
- hokudai が失敗した場合でも wasura は実行する（`if: always()`）。両方の結果がジョブログに残る
- いずれかが失敗すれば workflow 全体は失敗扱い（赤バッジで気付けるように）
- `concurrency` グループ `kaderu-reservation-sync` は維持（重複起動防止）

### 3.2 ビジネスルール

#### 重複時の優先順位
- 同一日付・同一団体の `practice_sessions` が既に存在する場合、Kaderu 同期は新規作成を行わない（既存の `ON CONFLICT (session_date, organization_id) DO NOTHING` の挙動を維持）
- ただし、既存セッションの `venue_id` が NULL の場合は Kaderu 予約の会場で補完（既存挙動のまま）
- 既存セッションの会場拡張（隣室追加）も既存挙動のまま動作

#### 取り込み対象
- 両団体とも対象部屋は同一: すずらん / はまなす / あかなら / えぞまつ / 樹 / 花
- 両団体とも対象時間帯は夜間（17:00-21:00）のみ
- 「取消」ステータスの予約は両団体とも除外

#### 認証失敗時
- Kaderu ログイン失敗時はそのステップを失敗終了させる
- もう一方の団体のステップは独立して実行される

## 4. 技術設計

### 4.1 API設計
変更なし（バックエンド API・フロントエンドへの影響なし）

### 4.2 DB設計
変更なし。以下が既に整備されているため流用する：
- `organizations` テーブル（`code='hokudai'` / `code='wasura'` 両方登録済み）
- `practice_sessions.organization_id NOT NULL`
- `practice_sessions` の複合ユニーク制約 `UNIQUE (session_date, organization_id)`

### 4.3 フロントエンド設計
変更なし

### 4.4 スクリプト設計（`scripts/room-checker/sync-reservations.js`）

#### 引数パーサーの変更
```js
// 既存
let months = 2;
let dryRun = false;
// 追加
let orgCode = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--dry-run") dryRun = true;
  else if (args[i] === "--months" && args[i + 1]) { months = parseInt(args[i + 1]) || 2; i++; }
  else if (args[i] === "--org" && args[i + 1])    { orgCode = args[i + 1]; i++; }
}
if (!orgCode) { console.error("--org <code> is required"); process.exit(1); }
```

#### organization 取得の変更（`syncToDb` 関数内、現在 245-252 行）
```js
const orgResult = await dbClient.query(
  "SELECT id FROM organizations WHERE code = $1",
  [orgCode]
);
if (orgResult.rows.length === 0) {
  throw new Error(`組織 '${orgCode}' が見つかりません`);
}
const organizationId = Number(orgResult.rows[0].id);
console.log(`[${orgCode}] 組織ID: ${organizationId}`);
```

#### ログ出力のプレフィックス付与
- `console.log` / `console.warn` / `console.error` の主要ログに `[${orgCode}]` を付ける
- スクレイピング結果サマリ・処理結果サマリ・詳細リストの先頭にも付与

#### scrape-mypage.js
- 変更不要。`KADERU_USER_ID` / `KADERU_PASSWORD` 環境変数経由でログインしているため、呼び出し側（workflow）で env を切り替えれば対応可能

### 4.5 Workflow 設計（`.github/workflows/sync-kaderu-reservations.yml`）

#### 修正後の steps
```yaml
- name: Sync reservations (hokudai)
  working-directory: ./scripts/room-checker
  run: node sync-reservations.js --org hokudai --months 2
  env:
    KADERU_USER_ID: ${{ secrets.KADERU_USER_ID }}
    KADERU_PASSWORD: ${{ secrets.KADERU_PASSWORD }}
    DATABASE_URL: ${{ secrets.KADERU_DATABASE_URL }}

- name: Sync reservations (wasura)
  if: always()
  working-directory: ./scripts/room-checker
  run: node sync-reservations.js --org wasura --months 2
  env:
    KADERU_USER_ID: ${{ secrets.WASURA_KADERU_USER_ID }}
    KADERU_PASSWORD: ${{ secrets.WASURA_KADERU_PASSWORD }}
    DATABASE_URL: ${{ secrets.KADERU_DATABASE_URL }}
```

### 4.6 運用作業
- GitHub Secrets に以下を追加（リポジトリ管理者作業）:
  - `WASURA_KADERU_USER_ID`
  - `WASURA_KADERU_PASSWORD`
- 既存 Secrets（`KADERU_USER_ID` / `KADERU_PASSWORD` / `KADERU_DATABASE_URL`）は変更しない

## 5. 影響範囲

### 変更が必要な既存ファイル
- `scripts/room-checker/sync-reservations.js`
  - 引数 `--org` 追加
  - organization 取得を `code` 引数化（ハードコードの `'hokudai'` を除去）
  - ログ出力にプレフィックス付与
- `.github/workflows/sync-kaderu-reservations.yml`
  - 1ステップ → 2ステップに分割（hokudai / wasura）
  - 2ステップ目に `if: always()` を付与

### 変更不要なファイル
- `scripts/room-checker/scrape-mypage.js`（環境変数経由で認証情報を受け取るため）
- バックエンド全般（DB スキーマ・API ともに既にマルチ団体対応済み）
- フロントエンド全般

### 既存機能への影響
- **hokudai の Kaderu 同期:** workflow の1ステップ目として継続実行されるため動作変更なし
- **わすらもち会の手動作成セッション / 伝助経由作成セッション:** 同一日付の既存レコードは `ON CONFLICT DO NOTHING` で保護されるため上書きされない
- **`densuke-multi-org` 機能:** 影響なし。Kaderu 取り込みで作成された wasura セッションがそのまま伝助同期の対象として動作する
- **`practice_sessions` の `created_by` / `updated_by`:** Kaderu 同期では引き続き `SYSTEM_USER_ID=0` が使われる

### 運用上の影響
- GitHub Secrets に2つの値を追加するまでは wasura 同期ステップが失敗し続け、workflow が常に赤バッジになる
  - 対応: PR マージ前に Secrets を追加するか、PR マージ前にステップを一時的にスキップする運用を採る

## 6. 設計判断の根拠

### 「1回1団体」のスクリプト実行構造を選択
- スクリプト内で複数団体をループする場合、認証情報を `HOKUDAI_*` / `WASURA_*` のような名前空間付き環境変数で複数渡す必要があり、スクリプトの責務が肥大化する
- 1回1団体に絞れば、既存の `KADERU_USER_ID` / `KADERU_PASSWORD` の env 名をそのまま使えるため、`scrape-mypage.js` を一切変更せずに済む
- workflow が「どの団体を同期するか」を制御する責任を持ち、スクリプトは「指定された1団体を処理する」責任を持つ、という関心分離が綺麗

### GitHub Secrets のみで認証情報を管理（DB 保存しない）
- DB に Kaderu 認証情報を保存すると、暗号化方式・キー管理・漏洩時影響範囲などの追加設計が発生する
- 団体数が増えるペースは年単位で、Secrets の手動追加コストは無視できる
- ローカル開発で本番認証情報を扱う必要性も低い（必要なら開発者個人が自分の env に設定すれば良い）

### 既存 Secrets 名（`KADERU_USER_ID` / `KADERU_PASSWORD`）を hokudai 用として温存
- リネームすると Secrets の削除・再作成が必要になり、その間 hokudai 同期が止まるリスクがある
- 既存運用への影響を最小化することを優先

### 重複時に既存を優先（上書きしない）
- 既存の `ON CONFLICT (session_date, organization_id) DO NOTHING` の挙動と一貫
- 伝助経由・手動作成された session の `venue_id` や `total_matches` の手動調整を Kaderu 同期で巻き戻したくないため
- venue_id が NULL の場合のみ Kaderu 予約で補完するという既存ロジックは合理的なので踏襲

### `if: always()` で2ステップ目も実行
- 1ステップ目（hokudai）の失敗で2ステップ目（wasura）がスキップされると、片方の障害が他方の同期停止に連鎖する
- 両方のログが残ることで、どちらが原因かの切り分けが容易
