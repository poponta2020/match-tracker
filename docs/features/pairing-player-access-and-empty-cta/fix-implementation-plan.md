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
- [ ] 完了
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
- [ ] 完了
- **対応Issue:** #825
- **概要:** 設定ページの「組み合わせ作成」メニュー項目を、管理者のみ → 全ユーザー表示に変更する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx`
    - 行36付近: `{ label: '組み合わせ作成', icon: Shuffle, path: '/pairings', visible: isAdmin() }` の `visible: isAdmin()` を `visible: true` に変更
    - 該当行以外で `isAdmin()` のインポートが不要にならないか確認（他の項目で使われている場合は据え置き）
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

### タスク4: MatchResultsView の権限解放と空状態CTA追加
- [ ] 完了
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
- [ ] 完了
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
