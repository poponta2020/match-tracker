---
status: completed
audit_source: ユーザー直接要望（監査レポートなし）
selected_items: [1, 2]
---
# 対戦組み合わせ機能のPLAYER開放と空状態CTA 改修要件定義書

## 1. 改修概要

- **対象機能:**
  - 対戦組み合わせ機能（`MatchPairingController` / `PairingGenerator` / `PairingSummary`）
  - 試合結果一括入力（`BulkResultInput`）
  - 試合結果ビュー（`MatchResultsView`）
  - 設定メニュー（`SettingsPage`）
- **改修の背景:** 現状、対戦組み合わせの作成・編集や結果一括入力は ADMIN 以上に限定されているが、PLAYER（一般メンバー）も自団体の組み合わせを操作できるようにしたい。また、`/matches/results` 画面でその日に組み合わせが未作成の状態でも「結果を一括入力」ボタンが表示されており、対戦組み合わせ画面に誘導するのが自然である。
- **改修スコープ:** 以下2項目。
  1. 対戦組み合わせ機能を PLAYER 以上に開放（削除系は ADMIN 以上のまま）
  2. `/matches/results` 画面で現在表示中の試合番号のペアリングが0件のとき、「結果を一括入力」ボタンの代わりに「対戦組み合わせを作成」ボタンを表示し、`/pairings?date=YYYY-MM-DD` に遷移させる

## 2. 改修内容

### 2.1 対戦組み合わせ機能を PLAYER 以上に開放

#### 2.1.1 対象API（PLAYER 追加）

下記APIの `@RequireRole` を `{SUPER_ADMIN, ADMIN}` から `{SUPER_ADMIN, ADMIN, PLAYER}` に変更する。

| メソッド | パス | 内容 |
|---------|------|------|
| POST | `/api/match-pairings` | 単一作成 |
| POST | `/api/match-pairings/batch` | 一括作成 |
| POST | `/api/match-pairings/auto-match` | 自動マッチング |
| PUT | `/api/match-pairings/{id}/player` | 選手変更 |

**据え置き（ADMIN+ のまま）:**
- `DELETE /api/match-pairings/{id}`（個別削除）
- `DELETE /api/match-pairings/date-and-match`（日付・試合番号で一括削除）
- `DELETE /api/match-pairings/{id}/with-result`（結果込みリセット）

削除系を据え置く理由: 不可逆操作のため、誤操作・悪用リスクを抑える目的で管理者専用を維持。

#### 2.1.2 自団体スコープ強制を PLAYER にも適用

- 現状: `validateAdminScopeByDate` / `validateAdminScopeByPairingId` は `currentUserRole == "ADMIN"` のときだけ自団体スコープを検証している。
- 改修後: PLAYER の場合も、対象練習日（または対象ペアリング）の組織IDが、自分の所属団体（複数可）に含まれていなければ `ForbiddenException` を投げる。
- SUPER_ADMIN は引き続きスコープ強制なし。
- PLAYER の所属団体ID取得は既存の `OrganizationService.getPlayerOrganizationIds(currentUserId)` を流用。

#### 2.1.3 フロントエンドのアクセス制御解放

| 対象 | 現状 | 改修後 |
|------|------|--------|
| `/pairings` ルート | `RoleProtectedPage requiredRole="ADMIN"` | `ProtectedPage`（ログイン済みなら可） |
| `/pairings/summary` ルート | 同上 | 同上 |
| `/matches/bulk-input/:sessionId` ルート | 同上 | 同上 |
| 設定メニュー「組み合わせ作成」 | `visible: isAdmin()` | `visible: true` |
| `MatchResultsView` の「結果を一括入力」ボタン | `(isAdmin() || isSuperAdmin())` 判定 | 認証済みであれば表示（ロール判定を撤去） |

### 2.2 空状態CTA（`MatchResultsView`）

- **現状の問題:** [MatchResultsView.jsx:637-645](../../../karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx#L637-L645) で `(isAdmin() || isSuperAdmin()) && session` だけを条件に「結果を一括入力」ボタンを表示しており、現在表示中の試合番号の `pairings` が0件でも同ボタンを出してしまう。
- **修正方針:**
  - `currentPairings.length === 0` のとき → 「対戦組み合わせを作成」ボタンを表示し、押下で `/pairings?date=${selectedDate}` に遷移
  - `currentPairings.length > 0` のとき → 既存通り「結果を一括入力」ボタンを表示（権限は本改修で PLAYER 以上に開放）
- **修正後のあるべき姿:** 空のときは作成画面へ、組み合わせがあるときは結果入力画面へ、自然に誘導される。

## 3. 技術設計

### 3.1 API変更

- 既存エンドポイントのロール制御解放のみ。リクエスト/レスポンス形式の変更なし。
- PLAYER に自団体スコープ強制を適用するため、`MatchPairingController` の `validateAdminScopeByDate` / `validateAdminScopeByPairingId` を以下のいずれかにリファクタ:
  - 案A: 既存メソッドを汎用化し `validateScopeByDate` / `validateScopeByPairingId` にリネームし、ADMIN/PLAYER 両方を扱う。
  - 案B: `validatePlayerScopeByDate` / `validatePlayerScopeByPairingId` を新規追加し、各エンドポイントで ADMIN/PLAYER を切り分けて呼ぶ。
- いずれの案でも、PLAYER の所属団体ID取得は `OrganizationService.getPlayerOrganizationIds(currentUserId)` を利用。実装手順書で確定する。

### 3.2 DB変更

- なし。

### 3.3 フロントエンド変更

- [karuta-tracker-ui/src/App.jsx](../../../karuta-tracker-ui/src/App.jsx)
  - `/pairings`, `/pairings/summary`, `/matches/bulk-input/:sessionId` の3ルートを `RoleProtectedPage requiredRole="ADMIN"` から `ProtectedPage` に変更。
- [karuta-tracker-ui/src/pages/SettingsPage.jsx](../../../karuta-tracker-ui/src/pages/SettingsPage.jsx)
  - 「組み合わせ作成」メニュー項目の `visible: isAdmin()` を `visible: true` に変更。
- [karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx](../../../karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx)
  - 「結果を一括入力」ボタン（行637-645付近）の表示条件を、ロール判定撤去 + ペアリング有無での分岐に書き換え。
  - ペアリング0件時は「対戦組み合わせを作成」ボタンを表示し、`navigate('/pairings?date=' + selectedDate)` で遷移。
  - アイコンは `Shuffle`（既に PairingGenerator で使われているもの）等を流用する想定。

### 3.4 バックエンド変更

- [karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java)
  - `@RequireRole` 変更（POST, POST /batch, POST /auto-match, PUT /{id}/player の4箇所）
  - スコープバリデーションを ADMIN/PLAYER 両対応にリファクタ
- 必要に応じて `OrganizationService` か `MatchPairingService` に PLAYER 用のスコープ判定ヘルパーを追加

### 3.5 ドキュメント変更

- [docs/SPECIFICATION.md](../../SPECIFICATION.md): 対戦組み合わせAPIの権限一覧、画面アクセス権限の節を更新
- [docs/DESIGN.md](../../DESIGN.md): 必要に応じて権限設計の節を更新
- [docs/SCREEN_LIST.md](../../SCREEN_LIST.md): 「組み合わせ作成」画面のアクセス権限を更新

## 4. 影響範囲

### 4.1 既存機能への影響

- **ADMIN / SUPER_ADMIN の操作:** 変化なし（既存の権限・スコープが維持される）
- **PLAYER から見える操作範囲:** 拡大
  - 設定メニューに「組み合わせ作成」が出現
  - `/pairings` 画面で組み合わせ作成・自動マッチング・選手変更・一括保存が可能になる
  - `/matches/results` 画面で「結果を一括入力」「対戦組み合わせを作成」ボタンが出るようになる
  - `/matches/bulk-input/:sessionId` 画面に遷移可能になる
- **監査ログ:** `created_by` / `updated_by` に PLAYER のIDが記録されるようになる。既存の `createdBy` ハードコード問題は match-pairing 改修（PR #285）で既に解消済み。
- **他団体への影響:** スコープ強制によりブロックされるため、誤操作の波及はなし。

### 4.2 破壊的変更

- なし。API仕様（パス・リクエスト・レスポンス形式）は変更なし。
- 既存の ADMIN 向けユースケースは全て従来通り動作する。

### 4.3 フロントエンド⇔バックエンド整合性

- フロントとバックの両方で PLAYER 開放を同時に行うため、リリース時のずれによる403連発の懸念は最小化される。

## 5. 設計判断の根拠

- **PLAYER 開放の範囲:** 作成・編集・自動マッチング・選手変更まで開放し、削除系は据え置き。組み合わせは現場で柔軟に組み直すケースが多いため作成・編集系は開放するメリットが大きい一方、削除は意図せぬ全消去のリスクが高いため、引き続き管理者専用とする。
- **自団体スコープ強制を PLAYER にも適用:** 複数団体運用が前提のため、PLAYER が他団体の練習日を誤操作・破壊しないようにする必要がある。ADMIN と同じセキュリティモデルをPLAYERにも適用することで、解放と安全性を両立。
- **空状態CTAの判定単位:** 「現在表示中の試合番号のペアリング有無（`currentPairings.length === 0`）」で判定。理由は、第1試合は組み合わせ済みでも第2試合は未作成というよくあるケースで、適切に作成画面に誘導するため。日付全体（全試合）で判定すると、一部試合だけ未作成の状況で誘導できなくなる。
- **「結果を一括入力」ボタンも PLAYER 開放:** 組み合わせ作成を PLAYER 可能にする以上、結果入力もセットで PLAYER に開放しなければ操作のフローが破綻する。`/matches/bulk-input/:sessionId` ルートも同時に開放することで「ボタンは出るが押すと拒否される」状態を避ける。
- **遷移先URL:** `/pairings?date=YYYY-MM-DD`。`PairingGenerator` 側が既に `searchParams.get('date')` を初期日付として読み取る実装になっているため、追加実装不要で日付が継承される。
