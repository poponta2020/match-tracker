---
name: ship_pr1072_card_division_header_format
description: 札分けテキストのヘッダ整形(M/D(曜) 会場名・囲みかっこ削除・空白行・位のあと全角スペース)を出荷(2026-07-16、PR #1072、quickfix・BEのみ)
metadata:
  node_type: memory
  type: ship
---

札分けテキスト（札分け確認画面の送信用テキスト＋LINEリマインダー本文）の**フォーマット改善**を出荷（2026-07-16、**PR #1072**）。ユーザー直依頼の quickfix（親Issueなし・requirements docなし）。

**内容**（唯一の生成元 `CardDivisionTextService.buildText` のみ改修）:
- タイトル行の囲みかっこ `【】` を**削除**
- 日付に**半角括弧の曜日**を付与 → `M/D(曜) 会場名`（すべて半角、`)` と会場名の間は半角スペース、曜日は `LocalDate.getDayOfWeek()` から `月火水木金土日` を算出）
- タイトル行の**直後に空白行1行**（先頭試合の前を `\n\n`）
- 一の位/十の位の行は**「位」のあとに全角スペース**（`一の位　1.3.4.5.7`。数字の詰まり解消）

出力例:
```
7/5(日) かでる2・7

1試合目：一の位　1.3.4.5.7
2試合目：0.2.9　91(きり)抜き
3試合目：十の位　1.3.5.6.7
```

**要件の確定過程**: ユーザーが対話中に3段階で refine（①曜日+空白行 → ②「かっこいらない」で迷い → ③最終「M/D(曜) 会場名・すべて半角」で確定 → ④「【】も不要」→ ⑤位のあと全角スペース追加）。曜日は日付から算出するため、ユーザー例の `6/11(日)`（実際は木曜）は書式イラストと判断。

**影響範囲**: BEテキスト生成のみ。フロント `CardDivision.jsx` はBEテキストを `whitespace-pre` textarea でそのまま表示＝無改修。`/pairings` の対戦組み合わせテキスト（`cardRules.js`）は**別生成元・別フォーマット**（位のあと空白なし・決まり字なし＝既に意図的に divergent）で無影響。DB/マイグレーション不要。ゴールデン・パリティテストは (種別,digits,removedCard) タプルのみ検証で表示文言は対象外ゆえ無影響。

**変更ファイル**: `karuta-tracker/src/main/java/.../service/CardDivisionTextService.java`（buildText＋renderRule＋`japaneseWeekday` ヘルパ新設）・`CardDivisionTextServiceTest.java`・`LineNotificationServiceCardDivisionSendTest.java`（代表フィクスチャ）・`docs/spec/matching.md` §札分け。

**auto-review**: 1R pass（Codex medium・blockers/should_fix/nits 全0・23.6k tokens）。BEテスト3クラス（CardDivisionTextServiceTest / LineNotificationServiceCardDivisionSendTest / CardDivisionReminderSchedulerTest）green。CIの test は pending のままマージ（v0.9.0 方針・赤なら追修正）。

**残課題（未対応・要検討）**: `/pairings` PairingSummary（cardRules.js）の 位行は依然クランプ表示。同じ「札組」でも 札分け確認/LINE と /pairings で見た目が divergent。統一するなら別PRで cardRules.js の description を検討（docstringは「cardRules.js 変更しない」方針のため要判断）。
