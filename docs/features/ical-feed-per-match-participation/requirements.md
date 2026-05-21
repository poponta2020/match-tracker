---
status: completed
---
# 参加試合に応じたカレンダーイベント時刻 要件定義書

## 1. 概要

### 1.1 目的
iCal カレンダー購読フィード（`/ical/calendar/{token}/...`）が生成するイベントの開始・終了時刻を、**プレイヤーが実際に参加する試合番号の時間帯**に絞り込む。

### 1.2 背景・動機
- 現状、フィードのイベント時刻は `PracticeSession.start_time` 〜 `end_time` を一律に使っており、ユーザーが何試合に参加するかに関係なく「練習全体の時間」がカレンダーに登録されている
- 6試合構成の練習で3試合目から参加するプレイヤーでも、カレンダー上は最初の試合開始時刻から最後の試合終了時刻までブロックされた状態になり、実態と合わない
- 会場には `VenueMatchSchedule`（試合番号ごとの開始/終了時刻）が既に登録されており、`PracticeParticipant.match_number` も保持しているため、これらを組み合わせれば「自分が参加する試合だけの時間帯」を出力できる

## 2. ユーザーストーリー

### 2.1 対象ユーザー
- iCal カレンダー購読フィード（団体別フィード／ゲスト参加フィード）を利用している全プレイヤー
- 特に「途中から参加」「途中で抜ける」など、練習の一部だけ参加するプレイヤー

### 2.2 ユーザーの目的
- 自分の Google カレンダー等に登録される予定の時間帯を、実際に練習に出る時間と一致させたい
- 他の予定との重複判定（Google カレンダーの「予定あり」「予定なし」など）を正確にしたい

### 2.3 利用シナリオ
1. ある練習が会場 A で 13:00〜17:00、試合番号1〜6で構成されている
2. プレイヤー X は試合番号3〜6に参加登録している（`PracticeParticipant.match_number = 3, 4, 5, 6` の4レコード）
3. 会場 A の `VenueMatchSchedule` には試合番号1〜6それぞれの開始/終了時刻が登録されている（例：試合3は 14:00〜14:45、試合6は 16:15〜17:00）
4. 翌日、Google カレンダーがフィードを取得すると、X のカレンダーには「練習＠会場A」のイベントが **14:00〜17:00**（試合3開始〜試合6終了）で表示される
5. 全試合参加するプレイヤー Y のイベントは 13:00〜17:00 のまま

## 3. 機能要件

### 3.1 画面仕様
**UI 変更なし**。設定画面のレイアウト・操作は既存のまま。挙動だけが変わる。

### 3.2 ビジネスルール

#### イベント時刻の決定アルゴリズム
1 つの `PracticeSession` に対するプレイヤー X の参加レコード集合（`activeParticipations` のうち当該 session に属するもの）から、以下の手順で `startTime` / `endTime` を決める。

```
Step 1: 参加レコードの match_number を集める
  - matchNumbersAll = 全参加レコードの match_number（nullを含む）
  - matchNumbersNonNull = matchNumbersAll から null を除いたもの

Step 2: スケジュール由来時刻を採用できるか判定
  条件A: matchNumbersAll に null が1件も含まれない（= 全参加レコードに match_number がある）
  条件B: session.venue_id != null かつ、その会場の VenueMatchSchedule が
        matchNumbersNonNull のうち少なくとも1件分は登録されている

Step 3a: 条件A・B が両方成立する場合
  - presentMatchNumbers = matchNumbersNonNull のうち VenueMatchSchedule が
    登録されている番号のみ
  - startTime = min(VenueMatchSchedule[mn].startTime for mn in presentMatchNumbers)
  - endTime   = max(VenueMatchSchedule[mn].endTime   for mn in presentMatchNumbers)

Step 3b: それ以外（条件A or B が成立しない）の場合
  - startTime = session.startTime
  - endTime   = session.endTime

Step 4: startTime / endTime が両方 null の場合
  - 全日イベント（既存仕様と同じ）
```

#### 補足
- **混在ケース**（同じ session で `match_number` ありレコードと null レコードが混在）：null が1件でもあれば session 全体時刻を採用する（条件A 不成立）
- **部分スケジュール**（参加 `match_number` の一部しか `VenueMatchSchedule` が無い）：あるものだけ使う（min/max の範囲は狭くなる可能性がある）
- **すべての参加スケジュールが欠けている**（条件B 不成立）：session 全体時刻にフォールバック
- **バッファ時間**：前後の受付・撤収時間は加味しない。試合の開始時刻〜終了時刻ぴったり

#### イベントタイトル・説明
変更なし（既存仕様を踏襲）。
- タイトル：`{表示名}＠{会場名}`
- 説明：`試合数: {minMatch}試合` または `試合数: {minMatch}〜{maxMatch}試合`
- UID：`session-{sessionId}-player-{playerId}@match-tracker`（既存と同じ）

イベント時刻が変わっても UID は同一なので、Google カレンダー側では同じイベントが「更新」される（重複登録にはならない）。

### 3.3 エラーケース・境界条件

| ケース | 挙動 |
|--------|------|
| 全参加レコードに match_number がある & 会場スケジュール完備 | スケジュール時刻を採用（新仕様の主要パス） |
| 1件でも match_number=null の参加レコードがある | session 全体時刻を採用 |
| 会場に VenueMatchSchedule が未登録 | session 全体時刻を採用 |
| 参加 match_number のスケジュールが部分的にだけ存在 | 存在分だけで min/max を計算（条件B 成立） |
| session.startTime/endTime も null | 全日イベント（既存と同じ） |
| session.startTime のみ存在、endTime が null | 既存通り `startTime.plusHours(4)` を endTime に使う（新仕様の Step 3b フォールバック時のみ発生） |

## 4. 技術設計

### 4.1 API設計
**変更なし**。エンドポイント・リクエスト・レスポンスは全て据え置き。

### 4.2 DB設計
**変更なし**。新規カラム・テーブル・マイグレーションは不要。

### 4.3 フロントエンド設計
**変更なし**。

### 4.4 バックエンド設計

#### 変更対象
- [karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java) のみ

#### 変更箇所
[IcalCalendarFeedService.buildEvent](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java#L392-L464) の時刻決定ロジックを、§3.2 のアルゴリズムに合わせて書き換える。

##### 現状の擬似コード（[L422-L438](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java#L422-L438)）
```java
LocalTime startTime = session.getStartTime();
LocalTime endTime = session.getEndTime();

// session 時刻が片方でも null かつ参加 match_number がある場合だけ
// VenueMatchSchedule にフォールバック
if ((startTime == null || endTime == null) && session.getVenueId() != null
        && matchNumbers != null && !matchNumbers.isEmpty()) {
    Map<Integer, VenueMatchSchedule> venueSchedule = scheduleMap.get(session.getVenueId());
    if (venueSchedule != null) {
        VenueMatchSchedule firstSchedule = venueSchedule.get(minMatch);
        VenueMatchSchedule lastSchedule = venueSchedule.get(maxMatch);
        if (startTime == null && firstSchedule != null) startTime = firstSchedule.getStartTime();
        if (endTime == null && lastSchedule != null) endTime = lastSchedule.getEndTime();
    }
}
```

##### 新仕様の擬似コード
```java
LocalTime startTime = null;
LocalTime endTime = null;

// Step 1: matchNumbers に null が含まれるかは buildIcsForParticipations 側で集約済み
//   → buildEvent に渡す段階で「全レコードに match_number があるか」を伝える必要あり
//   → 既存の matchNumbers リストは「null を除外した」状態で渡されているため、
//      「null を含むか」のフラグを別途渡す
boolean allHaveMatchNumber = (allHaveMatchNumberFlagForSession);

boolean scheduleApplied = false;
if (allHaveMatchNumber && session.getVenueId() != null
        && matchNumbers != null && !matchNumbers.isEmpty()) {
    Map<Integer, VenueMatchSchedule> venueSchedule = scheduleMap.get(session.getVenueId());
    if (venueSchedule != null) {
        // 参加 match_number のうちスケジュールがあるものだけ採用
        List<VenueMatchSchedule> presentSchedules = matchNumbers.stream()
                .map(venueSchedule::get)
                .filter(Objects::nonNull)
                .toList();
        if (!presentSchedules.isEmpty()) {
            startTime = presentSchedules.stream().map(VenueMatchSchedule::getStartTime)
                    .min(Comparator.naturalOrder()).orElse(null);
            endTime = presentSchedules.stream().map(VenueMatchSchedule::getEndTime)
                    .max(Comparator.naturalOrder()).orElse(null);
            scheduleApplied = true;
        }
    }
}

if (!scheduleApplied) {
    // 既存挙動にフォールバック
    startTime = session.getStartTime();
    endTime = session.getEndTime();
}

// 既存の「startTime はあるが endTime が null」処理はそのまま残す
// （Step 3b に該当する状況でしか発生しなくなる）
```

#### 呼び出し元（[buildIcsForParticipations](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java#L246-L329)）の改修
現状 `sessionMatchNumbers` は **null を除外して** 各 session のリストを作っている（[L309-L317](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java#L309-L317)）。

```java
for (PracticeParticipant p : activeParticipations) {
    if (!filteredSessionIds.contains(p.getSessionId())) continue;
    if (p.getMatchNumber() != null) {
        sessionMatchNumbers
                .computeIfAbsent(p.getSessionId(), k -> new ArrayList<>())
                .add(p.getMatchNumber());
    }
}
```

ここでは「null を含むかどうか」が落ちている。session 単位で「null を1件でも含むか」を集計する Map を追加する：

```java
Map<Long, Boolean> sessionHasNullMatchNumber = new HashMap<>();
for (PracticeParticipant p : activeParticipations) {
    if (!filteredSessionIds.contains(p.getSessionId())) continue;
    if (p.getMatchNumber() == null) {
        sessionHasNullMatchNumber.put(p.getSessionId(), true);
    } else {
        sessionMatchNumbers
                .computeIfAbsent(p.getSessionId(), k -> new ArrayList<>())
                .add(p.getMatchNumber());
        sessionHasNullMatchNumber.putIfAbsent(p.getSessionId(), false);
    }
}
```

`buildEvent` のシグネチャに `boolean allHaveMatchNumber` を追加して渡す。

#### テスト追加対象
[karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java](karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java) に以下のケースを追加：

1. 全参加レコードに match_number があり、会場スケジュール完備 → スケジュール時刻が採用される
2. 部分参加（試合3〜6に参加、会場は試合1〜6スケジュール完備） → 14:00〜17:00 のような正しい範囲が出る
3. 全試合参加（試合1〜6すべて）→ 13:00〜17:00（session 全体時刻と一致する範囲）
4. 同じ session に match_number ありと null が混在 → session.startTime/endTime が採用される
5. 会場に VenueMatchSchedule が未登録 → session.startTime/endTime が採用される
6. 参加 match_number の一部しかスケジュールが無い（試合3・5だけ登録、4は未登録）→ 試合3 start〜試合5 end が採用される
7. session.startTime/endTime も null かつスケジュール不在 → 全日イベント（既存挙動の維持確認）

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

**バックエンド**：
- [karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java)
  - `buildIcsForParticipations`: `sessionHasNullMatchNumber` Map の集計を追加
  - `buildEvent`: シグネチャに `boolean allHaveMatchNumber` を追加、時刻決定ロジックを §3.2 のアルゴリズムに置換

**テスト**：
- [karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java](karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java)
  - §4.4 の7ケースを追加（既存テストの一部は新仕様に合わせて期待値修正の可能性あり）

**ドキュメント**：
- [docs/SPECIFICATION.md](docs/SPECIFICATION.md) — カレンダー購読のイベント時刻仕様を更新
- [docs/DESIGN.md](docs/DESIGN.md) — iCal フィード生成ロジックの説明を更新
- [docs/SCREEN_LIST.md](docs/SCREEN_LIST.md) — 画面変更はなしのため、必要なら「カレンダー購読」セクションの説明補足

### 5.2 削除対象ファイル
なし。

### 5.3 既存機能への影響

- **既存購読ユーザーの Google/Apple カレンダー**：イベント UID は変わらないので、次回フェッチ時に「同じイベントが更新」されるだけ。重複や消失は発生しない
- **団体別フィード（`/org/{orgId}.ics`）／ゲスト参加フィード（`/guest.ics`）**：両方とも本変更の対象（共通の `buildIcsForParticipations` → `buildEvent` を通るため）
- **試合番号未指定の運用（matchNumber=null）**：従来通りの挙動を維持。ユーザーが意図的に「全参加」を null で運用しているケースを壊さない
- **VenueMatchSchedule 未登録の会場**：従来通りの挙動を維持
- **DB マイグレーション**：不要

### 5.4 共通コンポーネントへの影響
なし。フロントエンドの設定画面・API クライアント・他サービスへの影響はない。

## 6. 設計判断の根拠

### 6.1 なぜ条件A（全レコードに match_number 必須）を入れたか
- 1件でも `match_number=null` の参加レコードがある場合、そのプレイヤーが「全試合参加」を表現している可能性がある
- 例：5試合の練習で `match_number=3` のレコード1件と `match_number=null` のレコード1件があるとき、後者を「試合3だけ参加」と誤読してはいけない
- 安全側に倒して、null が混じったら従来通り session 全体時刻を使う

### 6.2 なぜ条件B（少なくとも1件はスケジュールが登録されている）を入れたか
- 会場運営が `VenueMatchSchedule` を入れていない場合に「null〜null」のような不正な時刻になるのを防ぐ
- 1件でもスケジュールがあれば、その範囲は信頼できる情報

### 6.3 なぜバッファ時間を入れないか
- ユーザー要望で「試合開始〜終了時刻ぴったり」と明示された
- バッファ要件は人によって異なる（電車で来る人と歩いて来る人）ため、サーバ側で一律に決めない方が良い
- 必要であれば Google カレンダー側の「予定の前にX分」などで個別調整できる

### 6.4 なぜ UI を変えないか
- 「カレンダーに登録される時間を実態と合わせる」というのは全ユーザーに有益な挙動の改善
- 切り替え式にすると設定画面が煩雑になり、デフォルト挙動の選択にも判断が必要
- 既存ユーザーへのカレンダー更新は UID 一致のため自動的に行われ、破壊的変更にならない

### 6.5 なぜ部分スケジュール時に「あるものだけ使う」か
- 「一部欠けていたら session 全体時刻」にすると、運営の登録漏れで本機能が無効化されるリスクがある
- 試合3・5だけ登録されているなら、その範囲（試合3 start〜試合5 end）でも従来より精度が高い
- 完全な情報を要求しすぎると実用性が落ちる
