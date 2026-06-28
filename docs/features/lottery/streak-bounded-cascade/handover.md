# 抽選機能 公平性改修 引き継ぎ書

作成日: 2026-05-07
ステータス: 仕様確定、実装未着手

## 概要

抽選機能における「複数試合に申込んだ人が不利になる」問題と「往復コスパ」の観点から議論を行い、最終的に **連敗3制約付き cascade（streak-bounded cascade）** の採用が決定した。本ドキュメントは、その経緯と次の実装ステップをまとめた引き継ぎ書である。

## 背景・動機

### 当初の問題提起

ユーザーから「抽選の公平性」について以下のシナリオで質問:

- A: 1試合目・2試合目の両方に申込
- B: 2試合目のみ申込
- C: 1試合目のみ申込
- 各試合 定員+1 の申込（n=10、申込11人）

現行 cascade ルール下での落選確率:

| | A | B | C |
|---|---|---|---|
| 落選確率（少なくとも1試合） | **17.4%** | 8.3% | 9.1% |
| 両方落選 | 9.1% | - | - |

→ 複数試合に申込んだ A の方が単独申込の B/C より落選しやすく、不公平に見える。

### cascade ルールの本来の意図

ユーザーの説明によると、cascade は **「往復コスパ最大化」** のために存在する:

> 「1試合のために往復1時間かけるのは無駄。1人が全試合落選して当日来ない方が、他の人が複数試合に出られて全体最適」

つまり「**敗者を1人に集中**」させる設計思想。前回指摘した「A の shut out 率が高い」は意図された結果（A 自身も「半端な1試合参加」より「行かなくて済む」方がコスパ的に良い）。

### 現行 cascade の不完全性

しかし現行は M1 → M2 の一方向 cascade のみで、以下の半端参加が残る:

- A が M1 当選 → M2 落選: 8.3% 発生（A が M1 だけ参加で半端）
- 試合が3つ以上: 様々な半端パターンが発生
- 1人が7連敗するケースもあり得る（極端な集中）

## 検討した代替案（不採用）

| 案 | 概要 | 不採用理由 |
|---|---|---|
| 独立抽選 | cascade なし、各試合独立 | 半端参加が多発（46.3%） |
| 人単位 all-or-nothing | 多重申込者を「全勝 or 全敗」に固定 | 単独申込者（M-R）への副作用大（M1 が空く or M-R 全落選） |
| 申込者数の多い順処理 | より大きい抽選を先に | 定員が均等な前提では限定的、また定員が極端に違う場合は逆効果 |
| 定員超過率の高い順処理 | 申込/定員 が高い試合を先に | 定員均等の前提では現行と同じ挙動 |

## 採用された仕様: 連敗3制約付き cascade

### ユーザーから提示された制約

1. **定員は試合間で同じ**（M1 = M2 = ... = M7）
2. **1日最大7試合** 実施することがある
3. **連続落選は最大3試合まで**（4連敗以上は禁止）

これにより「徹底した cascade（1人に全敗集中）」は不可能になり、「連敗3で強制当選」の仕様が必要になった。

### アルゴリズム

各人の `streak`（同セッション内の連続落選数）を追跡し、`streak ≥ 3` の人は次試合で **must-win**（強制当選）扱い。

### 抽選優先順位（高 → 低）

1. **must-win**（streak ≥ 3）— 必ず当選
2. **管理者優先**（既存）
3. **月内連続落選救済 rescue**（既存）
4. **通常**（streak == 0）
5. **cascade**（streak 1-2、高い方が優先）

`must-win > 管理者優先` とした理由: 連敗3制約はシステムレベルの保証であり、管理者優先より上位とすべき。

### streak 更新ルール（解釈B採用）

各試合処理後:

- **当選者**: streak = 0
- **落選者**: streak += 1
- **申込のなかった人**: streak = 0（解釈B: スキップで連続が切れる）

### 「連続」の定義について

- 解釈A: 申込んだ試合での連続落選（スキップは無視）
- **解釈B（採用）**: 試合番号順での連続落選（スキップで連続が切れる）

ユーザーの希望により解釈Bを採用。実装上は「申込のない人の streak をリセット」する処理が必要。

### 期待される動作（11人 / 7試合 / 定員10）

| 試合 | must-win | 落選者 | streak |
|---|---|---|---|
| M1 | - | A | A:1 |
| M2 | - | A（cascade） | A:2 |
| M3 | - | A（cascade） | A:3 |
| M4 | A | B | A:0, B:1 |
| M5 | - | B（cascade） | B:2 |
| M6 | - | B（cascade） | B:3 |
| M7 | B | C | B:0, C:1 |

→ A は M1-M3 で3連敗→M4以降全勝、B は M4-M6 で3連敗→M7当選、C は M7 のみ落選。各人の最大連敗は3。

### must-win が定員超過した場合

must-win 同士で抽選し、一部は落選（連敗3制約違反だが極端な状況のみ許容）。

### 既存 cascade との意味の変化

| | 現行 | 新仕様 |
|---|---|---|
| cascade 判定基準 | sessionLosers（永続） | streak 1-2（直近のみ） |
| 当選後の扱い | cascade のまま | 通常扱いに復帰（streak=0） |
| 連敗集中度 | 無制限 | 最大3 |

## 次のアクション（実装計画）

### Phase 1: 要件定義書・実装手順書の作成

- [ ] `docs/features/lottery/streak-bounded-cascade/requirements.md` 新規作成
- [ ] `docs/features/lottery/streak-bounded-cascade/implementation-plan.md` 新規作成

### Phase 2: 既存ドキュメントの更新

- [ ] `docs/requirements/lottery-system.md` の連鎖落選セクション更新
  - 連敗3制約を追加
  - cascade 判定基準を「sessionLosers」から「streak ベース」に変更
  - must-win 優先処理の仕様を記載
- [ ] `docs/SPECIFICATION.md` の連鎖落選説明を最新化

### Phase 3: 実装

主要変更箇所: `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`

- [ ] `processSession` 改修
  - `Map<Long, Integer> streakByPlayer` を導入し、各試合処理後に更新
  - 申込のなかった人の streak をリセット（解釈B）
  - 全 tracked プレイヤー集合を維持
- [ ] `processMatch` 改修
  - cascade 判定を `sessionLosers` から streak ベースに変更
  - must-win 優先処理を最上位に追加
  - cascade 内で streak 2 を streak 1 より優先
- [ ] `executeReLottery` も同様の変更（必要に応じて）

### Phase 4: テスト

- [ ] 既存テストの期待値修正
  - `LotteryServiceExecuteAndConfirmTest`
  - `LotteryControllerSessionResultTest`
  - `LotteryControllerCancelTest`
  - `LotteryControllerOfferTest`
  - その他 cascade 挙動を assertion しているテスト全般
- [ ] 新規テスト追加
  - 連敗3で must-win 発動シナリオ（11人/7試合/定員10 の期待パターン検証）
  - スキップで streak リセット（解釈B）
  - must-win 同士の定員超過 edge case
  - 当選後の streak リセット動作

### Phase 5: 動作確認・PR

- [ ] ローカルビルド: `cd karuta-tracker && ./gradlew build`
- [ ] テスト: `./gradlew test`
- [ ] フロントエンド影響確認（DTO 変更があれば修正）
- [ ] PR 作成・レビュー依頼

## 設計上の留意点・ハマりどころ

### streak の永続性

streak は同一セッション内のみ追跡。月跨ぎはリセット。
月内連続落選救済 (rescue) は別概念で並存（streak と rescue は同時に成立し得る）。

### 再抽選 (re-lottery) との整合性

セッション再抽選 `executeReLottery` でも同じロジックを適用する必要あり。要確認。

### キャンセル待ち順番引き継ぎとの整合性

現行は「連続する試合番号で前試合のキャンセル待ち順番を引き継ぐ」（[LotteryService.java:262](../../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java#L262)）。streak 導入後もこの挙動を維持する想定。

### must-win 同士の競合

理論上は連敗3制約違反になり得るが、実用上は希。現状は「must-win 同士で抽選」で許容する。
将来的に厳密化が必要なら、より複雑な制約解決（例: バックトラッキング）が必要。

## 議論の中で計算した参考値

### シナリオ1: A は M1+M2、B は M2 のみ、C は M1 のみ申込（n=10）

| 方式 | A: 半端参加 | A: 両方落選 | B 落選 | C 落選 |
|---|---|---|---|---|
| 独立抽選 | 16.5% | 0.8% | 9.1% | 9.1% |
| 現行 cascade | 8.3% | 9.1% | 8.3% | 9.1% |
| 人単位 all-or-nothing | 0% | 9.1% | 9.1% | 9.1% |

### シナリオ2: 多重申込12人(M1) + 単独申込6人(M2) (定員10)

| 方式 | 多重申込 per-app | M-R 当選率 | M1消化 | 半端参加 |
|---|---|---|---|---|
| 独立抽選 | 69.4% | 55.6% | 100% | 高 |
| 現行 cascade（M1→M2） | 67.7% | 62.5% | 100% | 中 |
| 申込者数多い順（M2→M1） | - | 55.6% | 100% | 低 |

## 参考リンク

- 主要実装ファイル: [karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java](../../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java)
- 既存仕様: [docs/requirements/lottery-system.md](../../../requirements/lottery-system.md)
- 既存改修履歴: `docs/features/lottery/fix-*` ファイル群（cascade を「自動落選」から「低優先度」に変更した v2 改修など）
