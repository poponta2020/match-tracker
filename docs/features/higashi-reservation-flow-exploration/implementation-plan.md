---
status: completed
---
# higashi-reservation-flow-exploration 実装手順書

## 実装タスク

### タスク1: .gitignore 更新
- [x] 完了
- **概要:** 探索スクリプトの出力先ディレクトリが Git 管理対象に入らないようにする。機密情報（セッションCookie情報を含むHTMLスナップショット等）の誤コミット防止。
- **変更対象ファイル:**
  - `.gitignore` — 以下のエントリを追加
    ```
    # exploration output (contains sensitive session/auth data)
    scripts/room-checker/exploration-output/
    ```
- **依存タスク:** なし
- **対応Issue:** #539
- **完了条件:** `git check-ignore scripts/room-checker/exploration-output/test` が hit する

### タスク2: 探索スクリプト実装
- [x] 完了
- **概要:** 東区民センター予約フローを辿る Playwright スクリプトを新規作成する。ログインから「申込確定ボタン押下直前」まで進め、各ステップの HTML/PNG/JSON を保存する。申込確定処理はコード上に書かない。
- **変更対象ファイル:**
  - `scripts/room-checker/explore-higashi-reservation.js` — 新規作成
- **実装要素:**
  - CLI引数パース: `--confirm-no-submit`（必須）, `--room`, `--date`, `--slot`, `--output-dir`
  - 実行前チェック:
    - `--confirm-no-submit` フラグ未指定 → `process.exit(1)` + 明示的エラーメッセージ
    - 環境変数 `SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` 未設定 → `process.exit(1)`
    - 出力ディレクトリ作成可否チェック
    - 実行日と同じ日付指定の場合は警告表示
  - `recordStep(page, stepNumber, stepName, nextActionHint)` 関数:
    - `page.url()`, `page.title()`, `page.content()` 取得
    - `page.screenshot({ fullPage: true })` 取得
    - form構造を `page.evaluate` で取得（`<input>`/`<select>`/`<button>` の name/id/type/value）
    - ViewState 系 hidden fields の長さ / 先頭20 / 末尾20 / SHA256 / 前ステップからの変化
    - `document.cookie` のキー一覧
    - 全て `step-NN-xxx.html` / `.png` / `.json` に保存
    - stdout に進捗と「次の操作」ヒントを表示
    - 2秒待機してから次へ進む
  - 探索フロー（スクリプト本体）:
    - step 01: ログインページ `UserLogin.aspx` を開く → recordStep
    - step 02: ユーザーID/パスワード入力 → ログインボタンクリック → recordStep
    - step 03: ログイン成功確認（`#ctl00_cphMain_WucImgBtnHistory_imgbtnMain` 等の目印要素存在チェック） → recordStep
    - step 04: メニュー → 空き状況検索 → 施設選択（103）→ 部屋選択（041/042）→ 空き状況月表示（既存 `sync-higashi-availability-to-db.js` のロジック流用） → recordStep（複数ステップに分けて各遷移を記録）
    - step 05: 指定日付・スロットの `○` セルをクリック → recordStep
    - step 06以降: 表示された次画面を観察しながら次ステップへ進む（「次へ」「確認」等のボタンを1つずつクリック） → 各 recordStep
    - 最終 step: 申込確定ボタンを視認できる画面に到達したら、そのボタンの selector をログに記録 → **クリックせずスクリプト終了**
    - スクリプト終了時に `summary.json` を出力（各ステップのメタ情報を配列で集約）
  - エラーハンドリング:
    - ログイン失敗 → HTML/PNG 保存 + exit 1
    - 想定外画面遷移 → 現状保存 + メッセージ表示 + exit 1
    - 申込ボタンが見つからない → 詰まった時点の状態を保存 + summary に記録 + 正常終了扱い（情報が取れただけでも成果）
  - コメントに明示: 「申込確定ボタンクリック処理はここに書かない。絶対に追加しないこと」
- **参考実装:**
  - [scripts/room-checker/scrape-higashi-history.js](scripts/room-checker/scrape-higashi-history.js) — ログインフロー
  - [scripts/room-checker/sync-higashi-availability-to-db.js](scripts/room-checker/sync-higashi-availability-to-db.js) — 施設/部屋/月/日付遷移
  - [scripts/room-checker/explore-higashi-availability.js](scripts/room-checker/explore-higashi-availability.js) — 探索スクリプトの構成
- **依存タスク:** タスク1 (#539)
- **対応Issue:** #540
- **完了条件:**
  - スクリプトが `--confirm-no-submit` なしで実行すると即時終了する
  - スクリプトが必須環境変数なしで実行すると明示的エラーで終了する
  - 構文エラーなくロードできる（`node --check scripts/room-checker/explore-higashi-reservation.js`）

### タスク3: ユーザーによる探索実行（手動タスク）
- [ ] 完了
- **概要:** タスク2で作成したスクリプトをユーザーがローカル環境で実際に実行し、探索データを取得する。
- **変更対象ファイル:** なし（実行のみ）
- **作業手順:**
  1. 環境変数 `SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` が設定されていることを確認
  2. 実行日から数日後（例: 3日後）の空きスロットが存在することを事前確認
  3. 以下を実行:
     ```bash
     cd scripts/room-checker
     node explore-higashi-reservation.js --confirm-no-submit --room さくら
     ```
  4. Chromium が立ち上がり、各ステップが自動実行される様子を目視確認
  5. 完了後 `exploration-output/higashi-reservation-{timestamp}/` に生成されたファイル群を確認
  6. エラーが出た場合はユーザーが Claude にフィードバックしてタスク2を修正
  7. 余力があれば `--room かっこう` でも実行して単独予約パターンを記録
- **依存タスク:** タスク2 (#540)
- **対応Issue:** #541
- **完了条件:**
  - `exploration-output/` 配下に各ステップの HTML/PNG/JSON と `summary.json` が生成される
  - 「申込トレイ相当の画面」または「申込確定ボタン直前の画面」まで到達できている（到達できなかった場合も、どこで止まったかが記録されている）

### タスク4: findings.md 執筆（Claudeによる分析）
- [ ] 完了
- **概要:** タスク3で得られた探索データ（summary.json + HTML + PNG）を Claude が読み込み、分析ドキュメントを執筆する。このドキュメントが後続 higashi-reservation-proxy 機能の要件定義のインプットとなる。
- **変更対象ファイル:**
  - `docs/features/higashi-reservation-flow-exploration/findings.md` — 新規作成
- **ドキュメント構成:**
  - 探索環境・実行日時（実際の実行ログから抽出）
  - 各ステップの詳細（URL / 遷移方式 / DOM要素 / form / ViewState挙動）
  - **申込トレイ相当画面の有無判定**
  - **ViewState / EventValidation の技術分析**
    - 長さ、ステップごとの変化パターン、Java側で抽出・再注入可能か
  - 完了画面の特徴（探索では実到達しないが、情報断片があれば記載）
  - 発生したエラー・ハマりどころ
  - **プロキシ実装時の推奨アプローチ**
    - (A) リバースプロキシで実現可能か yes/no の判断
    - (B) 推奨実装方式（kaderu-reservation-proxy との共通化可否 / ViewState処理モジュール設計 / その他）
    - (C) 実装時のリスク・注意点
  - **kaderu-reservation-proxy との統合設計の提案**
    - `venue-reservation-proxy` 統合機能へのリファクタ推奨有無
    - リファクタする場合の設計方針
- **依存タスク:** タスク3 (#541)
- **対応Issue:** #542
- **完了条件:**
  - `findings.md` が `docs/features/higashi-reservation-flow-exploration/` 配下に存在する
  - 後続 higashi-reservation-proxy の要件定義を開始する際に、この findings.md を参照するだけで主要な技術判断（リバースプロキシ可否、ViewState処理方針、統合設計可否）ができる状態になっている
  - ユーザーがドキュメントをレビューして承認する

## 実装順序

```
タスク1 (.gitignore)
  ↓
タスク2 (探索スクリプト実装)
  ↓
タスク3 (ユーザー実行)  ← 手動タスク
  ↓
タスク4 (findings.md 執筆)
```

すべて逐次実行。並列化の余地はなし（依存関係が一直線）。
