---
status: completed
---
# 選手情報の一括編集 (player-bulk-edit) 実装手順書

## 実装タスク

### タスク1: バックエンド一括更新API
- [x] 完了
- **概要:** 複数選手の `players` 列（性別・級・段位・かるた会）更新と、所属練習会の追加（追加のみ）をトランザクションで行う一括更新エンドポイントを実装する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerBulkUpdateRequest.java` — 新規。`List<Item> updates`、`Item{ Long playerId, Player.Gender gender, Player.KyuRank kyuRank, Player.DanRank danRank, String karutaClub, List<Long> addOrganizationIds }`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PlayerService.java` — `@Transactional bulkUpdate(PlayerBulkUpdateRequest)` を追加。各選手の `players` 列を更新し、`addOrganizationIds` を既存所属とマージして不足分のみ `PlayerOrganization` を保存（`(player_id, organization_id)` ユニーク制約で重複防止）。級↔段位の整合は単体更新と同一ルール
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PlayerController.java` — `PUT /bulk`（`@RequireRole(Role.ADMIN)`）を追加。団体スコープ検証はしない
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PlayerOrganizationRepository.java` — 既存 `findByPlayerIdIn` 等を利用（不足があればメソッド追加）
- **依存タスク:** なし
- **対応Issue:** #884

### タスク2: バックエンドのテスト
- [x] 完了
- **概要:** 一括更新サービス/エンドポイントの単体・結合テストを追加（Jacoco 最低カバレッジ60%維持）。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PlayerServiceTest.java`（または新規テストクラス）— bulkUpdate の正常系（複数選手の属性更新＋org追加マージ）、org重複追加が二重登録されないこと、級↔段位連動、空 updates 等の境界
  - 必要に応じて Controller テスト（`@RequireRole(ADMIN)` のロール制御）
- **依存タスク:** タスク1（#884）
- **対応Issue:** #885

### タスク3: フロント APIクライアント
- [ ] 完了
- **概要:** 一括更新APIを呼ぶクライアントメソッドを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/players.js` — `bulkUpdate: (updates) => apiClient.put('/players/bulk', { updates })` を追加
- **依存タスク:** タスク1（#884、APIコントラクト確定後）
- **対応Issue:** #886

### タスク4: 選手一覧（PlayerList）の選択UI・団体フィルタ・導線
- [ ] 完了
- **概要:** 選手一覧にチェックボックス選択・団体フィルタ（すべて/団体別/無所属）・「一括編集」導線を追加し、既存の招待用団体セレクトを表示フィルタと統合する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/players/PlayerList.jsx`
    - 選択状態 `selectedIds`（Set）、行チェックボックス、全選択/全解除、選択人数バッジ
    - 団体フィルタ（`organizationIds` で判定、空＝無所属）。初期値 ADMIN=`currentPlayer.adminOrganizationId`、SUPER_ADMIN=すべて（`useAuth()` から `currentPlayer`/`isAdmin`/`isSuperAdmin`）
    - 既存 `selectedOrgId`（招待用）と団体セレクトを統合。具体的団体選択時のみ招待ボタン有効、「すべて/無所属」時は無効（招待デグレに注意）
    - 「一括編集（N人）」ボタン → `navigate('/players/bulk-edit', { state: { players: 選択選手配列 } })`
- **依存タスク:** なし（ただしタスク5の遷移先が必要）
- **対応Issue:** #887

### タスク5: 一括編集画面（PlayerBulkEdit）の新規作成・ルーティング
- [ ] 完了
- **概要:** 選択選手を行で並べた編集テーブルと一括設定エリアを持つ新規ページを作成し、保存で一括更新APIを呼ぶ。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/players/PlayerBulkEdit.jsx` — 新規
    - router state から選手配列を受け取り（無ければ一覧へリダイレクト）、行ごとの編集値（性別/級/段位/かるた会/追加予定orgIds）を `useState` で保持
    - 上部一括設定: 性別/かるた会の「全員に適用」、級の「全員に適用」＋「全員をE級に」、所属練習会の「全員に北大を追加」「全員にわすらを追加」
    - 行ごと編集: 性別・級（段位自動連動。A級のみ段位手動）・かるた会・練習会（現在所属表示＋北大/わすら追加・追加分の取り消し）
    - 北大/わすらの団体IDは `organizationAPI.getAll()` の名前一致で解決（無ければ該当ボタン非活性）
    - 保存: 確認ダイアログ（「N人に適用します」＋変更概要）→ `playerAPI.bulkUpdate(updates)` → 成功で一覧へ戻る
    - 級↔段位連動は `PlayerEdit.jsx` の `handleKyuRankChange` 相当を踏襲（共通ユーティリティ化を検討）
  - `karuta-tracker-ui/src/App.jsx`（またはルーティング定義ファイル）— `/players/bulk-edit` ルートを追加
- **依存タスク:** タスク3（#886、bulkUpdate）、タスク4（#887、選手の受け渡し）
- **対応Issue:** #888

### タスク6: フロントエンドのテスト
- [ ] 完了
- **概要:** 選択UI・団体フィルタ・一括編集画面の主要挙動のテストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/players/PlayerList.*.test.jsx` — 団体フィルタ（無所属/自会）、選択と一括編集導線、招待ボタンの有効/無効
  - `karuta-tracker-ui/src/pages/players/PlayerBulkEdit.test.jsx` — 一括設定（全員E級/北大追加）、行ごと編集、確認ダイアログ→保存ペイロード、state無し時のリダイレクト
- **依存タスク:** タスク4（#887）, タスク5（#888）
- **対応Issue:** #889

### タスク7: ドキュメント更新
- [ ] 完了
- **概要:** 実装内容を仕様・画面一覧・設計書へ反映（CLAUDE.md のドキュメント更新ルール）。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 一括編集機能の仕様
  - `docs/SCREEN_LIST.md` — 一括編集画面（/players/bulk-edit）の追加
  - `docs/DESIGN.md` — API（PUT /players/bulk）・画面構成・フィルタ追加の設計
- **依存タスク:** タスク1〜6（#884〜#889）
- **対応Issue:** #890

## 実装順序
1. タスク1（バックエンド一括更新API・依存なし）
2. タスク2（バックエンドテスト・タスク1依存）
3. タスク3（フロントAPIクライアント・タスク1依存）
4. タスク4（PlayerList 選択UI・団体フィルタ・依存なし）
5. タスク5（PlayerBulkEdit・タスク3/4依存）
6. タスク6（フロントテスト・タスク4/5依存）
7. タスク7（ドキュメント更新・全タスク依存）

> 補足: タスク1とタスク4は並行着手可能。DBスキーマ変更なし＝本番マイグレーション不要。
