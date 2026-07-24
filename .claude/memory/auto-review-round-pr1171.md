---
name: auto-review-round-pr1171
description: auto-review PR #1171
type: project
---

PR#1171(対戦組み合わせ編集: キャンセル者アラート＆保存エラー明示)の auto-review 2R収束(全high、R2 pass、累計約166k=R1 58k+R2 107k)。

R1(needs_changes・実find1件・偽陽性ゼロ): キャンセル者アラートの**試合切替 stale 発火**。別useEffectで pairings と matchNumber を突き合わせていたため、試合切替直後は pairings が前試合のままで stale 発火し「別試合の選手名で誤アラート／読み込み前OKで別試合の組を現在試合のドラフト保存」の競合。Codexの指摘は正当(React の effect は matchNumber 変更後の最初のコミットで pairings がまだ旧値・条件falseでも既存 cancelAlert を消さない)。

修正: アラート判定を**読み込み effect 内(cached＝当該matchNumberの確定データ)へ畳み込み**、全分岐で setCancelAlert、データ反映(loadExistingPairingsToState)と同一バッチにして根絶。shouldTriggerCancelAlert/collectCancelledNames は cached を引数に再利用(テスト維持)。キャンセルあり→なし試合切替の実コンポーネント回帰テスト追加(jsdomはオーバーレイのヒットテストをしないためモーダル表示中でもタブ押下で transition を再現可)。

R2 pass。**教訓=別effectで「state A(pairings)とstate B(matchNumber)の対応」を前提に発火すると、両者が別コミットで更新される瞬間に stale する。対応が保証される単一ソース(cached を設定する load effect)に判定を寄せるのが堅牢**。意図設計8項目(read-time非破壊/試合単位/PLAYER編集可/hasResult除外/cancelledEmptied除外/saveError二経路/R1修正済み)を毎R先回り明記でFPゼロ。中立cwd+stdin+スキル禁止ガード踏襲。
