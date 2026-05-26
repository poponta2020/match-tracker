---
status: completed
---
# 対戦組み合わせ機能のPLAYER開放と空状態CTA 改修実装手順書

## 実装タスク

### タスク1: MatchPairingController のPLAYER開放とスコープバリデーション拡張
- [x] 完了
- **対応Issue:** #823
- **概要:** 対戦組み合わせの作成・編集系API（POST, POST /batch, POST /auto-match, PUT /{id}/player）にPLAYERロールを許可し、ADMINのみだった自団体スコープ検証をPLAYERにも適用する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java`
    - 以下4箇所の `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN})` を `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})` に変更:
      - `create()`（POST `/api/match-pairings`）
      - `createBatch()`（POST `/api/match-pairings/batch`）
      - `autoMatch()`（POST `/api/match-pairings/auto-match`）
      - `updatePlayer()`（PUT `/api/match-pairings/{id}/player`）
    - `validateAdminScopeByDate(LocalDate, HttpServletRequest)` を `validateScopeByDate` にリネームし、以下の挙動に拡張:
      - `SUPER_ADMIN` → 早期 return（スコープ強制なし）
      - `ADMIN` → 既存通り `adminOrganizationId` で照合
      - `PLAYER` → `organizationService.getPlayerOrganizationIds(currentUserId)` で所属団体IDリストを取得し、対象日付の練習セッションが所属団体のいずれかに属していなければ `ForbiddenException`
      - それ以外のロール → `ForbiddenException`
    - `validateAdminScopeByPairingId(Long, HttpServletRequest)` を `validateScopeByPairingId` にリネームし、同様に拡張（対象ペアリングの組織IDを `matchPairingService.getOrganizationIdByPairingId(id)` で取得して照合）
    - `createBatch()` / `autoMatch()` 内で `(Long) httpRequest.getAttribute("adminOrganizationId")` を取得している箇所は、PLAYER でも適切な組織IDが取得できるよう以下のいずれかで対応:
      - 案A（推奨）: 対象練習日の `PracticeSession.organizationId` をServiceで取得して引数に渡す
      - 案B: リクエスト属性に `currentUserOrganizationId`（ロール非依存のプライマリ組織ID）を別途設定するinterceptor側の改修
      - 実装時に既存パターンと整合する案を選択し、不可能なら認識合わせ
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java`
    - 必要に応じて `getOrganizationIdByDate(LocalDate)` 等のヘルパーメソッドを追加（案A採用時）
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

### タスク2: フロントエンドのルートを PLAYER 以上に開放
- [x] 完了
- **対応Issue:** #824
- **概要:** `/pairings`, `/pairings/summary`, `/matches/bulk-input/:sessionId` の3ルートを `RoleProtectedPage requiredRole="ADMIN"` から `ProtectedPage` に変更し、ログイン済みなら誰でもアクセス可能にする。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/App.jsx`
    - 行94: `<Route path="/matches/bulk-input/:sessionId" element={<RoleProtectedPage requiredRole="ADMIN"><BulkResultInput /></RoleProtectedPage>} />` → `<Route path="/matches/bulk-input/:sessionId" element={<ProtectedPage><BulkResultInput /></ProtectedPage>} />`
    - 行111: `<Route path="/pairings" element={<RoleProtectedPage requiredRole="ADMIN"><PairingGenerator /></RoleProtectedPage>} />` → `<Route path="/pairings" element={<ProtectedPage><PairingGenerator /></ProtectedPage>} />`
    - 行112: `<Route path="/pairings/summary" element={<RoleProtectedPage requiredRole="ADMIN"><PairingSummary /></RoleProtectedPage>} />` → `<Route path="/pairings/summary" element={<ProtectedPage><PairingSummary /></ProtectedPage>} />`
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

### タスク3: 設定メニュー「組み合わせ作成」を全員表示
- [x] 完了
- **対応Issue:** #825
- **概要:** 設定ページの「組み合わせ作成」メニュー項目を、管理者のみ → 全ユーザー表示に変更する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx`
    - 行36付近: `{ label: '組み合わせ作成', icon: Shuffle, path: '/pairings', visible: isAdmin() }` の `visible: isAdmin()` を `visible: true` に変更
    - 該当行以外で `isAdmin()` のインポートが不要にならないか確認（他の項目で使われている場合は据え置き）
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

### タスク4: MatchResultsView の権限解放と空状態CTA追加
- [x] 完了
- **対応Issue:** #826
- **概要:** `/matches/results` 画面で「結果を一括入力」ボタンのロール判定を撤去し、現在表示中の試合番号のペアリングが0件のときは代わりに「対戦組み合わせを作成」ボタン（遷移先 `/pairings?date=YYYY-MM-DD`）を表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx`
    - 行5: `import { isAdmin, isSuperAdmin } from '../../utils/auth';` のうち、本ファイル内で他に利用していなければ削除（要確認）
    - 行637-645付近の「結果を一括入力」ボタン部分を、以下の分岐に書き換え:
      - `session` が存在する場合のみ表示
      - `currentPairings.length === 0` のとき → 「対戦組み合わせを作成」ボタン（アイコン `Shuffle`、`navigate('/pairings?date=' + selectedDate)` で遷移）
      - `currentPairings.length > 0` のとき → 既存通り「結果を一括入力」ボタン（`navigate('/matches/bulk-input/' + session.id)`）
    - 必要に応じて `Shuffle` アイコンを `lucide-react` から import（既存の import 行 [MatchResultsView.jsx:6](../../../karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx#L6) に追加）
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

### タスク5: ドキュメント更新
- [x] 完了
- **対応Issue:** #827
- **概要:** 仕様書・設計書・画面一覧に対戦組み合わせ機能の権限変更を反映する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 対戦組み合わせAPIの権限一覧で、POST `/api/match-pairings`, POST `/api/match-pairings/batch`, POST `/api/match-pairings/auto-match`, PUT `/api/match-pairings/{id}/player` の権限を「ADMIN+」→「PLAYER+」に修正。`/matches/bulk-input/:sessionId` 画面の権限も「ADMIN+」→「PLAYER+」に修正
  - `docs/DESIGN.md` — 権限設計の節（あれば）を更新。スコープ強制の挙動を「ADMIN: 自団体、PLAYER: 所属団体（複数可）、SUPER_ADMIN: 制限なし」に明記
  - `docs/SCREEN_LIST.md` — 「組み合わせ作成」「結果一括入力」画面のアクセス権限を「ADMIN+」→「PLAYER+」に修正
- **依存タスク:** タスク1〜4（コード変更が確定してからドキュメントに反映）
- **対応Issue:** （Issue作成後に記入）

## 実装順序

1. **タスク1** — バックエンドのAPI権限解放とスコープバリデーション拡張（依存なし、最優先）
2. **タスク2** — フロントエンドのルート開放（タスク1と並列可能）
3. **タスク3** — 設定メニュー表示開放（依存なし、並列可能）
4. **タスク4** — MatchResultsView の改修（依存なし、並列可能）
5. **タスク5** — ドキュメント更新（タスク1〜4 確定後）

※ タスク1〜4 は互いに独立しているため、並列実装が可能。リリース時はバックエンド（タスク1）とフロントエンド（タスク2〜4）を同時にデプロイすることで、PLAYER 開放のタイミングを揃える。

## 実装後の追加対応

- **Home.jsx の「組み合わせを作成」ボタン解放**: 要件定義書では明示されていなかったが、実装中にユーザー認識合わせを行い、当日かつ ADMIN のみ表示だったホーム画面のボタン（`Home.jsx` 行282〜290）も PLAYER 含む全ロール表示に変更した。設定メニュー（タスク3）・MatchResultsView の空状態CTA（タスク4）と挙動を揃え、UX の不整合を解消する目的。タスク5 のドキュメント更新と同コミットで対応。

## レビュー対応（review round 1 / 2）

### review round 1（CRITICAL 2 / WARNING 3）
- BulkResultInput の内部ロールリダイレクトを撤去（PLAYER 開放）
- MatchPairingService.create / createBatch / updatePlayer に「対象セッション参加者のみ」選手ID検証を追加
- `resolveOrganizationIdForScopedWrite` は同日複数所属団体該当時 ForbiddenException で拒否（曖昧性回避）
- PairingGenerator の「全削除」「結果込みリセット」を `isAdmin() || isSuperAdmin()` ガード
- MatchPairingControllerTest に PLAYER 自所属団体成功・所属外403・複数団体曖昧性403 を追加

### review round 2（CRITICAL 2 / WARNING 1）
- **PLAYER 一括結果入力の認可拡張**: `POST /api/matches/detailed`（`MatchController.createMatchDetailed`）と `PUT /api/matches/{id}/detailed`（`MatchService.updateMatch`）の PLAYER 認可を「自分の試合のみ」から「自分の試合 OR 所属団体セッション」へ拡張。`POST /api/bye-activities/batch`（`ByeActivityController.createBatch`）も PLAYER 開放し、`validateScopeByDate` を新設して所属団体スコープを強制。
- **既知の制限（CRITICAL 2 を将来課題化）**: PLAYER が同日に複数所属団体のセッションを持つ場合、フロントから `organizationId` を渡していないため `resolveOrganizationIdForScopedWrite` が ForbiddenException で拒否する（安全側フォールバック）。複数団体運用の同日重複は実発生がレアと判断し本 PR では据え置き。完全対応には PairingGenerator / BulkResultInput / MatchResultsView から `organizationId` を一貫して伝播させるか、書き込み API のスコープキーを `sessionId` に変える設計変更が必要。別 Issue 化推奨。
- **Service 層直接テスト追加**: `MatchPairingServiceTest` で `create` / `createBatch` / `updatePlayer` の organizationId 指定時の所属外ID拒否・待機者ID拒否・updatePlayer の所属外 newPlayerId 拒否・organizationId=null の既存経路維持を直接担保。

### review round 3（CRITICAL 2）
- **Match 書き込み経路の厳密化**: `MatchService.createMatch` / `updateMatch` に `validatePlayerCanWriteMatch` を追加し、PLAYER 一括結果入力で `player1Id` / `player2Id` の両方が所属団体セッション参加者であることを検証。同日複数所属団体時は403で安全側拒否。`MatchController.createMatchDetailed` の認可は `createdBy` 一致のみに簡素化し、選手スコープ検証は Service 側に集約。
- **ByeActivity 書き込み経路の厳密化**: `ByeActivityService.createBatch(... organizationId)` を新設し、組織スコープ実行時は対象セッション参加者の playerId 範囲でのみ論理削除＋ items の playerId 検証。`ByeActivityController.createBatch` で `resolveOrganizationIdForScopedWrite` ヘルパーを追加し、同日複数所属団体時は403拒否。SUPER_ADMIN 経路（organizationId == null）は従来通り全レコード論理削除。

### review round 4（CRITICAL 1 / WARNING 1）
- **ペアリングID団体判定の厳密化**: `MatchPairingService.getOrganizationIdByPairingId` を「両プレイヤーが同一セッションに参加している場合のみ organizationId を返す」設計に変更。0件または複数件の場合は null を返し、`validateScopeByPairingId` の null チェックにより ADMIN/PLAYER で ForbiddenException となる。これにより、片方の選手だけが別団体セッションにも参加しているケースで別団体のペアリングを差し替えできる経路を塞いだ。
- **既知の制限（WARNING を将来課題化）**: ペアリング書き込み・抜け番活動一括の選手スコープ検証は「セッション全体の参加者」しか見ておらず、`PracticeParticipant.matchNumber` や参加ステータス（WON / PENDING / CANCELLED / WAITLISTED）までは見ていない。UI は試合番号・ステータスで絞っているが、PLAYER が直接 API で別試合番号・対象外ステータスの選手IDを渡せば書き込みに含められる。試合番号＋ステータスを含む厳密スコープは後続課題として別 Issue で対応予定。
