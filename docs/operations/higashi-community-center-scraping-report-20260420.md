# 東区民センター予約スクレイピング調査報告書（2026-04-20）

## 1. 目的
- 東区民センター練習（`さくら（和室）` / `和室全室`）の予約状況を、外部予約サイトから安定して取得できるかを確認する。
- 実装担当者がそのまま着手できるように、必要情報・画面遷移・DOMセレクタ・抽出ルールを整理する。

## 2. 調査概要
- 調査日時: 2026-04-20 23:30〜23:45 (JST)
- 調査環境: Node.js + Playwright (Chromium, headless)
- 対象サイト:
  - ログイン起点: `https://sapporo-community.jp/UserWebApp/Form/UserMenu.aspx`
- 備考:
  - 認証情報（ID/パスワード）はユーザーから提供済み。
  - セキュリティ上、本ドキュメントには平文資格情報を記載しない（実装時は環境変数管理）。

## 3. 必要な情報（実装前提）
- 認証情報
  - `SAPPORO_COMMUNITY_USER_ID`
  - `SAPPORO_COMMUNITY_PASSWORD`
- 対象施設・部屋の識別
  - 施設: `札幌市東区民センター`
  - 部屋: `さくら（和室）`, `かっこう（和室）`, `和室全室`
- 日時解釈
  - サイト表記は和暦（令和）
  - DB連携時は西暦 `YYYY-MM-DD` へ変換が必要

## 4. ログイン〜履歴取得の画面遷移
1. `UserLogin.aspx` に遷移  
   `https://sapporo-community.jp/UserWebApp/Form/UserLogin.aspx`
2. ID/PWを入力してログイン
3. メニュー画面 (`UserMenu.aspx`) で「申込履歴・結果」を押下
4. 履歴画面 (`Foau/UserHistory.aspx`) で一覧を取得

### 4.1 確認済みセレクタ（ログイン）
- ID入力: `#ctl00_cphMain_tbUserno`
- PW入力: `#ctl00_cphMain_tbPassword`
- ログインボタン: `#ctl00_cphMain_btnReg`

### 4.2 確認済みセレクタ（メニュー）
- 申込履歴・結果: `#ctl00_cphMain_WucImgBtnHistory_imgbtnMain`
- 空き状況検索: `#ctl00_cphMain_wucImgBtnVacantRoomsSearchLogin_imgbtnMain`

## 5. 履歴画面のDOM構造
- URL: `https://sapporo-community.jp/UserWebApp/Foau/UserHistory.aspx`
- 一覧テーブル: `#ctl00_cphMain_gvView`
- ヘッダ:
  - `状態`
  - `利用日`
  - `利用時間`（実際は `開始`, `～`, `終了` の3列）
  - `申込内容`
  - `申込日`
- 1データ行は `td` が7列:
  1. 状態
  2. 利用日
  3. 開始時刻
  4. 区切り（`～`）
  5. 終了時刻
  6. 申込内容
  7. 申込日

### 5.1 ページング
- 20件/ページ
- ページリンクは `#ctl00_cphMain_gvView` 内の `a[href*="Page$N"]`
- 例:
  - `javascript:__doPostBack('ctl00$cphMain$gvView','Page$2')`
  - `Page$3`, `Page$4` ... で順次取得可能

## 6. 抽出可能データ（実測）
- `状態`: `予約済`, `利用済`, `取消済`
- `利用日`: 例 `令和08年05月21日（木）`
- `利用時間`: 例 `18:00 ～ 21:00`
- `申込内容`: 例 `札幌市東区民センターさくら（和室） 利用申込`

## 7. 空き検索側で確認した施設・部屋コード
※ 予約同期の主データ源は「申込履歴」だが、識別子確認のため空き検索画面も調査。

### 7.1 遷移
1. メニューで「空き状況検索」押下
2. 検索方法選択画面 (`Form/SvrSelSearchType.aspx`)
3. 「施設から検索」押下: `#ctl00_cphMain_WucImageButton1_imgbtnMain`
4. 施設選択画面 (`Form/SvrSelFacilities.aspx`)

### 7.2 セレクタ
- 施設select: `#ctl00_cphMain_WucFacilitySelect_ddlFacilities`
- 部屋select: `#ctl00_cphMain_wucRoomSelect_ddlRooms`

### 7.3 確認済みコード（2026-04-20時点）
- 施設:
  - `札幌市東区民センター` = `103`
- 部屋:
  - `和室全室` = `040`
  - `かっこう（和室）` = `041`
  - `さくら（和室）` = `042`

## 8. 推奨抽出ルール
1. 履歴テーブル (`#ctl00_cphMain_gvView`) からデータ行を抽出  
   - 判定条件（推奨）:  
     - `td` が7列  
     - 3列目/5列目が時刻フォーマット（`^\d{1,2}:\d{2}$`）
2. `申込内容` に `札幌市東区民センター` を含む行のみ採用
3. `状態=取消済` は除外
4. 和暦を西暦へ変換
5. 日付単位で部屋を集約し、判定:
   - `和室全室` があれば「全室」
   - `さくら` + `かっこう` が同日なら「全室」
   - `さくら`のみなら「単室」
   - `かっこう`のみは運用対象外（自動反映しない / 要確認キュー）

## 9. 運用・実装上の注意
- 直接URLアクセスで `HttpClientError.html` になるケースがある。  
  必ず「ログイン後のボタン操作」で遷移すること。
- 夜間に `OutsideServiceTime.html` が返る時間帯がある。  
  失敗時はリトライまたは次回ジョブ再実行前提にする。
- 履歴テーブル先頭にページャー行がある。  
  ヘッダ/ページャーを誤ってデータ扱いしないこと。
- 資格情報は環境変数化し、リポジトリへ平文保存しないこと。

## 10. 実装担当向け最小出力スキーマ（提案）
- `date` (`YYYY-MM-DD`)
- `status` (`予約済` / `利用済` / `取消済`)
- `startTime`
- `endTime`
- `facilityName`
- `roomName` (`さくら` / `かっこう` / `和室全室`)
- `rawContent`（監査用）
- `resolvedType`（`single` / `expanded` / `ignored`）

## 11. 既知の未確認事項
- サイト側の文言変更・DOM変更（class/id変更）時の耐性は未検証。
- 履歴保持期間の上限（何ページまで遡れるか）は未確認。
- サービス停止時間帯の公式時間は未確認（画面遷移時の実測のみ）。

