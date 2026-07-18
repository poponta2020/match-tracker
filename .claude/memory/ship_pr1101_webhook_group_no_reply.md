---
name: ship_pr1101_webhook_group_no_reply
description: PR#1101出荷（LINEグループ/複数人トークの発言にwebhookが応答しないよう fail-closed ガード追加。配信専用アカウント対策）
metadata:
  type: project
---

全体配信用のLINE公式アカウント（`@773ifizy`）を本番グループ（67名）へ参加させたところ、**メンバーが発言するたびに「コードが無効です。…」と返信してしまう**事象が発生。ユーザー要件は「原則アプリからの一方的な配信のみ・一切応答しない」ため quickfix で修正した。

- **根本原因**: `LineWebhookController` の `handleMessage` / `handlePostback` が `source.type` を検査せず、あらゆるテキストメッセージを連携コード入力として `verifyCode` → `sendReplyMessage` していた。**グループ内の発言でも `source.userId`（発言者）は取得できるため、userId の有無ではガードできない**のが罠
- **修正**: 共通の `isOneToOneUserSource(event)`（`"user".equals(source.type)`）を新設し、両ハンドラ冒頭で **fail-closed** ガード。`source.type` の欠落・未知値も無視する（LINEのwebhookは常に `source.type` を含むため、欠落＝不正ペイロード扱いが妥当）
- **join/leave は別ハンドラ**なのでグループID捕捉（`line_channels.line_group_id`）は維持。既存テストで担保
- **既存テストの `source` に `"type": "user"` を追加**（3箇所）。実際のLINEペイロードに合わせる修正でもある。これを忘れると fail-closed ガードで既存テストが落ちる
- **回帰テスト4件追加**: group メッセージ / room メッセージ / group postback / `source.type` 欠落 のいずれでも `verifyCode`・`sendReplyMessage` が呼ばれないこと
- **検証**: `LineWebhookControllerTest` 17件 green。**Testcontainers 依存テスト124件はローカルに Docker Desktop が無いため未実行**（失敗クラスは全て `TestContainersConfig` 参照＝環境要因と確認済み）。webhook に触れるテストが他に無いことを grep で確認し、CI pending のままマージ
- **auto-review**: 1R pass（effort=high、blockers/should_fix/nits すべて0、累計 53,458 tokens）。LINE連携の本番挙動を変え誤ると全選手の連携が壊れるため境界をhighに倒した
- **途中の詰まり**: Codex CLI 0.130.0 が `config.toml` の `model = "gpt-5.6-sol"` に非対応で `CODEX_EXIT=1`（`requires a newer version of Codex`）。`npm install -g @openai/codex@latest` で **0.144.5** に更新して解決
- **docs**: `docs/spec/notifications.md` のアカウント紐付けフローに「1:1トーク専用・fail-closed・join/leaveへの非影響」を追記
- **PR #1101**: <https://github.com/poponta2020/match-tracker/pull/1101>、コミット 2071961a、Issue なし（本番投入作業中にユーザーが直接報告）
