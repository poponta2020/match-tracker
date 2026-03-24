# リファクタリング計画書

## 概要
2026-03-24のコードベース全体調査に基づくリファクタリング。
フロントエンドの見た目は変更しない。本番（Render）への段階デプロイで安全に適用する。

---

## デプロイ履歴と教訓

### 障害1: 一括デプロイ（e501a3e）
- **事象**: 全変更を1コミットでpush → 本番で全APIが500エラー
- **原因**: `spring-boot-starter-thymeleaf`の削除。Spring Bootのエラーページレンダリングに使用されていた
- **対応**: revert（f9cf225）で復旧
- **教訓**: Thymeleafは削除しない。変更は小さいコミット単位でデプロイする

### 障害2: 再適用（72c0dbb）
- **事象**: Thymeleaf復元して再push → 同じエラー
- **原因**: Renderの起動に約3分かかり、その間のアクセスがタイムアウトしていた可能性が高い。ログ上の `NoResourceFoundException: No static resource .` はRenderヘルスチェックによる無害なエラー
- **対応**: revert（0e5b867）で復旧後、段階デプロイに方針変更

---

## 完了済み（本番デプロイ済み・動作確認済み）

### Step 1: フロントエンドリファクタリング（59f54fa）
- [x] `YearMonthPicker`をPracticeListからcomponents/に抽出
- [x] `useFetch`カスタムフック作成（hooks/useFetch.js）
- [x] 直接apiClient使用箇所をAPIラッパー経由に変更
  - MatchParticipantsEditModal → `playerAPI.getAll()`
  - VenueForm → `venueAPI.getById/update/create`
  - VenueList → `venueAPI.getAll/delete`
  - PlayerEdit → `playerAPI.updateRole()`
  - MatchResultsView → `pairingAPI.getByDate/matchAPI.getByDate`
  - BulkResultInput → `practiceAPI.getById/matchAPI.getByDate`
- [x] APIラッパーに不足メソッド追加（matchAPI.getByDate, playerAPI.updateRole, pairingAPI params拡張）

### Step 2: バックエンド低リスク変更（105dfe1）
- [x] Venue, MatchPairing, VenueMatchSchedule: `@Data` → `@Getter/@Setter`
- [x] Venue: タイムスタンプを`@PrePersist/@PreUpdate`方式に統一
- [x] MatchService: 勝敗文字列を`RESULT_WIN/LOSE/DRAW`定数化
- [x] MatchService: `collectPlayerNames()`/`determineResult()`共通メソッド抽出
- [x] MatchPairingService: マジックナンバー定数化（`MATCH_HISTORY_DAYS`等）
- [x] MatchPairingService: `convertToDto()` → `collectPlayerNames` + `convertToDtoWithCache`委譲

---

## 完了済み（デプロイ確認待ち）

### Step 3: PracticeParticipantService新設（cbcb8b7 + 6ea947c）
- [x] PracticeParticipantService作成（参加者CRUD・統計・参加登録）
- [x] PracticeSessionController: 参加者系エンドポイントの委譲先を変更
- [x] HomeController: 参加率取得の委譲先を変更

### Step 4: PracticeSessionServiceの軽量化（2026-03-25実施）
- [x] PracticeParticipantServiceに移動済みの未使用メソッド13個を完全削除
  - setMatchParticipants, getParticipants, registerParticipations, registerParticipationsBeforeDeadline, registerParticipationsAfterDeadline, isFreeRegistrationOpen, getPlayerParticipationsByMonth, getPlayerParticipationStatusByMonth, addParticipantToMatch, removeParticipantFromMatch, getParticipationRateTop3, getPlayerParticipationRate, computeAllParticipationRates
- [x] 不要フィールド削除（LotteryExecutionRepository, LotteryDeadlineHelper, EntityManager）
- [x] enrichSessionWithParticipants / enrichSessionsWithParticipants の重複ロジックを`enrichDtoWithMatchDetails()`に統合
  - リスト版で欠落していた `.role()` 設定も統合版で修正
- [x] 不要import整理
- [x] PracticeSessionServiceTestに不足モック追加（フィールド削除に伴うNPE対応）
- **効果**: PracticeSessionService 1091行 → 約480行（約56%削減）

---

## 未実施（今後の作業）

### Step 5: App.jsx整理
- [ ] `ProtectedPage`ヘルパーコンポーネントでルート定義を簡潔化
- [ ] ルートをカテゴリ別にグループ化
- [ ] 未実装の`/statistics`プレースホルダー除去

### Step 6: Home.jsx分割
- [ ] `NavigationMenu`コンポーネントをcomponents/に抽出
- [ ] Home.jsxからナビゲーション関連のstate/ref/effectを除去

### 見送り
- **GlobalExceptionHandler**: 全APIに影響するため変更しない
- **build.gradle Thymeleaf削除**: Renderのエラーページレンダリングに必要なため削除しない

---

## 既知のテスト問題

| テスト | 状態 | 備考 |
|---|---|---|
| MatchServiceTest | 13件失敗 | Step 4以前から存在。リファクタリングとは無関係 |
| MatchTrackerApplicationTests.contextLoads() | Docker未起動時に失敗 | 環境依存。CI（GitHub Actions）では正常 |

---

## 変更ファイル一覧

### フロントエンド（新規）
- `components/YearMonthPicker.jsx`
- `hooks/useFetch.js`

### フロントエンド（修正）
- `pages/practice/PracticeList.jsx` — YearMonthPicker共通化
- `api/matches.js` — getByDate追加
- `api/players.js` — updateRole追加
- `api/pairings.js` — getByDate params拡張
- `components/MatchParticipantsEditModal.jsx` — playerAPI使用
- `pages/venues/VenueForm.jsx` — venueAPI使用
- `pages/venues/VenueList.jsx` — venueAPI使用
- `pages/players/PlayerEdit.jsx` — playerAPI.updateRole使用
- `pages/matches/MatchResultsView.jsx` — APIラッパー使用
- `pages/matches/BulkResultInput.jsx` — APIラッパー使用

### バックエンド（新規）
- `service/PracticeParticipantService.java`

### バックエンド（修正）
- `entity/Venue.java` — @Getter/@Setter, @PrePersist
- `entity/MatchPairing.java` — @Getter/@Setter
- `entity/VenueMatchSchedule.java` — @Getter/@Setter
- `service/MatchService.java` — 定数化、共通メソッド抽出
- `service/MatchPairingService.java` — 定数化、convertToDto統合
- `controller/PracticeSessionController.java` — PracticeParticipantService委譲
- `controller/HomeController.java` — PracticeParticipantService委譲
- `service/PracticeSessionService.java` — 未使用メソッド13個削除、不要フィールド削除、enrichDtoWithMatchDetails統合

### テスト（修正）
- `service/PracticeSessionServiceTest.java` — 不足モック追加（Step 4のフィールド削除に対応）

---

## Render環境メモ

- 起動に約165秒（約3分）かかる（`lazy-initialization: true`）
- ヘルスチェックが`/`にアクセスし`NoResourceFoundException`が毎回出るが**無害**
- `spring-boot-starter-thymeleaf`はエラーページレンダリングに必要
- Dockerビルド: `./gradlew bootJar --no-daemon -x test`
