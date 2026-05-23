---
status: completed
---
# subpage-topbar-title 要件定義書

## 1. 概要

### 目的
独自トップバーを持たない画面に共通の `PageHeader` コンポーネントを適用し、トップバーに「画面タイトル + 戻るボタン」を表示する。

### 背景・動機
- `Layout.jsx` には「ベースナビバー」と呼ばれる空のプレースホルダー（`bg-[#4a6b5a]` の緑のバー、`z-40`）がある。
- このプレースホルダーは「各ページのナビバーが `z-50` で上書きする。ローディング中のフォールバック」という設計だが、独自ヘッダーを持たない画面ではこの空のバーがそのまま見えてしまい、画面領域がもったいない。
- 該当画面で「いま何の画面にいるか／どこに戻れるか」をユーザーが把握できるようにする。

## 2. ユーザーストーリー

### 対象ユーザー
当アプリの全ユーザー（PLAYER / ADMIN / SUPER_ADMIN）。

### ユーザーの目的・利用シナリオ
- 設定画面のグリッドメニューから任意のサブ画面に遷移したとき、画面上部に常に「今どこにいるか（画面名）」と「どこに戻るか（戻る導線）」が見える。
- 抽選結果・通知一覧・試合詳細・選手編集など、独自トップバーを持たない他の画面でも、同じ位置に同じスタイルで画面タイトルが見える。

### ゴール
- 対象となる22ルートすべてで、トップバーに「← 画面タイトル」が表示される。
- 戻るボタンを押すと、画面ごとに事前定義された遷移先へ移動する（ブラウザ履歴依存ではなく明示的）。
- 既存の画面本文にあるタイトル（H1相当）はトップバーに集約し、重複を解消する。

## 3. 機能要件

### 3.1 画面仕様

#### 共通：PageHeaderの見た目と振る舞い
- **見た目**：既存の固定トップバーと同じスタイル。`bg-[#4a6b5a]`（緑）、白文字、`fixed top-0 left-0 right-0 z-50`、`shadow-sm`、`border-b border-[#3d5a4c]`、`px-4 py-4`。
- **配置**：左端に戻るボタン（`ArrowLeft` アイコン）、中央寄りにタイトル、右端にオプションのアクション領域。
- **戻るボタン**：押下時に `navigate(backTo)` を呼ぶ（`useNavigate` を使用）。アイコンは `lucide-react` の `ArrowLeft`、白色。
- **タイトル**：`text-lg font-semibold text-white`、長文時は `truncate` で省略。アイコンは付けない（シンプル統一）。
- **右アクション**：`rightActions` prop で任意の `ReactNode` を渡せる。渡さないページでは何も表示しない。
- **アクセシビリティ**：戻るボタンに `aria-label="戻る"` を付与。

#### 対象画面と適用内容

##### A. 設定画面のサブページ群（戻る → `/settings`）

| パス | コンポーネント | タイトル | 既存本文H1の扱い |
|---|---|---|---|
| `/profile` | `Profile.jsx` | プロフィール | `{player.name}` は本文に残す（プロフィール本体の識別情報のため） |
| `/settings/organizations` | `OrganizationSettings.jsx` | 参加練習会 | 「参加練習会の設定」H1（Building2アイコン付き）を削除 |
| `/settings/notifications` | `NotificationSettings.jsx` | 通知設定 | 「通知設定」H1（Bellアイコン付き）を削除 |
| `/settings/mentor` | `mentor/MentorManagement.jsx` | メンター管理 | 「メンター管理」H1 を削除 |
| `/settings/calendar` | `CalendarSubscriptionPage.jsx` | カレンダー購読 | 「カレンダー購読」H1（Rssアイコン付き）を削除 |
| `/pairings` | `pairings/PairingGenerator.jsx` | 組み合わせ作成 | H1なし、追加のみ |
| `/venues` | `venues/VenueList.jsx` | 会場管理 | H1なし、追加のみ |
| `/admin/lottery` | `lottery/LotteryManagement.jsx` | 抽選管理 | インラインヘッダー（戻る矢印+H1+「システム設定」ボタン）を削除。「システム設定」ボタンを `rightActions` に移動 |
| `/admin/line/channels` | `line/LineChannelAdmin.jsx` | LINEチャネル管理 | 「LINEチャネル管理」H1 を削除 |
| `/admin/line/schedule` | `line/LineScheduleAdmin.jsx` | LINE通知スケジュール | 「LINE通知スケジュール」H1（Calendarアイコン付き）を削除 |

##### A'. 抽選管理から入る画面（戻る → `/admin/lottery`）

| パス | コンポーネント | タイトル | 既存本文H1の扱い |
|---|---|---|---|
| `/admin/settings` | `settings/SystemSettings.jsx` | システム設定 | 「システム設定」H1（Settingsアイコン付き）を削除 |

> 設定グリッドに「システム設定」のメニュー項目はなく、主要導線は `LotteryManagement` 画面右上の「システム設定」ボタンから到達する。そのため戻る先を `/admin/lottery` に揃え、抽選管理ワークフローへ復帰できるようにする。

##### B. リスト→詳細/編集（戻る → 親リスト）

| パス | コンポーネント | タイトル | 戻る先 | 既存本文H1の扱い |
|---|---|---|---|---|
| `/matches/:id` | `matches/MatchDetail.jsx` | 試合詳細 | `/matches` | 本文先頭のインライン「← 試合記録一覧に戻る」リンクを削除 |
| `/practice/:id` | `practice/PracticeDetail.jsx` | 練習詳細 | `/practice` | 編集/削除ボタンは本文に残す。H1なし |
| `/players/new` | `players/PlayerEdit.jsx` | 選手新規登録 | `/players` | 本文の `<h1>選手新規登録</h1>` （Userアイコン+`bg-primary-600`バー）と上部のインライン「← 選手一覧に戻る」を削除 |
| `/players/:id/edit` | `players/PlayerEdit.jsx` | 選手情報編集 | `/players/:id` | 同上。タイトルは `id` の有無で動的に切り替え |
| `/venues/new` | `venues/VenueForm.jsx` | 新規会場登録 | `/venues` | H1なし、追加のみ |
| `/venues/edit/:id` | `venues/VenueForm.jsx` | 会場編集 | `/venues` | 同上。タイトルは `isEditMode` で動的に切り替え |
| `/pairings/summary` | `pairings/PairingSummary.jsx` | 札ルール一覧 | `/pairings` | 本文先頭のインライン「組み合わせに戻る」リンクを削除。本文末尾の「ホームに戻る」ボタンは残す |

##### C. ホームから到達する画面（戻る → `/`）

| パス | コンポーネント | タイトル | 既存本文H1の扱い |
|---|---|---|---|
| `/notifications` | `notifications/NotificationList.jsx` | 通知 | H1「通知」を削除。「すべて削除」ボタンを `rightActions` に移動 |
| `/lottery/results` | `lottery/LotteryResults.jsx` | 抽選結果 | H1「抽選結果」を削除 |
| `/lottery/waitlist` | `lottery/WaitlistStatus.jsx` | キャンセル待ち状況 | H1「キャンセル待ち状況」を削除 |
| `/lottery/offer-response` | `lottery/OfferResponse.jsx` | 繰り上げ参加のご連絡 | H1「繰り上げ参加のご連絡」を削除。本文の「ホームに戻る」ボタン（エラー/期限切れ時）は残す |

### 3.2 ビジネスルール

- 戻るボタンの遷移先は画面ごとに固定値（明示指定）。`navigate(-1)`（ブラウザ履歴）は使わない。
  - 理由：ディープリンク・リロード時にも一貫した戻り先を保証するため。
- タイトルの動的切り替えが必要なページ（`PlayerEdit`、`VenueForm`）では、`id` などの条件に応じて文字列を切り替える。
- アイコンはトップバーに付けない。本文H1にアイコンが付いていた場合（Bell, Rss, Calendar, Settings, Users, Building2 等）も削除する。
- 「すべて削除」「システム設定」など、もとのH1の隣に並んでいたアクションボタンは `rightActions` 経由でトップバー右端に配置する。

### 3.3 エラーケース・例外処理

- `navigate(backTo)` 自体は React Router の標準動作。エラーケースなし。
- 認証切れ等で対象パスに遷移できなくても、ルーティング側で `PrivateRoute` がログイン画面にリダイレクトするため PageHeader 側で考慮不要。

## 4. 技術設計

### 4.1 API設計

新規・変更APIなし。

### 4.2 DB設計

スキーマ変更なし。

### 4.3 フロントエンド設計

#### 4.3.1 新規コンポーネント：`PageHeader`

**ファイル**：`karuta-tracker-ui/src/components/PageHeader.jsx`

**Props**：
| 名前 | 型 | 必須 | 説明 |
|---|---|---|---|
| `title` | `string` | ○ | トップバーに表示するタイトル文字列 |
| `backTo` | `string` | ○ | 戻るボタン押下時の遷移先パス |
| `rightActions` | `ReactNode` | × | トップバー右端に表示する追加要素（ボタン等） |

**実装方針**：
- `useNavigate` を `react-router-dom` から取得し、戻るボタンの `onClick` で `navigate(backTo)` を呼ぶ。
- DOM構造・Tailwindクラスは `SettingsPage.jsx` の独自ヘッダーと同等。
- 戻るボタンは `<button type="button">` で、フォーカス・ホバー時のスタイルも既存と同等（`hover:bg-[#3d5a4c]`、丸ボタン）。

**雛形（参考）**：
```jsx
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

const PageHeader = ({ title, backTo, rightActions = null }) => {
  const navigate = useNavigate();
  return (
    <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
      <div className="max-w-7xl mx-auto flex items-center gap-3">
        <button
          type="button"
          onClick={() => navigate(backTo)}
          className="p-1 -ml-1 hover:bg-[#3d5a4c] rounded-full transition-colors"
          aria-label="戻る"
        >
          <ArrowLeft className="w-5 h-5 text-white" />
        </button>
        <span className="text-lg font-semibold text-white truncate flex-1">
          {title}
        </span>
        {rightActions}
      </div>
    </div>
  );
};

export default PageHeader;
```

#### 4.3.2 各対象ページへの適用方針

各ページの return 直下に `<PageHeader title="..." backTo="..." />` を追加し、既存の本文H1や本文先頭のインライン戻るリンクを削除する。

例（`NotificationSettings.jsx`）：
```jsx
return (
  <>
    <PageHeader title="通知設定" backTo="/settings" />
    <div className="max-w-lg mx-auto p-4 space-y-6">
      {/* ←既存の <h1>通知設定</h1> は削除 */}
      {/* ...残りの本文... */}
    </div>
  </>
);
```

例（`NotificationList.jsx` — rightActions使用）：
```jsx
return (
  <>
    <PageHeader
      title="通知"
      backTo="/"
      rightActions={
        notifications.length > 0 && !loading ? (
          <button onClick={handleDeleteAll} disabled={deleting} className="...">
            {deleting ? '削除中...' : 'すべて削除'}
          </button>
        ) : null
      }
    />
    <div className="max-w-2xl mx-auto p-4">
      {/* H1と「すべて削除」を含むラッパー <div> を削除 */}
      {/* ...残りの本文... */}
    </div>
  </>
);
```

例（`PlayerEdit.jsx` — タイトル動的）：
```jsx
return (
  <>
    <PageHeader
      title={id ? '選手情報編集' : '選手新規登録'}
      backTo={id ? `/players/${id}` : '/players'}
    />
    {/* 既存の <div className="bg-primary-600..."><h1>...</h1></div> と上部の戻るボタンを削除 */}
    {/* ...残りの本文（form 等）... */}
  </>
);
```

#### 4.3.3 Layout.jsx の扱い

`Layout.jsx` の「ベースナビバー」（空のプレースホルダー、`z-40`）は変更不要。引き続きローディング中のフォールバックとして機能する。PageHeader は `z-50` でこれを上書きする。

### 4.4 バックエンド設計

変更なし。

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

#### 新規作成
- `karuta-tracker-ui/src/components/PageHeader.jsx`

#### 修正
1. `karuta-tracker-ui/src/pages/Profile.jsx`
2. `karuta-tracker-ui/src/pages/settings/OrganizationSettings.jsx`
3. `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx`
4. `karuta-tracker-ui/src/pages/mentor/MentorManagement.jsx`
5. `karuta-tracker-ui/src/pages/CalendarSubscriptionPage.jsx`
6. `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
7. `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx`
8. `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx`
9. `karuta-tracker-ui/src/pages/practice/PracticeDetail.jsx`
10. `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx`
11. `karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx`
12. `karuta-tracker-ui/src/pages/lottery/OfferResponse.jsx`
13. `karuta-tracker-ui/src/pages/players/PlayerEdit.jsx`
14. `karuta-tracker-ui/src/pages/venues/VenueList.jsx`
15. `karuta-tracker-ui/src/pages/venues/VenueForm.jsx`
16. `karuta-tracker-ui/src/pages/notifications/NotificationList.jsx`
17. `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx`
18. `karuta-tracker-ui/src/pages/settings/SystemSettings.jsx`
19. `karuta-tracker-ui/src/pages/line/LineChannelAdmin.jsx`
20. `karuta-tracker-ui/src/pages/line/LineScheduleAdmin.jsx`

### 5.2 既存機能への影響

- **デザインの一貫性**：これまで「設定→サブ画面」と遷移したときに見えていた空の緑バーが、画面名 + 戻るボタンに置き換わる（UX改善）。
- **本文上部の見た目**：本文先頭の H1（およびその隣にあったアイコン/ボタン）が削除されるため、本文の余白の取り方が変わる。各ページで違和感が出ないことを目視確認する必要がある。
- **遷移挙動の変更**：`MatchDetail` 等の「← 試合記録一覧に戻る」リンクが消えるが、トップバーに戻るボタンが現れるため機能は維持される。
- **既存テストへの影響**：H1テキストを `getByText(/通知設定/)` のように引いていたテストがあれば、`role="heading"` の検索方法に変えるかPageHeader経由で検証する必要がある。要確認。

### 5.3 共通コンポーネント・ユーティリティへの影響

- `Layout.jsx` のベースナビバーは引き続きそのまま。PageHeader は `z-50` で上書きする。
- 既存の独自トップバーを持つページ（`SettingsPage`、`PracticeList`、`MatchList`、`PlayerList`、`PlayerDetail`、`DensukeManagement`、`ProfileEdit`、`PracticeCancelPage`、`PracticeForm`、`PracticeParticipation`、`MatchForm`、`MatchResultsView`、`BulkResultInput`）は今回の対象外。今後共通化する場合は別タスク。

### 5.4 API・DBスキーマの互換性

変更なし。

## 6. 設計判断の根拠

### 6.1 なぜ「PageHeaderコンポーネント新設」アプローチか

- **代替案A**：`Layout.jsx` に `title` prop を追加し、ProtectedPage 経由で渡す
  - 却下理由：ルーティング側で全画面に静的タイトルを渡す形になり、`PlayerEdit` のように同じコンポーネントが複数パスから動的なタイトルで使われるケースに対応しづらい。
- **代替案B**：ルート定義に `title` メタを持たせ、`Layout` が `location` から引き当てる
  - 却下理由：動的タイトル（`id` の有無で切り替え等）が表現できない。
- **採用案**：各ページが `<PageHeader title="..." backTo="..." />` を自分で配置
  - 利点：動的タイトル・動的 backTo を素直に書ける。SettingsPage 等の既存パターンと一貫している。`rightActions` で柔軟にアクションを追加できる。

### 6.2 なぜ `navigate(-1)` ではなく明示的 `backTo` か

- ディープリンク（外部リンク／LINE通知／ブックマーク）から開いたときに `navigate(-1)` は履歴の前ページに戻ってしまい、意図せぬ画面に飛ぶ。
- 明示的に親ページを指定することで、どの経路で開いてもユーザーが期待する画面に戻れる。
- 設定サブページは `/settings` に統一、リスト→詳細/編集は親リストに戻る、というメンタルモデルがユーザーにとって分かりやすい。

### 6.3 なぜタイトルにアイコンを付けないか

- 既存の独自トップバー（`SettingsPage`、`PlayerList`、`PracticeCancelPage`、`PlayerDetail`、`MatchList`、`PracticeList` 等）はアイコンを付けていないため、合わせる方が一貫する。
- 本文H1にあったアイコンは一緒に削除することでシンプルな見た目を保つ。

### 6.4 なぜ `Profile` だけ本文の `{player.name}` を残すか

- `Profile.jsx` の `{player.name}` はページタイトルではなく、プロフィール本体の識別情報（誰のプロフィールを表示しているかを示すラベル）。
- ロールバッジや「編集」ボタンと一緒に表示される本文要素のため、トップバーの「プロフィール」というメニュー名とは役割が異なる。
- 他のH1は削除するが、Profile だけは本文識別情報として残す。

### 6.5 なぜ `rightActions` を共通APIとして用意するか

- `NotificationList` の「すべて削除」、`LotteryManagement` の「システム設定」など、もともとH1の隣に並んでいた重要アクションが、H1削除で行き場をなくす。
- これらをトップバー右端に置けば、レイアウト的にも自然で操作性も保たれる。
- prop はオプションなので、不要なページに影響しない。

### 6.6 戻る先のグルーピング根拠

- **設定グリッドから入る画面 → `/settings`**：ユーザーは設定ページから来たという文脈を持って入るため、戻る先は設定ページ。
- **抽選管理から入る画面 → `/admin/lottery`**（システム設定）：`SystemSettings` は設定グリッドに項目がなく、`LotteryManagement` 画面右上の「システム設定」ボタンが主要導線。`/settings` を戻る先にすると元の抽選管理ワークフローへ復帰できないため、例外的に `/admin/lottery` に揃える。
- **リスト→詳細/編集 → 親リスト**：標準的なリスト/詳細パターン。
- **ホームから入る画面 → `/`**：通知・抽選結果・キャンセル待ち・オファー応答はホームの導線（ベルアイコン、抽選通知）から到達するため、戻る先はホーム。
- **`Profile`**：HomeのUserアイコンと設定グリッドの両方から到達するが、設定サブページパターンに揃えて `/settings`。Homeから来た場合はボトムナビのHomeで戻れる。
