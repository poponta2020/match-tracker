---
status: completed
---
# subpage-topbar-title 実装手順書

## 前提

- 全タスクは同一ブランチ（例：`feat/subpage-topbar-title`）で作業し、1つのPRで集約マージする。
- タスクごとにIssueを分け、PR本文で4Issueすべてをクローズする（`Closes #xx`）。
- 各タスクの完了は、対応するページが期待通りトップバーを表示し、戻るボタンが指定先に遷移すること、そして既存機能が壊れていないこと。

## 実装タスク

### タスク1: PageHeader コンポーネント作成
- [x] 完了
- **概要:** トップバー領域に「タイトル + 戻るボタン + 右アクション」を表示する共通コンポーネントを新規作成する。`react-router-dom` の `useNavigate` を使い、`backTo` 押下で `navigate(backTo)` を呼ぶ。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/PageHeader.jsx` — 新規作成。props: `title: string`（必須）、`backTo: string`（必須）、`rightActions?: ReactNode`（任意）。スタイルは `bg-[#4a6b5a]` 緑背景・白文字・`fixed top-0 z-50 px-4 py-4`、左に `ArrowLeft`（`lucide-react`）アイコンの戻るボタン、中央寄りに `truncate` 付きタイトル、右に `rightActions` を配置。`aria-label="戻る"` を戻るボタンに付与。
- **依存タスク:** なし
- **対応Issue:** #728

### タスク2: Aグループ（設定サブページ群）への PageHeader 適用
- [ ] 完了
- **概要:** 設定画面のグリッドから到達するサブページ群に `PageHeader` を適用する。戻る先は `/settings` で統一。既存の本文H1（およびアイコン）を削除する。`LotteryManagement` のみ `rightActions` で「システム設定」ボタンをトップバー右端に移す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/Profile.jsx` — `<PageHeader title="プロフィール" backTo="/settings" />` を追加。本文の `{player.name}` 表示は残す。
  - `karuta-tracker-ui/src/pages/settings/OrganizationSettings.jsx` — `<PageHeader title="参加練習会" backTo="/settings" />` を追加。「参加練習会の設定」H1（Building2 アイコン付き）を削除。
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — `<PageHeader title="通知設定" backTo="/settings" />` を追加。`<h1>...通知設定...</h1>`（Bell アイコン付き）を削除。
  - `karuta-tracker-ui/src/pages/mentor/MentorManagement.jsx` — `<PageHeader title="メンター管理" backTo="/settings" />` を追加。`<h1>メンター管理</h1>` を削除。
  - `karuta-tracker-ui/src/pages/CalendarSubscriptionPage.jsx` — `<PageHeader title="カレンダー購読" backTo="/settings" />` を追加。`<h1>...カレンダー購読...</h1>`（Rss アイコン付き）を削除。
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — `<PageHeader title="組み合わせ作成" backTo="/settings" />` を追加。既存H1なし。
  - `karuta-tracker-ui/src/pages/venues/VenueList.jsx` — `<PageHeader title="会場管理" backTo="/settings" />` を追加。既存H1なし。
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` — `<PageHeader title="抽選管理" backTo="/settings" rightActions={<システム設定ボタン />} />` を追加。インラインヘッダー（戻る矢印 + H1 + システム設定ボタンを含む `<div>` ブロック）を削除し、システム設定ボタンを `rightActions` 経由でトップバー右端へ移動。
  - `karuta-tracker-ui/src/pages/settings/SystemSettings.jsx` — `<PageHeader title="システム設定" backTo="/settings" />` を追加。`<h1>...システム設定...</h1>`（Settings アイコン付き）を削除。
  - `karuta-tracker-ui/src/pages/line/LineChannelAdmin.jsx` — `<PageHeader title="LINEチャネル管理" backTo="/settings" />` を追加。`<h1>LINEチャネル管理</h1>` を削除。
  - `karuta-tracker-ui/src/pages/line/LineScheduleAdmin.jsx` — `<PageHeader title="LINE通知スケジュール" backTo="/settings" />` を追加。`<h1>...LINE通知スケジュール...</h1>`（Calendar アイコン付き）を削除。
- **依存タスク:** タスク1
- **対応Issue:** #729

### タスク3: Bグループ（リスト→詳細・編集）への PageHeader 適用
- [ ] 完了
- **概要:** リストから詳細・編集に遷移する画面に `PageHeader` を適用する。戻る先は親リスト（`PlayerEdit` 編集時のみ選手詳細）。既存のインライン「戻る」リンクと本文H1を削除する。`PlayerEdit` と `VenueForm` はタイトルが動的（新規/編集）。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — `<PageHeader title="試合詳細" backTo="/matches" />` を追加。本文先頭のインライン「← 試合記録一覧に戻る」リンクを削除。
  - `karuta-tracker-ui/src/pages/practice/PracticeDetail.jsx` — `<PageHeader title="練習詳細" backTo="/practice" />` を追加。編集/削除ボタンは本文に残す。
  - `karuta-tracker-ui/src/pages/players/PlayerEdit.jsx` — `<PageHeader title={id ? '選手情報編集' : '選手新規登録'} backTo={id ? \`/players/${id}\` : '/players'} />` を追加。本文の `<h1>...</h1>`（Userアイコン+`bg-primary-600`バー）と上部のインライン戻るボタンを削除。
  - `karuta-tracker-ui/src/pages/venues/VenueForm.jsx` — `<PageHeader title={isEditMode ? '会場編集' : '新規会場登録'} backTo="/venues" />` を追加。既存H1なし。
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx` — `<PageHeader title="札ルール一覧" backTo="/pairings" />` を追加。本文先頭のインライン「組み合わせに戻る」リンクを削除。本文末尾の「ホームに戻る」ボタンは残す。
- **依存タスク:** タスク1
- **対応Issue:** #730

### タスク4: Cグループ（ホーム導線）への PageHeader 適用
- [ ] 完了
- **概要:** ホームから到達する画面（通知一覧・抽選結果・キャンセル待ち・繰り上げ参加応答）に `PageHeader` を適用する。戻る先は `/`。`NotificationList` のみ `rightActions` で「すべて削除」ボタンをトップバー右端に移す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationList.jsx` — `<PageHeader title="通知" backTo="/" rightActions={<すべて削除ボタン />} />` を追加。`<h1>通知</h1>` と「すべて削除」ボタンを含むラッパーdivを削除し、ボタンを `rightActions` 経由でトップバー右端へ移動。
  - `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx` — `<PageHeader title="抽選結果" backTo="/" />` を追加。`<h1>抽選結果</h1>` を削除。
  - `karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx` — `<PageHeader title="キャンセル待ち状況" backTo="/" />` を追加。`<h1>キャンセル待ち状況</h1>` を削除。
  - `karuta-tracker-ui/src/pages/lottery/OfferResponse.jsx` — `<PageHeader title="繰り上げ参加のご連絡" backTo="/" />` を追加。`<h1>繰り上げ参加のご連絡</h1>` を削除。本文の「ホームに戻る」ボタン（エラー/期限切れ時）は残す。
- **依存タスク:** タスク1
- **対応Issue:** #731

## 実装順序

1. **タスク1**（依存なし）：`PageHeader` を作成して動作確認（任意の1ページに仮適用）。
2. **タスク2 / タスク3 / タスク4**（タスク1完了後、並行可）：各グループに適用。
   - 一度に全グループを進めても良いが、レビューしやすいよう論理的にコミットを分けることを推奨（例：`feat(page-header): create PageHeader component` / `feat(settings): apply PageHeader to settings sub-pages` / `feat(detail): apply PageHeader to detail/edit pages` / `feat(home-flow): apply PageHeader to home-reachable pages`）。
3. 最後に対象22ルートをブラウザで動作確認し、PRに集約。

## 動作確認の観点

- 各画面でトップバーに正しいタイトルが表示される
- 戻るボタン押下で指定の `backTo` に遷移する
- 既存H1の削除によって本文上部に違和感（余白の崩れ）が出ていない
- `NotificationList` の「すべて削除」、`LotteryManagement` の「システム設定」がトップバー右端で正しく機能する
- `PlayerEdit` と `VenueForm` で新規/編集モードに応じてタイトルが正しく切り替わる
- ディープリンクやリロード後でも戻るボタンが期待通り動作する
