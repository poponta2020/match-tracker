---
status: completed
audit_source: 会話内レポート
selected_items: [1, 2, 3, 4, 5, 6, 7, 8, 9]
---
# 団体別の表示機能 改修要件定義書

## 1. 改修概要

- **対象機能:** 団体別の表示・フィルタリング機能
- **改修の背景:** 監査レポートにて、OrganizationController の認可不足（他人の参加団体を変更可能）、複数エンドポイントの団体フィルタ適用漏れ、フロントエンドのハードコード、型安全性の不足が検出された
- **改修スコープ:** 全9項目（高2件・中3件・低4件）

## 2. 改修内容

### 2.1 OrganizationController の認可チェック追加（高・項目1,2）

**現状の問題:**
- `GET /api/organizations/players/{playerId}` に認可チェックなし → 他人の参加団体を取得可能
- `PUT /api/organizations/players/{playerId}` に認可チェックなし → 他人の参加団体を変更可能

**修正方針:**
- 両エンドポイントに `HttpServletRequest` パラメータを追加
- `RoleCheckInterceptor.extractAndSetUserId(request, false)` で設定される `currentUserId` を取得
- `currentUserId` が null の場合は 401 を返す
- `currentUserId` と `playerId` が一致しない場合、SUPER_ADMIN でなければ 403 を返す
- SUPER_ADMIN は他ユーザーの操作も許可

**修正後のあるべき姿:**
- ログインユーザーは自分自身の参加団体のみ取得・変更可能
- SUPER_ADMIN は任意ユーザーの参加団体を操作可能

### 2.2 findNextParticipation に団体フィルタ追加（中・項目3）

**現状の問題:**
- `PracticeSessionService.findNextParticipation` が `findUpcomingSessions(today)` を呼び出しており、全団体の練習セッションから直近1件を取得する
- ユーザーが参加していない団体のセッションが表示される可能性がある

**修正方針:**
- `findNextParticipation` 内で `organizationService.getPlayerOrganizationIds(playerId)` を取得
- `practiceSessionRepository.findUpcomingSessions(today)` を `practiceSessionRepository.findUpcomingSessionsByOrganizationIdIn(orgIds, today)` に変更
- orgIds が空の場合は null を返す

**修正後のあるべき姿:**
- ホーム画面の「次の参加予定」は、ユーザーの参加団体に属する練習セッションのみ対象

### 2.3 未使用エンドポイントの削除（中・項目4,5 / 低・項目7）

**現状の問題:**
- `GET /api/practice-sessions`（全件取得）: フロントエンドから未使用
- `GET /api/practice-sessions/upcoming`: フロントエンドから未使用
- `GET /api/practice-sessions/range`: フロントエンドに API メソッド自体が未定義

**修正方針:**

| 対象 | Controller メソッド | Service メソッド | Repository メソッド | API クライアント |
|------|-------------------|-----------------|-------------------|----------------|
| 全件取得 | `getAllSessions` 削除 | `findAllSessions` 削除, `findAllSessionsByPlayer` 削除 | `findAllOrderBySessionDateDesc` 削除, `findByOrganizationIdInOrderBySessionDateDesc` 削除 | `getAll` 削除 |
| upcoming | `getUpcomingSessions` 削除 | `findUpcomingSessions` は残す（findNextParticipation内で使用→項目3で置換後に削除可） | `findUpcomingSessions` は残す（項目3で不要になれば削除） | `getUpcoming` 削除 |
| range | `getSessionsInRange` 削除 | `findSessionsInRange` 削除 | `findByDateRange` 削除, `findByOrganizationIdInAndDateRange` 削除 | 定義なし（対応不要） |

**注意:** 項目3の修正で `findUpcomingSessions` が不要になるため、合わせて Repository の `findUpcomingSessions` も削除可能。

**修正後のあるべき姿:**
- 不要なエンドポイントが存在せず、未フィルタの API 経由でのデータ漏洩リスクが解消される

### 2.4 GET /api/practice-sessions/dates に団体フィルタ追加（低・項目6）

**現状の問題:**
- `MatchResultsView.jsx` から呼ばれているが、他団体の練習日も日付候補に含まれる

**修正方針:**
- Controller の `getSessionDates` に `HttpServletRequest` を追加し `currentUserId` を取得
- Service に `findSessionDatesByPlayer` メソッドを追加
  - `organizationService.getPlayerOrganizationIds(playerId)` で団体ID一覧取得
  - `practiceSessionRepository.findSessionDatesByOrganizationIdIn(orgIds, fromDate)` を使用（既存メソッド）
- `currentUserId` が null の場合はフィルタなし（既存挙動を維持）

**修正後のあるべき姿:**
- 試合結果ビューの日付候補が、ユーザーの参加団体の練習日のみに絞られる

### 2.5 getOrgUnderlineColor のハードコード解消（低・項目8）

**現状の問題:**
- `PracticeList.jsx:203-208` で団体コードごとの色がハードコードされている
- DB の `organizations.color` フィールドが存在するにもかかわらず使用されていない

**修正方針:**
- `getOrgUnderlineColor` 関数を修正し、`orgMap[organizationId]?.color` を直接返すように変更

**修正後のあるべき姿:**
- 新しい団体が追加されても、DB の `color` フィールドに基づいて自動的に色が反映される

### 2.6 updatePlayerOrganizations の型安全性向上（低・項目9）

**現状の問題:**
- `OrganizationController.updatePlayerOrganizations` の `@RequestBody` が `Map<String, List<Long>>` で型安全性が低い

**修正方針:**
- `dto/UpdatePlayerOrganizationsRequest.java` を新規作成
  ```java
  @Data
  public class UpdatePlayerOrganizationsRequest {
      private List<Long> organizationIds;
  }
  ```
- Controller の引数を `@RequestBody UpdatePlayerOrganizationsRequest request` に変更

**修正後のあるべき姿:**
- リクエストボディが型安全に検証される

## 3. 技術設計

### 3.1 API変更

| エンドポイント | 変更内容 |
|--------------|---------|
| `GET /api/organizations/players/{playerId}` | 自ユーザー検証追加（振る舞い変更） |
| `PUT /api/organizations/players/{playerId}` | 自ユーザー検証追加 + リクエストボディ型変更 |
| `GET /api/practice-sessions` | **削除** |
| `GET /api/practice-sessions/upcoming` | **削除** |
| `GET /api/practice-sessions/range` | **削除** |
| `GET /api/practice-sessions/dates` | 団体フィルタ追加（振る舞い変更） |
| `GET /api/practice-sessions/next-participation` | 団体フィルタ追加（振る舞い変更） |

### 3.2 DB変更

なし

### 3.3 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `api/practices.js` | `getAll`, `getUpcoming` メソッド削除 |
| `pages/practice/PracticeList.jsx` | `getOrgUnderlineColor` を `orgMap[id]?.color` に変更 |

### 3.4 バックエンド変更

| レイヤー | ファイル | 変更内容 |
|---------|---------|---------|
| DTO | `UpdatePlayerOrganizationsRequest.java` | **新規作成** |
| Controller | `OrganizationController.java` | 認可チェック追加、リクエスト型変更 |
| Controller | `PracticeSessionController.java` | 3エンドポイント削除、`getSessionDates` に `HttpServletRequest` 追加 |
| Service | `PracticeSessionService.java` | `findAllSessions`/`findAllSessionsByPlayer`/`findSessionsInRange`/`findUpcomingSessions` 削除、`findNextParticipation` 修正、`findSessionDatesByPlayer` 追加 |
| Repository | `PracticeSessionRepository.java` | 不要メソッド削除（`findAllOrderBySessionDateDesc`, `findByOrganizationIdInOrderBySessionDateDesc`, `findUpcomingSessions`, `findByDateRange`, `findByOrganizationIdInAndDateRange`） |

## 4. 影響範囲

### 影響を受ける既存機能
- **ホーム画面:** `findNextParticipation` の団体フィルタ追加により、参加団体の練習のみ表示される（改善）
- **試合結果ビュー:** `getDates` の団体フィルタ追加により、参加団体の日付のみ表示される（改善）
- **カレンダー表示:** アンダーライン色がDB値に基づくようになる（`org.color` の値が適切であることが前提）

### 破壊的変更の有無
- **API削除:** `GET /api/practice-sessions`, `GET /api/practice-sessions/upcoming`, `GET /api/practice-sessions/range` の3エンドポイントが削除される。フロントエンドからは未使用であることを確認済み
- **認可追加:** `GET/PUT /api/organizations/players/{playerId}` で認可チェックが追加される。未ログインや他人のIDを指定した場合にエラーとなる（セキュリティ改善）
- **リクエスト形式変更:** `PUT /api/organizations/players/{playerId}` のリクエストボディが `{ "organizationIds": [...] }` のまま（フロントエンドの変更不要）

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 未使用エンドポイントは削除 | フィルタを追加して残すより、攻撃面を減らす方が安全。必要になった際に団体フィルタ付きで再実装すればよい |
| `@RequireRole` を追加しない（項目1,2） | `@RequireRole(PLAYER)` 以上を付けると認可の二重管理になる。`currentUserId` ベースの自己一致チェックの方がシンプル |
| `findUpcomingSessions` は項目3と合わせて削除 | Controller エンドポイント削除後、唯一の呼び出し元である `findNextParticipation` でも団体フィルタ版に置換されるため不要 |
| `org.color` を直接使用 | ハードコードは新団体追加時に修正漏れリスクがあり、DB定義のカラーを使うのが自然 |
