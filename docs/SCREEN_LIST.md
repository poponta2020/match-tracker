# 画面一覧

## 概要

本ドキュメントは、match-tracker アプリケーションの全画面をパス・メインコンポーネント・主要子コンポーネント・アクセス権限とともに整理したものです。

---

## 認証・ルートガード構成

| ガード | 説明 |
|--------|------|
| `AuthRoute` | 未認証→Landing、認証済→指定コンポーネントを表示 |
| `PrivateRoute` | 認証必須。`kyuRank` 未設定の場合は `/profile/edit?setup=true` にリダイレクト |
| `Layout` | ヘッダーバー（タイトル・通知ベル・プロフィール）+ 下部ナビゲーション付きの共通レイアウト |

---

## 1. 公開画面（認証不要）

| # | パス | ページコンポーネント | 主要子コンポーネント | 説明 |
|---|------|---------------------|---------------------|------|
| 1 | `/`（未認証時） | `Landing.jsx` | — | ランディングページ（機能紹介・CTAボタン） |
| 2 | `/login` | `Login.jsx` | — | ログイン画面 |
| 3 | `/register/:token` | `InviteRegister.jsx` | — | 招待リンクによる新規登録画面（トークン検証付き） |
| 4 | `/privacy` | `PrivacyPolicy.jsx` | — | プライバシーポリシー |
| 5 | `/terms` | `TermsOfService.jsx` | — | 利用規約 |

---

## 2. ホーム・ダッシュボード

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 6 | `/`（認証時） | `Home.jsx` | ハンバーガーメニュー、繰り上げオファーバナー | ALL | ダッシュボード（次回練習・参加率TOP3・繰り上げ通知・Googleカレンダー連携） |

---

## 3. 試合管理（matches）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 7 | `/matches` | `MatchList.jsx` | `FilterBottomSheet` | ALL | 試合一覧（勝率・段位別統計・フィルタ） |
| 8 | `/matches/new` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、抜け番活動種別選択、「抜け番として記録する」ボタン（ペアリング未作成時） | ALL | 試合結果入力（抜け番の場合は活動記録。ペアリング未作成時は手動切替可能） |
| 9 | `/matches/:id` | `MatchDetail.jsx` | — | ALL | 試合詳細表示 |
| 10 | `/matches/:id/edit` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択 | ALL | 試合結果編集 |
| 11 | `/matches/bulk-input/:sessionId` | `BulkResultInput.jsx` | 組み合わせリスト、枚数差入力、抜け番活動入力 | ADMIN+ | 一括結果入力（抜け番の活動も含む） |
| 12 | `/matches/results/:sessionId?` | `MatchResultsView.jsx` | カレンダーピッカー、セッションナビ、抜け番活動表示 | ALL | 試合結果一覧（抜け番の活動もバッジ表示） |

---

## 4. 練習管理（practice）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 13 | `/practice` | `PracticeList.jsx` | `PlayerChip`, `MatchParticipantsEditModal` | ALL | 練習日程一覧（月別カレンダー表示）。ADMIN+は「抽選結果を通知」ボタンでアプリ内+LINE通知を一括送信可能 |
| 14 | `/practice/new` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程作成 |
| 15 | `/practice/:id` | `PracticeDetail.jsx` | — | ALL | 練習日程詳細 |
| 16 | `/practice/:id/edit` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程編集 |
| 17 | `/practice/participation` | `PracticeParticipation.jsx` | 月ナビゲーション、試合番号チェックボックス、抽選ステータスバッジ | ALL | 参加登録（抽選済みセッションはステータス表示のみ） |
| 18 | `/practice/cancel` | `PracticeCancelPage.jsx` | キャンセル専用カレンダー、試合選択チェックボックス、キャンセル理由ラジオボタン | ALL | 参加キャンセル（WON登録日をハイライトしたカレンダー→試合選択→理由選択→確認ダイアログ） |

---

## 5. 組み合わせ管理（pairings）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 19 | `/pairings` | `PairingGenerator.jsx` | 参加者リスト、待機者リスト、対戦履歴 | ADMIN+ | 組み合わせ作成 |
| 20 | `/pairings/summary` | `PairingSummary.jsx` | カレンダーピッカー、試合番号タブ | ADMIN+ | 組み合わせ一覧表示 |

---

## 6. 選手管理（players）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 21 | `/players` | `PlayerList.jsx` | 検索、段位ソート、ロールバッジ、招待リンク生成（グループ用/個人用） | SUPER_ADMIN | 選手一覧 |
| 22 | `/players/new` | `PlayerEdit.jsx` | — | SUPER_ADMIN | 選手新規作成 |
| 23 | `/players/:id` | `PlayerDetail.jsx` | — | SUPER_ADMIN | 選手詳細 |
| 24 | `/players/:id/edit` | `PlayerEdit.jsx` | — | SUPER_ADMIN | 選手編集 |

---

## 7. 会場管理（venues）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 25 | `/venues` | `VenueList.jsx` | 検索、会場カード | SUPER_ADMIN | 会場一覧 |
| 26 | `/venues/new` | `VenueForm.jsx` | — | SUPER_ADMIN | 会場新規作成 |
| 27 | `/venues/edit/:id` | `VenueForm.jsx` | — | SUPER_ADMIN | 会場編集 |

---

## 8. 抽選・通知（lottery / notifications）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 28 | `/lottery/results` | `LotteryResults.jsx` | 月ナビゲーション、当選/落選リスト | ALL | 月別抽選結果一覧 |
| 29 | `/lottery/waitlist` | `WaitlistStatus.jsx` | ステータスバッジ、応答リンク | ALL | 自分のキャンセル待ち状況 |
| 30 | `/lottery/offer-response` | `OfferResponse.jsx` | オファー詳細（日付・会場・試合・期限）、参加/辞退ボタン、期限切れ表示、処理済み表示 | ALL | 繰り上げ参加の承認/辞退 |
| 31 | `/notifications` | `NotificationList.jsx` | 通知カード、未読バッジ | ALL | 通知一覧（タップで関連画面に遷移） |

---

## 8.5 LINE通知（line）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 32 | `/settings/line` | `LineSettings.jsx` | 連携状態表示、友だち追加ボタン、ワンタイムコード（コピーボタン付き）、通知種別トグル | ALL | LINE通知設定（有効化/無効化、コード発行、種別ON/OFF） |
| 33 | `/admin/line/channels` | `LineChannelAdmin.jsx` | チャネル一覧テーブル、新規登録フォーム、ステータスバッジ | SUPER_ADMIN | LINEチャネル管理（登録・無効化・強制解除） |
| 34 | `/admin/line/schedule` | `LineScheduleAdmin.jsx` | リマインダー設定カード、送信日数入力 | ADMIN+ | LINE通知スケジュール設定 |

---

## 9. プロフィール

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 35 | `/profile` | `Profile.jsx` | ロールバッジ | ALL | 自分のプロフィール表示 |
| 36 | `/profile/edit` | `ProfileEdit.jsx` | パスワード変更セクション | ALL | プロフィール編集（※Layout なし） |

---

## 10. その他

| # | パス | ページコンポーネント | 権限 | 説明 |
|---|------|---------------------|------|------|
| 37 | `/statistics` | （スタブ: `div`） | ALL | 統計画面（未実装: "実装中..."） |
| 38 | `*`（存在しないパス） | `Navigate` → `/` | — | 404リダイレクト |

---

## 共通UIコンポーネント

| コンポーネント | ファイル | 用途 |
|---------------|---------|------|
| `Layout` | `components/Layout.jsx` | ヘッダーバー（通知ベル・プロフィール）+ 下部ナビゲーション付き共通レイアウト |
| `PrivateRoute` | `components/PrivateRoute.jsx` | 認証ガード＋プロフィール設定チェック |
| `AuthRoute` | `components/AuthRoute.jsx` | 認証状態による条件分岐レンダリング |
| `FilterBottomSheet` | `components/FilterBottomSheet.jsx` | 試合フィルタUI（年月・段位・性別・利き手・結果） |
| `PlayerChip` | `components/PlayerChip.jsx` | 選手バッジ |
| `MatchParticipantsEditModal` | `components/MatchParticipantsEditModal.jsx` | 試合参加者編集モーダル |
| `ErrorBoundary` | `components/ErrorBoundary.jsx` | エラーバウンダリ |

---

## ヘッダーバー（Layout）

| 要素 | 説明 |
|------|------|
| ページタイトル | 現在のパスに応じた画面タイトル |
| 通知ベル | `/notifications` に遷移。未読数バッジ付き |
| プロフィール | `/profile` に遷移 |

## 下部ナビゲーション（Layout）

| アイコン | ラベル | 遷移先 |
|---------|--------|--------|
| 🏠 | Home | `/` |
| ➕ | Add | `/matches/new` |
| ⚔️ | Match | `/matches/results` |
| 📅 | Schedule | `/practice` |
| 📊 | Record | `/matches` |

---

## ハンバーガーメニュー（Home画面）

| メニュー項目 | 遷移先 | 権限 |
|------------|--------|------|
| プロフィール | `/profile` | ALL |
| 組み合わせ作成 | `/pairings` | ADMIN+ |
| 選手管理 | `/players` | SUPER_ADMIN |
| 会場管理 | `/venues` | SUPER_ADMIN |
| 練習日程作成 | `/practice/new` | SUPER_ADMIN |
| LINE通知設定 | `/settings/line` | ALL |
| LINEチャネル管理 | `/admin/line/channels` | SUPER_ADMIN |
| LINE通知スケジュール | `/admin/line/schedule` | ADMIN+ |
| Googleカレンダー連携 | （OAuth） | ALL |
| ログアウト | — | ALL |

---

## ファイルパス一覧

```
karuta-tracker-ui/src/
├── App.jsx                          # ルート定義
├── context/AuthContext.jsx          # 認証コンテキスト
├── utils/auth.js                    # 認証ユーティリティ
├── components/
│   ├── Layout.jsx
│   ├── PrivateRoute.jsx
│   ├── AuthRoute.jsx
│   ├── FilterBottomSheet.jsx
│   ├── PlayerChip.jsx
│   ├── MatchParticipantsEditModal.jsx
│   ├── NavigationMenu.jsx
│   └── ErrorBoundary.jsx
└── pages/
    ├── Home.jsx
    ├── Login.jsx
    ├── InviteRegister.jsx
    ├── Landing.jsx
    ├── Profile.jsx
    ├── ProfileEdit.jsx
    ├── PrivacyPolicy.jsx
    ├── TermsOfService.jsx
    ├── matches/
    │   ├── MatchList.jsx
    │   ├── MatchForm.jsx
    │   ├── MatchDetail.jsx
    │   ├── BulkResultInput.jsx
    │   └── MatchResultsView.jsx
    ├── practice/
    │   ├── PracticeList.jsx
    │   ├── PracticeForm.jsx
    │   ├── PracticeDetail.jsx
    │   ├── PracticeParticipation.jsx
    │   └── PracticeCancelPage.jsx
    ├── lottery/
    │   ├── LotteryResults.jsx
    │   ├── WaitlistStatus.jsx
    │   └── OfferResponse.jsx
    ├── line/
    │   ├── LineSettings.jsx
    │   ├── LineChannelAdmin.jsx
    │   └── LineScheduleAdmin.jsx
    ├── notifications/
    │   └── NotificationList.jsx
    ├── pairings/
    │   ├── PairingGenerator.jsx
    │   └── PairingSummary.jsx
    ├── players/
    │   ├── PlayerList.jsx
    │   ├── PlayerDetail.jsx
    │   └── PlayerEdit.jsx
    └── venues/
        ├── VenueList.jsx
        └── VenueForm.jsx
```
