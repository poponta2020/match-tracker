---
status: completed
completed_sections: [改修内容, 技術設計, 影響範囲]
next_section: null
audit_source: 会話内レポート (Issue #521 調査結果)
selected_items: [1, 4]
---

# densuke-change-time-tracking 改修要件定義書

## 1. 改修概要

### 対象機能
- `DensukeScraper` による伝助ページHTMLの取り込み
- `DensukeImportService` の Phase3 状態遷移処理

### 改修の背景
Issue #521 の調査で以下が判明した:

- 2026-04-23 12:51頃、伝助ページで別タイミングにキャンセルされた3名分の通知が一括で管理者に届いた
- 伝助ページの各メンバーリンクには `title="M/d HH:mm"` 形式の「densuke側最終更新時刻」属性が存在するが、アプリは一切利用していなかった
- そのため「densuke上での実キャンセル時刻」と「アプリが検出した時刻」の乖離を事後解析できなかった
- 原因究明は「とりあえず起こらないもの」として打ち切ったが、**将来再び同種の事象が起きたとき即座に解析できる状態にする必要がある**

### 改修スコープ
アプリの**挙動変更は一切行わない**。観測性（ログ出力と内部データ構造）のみを追加する。

採用項目:
1. **DensukeScraper に title 属性パース追加** — `DensukeData.memberLastChangeTimes` として保持
2. **Phase3系ログ拡張 + 乖離WARN** — 状態遷移ログに `densuke title時刻` と `drift` を含め、10分を超える乖離は WARN

**明示的に見送った項目**（観点：挙動変更を伴うため今回は対象外）:
- `cancelled_at` を title 時刻由来に置換する改修
- `isAfterSameDayNoon` を title 時刻で評価する改修

これらは将来、観測ログから「乖離が頻発する」等の事実が確認できたら再検討する。

---

## 2. 改修内容

### 2.1 DensukeScraper に title 属性パースを追加（対策1）

**現状の問題**
- HTMLヘッダーの `<a title="M/d HH:mm">` が無視されている
- アプリ側には「densuke側最終更新時刻」の情報が全く伝わらない

**修正方針**
- `DensukeScraper.DensukeData` に `Map<String, LocalDateTime> memberLastChangeTimes` フィールドを追加
- `parse(Document, int year)` のヘッダーパース部分で、各メンバーの `link.attr("title")` を取得して parse
- 形式は `"M/d HH:mm"`、year は parse の引数を使う
- 空文字や parse 失敗時は map に entry を追加しない（null スキップ）
- 年跨ぎの誤判定は今回考慮しない（scrape year 固定）

**修正後のあるべき姿**
- scrape 完了後、各メンバーについて「densuke上の最終更新時刻」が `memberLastChangeTimes` から引ける
- 既存の `memberNames`, `ScheduleEntry` の構造は一切変わらない

---

### 2.2 Phase3系ログ拡張 + 乖離 WARN（対策4）

**現状の問題**
- `Phase3-C2: cancelled WON player {} via densuke` など状態遷移ログに playerId しか出ていない
- 「いつ densuke 側で変更されたか」「検出までどれくらい遅れたか」が Render ログだけでは追えない

**修正方針**
- `DensukeImportService` の Phase3 系メソッド（Phase3Maru / Phase3Sankaku / Phase3Batsu）と Phase1 系で状態遷移ログを出している箇所に対し:
  1. ログメッセージに `densukeTitleTime=YYYY-MM-DDTHH:mm detectedAt=YYYY-MM-DDTHH:mm:ss driftMinutes=N` を追記
  2. `driftMinutes > 10` の場合、追加で WARN ログを独立に出力
- `detectedAt` = `JstDateTimeUtil.now()`
- `driftMinutes` = `Duration.between(densukeTitleTime, detectedAt).toMinutes()`
- `densukeTitleTime` が null（title未取得）の場合は `titleTime=(unknown)`、WARN は抑制

**修正後のあるべき姿**
通常時の INFO ログ例:
```
INFO Phase3-C2: cancelled WON player 20 via densuke (densukeTitle=2026-04-23T12:45, detectedAt=2026-04-23T12:50:56, drift=5m)
```

乖離時の WARN ログ例:
```
WARN Densuke change-time drift detected: phase=Phase3-C2 session=934 match=1 player=20 (鮎川知佳) densukeTitle=2026-04-22T22:06 detectedAt=2026-04-23T12:50:56 driftMinutes=884
```

---

## 3. 技術設計

### 3.1 API変更
なし

### 3.2 DB変更
なし（Render PostgreSQL マイグレーション不要）

### 3.3 フロントエンド変更
なし

### 3.4 バックエンド変更

#### 3.4.1 `DensukeScraper.java`

- `DensukeData` に `memberLastChangeTimes: Map<String, LocalDateTime>` を追加
- `parse()` のヘッダーループで title をパース、map に格納
- 新規 static helper: `parseDensukeTitleAsDateTime(String title, int year) : LocalDateTime`
  - パターン: `M/d HH:mm`
  - 失敗時は null を返す（例外は握り潰し）

#### 3.4.2 `DensukeImportService.java`

- `processPhase1`、`processPhase3` とその配下（`processPhase3Maru`, `processPhase3Sankaku`, `processPhase3Batsu`, `reactivateAsWaitlisted` など状態遷移でログを出す系）に **`Map<String, LocalDateTime> memberLastChangeTimes`** を引数として伝搬
- 各状態遷移ログの直前で drift 計算を行う小ヘルパを導入:
  ```java
  private String formatDriftLog(Long playerId, Map<Long, String> playerIdMap,
                                Map<String, LocalDateTime> memberLastChangeTimes,
                                LocalDateTime detectedAt)
  ```
  戻り値例: `"densukeTitle=2026-04-22T22:06 detectedAt=2026-04-23T12:50:56 drift=884m"`
- 並行して `driftMinutes > 10` を WARN で吐く専用ログ出力メソッド:
  ```java
  private void warnIfDrifted(String phase, Long sessionId, int matchNumber, 
                              Long playerId, Map<Long, String> playerIdMap,
                              Map<String, LocalDateTime> memberLastChangeTimes,
                              LocalDateTime detectedAt)
  ```

#### 3.4.3 閾値定数
`DensukeImportService` 内に定数として定義:
```java
private static final long DRIFT_WARN_THRESHOLD_MINUTES = 10;
```

---

## 4. 影響範囲

### 影響を受ける既存機能
なし（ログ文字列の書式が変わるが、挙動には影響なし）

### 共通コンポーネント・ユーティリティへの影響
- `DensukeScraper.DensukeData` のフィールド追加 → 互換性あり（既存呼び出しは無視すれば良い）
- `DensukeImportService` 内部メソッドシグネチャ変更 → privateメソッドのため外部影響なし

### API・DBスキーマの互換性
- 破壊的変更なし
- DB マイグレーション不要

### テスト影響
- `DensukeScraperTest` / `DensukeScraperLiveSnapshotTest`: `memberLastChangeTimes` に期待値が入ることを検証するテストを追加
- `DensukeImportServiceTest` など: 既存テストの期待ログが変わる可能性あり。ログ書式変更に合わせてassertion調整

### デプロイ時の注意
- Render へは通常の push → deploy で反映
- 本番DB適用は不要
- 既存稼働中のsyncサイクルに影響を与えず、次回sync から新ログが出る

---

## 5. 設計判断の根拠

### なぜ対策2,3を見送り観測性だけに絞ったか
- 挙動変更を伴う対策2,3は、通常フローへの分岐変更によって**管理者通知の取りこぼし**等の副作用を生む懸念があり、設計判断が必要
- 今回の事象の原因が未特定のまま挙動を変えると、別の不具合を招きかねない
- まずは観測性を強化し、再発時の解析データを蓄積することを優先する
- 観測性を手に入れた状態で2回目以降の事象を観察することで、**本当に起きているズレの傾向**を掴んだ上で対策2,3の要否・範囲を判断できる

### WARN 閾値を 10分 にした理由
- Densuke sync の `fixedDelay = 300000ms`（5分）の約2倍
- 通常運用では超えない値。超えたときは単純な「次回syncで処理」では説明できないズレ
- 30分や1時間にすると「微妙な遅延」を見逃す

### title パースエラー時に null スキップを選んだ理由
- title は densuke のUI上の情報表示で、パース不可なフォーマット変更があり得る
- 例外を投げて import 全体を止めるとサービス影響が大きすぎる
- 「title 取得できないメンバーは WARN 対象外」で安全側に倒す
