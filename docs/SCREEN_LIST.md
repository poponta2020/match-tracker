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
| 7 | `/matches` | `MatchList.jsx` | `FilterBottomSheet` | ALL | 試合一覧（勝率・段位別統計・フィルタ）。**各行は CSS Grid による 7 列のテーブル風レイアウトで列揃え**され、左から「日付 `M/D`」「対戦相手名」「勝敗（〇N/×N/△N）」「会場名 N試合目」「動画アイコン」「メモアイコン」「お手付き回数 `手N`」の順に並ぶ（会場不明時は `N試合目` のみ表示、長すぎる対戦相手名・会場名は truncate）。**カラム幅は `1.75rem / 5.25rem / 2.5rem / minmax(0,1fr) / 1.5rem / 1.5rem / 2rem`** で、対戦相手名は `text-sm`（`5.25rem` = 84px）固定、会場名列が残り幅を受け取る配分。動画アイコン・メモアイコン・お手付きは非表示条件の行でも列幅を確保し、全行で各列の左端 x 座標が揃う。**動画アイコン列**: `MatchDto.video` が non-null の行のみ `Video` アイコン（lucide-react・`w-3.5 h-3.5`・赤系 `text-[#c0392b]` で当日結果一覧の「動画あり」バッジとトーンを合わせる）を表示し、動画なしの行は不可視プレースホルダーで列幅のみ確保。**行内タップ動線**: 対戦相手名タップで `/matches?playerId=<opponentId>` へ遷移（ゲスト選手は無効）、動画アイコンタップで対戦詳細 `/matches/{id}`（他選手閲覧時は `?playerId=` 付き）へ遷移（**閲覧制限なし＝自分・他選手いずれの一覧でも表示**）、メモアイコンタップで対戦詳細へ遷移（自分閲覧時とメンター閲覧時のみ表示、メモ有/無で濃淡切替）。行全体タップによる詳細遷移は廃止。**読み・一人取りの抜け番行**: その選手が抜け番で読み（`READING`）・一人取り（`SOLO_PICK`）を行った回を、同日の試合から借用した会場名付きで「会場名 N試合目 活動名」（例: `クラ館 1試合目 読み`）として該当試合番号の位置（日付降順→試合番号降順）に差し込み表示（会場不明日は会場省略、活動アイコン付き、タップ無効、自分・他選手とも表示。`GET /api/bye-activities/player/{id}` を利用）。相手前提フィルタ（結果/相手名/級・性別・利き手）有効時は非表示、期間フィルタには連動。統計エリアに「読み n回・一人取り m回」を活動別併記（期間連動・0回非表示、勝敗統計には不算入）。**指導試合は勝敗欄をグレーで「指導」表示（色付け・〇×なし）**。統計エリアの総合の下に「指導 n回・被指導 m回」を併記（値>0のみ・期間/属性連動） |
| 8 | `/matches/new` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト(0〜20)、個人メモ、抜け番活動種別選択、「抜け番として記録する」ボタン（ペアリング未作成時） | ALL | 試合結果入力（お手付き・個人メモ含む。抜け番の場合は活動記録。ペアリング未作成時は手動切替可能）。**枚数差ピッカー末尾で「指導」を選択可能（登録済み相手のみ）** |
| 9 | `/matches/:id` | `MatchDetail.jsx` | `MatchCommentThread`（メンター⇔メンティー間コメントスレッド。メンター閲覧時はACTIVEメンター関係があれば表示、メンティー本人画面では自分以外の投稿者のコメントが1件以上ある場合のみ表示。未通知コメントがある場合は「LINE通知を送信（N件）」ボタンを表示。コメント入力中はボトムナビを非表示にして誤タップを防止）、`VideoRegisterModal`（試合動画の登録/編集モーダル） | ALL | 試合詳細表示。**1つの統合カード**に「対戦相手名 〇/×/△ 枚数差（絶対値）」を上段、「試合日 第N試合 会場名」を1行で中段、「お手付き」「メモ」を下段に表示（メンター閲覧時はメンティーのお手付き・メモ、メンティー本人閲覧時は自分のお手付き・メモを統合カード内に表示）。表示条件を満たす場合のみコメントスレッドを表示。**統合カードの下に「試合動画」セクションを表示**（`MatchDto.video` の有無で内容を切替）。動画ありの場合は YouTube 埋め込みプレイヤー（`youtube-nocookie.com/embed/{youtubeVideoId}`・16:9 レスポンシブ・全画面可）をインライン再生し、動画タイトル（あれば）と「YouTubeで開く」リンク（`videoUrl`・新規タブ）を表示。**「編集」「削除」ボタンは登録者本人（`video.createdBy` == ログインユーザーID）または ADMIN/SUPER_ADMIN のみ表示**（編集は `VideoRegisterModal` を編集モードで開く、削除は確認ダイアログ「削除されるのは試合との紐付けのみで、YouTube上の動画は残ります」で `DELETE /api/match-videos/{id}` を実行）。動画なしの場合は**両選手が登録済みの試合のみ**「動画を追加」ボタンを表示（全ロール可、`VideoRegisterModal` を新規モードで開く。ゲスト/未登録相手の試合では非表示）。追加/編集/削除の成功時は試合詳細を再取得。削除APIのエラー（403等）はセクション内にメッセージ表示 |
| 10 | `/matches/:id/edit` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト、個人メモ | ALL | 試合結果編集（お手付き・個人メモの編集含む）。**指導試合の選択/解除も可能（登録済み相手は詳細版APIで保存）** |
| 11 | `/matches/bulk-input/:sessionId` | `BulkResultInput.jsx` | 組み合わせリスト、枚数差入力（末尾に「指導」）、抜け番活動入力、組み合わせ未作成メッセージ | PLAYER+ | 一括結果入力（抜け番の活動も含む。お手付き・個人メモは含まない）。**指導試合は両者黒・中央「指導」表示**。ADMIN/PLAYERは自/所属団体のみ。組み合わせ未作成時はメッセージ表示+全ロールに作成画面への遷移ボタン。**抜け番は試合ごとの組み合わせ対象参加者（抽選あり運用は WON のみ、抽選なし運用は WON+PENDING）からペア済み選手を除外して算出（CANCELLED 等は含めない）。組み合わせ対象に PENDING を含めるかは `PracticeSessionDto.pairingIncludesPending` フラグで判定** |
| 12 | `/matches/results/:sessionId?` | `MatchResultsView.jsx` | カレンダーピッカー、セッションナビ、抜け番活動表示、`VideoPlayerModal`（試合動画の再生モーダル） | ALL | 試合結果一覧（抜け番の活動もバッジ表示。自分の試合にお手付き・個人メモ表示）。**指導試合は両者黒・中央「指導」表示（緑・〇×なし）**。**抜け番は試合ごとの組み合わせ対象参加者（抽選あり運用は WON のみ、抽選なし運用は WON+PENDING）からペア済み選手を除外して算出（CANCELLED 等は含めない）。組み合わせ対象に PENDING を含めるかは `PracticeSessionDto.pairingIncludesPending` フラグで判定**。**表示対象日が確定したら `GET /api/match-videos?date=YYYY-MM-DD` で当日の動画一覧を取得し、各組（試合番号＋選手ペア。選手ペアは `Math.min`/`Math.max` で正規化して動画側の `player1Id < player2Id` 正規化済みデータと照合）に対応する動画があれば、組のスコア/`vs` 行の下に「動画あり」バッジ（`Video` アイコン＋赤系の小バッジ）を表示。**バッジタップ時の挙動**: その組の結果が入力済み（対応する match レコードあり）なら試合詳細 `/matches/{matchId}` へ遷移、結果未入力（組み合わせのみ）なら `VideoPlayerModal` を開いてインライン再生（選手名タップの `/matches?playerId=...` 遷移と競合しないよう `stopPropagation`）。動画一覧の取得失敗時はバッジを出さず静かに無視（コンソールエラーのみ） |

---

## 3.5 動画倉庫（videos）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 12.5 | `/videos` | `VideoLibrary.jsx` | `VideoPlayerModal`（再生モーダル）、`VideoRegisterModal`（試合選択モードの登録モーダル） | ALL | 動画倉庫（登録済み試合動画の一覧・検索・登録導線）。設定画面メニューの「動画倉庫」から遷移。**一覧**は YouTube サムネイル（`https://i.ytimg.com/vi/{youtubeVideoId}/mqdefault.jpg`・16:9）付きの縦リスト（モバイルファースト。背景・影付きカードではなく、各行を薄い区切り線で区切るフラットスタイル。上部の絞り込みパネルも同様にフラット）で、各行に「試合日（`YYYY/MM/DD`） 第N試合」「対戦カード（player1Name vs player2Name）」「結果（`winnerId`/`scoreDifference` があれば『勝者名〇N』。N=枚数差、対戦一覧（`MatchList`）と同じ表記に統一。枚差未入力時は『勝者名〇』）」を表示（YouTube 動画タイトルは一覧では非表示。再生モーダルでは引き続き表示）。並びは `GET /api/match-videos/search` が返す試合日降順で、**ページングは `totalPages` を用いた「もっと見る」方式**（次ページを末尾に追記読み込み）。**検索・絞り込み**（変更時は page=0 から再検索）は ①選手絞り込み（`playerAPI.getAll` の選手を名前部分一致でフィルタするセレクト → `playerId`）②年月絞り込み（年セレクト＋月セレクト。未選択＝全期間、年のみ選択可 → `year`/`month`）③「自分が関わる動画」トグル（ON で `mine=true`。**選手絞り込みより優先**され、ON 中は選手絞り込みを無効化・クリア）。一覧タップで `VideoPlayerModal` をインライン再生（MatchVideoDto をそのまま渡す。結果入力済みなら「試合詳細を見る」リンクも表示）。**「動画を登録」ボタン**で `VideoRegisterModal` を試合選択モードで開き、登録成功時は一覧を再検索。0件時は空状態（条件なし時「動画がまだ登録されていません」／絞り込み時「条件に合う動画がありません」）を表示 |

---

## 4. 練習管理（practice）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 13 | `/practice` | `PracticeList.jsx` | `PlayerChip`, `MatchParticipantsEditModal`, `AttendanceRegisterModal` | ALL | 練習日程一覧（月別カレンダー表示）。同一日に複数団体のセッションがある場合はカレンダーセルに団体ごとに表示。**会場名の下に試合別ステータスグリッドを表示**（試合番号順に左詰めで `○`／`△`／`×` を 3列固定 grid で配置: `○`＝空きあり（remaining > 2）／`△`＝残わずか（0 < remaining ≤ 2）／`×`＝満員（effectiveCount ≥ capacity）。effectiveCount は WON+PENDING+OFFERED）。**グリッド非表示条件**: 同日複数セッション／フォールバック適用後の有効 capacity が null・0 以下（有効 capacity = session.capacity を優先、null のときのみ venue 既定 capacity にフォールバック。session.capacity = 0 のような明示値は venue 既定値で上書きされずそのまま非表示）／totalMatches null・0 以下・10 以上／`matchCapacityStatuses` が null または不正値混入。同日複数セッションのときはグリッドそのものを描画せず会場名のみ。参加状況背景色（`confirmed` / `waitlisted`）はグリッド記号の可読性確保のため既存より一段薄くしている。`?openToday=true` パラメータ付きアクセス時は当日セッションのポップアップを自動表示（LINEリッチメニューからの導線）。出欠登録は右下フローティングボタン（**過去月のときは非表示**）と選択セッション詳細部のインラインボタン（過去日でない場合のみ表示）の「出欠登録」ボタンから `AttendanceRegisterModal` を開き、「参加登録」「キャンセル登録」を選択して各画面へ遷移する（カレンダー表示中の年月をクエリパラメータで引き継ぐ）。**カレンダー表示月の抽選確定状態（`PlayerParticipationStatusDto.lotteryExecuted`）に応じて当月扱い／来月扱いを判定し、`AttendanceRegisterModal` の「キャンセル登録」ボタン表示を切り替える**（当月扱い＝現在年月、または未来月で抽選確定済みセッションが1つ以上ある月：両ボタン表示／来月扱い＝未来月で抽選確定済みセッションが0個の月：「参加登録」のみ表示）。ADMIN+は隣室チェック対象会場（かでる和室4部屋 + 東🌸）で隣室が空きの場合「隣室を予約」→「予約完了を報告」→「会場を拡張」の3段階操作で会場拡張が可能（東🌸はPhase 1の会場予約プロキシ対象外なので「隣室を予約」をスキップし初期状態から「予約完了を報告」を表示）。**練習日ポップアップではセッション単位の「再抽選」ボタンは表示しない**（バックエンドAPI `POST /api/lottery/re-execute/{sessionId}` はADMIN+で稼働継続だが、UIからの呼び出し導線は提供しない） |
| 14 | `/practice/new` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | ADMIN+ | 練習日程作成（ADMIN は自団体、SUPER_ADMIN は団体選択）。**上部ナビバーの月送り右矢印の右側に「Kaderu: {orgCode}」小ボタンを団体ごとに配置**（ADMIN は自団体のみ、SUPER_ADMIN は全団体）。押下すると `POST /api/kaderu-sync/trigger` で GitHub Actions の手動同期 workflow を起動し、PENDING 中は「{orgCode} 同期中… mm:ss」表示で disabled。完了/失敗は押下者本人の LINE 通知（`ADMIN_KADERU_SYNC_COMPLETED` / `ADMIN_KADERU_SYNC_FAILED`）で通知され、ユーザーは画面を手動リロード |
| 15 | `/practice/:id` | `PracticeDetail.jsx` | — | ALL | 練習日程詳細 |
| 16 | `/practice/:id/edit` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程編集 |
| 17 | `/practice/participation` | `PracticeParticipation.jsx` | 月ナビゲーション、試合番号チェックボックス、抽選ステータスバッジ、締め切り表示、`SaveProgressOverlay` | ALL | 参加登録（抽選済みセッションはステータス表示のみ）。試合番号チェック欄はチェックボックス周囲のラベル領域もタップ対象。締め切り前は「締め切り: ○月○日（あと○日）」を表示（締め切り後・締め切りなし時は非表示）。**カレンダー表示月の抽選確定状態に応じて当月扱い／来月扱いを判定し、既存登録（保存済み）のチェック外し挙動を切り替える**：当月扱い時は既存登録のチェックボックスを一律 disabled（解除不可、キャンセル画面の理由付きキャンセルへ誘導）／来月扱い時は既存登録もチェック外し可能（API上は未登録に戻す＝理由なしキャンセル）。締め切り後は来月扱いでも既存登録ロックを維持（既存仕様）。未登録の試合への追加登録は当月扱い・来月扱いいずれも可能。抽選確定済みセッションはステータス表示固定（現状維持）。一度キャンセルした試合（`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED`）はチェックが外れた未登録状態で表示され、再度チェックして再登録できる。クエリパラメータ `?year=YYYY&month=M` で初期表示月を指定可能（不正値時は現在月にフォールバック）。**保存ボタン押下時は `SaveProgressOverlay` で全画面オーバーレイ（保存中／完了／エラー）を表示し、完了時にユーザーが「カレンダーに戻る」ボタンを押すと `/practice` へ遷移する**（旧仕様の1秒タイマー自動遷移は廃止）。エラー時は「閉じる」で編集中のチェック状態を維持したまま画面に戻る |
| 18 | `/practice/cancel` | `PracticeCancelPage.jsx` | キャンセル専用カレンダー、試合選択チェックボックス、キャンセル理由ラジオボタン、当日12:00以降確認ダイアログ、`SaveProgressOverlay` | ALL | 参加キャンセル（WON または PENDING の参加日を抽選状態に関わらず赤系で統一ハイライトしたカレンダー→試合選択→理由選択→確認ダイアログ）。試合選択ではステータス（当選/申込）を区別するバッジは表示せず、第N試合のみを表示。当日12:00（JST）以降のキャンセル時は「当日キャンセルとなります。補充募集が行われます。」の追加警告を確認ダイアログに表示。**月ナビゲーション（前月/翌月ボタン・YearMonthPicker）は廃止し、対象月はクエリパラメータ `?year=YYYY&month=M` で固定**（クエリ未指定時は現在年月にフォールバック）。`PracticeList` のキャンセル登録ボタン経由（当月扱いの月でのみ表示）で年月を引き継いで遷移する想定。タイトル下に「○年○月」を中央寄せで固定表示する。**キャンセル実行押下時は `SaveProgressOverlay` で全画面オーバーレイ（キャンセル処理中／完了／エラー）を表示し、完了時に「カレンダーに戻る」ボタンで `/practice` へ遷移する**（旧仕様の `alert` 通知は廃止）。エラー時は「閉じる」で選択状態（試合・理由）を維持したまま画面に戻る |

補足: `venue-reservation-proxy` はバックエンド Controller / Service 層、フロントエンド API クライアント / venue 判別ユーティリティ、`/practice` の隣室予約導線接続まで実装済み。新規フロントエンドルートは追加されていないが、予約操作時は新規タブで `/api/venue-reservation-proxy/view?token=...` のプロキシ画面を開く。Kaderu は会場サイトの hidden field を引き継いで申込トレイ画面を準備し、会場サイト由来のCSS `@import` / `url(...)` もプロキシ経由に書き換えて表示する。旧 `/api/kaderu/*` 導線は削除済み。

---

## 5. 組み合わせ管理（pairings）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 19 | `/pairings` | `PairingGenerator.jsx` | 参加者リスト、待機者リスト（D&D / タップ選択対応）、対戦履歴（当日他試合で組まれたペアは `⚠今日` 赤字警告）、新規作成ドロップゾーン、DraggablePlayerChip、DroppableSlot、結果入力済ロック表示・リセットボタン | PLAYER+ | 組み合わせ作成（ドラッグ&ドロップ or タップ選択モード）。ADMIN/PLAYERは自/所属団体のみ。削除系のみ ADMIN+ 専用。タップ選択はスマホ向け代替操作で、選手をタップで選択→別カード/空き枠/待機/新規ペアゾーンをタップで配置。結果入力済みペアリングはロック表示（グレーアウト+「結果入力済」バッジ）、個別リセット可能。**組み合わせ対象は団体の運用設定により切り替わる: 抽選あり運用 (MONTHLY+締め切りあり) は WON のみ / 抽選なし運用 (SAME_DAY または MONTHLY+締め切りなし) は WON+PENDING（バックエンドの `PracticeSessionDto.pairingIncludesPending` で判定）** |
| 20 | `/pairings/summary` | `PairingSummary.jsx` | カレンダーピッカー、試合番号タブ | PLAYER+ | 組み合わせ一覧表示。札ルールは URL `?date=YYYY-MM-DD` 単位で localStorage（キー `karuta-tracker:card-rules:<YYYY-MM-DD>`）に保存され、同一日内であれば対戦再生成後も同じ札ルールが復元される。画面ロード時に今日以外の日付の保存値はまとめて削除する。試合数と保存長の不一致時は「保存が短ければ末尾追加・上書き／保存が長ければ先頭から表示用に切り出し（保存値は保持）」で吸収。「札を再生成」ボタンは `window.confirm` で上書き再生成の確認ダイアログを表示し、OK 時のみ再生成・localStorage 上書き。各試合に「読み」設定の抜け番選手がいれば `{N}試合目` 行直後に `【読手：○○】`（同一試合に複数いる場合は「、」区切り）を出力する（`byeActivityAPI.getByDate(date)` で取得、取得失敗時は読手なしで継続） |

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
| `Layout` | `components/Layout.jsx` | ヘッダーバー（プロフィール）+ 下部ナビゲーション付き共通レイアウト。`BottomNavContext` の `isVisible` に応じてボトムナビの表示/非表示をスライドアニメーションで切り替え |
| `PrivateRoute` | `components/PrivateRoute.jsx` | 認証ガード＋プロフィール設定チェック |
| `AuthRoute` | `components/AuthRoute.jsx` | 認証状態による条件分岐レンダリング |
| `FilterBottomSheet` | `components/FilterBottomSheet.jsx` | 試合フィルタUI（年月・段位・性別・利き手・結果） |
| `PlayerChip` | `components/PlayerChip.jsx` | 選手バッジ |
| `MatchParticipantsEditModal` | `components/MatchParticipantsEditModal.jsx` | 試合参加者編集モーダル |
| `VideoPlayerModal` | `components/VideoPlayerModal.jsx` | 試合動画 再生モーダル（YouTube 埋め込み・対戦情報・`matchId` 非null時「試合詳細を見る」リンク）。倉庫一覧・当日結果一覧から利用 |
| `VideoRegisterModal` | `components/VideoRegisterModal.jsx` | 試合動画 登録/編集モーダル。**固定モード**（`match` を渡す。試合詳細画面から URL 入力ステップのみ）と**選択モード**（`selectMode`。動画倉庫から ①試合選択→②URL 入力 の2段構成。①は「日付から」「選手から」のタブで候補を絞り込み登録済み試合はグレーアウト）の2モードを持つ。**「日付から」タブは `GET /api/match-videos/date-candidates` を使用し、参加日スコープに依存せず**（その日の練習に参加していない撮影担当・第三者でも候補が出る）、組み合わせ・試合結果を統合した当日の全カードを候補表示する（組織スコープは適用）。各候補は**登録済み（同自然キーの動画あり）はグレーアウトで選択不可**、**相手が未登録（`player1Id`/`player2Id` が `0`/`null`、ゲスト戦）も選択不可**（matches 由来のゲスト戦は相手名を `opponentName` で表示） |
| `ErrorBoundary` | `components/ErrorBoundary.jsx` | エラーバウンダリ |

---

## ヘッダーバー（Layout）

| 要素 | 説明 |
|------|------|
| ページタイトル | 現在のパスに応じた画面タイトル |
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
| 組み合わせ作成 | `/pairings` | ALL |
| 選手管理 | `/players` | SUPER_ADMIN |
| 会場管理 | `/venues` | SUPER_ADMIN |
| 練習日程作成 | `/practice/new` | ADMIN+ |
| 参加練習会 | `/settings/organizations` | ALL |
| メンター管理 | `/settings/mentor` | ALL |
| 動画倉庫 | `/videos` | ALL |
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
    ├── videos/
    │   └── VideoLibrary.jsx
    ├── settings/
    │   ├── SystemSettings.jsx
    │   └── OrganizationSettings.jsx
    └── venues/
        ├── VenueList.jsx
        └── VenueForm.jsx
```
