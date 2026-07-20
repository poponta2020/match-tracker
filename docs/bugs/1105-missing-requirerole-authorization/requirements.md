---
status: approved
issue: 1105
---
# バグ改修要件: 未認証で叩ける状態変更エンドポイント6件に認可を追加する

## 再現手順

いずれも**認証ヘッダーを一切送らずに**再現する（`X-User-Role` の詐称すら不要）。

```bash
# 1. 任意アカウントのパスワードを書き換え → ログイン → SUPER_ADMIN 奪取
curl -X PUT  $API/api/players/1 -H 'Content-Type: application/json' -d '{"password":"hacked123"}'
curl -X POST $API/api/players/login -H 'Content-Type: application/json' -d '{"name":"<対象名>","password":"hacked123"}'

# 2. 任意の試合記録を物理削除
curl -X DELETE $API/api/matches/1

# 3. 他人の LINE 連携コードを窃取
curl -X POST $API/api/line/PLAYER/enable -H 'Content-Type: application/json' -d '{"playerId":1}'

# 4-6. 会場マスタ / プロフィール / 抜け番活動の改変
curl -X DELETE $API/api/venues/1
curl -X DELETE $API/api/player-profiles/1
curl -X PUT    $API/api/bye-activities/1 -H 'Content-Type: application/json' -d '{"activityType":"OTHER","freeText":"x"}'
```

**期待される動作**: いずれも 403 Forbidden で拒否される
**実際の動作**: すべて成功し、データが変更される

## 根本原因

`interceptor/RoleCheckInterceptor.java:42-45` は `@RequireRole` が無いハンドラを**素通り**させる（`extractAndSetUserId(request, false)` を呼ぶだけで、認証を要求しない）。

```java
// @RequireRole アノテーションがない場合はユーザーIDだけ設定してスキップ
if (requireRole == null) {
    extractAndSetUserId(request, false);
    return true;
}
```

したがって「注釈の付け忘れ」が**そのまま未認証公開**になる（fail-open）。加えて `SecurityFilterChain` / `@EnableWebSecurity` / spring-boot-starter-security はリポジトリ全体に存在せず（grep 0 件）、**このインターセプタが唯一の認可層**なので、他の防御が重なることはない。

対象6件はいずれも `@RequireRole` が無く、かつハンドラ／サービス層に代替の検証も無い。特に 1〜5 は**ハンドラがサービスへ呼び出し元識別子を一切渡していない**ため、サービス側が原理的に認可し得ない。

| # | 箇所 | 現状 |
|---|---|---|
| 1 | `controller/PlayerController.java:149` `updatePlayer` | 注釈なし。`PlayerUpdateRequest` に `password` があり、`login()` は平文比較（`service/PlayerService.java:341`） |
| 2 | `controller/MatchController.java:320` `deleteMatch` | 注釈なし。`MatchService.deleteMatch:770` は `deleteById` の**物理削除** |
| 3 | `controller/LineUserController.java:44,74,90,148` | 注釈なし。対象 `playerId` をボディ／DTO から受け取り `currentUserId` を参照しない。`enable`/`reissue-code` は `linkingCode` をレスポンスで返す |
| 4 | `controller/VenueController.java:55,68,82` | 注釈なし・検証なし |
| 5 | `controller/PlayerProfileController.java:79,93,108` | 注釈なし・検証なし |
| 6 | `controller/ByeActivityController.java:106` `update` | 注釈なし。**本人確認が未配線**（下記） |

### 6 は「実装されているつもりで存在しない」ケース

- controller javadoc: 「本人の場合はplayerIdの一致を検証」
- `service/ByeActivityService.java:205-207` javadoc: 「Controller側で権限制御」
- 実際の `update()`（同 210-225）は `userId` を `setUpdatedBy` に使うだけで所有者チェック無し
- 決定的証拠: `getPlayerIdForActivity`（同 232、javadoc に「Controller側の権限チェック用」）は**リポジトリ全体で呼び出し 0 件のデッドコード**

双方の javadoc が互いに相手側で検証していると述べている状態。

## 修正方針

各エンドポイントに `@RequireRole` を付与し、本人性が絡むものはハンドラ／サービス層に所有者チェックを追加する。**ロールの割り当てはフロントエンドの既存ルートガードと既存の同等エンドポイントに合わせる**（新しい権限モデルを発明しない）。

| # | 対象 | 付与するロール | 追加する検証 |
|---|---|---|---|
| 1 | `PUT /api/players/{id}` | SUPER_ADMIN / ADMIN / PLAYER | **本人 or SUPER_ADMIN**。`OrganizationController.checkPlayerAccess` と同型 |
| 2 | `DELETE /api/matches/{id}` | SUPER_ADMIN / ADMIN / PLAYER | `deleteMatch` に `currentUserId`/`currentUserRole` を渡し、`updateMatch:661-668` の PLAYER 判定（自分の試合 or `validatePlayerCanWriteMatch`）を再利用 |
| 3 | `/api/line/**` 4件 | SUPER_ADMIN / ADMIN / PLAYER | **本人 or SUPER_ADMIN**（`playerId` == `currentUserId`） |
| 4 | `/api/venues` 3件 | **SUPER_ADMIN** | 不要（FE ルートガードと一致） |
| 5 | `/api/player-profiles` 3件 | **SUPER_ADMIN** | 不要 |
| 6 | `PUT /api/bye-activities/{id}` | SUPER_ADMIN / ADMIN / PLAYER | **本人 or ADMIN+**。既存の `getPlayerIdForActivity` を呼んで配線する |

### フロントエンドは無改修で動く（根拠）

- `karuta-tracker-ui/src/api/client.js:20-33` の axios リクエストインターセプタが、`localStorage` に `currentPlayer` があれば**全リクエストに** `X-User-Role` と `X-User-Id` を付与する
- 対象6件はいずれもログイン後の画面からのみ呼ばれ、**ログイン前に到達する経路は無い**（`POST /api/players/login` 自体は認証不要のまま維持する）
- ロール割り当ては既存 FE ガードと一致する: `/players/:id/edit` と `/venues/*` は `RoleProtectedPage requiredRole="SUPER_ADMIN"`（`App.jsx:127,129,137,138`）
- 本人経路も維持される: `ProfileEdit.jsx:165` は `playerAPI.update(currentPlayer.id, ...)`、`LineSettings`/`NotificationSettings` は常に `currentPlayer.id` を渡す
- `ForbiddenException` は 403（`GlobalExceptionHandler:120-126`）。FE のレスポンスインターセプタが自動ログアウトするのは 401 のみなので、403 でログアウトループは起きない

## Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | 上記6件すべてについて、**認証ヘッダー無しのリクエストが 403** になる | auto-test（回帰テスト） |
| AC-2 | 既存テスト・lint・typecheck がすべて成功する（デグレードなし） | auto-test |
| AC-3 | `PUT /api/players/{id}`: 本人は自分を更新でき、**他人の更新は 403**。SUPER_ADMIN は他人も更新できる | auto-test |
| AC-4 | `DELETE /api/matches/{id}`: PLAYER は自分が参加した試合を削除でき、**無関係な試合の削除は 403**。ADMIN+ は削除できる | auto-test |
| AC-5 | `/api/line/**` 4件: **他人の `playerId` を指定すると 403**（連携コードが返らない） | auto-test |
| AC-6 | `PUT /api/bye-activities/{id}`: **他人の記録の更新は 403**。本人と ADMIN+ は更新できる | auto-test |
| AC-7 | フロントエンドの既存操作（本人のプロフィール編集・LINE設定・SUPER_ADMIN の会場/選手編集）が引き続き成功する | verify |

## Non-goals

以下は**本PRで扱わない**（いずれも既知の別課題）:

- **認証方式そのものの是正**: `X-User-Role` / `X-User-Id` の自己申告をロール・本人性の根拠にしている点。本PRは6件を「既存のベースラインと同じ水準」に引き上げるのが目的
- **パスワードの平文保存**（`PlayerService.java:341`、`audit_third_party_club_deployment` に記録済み）。認可を追加することが本件の完全な修正であり、ハッシュ化は独立した課題
- **`@RequireRole` 付け忘れの構造的な fail-closed 化**（起動時検証等）。Issue #1105 末尾に提案として記載済み
- **`/api/player-profiles` の削除**: FE から未使用だが、削除は「未使用であることの確証」が別途必要なため対象外（今回は SUPER_ADMIN で塞ぐのみ）
- **`ProfileEdit` が本人に段位・級位の自己申告を許している点**: 本人ゲートを入れると自己申告が追認される形になるが、これは既存の意図された製品挙動（フィールド構成は変更しない）
- 周辺コードのリファクタ全般

## 修正ステップ

3ファイル以上・複数レイヤー跨ぎのため分解する。**すべて直列**で進める（単一の根本原因に密結合で、並行の利得が小さいため）。

- [x] ステップ1: 回帰テストを先に書き、修正前に fail することを確認する（対応AC: AC-1〜AC-6 / 変更領域: `karuta-tracker/src/test/` / 依存: なし / 完了条件: 6件すべてで「ヘッダー無し→403」が fail する）
- [x] ステップ2: 注釈のみで済む4・5を修正（対応AC: AC-1 / 変更領域: `VenueController`, `PlayerProfileController` / 依存: ステップ1 / 完了条件: 該当テスト green）
- [x] ステップ3: 本人性チェックを伴う1・3・6を修正（対応AC: AC-1,3,5,6 / 変更領域: `PlayerController`, `LineUserController`, `ByeActivityController`+`ByeActivityService` / 依存: ステップ1 / 完了条件: 該当テスト green）
- [x] ステップ4: 2を修正（サービス層のシグネチャ変更を伴う）（対応AC: AC-1,4 / 変更領域: `MatchController`, `MatchService` / 依存: ステップ1 / 完了条件: 該当テスト green）
- [x] ステップ5: 全体のテスト・lint・typecheck を green にし、FE の主要操作を verify（対応AC: AC-2, AC-7 / 依存: ステップ2〜4）

## 影響範囲

**変更するファイル（BE のみ）**
- `controller/PlayerController.java` / `MatchController.java` / `LineUserController.java` / `VenueController.java` / `PlayerProfileController.java` / `ByeActivityController.java`
- `service/MatchService.java`（`deleteMatch` のシグネチャに `currentUserId`/`currentUserRole` を追加）
- `service/ByeActivityService.java`（`update` に所有者チェックを追加、または controller から `getPlayerIdForActivity` を呼ぶ）

**影響を受ける既存機能（FE 改修は不要・動作維持を AC-7 で確認）**
- 本人のプロフィール編集（`ProfileEdit.jsx`）／SUPER_ADMIN の選手編集（`PlayerEdit.jsx`）
- LINE 通知設定（`LineSettings.jsx`, `NotificationSettings.jsx`）
- 会場の登録・編集（`VenueForm.jsx`）
- 試合詳細からの削除（`MatchDetail.jsx:129`）
- 抜け番活動の編集（`BulkResultInput.jsx` 経路）

**注意点**
- `@RequireRole` を付けると `X-User-Id` が**必須**になる（`extractAndSetUserId(request, true)`）。ヘッダーを送らない既存の BE テストは 403 になるため、テスト側の更新が必要
- `MatchService.deleteMatch` のシグネチャ変更は呼び出し元（テスト含む）に波及する
- DB スキーマ変更は無し（本番 DB 適用は不要）
