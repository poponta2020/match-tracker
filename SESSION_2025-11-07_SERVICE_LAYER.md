# 作業セッション記録: サービス層実装とテスト

**作業日**: 2025年11月7日
**セッション**: サービス層の実装とテスト
**開始時の状況**: リポジトリ層の統合テスト完了（37テスト成功）

---

## 今回実装した内容

### 1. サービス層の実装 ✅

#### DTOクラス (10ファイル)

**選手関連 (3ファイル)**
- `src/main/java/com/karuta/matchtracker/dto/PlayerDto.java`
  - 選手情報のDTO
  - エンティティからの変換メソッド
  - アクティブ判定メソッド

- `src/main/java/com/karuta/matchtracker/dto/PlayerCreateRequest.java`
  - 選手登録リクエスト
  - Jakarta Validationアノテーション
  - エンティティへの変換メソッド

- `src/main/java/com/karuta/matchtracker/dto/PlayerUpdateRequest.java`
  - 選手更新リクエスト
  - 部分更新対応（nullフィールドはスキップ）

**試合関連 (3ファイル)**
- `src/main/java/com/karuta/matchtracker/dto/MatchDto.java`
  - 試合結果のDTO
  - 選手名も含む（enrichされる）
  - 勝者判定メソッド

- `src/main/java/com/karuta/matchtracker/dto/MatchCreateRequest.java`
  - 試合登録リクエスト
  - player1Id < player2Idを自動保証
  - 点差のバリデーション (0-25)

- `src/main/java/com/karuta/matchtracker/dto/MatchStatisticsDto.java`
  - 試合統計情報のDTO
  - 勝率計算（小数点第1位まで）

**練習日関連 (2ファイル)**
- `src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java`
  - 練習日のDTO

- `src/main/java/com/karuta/matchtracker/dto/PracticeSessionCreateRequest.java`
  - 練習日登録リクエスト
  - totalMatchesのデフォルト値: 0

**プロフィール関連 (2ファイル)**
- `src/main/java/com/karuta/matchtracker/dto/PlayerProfileDto.java`
  - 選手プロフィールのDTO
  - 有効期限判定メソッド

- `src/main/java/com/karuta/matchtracker/dto/PlayerProfileCreateRequest.java`
  - プロフィール登録リクエスト
  - validToは自動設定（null = 無期限）

#### 例外クラス (2ファイル)

- `src/main/java/com/karuta/matchtracker/exception/ResourceNotFoundException.java`
  - リソースが見つからない場合の例外
  - 複数のコンストラクタ（id指定、フィールド指定）

- `src/main/java/com/karuta/matchtracker/exception/DuplicateResourceException.java`
  - リソースが重複している場合の例外
  - UNIQUE制約違反時に使用

#### サービスクラス (4ファイル)

**PlayerService** (`src/main/java/com/karuta/matchtracker/service/PlayerService.java`)
- 実装メソッド:
  - `findAllActivePlayers()` - 全アクティブ選手取得
  - `findById(Long id)` - ID検索
  - `findByName(String name)` - 名前検索（完全一致）
  - `searchByName(String nameFragment)` - 名前検索（部分一致）
  - `findByRole(Player.Role role)` - ロール別検索
  - `countActivePlayers()` - アクティブ選手数
  - `createPlayer(PlayerCreateRequest)` - 新規登録
  - `updatePlayer(Long id, PlayerUpdateRequest)` - 更新
  - `deletePlayer(Long id)` - 論理削除
  - `updateRole(Long id, Player.Role)` - ロール変更
- ビジネスロジック:
  - 名前の重複チェック
  - 削除済み選手の操作制限

**MatchService** (`src/main/java/com/karuta/matchtracker/service/MatchService.java`)
- 実装メソッド:
  - `findMatchesByDate(LocalDate date)` - 日付別試合取得
  - `existsMatchOnDate(LocalDate date)` - 試合存在確認
  - `findPlayerMatches(Long playerId)` - 選手の試合履歴
  - `findPlayerMatchesInPeriod(Long playerId, LocalDate start, LocalDate end)` - 期間検索
  - `findMatchesBetweenPlayers(Long p1, Long p2)` - 対戦履歴
  - `getPlayerStatistics(Long playerId)` - 統計情報取得
  - `createMatch(MatchCreateRequest)` - 試合登録
  - `updateMatch(Long id, Long winnerId, Integer score, Long updatedBy)` - 試合更新
  - `deleteMatch(Long id)` - 試合削除
- ビジネスロジック:
  - 選手名のエンリッチ（試合データに選手名を追加）
  - 勝者が対戦者のいずれかであることの確認
  - 自己対戦の防止
  - 勝率の計算

**PracticeSessionService** (`src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`)
- 実装メソッド:
  - `findAllSessions()` - 全練習日取得
  - `findById(Long id)` - ID検索
  - `findByDate(LocalDate date)` - 日付検索
  - `findSessionsInRange(LocalDate start, LocalDate end)` - 期間検索
  - `findSessionsByYearMonth(int year, int month)` - 年月検索
  - `findUpcomingSessions(LocalDate from)` - 今後の練習日
  - `existsSessionOnDate(LocalDate date)` - 練習日存在確認
  - `createSession(PracticeSessionCreateRequest)` - 練習日登録
  - `updateTotalMatches(Long id, Integer total)` - 総試合数更新
  - `deleteSession(Long id)` - 練習日削除
- ビジネスロジック:
  - 日付の重複チェック
  - 総試合数の負の値チェック

**PlayerProfileService** (`src/main/java/com/karuta/matchtracker/service/PlayerProfileService.java`)
- 実装メソッド:
  - `findCurrentProfile(Long playerId)` - 現在有効なプロフィール
  - `findProfileAtDate(Long playerId, LocalDate date)` - 特定日時点のプロフィール
  - `findProfileHistory(Long playerId)` - プロフィール履歴
  - `createProfile(PlayerProfileCreateRequest)` - プロフィール登録
  - `setValidTo(Long profileId, LocalDate validTo)` - 有効期限設定
  - `deleteProfile(Long profileId)` - プロフィール削除
- ビジネスロジック:
  - 既存プロフィールの有効期限自動設定
  - 選手名のエンリッチ
  - 有効期限の妥当性チェック

### 2. サービス層のテスト実装 ✅

#### 単体テスト (4ファイル)

**PlayerServiceTest** (`src/test/java/com/karuta/matchtracker/service/PlayerServiceTest.java`)
- 16テストケース
- テスト内容:
  - 全アクティブ選手取得
  - ID/名前検索（正常系・異常系）
  - 部分一致検索
  - ロール別検索
  - アクティブ選手数カウント
  - 選手登録（正常系・重複エラー）
  - 選手更新（正常系・削除済みエラー）
  - 論理削除（正常系・既削除）
  - ロール変更（正常系・削除済みエラー）

**MatchServiceTest** (`src/test/java/com/karuta/matchtracker/service/MatchServiceTest.java`)
- 13テストケース
- テスト内容:
  - 日付別試合取得
  - 試合存在確認
  - 選手の試合履歴（正常系・選手不在エラー）
  - 期間検索
  - 対戦履歴
  - 統計情報取得（勝率計算）
  - 試合登録（正常系・勝者不正エラー・自己対戦エラー）
  - 試合更新
  - 試合削除（正常系・不在エラー）

**PracticeSessionServiceTest** (`src/test/java/com/karuta/matchtracker/service/PracticeSessionServiceTest.java`)
- 14テストケース
- テスト内容:
  - 全練習日取得
  - ID検索（正常系・不在エラー）
  - 日付検索
  - 期間検索
  - 年月検索
  - 今後の練習日取得
  - 練習日存在確認
  - 練習日登録（正常系・重複エラー）
  - 総試合数更新（正常系・負の値エラー）
  - 練習日削除（正常系・不在エラー）

**PlayerProfileServiceTest** (`src/test/java/com/karuta/matchtracker/service/PlayerProfileServiceTest.java`)
- 9テストケース
- テスト内容:
  - 現在プロフィール取得（正常系・選手不在エラー）
  - 特定日時点のプロフィール取得
  - プロフィール履歴取得
  - プロフィール登録（既存プロフィールの有効期限自動設定）
  - 有効期限設定（正常系・日付不正エラー）
  - プロフィール削除（正常系・不在エラー）

### 3. 依存関係の追加

**build.gradleに追加した依存関係:**
```gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

Jakarta Validationを使用するために追加。

---

## テスト結果

### 統合テスト（リポジトリ層）
- **37テストケース** - 全て成功 ✅
- Testcontainers + MySQL 8.0で実行
- 4つのリポジトリ全てのカスタムクエリを検証

### 単体テスト（サービス層）
- **52テストケース** - 全て成功 ✅
- Mockitoを使用したモックテスト
- 正常系・異常系・バリデーションを網羅

### 合計
- **89テストケース** - 全て成功 🎉

---

## 技術スタック

### 実装に使用した技術
- **Spring Framework**
  - @Service - サービス層のコンポーネント
  - @Transactional - トランザクション管理
- **Lombok**
  - @RequiredArgsConstructor - コンストラクタDI
  - @Slf4j - ログ出力
  - @Data, @Builder - DTOのボイラープレート削減
- **Jakarta Validation**
  - @NotNull, @NotBlank, @Size, @Min, @Max - バリデーション
- **Java Stream API** - コレクション処理

### テストに使用した技術
- **JUnit 5** - テストフレームワーク
- **Mockito** - モックフレームワーク
  - @Mock - 依存関係のモック
  - @InjectMocks - テスト対象へのモック注入
  - @ExtendWith(MockitoExtension.class) - Mockito統合
- **AssertJ** - 流暢なアサーション

---

## ファイル構成

```
karuta-tracker/
├── src/main/java/com/karuta/matchtracker/
│   ├── dto/
│   │   ├── MatchCreateRequest.java         ✅ NEW
│   │   ├── MatchDto.java                   ✅ NEW
│   │   ├── MatchStatisticsDto.java         ✅ NEW
│   │   ├── PlayerCreateRequest.java        ✅ NEW
│   │   ├── PlayerDto.java                  ✅ NEW
│   │   ├── PlayerProfileCreateRequest.java ✅ NEW
│   │   ├── PlayerProfileDto.java           ✅ NEW
│   │   ├── PlayerUpdateRequest.java        ✅ NEW
│   │   ├── PracticeSessionCreateRequest.java ✅ NEW
│   │   └── PracticeSessionDto.java         ✅ NEW
│   ├── exception/
│   │   ├── DuplicateResourceException.java ✅ NEW
│   │   └── ResourceNotFoundException.java  ✅ NEW
│   └── service/
│       ├── MatchService.java               ✅ NEW
│       ├── PlayerProfileService.java       ✅ NEW
│       ├── PlayerService.java              ✅ NEW
│       └── PracticeSessionService.java     ✅ NEW
│
└── src/test/java/com/karuta/matchtracker/service/
    ├── MatchServiceTest.java               ✅ NEW (13 tests)
    ├── PlayerProfileServiceTest.java       ✅ NEW (9 tests)
    ├── PlayerServiceTest.java              ✅ NEW (16 tests)
    └── PracticeSessionServiceTest.java     ✅ NEW (14 tests)
```

---

## 現在の進捗状況

### 完了した層

1. ✅ **データベース層**
   - `database/phase1_schema.sql` - 4テーブルのスキーマ定義
   - 初期データ投入

2. ✅ **エンティティ層**
   - Player.java - 選手マスタ
   - PlayerProfile.java - 選手プロフィール履歴
   - PracticeSession.java - 練習日
   - Match.java - 試合結果

3. ✅ **リポジトリ層**
   - PlayerRepository.java (9メソッド)
   - PlayerProfileRepository.java (4メソッド)
   - PracticeSessionRepository.java (7メソッド)
   - MatchRepository.java (10メソッド)
   - **37統合テスト** - Testcontainers + MySQL

4. ✅ **サービス層**
   - PlayerService.java (10メソッド)
   - MatchService.java (9メソッド)
   - PracticeSessionService.java (9メソッド)
   - PlayerProfileService.java (6メソッド)
   - **52単体テスト** - Mockito

### 未実装の層

5. ⬜ **コントローラ層** ← 次のステップ
   - PlayerController
   - MatchController
   - PracticeSessionController
   - PlayerProfileController
   - グローバル例外ハンドラー

6. ⬜ **ビュー層**
   - Thymeleafテンプレート
   - Phase 1の画面（17画面）

7. ⬜ **セキュリティ層**
   - Spring Security設定
   - 認証・認可
   - ロールベースアクセス制御

---

## 次回作業開始時のチェックリスト

### 環境確認

1. **Docker Desktopが起動しているか確認**
   ```bash
   docker ps
   ```
   - Testcontainersを使用するため必須

2. **前回のテストが全て成功することを確認**
   ```bash
   cd c:\Users\popon\match-tracker\karuta-tracker
   ./gradlew test
   ```
   - 期待結果: 89 tests successful

3. **コンパイルが成功することを確認**
   ```bash
   ./gradlew compileJava
   ```

### 次のタスク: コントローラ層の実装

#### Step 1: グローバル例外ハンドラーの作成

**作成するファイル:**
- `src/main/java/com/karuta/matchtracker/controller/GlobalExceptionHandler.java`

**実装内容:**
- @RestControllerAdvice
- ResourceNotFoundExceptionのハンドリング → 404
- DuplicateResourceExceptionのハンドリング → 409
- IllegalArgumentExceptionのハンドリング → 400
- MethodArgumentNotValidExceptionのハンドリング → 400
- 汎用エラーレスポンスDTO

#### Step 2: PlayerControllerの作成

**作成するファイル:**
- `src/main/java/com/karuta/matchtracker/controller/PlayerController.java`

**実装するエンドポイント:**
```
GET    /api/players              - 全選手取得
GET    /api/players/{id}         - 選手詳細
GET    /api/players/search?name  - 名前検索
POST   /api/players              - 選手登録
PUT    /api/players/{id}         - 選手更新
DELETE /api/players/{id}         - 選手削除
PUT    /api/players/{id}/role    - ロール変更
```

#### Step 3: PlayerControllerのテスト作成

**作成するファイル:**
- `src/test/java/com/karuta/matchtracker/controller/PlayerControllerTest.java`

**使用する技術:**
- @WebMvcTest(PlayerController.class)
- MockMvc
- @MockBean for PlayerService

#### Step 4-6: 他のコントローラも同様に実装

- MatchController + テスト
- PracticeSessionController + テスト
- PlayerProfileController + テスト

---

## 重要な注意事項

### リポジトリメソッド名のマッピング

サービス層からリポジトリを呼び出す際、以下のメソッド名マッピングに注意:

**PlayerRepository:**
- `findByNameAndActive(name)` ← NOT `findByNameAndDeletedAtIsNull(name)`
- `findByRoleAndActive(role)` ← NOT `findByRoleAndDeletedAtIsNull(role)`
- `countActive()` ← NOT `countByDeletedAtIsNull()`

**MatchRepository:**
- `findByPlayerId(playerId)` ← NOT `findByPlayer(playerId)`
- `findByPlayerIdAndDateRange(...)` ← NOT `findByPlayerAndDateRange(...)`
- `findByTwoPlayers(p1, p2)` ← NOT `findByPlayer1IdAndPlayer2Id(p1, p2)`
- `countByPlayerId(playerId)` ← NOT `countByPlayer(playerId)`
- `countWinsByPlayerId(playerId)` ← NOT `countWinsByPlayer(playerId)`

**PlayerProfileRepository:**
- `findAllByPlayerIdOrderByValidFromDesc(playerId)` ← NOT `findByPlayerIdOrderByValidFromDesc(playerId)`

**PracticeSessionRepository:**
- `findAllOrderBySessionDateDesc()` ← NOT `findAllByOrderBySessionDateDesc()`
- `findByYearAndMonth(year, month)` ← YearMonthではなくint, intを渡す
- `findUpcomingSessions(date)` ← NOT `findBySessionDateGreaterThanEqualOrderBySessionDate(date)`

### 既知の問題

なし（現時点で全てのテストが成功）

### テスト実行時の注意

- **統合テスト実行時**: Docker Desktopが起動している必要がある
- **初回実行**: MySQLイメージのダウンロードに時間がかかる
- **全テスト実行**: `./gradlew test` (約60秒)
- **サービス層のみ**: `./gradlew test --tests "*service*"` (約6秒)

---

## Git状態

### 現在のブランチ
```
main
```

### 追跡されていないファイル
```
database/
karuta-tracker/src/main/java/com/karuta/matchtracker/dto/
karuta-tracker/src/main/java/com/karuta/matchtracker/exception/
karuta-tracker/src/main/java/com/karuta/matchtracker/service/
karuta-tracker/src/test/java/com/karuta/matchtracker/service/
```

### 次回コミット時のメッセージ案
```
Implement service layer with comprehensive tests

- Add 10 DTO classes for request/response handling
- Add 2 custom exception classes
- Implement 4 service classes with business logic
  - PlayerService (10 methods)
  - MatchService (9 methods)
  - PracticeSessionService (9 methods)
  - PlayerProfileService (6 methods)

- Add 52 unit tests for service layer
  - PlayerServiceTest (16 tests)
  - MatchServiceTest (13 tests)
  - PracticeSessionServiceTest (14 tests)
  - PlayerProfileServiceTest (9 tests)

All 89 tests (37 integration + 52 unit) passing

🤖 Generated with Claude Code
```

---

## 参考リンク

### プロジェクトドキュメント
- [DESIGN_DOCUMENT.md](DESIGN_DOCUMENT.md) - 設計書（画面設計、DB設計）
- [CHANGELOG.md](CHANGELOG.md) - 変更履歴
- [RESTART_GUIDE.md](RESTART_GUIDE.md) - 再起動後のガイド

### Spring Boot公式ドキュメント
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/3.4.1/reference/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Jakarta Validation](https://jakarta.ee/specifications/bean-validation/3.0/)

---

**作成日時**: 2025年11月7日
**次回作業**: コントローラ層の実装とテスト
