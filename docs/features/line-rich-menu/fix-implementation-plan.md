---
status: completed
---
# LINE リッチメニュー 改修実装手順書

## 実装タスク

### タスク1: setup-richmenu.sh のURI変更
- [ ] 完了
- **概要:** リッチメニュー設定スクリプトのエリア4の遷移先URIを変更
- **変更対象ファイル:**
  - `docs/features/line-rich-menu/setup-richmenu.sh` — エリア4のURIを `/settings/notifications` → `/matches/results` に変更
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

### タスク2: リッチメニュー画像の更新
- [ ] 完了
- **概要:** 左下ボタンのテキストを「通知設定」→「結果入力」に変更、アイコンも適宜変更
- **変更対象ファイル:**
  - `docs/features/line-rich-menu/richmenu.png` — 左下ボタンの画像更新
  - `docs/features/line-rich-menu/richmenu_compressed.jpeg` — 圧縮版の更新
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）
- **備考:** 画像編集は外部ツール（Figma, Canva等）で実施。Claude Codeでは画像生成不可

### タスク3: リッチメニューの本番再登録
- [ ] 完了
- **概要:** 更新した画像とスクリプトを使ってLINE APIにリッチメニューを再登録
- **変更対象ファイル:**
  - なし（既存スクリプトを実行するのみ）
- **依存タスク:** タスク1, タスク2
- **対応Issue:** （Issue作成後に記入）
- **実行コマンド:** `bash docs/features/line-rich-menu/setup-richmenu.sh`

## 実装順序
1. タスク1（スクリプト修正）とタスク2（画像更新）は並行作業可能
2. タスク3（本番再登録）はタスク1・2の完了後に実施
