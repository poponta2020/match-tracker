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
| 6 | `/`（認証時） | `Home.jsx` | ハンバーガーメニュー、繰り上げオファーバナー | ALL | ダッシュボード（次回練習・参加率TOP3（団体別フィルタリング）・繰り上げ通知） |

---

## 3. 試合管理（matches）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 7 | `/matches` | `MatchList.jsx` | `FilterBottomSheet` | ALL | 試合一覧（勝率・段位別統計・フィルタ）。自分の試合にはメモ有無アイコン・お手付き回数を表示 |
| 8 | `/matches/new` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト(0〜20)、個人メモ、抜け番活動種別選択、「抜け番として記録する」ボタン（ペアリング未作成時） | ALL | 試合結果入力（お手付き・個人メモ含む。抜け番の場合は活動記録。ペアリング未作成時は手動切替可能） |
| 9 | `/matches/:id` | `MatchDetail.jsx` | — | ALL | 試合詳細表示（自分のお手付き回数・個人メモを表示） |
| 10 | `/matches/:id/edit` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト、個人メモ | ALL | 試合結果編集（お手付き・個人メモの編集含む） |
| 11 | `/matches/bulk-input/:sessionId` | `BulkResultInput.jsx` | 組み合わせリスト、枚数差入力、抜け番活動入力 | ADMIN+ | 一括結果入力（抜け番の活動も含む。お手付き・個人メモは含まない） |
| 12 | `/matches/results/:sessionId?` | `MatchResultsView.jsx` | カレンダーピッカー、セッションナビ、抜け番活動表示 | ALL | 試合結果一覧（抜け番の活動もバッジ表示。自分の試合にお手付き・個人メモ表示） |

---

## 4. 練習管理（practice）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 13 | `/practice` | `PracticeList.jsx` | `PlayerChip`, `MatchParticipantsEditModal` | ALL | 練習日程一覧（月別カレンダー表示）。同一日に複数団体のセッションがある場合はカレンダーセルに団体ごとに表示。ADMIN+は「抽選結果を通知」ボタンでアプリ内+LINE通知を一括送信可能 |
| 14 | `/practice/new` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程作成 |
| 15 | `/practice/:id` | `PracticeDetail.jsx` | — | ALL | 練習日程詳細 |
| 16 | `/practice/:id/edit` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程編集 |
| 17 | `/practice/participation` | `PracticeParticipation.jsx` | 月ナビゲーション、試合番号チェックボックス、抽選ステータスバッジ、締め切り表示 | ALL | 参加登録（抽選済みセッションはステータス表示のみ）。締め切り前は「締め切り: ○月○日（あと○日）」を表示（締め切り後・締め切りなし時は非表示）。締め切り後は既存登録のチェックボックスがdisabled（グレーアウト）になり解除不可。未登録の試合への追加登録は可能 |
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
| 28 | `/lottery/results` | `LotteryResults.jsx` | 月ナビゲーション、当選/落選リスト、キャンセル待ち辞退/復帰ボタン、WAITLIST_DECLINEDバッジ | ALL | 月別抽選結果一覧。自分のキャンセル待ちセッションに辞退/復帰ボタンあり |
| 29 | `/lottery/waitlist` | `WaitlistStatus.jsx` | ステータスバッジ、応答リンク | ALL | 自分のキャンセル待ち状況 |
| 30 | `/lottery/offer-response` | `OfferResponse.jsx` | オファー詳細（日付・会場・試合・期限）、参加/辞退ボタン、期限切れ表示、処理済み表示 | ALL | 繰り上げ参加の承認/辞退 |
| 31 | `/notifications` | `NotificationList.jsx` | 通知カード、未読バッジ、キャンセル待ち辞退ボタン、一括削除ボタン | ALL | 通知一覧。LOTTERY_ALL_WON/LOTTERY_REMAINING_WON/LOTTERY_WAITLISTED対応。WAITLISTED通知にはインライン辞退ボタンあり。「すべて削除」で全通知を論理削除 |

---

## 8.5 通知設定（notifications / line）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 32 | `/settings/notifications` | `NotificationSettings.jsx` | Web Push通知セクション（有効化/無効化・種別ON/OFFトグル）、LINE通知セクション（連携状態・友だち追加・コード・種別トグル） | ALL | 統合通知設定（Web Push + LINE） |
| 33 | `/admin/line/channels` | `LineChannelAdmin.jsx` | チャネル一覧テーブル、新規登録フォーム、ステータスバッジ | SUPER_ADMIN | LINEチャネル管理（登録・無効化・強制解除） |
| 34 | `/admin/line/schedule` | `LineScheduleAdmin.jsx` | リマインダー設定カード、送信日数入力 | ADMIN+ | LINE通知スケジュール設定 |

---

## 8.6 システム設定（settings）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 38 | `/admin/settings` | `SystemSettings.jsx` | 締め切り日数入力（「締め切りなし」チェックボックス付き）、一般枠割合入力、プレビュー表示、確認ダイアログ | ADMIN+ | システム設定管理（抽選締め切り日数・一般枠保証割合の確認・変更） |

## 8.7 団体設定（organizations）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 39 | `/settings/organizations` | `OrganizationSettings.jsx` | 団体チェックボックス（色ドット付き）、最低1つ必須バリデーション | ALL | 参加する練習会の選択（わすらもち会 / 北海道大学かるた会） |

---

## 9. プロフィール

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 35 | `/profile` | `Profile.jsx` | ロールバッジ | ALL | 自分のプロフィール表示 |
| 36 | `/profile/edit` | `ProfileEdit.jsx` | パスワード変更セクション | ALL | プロフィール編集（※Layout なし）。`?changePassword=true` でパスワード変更強制モード |

---

## 9.5 伝助管理（densuke）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 37 | `/admin/densuke` | `DensukeManagement.jsx` | 月ナビゲーション、団体別ブロック（URL入力・同期ボタン・書き込み状況・同期結果・未登録者チェックリスト） | ADMIN+ | 団体別の伝助URL管理・手動同期実行・書き込み状況・未登録者確認・一括登録。ADMINは自団体のみ表示、SUPER_ADMINは全団体を並べて表示。各団体ブロックに団体カラーのアクセント付き |

---

## 10. その他

| # | パス | ページコンポーネント | 権限 | 説明 |
|---|------|---------------------|------|------|
| 39 | `/statistics` | （スタブ: `div`） | ALL | 統計画面（未実装: "実装中..."） |
| 40 | `*`（存在しないパス） | `Navigate` → `/` | — | 404リダイレクト |

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
| 練習日程作成 | `/practice/new` | ADMIN+ |
| 参加練習会 | `/settings/organizations` | ALL |
| 通知設定 | `/settings/notifications` | ALL |
| LINEチャネル管理 | `/admin/line/channels` | SUPER_ADMIN |
| LINE通知スケジュール | `/admin/line/schedule` | ADMIN+ |
| 伝助管理 | `/admin/densuke` | ADMIN+ |
| システム設定 | `/admin/settings` | ADMIN+ |
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
    ├── densuke/
    │   └── DensukeManagement.jsx
    ├── line/
    │   ├── LineSettings.jsx
    │   ├── LineChannelAdmin.jsx
    │   └── LineScheduleAdmin.jsx
    ├── notifications/
    │   ├── NotificationList.jsx
    │   └── NotificationSettings.jsx
    ├── pairings/
    │   ├── PairingGenerator.jsx
    │   └── PairingSummary.jsx
    ├── players/
    │   ├── PlayerList.jsx
    │   ├── PlayerDetail.jsx
    │   └── PlayerEdit.jsx
    ├── settings/
    │   ├── SystemSettings.jsx
    │   └── OrganizationSettings.jsx
    └── venues/
        ├── VenueList.jsx
        └── VenueForm.jsx
```
