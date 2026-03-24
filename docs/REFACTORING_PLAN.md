# リファクタリング計画

## 概要
2026-03-24のコードベース全体調査に基づくリファクタリング計画。
フロントエンドの見た目は変更しない。

---

## Phase 1: エンティティ・設定の整理（低リスク）

### 1.1 Lombokアノテーションの統一
- [x] Venue.java: `@Data` → `@Getter` / `@Setter`（JPAエンティティでの@Data問題回避）
- [x] MatchPairing.java: `@Data` → `@Getter` / `@Setter`
- [x] VenueMatchSchedule.java: `@Data` → `@Getter` / `@Setter`

### 1.2 タイムスタンプ管理の統一
- [x] Venue.java: `@CreationTimestamp`/`@UpdateTimestamp` → `@PrePersist`/`@PreUpdate`方式に統一

### 1.3 build.gradle整理
- [x] 未使用の`spring-boot-starter-thymeleaf`を削除

### 1.4 GlobalExceptionHandlerの重複排除
- [x] 共通処理を`buildResponse()`メソッドに抽出

---

## Phase 2: バックエンドサービス層リファクタリング（中リスク）

### 2.1 PracticeSessionService分割
- [x] `PracticeParticipantService`を新設し、参加者管理メソッドを移動
- [x] PracticeSessionServiceから参加者関連メソッドを委譲に変更
- [x] PracticeSessionController / HomeControllerを更新
- [x] enrichSession内の重複ロジックを`enrichDtoWithMatchDetails()`に統合

### 2.2 MatchService: 重複ロジック抽出
- [x] `collectPlayerNames()`で選手名取得を共通化
- [x] `determineResult()`で勝敗判定を共通化
- [x] ハードコード文字列を定数化: `RESULT_WIN`, `RESULT_LOSE`, `RESULT_DRAW`

### 2.3 MatchPairingService: 重複排除
- [x] `convertToDto()`を`convertToDtoWithCache()`に委譲（3回のDB呼び出しを1回に削減）
- [x] マジックナンバー定数化: `MATCH_HISTORY_DAYS`, `SAME_DAY_PENALTY_SCORE`, `INTERVAL_BASE_SCORE`

---

## Phase 3: フロントエンドリファクタリング（中リスク）

### 3.1 共通コンポーネント抽出
- [x] `YearMonthPicker`コンポーネントを`components/`に抽出
- [x] PracticeList.jsxを更新してインライン定義を削除

### 3.2 カスタムフック作成
- [x] `hooks/useFetch.js`を作成（StrictMode二重fetch防止＋AbortController対応）

### 3.3 API呼び出しの統一
- [x] MatchParticipantsEditModal: `apiClient.get('/players')` → `playerAPI.getAll()`
- [x] VenueForm: 3箇所 → `venueAPI.getById/update/create`
- [x] VenueList: 2箇所 → `venueAPI.getAll/delete`
- [x] PlayerEdit: `apiClient.put` → `playerAPI.updateRole()`
- [x] MatchResultsView: 2箇所 → `pairingAPI.getByDate/matchAPI.getByDate`
- [x] BulkResultInput: 2箇所 → `practiceAPI.getById/matchAPI.getByDate`
- [x] APIラッパーに不足メソッド追加: `matchAPI.getByDate`, `playerAPI.updateRole`, `pairingAPI.getByDate`のparams拡張

### 3.4 App.jsx整理
- [x] `ProtectedPage`ヘルパーコンポーネントでルート定義を簡潔化（377行→117行）
- [x] ルートをカテゴリ別にグループ化（試合/練習/抽選/組み合わせ/選手/会場/LINE）
- [x] 未実装の`/statistics`プレースホルダー除去

### 3.5 Home.jsx分割
- [x] `NavigationMenu`コンポーネントを`components/`に抽出
- [x] Home.jsxからナビゲーション関連のstate/ref/effectを除去

---

## 進捗状況

| Phase | ステータス | 備考 |
|-------|----------|------|
| Phase 1 | 完了 | エンティティ・設定の低リスク修正 |
| Phase 2 | 完了 | サービス層の分割・重複排除 |
| Phase 3 | 完了 | フロントエンド共通化・API統一 |

---

## 変更ファイル一覧

### バックエンド（新規）
- `service/PracticeParticipantService.java` — 参加者管理サービス（PracticeSessionServiceから分離）

### バックエンド（修正）
- `entity/Venue.java` — @Data→@Getter/@Setter, タイムスタンプ方式統一
- `entity/MatchPairing.java` — @Data→@Getter/@Setter
- `entity/VenueMatchSchedule.java` — @Data→@Getter/@Setter
- `build.gradle` — Thymeleaf依存削除
- `controller/GlobalExceptionHandler.java` — 共通メソッド抽出
- `service/PracticeSessionService.java` — 参加者ロジック分離、enrichment重複排除
- `service/MatchService.java` — 定数化、共通メソッド抽出
- `service/MatchPairingService.java` — convertToDto統合、マジックナンバー定数化
- `controller/PracticeSessionController.java` — PracticeParticipantService注入
- `controller/HomeController.java` — PracticeParticipantService注入

### フロントエンド（新規）
- `components/YearMonthPicker.jsx` — 年月選択ピッカー
- `components/NavigationMenu.jsx` — ナビゲーションメニュー
- `hooks/useFetch.js` — データフェッチカスタムフック

### フロントエンド（修正）
- `App.jsx` — ProtectedPageヘルパー導入、ルート整理
- `pages/Home.jsx` — NavigationMenu分離
- `pages/practice/PracticeList.jsx` — YearMonthPicker共通化
- `api/matches.js` — getByDate追加
- `api/players.js` — updateRole追加
- `api/pairings.js` — getByDateのparams拡張
- `components/MatchParticipantsEditModal.jsx` — playerAPI使用
- `pages/venues/VenueForm.jsx` — venueAPI使用
- `pages/venues/VenueList.jsx` — venueAPI使用
- `pages/players/PlayerEdit.jsx` — playerAPI.updateRole使用
- `pages/matches/MatchResultsView.jsx` — APIラッパー使用
- `pages/matches/BulkResultInput.jsx` — APIラッパー使用
