---
status: completed
---

# 伝助同期の未入力保護（densuke-sync-blank-protection）要件定義書

## 1. 概要

### 目的
アプリの通常同期が伝助上の「未入力」マスを×に上書きしてしまう問題を修正し、アプリが変更していないマスの未入力状態を保護する。

### 背景・動機
伝助を使っている会員の中には、予定が確定するまで出欠を未入力にしておく人がいる。現在の通常同期は、プレイヤーにdirty行が1つでもあると月内全スロットを送信し、アプリに未登録のマスを`null→×(1)`で上書きしてしまう。さらにBYE（matchNumber=NULL）のdirty行が解除されないため、5分ごとに上書きが繰り返される。

### 根本原因
1. 通常同期がプレイヤー単位で月内全マスを送信する設計（DensukeWriteService.java:440-461）
2. アプリ未登録マスを`toJoinValue(null)=1(×)`で送信（DensukeWriteService.java:742）
3. BYE（matchNumber=NULL）がdirty=trueで生成され、dirty解除されず繰り返し発火（PracticeParticipant.java:119）

---

## 2. ユーザーストーリー

### 対象ユーザー
- 伝助で出欠管理をしている全会員（伝助のみの利用者を含む）

### ユーザーの目的
- 予定が確定するまで伝助上で未入力のまま残しておきたい
- アプリの同期が勝手に未入力を×に上書きしないでほしい

### 利用シナリオ
1. 選手Aが4/12の1試合目だけアプリで参加登録 → 通常同期で4/12の1試合目のみ○が送信される。4/19（未登録）の全マスは送信されず、伝助上の未入力が保護される
2. 選手Bが奇数人数で抜け番（BYE）になる → BYEエントリはdirty=falseで作成され、不要な同期トリガーにならない
3. 一度未入力から入力にした場合、再度未入力に戻すケースは考慮不要（まれなため）

### 補足条件
- アプリ側で「未入力」状態を表現する必要はない
- 抽選確定同期（writeAllForLotteryConfirmation）は現行の全体書き戻し方針を維持する

---

## 3. 機能要件

### 3.1 通常同期の送信範囲変更

**現状**: プレイヤーにdirty行が1つでもあると、そのプレイヤーの月内全セッション×全試合をformDataに含めて送信する。

**変更後**: 通常同期では、dirty=trueかつmatchNumber!=nullの行に対応するセッション×試合のみをformDataに含めて送信する。アプリに未登録のマスや、dirtyでないマスは送信しない。

| 条件 | 現行動作 | 変更後動作 |
|------|----------|------------|
| dirty=true, matchNumber!=null | 送信（○/△/×） | 送信（○/△/×）変更なし |
| dirty=false, matchNumber!=null | 送信（上書き） | **送信しない** |
| dirty=true, matchNumber=null (BYE) | 送信トリガーになるが書き込み不可、dirty解除もされない | **同期対象から除外** |
| アプリ未登録（pp=null） | ×(1)で送信 | **送信しない** |

### 3.2 抽選確定同期の維持

抽選確定同期（writeAllForLotteryConfirmation）は現行動作を維持する。アクティブステータス（WON/WAITLISTED/OFFERED/PENDING）のみ書き込み、CANCELLED/DECLINED/未登録はスキップ。未入力は上書きされない。

### 3.3 BYEエントリのdirty隔離

BYE（matchNumber=null）エントリは伝助の行に対応しないため、常にdirty=falseで管理する。

| BYE生成経路 | 変更内容 |
|-------------|----------|
| MatchPairingService.createBatch（待機者BYE作成） | dirty=falseで保存 |
| ByeActivityService.evaluatePracticeParticipant（BYE復元） | dirty=falseで保存 |
| softDeleteByPlayerIdAndSessionIds（一括キャンセル） | BYE（matchNumber=null）を除外 |
| softDeleteBySessionIdAndMatchNumber（試合単位キャンセル） | BYE除外（防御的追加） |
| PracticeSessionService.updateSession（セッション更新） | BYE（matchNumber=null）を除外 |

### 3.4 既存データのクリーンアップ

デプロイ直後に以下のSQLを1回実行し、既存のBYEノイズを解消する。

```sql
UPDATE practice_participants SET dirty = false WHERE match_number IS NULL AND dirty = true;
```

### 3.5 テスト要件

将来の逆戻りを防ぐため、以下のテストを追加する。

| テスト対象 | 検証内容 |
|------------|----------|
| DensukeWriteService（通常同期） | dirty行のみformDataに含まれること。未登録マスが送信されないこと |
| DensukeWriteService（抽選確定同期） | 既存挙動が維持されること（アクティブのみ書き込み） |
| MatchPairingService.createBatch | BYE生成時にdirty=falseであること |
| ByeActivityService.evaluatePracticeParticipant | BYE復元時にdirty=falseであること |

---

## 4. 技術設計

### 4.1 DensukeWriteService.java

#### 変更①: writePlayerToDensukeのループ（line 440-461）

通常同期時（`lotteryConfirmation=false`）は、dirtyParticipantsのsession×matchキーセットを作り、そこに含まれるスロットのみformDataに追加する。

```java
// 通常同期用: dirtyなキーだけ送信対象にする
Set<String> dirtyKeys = dirtyParticipants.stream()
    .map(p -> p.getSessionId() + "_" + p.getMatchNumber())
    .collect(Collectors.toSet());

for (PracticeSession session : urlSessions) {
    for (int matchNum = 1; matchNum <= session.getTotalMatches(); matchNum++) {
        String key = session.getId() + "_" + matchNum;

        PracticeParticipant pp = bySessionAndMatch.get(key);
        ParticipantStatus status = pp != null ? pp.getStatus() : null;

        // 通常同期: dirtyでないマスはスキップ（未入力保護）
        if (!lotteryConfirmation && !dirtyKeys.contains(key)) continue;

        // 抽選確定同期: 非アクティブステータスはスキップ（既存動作維持）
        if (lotteryConfirmation && !isActiveStatus(status)) continue;

        // ...formDataに追加
    }
}
```

#### 変更②: dirty抽出（line 155-156）

`findDirtyBySessionIds`は既存互換のまま残し、通常同期では新メソッド`findDirtyForDensukeSync`を使用する。

```java
// 変更前
List<PracticeParticipant> dirtyParticipants =
    practiceParticipantRepository.findDirtyBySessionIds(allSessionIds);

// 変更後
List<PracticeParticipant> dirtyParticipants =
    practiceParticipantRepository.findDirtyForDensukeSync(allSessionIds);
```

#### 変更なし: 全参加者取得（line 422）

ステータス値の参照用に`findByPlayerIdAndSessionIds`（全参加者取得）はそのまま残す。dirtyKeysのフィルタはループ内で行う。

#### 変更なし: dirty解除（line 486-492）

writtenSessionMatchKeysに含まれるもののみdirty=false。通常同期ではdirty行のみ書き込むため、dirty行のみ解除される。整合性は維持される。

### 4.2 PracticeParticipantRepository.java

#### 新メソッド追加

```java
@Query("SELECT p FROM PracticeParticipant p WHERE p.sessionId IN :sessionIds " +
       "AND p.dirty = true AND p.matchNumber IS NOT NULL")
List<PracticeParticipant> findDirtyForDensukeSync(@Param("sessionIds") List<Long> sessionIds);
```

#### softDeleteByPlayerIdAndSessionIds 修正

```java
// matchNumber IS NOT NULL 条件を追加
@Query("UPDATE PracticeParticipant p SET p.status = 'CANCELLED', p.dirty = true, " +
       "p.cancelledAt = :now " +
       "WHERE p.playerId = :playerId AND p.sessionId IN :sessionIds " +
       "AND p.matchNumber IS NOT NULL " +
       "AND p.status NOT IN ('CANCELLED', 'DECLINED', 'WAITLIST_DECLINED')")
int softDeleteByPlayerIdAndSessionIds(...);
```

#### softDeleteBySessionIdAndMatchNumber 修正（防御的）

```java
// matchNumber IS NOT NULL 条件を追加（防御的）
@Query("UPDATE PracticeParticipant p SET p.status = 'CANCELLED', p.dirty = true, " +
       "p.cancelledAt = :now " +
       "WHERE p.sessionId = :sessionId AND p.matchNumber = :matchNumber " +
       "AND p.matchNumber IS NOT NULL " +
       "AND p.status NOT IN ('CANCELLED', 'DECLINED', 'WAITLIST_DECLINED')")
int softDeleteBySessionIdAndMatchNumber(...);
```

### 4.3 MatchPairingService.java

createBatch（line 133-140）のBYE生成で`.dirty(false)`を追加。

```java
List<PracticeParticipant> byeParticipants = waitingPlayerIds.stream()
    .map(playerId -> PracticeParticipant.builder()
            .sessionId(session.getId())
            .playerId(playerId)
            .matchNumber(null)
            .dirty(false)  // ← 追加
            .build())
    .collect(Collectors.toList());
```

### 4.4 ByeActivityService.java

evaluatePracticeParticipant（line 217-222）のBYE復元で`.dirty(false)`を追加。

```java
PracticeParticipant restored = PracticeParticipant.builder()
        .sessionId(sessionId)
        .playerId(playerId)
        .matchNumber(null)
        .dirty(false)  // ← 追加
        .build();
```

### 4.5 PracticeSessionService.java

updateSession（line 391-397）の削除ループでmatchNumber==null除外。

```java
for (PracticeParticipant p : existingParticipants) {
    if (p.getMatchNumber() == null) continue;  // ← 追加: BYE除外
    if (!requestedPlayerIds.contains(p.getPlayerId())) {
        p.setStatus(ParticipantStatus.CANCELLED);
        p.setDirty(true);
        p.setCancelledAt(JstDateTimeUtil.now());
    }
}
```

---

## 5. 影響範囲

### 変更対象ファイル

| ファイル | 変更内容 |
|----------|----------|
| `DensukeWriteService.java` | writePlayerToDensukeのループにdirtyKeysフィルタ追加。dirty抽出を新メソッドに切替 |
| `PracticeParticipantRepository.java` | `findDirtyForDensukeSync`新メソッド追加。`softDeleteByPlayerIdAndSessionIds`にmatchNumber IS NOT NULL追加。`softDeleteBySessionIdAndMatchNumber`に防御的条件追加 |
| `MatchPairingService.java` | createBatchのBYE生成で`.dirty(false)`追加 |
| `ByeActivityService.java` | evaluatePracticeParticipantのBYE復元で`.dirty(false)`追加 |
| `PracticeSessionService.java` | updateSessionの削除ループでmatchNumber==null除外 |

### 既存機能への影響

| 機能 | 影響 |
|------|------|
| 通常同期（5分ごと） | dirty行のみ送信に変更。既存のdirty行は正しく送信されるため機能的に互換 |
| 抽選確定同期 | 変更なし。lotteryConfirmation=trueのパスは一切触らない |
| 伝助→アプリ同期（読み取り） | 変更なし |
| 手動同期（writeToDensukeForOrganization） | writeToDensukeInternalを経由するため通常同期と同じ改善が適用される |
| 対戦組み合わせ作成（createBatch） | BYEのdirty値のみ変更。組み合わせ作成ロジックへの影響なし |
| BYE活動記録（ByeActivity） | BYE復元時のdirty値のみ変更。活動記録ロジックへの影響なし |
| セッション更新（updateSession） | BYE除外により、キャンセル時にBYEが残るが、次回createBatchで上書きされるため実害なし |
| 参加登録（registerSameDay/registerBeforeDeadline） | softDeleteの変更でBYEが除外されるが、BYEはcreateBatchで管理されるため影響なし |
| 参加者設定（setMatchParticipants） | softDeleteBySessionIdAndMatchNumberは呼び出し元が非null matchNumberを渡すため実質影響なし |

### 影響がないことの確認

- **DBスキーマ変更**: なし（既存カラムの使い方の変更のみ）
- **APIエンドポイント変更**: なし
- **フロントエンド変更**: なし
- **伝助→アプリ方向の同期**: 変更なし

---

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 通常同期でdirty行のみ送信 | 未登録マスを×で上書きする根本原因の解消。伝助APIは送信されたフィールドのみ更新し、省略されたフィールドは維持するため安全 |
| 抽選確定同期は現行維持 | 抽選後はアプリが権威的ソース。既にisActiveStatusで未登録スキップ済みのため未入力上書きは発生しない |
| BYEをdirty=falseで管理 | BYEは伝助の行（日付×試合番号）に対応しないため、伝助同期の対象にする意味がない。dirty=trueのままだと永久に解除されず不要な同期トリガーになる |
| findDirtyBySessionIdsを残して新メソッド追加 | 既存互換性の維持。他の呼び出し箇所がある場合のリスク回避 |
| softDeleteでBYE除外 | BYEのライフサイクルはcreateBatchが管理しており、softDeleteで巻き込む必要がない |
| 本番補正SQLをコード反映直後に実施 | コード修正で再発防止した上で、既存ノイズを一掃する順序が安全 |
