---
status: completed
---

# 抽選結果コピー可能テキスト プレビュー画面表示 改修実装手順書

## 実装タスク

### タスク1: LotteryManagement.jsx にコピー領域を追加
- [x] 完了
- **概要:** `LotteryManagement.jsx` のプレビュー結果セクションに、LINE告知用テキストの textarea とコピーボタンを追加する。`phase === 'preview'` のときはコピーボタンを警告色（オレンジ）に切り替える。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx`
    - `lotteryResultText` から `buildCopyText` / `hasAnyWaitlisted` を import
    - `copyText` / `copyFeedback` state を追加
    - `previewResults` 変化時に `buildCopyText` を呼ぶ `useEffect` を追加
    - `handleCopy` ハンドラを追加（`navigator.clipboard.writeText` + フィードバック表示）
    - プレビュー結果ブロック内（既存セッション別結果と確定/通知ボタン群の **後**）にコピー領域UIを挿入
    - フェーズに応じてコピーボタンの色を切り替え（preview: orange、confirmed: blue）
    - プレビュー時は「※ プレビュー（未確定）」ラベルを併記
- **依存タスク:** なし
- **対応Issue:** #625

### タスク2: ドキュメント更新
- [x] 完了
- **概要:** `docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md` のうち、本改修で更新が必要な箇所を反映する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 抽選プレビュー画面でも告知用テキストをコピー可能になった旨を追記
  - `docs/SCREEN_LIST.md` — 抽選管理画面の機能一覧にコピー領域を追記
  - `docs/DESIGN.md` — 必要に応じてフロント設計の該当箇所を更新
- **依存タスク:** タスク1
- **対応Issue:** #626

## 実装順序
1. タスク1（依存なし）— UI 実装本体
2. タスク2（タスク1 に依存）— ドキュメント更新

## 動作確認手順（手動）
1. `npm run dev` でフロント起動
2. 管理者ロールでログイン → 抽選管理画面を開く
3. 抽選プレビュー実行 → コピー領域が表示され、コピーボタンがオレンジ、「※ プレビュー（未確定）」ラベルが付くことを確認
4. テキストエリアに `buildCopyText` の出力が反映されていることを確認
5. コピーボタン押下 → クリップボードに反映、「コピーしました」表示が出ることを確認
6. 補欠者なしのケース（全セッションで抽選漏れがない）でコピーボタンが disabled になることを確認
7. 抽選確定実行 → コピーボタンが青に切り替わり、「※ プレビュー（未確定）」ラベルが消えることを確認
8. `LotteryResults.jsx`（抽選結果画面）側の表示・コピー動作にデグレがないことを確認
