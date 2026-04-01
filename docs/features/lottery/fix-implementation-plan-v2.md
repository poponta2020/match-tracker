---
status: completed
---

# 抽選機能 連鎖落選ロジック改修 実装手順書

## 実装タスク

### タスク1: processMatch の連鎖落選ロジック修正
- [x] 完了
- **概要:** `LotteryService.processMatch` の Step 1〜2 を修正し、前試合落選者（cascadeLosers）を自動落選ではなく低優先度扱いにする。remaining の抽選後に余り枠があれば cascadeLosers を当選させる。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — processMatch メソッドの L259-338 を修正。remaining の抽選後に余り枠を計算し、cascadeLosers から余り枠分を当選させるロジックを追加
- **依存タスク:** なし
- **対応Issue:** #233

### タスク2: 仕様書・ドキュメント更新
- [x] 完了
- **概要:** 連鎖落選の仕様説明を「自動落選」から「空きがあれば当選、定員超過時は優先的に落選」に更新する。テストケースも更新。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 連鎖落選の説明を更新
  - `docs/requirements/lottery-system.md` — 連鎖落選のアルゴリズム説明・用語定義・テストケースを更新
- **依存タスク:** タスク1
- **対応Issue:** #234

---

## 実装順序

1. **タスク1: processMatch 修正**（コア修正）
2. **タスク2: 仕様書更新**（タスク1の修正内容を反映）
