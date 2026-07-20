---
status: completed
---
# 予約取り込みを手動ボタンのみに 実装手順書

純 GHA workflow・単一タスク。Java/JS 変更なし。

## 実装タスク

### タスク1: 予約 sync 定期 cron 停止＋手動WFに東区民ステップ追加
- [x] 完了
- **目的:** 予約→練習日 sync の定期 cron を止め、手動ボタン1押下でかでる＋東区民（hokudai時）を取り込めるようにする。
- **対応AC:** AC-1〜AC-5
- **主な変更領域:**
  - `.github/workflows/sync-kaderu-reservations.yml`: `schedule:` ブロック削除（`workflow_dispatch` 維持）
  - `.github/workflows/sync-higashi-reservations.yml`: `schedule:` ブロック削除（`workflow_dispatch` 維持）
  - `.github/workflows/sync-kaderu-reservations-manual.yml`: 東区民ステップ追加（`if: inputs.org == 'hokudai'`、`working-directory: ./scripts/room-checker`、`run: node sync-higashi-reservations.js --months 2`、env=`SAPPORO_COMMUNITY_USER_ID`/`SAPPORO_COMMUNITY_PASSWORD`/`DATABASE_URL: KADERU_DATABASE_URL`）。既存の wasura ステップの後ろに置く
  - docs: 会場予約同期の cadence 記述（定期→手動のみ）を該当 spec/feature ドキュメントで in-place 更新＋変更履歴
- **依存タスク:** なし
- **対応Issue:** #1138
- **必要なテスト:** GHA workflow YAML のため自動テスト対象外。**verify（AC-4）**: 実際に hokudai でボタン押下し、かでる＋東区民の run が走り練習日が取り込まれることを確認。wasura で東区民ステップが skip されることを確認。**manual（AC-1/2/3/5）**: workflow ファイル差分と Actions 実行履歴で確認
- **完了条件:** 3ファイルの差分が AC-1〜3 を満たす・（可能なら）hokudai/wasura の実起動 verify・既存 build/test green（本タスクは Java/JS 非改変のため既存 green のまま）

## 実装順序（Wave）
- Wave 1: タスク1（単一）
