---
name: ship-pr1096-line-chat-reserve-broadcast
description: PR#1096 札分け全体配信のLINEチャット予約送信化 タスク1-6 の出荷記録
type: ship
---

# ship PR #1096 — line-chat-reserve-broadcast タスク1-6（PR#1）

- **PR**: [#1096](https://github.com/poponta2020/match-tracker/pull/1096) feat(line-chat-reserve): 札分け全体配信のLINEチャット予約送信化 タスク1-6（PR#1）
- **日付**: 2026-07-17
- **親Issue**: #1084（T7・T8 残のため未クローズ）／子Issue: #1085-#1091（T0-T6・PR本文 closing keyword でマージ時クローズ）

## 内容
札分け全体配信を Messaging API push（通数課金）から LINE OAM の**チャット予約送信**（無料通数対象外）へ差し替える基盤。既存 push は 1グループ1bot 制約で 10体ローテ不成立のため**単一botフォールバック**へ役割変更。

- T1 スキーマ（line_chat_reservations 8状態・部分ユニーク WHERE status<>CANCELLED、LineBroadcastGroup に chat_room_id/name、LineMessageLog CHECK 25→26値）
- T2 スケジューラ（20:00バッチ＋15分リコンサイル、送信時刻resolver共通化、再作成はCANCELLED行あり限定）
- T3 ワーカーAPI＋ServiceTokenInterceptor（X-Service-Token 定数時間比較・状態遷移検証）
- T4 フォールバックpushガード（予約状態分岐・CANCEL_PENDING抑止）＋管理者アラート
- T5 予約状況の管理API＋管理画面（task-implementer委譲）
- T6 VM常駐ワーカー骨格＋ロジック層＋mock-POテスト（Page Objectは実セレクタ未実装＝T7で確定・task-implementer委譲）

## レビュー（auto-review-loop）
**7ラウンド（全high）で R7 verdict=pass 収束**（累計 ~704k tok・token budget 500k をユーザー判断「続けてください」で超過継続）。
実find 7件修正（取消の誤チャット削除防止・CANCEL_PENDING認証失効通知・Dockerfile lockfile固定・無効化グループの予約取消・期限切れPENDING失効・storageState明示エラー・AC-10ワーカー失敗アラート）＋偽陽性3件棄却。詳細は harness memory `auto_review_round_pr1096`。

## AC / 検証
- auto-test（AC-1〜7,10,14）: backend全suite green（MatchTrackerApplicationTests contextLoads でフルcontext boot確認）、frontend 738 green、worker 27 green＋tsc＋lint。
- **AC-13 本番DB適用: 完了**（create_line_chat_reservations.sql を RunMig.java で本番Render Postgresへ適用・introspect照合済）。
- verify型（AC-8 dry-run / AC-9 画面）はロジック/Vitestのみ・e2e未駆動。AC-11(Phase1手動)=GO済、AC-12(Phase3観測)=T8。

## 残（ユーザー協働・自動フロー対象外）
- T7（#1092）Phase2ローカルPoC: chat.line.biz 実DOM調査→Page Objectセレクタ確定→実走・PR#2。ワーカーはこれまで実運用不可（Page Object全stub）。
- T8（#1093）Phase3 VMデプロイ＋2週間セッション観測→本番投入。

## デプロイ影響
マージ後 Render 自動デプロイで予約スケジューラ稼働。ワーカー未稼働のため予約はPENDING滞留→フォールバックpushが拾う設計（graceful degradation）。現状 org2 グループは GROUP bot 未セットアップのため実送信は発生せず。
