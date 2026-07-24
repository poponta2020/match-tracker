---
name: ship-pairing-cancel-alert-and-save-errors
description: 対戦組み合わせ編集: キャンセル者アラート＆保存エラー明示
type: project
---

pairing-cancel-alert-and-save-errors を出荷（2026-07-24、**PR #1171 マージ済み** f085991f、純フロントエンド・BE無改修・スキーマ変更なし）。親Issue #1168・子#1169/#1170 クローズ。

**内容**: 対戦組み合わせ編集(/pairings)のUX改善2点。①作成済み組にキャンセル者が残る試合を閲覧すると単一OKアラートで通知しOKで現在の試合を編集モード化(空きに実体化)。②「確定して保存」を常時押下可にし、対戦相手未設定の組があれば押下後に未設定選手名を挙げたエラーを保存ボタン直上のフッターに表示。read-time非破壊維持。

**auto-review**: 2R収束(全high、累計約166k)。R1 実find1=キャンセル者アラートの試合切替 stale 発火(別effectで pairings と matchNumber を突き合わせる競合)→アラート判定を読み込みeffect内のcachedベースへ畳み込んで根絶＋実コンポーネント回帰テスト追加。R2 pass・偽陽性ゼロ。詳細は [[auto-review-round-pr1171]]。

**advisor完了前チェックの効果**: 変更2のエラー表示位置をdesign宿題にしてdesign-screenスキップ→上部共通errorバナー既定化していた(組数多いと保存ボタンから見切れ変更2の目的を損なう)を捕捉→saveError専用stateでフッター描画に修正。

**AC**: auto-test13件はテストで担保(FE全878 green・pairings 324含む)。AC-7(PLAYER/ADMIN同一挙動)はverify項目で標準lean フローでは実動作未確認(実機確認は宿題)。lint0err。実装詳細は [[impl-pairing-cancel-alert-save-errors]]。
