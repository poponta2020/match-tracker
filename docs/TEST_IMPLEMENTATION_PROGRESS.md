# テストコード実装進捗状況

## プロジェクト概要
競技かるた練習結果管理システム（Match Tracker）の包括的なテストコード実装プロジェクト

**目標**: Plan C - 網羅的テストカバレッジ
- 全メソッド・全エンドポイントのテスト
- GitHub Actions CI/CD による自動テスト実行
- バグの早期発見と修正コストの削減

**技術スタック**:
- Spring Boot 3.4 + Java 21
- Gradle ビルドシステム
- JUnit 5 + Mockito + AssertJ
- Testcontainers (MySQL 8.0)

---

## 実装完了項目

### Phase 1: 共通コンポーネントのテスト ✅
1. **RoleCheckInterceptorTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptorTest.java`
   - 9テストケース: アノテーションなし、有効/無効ロール、ヘッダー欠落、権限不足など
   - 状態: コンパイル成功

2. **GlobalExceptionHandlerTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/controller/GlobalExceptionHandlerTest.java`
   - 例外→HTTPステータスマッピングテスト (404, 409, 403, 400, 500)
   - 状態: コンパイル成功

### Phase 2: Venueドメインのテスト ✅
3. **VenueRepositoryTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/repository/VenueRepositoryTest.java`
   - カスタムクエリ、CRUD操作、大小文字区別、最大長名前テスト
   - 状態: コンパイル成功

4. **VenueServiceTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/service/VenueServiceTest.java`
   - 全サービスメソッドのテスト、スケジュール作成/置換、重複名処理
   - 状態: コンパイル成功

5. **VenueControllerTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/controller/VenueControllerTest.java`
   - 全エンドポイント、バリデーションエラー、権限チェック
   - 状態: コンパイル成功

6. **VenueIntegrationTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/integration/VenueIntegrationTest.java`
   - CRUDライフサイクル、複数会場、スケジュール置換、重複競合、境界値テスト
   - 状態: コンパイル成功

### Phase 3: MatchPairingドメインのテスト ✅ (修正必要)
7. **MatchPairingRepositoryTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/repository/MatchPairingRepositoryTest.java`
   - カスタムクエリ、順序、空結果、複数試合番号
   - 状態: コンパイル成功

8. **MatchPairingServiceTest.java** - 完了 (コンパイルエラーあり)
   - ファイル: `src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java`
   - 全サービスメソッド、自動マッチングアルゴリズム詳細テスト
   - **状態**: コンパイルエラー - DTOメソッド呼び出しの修正必要

9. **MatchPairingControllerTest.java** - 完了
   - ファイル: `src/test/java/com/karuta/matchtracker/controller/MatchPairingControllerTest.java`
   - 全エンドポイント、権限チェック、バリデーション
   - 状態: コンパイル成功見込み

10. **MatchPairingIntegrationTest.java** - 完了 (コンパイルエラーあり)
    - ファイル: `src/test/java/com/karuta/matchtracker/integration/MatchPairingIntegrationTest.java`
    - CRUDライフサイクル、自動マッチング、奇数/偶数人数、権限テスト
    - **状態**: コンパイルエラー - DTOメソッド呼び出しの修正必要

### Phase 4: 既存テストへの追加 ✅ (修正必要)
11. **MatchServiceTest.java** - 追加完了 (コンパイルエラーあり)
    - ファイル: `src/test/java/com/karuta/matchtracker/service/MatchServiceTest.java`
    - 追加内容:
      - `findPlayerMatchesWithFilters` - フィルタリング付き試合検索
      - `getPlayerStatisticsByRank` - 級別統計
      - `createMatchSimple` - シンプル試合作成
      - `updateMatchSimple` - シンプル試合更新
    - **状態**: コンパイルエラー - `MatchSimpleCreateRequest.builder()` および `RankStatisticsDto.getMatches()` の修正必要

12. **MatchControllerTest.java** - 追加完了
    - ファイル: `src/test/java/com/karuta/matchtracker/controller/MatchControllerTest.java`
    - 追加内容:
      - フィルタ付き試合履歴取得
      - 級別統計取得 (フィルタあり/なし)
      - 簡易版試合登録/更新
      - IDによる試合取得
    - 状態: コンパイル成功見込み

13. **PlayerServiceTest.java** - 追加完了
    - ファイル: `src/test/java/com/karuta/matchtracker/service/PlayerServiceTest.java`
    - 追加内容:
      - `login` メソッドの全テストケース (成功、選手不在、誤パスワード、空パスワード)
    - 状態: コンパイル成功見込み

14. **PlayerControllerTest.java** - 追加完了
    - ファイル: `src/test/java/com/karuta/matchtracker/controller/PlayerControllerTest.java`
    - 追加内容:
      - POST /api/players/login エンドポイントの全テストケース
      - バリデーションエラーテスト
    - 状態: コンパイル成功見込み

### Phase 5: CI/CD設定 ✅
15. **GitHub Actions ワークフロー** - 完了
    - ファイル: `.github/workflows/test.yml`
    - 機能:
      - push/PR時の自動テスト実行 (main, develop)
      - MySQL 8.0 サービスコンテナ
      - JDK 21 (Temurin)
      - テストレポート生成
      - カバレッジコメント (PR時)
    - 状態: 完了 (注意: Mavenではなく **Gradle** を使用するよう修正必要)

### その他の修正 ✅
16. **BaseIntegrationTest.java** - 修正完了
    - ファイル: `src/test/java/com/karuta/matchtracker/integration/BaseIntegrationTest.java`
    - 追加内容: 新テーブルのTRUNCATE処理
      ```java
      jdbcTemplate.execute("TRUNCATE TABLE match_pairings");
      jdbcTemplate.execute("TRUNCATE TABLE venue_match_schedules");
      jdbcTemplate.execute("TRUNCATE TABLE venues");
      ```
    - 状態: 完了

---

## 現在のコンパイルエラー一覧

### エラーカテゴリ

#### 1. MatchSimpleCreateRequest - builderメソッド不在
**問題**: `MatchSimpleCreateRequest`は`@Data`のみで`@Builder`がない
**影響ファイル**: `MatchServiceTest.java` (714, 741, 778, 800, 824, 863, 899, 922行目)

**解決方法**:
```java
// ❌ 誤り (builderは存在しない)
MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
    .matchDate(today)
    .build();

// ✅ 正しい方法1: newでインスタンス化してsetterで設定
MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
request.setMatchDate(today);
request.setMatchNumber(1);
request.setPlayerId(1L);
request.setOpponentName("未登録選手");
request.setResult("勝ち");
request.setScoreDifference(5);

// ✅ 正しい方法2: 全引数コンストラクタ (Lombokが生成)
// ※ただし、フィールド順序を確認する必要あり
```

#### 2. RankStatisticsDto - getMatches()メソッド不在
**問題**: フィールド名は`total`であり、`matches`ではない
**影響ファイル**: `MatchServiceTest.java` (609, 613, 615, 661, 662, 701行目)

**解決方法**:
```java
// ❌ 誤り
assertThat(result.getTotal().getMatches()).isEqualTo(3);

// ✅ 正しい
assertThat(result.getTotal().getTotal()).isEqualTo(3);
```

#### 3. MatchPairingDto - recordスタイルメソッド呼び出し
**問題**: `MatchPairingDto`は通常のクラス(record型ではない)
**影響ファイル**:
- `MatchPairingServiceTest.java` (80-84, 126-129, 219-220, 247-251, 276行目など)
- `MatchPairingIntegrationTest.java` (68, 278, 317, 367, 372行目)

**解決方法**:
```java
// ❌ 誤り (recordスタイル)
assertThat(result.sessionDate()).isEqualTo(sessionDate);
assertThat(result.matchNumber()).isEqualTo(1);
Long pairingId = created.id();

// ✅ 正しい (getterメソッド)
assertThat(result.getSessionDate()).isEqualTo(sessionDate);
assertThat(result.getMatchNumber()).isEqualTo(1);
Long pairingId = created.getId();
```

#### 4. AutoMatchingResult - recordスタイルメソッド呼び出し
**問題**: `AutoMatchingResult`も通常のクラス
**影響ファイル**: `MatchPairingIntegrationTest.java` (278, 317行目)

**解決方法**:
```java
// ❌ 誤り
List<Long> pairedPlayerIds = result.pairings().stream()...
assertThat(result.waitingPlayers()).hasSize(1);

// ✅ 正しい
List<Long> pairedPlayerIds = result.getPairings().stream()...
assertThat(result.getWaitingPlayers()).hasSize(1);
```

#### 5. Player - setCurrentRank()メソッド不在
**問題**: `Player`エンティティには`currentRank`フィールドがなく、`kyuRank`と`danRank`がある
**影響ファイル**:
- `PlayerServiceTest.java` (321行目)
- `MatchPairingIntegrationTest.java` (380行目)

**解決方法**:
```java
// ❌ 誤り
player.setCurrentRank("A級");
Player player = Player.builder().currentRank("A級").build();

// ✅ 正しい (KyuRankを使用)
player.setKyuRank(Player.KyuRank.A級);
Player player = Player.builder().kyuRank(Player.KyuRank.A級).build();
```

#### 6. MatchPairingService.getByDateAndMatchNumber() - 返り値の型
**問題**: このメソッドは`List<MatchPairingDto>`を返すが、テストでは単一のDtoとして扱っている
**影響ファイル**: `MatchPairingServiceTest.java` (123行目)

**解決方法**:
```java
// ❌ 誤り
MatchPairingDto result = matchPairingService.getByDateAndMatchNumber(sessionDate, matchNumber);

// ✅ 正しい
List<MatchPairingDto> resultList = matchPairingService.getByDateAndMatchNumber(sessionDate, matchNumber);
MatchPairingDto result = resultList.get(0);
// または、リストが1件であることをアサート
assertThat(resultList).hasSize(1);
MatchPairingDto result = resultList.get(0);
```

---

## 次にやるべきこと (優先順位順)

### 🔴 最優先: コンパイルエラー修正

#### タスク1: MatchServiceTest.javaの修正
**ファイル**: `src/test/java/com/karuta/matchtracker/service/MatchServiceTest.java`

**修正箇所**:
1. 行609, 613, 615, 661, 662, 701: `getMatches()` → `getTotal()`
2. 行714, 741, 778, 800, 824, 863, 899, 922: `MatchSimpleCreateRequest.builder()` → `new MatchSimpleCreateRequest()` + setters

**修正例**:
```java
// 修正前 (行714-721)
MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
    .matchDate(today)
    .matchNumber(1)
    .playerId(1L)
    .opponentName("未登録選手")
    .result("勝ち")
    .scoreDifference(5)
    .build();

// 修正後
MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
request.setMatchDate(today);
request.setMatchNumber(1);
request.setPlayerId(1L);
request.setOpponentName("未登録選手");
request.setResult("勝ち");
request.setScoreDifference(5);
```

#### タスク2: MatchPairingServiceTest.javaの修正
**ファイル**: `src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java`

**修正箇所**:
1. 全ての`result.sessionDate()` → `result.getSessionDate()`
2. 全ての`result.matchNumber()` → `result.getMatchNumber()`
3. 全ての`result.player1Id()` → `result.getPlayer1Id()`
4. 全ての`result.player2Id()` → `result.getPlayer2Id()`
5. 行123: 返り値を`List<MatchPairingDto>`として処理

**一括置換コマンド (参考)**:
```bash
cd src/test/java/com/karuta/matchtracker/service
sed -i 's/\.sessionDate()/\.getSessionDate()/g' MatchPairingServiceTest.java
sed -i 's/\.matchNumber()/\.getMatchNumber()/g' MatchPairingServiceTest.java
sed -i 's/\.player1Id()/\.getPlayer1Id()/g' MatchPairingServiceTest.java
sed -i 's/\.player2Id()/\.getPlayer2Id()/g' MatchPairingServiceTest.java
```

#### タスク3: MatchPairingIntegrationTest.javaの修正
**ファイル**: `src/test/java/com/karuta/matchtracker/integration/MatchPairingIntegrationTest.java`

**修正箇所**:
1. 行68, 367, 372: `created.id()` → `created.getId()`
2. 行278: `result.pairings()` → `result.getPairings()`
3. 行317: `result.waitingPlayers()` → `result.getWaitingPlayers()`
4. 行380: `player.setCurrentRank(rank)` → `player.setKyuRank(Player.KyuRank.valueOf(rank))`
   - 注意: rank文字列(例: "A級")を`KyuRank`enumに変換する必要あり

**修正例 (行380)**:
```java
// 修正前
private Player createAndSavePlayer(String name, String rank) {
    Player player = new Player();
    player.setName(name);
    player.setCurrentRank(rank);  // ← エラー
    player.setRole(Player.Role.PLAYER);
    return playerRepository.save(player);
}

// 修正後
private Player createAndSavePlayer(String name, String rank) {
    Player player = new Player();
    player.setName(name);
    if (rank != null && !rank.isEmpty()) {
        player.setKyuRank(Player.KyuRank.valueOf(rank));
    }
    player.setRole(Player.Role.PLAYER);
    return playerRepository.save(player);
}
```

#### タスク4: PlayerServiceTest.javaの修正
**ファイル**: `src/test/java/com/karuta/matchtracker/service/PlayerServiceTest.java`

**修正箇所**:
1. 行321: `.currentRank("A級")` → `.kyuRank(Player.KyuRank.A級)`

**修正例**:
```java
// 修正前
Player player = Player.builder()
    .id(1L)
    .name("山田太郎")
    .password("password123")
    .role(Player.Role.PLAYER)
    .currentRank("A級")  // ← エラー
    .build();

// 修正後
Player player = Player.builder()
    .id(1L)
    .name("山田太郎")
    .password("password123")
    .role(Player.Role.PLAYER)
    .kyuRank(Player.KyuRank.A級)
    .build();
```

### 🟡 中優先: GitHub Actions ワークフローの修正

#### タスク5: test.ymlをGradle対応に修正
**ファイル**: `.github/workflows/test.yml`

**修正内容**:
```yaml
# 修正前
- name: Run tests
  working-directory: ./karuta-tracker
  run: mvn clean test  # ← Mavenコマンド

# 修正後
- name: Run tests
  working-directory: ./karuta-tracker
  run: ./gradlew clean test  # ← Gradleコマンド

# その他、Maven固有の設定も修正必要:
# - cache: maven → cache: gradle
# - surefire-reports → test-results/test
# - jacoco設定のパスも要確認
```

### 🟢 低優先: テスト実行と検証

#### タスク6: 全テスト実行
**コマンド**:
```bash
cd karuta-tracker
./gradlew clean test
```

**確認事項**:
- [ ] 全テストがコンパイル成功
- [ ] 全テストが実行成功
- [ ] カバレッジレポート生成確認

#### タスク7: 統合テスト実行
**コマンド**:
```bash
cd karuta-tracker
./gradlew clean test --tests "*IntegrationTest"
```

---

## テストカバレッジ統計 (想定)

### 作成したテストファイル数
- **新規作成**: 10ファイル
- **既存に追加**: 4ファイル
- **合計**: 14ファイル

### テストケース数 (概算)
- RoleCheckInterceptorTest: 9件
- GlobalExceptionHandlerTest: 6件
- VenueRepositoryTest: 8件
- VenueServiceTest: 12件
- VenueControllerTest: 18件
- VenueIntegrationTest: 10件
- MatchPairingRepositoryTest: 10件
- MatchPairingServiceTest: 40件
- MatchPairingControllerTest: 35件
- MatchPairingIntegrationTest: 11件
- MatchServiceTest (追加分): 25件
- MatchControllerTest (追加分): 9件
- PlayerServiceTest (追加分): 4件
- PlayerControllerTest (追加分): 5件

**合計**: 約202テストケース (既存169件 + 新規33件以上)

---

## 技術的メモ

### DTOとエンティティの設計パターン

#### Record型 vs 通常クラス
- **Record型**: `id()`, `name()`のようにフィールド名がそのままメソッド名
- **通常クラス (@Data)**: `getId()`, `getName()`のようにJavaBeansスタイル

**このプロジェクトの実装**:
- `MatchPairingDto`: 通常クラス (@Data + @Builder)
- `AutoMatchingResult`: 通常クラス
- `RankStatisticsDto`: 通常クラス
- `MatchSimpleCreateRequest`: 通常クラス (@Data のみ、@Builderなし)

### Testcontainersの使用方法
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class BaseIntegrationTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @BeforeEach
    void cleanUp() {
        // テーブルのTRUNCATE処理
    }
}
```

### モックとスタブのベストプラクティス
- **単体テスト (@ExtendWith(MockitoExtension.class))**: 依存関係をモック化
- **統合テスト (@SpringBootTest)**: 実際のDB(Testcontainers)を使用
- **コントローラーテスト (@WebMvcTest)**: サービス層をモック化、MockMvcで検証

---

## 参考リンク

### ドキュメント
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Core Documentation](https://assertj.github.io/doc/)
- [Testcontainers Documentation](https://www.testcontainers.org/)

### プロジェクト固有
- 既存テストコード参照: `src/test/java/com/karuta/matchtracker/`
- DTOクラス: `src/main/java/com/karuta/matchtracker/dto/`
- エンティティクラス: `src/main/java/com/karuta/matchtracker/entity/`

---

## 最終チェックリスト

### コンパイルエラー修正
- [ ] MatchServiceTest.java - `getMatches()` → `getTotal()`
- [ ] MatchServiceTest.java - `MatchSimpleCreateRequest.builder()` 修正
- [ ] MatchPairingServiceTest.java - recordスタイルメソッド修正
- [ ] MatchPairingIntegrationTest.java - recordスタイルメソッド修正
- [ ] MatchPairingIntegrationTest.java - `setCurrentRank()` 修正
- [ ] PlayerServiceTest.java - `currentRank` → `kyuRank`

### テスト実行
- [ ] 単体テスト全件実行成功
- [ ] 統合テスト全件実行成功
- [ ] カバレッジレポート確認

### CI/CD
- [ ] GitHub Actions ワークフロー修正 (Maven → Gradle)
- [ ] ワークフロー動作確認

### ドキュメント
- [x] 進捗状況ドキュメント作成
- [ ] テスト実行結果の記録

---

**最終更新日**: 2026-02-13
**作成者**: Claude Sonnet 4.5
**ステータス**: コンパイルエラー修正待ち (約100件のエラー)
