---
status: completed
design_required: false
completed_sections: [delta動機, 変更後挙動, 変わらないもの, Acceptance Criteria, 技術制約]
next_section: 技術計画
---
# 予約取り込みを手動ボタンのみに 要件定義書

## 1. 概要
かでる／東区民の「予約→練習日 sync」を、**30分毎の定期 cron 稼働から手動ボタン起動のみ**に変える。既存の手動ボタン（練習日登録画面）が通常運用の主たる起点になるよう、手動ワークフローに東区民ステップを追加し、**1ボタンでかでる＋東区民をまとめて取り込む**（案A）。純 GHA workflow（Java 変更なし）。定期 cron 停止後も各 workflow の `workflow_dispatch`（GitHub UI からの直接起動）は保守・フォールバック経路として残す。

## 2. 背景・動機
- 予約→練習日 sync は現在 `sync-kaderu-reservations.yml`・`sync-higashi-reservations.yml` が30分毎に常時稼働（「常時反映」の正体）。GHA 無料枠の無駄。
- 手動同期ボタンは既に練習日登録画面（[PracticeForm.jsx](../../../karuta-tracker-ui/src/pages/practice/PracticeForm.jsx)）に実装済み。定期 cron を止めれば「予約したその場でボタンを押す」導線に一本化できる。
- 東区民には手動経路が無いため、手動ワークフローに東区民ステップを足す。東区民は北大(hokudai)の会場なので **hokudai 押下時のみ**東区民も同期。

## 3. 変更後の挙動（delta）
| 対象ファイル | 変更 |
|---|---|
| `.github/workflows/sync-kaderu-reservations.yml` | `schedule:` ブロック削除（`workflow_dispatch` は維持） |
| `.github/workflows/sync-higashi-reservations.yml` | `schedule:` ブロック削除（`workflow_dispatch` は維持） |
| `.github/workflows/sync-kaderu-reservations-manual.yml` | 東区民ステップを追加。`if: inputs.org == 'hokudai'`（wasura は東区民を使わない）。env は `SAPPORO_COMMUNITY_USER_ID/PASSWORD` ＋ `KADERU_DATABASE_URL`（`sync-higashi-reservations.yml` と同一）。`run: node sync-higashi-reservations.js --months 2` |

- 結果：hokudai でボタン押下 → かでる(hokudai)＋東区民を取り込み。wasura → かでる(wasura)のみ。
- セーフティネット cron は付けない（押すまで練習日は出ない＝意図どおり）。

## 4. Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | `sync-kaderu-reservations.yml` から `schedule:` が削除され、`workflow_dispatch` が残る | manual |
| AC-2 | `sync-higashi-reservations.yml` から `schedule:` が削除され、`workflow_dispatch` が残る | manual |
| AC-3 | `sync-kaderu-reservations-manual.yml` に東区民ステップが `if: inputs.org == 'hokudai'` 付きで追加され、env と script が `sync-higashi-reservations.yml` と一致する | manual |
| AC-4 | hokudai でボタン押下 → かでる＋東区民が練習日として取り込まれる。wasura 押下 → かでるのみ（東区民ステップは skip） | verify（実起動1回） |
| AC-5 | 定期 cron 停止後、予約→練習日 sync が手動起動以外で走らない | manual（Actions 実行履歴で cron 発火が無いこと） |

## 5. Non-goals
- 空き状況スクレイプ（`scrape-kaderu.yml`／`scrape-higashi-availability.yml`）や隣室通知の変更 ← 別 feature（隣室軽量化）
- 取り込み**通知の宛先**変更 ← 別 feature（通知宛先拡大）
- 完了サマリーに東区民の件数を集計・分離表示すること（`extractSummary` はかでるログの先頭一致を拾う現行のまま。東区民は同期されるが件数は itemize しない）
- `sync-reservations.js`／`sync-higashi-reservations.js` のスクリプト本体・DOM 抽出ロジックの変更
- 手動ワークフローの concurrency グループ変更（`kaderu-reservation-sync` のまま）

## 6. 技術的制約・契約
- GHA workflow YAML のみの変更。Java/JS のプロダクションコード変更なし（＝自動テスト対象の surface が無く、AC は実質 manual/verify 中心。これは本改修の性質上不可避）。
- 手動ワークフローの `org` input は `hokudai`／`wasura` の choice。東区民ステップは hokudai のみ。
- 東区民同期は既存 `sync-higashi-reservations.js`（idempotent な upsert 前提）を流用。二重起動（別途 higashi の workflow_dispatch）でも壊れない。
- docs 更新: 会場予約同期の cadence を「定期 cron → 手動ボタンのみ」に更新（該当 spec / feature ドキュメントを in-place 更新＋変更履歴）。

## 7. 設計判断の根拠
- **案A（1ボタン統合）採用**: 東区民専用インフラ（イベントテーブル/サービス/scheduler/ボタン）を複製する案Bは重い。既存かでる手動WFに1ステップ足すだけで「押したら両方取り込む」が実現でき、ユーザーの1アクションの感覚とも一致。
- **東区民ステップを hokudai 条件付き**: 東区民は北大の会場。wasura で東区民を叩く意味がなく、無駄な起動・誤同期を防ぐ。
- **cron 完全停止（セーフティネットなし）**: ユーザーの明示要望「押した時だけ」に忠実。押し忘れが問題化したら後から日1回 cron を足せる。
