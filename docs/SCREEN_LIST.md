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
| 6 | `/`（認証時） | `Home.jsx` | 次の練習ヒーロー（深緑・常時表示）、参加率の行背景バー、和紙繊維テクスチャ背景 | ALL | ダッシュボード。**脱カード・washi デザイン**（design-md-anti-slop のパイロット。横断的な原則・トークンは正典 `docs/design/design.md`、Home 固有のピクセル値は `docs/features/design-md-anti-slop/design-spec.md`）。次の練習ヒーロー（深緑 `#33503f`・TODAY/NEXT/未登録/予定なしを単一デザインで表示、CTA「対戦確認画面へ」→`/pairings?date=` ／未登録は「参加登録」→`/practice/participation`）＋今月の参加率 TOP3（団体別。カードでなく行の背景バーで順位・名前・参加試合数・率%を表示、自分が圏外なら末尾に自分の行）。**トップバー（`NavigationMenu`）・繰り上げオファーバナー・参加者チップ（`PlayerChip`）は撤去**（プロフィールは設定→プロフィール、繰り上げは通知一覧で確認、参加者は人数「N名」に集約） |

---

## 3. 試合管理（matches）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 7 | `/matches` | `MatchList.jsx` | `MatchViewTabs`（カレンダー/戦績確認タブ帯）・`MatchCalendar`・`FilterBottomSheet` | ALL | **緑トップバー直下のタブ帯（`MatchViewTabs`。下線アクティブ式・等幅）で「カレンダー」（左）／「戦績確認」（右）の2タブ構成。既定＝戦績確認。タブ状態は URL `?view=calendar`（無指定＝戦績確認）、切替は `playerId` を保った `setSearchParams(replace)`（戻るボタンでタブがトグルせず、試合詳細から戻ると居たタブに復帰）。カレンダータブ（`MatchCalendar`）は `playerId` を無視し常に自分（`currentPlayer.id`）の試合を `matchAPI.getByPlayerId(self)` で自前取得して月カレンダー表示（トップバーは自分の名前＋級のみ・期間フィルタ/選手検索ボタンなし・読み取り専用）。日セルにその日の自分の試合数バッジ（抜け番は除外＝試合のみ）、今日＝数字に緑リング／選択日＝角丸ボックス囲い＋薄緑背景・日曜赤/土曜青・前月翌月のはみ出しセルはタップ不可プレースホルダー・グリッド領域の左右スワイプで月移動（左=翌月/右=前月・縦スクロール非干渉）。年月ラベル押下で `YearMonthPicker`（各月セルに当月試合数バッジ・0件非表示）により任意年月へジャンプ（開いている時のラベル再タップで閉じる）＋ラベル直下に当月の総試合数バッジ（月移動で連動）。日選択でその日の試合を試合番号昇順に `N試合目： 勝敗記号+枚数差 相手名(級) お手N` ＋個人メモ（`myPersonalNotes`）で表示（見出し帯＝`M/D(曜) 会場名`・M/Dは十の位0省略・会場は日付に一意で1回・指導試合は「指導」・相手級なし＝括弧なし・お手つき不明＝お手なし）、項目タップで `/matches/:id` へ遷移。試合が無い日は「記録がありません」（登録ボタンなし）。以下は「戦績確認」タブ（従来の `MatchList`）の仕様。** 試合一覧（勝率・段位別統計・フィルタ）。**各行は CSS Grid による 7 列のテーブル風レイアウトで列揃え**され、左から「日付 `M/D`」「対戦相手名」「勝敗（〇N/×N/△N）」「会場名 N試合目」「動画アイコン」「メモアイコン」「お手付き回数 `手N`」の順に並ぶ（会場不明時は `N試合目` のみ表示、長すぎる対戦相手名・会場名は truncate）。**カラム幅は `1.75rem / 5.25rem / 2.5rem / minmax(0,1fr) / 1.5rem / 1.5rem / 2rem`** で、対戦相手名は `text-sm`（`5.25rem` = 84px）固定、会場名列が残り幅を受け取る配分。動画アイコン・メモアイコン・お手付きは非表示条件の行でも列幅を確保し、全行で各列の左端 x 座標が揃う。**動画アイコン列**: `MatchDto.video` が non-null の行のみ `Video` アイコン（lucide-react・`w-3.5 h-3.5`・赤系 `text-[#c0392b]` で当日結果一覧の「動画あり」バッジとトーンを合わせる）を表示し、動画なしの行は不可視プレースホルダーで列幅のみ確保。**行内タップ動線**: 対戦相手名タップで `/matches?playerId=<opponentId>` へ遷移（ゲスト選手は無効）、動画アイコンタップで対戦詳細 `/matches/{id}`（他選手閲覧時は `?playerId=` 付き）へ遷移（**閲覧制限なし＝自分・他選手いずれの一覧でも表示**）、メモアイコンタップで対戦詳細へ遷移（自分閲覧時とメンター閲覧時のみ表示、メモ有/無で濃淡切替）。行全体タップによる詳細遷移は廃止。**読み・一人取りの抜け番行**: その選手が抜け番で読み（`READING`）・一人取り（`SOLO_PICK`）を行った回を、同日の試合から借用した会場名付きで「会場名 N試合目 活動名」（例: `クラ館 1試合目 読み`）として該当試合番号の位置（日付降順→試合番号降順）に差し込み表示（会場不明日は会場省略、活動アイコン付き、タップ無効、自分・他選手とも表示。`GET /api/bye-activities/player/{id}` を利用）。相手前提フィルタ（結果/相手名/級・性別・利き手）有効時は非表示、期間フィルタには連動。統計エリアに「読み n回・一人取り m回」を活動別併記（期間連動・0回非表示、勝敗統計には不算入）。**指導試合は勝敗欄をグレーで「指導」表示（色付け・〇×なし）**。統計エリアの総合の下に「指導 n回・被指導 m回」を併記（値>0のみ・期間/属性連動） |
| 8 | `/matches/new` | `MatchForm.jsx` | 日付＋会場ヘッダー、試合番号タブ（N試合目）、対戦相手プルダウン（当日参加者＋「抜け番」）、未参加から検索モーダル、結果トグル、枚数差ピッカー（末尾「指導」）、お手付き回数セレクト(不明/0〜20)、個人メモ、抜け番活動種別選択 | ALL | 試合結果入力（お手付き・個人メモ含む。抜け番の場合は活動記録）。**design-spec の Anti-Slop 階層デザインへ刷新（locked 2026-06-30）。ヘッダーは「日付(曜)＋会場名」（venueName 無しは日付のみ）。対戦相手は当日参加者（自分を除く）＋「抜け番」のプルダウンから選択、当日未参加の選手は「未参加から検索」（全選手−参加者−自分）で名前検索。確定相手は級を `(A)` で控えめ表示（名前タップ▽で変更）。抜け番はプルダウン「抜け番」選択に一本化（旧「抜け番として記録する」ボタン廃止）。未参加の相手を選んで保存するとサーバ側で当該試合に自動参加登録（WON・冪等）。**`枚数差ピッカー末尾で「指導」を選択可能（登録済み相手のみ）`**。**試合番号タブはタッチデバイスでの左右スワイプでも切替可能（方式B＝スライドイン。左スワイプ=次の試合、右スワイプ=前の試合、端で止まる）。入力途中（未保存）でスワイプ／タブタップした場合は「入力中の内容は破棄されます。移動しますか？」の確認ダイアログを挟む（OKで切替＋スライドイン、キャンセルで据え置き）。編集モードは対象外。新規入力かつタブ2件以上のときのみ、コンテンツ上部に控えめな案内『‹ スワイプで試合を切替 ›』を表示（1試合のみ・編集モードは非表示）**。**メモの下に「取り札・お手付きを記録」の折りたたみ（任意・初期閉じ）＝取り札記録（docs/spec/matches.md 参照）。展開で取り札盤面（敵陣180°回転・畳・決まり字の縦書きチップ・不明プールは陣の間・母数=50−枚数差）と、お手付き回数分の詳細フォーム（種類別）を表示。出札50枚は札ルール（nonceはDB共有）から導出。不明プールは**決まり字順**（1文字目むすめふさほせ→五十音）で並び、配置は**タップ＋ドラッグ&ドロップ**（不明⇔マス／マス⇔マス。盤面は `data-swipe-ignore` で試合番号スワイプと分離）。象限内は札数に応じ横幅を動的吸収。試合保存後に本人の取り札記録を `PUT /api/matches/{id}/card-record` で保存。**新規保存の成功後は `/matches/:id`（試合詳細）へ遷移**（抜け番は `/`）** |
| 9 | `/matches/:id` | `MatchDetail.jsx` | `MatchCommentThread`（メンター⇔メンティー間コメントスレッド。メンター閲覧時はACTIVEメンター関係があれば表示、メンティー本人画面では自分以外の投稿者のコメントが1件以上ある場合のみ表示。未通知コメントがある場合は「LINE通知を送信（N件）」ボタンを表示。コメント入力中はボトムナビを非表示にして誤タップを防止）、`VideoRegisterModal`（試合動画の登録/編集モーダル） | ALL | 試合詳細表示。**1つの統合カード**に「対戦相手名 〇/×/△ 枚数差（絶対値）」を上段、「試合日 第N試合 会場名」を1行で中段、「お手付き」「メモ」を下段に表示（メンター閲覧時はメンティーのお手付き・メモ、メンティー本人閲覧時は自分のお手付き・メモを統合カード内に表示）。表示条件を満たす場合のみコメントスレッドを表示。**統合カードの下に「試合動画」セクションを表示**（`MatchDto.video` の有無で内容を切替）。動画ありの場合は YouTube 埋め込みプレイヤー（`youtube-nocookie.com/embed/{youtubeVideoId}`・16:9 レスポンシブ・全画面可）をインライン再生し、動画タイトル（あれば）と「YouTubeで開く」リンク（`videoUrl`・新規タブ）を表示。**「編集」「削除」ボタンは登録者本人（`video.createdBy` == ログインユーザーID）または ADMIN/SUPER_ADMIN のみ表示**（編集は `VideoRegisterModal` を編集モードで開く、削除は確認ダイアログ「削除されるのは試合との紐付けのみで、YouTube上の動画は残ります」で `DELETE /api/match-videos/{id}` を実行）。動画なしの場合は**両選手が登録済みの試合のみ**「動画を追加」ボタンを表示（全ロール可、`VideoRegisterModal` を新規モードで開く。ゲスト/未登録相手の試合では非表示）。追加/編集/削除の成功時は試合詳細を再取得。削除APIのエラー（403等）はセクション内にメッセージ表示。**本人閲覧時のみ、統合カードの下に取り札（読み取り専用盤面・配置済みのみ・不明プールと操作なし）とお手付き詳細（読み取り専用）を表示**（`GET /api/matches/{id}/card-record` を流用。記録が無ければ非表示、メンター閲覧 `?playerId=` では非取得・非表示） |
| 10 | `/matches/:id/edit` | `MatchForm.jsx` | 試合番号タブ、対戦相手選択、お手付き回数セレクト、個人メモ | ALL | 試合結果編集（お手付き・個人メモの編集含む）。**指導試合の選択/解除も可能（登録済み相手は詳細版APIで保存）**。**取り札・お手付きの詳細記録（折りたたみ）も編集可（保存済みは `GET /api/matches/{id}/card-record` で復元）。不明プールは決まり字順、配置はタップ＋ドラッグ&ドロップ（編集モードの保存後遷移は `/matches` のまま不変）** |
| 11 | `/matches/bulk-input/:sessionId` | `BulkResultInput.jsx` | 組み合わせリスト、枚数差入力（末尾に「指導」）、抜け番活動入力、組み合わせ未作成メッセージ | PLAYER+ | 一括結果入力（抜け番の活動も含む。お手付き・個人メモは含まない）。**ヘッダーは「日付(曜)＋会場名」表示（venueName 無しは日付のみ。design-spec 3画面共通）**。**指導試合は両者黒・中央「指導」表示**。ADMIN/PLAYERは自/所属団体のみ。組み合わせ未作成時はメッセージ表示+全ロールに作成画面への遷移ボタン。**右上の「対戦変更」ボタンで `/pairings?date=YYYY-MM-DD&matchNumber=N&from=%2Fmatches%2Fbulk-input%2F{sessionId}` へ遷移し、組み合わせ作成画面で対戦内容を変更できる（未保存の入力結果がある場合は `window.confirm` で確認。組み合わせ作成画面の戻るボタンは本画面へ戻る）**。**抜け番は試合ごとの組み合わせ対象参加者（抽選あり運用は WON のみ、抽選なし運用は WON+PENDING）からペア済み選手を除外して算出（CANCELLED 等は含めない）。組み合わせ対象に PENDING を含めるかは `PracticeSessionDto.pairingIncludesPending` フラグで判定**。**試合番号タブはタッチデバイスでの左右スワイプでも切替可能（指追従カルーセル。左=次/右=前、端で止まる）。入力結果は全試合分を保持しているためスワイプで移動しても消えず、下部固定の保存バー（保存する N件）はスワイプ対象外。切替時はアクティブタブを自動スクロール。2試合以上のときはカルーセル上部に控えめな案内『‹ スワイプで試合を切替 ›』を表示**。**初期表示試合番号は「入力済み試合番号の最大+1 ＞ 当日かつ会場スケジュールありの時刻ベース ＞ 1試合目」の優先順で初回データ取得時のみ自動選択（`defaultMatchNumber.js`）。保存成功後は `/matches/results/:sessionId?date=<セッション日付>&matchNumber=<現在の試合番号>` へ遷移し、保存元セッションの日付と入力していた試合番号を一覧画面へ引き継ぐ（一覧は `sessionId` でなく日付でセッションを解決するため、`date` 無しだと過去日・未来日の保存で当日に飛んでしまう）** |
| 12 | `/matches/results/:sessionId?` | `MatchResultsView.jsx` | カレンダーピッカー、セッションナビ、抜け番活動表示、`VideoPlayerModal`（試合動画の再生モーダル） | ALL | 試合結果一覧（抜け番の活動もバッジ表示。自分の試合にお手付き・個人メモ表示）。**ヘッダーは「日付(曜)＋会場名」表示（venueName 無しは日付のみ。日付前後移動・カレンダーは維持。design-spec 3画面共通）**。**指導試合は両者黒・中央「指導」表示（緑・〇×なし）**。**抜け番は試合ごとの組み合わせ対象参加者（抽選あり運用は WON のみ、抽選なし運用は WON+PENDING）からペア済み選手を除外して算出（CANCELLED 等は含めない）。組み合わせ対象に PENDING を含めるかは `PracticeSessionDto.pairingIncludesPending` フラグで判定**。**表示対象日が確定したら `GET /api/match-videos?date=YYYY-MM-DD` で当日の動画一覧を取得し、各組（試合番号＋選手ペア。選手ペアは `Math.min`/`Math.max` で正規化して動画側の `player1Id < player2Id` 正規化済みデータと照合）に対応する動画があれば、組のスコア/`vs` 行の下に「動画あり」バッジ（`Video` アイコン＋赤系の小バッジ）を表示。**バッジタップ時の挙動**: その組の結果が入力済み（対応する match レコードあり）なら試合詳細 `/matches/{matchId}` へ遷移、結果未入力（組み合わせのみ）なら `VideoPlayerModal` を開いてインライン再生（選手名タップの `/matches?playerId=...` 遷移と競合しないよう `stopPropagation`）。動画一覧の取得失敗時はバッジを出さず静かに無視（コンソールエラーのみ）。**試合番号タブはタッチデバイスでの左右スワイプでも切替可能（指追従カルーセル。左=次/右=前、端で止まる）。日付ナビ・カレンダー・FAB（自分の結果を入力）はスワイプ対象外で固定。切替時はアクティブタブを自動スクロール。2試合以上のときはカルーセル上部に控えめな案内『‹ スワイプで試合を切替 ›』を表示**。**初期表示試合番号は「URLクエリ `matchNumber`（一括入力からの保存後遷移など、`1〜totalMatches` の範囲内のみ）＞ 当日かつ会場スケジュールありの時刻ベース（終了時刻+15分の猶予）＞ 1試合目」の優先順で初回データ取得時のみ自動選択（`defaultMatchNumber.js`）** |

---

## 3.5 動画倉庫（videos）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 12.5 | `/videos` | `VideoLibrary.jsx` | `VideoPlayerModal`（再生モーダル）、`VideoRegisterModal`（試合選択モードの登録モーダル） | ALL | 動画倉庫（登録済み試合動画の一覧・検索・登録導線）。設定画面メニューの「動画倉庫」から遷移。**一覧**は YouTube サムネイル（`https://i.ytimg.com/vi/{youtubeVideoId}/mqdefault.jpg`・16:9）付きの縦リスト（モバイルファースト。背景・影付きカードではなく、各行を薄い区切り線で区切るフラットスタイル。上部の絞り込みパネルも同様にフラット）で、各行に「試合日（`YYYY/MM/DD`） 第N試合」「対戦カード（player1Name vs player2Name）」「結果（`winnerId`/`scoreDifference` があれば『勝者名〇N』。N=枚数差、対戦一覧（`MatchList`）と同じ表記に統一。枚差未入力時は『勝者名〇』）」を表示（YouTube 動画タイトルは一覧では非表示。再生モーダルでは引き続き表示）。並びは `GET /api/match-videos/search` が返す試合日降順で、**ページングは `totalPages` を用いた「もっと見る」方式**（次ページを末尾に追記読み込み）。**検索・絞り込み**（変更時は page=0 から再検索）は ①選手絞り込み（`playerAPI.getAll` の選手を名前部分一致でフィルタするセレクト → `playerId`）②年月絞り込み（年セレクト＋月セレクト。未選択＝全期間、年のみ選択可 → `year`/`month`）③「自分が関わる動画」トグル（ON で `mine=true`。**選手絞り込みより優先**され、ON 中は選手絞り込みを無効化・クリア）。一覧タップで `VideoPlayerModal` をインライン再生（MatchVideoDto をそのまま渡す。結果入力済みなら「試合詳細を見る」リンクも表示）。**「動画を登録」ボタン**で `VideoRegisterModal` を試合選択モードで開き、登録成功時は一覧を再検索。0件時は空状態（条件なし時「動画がまだ登録されていません」／絞り込み時「条件に合う動画がありません」）を表示 |

---

## 4. 練習管理（practice）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 13 | `/practice` | `PracticeList.jsx` | `PlayerChip`, `MatchParticipantsEditModal`, `AttendanceRegisterModal` | ALL | 練習日程一覧（月別カレンダー表示）。同一日に複数団体のセッションがある場合はカレンダーセルに団体ごとに表示。**会場名の下に試合別ステータスグリッドを表示**（試合番号順に左詰めで `○`／`△`／`×` を 3列固定 grid で配置: `○`＝空きあり（remaining > 2）／`△`＝残わずか（0 < remaining ≤ 2）／`×`＝満員（effectiveCount ≥ capacity）。effectiveCount は WON+PENDING+OFFERED）。**グリッド非表示条件**: 同日複数セッション／フォールバック適用後の有効 capacity が null・0 以下（有効 capacity = session.capacity を優先、null のときのみ venue 既定 capacity にフォールバック。session.capacity = 0 のような明示値は venue 既定値で上書きされずそのまま非表示）／totalMatches null・0 以下・10 以上／`matchCapacityStatuses` が null または不正値混入。同日複数セッションのときはグリッドそのものを描画せず会場名のみ。参加状況背景色（`confirmed` / `waitlisted`）はグリッド記号の可読性確保のため既存より一段薄くしている。**カレンダーテーブル直上・右寄せに「ⓘ 記号の見方」ボタンを常時表示**し、タップで記号（○＝空きあり/△＝残りわずか/×＝満員）とセル背景色（参加確定/キャンセル待ち）を説明する凡例パネルを開閉する（既存 `YearMonthPicker` と同方式のアンカードロップダウン、パネル外タップ・再タップ・×ボタンで閉じる）。凡例は「試合の空き状況（試合ごと）」「あなたの参加状況」の2グループに分離し、配色はカレンダー本体と一致。**初回訪問時のみ自動表示**し、`localStorage` キー `practiceCalendarLegendSeen` で端末単位の既読管理（表示専用・API/DB/マイグレーション変更なし）。`?openToday=true` パラメータ付きアクセス時は当日セッションのポップアップを自動表示（LINEリッチメニューからの導線）。選択セッション詳細部のインライン「出欠登録」ボタン（過去日でない場合のみ表示）は**その日のそのセッション専用の1日分出欠登録画面（`/practice/attendance?sessionId=<id>`、下記 #18-2）へ直接遷移**する（`AttendanceRegisterModal` を経由しない）。右下フローティングボタン（**過去月のときは非表示**）は**「出欠一括登録」**に改名され、従来どおり `AttendanceRegisterModal` を開いて「参加登録」「キャンセル登録」を選択し月まとめ各画面へ遷移する（カレンダー表示中の年月をクエリパラメータで引き継ぐ）。**カレンダー表示月の抽選確定状態（`PlayerParticipationStatusDto.lotteryExecuted`）に応じて当月扱い／来月扱いを判定し、`AttendanceRegisterModal` の「キャンセル登録」ボタン表示を切り替える**（当月扱い＝現在年月、または未来月で抽選確定済みセッションが1つ以上ある月：両ボタン表示／来月扱い＝未来月で抽選確定済みセッションが0個の月：「参加登録」のみ表示）。ADMIN+は隣室チェック対象会場（かでる和室4部屋 + 東🌸）で隣室が拡張可能（`○` 空き ／ `●` 要問合せ）な場合に会場拡張が可能。`○` は「隣室を予約」→「予約完了を報告」→「会場を拡張」の3段階。`●`（当日・直近日でかでるがネット予約締切）および東🌸（Phase 1の会場予約プロキシ対象外）は「隣室を予約」をスキップし初期状態から「予約完了を報告」→「会場を拡張」の2段階（電話等で手動確保した前提）。`×`/`不明` 等では拡張ボタンは非表示。**練習日ポップアップではセッション単位の「再抽選」ボタンは表示しない**（バックエンドAPI `POST /api/lottery/re-execute/{sessionId}` はADMIN+で稼働継続だが、UIからの呼び出し導線は提供しない）。**伝助側で削除が検知された試合番号は、試合別ステータスグリッドの該当マスを灰色×で表示**（`densukeDeletionCandidateMatchNumbers`。検知時点(承認前)から承認後まで一貫して表示、却下時は通常表示に戻る）。**選択セッション詳細の各試合行に「編集」ボタン（ADMIN / SUPER_ADMIN のみ表示）**を持ち、`MatchParticipantsEditModal` を開いて当選/参加確定者の追加・削除に加え、「キャンセル待ち」一覧の各人を **「繰り上げ」ボタン**で当選（WON）へ手動繰り上げできる（`PUT /api/lottery/admin/edit-participants`。繰り上げ時は待ち番号を自動繰り下げ、モーダルは開いたまま最新化） |
| 14 | `/practice/new` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | ADMIN+ | 練習日程作成（ADMIN は自団体、SUPER_ADMIN は団体選択）。**上部ナビバーの月送り右矢印の右側に「Kaderu: {orgCode}」小ボタンを団体ごとに配置**（ADMIN は自団体のみ、SUPER_ADMIN は全団体）。押下すると `POST /api/kaderu-sync/trigger` で GitHub Actions の手動同期 workflow を起動し、PENDING 中は「{orgCode} 同期中… mm:ss」表示で disabled。完了/失敗は押下者本人の LINE 通知（`ADMIN_KADERU_SYNC_COMPLETED` / `ADMIN_KADERU_SYNC_FAILED`）で通知され、ユーザーは画面を手動リロード |
| 15 | `/practice/:id` | `PracticeDetail.jsx` | — | ALL | 練習日程詳細。試合時間割の該当試合番号に、伝助側で削除が検知された場合（承認前・承認後とも）「伝助で削除されました」バッジを表示 |
| 16 | `/practice/:id/edit` | `PracticeForm.jsx` | 会場セレクタ、日付ピッカー | SUPER_ADMIN | 練習日程編集 |
| 17 | `/practice/participation` | `PracticeParticipation.jsx` | 月ナビゲーション、試合番号チェックボックス、抽選ステータスバッジ、締め切り表示、`SaveProgressOverlay` | ALL | 参加登録（抽選済みセッションはステータス表示のみ）。試合番号チェック欄はチェックボックス周囲のラベル領域もタップ対象。締め切り前は「締め切り: ○月○日（あと○日）」を表示（締め切り後・締め切りなし時は非表示）。**カレンダー表示月の抽選確定状態に応じて当月扱い／来月扱いを判定し、既存登録（保存済み）のチェック外し挙動を切り替える**：当月扱い時は既存登録のチェックボックスを一律 disabled（解除不可、キャンセル画面の理由付きキャンセルへ誘導）／来月扱い時は既存登録もチェック外し可能（API上は未登録に戻す＝理由なしキャンセル）。締め切り後は来月扱いでも既存登録ロックを維持（既存仕様）。未登録の試合への追加登録は当月扱い・来月扱いいずれも可能。抽選確定済みセッションはステータス表示固定（現状維持）。一度キャンセルした試合（`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED`）はチェックが外れた未登録状態で表示され、再度チェックして再登録できる。クエリパラメータ `?year=YYYY&month=M` で初期表示月を指定可能（不正値時は現在月にフォールバック）。**保存ボタン押下時は `SaveProgressOverlay` で全画面オーバーレイ（保存中／完了／エラー）を表示し、完了時にユーザーが「カレンダーに戻る」ボタンを押すと `/practice` へ遷移する**（旧仕様の1秒タイマー自動遷移は廃止）。エラー時は「閉じる」で編集中のチェック状態を維持したまま画面に戻る。**伝助側で削除が検知された試合番号（承認前・承認後とも）は、チェックボックスの代わりに灰色×を表示**（操作不可。承認済みはAPIレベルでも登録を拒否） |
| 18 | `/practice/cancel` | `PracticeCancelPage.jsx` | キャンセル専用カレンダー、試合選択チェックボックス、キャンセル理由ラジオボタン、当日12:00以降確認ダイアログ、`SaveProgressOverlay` | ALL | 参加キャンセル（WON または PENDING の参加日を抽選状態に関わらず赤系で統一ハイライトしたカレンダー→試合選択→理由選択→確認ダイアログ）。試合選択ではステータス（当選/申込）を区別するバッジは表示せず、第N試合のみを表示。当日12:00（JST）以降のキャンセル時は「当日キャンセルとなります。補充募集が行われます。」の追加警告を確認ダイアログに表示。**月ナビゲーション（前月/翌月ボタン・YearMonthPicker）は廃止し、対象月はクエリパラメータ `?year=YYYY&month=M` で固定**（クエリ未指定時は現在年月にフォールバック）。`PracticeList` のキャンセル登録ボタン経由（当月扱いの月でのみ表示）で年月を引き継いで遷移する想定。タイトル下に「○年○月」を中央寄せで固定表示する。**キャンセル実行押下時は `SaveProgressOverlay` で全画面オーバーレイ（キャンセル処理中／完了／エラー）を表示し、完了時に「カレンダーに戻る」ボタンで `/practice` へ遷移する**（旧仕様の `alert` 通知は廃止）。エラー時は「閉じる」で選択状態（試合・理由）を維持したまま画面に戻る |
| 18-2 | `/practice/attendance?sessionId=<id>` | `PracticeSessionAttendance.jsx` | 参加トグル行、理由付きキャンセル（理由ラジオ）、当日12:00以降確認ダイアログ、`SaveProgressOverlay` | ALL | **1日分の出欠登録**（カレンダー #13 のセッション詳細ポップアップ「出欠登録」から遷移。対象はポップアップで開いた1セッションのみ）。上部緑バーに「M/D(曜) 会場名」＋団体カラーのドット・団体名。参加/キャンセル/読み取り専用を排他振り分け（純関数 `resolveAttendanceSections`）: **参加する試合**（未参加・抽選前・伝助削除でない試合をチェック、末尾「参加を保存」）／**参加をキャンセル**（WON・PENDING を理由付きで、**当月扱いのみ**）／**読み取り専用**（WAITLISTED・OFFERED 等）。満員（`matchParticipantCounts[n] >= capacity`）でも抽選前はチェック可（無効化しない）。伝助削除承認済みは参加トグルを出さず × 表示。当月扱い＝既存参加はトグルに出さず理由付きキャンセルで取り消し／来月扱い＝全試合トグル（登録済み pre-check、外すと理由なし取消）／抽選確定済み＝参加トグル不可・WON/PENDING のみキャンセル可。SAME_DAY 当日12時以降は参加保存・キャンセルとも追加確認ダイアログ。参加保存は月全体の全置換ペイロード（他日の参加を保持、純関数 `buildMonthParticipationsPayload`）＋`expectedVersion` を送信し 409 は再読込。完了/エラーは `SaveProgressOverlay`（完了「カレンダーに戻る」→ `/practice`）。**フロントのみ**（既存 `registerParticipations` / `cancelMultiple` 流用、新規API・DBなし）。詳細仕様は docs/spec/practice-attendance.md |

補足: `venue-reservation-proxy` はバックエンド Controller / Service 層、フロントエンド API クライアント / venue 判別ユーティリティ、`/practice` の隣室予約導線接続まで実装済み。新規フロントエンドルートは追加されていないが、予約操作時は新規タブで `/api/venue-reservation-proxy/view?token=...` のプロキシ画面を開く。Kaderu は会場サイトの hidden field を引き継いで申込トレイ画面を準備し、会場サイト由来のCSS `@import` / `url(...)` もプロキシ経由に書き換えて表示する。旧 `/api/kaderu/*` 導線は削除済み。

---

## 5. 組み合わせ管理（pairings）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 19 | `/pairings` | `PairingGenerator.jsx` | 参加者リスト、待機者リスト（D&D / タップ選択対応）、対戦履歴（当日他試合で組まれたペアは `⚠今日` 赤字警告）、新規作成ドロップゾーン、DraggablePlayerChip、DroppableSlot、結果入力済ロック表示・リセットボタン、手動ロックボタン（鍵アイコンのみ）/解除ボタン、再シャッフルボタン（動的文言・確認ダイアログ）、使い方ヘルプ（ⓘ・`PairingHelp`。ヘッダ右に配置）、LINE送信用テキスト生成導線（画面上部の折りたたみトグル格納。全試合/{N}試合目セグメント＋生成ボタン） | PLAYER+ | 組み合わせ作成（ドラッグ&ドロップ or タップ選択モード）。**UI は脱カードデザイン**（白カード/塗りバッジをやめ hairline・余白・文字階層で構成。ロック/結果/キャンセルはフラット色ラベル、選手チップは従来のピル形式を維持）。**ヘッダに `日付＋会場名`（`PracticeSessionDto.venueName`、無ければ日付のみ）を表示し、この画面での日付変更（日付入力＋「今日」ボタン）は廃止**（日付は `?date=` クエリ／当日デフォルトから受け取る）。**試合番号タブは同一フットプリントの数字タブを左寄せ表示（≤7 は1行に収まり 8以上は横スクロール）、アクティブは実測追従で滑る cream ハイライト（`#ebe4d8`）で表現し下辺で cream 連結パネルに接続（「N試合目」はパネル冒頭の静的見出しで保持）、参加者リストは折りたたみ廃止で常時展開。** **参加者一覧は組み合わせ未作成時（`pairings.length === 0`）かつ非編集モードのみ表示し（`shouldShowParticipantSection`）、既存の組み合わせがある試合は結果入力済み・未入力に関わらず組み合わせ表示に統一する（結果未入力の試合と見た目を一貫させる）。** **主アクション「対戦編集」ボタンはパネル冒頭の「N試合目」行の右端に**常設**する（pairing-always-visible-edit-button）。パネル内で常に描画される行はここだけで、参加者ヘッダ（組0件のみ）と組み合わせヘッダ（組1件以上）は相互排他なため、そこに置くと組の有無でボタン位置が動く。位置・ラベル・見た目は状態によらず不変で、挙動だけを `resolvePairingEditAction`（pairingDisplayLogic）で切り替える: 組が1件以上あれば `'edit-existing'`＝閲覧モードから編集モードへ切り替えるのみ（**auto-match を呼ばない。呼ぶと保存済みの組が黙って組み直されて失われる**）／組0件・参加者ありなら `'auto-match'`＝従来の自動マッチング／組0件・参加者0名なら `'empty-edit'`＝空の編集モードへ入り待機中セクションの「選手追加」に到達できるようにする。押せない状態（`isReadOnly`、または既に編集モード）は非表示でなく `disabled` にして高さを保つ（`isPairingEditDisabled`）。編集エリアの描画可否は `shouldShowEditingArea`＝`pairings.length > 0 || isEditing`。**この画面から「更新」「追加」（参加者ヘッダ）・組み合わせヘッダの「編集」・右下の「対戦編集」は廃止し、常設ボタン1つに統合した**（選手の追加は編集モードの待機中セクション「選手追加」で行う）。 **組み合わせが1件以上ある編集可能状態（閲覧モード/閲覧専用でない）では、組み合わせヘッダに再シャッフルボタンを常設する（pairing-reshuffle-except-locked）。文言はロック済みの組（`hasResult || locked`）の有無で「ロックされた組以外をシャッフル」/「再シャッフル」に動的切替、実行前に確認ダイアログ。ロック以外の選手＋待機者を再最適化し、未保存の手動ロックも尊重する（`POST /auto-match` の `lockedPairs` にロック組を同梱）。** ADMIN/PLAYERは自/所属団体のみ。削除系のみ ADMIN+ 専用。タップ選択はスマホ向け代替操作で、選手をタップで選択→別カード/空き枠/待機/新規ペアゾーンをタップで配置。結果入力済みペアリングはロック表示（グレーアウト+「結果入力済」バッジ）、個別リセット可能。結果未入力の組も鍵アイコンボタンで手動ロック可能（編集モードは全ロール「解除」ボタンのみ、閲覧/閲覧専用モードは🔒「ロック」バッジを表示。バッジと解除ボタンは `shouldShowManualLockBadge` で排他）。既存組み合わせを持つ編集状態の「対戦をリセット」ボタンは確認ダイアログ後にロック済み以外を削除する（挙動不変）。ロック/解除はローカル状態のトグルで、「確定して保存」時に `createBatch` の `locked` で永続化する（即時ロックAPIは廃止、`PATCH /api/match-pairings/{id}/lock`・`/unlock` は残置だが未使用）。手動ロック組は自動マッチング・回戦削除から保護（保護判定 `hasResult OR locked`）、一括保存は結果入力済み（`hasResult`）のみ保護し手動ロック組は削除→再作成で `locked` を再現。二重ブッキングはUI上構造的に発生しない。ヘッダ右の「使い方」ボタンからⓘドロップダウン（4セクション・初回自動表示、`PairingHelp`）。**組み合わせ対象は団体の運用設定により切り替わる: 抽選あり運用 (MONTHLY+締め切りあり) は WON のみ / 抽選なし運用 (SAME_DAY または MONTHLY+締め切りなし) は WON+PENDING（バックエンドの `PracticeSessionDto.pairingIncludesPending` で判定）**。LINE送信用テキスト生成導線は画面上部の折りたたみトグル（「LINE送信用テキスト」見出し・初期は畳む）に格納し、展開時に「全試合 / {N}試合目」セグメント＋生成ボタンを表示する。全試合が揃えば「全試合」、選択中の試合が完成していれば「{N}試合目」を選べる（タブ切替で無効側は有効側へ自動フォールバック）。**トグル見出しは組み合わせの有無に関わらず常時表示する**（pairing-always-visible-edit-button）。以前は生成可能な対象が1つも無いとセクションごと消えており、その分だけ直下の試合番号タブが上下していた。生成可能な対象が無いときは展開しても両セグメントが `disabled` で、生成ボタンの代わりに「対戦組み合わせが未作成のため生成できません」の注記を出す。生成ボタンは対象に応じた URL（全試合 `?date=...` / 単一試合 `?date=...&matchNumber=N`）で札ルール一覧（summary）へ遷移する。**`from` クエリパラメータ対応**: `?from=<パス>` が指定された場合（例: 結果入力画面「対戦変更」ボタンからの遷移）、戻るボタンの遷移先が動的に変更される（未指定時は従来どおり `/settings`）。**対戦相手キャンセル反映（pairing-cancelled-opponent）**: 作成後に参加者がその試合をキャンセルすると、閲覧モードは相手を取消線＋グレー名＋右端「キャンセル」タグで表示（両方キャンセルは行ごと非表示）、編集モードは当該スロットを「空き」にする。read-time・非破壊（取得APIが各組DTOに `player1Cancelled`/`player2Cancelled` を付与、試合単位判定、`match_pairings` は不変）。**未組み合わせ選手チップ（pairing-view-unpaired-chips）**: 一部だけ組がある試合を閲覧モード（`isViewMode`）または読み取り専用（`isReadOnly`）で見ているとき、まだどの組にも入っていない参加者を組み合わせ一覧の直後に「待機中 N名」＋読み取り専用の名前チップ（参加者一覧と同じ `PlayerChip`・`sortPlayersByRank` 順、活動プルダウン/選手追加/D&D なし）で表示する（表示可否 `shouldShowViewModeUnpairedSection` ＝編集モード待機中ガードの厳密な補集合、`pairings.length>0` で参加者一覧との二重表示を防止）|
| 20 | `/pairings/summary` | `PairingSummary.jsx` | カレンダーピッカー、試合番号タブ、全試合/単一試合の表示切替（URL `matchNumber`） | PLAYER+ | 組み合わせ一覧表示。札ルールは `(date, 再生成カウンタ nonce)` をシードにした決定論的生成（`hashSeed`→`mulberry32`→seeded Fisher-Yates）で導出し、同じ日・同じ nonce ならいつ・どの端末でも同じ札ルールになる。札ルール配列は保存せず、保存するのは日付ごとの `nonce`（既定0、キー `karuta-tracker:card-nonce:<YYYY-MM-DD>`）のみ（既定状態は保存不要で全端末一致）。試合数を増やしても先頭の試合の札ルールは安定。画面ロード時に旧形式の札ルール配列キー（`karuta-tracker:card-rules:<date>`）は全削除し、`nonce` キーは今日以外を削除する。「札を再生成」は `window.confirm` 確認後、当日の `nonce` を +1 して再計算。**URL `?matchNumber=N`（1..totalMatches）指定で単一試合モード**: 日付見出し＋対象 `N試合目` のブロック（札ルール・読手・ペア）のみを全試合テキストの該当ブロックと完全一致で表示（数値でない/範囲外の無効値は全試合モードへフォールバック、ペアが空でも見出し＋札ルールは表示）。「札を再生成」は当日かつ全試合モードのみ表示（単一試合モード・過去日は非表示＝決定論の既定札ルールを表示）。各試合に「読み」設定の抜け番選手がいれば `{N}試合目` 行直後に `【読手：○○】`（同一試合に複数いる場合は「、」区切り）を出力する（`byeActivityAPI.getByDate(date)` で取得、取得失敗時は読手なしで継続） |

---

## 6. 選手管理（players）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 21 | `/players` | `PlayerList.jsx` | 検索、段位ソート、ロールバッジ、選択チェックボックス・全選択、団体フィルタ（すべて/団体別/無所属、招待先セレクタと統合）、一括編集導線、招待リンク生成（具体的団体選択時のみ有効） | ADMIN+ | 選手一覧（ADMINは初期フィルタが自管轄団体） |
| 22 | `/players/new` | `PlayerEdit.jsx` | — | SUPER_ADMIN | 選手新規作成 |
| 23 | `/players/:id` | `PlayerDetail.jsx` | — | SUPER_ADMIN | 選手詳細 |
| 24 | `/players/:id/edit` | `PlayerEdit.jsx` | ロールがADMINの場合に管理団体ドロップダウン表示（SUPER_ADMIN専用） | SUPER_ADMIN | 選手編集 |
| 40 | `/players/bulk-edit` | `PlayerBulkEdit.jsx` | 全員一括設定（性別/級＋「全員をE級に」/かるた会/北大・わすら追加）、選手ごとの編集（級↔段位連動・A級のみ段位手動・練習会追加/取消）、確認ダイアログ | ADMIN+ | 選手の一括編集（選手一覧で選択した選手をまとめて編集。直接遷移時は一覧へリダイレクト） |

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
|  | `/admin/line/broadcast` | `pages/line/CardDivisionBroadcastAdmin.jsx` | 配信グループ一覧テーブル（有効トグル・想定受信数編集）、配信グループ登録フォーム、bot割当/解除（チャネルID入力）、稼働状況（bot別残枠バー・次配信bot強調・枯渇アラート）、配信ログ一覧、予約状況一覧（状態バッジ・要確認アラート・再試行ボタン）、bot群セットアップ手順の案内文 | ADMIN+ | 全体LINE配信管理。札分けの全体LINEグループへの一斉配信で使うbotローテーションの管理画面。配信グループの登録・有効化、bot（LINEチャネル）の割当/解除、当月の送信残枠・次に配信するbotの確認、枯渇時のアラート表示、配信ログ（成功/失敗/スキップ）の確認を行う。**予約状況**セクション（line-chat-reserve-broadcast）: LINEチャット予約送信の予約一覧を状態バッジ（予約待ち/処理中/予約済み/失敗/要確認/取消処理中/取消済み/dry-run成功）付きで表示し、MANUAL_REVIEW_REQUIRED があれば要確認アラートを出す。FAILED かつ送信予定時刻まで安全マージン（30分）がある行のみ再試行ボタンが活性化する |

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

## 8.10 札分け確認（card division）

| # | パス | ページコンポーネント | 主要子コンポーネント | 権限 | 説明 |
|---|------|---------------------|---------------------|------|------|
| 41 | `/settings/card-division` | `CardDivision.jsx` | 所属団体ごとの札分けブロック（「今日」「明日」の2日分。各日 readonly textarea + 日別コピーボタン、明日は「暫定」注記、LINE購読トグル、LINE未連携時の通知設定への導線） | ALL | 札分け（各試合の出札ルール）を**今日＋明日の2日分**テキストで確認・コピーする画面。所属団体ごとに1ブロックを表示（複数所属時は団体名見出しあり）。各ブロックに「今日 M/D」「明日 M/D」の枠を常に表示し、その日にセッションが無ければ「今日は練習がありません」「明日は練習がありません」の空表示。明日は確定前要素（試合数・会場等）で後日変わりうるため「暫定（確定前に変わる場合あり）」注記を添える。明日の日付は BE がJSTで解決した当日（レスポンスの date）を基準に+1して取得（フロントの `new Date()` に依存しない）。テキストの閲覧・日別コピーは購読トグルの状態に依存せず常時可能。LINE購読トグル（既定OFF・通知は当日分のみ）は団体ごとに個別に操作でき、LINE未連携の場合はトグル付近に通知設定画面への導線を表示 |

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
| 37 | `/admin/densuke` | `DensukeManagement.jsx` | 月ナビゲーション、団体別ブロック（URL入力・同期ボタン・書き込み状況・**削除候補一覧（承認/却下ボタン）**・同期結果・未登録者チェックリスト・**伝助ページ作成ボタン**・**作り直すボタン**・**テンプレート編集ボタン**）、`DensukePageCreateModal`、`DensukeTemplateModal` | ADMIN+ | 団体別の伝助URL管理・手動同期実行・書き込み状況・未登録者確認・一括登録・**削除候補の確認・承認/却下**（伝助側で削除された試合を検知して一覧表示。承認すると該当試合の出欠エントリを削除、却下するとデータは変更せず解消。団体別・月に関わらず全件表示）・**伝助ページ自動作成**（アプリの練習日データから densuke.biz にページを新規発行。当月+未来2ヶ月まで作成可能、既に URL 登録済みの月は作成ボタン非表示）・**作り直す**（既に URL 登録済みの月に表示。確認ダイアログ→アプリ側の `densuke_urls` レコード削除→作成モーダル自動オープンの流れ。旧 densuke.biz ページ自体は削除できないためそのまま残るが、アプリからは参照されなくなる。削除成功時は同期結果・未登録者選択・書き込み状況もリセット）・**テンプレート編集**（団体ごとのタイトル・説明・連絡先メアドのデフォルト値、プレースホルダー `{year}` / `{month}` / `{organization_name}` 対応）。ADMINは自団体のみ表示、SUPER_ADMINは全団体を並べて表示。各団体ブロックに団体カラーのアクセント付き |

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

> **注記（design-md-anti-slop）**: Home のトップバー（`NavigationMenu`）は撤去され、Home 画面上にこのメニュー UI は表示されない。下表の各遷移先は設定グリッド（`/settings`）等から到達する（プロフィールは設定→プロフィール）。表自体は遷移先の参照として残置。

| メニュー項目 | 遷移先 | 権限 |
|------------|--------|------|
| プロフィール | `/profile` | ALL |
| 組み合わせ作成 | `/pairings` | ALL |
| 選手管理 | `/players` | ADMIN+ |
| 会場管理 | `/venues` | SUPER_ADMIN |
| 練習日程作成 | `/practice/new` | ADMIN+ |
| 参加練習会 | `/settings/organizations` | ALL |
| メンター管理 | `/settings/mentor` | ALL |
| 動画倉庫 | `/videos` | ALL |
| 通知設定 | `/settings/notifications` | ALL |
| 札分け確認 | `/settings/card-division` | ALL |
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
│   ├── MatchCarousel.jsx               # 試合番号の指追従スワイプカルーセル（結果一覧・一括入力で共用）
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
    │   ├── MatchViewTabs.jsx
    │   ├── MatchCalendar.jsx
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
    │   ├── PairingHelp.jsx
    │   └── PairingSummary.jsx
    ├── players/
    │   ├── PlayerList.jsx
    │   ├── PlayerBulkEdit.jsx
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

---

## 公開ページ

パス・コンポーネントは上記「1. 公開画面（認証不要）」表と同一のため再掲しない。差分（既存表に無い事実）のみ記載する。

- `/register/:token`: 招待トークン付きURLでアカウント作成時のデフォルトロールは **PLAYER**。

⚠要確認（分割前の旧DESIGN §5.1 の記載が上記表・旧SPECIFICATION §5.1 と食い違う。「旧」は2026-07-10のドメイン分割前の記述で、原文は git 履歴の docs/SPECIFICATION.md・docs/DESIGN.md を参照）:
- 旧DESIGN §5.1 は「ランディング」のパスを `/landing` としているが、既存表・旧SPECIFICATION §5.1 は `/`（未認証時）→ `Landing.jsx`。
- 旧DESIGN §5.1 は「ユーザー登録」のパスを `/register`（トークンなし）としているが、既存表・旧SPECIFICATION §5.1 は `/register/:token`。
- 旧DESIGN §5.1 は「プライバシーポリシー」のパスを `/privacy-policy` としているが、既存表・旧SPECIFICATION §5.1 は `/privacy`。
- 旧DESIGN §5.1 は「利用規約」のパスを `/terms-of-service` としているが、既存表・旧SPECIFICATION §5.1 は `/terms`。

---

## 認証必須ページ

全ページは `PrivateRoute` で保護され、未ログイン時は `/login` にリダイレクトする（`kyuRank` 未設定時の `/profile/edit?setup=true` リダイレクトは上記「認証・ルートガード構成」表の `PrivateRoute` 行を参照）。

パス・コンポーネント・権限は各機能別表（2〜9.5節）と重複するため再掲しない。既存表との間で権限・パス表記に相違がある箇所を ⚠要確認 として記録する。

⚠要確認（既存表と旧SPECIFICATION §5.2 / 旧DESIGN §5.1 の記載の相違）:
- `/players`（選手一覧）: 既存表・ハンバーガーメニュー表は **ADMIN+**（`PlayerList.jsx`、ADMINは初期フィルタが自管轄団体）。旧SPECIFICATION §5.2 は **SUPER_ADMIN**、旧DESIGN §5.1 は **全員**。
- `/players/:id`（選手詳細）: 既存表は **SUPER_ADMIN**（`PlayerDetail.jsx`）。旧SPECIFICATION §5.2・旧DESIGN §5.1 はいずれも **ALL/全員**。
- `/venues/new`・会場編集: 既存表は **SUPER_ADMIN**（会場編集パスは `/venues/edit/:id`）。旧DESIGN §5.1 は権限を **全員** とし、パスも `/venues/:id/edit` と表記している。
- 通知設定のパス: 既存表は `/settings/notifications`。旧SPECIFICATION §5.2・旧DESIGN §5.1 はいずれも `/settings/line`（旧パスの可能性）。
- キャンセル待ち状況のパス: 既存表は `/lottery/waitlist`。旧DESIGN §5.1 は `/lottery/waitlist-status`。
- 組み合わせ作成のパス: 既存表・旧SPECIFICATION §5.2 は `/pairings`。旧DESIGN §5.2（画面遷移と導線）のホーム導線記述は `/pairings/generate`。
- `/matches` 一覧の列レイアウト説明: 旧SPECIFICATION §5.2 は 6 列グリッド（`grid-cols-[2rem_6.125rem_2.5rem_minmax(0,1fr)_1.5rem_2rem]`、動画アイコン列なし）と記載しているが、既存表（3. 試合管理）は動画アイコン列を含む 7 列グリッド（`1.75rem/5.25rem/2.5rem/minmax(0,1fr)/1.5rem/1.5rem/2rem`）で、既存表の方が最新。

---

## ナビゲーション構造

ボトムナビゲーション本体の構成（アイコン・ラベル・遷移先）は上記「下部ナビゲーション（Layout）」表、ハンバーガーメニューの項目一覧は上記「ハンバーガーメニュー（Home画面）」表を参照（重複のため再掲しない）。

### ボトムナビゲーション表示制御

- `BottomNavContext`（`isVisible` state）で表示/非表示を管理
- デフォルトは常に表示（`isVisible: true`）
- コメント入力欄（`MatchCommentThread` 内の textarea）にフォーカス時、ボトムナビをスライドダウンで非表示にする
- フォーカスが外れると100ms後にスライドアップで再表示する（textarea間のフォーカス移動によるチラつき防止のため遅延あり）
- `MatchCommentThread` がアンマウントされた場合は自動的に表示状態にリセット
- アニメーション: `transition-transform duration-300`（`translate-y-0` ⇔ `translate-y-full`）

⚠要確認:
- ボトムナビゲーションのラベル表記: 既存表は絵文字＋英語ラベル（🏠 Home / ➕ Add / ⚔️ Match / 📅 Schedule / 📊 Record）。旧SPECIFICATION §5.3 は lucide アイコン名＋日本語ラベル（Home / PlusCircle 結果入力 / ClipboardList 対戦結果 / Calendar スケジュール / List 対戦履歴）。パス・並び順は一致するが表記方式が異なり、どちらが現行UIの実態かは要確認。
- ハンバーガーメニュー: 旧SPECIFICATION §5.3 のメニュー一覧には、既存表にある「参加練習会」「メンター管理」「動画倉庫」「システム設定」の4項目が記載されていない（SPECIFICATION側の記載漏れ・更新漏れの可能性）。

---

## 画面遷移と導線

#### ホーム画面からの導線

ホーム画面（`/`）には以下のクイックアクション:
- **試合記録**: `/matches/new` へ遷移（試合登録）
- **練習記録**: `/practice` へ遷移（カレンダー画面）
- **組み合わせ**: `/pairings/generate` へ遷移（対戦組み合わせ生成）⚠要確認: 既存表・旧SPECIFICATION §5.2 では組み合わせ作成のパスは `/pairings`

カレンダー画面（`/practice`）を練習関連機能の中心ハブとして設計。
- カレンダー画面から以下に遷移可能:
  - 出欠一括登録モーダル → 月まとめ参加登録画面 / 月まとめキャンセル画面（いずれも年月を引き継ぐ）
  - セッション詳細ポップアップ「出欠登録」→ 1日分の出欠登録画面（`/practice/attendance`）
  - 練習日編集（SUPER_ADMINのみ）

#### 練習関連の導線フロー
```
ホーム画面（/）
  ↓ 「練習記録」クリック
カレンダー画面（/practice）
  ├─ 「出欠一括登録」ボタン（右下フローティング、過去月のときは非表示）
  │     ↓
  │   出欠登録モーダル（AttendanceRegisterModal）
  │     ├─ 「参加登録」 → /practice/participation?year=YYYY&month=M
  │     │                   ↓ 保存 → SaveProgressOverlay（保存中／完了／エラー）
  │     │                   └→ 「カレンダーに戻る」ボタン押下で /practice へ遷移（データ再取得）
  │     ├─ 「キャンセル登録」 → /practice/cancel?year=YYYY&month=M
  │     │   （当月扱いの月でのみ表示。来月扱いの月では非表示）
  │     │                   ↓ キャンセル実行 → SaveProgressOverlay（キャンセル処理中／完了／エラー）
  │     │                   └→ 「カレンダーに戻る」ボタン押下で /practice へ遷移
  │     └─ 「閉じる」 → モーダルを閉じる（遷移なし）
  │
  ├─ 日付クリック → 選択セッション詳細モーダル表示
  │   ├─ 「出欠登録」ボタン（過去日でない場合のみ表示）
  │   │     → /practice/attendance?sessionId=<id>（1日分の参加＋理由付きキャンセル。AttendanceRegisterModal を経由しない）
  │   │           ↓ 参加保存 / キャンセル実行 → SaveProgressOverlay
  │   │           └→ 「カレンダーに戻る」ボタン押下で /practice へ遷移
  │   └─ 「試合結果」ボタン（過去日のみ表示） → /matches/results/<sessionId>
  │
  └─ 「編集」ボタン（SUPER_ADMINのみ） → 練習日編集画面
```

---

## 共通レイアウト

### 共通トップバー（PageHeader コンポーネント）

独自トップバー（NavigationBar 等）を持たない画面では、共通コンポーネント `PageHeader`（`karuta-tracker-ui/src/components/PageHeader.jsx`）が画面最上部に固定表示される。

**責務**:
- `Layout.jsx` のベースナビバー（`bg-[#4a6b5a]` の空の緑バー、`z-40`、ローディング中のフォールバック）の上に `z-50` で重ねて表示し、各画面に「タイトル」と「明示的な戻る導線」を提供する
- 該当画面の本文先頭にあった H1（および付随アイコン）はトップバーに集約され、本文側からは削除される

**Props**:
| 名前 | 型 | 必須 | 説明 |
|---|---|---|---|
| `title` | `string` | ○ | トップバー中央に表示するタイトル（`<h1>` 要素として描画） |
| `backTo` | `string` | ○ | 戻るボタン押下時の遷移先パス（`useNavigate()(backTo)` で明示遷移）|
| `rightActions` | `ReactNode` | × | トップバー右端に配置する追加要素（例: 「すべて削除」「システム設定」） |

**戻り先のグルーピング**（ディープリンク・リロード後も一貫した戻り先を保証するため、`navigate(-1)` ではなく明示的な `backTo` を採用）:
- 設定グリッドから入る画面 → `/settings`（プロフィール、参加練習会、通知設定、札分け確認、メンター管理、カレンダー購読、組み合わせ作成、会場管理、抽選管理、LINEチャネル管理、LINE通知スケジュール、全体LINE配信管理）
- 抽選管理から入る画面 → `/admin/lottery`（システム設定）。設定グリッドに「システム設定」のメニュー項目はなく、`LotteryManagement` 画面右上の `rightActions` ボタンから到達する画面のため、戻る先は抽選管理に揃える
- リスト → 詳細・編集 → 親リスト（試合詳細、練習詳細、選手新規/編集、会場新規/編集、札ルール一覧）
- ホーム導線 → `/`（通知一覧、抽選結果、キャンセル待ち状況、繰り上げ参加のご連絡）

**Z-index 重なり順**:
- `PageHeader`（`z-50`、固定トップバー） > `Layout` ベースナビバー（`z-40`、空のフォールバック） > 本文

**動的タイトル / 動的 backTo**:
- 同じコンポーネントが新規/編集で使い回されるページ（`PlayerEdit`, `VenueForm`）は `id` / `isEditMode` から動的に切り替える
- `PairingGenerator` は `from` クエリパラメータが指定された場合（例: 結果入力画面「対戦変更」ボタンからの遷移）、`backTo={searchParams.get('from') || '/settings'}` で動的に戻り先を設定する

**ローディング・エラー画面**:
- `if (loading) return <LoadingScreen />` のような早期 return でも `<><PageHeader ... /><LoadingScreen /></>` で包み、空のベースナビバーが見えないようにする

**詳細仕様・要件**: `docs/features/subpage-topbar-title/requirements.md`
