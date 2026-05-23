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
| `PageHeader` | 独自トップバーを持たない画面で `Layout` のベースナビバー（空の緑バー）を上書きする共通コンポーネント。`title`（必須）・`backTo`（必須：戻るボタンの遷移先パス）・`rightActions`（任意：右端アクション要素）を受け取り、`fixed top-0 z-50` のトップバーを描画する。設定サブページ群／リスト→詳細・編集／ホーム導線の20ファイル22ルートで使用 |

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
| 7 | `/matches` | `MatchList.jsx` | `FilterBottomSheet` | ALL | 試合一覧（勝率・段位別統計・フィルタ）。各行頭に `M/D 会場名(N)` 形式で日付・会場・試合番号を表示（会場不明時は `M/D (N)`、長すぎる会場名は truncate）。自分の試合にはメモ有無アイコン・お手付き回数を表示。**行内タップ動線**: 対戦相手名タップで `/matches?playerId=<opponentId>` へ遷移（ゲスト選手は無効）、メモアイコンタップで対戦詳細へ遷移（自分閲覧時とメンター閲覧時のみ表示、メモ有/無で濃淡切替）。行全体タップによる詳細遷移は廃止 |
| 8 | `/matches/new` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト(0〜20)、個人メモ、抜け番活動種別選択、「抜け番として記録する」ボタン（ペアリング未作成時） | ALL | 試合結果入力（お手付き・個人メモ含む。抜け番の場合は活動記録。ペアリング未作成時は手動切替可能） |
| 9 | `/matches/:id` | `MatchDetail.jsx` | `MatchCommentThread`（メンター⇔メンティー間コメントスレッド。メンティー本人またはACTIVEメンターのみ表示。未通知コメントがある場合は「LINE通知を送信（N件）」ボタンを表示。コメント入力中はボトムナビを非表示にして誤タップを防止） | ALL | 試合詳細表示（自分のお手付き回数・個人メモを表示。詳細情報セクションに試合日・試合番号・会場の3カードを並べて表示。メンター関係がある場合はコメントスレッドを表示） |
| 10 | `/matches/:id/edit` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト、個人メモ | ALL | 試合結果編集（お手付き・個人メモの編集含む） |
| 11 | `/matches/bulk-input/:sessionId` | `BulkResultInput.jsx` | 組み合わせリスト、枚数差入力、抜け番活動入力、組み合わせ未作成メッセージ | ADMIN+ | 一括結果入力（抜け番の活動も含む。お手付き・個人メモは含まない）。組み合わせ未作成時はメッセージ表示+ADMIN以上に作成画面への遷移ボタン。**抜け番は試合ごとの組み合わせ対象参加者（抽選あり運用は WON のみ、抽選なし運用は WON+PENDING）からペア済み選手を除外して算出（CANCELLED 等は含めない）。組み合わせ対象に PENDING を含めるかは `PracticeSessionDto.pairingIncludesPending` フラグで判定** |
| 12 | `/matches/results/:sessionId?` | `MatchResultsView.jsx` | カレンダーピッカー、セッションナビ、抜け番活動表示 | ALL | 試合結果一覧（抜け番の活動もバッジ表示。自分の試合にお手付き・個人メモ表示）。**抜け番は試合ごとの組み合わせ対象参加者（抽選あり運用は WON のみ、抽選なし運用は WON+PENDING）からペア済み選手を除外して算出（CANCELLED 等は含めない）。組み合わせ対象に PENDING を含めるかは `PracticeSessionDto.pairingIncludesPending` フラグで判定** |

---

## 4. 練習管理（practice）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 13 | `/practice` | `PracticeList.jsx` | `PlayerChip`, `MatchParticipantsEditModal`, `AttendanceRegisterModal` | ALL | 練習日程一覧（月別カレンダー表示）。同一日に複数団体のセッションがある場合はカレンダーセルに団体ごとに表示。**会場名の下に試合別ステータスグリッドを表示**（試合番号順に左詰めで `○`／`△`／`×` を 3列固定 grid で配置: `○`＝空きあり（remaining > 2）／`△`＝残わずか（0 < remaining ≤ 2）／`×`＝満員（effectiveCount ≥ capacity）。effectiveCount は WON+PENDING+OFFERED）。**グリッド非表示条件**: 同日複数セッション／capacity null・0 以下／totalMatches null・0 以下・10 以上／`matchCapacityStatuses` が null または不正値混入。同日複数セッションのときはグリッドそのものを描画せず会場名のみ。参加状況背景色（`confirmed` / `waitlisted`）はグリッド記号の可読性確保のため既存より一段薄くしている。`?openToday=true` パラメータ付きアクセス時は当日セッションのポップアップを自動表示（LINEリッチメニューからの導線）。出欠登録は右下フローティングボタン（**過去月のときは非表示**）と選択セッション詳細部のインラインボタン（過去日でない場合のみ表示）の「出欠登録」ボタンから `AttendanceRegisterModal` を開き、「参加登録」「キャンセル登録」を選択して各画面へ遷移する（カレンダー表示中の年月をクエリパラメータで引き継ぐ）。**カレンダー表示月の抽選確定状態（`PlayerParticipationStatusDto.lotteryExecuted`）に応じて当月扱い／来月扱いを判定し、`AttendanceRegisterModal` の「キャンセル登録」ボタン表示を切り替える**（当月扱い＝現在年月、または未来月で抽選確定済みセッションが1つ以上ある月：両ボタン表示／来月扱い＝未来月で抽選確定済みセッションが0個の月：「参加登録」のみ表示）。ADMIN+は隣室チェック対象会場（かでる和室4部屋 + 東🌸）で隣室が空きの場合「隣室を予約」→「予約完了を報告」→「会場を拡張」の3段階操作で会場拡張が可能（東🌸はPhase 1の会場予約プロキシ対象外なので「隣室を予約」をスキップし初期状態から「予約完了を報告」を表示） |
| 14 | `/practice/new` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程作成 |
| 15 | `/practice/:id` | `PracticeDetail.jsx` | — | ALL | 練習日程詳細 |
| 16 | `/practice/:id/edit` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程編集 |
| 17 | `/practice/participation` | `PracticeParticipation.jsx` | 月ナビゲーション、試合番号チェックボックス、抽選ステータスバッジ、締め切り表示、`SaveProgressOverlay` | ALL | 参加登録（抽選済みセッションはステータス表示のみ）。試合番号チェック欄はチェックボックス周囲のラベル領域もタップ対象。締め切り前は「締め切り: ○月○日（あと○日）」を表示（締め切り後・締め切りなし時は非表示）。**カレンダー表示月の抽選確定状態に応じて当月扱い／来月扱いを判定し、既存登録（保存済み）のチェック外し挙動を切り替える**：当月扱い時は既存登録のチェックボックスを一律 disabled（解除不可、キャンセル画面の理由付きキャンセルへ誘導）／来月扱い時は既存登録もチェック外し可能（API上は未登録に戻す＝理由なしキャンセル）。締め切り後は来月扱いでも既存登録ロックを維持（既存仕様）。未登録の試合への追加登録は当月扱い・来月扱いいずれも可能。抽選確定済みセッションはステータス表示固定（現状維持）。一度キャンセルした試合（`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED`）はチェックが外れた未登録状態で表示され、再度チェックして再登録できる。クエリパラメータ `?year=YYYY&month=M` で初期表示月を指定可能（不正値時は現在月にフォールバック）。**保存ボタン押下時は `SaveProgressOverlay` で全画面オーバーレイ（保存中／完了／エラー）を表示し、完了時にユーザーが「カレンダーに戻る」ボタンを押すと `/practice` へ遷移する**（旧仕様の1秒タイマー自動遷移は廃止）。エラー時は「閉じる」で編集中のチェック状態を維持したまま画面に戻る |
| 18 | `/practice/cancel` | `PracticeCancelPage.jsx` | キャンセル専用カレンダー、試合選択チェックボックス、キャンセル理由ラジオボタン、当日12:00以降確認ダイアログ、`SaveProgressOverlay` | ALL | 参加キャンセル（WON または PENDING の参加日を抽選状態に関わらず赤系で統一ハイライトしたカレンダー→試合選択→理由選択→確認ダイアログ）。試合選択ではステータス（当選/申込）を区別するバッジは表示せず、第N試合のみを表示。当日12:00（JST）以降のキャンセル時は「当日キャンセルとなります。補充募集が行われます。」の追加警告を確認ダイアログに表示。**月ナビゲーション（前月/翌月ボタン・YearMonthPicker）は廃止し、対象月はクエリパラメータ `?year=YYYY&month=M` で固定**（クエリ未指定時は現在年月にフォールバック）。`PracticeList` のキャンセル登録ボタン経由（当月扱いの月でのみ表示）で年月を引き継いで遷移する想定。タイトル下に「○年○月」を中央寄せで固定表示する。**キャンセル実行押下時は `SaveProgressOverlay` で全画面オーバーレイ（キャンセル処理中／完了／エラー）を表示し、完了時に「カレンダーに戻る」ボタンで `/practice` へ遷移する**（旧仕様の `alert` 通知は廃止）。エラー時は「閉じる」で選択状態（試合・理由）を維持したまま画面に戻る |

補足: `venue-reservation-proxy` はバックエンド Controller / Service 層、フロントエンド API クライアント / venue 判別ユーティリティ、`/practice` の隣室予約導線接続まで実装済み。新規フロントエンドルートは追加されていないが、予約操作時は新規タブで `/api/venue-reservation-proxy/view?token=...` のプロキシ画面を開く。Kaderu は会場サイトの hidden field を引き継いで申込トレイ画面を準備し、会場サイト由来のCSS `@import` / `url(...)` もプロキシ経由に書き換えて表示する。旧 `/api/kaderu/*` 導線は削除済み。

---

## 5. 組み合わせ管理（pairings）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 19 | `/pairings` | `PairingGenerator.jsx` | 参加者リスト、待機者リスト（D&D / タップ選択対応）、対戦履歴（当日他試合で組まれたペアは `⚠今日` 赤字警告）、新規作成ドロップゾーン、DraggablePlayerChip、DroppableSlot、結果入力済ロック表示・リセットボタン | ADMIN+ | 組み合わせ作成（ドラッグ&ドロップ or タップ選択モード）。タップ選択はスマホ向け代替操作で、選手をタップで選択→別カード/空き枠/待機/新規ペアゾーンをタップで配置。結果入力済みペアリングはロック表示（グレーアウト+「結果入力済」バッジ）、個別リセット可能。**組み合わせ対象は団体の運用設定により切り替わる: 抽選あり運用 (MONTHLY+締め切りあり) は WON のみ / 抽選なし運用 (SAME_DAY または MONTHLY+締め切りなし) は WON+PENDING（バックエンドの `PracticeSessionDto.pairingIncludesPending` で判定）** |
| 20 | `/pairings/summary` | `PairingSummary.jsx` | カレンダーピッカー、試合番号タブ | ADMIN+ | 組み合わせ一覧表示 |

---

## 6. 選手管理（players）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 21 | `/players` | `PlayerList.jsx` | 検索、段位ソート、ロールバッジ、招待リンク生成（グループ用/個人用、招待先団体セレクタで指定） | SUPER_ADMIN | 選手一覧 |
| 22 | `/players/new` | `PlayerEdit.jsx` | — | SUPER_ADMIN | 選手新規作成 |
| 23 | `/players/:id` | `PlayerDetail.jsx` | — | SUPER_ADMIN | 選手詳細 |
| 24 | `/players/:id/edit` | `PlayerEdit.jsx` | ロールがADMINの場合に管理団体ドロップダウン表示（SUPER_ADMIN専用） | SUPER_ADMIN | 選手編集 |

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
| 28 | `/lottery/results` | `LotteryResults.jsx` | 月ナビゲーション、当選/落選リスト、キャンセル待ち辞退/復帰ボタン、WAITLIST_DECLINEDバッジ、管理者向けコピーテキスト領域（textarea + コピーボタン、ADMIN/SUPER_ADMIN かつ抽選確定済の月のみ表示） | ALL | 月別抽選結果の閲覧専用画面。自分のキャンセル待ちセッションに辞退/復帰ボタンあり。抽選確定操作は `/admin/lottery` 側に集約しており、本画面では確定ステータス表示・確定ボタンを持たない。管理者は画面下部にLINE告知用の整形済みコピーテキスト（キャンセル待ちのみ）を取得できる |
| 29 | `/lottery/waitlist` | `WaitlistStatus.jsx` | ステータスバッジ、応答リンク | ALL | 自分のキャンセル待ち状況 |
| 30 | `/lottery/offer-response` | `OfferResponse.jsx` | オファー詳細（日付・会場・試合・期限）、同一セッション内の複数オファー統合表示、個別参加ボタン(緑)×N、すべての試合に参加ボタン(青・2試合以上時)、辞退ボタン(赤・一括辞退)、部分参加後の残りオファー再表示、期限切れ表示、処理済み表示 | ALL | 繰り上げ参加の承認/辞退。同一セッション内の複数オファーを統合表示し、個別参加・一括参加・一括辞退に対応 |
| 30.1 | `/admin/lottery` | `LotteryManagement.jsx` | 年月セレクター、参加希望者一覧・優先選手指定（チップUI、選択中人数表示、確定後disabled、sessionStorage保存）、抽選実行ボタン、プレビュー結果表示（セッション別・試合別の当選/キャンセル待ち一覧）、確定ボタン、全員通知送信ボタン、キャンセル待ちのみ通知送信ボタン、通知送信結果表示、LINE告知用コピーテキスト領域（プレビュー段階はオレンジ警告色＋「※ プレビュー（未確定）」ラベル、確定後は青色）、システム設定リンク | ADMIN+ | 抽選管理画面。優先選手指定→抽選プレビュー→結果確認→確定→通知送信の一連のワークフローを提供。プレビュー段階および確定後にLINE告知用テキストをコピー可能（誤配信防止のためプレビュー時はボタンを警告色化）。通知送信ボタンは抽選確定後、表示中の月が終わるまで（=現在の年月以降の間）表示し続けるため、月切り替え後でも対象月の通知再送信が可能。SettingsPageのグリッドからアクセス |
| 31 | `/notifications` | `NotificationList.jsx` | 通知カード、未読バッジ、キャンセル待ち辞退ボタン、一括削除ボタン | ALL | 通知一覧。LOTTERY_ALL_WON/LOTTERY_REMAINING_WON/LOTTERY_WAITLISTED対応。WAITLISTED通知にはインライン辞退ボタンあり。「すべて削除」で全通知を論理削除 |

---

## 8.5 通知設定（notifications / line）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 32 | `/settings/notifications` | `NotificationSettings.jsx` | LINE通知セクション（連携状態・友だち追加・コード・種別トグル）、当日キャンセル補充通知トグル3種（参加者確定通知・当日キャンセル通知・空き募集通知）、管理者通知セクション（ADMIN/SUPER_ADMIN） | ALL | LINE通知設定画面。LINE通知セクションに当日キャンセル補充関連の通知トグル3種（sameDayConfirmation / sameDayCancel / sameDayVacancy）。ADMIN/SUPER_ADMINは「管理者通知」セクションが追加表示され、参加者確定通知（adminSameDayConfirmation）、キャンセル待ち状況通知（adminWaitlistUpdate）、当日キャンセル・参加・空き枠通知（adminSameDayCancel）の3トグルを制御可能。**管理者（ADMIN/SUPER_ADMIN）の場合、LINE通知セクションは「選手用LINE」「管理者用LINE」の2セクション表示**。各セクションは独立して有効化/無効化・友だち追加・コードリンクが可能。通知種別トグルも用途別に振り分け表示（選手用: 抽選結果・キャンセル待ち等、管理者用: キャンセル待ち状況通知・当日確認まとめ・当日キャンセル・参加・空き枠通知）。※Web Push通知はバックエンド・Service Worker・APIクライアント・DBスキーマは保持しているが、UI（有効化/無効化・種別ON/OFFトグル）はこの画面から削除済み |
| 33 | `/admin/line/channels` | `LineChannelAdmin.jsx` | タブUI（選手用/管理者用）、チャネル一覧テーブル、新規登録フォーム、ステータスバッジ | SUPER_ADMIN | LINEチャネル管理（登録・無効化・強制解除）。「選手用」「管理者用」タブで用途別にフィルタリング表示。チャネル追加時は選択中タブの用途が自動セットされる |
| 34 | `/admin/line/schedule` | `LineScheduleAdmin.jsx` | リマインダー設定カード、送信日数入力 | ADMIN+ | LINE通知スケジュール設定 |

---

## 8.6 システム設定（settings）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 38 | `/admin/settings` | `SystemSettings.jsx` | 対象団体セレクタ（SUPER_ADMIN）、締め切り日数入力（「締め切りなし」チェックボックス付き）、一般枠割合入力、プレビュー表示、確認ダイアログ | ADMIN+ | システム設定管理（抽選締め切り日数・一般枠保証割合の確認・変更）。ADMINは自団体固定、SUPER_ADMINは対象団体を選択して変更 |

## 8.7 メンター管理（mentor）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 39.1 | `/settings/mentor` | `MentorManagement.jsx` | 承認待ちリクエスト（承認/拒否ボタン）、マイメンター（指名フォーム・解除ボタン・ステータス表示）、マイメンティー（試合履歴ナビ・解除ボタン） | ALL | メンター関係の管理。メンティーはメンターを指名、メンターは承認/拒否。メンティーの試合履歴（`/matches?playerId=X`）への導線あり |

---

## 8.8 団体設定（organizations）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 39 | `/settings/organizations` | `OrganizationSettings.jsx` | 団体チェックボックス（色ドット付き）、最低1つ必須バリデーション | ALL | 参加する練習会の選択（わすらもち会 / 北海道大学かるた会） |

---

## 8.9 カレンダー購読（calendar）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 40 | `/settings/calendar` | `CalendarSubscriptionPage.jsx` | 「このページについて」常時表示ボックス（目的・使い方3ステップ・カレンダーに表示されるもののスコープ説明）、所属団体ごとのフィードURLカード（複数、URL+コピーボタン+操作ガイドのサブテキスト常時表示+カードごとのコピー成功フィードバック）、ゲスト参加フィードURLカード（カレンダー名は「ゲスト参加」固定）、「登録手順を見る」アコーディオン（Google PCブラウザ / Apple iPhone、初期は全て閉じる・同時に1つだけ開く、テキスト手順のみ）、表示名カスタマイズカード（PlayerOrganization ごとに入力欄、maxLength=50、説明文に `{表示名}＠{会場名}` 形式の表示例つき、一括「表示名を保存」ボタン）、URL一括再発行カード（`window.confirm` で「すべてのカレンダーURLが一斉に無効」と警告） | ALL | iCalフィードURLの取得・コピー・再発行・表示名カスタマイズ。所属団体ごと（`/ical/calendar/{token}/org/{orgId}.ics`）とゲスト参加（`/ical/calendar/{token}/guest.ics`）の複数URLを管理。Googleカレンダー・Apple Calendar・Outlookに登録すると、カレンダー単位を分けることで購読側で団体ごとに色分けできる。画面冒頭の「このページについて」ボックスと、画面内アコーディオンに収めた Google/Apple のカレンダー登録手順により、外部ドキュメント不要で利用方法が完結する。表示名カスタマイズは `PlayerOrganization.calendar_display_name` に保存され、該当団体カードのカレンダー名・イベントタイトル・X-WR-CALNAME に反映（ゲスト参加カレンダー内のイベントタイトルは `Organization.name` 固定）。再発行は親トークン `Player.ical_feed_token` を上書きするため、所属団体URL+ゲストURLすべてが一斉に新URLに切り替わる |

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
| 37 | `/admin/densuke` | `DensukeManagement.jsx` | 月ナビゲーション、団体別ブロック（URL入力・同期ボタン・書き込み状況・同期結果・未登録者チェックリスト・**伝助ページ作成ボタン**・**作り直すボタン**・**テンプレート編集ボタン**）、`DensukePageCreateModal`、`DensukeTemplateModal` | ADMIN+ | 団体別の伝助URL管理・手動同期実行・書き込み状況・未登録者確認・一括登録・**伝助ページ自動作成**（アプリの練習日データから densuke.biz にページを新規発行。当月+未来2ヶ月まで作成可能、既に URL 登録済みの月は作成ボタン非表示）・**作り直す**（既に URL 登録済みの月に表示。確認ダイアログ→アプリ側の `densuke_urls` レコード削除→作成モーダル自動オープンの流れ。旧 densuke.biz ページ自体は削除できないためそのまま残るが、アプリからは参照されなくなる。削除成功時は同期結果・未登録者選択・書き込み状況もリセット）・**テンプレート編集**（団体ごとのタイトル・説明・連絡先メアドのデフォルト値、プレースホルダー `{year}` / `{month}` / `{organization_name}` 対応）。ADMINは自団体のみ表示、SUPER_ADMINは全団体を並べて表示。各団体ブロックに団体カラーのアクセント付き |

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
| `Layout` | `components/Layout.jsx` | ヘッダーバー（通知ベル・プロフィール）+ 下部ナビゲーション付き共通レイアウト。`BottomNavContext` の `isVisible` に応じてボトムナビの表示/非表示をスライドアニメーションで切り替え |
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
| メンター管理 | `/settings/mentor` | ALL |
| 通知設定 | `/settings/notifications` | ALL |
| LINEチャネル管理 | `/admin/line/channels` | SUPER_ADMIN |
| LINE通知スケジュール | `/admin/line/schedule` | ADMIN+ |
| 伝助管理 | `/admin/densuke` | ADMIN+ |
| システム設定 | `/admin/settings` | ADMIN+ |
| カレンダー購読 | `/settings/calendar` | ALL |
| ログアウト | — | ALL |

---

## ファイルパス一覧

```
karuta-tracker-ui/src/
├── App.jsx                          # ルート定義
├── context/AuthContext.jsx          # 認証コンテキスト
├── context/BottomNavContext.jsx     # ボトムナビ表示制御コンテキスト
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
    │   ├── MatchCommentThread.jsx
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
    │   ├── DensukeManagement.jsx
    │   ├── DensukePageCreateModal.jsx
    │   └── DensukeTemplateModal.jsx
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
    ├── mentor/
    │   └── MentorManagement.jsx
    ├── settings/
    │   ├── SystemSettings.jsx
    │   └── OrganizationSettings.jsx
    └── venues/
        ├── VenueList.jsx
        └── VenueForm.jsx
```
