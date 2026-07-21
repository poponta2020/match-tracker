---
status: locked
slug: match-record-calendar-tabs
target: /matches（MatchList を2タブ化）+ 新規 MatchCalendar / MatchViewTabs
chosen_direction: "モックアップをアプリ緑基調へ翻訳した単一案（下線アクティブタブ＋緑アクセントカレンダー）"
round: 3
prototype_branch: design/match-record-calendar-tabs
prototype_base: 7e8f9b2d02150286661d6c34fb4af835070d0955
---
# 戦績確認画面 カレンダータブ デザイン仕様（design-spec）

> `/design-screen` が出力する確定仕様。実装は `/define-feature match-record-calendar-tabs` → `/implement` で行う。
> **レイアウトの詳細は `design-prototype.patch`（実差分）が正**。このファイルは patch から読み取れない意図・判断・データ要件・宿題を記述する。

## 1. 対象と狙い
- **対象画面:** `/matches`（`MatchList`）に緑トップバー直下のタブ帯を足し、「カレンダー」タブ本体（`MatchCalendar`）を新設する。
- **現状の不満:** 戦績確認は級別統計＋縦長リストで、「いつ試合したか」「特定の日の記録・メモを日付軸で振り返る」導線が無い。
- **狙い:** 自分の試合を月カレンダーで俯瞰し、日を選んでその日の結果・個人メモを確認、試合詳細へ進める。
- **主ユーザー / 主な使い方:** 選手本人が自分の記録を日付軸で振り返る（常に自分のみ）。

## 2. 採用した方向性
- **方向性:** ユーザー提供モックアップ（Karuta Log のカレンダー）を、このアプリの緑基調（`#4a6b5a` / クリーム `#f9f6f2` / 罫線 `#e5e0da`）へ翻訳した単一案。タブ帯は下線アクティブ式。
- **不採用案と理由:** タブ帯は「cream ピル（/pairings 風）」「緑バー内セグメント」も候補に挙げたが、ユーザーが下線アクティブ式を選択。モックの青系ハイライトはアプリに青が無いため緑へ翻訳。
- **確定プロトタイプ:** `design-prototype.patch`（ブランチ `design/match-record-calendar-tabs`・基点 `7e8f9b2d`）
- **確認URL:** `/design/calendar-tabs`（DESIGN-PROTO プレビュールート。スタブ試合データで全状態を再現）

## 3. patch に現れない設計判断
- **タブの表示順は「カレンダー（左）／戦績確認（右）」だが既定選択は戦績確認**（`/matches` 既定・ボトムナビ📊の遷移先）。表示順はユーザー指定、既定タブは要件由来で別軸。
- **今日と選択日の視覚言語を分離**: 今日＝数字に細い緑リング、選択日＝セルを角丸ボックスで囲い＋薄緑背景（`#eef2ef`）。両立時はボックス＋緑リングが重なる（意図どおり）。円塗り案はユーザーが「セルを囲う」を希望したため不採用。
- **試合数バッジは日付の“下”に配置**（数字への重ね置きより窮屈にならない）。モックは右下重ねだが、可読性を優先。
- **選択日リストは1行に凝縮**: `N試合目： {勝敗}{枚数差} 相手名（級） お手N`。左の「N試合目」独立列は行が縦に空く・横幅を圧迫するため廃止（ユーザー指摘）。
- **会場は日付に一意という前提**（ユーザー確認）で、見出し `M/D 会場名` に1回だけ出す。各試合行への会場併記は廃止（要件 AC-11 を収束時に上書き）。

## 4. 使用コンポーネント
- **既存プリミティブ:** `YearMonthPicker`（`components/YearMonthPicker.jsx`。年月ラベル押下時のグリッド型ピッカーとして流用）。lucide-react の `ChevronLeft/Right/Down`。配色トークンは `MatchList` と共通。
- **新規:**
  - `MatchViewTabs`（`pages/matches/MatchViewTabs.jsx`）: 下線アクティブ式2タブ帯（表示のみ。active/onChange を親が握る）。
  - `MatchCalendar`（`pages/matches/MatchCalendar.jsx`）: 月カレンダー本体＋選択日リスト。props `matches`（自分視点 MatchDto 配列）・`selfId`。内部 state で表示月・選択日・年月ピッカー開閉を保持。カレンダーグリッド生成は `PracticeList.generateCalendar` と同型（前月・翌月のはみ出しセルを実日付で薄色表示）。

## 5. 状態（state）
- **通常:** 当月・今日選択。日セルに試合数バッジ、選択日リストに試合カード。
- **空（試合0件の日）:** 「記録がありません」（登録ボタンなし）。
- **月内0件:** 年月バッジ「0試合」、全日バッジ無し。
- **複数試合の日:** 試合番号昇順で列挙（会場は日付一意で見出しに1回）。
- **エラー / 不明値:** 指導試合＝勝敗の代わりに「指導」。ゲスト/相手不明＝級なし（括弧省略）・お手つき null（`お手` 省略）。
- **プロトタイプでの再現方法:** すべて `DesignCalendarPreview.jsx` の DESIGN-PROTO スタブ配列で再現（今日3試合＋長文メモ／単発日／指導／ゲスト／同日2試合／空日）。実装時は実データで再検証。

## 6. 必要データ
- **表示フィールド（すべて既存 `GET /matches/player/{selfId}` レスポンスに存在。BE改修不要）:** `matchDate` `matchNumber` `result` `scoreDifference` `player1Id/player2Id` `player1KyuRank/player2KyuRank`（相手級＝self でない側）`opponentName` `myOtetsukiCount` `myPersonalNotes` `isLesson` `venueName` `id`。
- **集計・導出:** 日付→試合数（バッジ）／月→総試合数（バッジ）はクライアント集計。相手級の単字化は `kyuRank.replace('級','')`。M/D は十の位0省略（`Number()` 変換）。

## 7. インタラクション / レスポンシブ
- **操作:** 日セルタップ→選択日切替。試合カードタップ→`/matches/:id`。年月ラベルタップ→`YearMonthPicker`。‹ › で前後月。タブタップで戦績確認/カレンダー切替。
- **モバイル:** 375px 基準で横スクロールなし・console エラーなしを実機（Browser プレビュー）で確認済み。7列グリッドはセル高さ固定でタップ領域確保。

## 8. ガードレール準拠メモ
- 配色は既存トークンのみ（`#4a6b5a` / `#f9f6f2` / `#eef2ef` / `#e5e0da` / text `#374151`・`#6b7280`・`#9ca3af`、勝敗の green-600/red-600/gray-600）。新色の発明なし。曜日 日=赤・土=青は Japanese カレンダー慣習かつ既存 PracticeList カレンダーと整合。文言は日本語。

## 9. 残課題・実装への申し送り
- **`/matches` への統合方法は技術計画で確定**: `MatchList` をタブコンテナ化するか、`/matches` 用ラッパーで `MatchList`（戦績確認ビュー）と `MatchCalendar` を出し分けるか。`MatchList` の独自トップバー（`fixed top-0`）とタブ帯の固定配置・上部パディングの整合が要検討。
- **カレンダータブの緑トップバー**（自分の名前＋級）は本物では `useAuth().currentPlayer` から供給する（プロトは固定文字列 DESIGN-PROTO）。
- **`MatchCalendar` のデータ供給**は本物では `matchAPI.getByPlayerId(currentPlayer.id)` を自前 fetch（戦績確認タブの playerId とは独立に常に自分）。
- 年月ピッカーや ‹ › で別月へ移動しても選択日リストは直前の選択日を保持する（見出しに日付が出るため取り違え無し）。この挙動でユーザー合意済み。
- patch が `git apply` で当たらない場合は手動移植（新規3ファイル＋App.jsx への1 import・1 Route）。
- **DESIGN-PROTO スタブ一覧（実装時に全除去＝`git grep DESIGN-PROTO` 0件）:**
  - `App.jsx`: `DesignCalendarPreview` の import と `/design/calendar-tabs` ルート（削除）。
  - `pages/DesignCalendarPreview.jsx`: ファイルごと削除（プレビュー足場・スタブ試合データ・名前/級の固定値）。
  - `MatchViewTabs.jsx` / `MatchCalendar.jsx` は本物（DESIGN-PROTO マーカー無し・そのまま productionize）。

## 10. 要件への宿題（→ /define-feature match-record-calendar-tabs）
- （なし＝収束）。プロトタイピングで出た表示仕様の変更（タブ順・年月ピッカー・月間試合数バッジ・見出し `M/D 会場名`・`名前（級） お手N`・会場は日付一意）は requirements.md（§3.1/§3.2/AC）へ反映済み。
