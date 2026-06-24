---
status: completed
---
# Kaderu予約取り込みマルチ団体対応 実装手順書

## 実装タスク

### タスク1: sync-reservations.js のマルチ団体対応
- [x] 完了
- **概要:** `--org <code>` 引数を追加し、`organization='hokudai'` のハードコードを引数ベースに変更。主要ログに `[<org_code>]` プレフィックスを付与する
- **変更対象ファイル:**
  - `scripts/room-checker/sync-reservations.js`
    - 引数パーサーに `--org <code>` を追加（必須化、未指定ならエラー終了）
    - `syncToDb()` 関数の organization 取得SQLを `WHERE code = $1` のパラメータ化に変更（現状は `WHERE code = 'hokudai'` の固定文字列）
    - エラーメッセージを `組織 '${orgCode}' が見つかりません` に変更
    - `console.log` / `console.warn` の主要メッセージ（スクレイピング進捗、組織ID、処理結果サマリ、詳細リスト）に `[<orgCode>]` を付与
    - `main()` で `--org` 引数を受け取り、`syncToDb()` に `orgCode` を渡す
- **依存タスク:** なし
- **対応Issue:** #784

### タスク2: GitHub Actions workflow の2ステップ化
- [x] 完了
- **概要:** `sync-kaderu-reservations.yml` を hokudai / wasura の2ステップ実行に変更。2ステップ目は `if: always()` で hokudai 失敗時にも実行
- **変更対象ファイル:**
  - `.github/workflows/sync-kaderu-reservations.yml`
    - 既存の `Sync reservations to practice sessions` ステップを2つに分割
      - `Sync reservations (hokudai)`: 既存 Secrets を使用、`--org hokudai` を付与
      - `Sync reservations (wasura)`: `WASURA_KADERU_USER_ID` / `WASURA_KADERU_PASSWORD` を使用、`--org wasura` を付与、`if: always()` を付与
    - `concurrency` 設定は変更しない（既存の `kaderu-reservation-sync` を維持）
- **依存タスク:** タスク1（スクリプトが `--org` 引数を受け付ける状態であること）
- **対応Issue:** #785

### タスク3: ドキュメント更新
- [x] 完了
- **概要:** `SPECIFICATION.md` の「4.4 かでる2・7 予約同期」節を、両団体対応の記述に更新
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
    - 4.4.1 概要: 「北大かるた会のみ」→「北大かるた会・わすらもち会の両団体」に変更
    - 4.4.3 処理フロー: 「hokudai → wasura の順で workflow が2回スクリプトを呼ぶ」記述を追加
    - 4.4.4 トリガー / 4.4.5 スクリプト構成: `--org <code>` 引数の説明を追記
    - 認証情報の節（あれば）に `WASURA_KADERU_USER_ID` / `WASURA_KADERU_PASSWORD` を追記
- **依存タスク:** タスク1, タスク2（実装内容と整合させるため）
- **対応Issue:** #786

### タスク4: GitHub Secrets 登録依頼（PR 上で明示）
- [x] 完了
- **概要:** PR の本文で「マージ前に GitHub Secrets へ以下2つを登録してください」とユーザーに明示する。Claude 側で登録は行わない
- **登録する Secrets:**
  - `WASURA_KADERU_USER_ID`: わすらもち会の Kaderu 2.7 利用者ID
  - `WASURA_KADERU_PASSWORD`: わすらもち会の Kaderu 2.7 パスワード
- **登録手順:**
  1. GitHub リポジトリ → Settings → Secrets and variables → Actions
  2. 「New repository secret」で上記2つを追加
- **依存タスク:** タスク2（workflow が新 Secrets 名を参照する状態であること）
- **対応Issue:** #787

### タスク5: 動作確認
- [x] 完了
- **概要:** PR マージ後、GitHub Actions が想定通り動作することを確認
- **確認内容:**
  - Actions タブで `Sync Kaderu Reservations to Practice Sessions` を `workflow_dispatch` で手動実行
  - hokudai ステップのログに `[hokudai]` プレフィックスが出ていること
  - wasura ステップのログに `[wasura]` プレフィックスが出ていること
  - 両ステップとも緑（成功）になること
  - 当日以降のわすらもち会 Kaderu 予約があれば、対応する `practice_sessions` レコードが `organization_id=<wasura>` で作成されていること（DBで `SELECT` 確認）
- **依存タスク:** タスク1〜4 全て
- **対応Issue:** #788

## 実装順序
1. タスク1（依存なし）— sync-reservations.js 改修
2. タスク2（タスク1に依存）— workflow 改修
3. タスク3（タスク1, 2に依存）— SPECIFICATION.md 更新
4. PR 作成（タスク1〜3 を1コミットにまとめる）
5. タスク4（PR 上で Secrets 登録依頼） → ユーザーが登録 → PR マージ
6. タスク5（マージ後の動作確認）
