---
status: locked
round: 3
chosen_direction: 脱カード再設計（Anti-Slop）— 白い浮きカード/ピル/多色枠をやめ hairline・余白・文字階層で組む。全状態に適用
design_required: true
change_type: ui-refactor （主体は視覚。純フロント。ただしユーザー合意の小さなインタラクション削減3点を含む → 下記「意図的な挙動差分」）
target_screen: /pairings （PairingGenerator）対戦組み合わせ作成・編集画面
design_project: Match Tracker Design System (1f747846-a832-423a-90d2-fdc9d0b5e59b)
---

# 対戦組み合わせ画面（PairingGenerator）ビジュアルデザイン

> 状態: **locked**（脱カード方向で全状態確定・A/B/C/D の4カードが正）。純UI改修のため本 spec が実装契約。次は `/implement pairings-ui-change`。
> 関連ロジック仕様: `docs/spec/matching.md`（振る舞いはここが正典。本ファイルで再記述しない）

## 対象画面

- パス: `/pairings`、コンポーネント: `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
- 実デザイントークン（抽出済み）: 緑 `#4a6b5a`（ヘッダ・ボトムナビ・二次ボタン）/ 紺 `#1A3654`（主要アクション「自動組み合わせ」「確定して保存」）/ クリーム背景 `#f2ede6` / 濃緑 `#2d4a3e`（LINE生成）/ 緑淡 `#e5ebe7`・クリーム淡 `#f9f6f2` / 緑グレー枠 `#a5b4aa`・`#c5cec8`。※Tailwind config の `primary`（赤系）はこの画面では未使用。
- 級バッジ枠色: A級=red-200 / B級=blue-200 / C級=orange-200 / D級=yellow-200 / E級=lime-200。
- 状態バッジ: 結果入力済=blue-100/700 / 手動ロック=amber-100/700 / キャンセル=gray-100/600。直近対戦=初(緑)/⚠今日(赤太字)/日付(グレー)。

## 現状スナップショット（Claude Design 上のカード）

グループ「対戦組み合わせ画面 (現状)」— 確定モック4枚（全状態カバー）:
- `preview/pairings-current-a.html` — **A 新規作成前**（参加者一覧＋主アクション）
- `preview/pairings-current-b.html` — **B 編集モード**（生成/手入れ・ロック各種・待機者・保存）
- `preview/pairings-current-c.html` — **C 閲覧モード**（既存組み合わせ・キャンセル表示・結果入力済）
- `preview/pairings-current-d.html` — **D 補助状態**（エラー/通知/未保存バナー・選手追加モーダル・ローディング）

※旧アセット `preview/pairing-cancelled-opponent-a.html`（グループ「対戦組み合わせ (現行画面)」）は**旧カード版の忠実再現**で、本 design-spec の脱カード版（C）が**上位・正**。旧アセットは履歴として残置（削除は要ユーザー承認）。実装は C を参照すること。

## 採用方向：脱カード再設計（Anti-Slop）— round 1

ユーザーが Claude Design 上で **A（新規作成前）を直接リデザイン**。方向性が確定した：

- **箱で囲わない**：白い浮きカード・ピル・級ごとの多色枠を廃し、cream 面 `#f2ede6` の上に hairline `#e7e0d4` と余白・文字階層で構成。
- **日付をヘッダへ**：緑ヘッダの h1 に `7/12 中央体育館`（日付＋会場）。独立した日付カードを廃止。
- **試合番号は下線タブ**：アクティブは大きい紺文字＋連結パネル（`#ebe4d8`）。完了は緑チェック、未保存はオレンジ文字。
- **LINE生成はトグル副次**：小見出しをタップで展開、生成は枠線ボタン（塗り緑をやめる）。
- **主アクションは紺1つ**：右寄せの紺ボタン（現状は文言「対戦編集」。※要件確認事項、下記宿題）。
- **muted 系トークン**：ラベル `#8a8275`、副次ボタン文字 `#5b5446`／`#33463c`、highlight 面 `#fffdf9`。

### B（編集モード）の脱カード適用 — round 2
- **白カード・行の塗り・塗りバッジを廃止**。cream パネル `#ebe4d8`（試合番号タブに連結）の上に hairline `#ddd3c2` 区切りでペアを縦積み。
- **選手チップは A と同じ「元のピル形式」を流用**（`.pg-name`。ロック済み行のみ muted プレーン名 `.pg-pname`）。
- **ロック済み行**：塗りバッジ（青/オレンジのpill）をやめ、名前を muted＋アイコン付き色ラベル（結果入力済=blue `#1d4ed8` / ロック=amber `#b45309`）＋テキストアクション（リセット/解除）。
- **直近履歴**（初/⚠今日/日付）は各行の右端メタに集約。⚠今日 は赤太字を維持。
- **主アクションは「確定して保存」紺のみ**。全削除は muted 赤のテキストボタン。日付・会場はヘッダへ。
- 冗長だった見出し「第N試合の組み合わせ」は撤去（タブの「N試合目」がその役割を担う）。「組み合わせ N組」の小見出しに置換。

### C（閲覧モード）の脱カード適用 — round 3
- 既存の組み合わせを開いた読み取り専用状態。旧アセット `pairing-cancelled-opponent-a.html`（カード版）を脱カード言語で作り直したものが**正**。
- ペアは cream パネル上に hairline 区切り。**名前はプレーン中央寄せ `#374151`**（現行踏襲・チップ化しない。ドラッグ不可のため）。
- セクションバー右に **「編集」ボタン**（ghost・ペン。押下で B 編集モードへ）。
- **対戦相手キャンセル**：キャンセルした選手名は**取消線＋グレー `#9ca3af`**、右端に「キャンセル」ラベル（Ban アイコン・muted `#6b7280`、塗りpillをやめフラット化）。**タグは常に行の右端**（`vs` の揃いを崩さない構造 = 結果入力済行と同一）。**両方キャンセルの組は行ごと非表示**。
- **結果入力済**：muted 名＋青ラベル（`#1d4ed8`・右端）。閲覧モードではリセット/解除ボタンは出さない（現行どおり）。

### D（補助状態）の脱カード適用 — round 3
- **機能バナーは意味色を保持**しつつ薄い tint＋hairline に：エラー=赤 `#b3403a`/`#fdf0ee`/`#f2c9c2`、通知・未保存=琥珀 `#8a5a12`/`#fbf3df`/`#ecd9a8`。未保存警告は中央寄せ。
- **選手追加モーダル**：warm-white surface `#fffdf9`＋hairline `#e7e0d4`＋角丸14px＋暗幕 `rgba(26,39,68,.42)`。タイトル ink、検索欄は hairline、追加=緑 `#4a6b5a`・キャンセル=ghost。
- **ローディング**：スピナーを緑アクセント（`border-bottom-color:#4a6b5a`）にトークン化。文言・挙動は現行どおり。

## 状態カバレッジ一覧（実装が全状態を脱カードで揃えるためのチェックリスト）

| # | 状態 | 実装の分岐 | モック | 扱い |
|---|---|---|---|---|
| 1 | 新規作成前（未組） | `shouldShowParticipantSection` / `pairings.length===0` | A | 脱カード |
| 2 | 自動生成後・編集モード | `pairings>0 && !isViewMode && !isReadOnly` | B | 脱カード |
| 3 | 閲覧モード（既存組み合わせ） | `isViewMode` | C | 脱カード |
| 4 | 結果入力済ロック行 | `showsResultLockedRow` / `hasResult` | B・C | 脱カード（muted名＋青ラベル） |
| 5 | 手動ロック行 | `locked && !hasResult` | B | 脱カード（muted名＋橙ラベル＋解除） |
| 6 | 対戦相手キャンセル行 | `player1Cancelled/player2Cancelled`（read-time） | C | 脱カード（取消線＋右端タグ／両方は非表示） |
| 7 | 待機者＋活動プルダウン | `waitingPlayers` | B | 脱カード |
| 8 | 新規ペア作成ドロップゾーン | 待機選手選択時 | B | 脱カード（dashed hairline） |
| 9 | 選手追加モーダル | `showAddPlayer` | D | 脱カード surface |
| 10 | 未保存で他タブ閲覧（isReadOnly バナー） | `isReadOnly` | D | 琥珀バナー |
| 11 | エラー／通知バナー | `error` / `notice` | D | 意味色バナー |
| 12 | ローディング | `matchLoading` | D | 緑スピナー |
| — | LINE生成導線 | `computeLineTextAvailability` | A（トグル） | 脱カード（トグル副次） |

**明示スコープ外（今回のリデザインで触らない）**: `PairingHelp`（使い方ドロップダウンの中身。ヘッダの「使い方」ボタン起点は残す）、`PlayerSearchCombobox` の内部候補リスト（モーダル内の検索UIは surface のみ揃える）。これらは旧トーンのまま残っても継ぎ目にならない小要素として許容。

### ★ 不変（ユーザー明示指定）
- **選手名チップ（A の参加者一覧、B のペア内チップ・待機者チップ）は変更前の元の形式を維持**：ピル（`rounded-full`）＋ bg `#f9f6f2` ＋ 級ごとの枠色（A=red-200 #fecaca / B=blue-200 #bfdbfe / C=orange-200 #fed7aa / D=yellow-200 #fef08a / E=lime-200 #d9f99d）＋ text `#374151` / 14px / weight 400。＝実装 `PlayerChip`／`DraggablePlayerChip` の見た目そのまま。**この部分はリデザインの対象外。** round 1 で warm-white 角丸タイルに変わっていたのを元のピルへ戻し済み（A・B 共通の `.pg-name`）。

## 意図的な挙動差分（ユーザー合意済み・2026-07-12）
リデザインで**現行から意図的に変える3点**（純視覚ではないが、ユーザーが明示選択）。実装はこれらを反映し、回帰ACの「挙動不変」からは除外する：
1. **この画面での日付変更を廃止**。日付はヘッダに表示のみ（`sessionDate` は `?date=` クエリ or 当日デフォルトから受け取る。現行の日付入力＋「今日」ボタンは削除）。→ `setSessionDate` の UI トリガーが無くなる（state 自体は初期化・読み取りで残す）。
2. **LINE生成導線はトグル格納**（初期は畳む。従来はタブ直下に常時表示）。可用性判定（`computeLineTextAvailability`）自体は不変。
3. **参加者リストの折りたたみ廃止＝常時展開**（`showParticipantList` トグル削除）。

## 解決済み論点
- **主アクションの「対戦編集」ラベル**：ユーザー確認済み ＝ **見た目（文言）変更のみ、挙動はそのまま**（＝従来の自動組み合わせ動線 `handleAutoMatch`。ロジック変更なし）。
- **ヘッダの日付＋会場名表示**：ユーザーが表示する方針で確定。データは既存 `PracticeSessionDto.venueName`（+ `sessionDate`）で出せる。**バックエンド改修不要**（純フロント表示）。会場名 null/空 → 日付のみにフォールバック。

## Acceptance Criteria（この design-spec が実装契約＝純UI改修のため requirements.md は起こさない）

### 見た目の AC（新デザインが適用されている）
| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | A/B/C/D の全状態で白い浮きカード・行の塗り・塗りバッジが無く、cream 面＋hairline＋文字階層で構成される | verify |
| AC-2 | ヘッダ h1 に `日付＋会場名` を表示する（会場名は `venueName`）。会場名が null/空なら日付のみ表示にフォールバックする | verify・auto-test（フォールバック分岐） |
| AC-3 | 試合番号は下線タブ表示。アクティブは大きい紺文字、作成済みは緑チェック、未保存タブは琥珀文字で区別される | verify |
| AC-4 | ロック済み行は muted 名＋アイコン付き色ラベル（結果入力済=青／手動ロック=橙）で、塗りpillバッジを使わない | verify |
| AC-5 | 対戦相手キャンセル行は取消線＋グレー名＋右端フラット「キャンセル」タグ。両方キャンセルは非表示。`vs` の左右揃いが崩れない | verify |
| AC-6 | この画面に日付入力・「今日」ボタンが無く、日付はヘッダ表示のみ（`?date=`／当日デフォルトから） | verify |
| AC-7 | LINE生成導線はトグルで、初期は畳まれている（可用時のみトグル表示） | verify |
| AC-8 | 参加者リストは常時展開で、折りたたみトグルが無い | verify |

### ★ 回帰 AC（挙動不変＝リデザインで壊してはいけない。most-severe）
| ID | 条件 | 検証手段 |
|----|------|------|
| AC-R1 | **選手名チップは元のピル形式のまま**（rounded-full／bg `#f9f6f2`／級枠色 red-200…lime-200／text `#374151` 14px）。A 参加者・B ペア内・B 待機者いずれも | verify |
| AC-R2 | D&D／タップ選択での選手入替（`computeDragResult`）が従来どおり動く（ペア間スワップ・待機↔ペア・新規ペア作成・同一ペア左右入替） | auto-test（既存 `pairingDragLogic.test.js` 等 green 維持） |
| AC-R3 | 保護判定 `hasResult \|\| locked` が維持され、ロック済み組は自動組み合わせ・回戦削除・手入れから保護される | auto-test（既存ロジックテスト green 維持） |
| AC-R4 | ロックの明示保存モデル維持（ローカルトグル→「確定して保存」で `createBatch` の `locked` 永続化）。即時ロックAPIは呼ばない | auto-test |
| AC-R5 | 対戦相手キャンセルの read-time 反映（`player1Cancelled/player2Cancelled` 由来の表示・両方非表示・編集モードでの空き化）が維持される | auto-test（`pairingDisplayLogic.test.js` green 維持）・verify |
| AC-R6 | 抽選あり/なしでの参加者範囲（`pairingIncludesPending`）が維持され、新規作成UIは `pairings.length===0` のときのみ表示される | auto-test・verify |
| AC-R7 | 主アクションのラベルは「対戦編集」に変更するが**動線・挙動は従来の自動組み合わせと同一**（クリックで従来と同じ処理） | verify |
| AC-R8 | 既存の Vitest スイート・`npm run lint` がすべて green | auto-test |

## Non-goals（今回やらない）
- バックエンド・API・DB の変更（純フロント。会場名は既存 `PracticeSessionDto.venueName` を使うのみ）。
- **組み合わせの中核ロジック・保護ルールの変更**（`pairingDragLogic` / `pairingLockLogic` / `pairingDisplayLogic` / `lineTextTarget` / `cardRules` の純粋関数と保護判定は挙動不変）。※上記「意図的な挙動差分」3点は例外（UI導線の削減であり中核ロジックには触れない）。
- `PairingHelp` ドロップダウン内容・`PairingSummary`（`/pairings/summary`）のリデザイン（別画面。今回対象外。ヘッダの「使い方」トリガーの配置・配色のみ調整）。
- 主アクションの動線・処理の変更（文言のみ）。

## ハンドオフ（→ /implement pairings-ui-change）— 薄い実装メモ
- **対象ファイル（主）**: `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`（JSX マークアップ／Tailwind クラスの置換が中心）。日付・会場名のヘッダ表示は `PageHeader` の `title` 生成 or ヘッダ相当の見直し（`venueName` を `currentSession` から渡す）。
- **触らない（挙動側）**: `pairingDragLogic.js` / `pairingLockLogic.js` / `pairingDisplayLogic.js` / `lineTextTarget.js` / `cardRules.js` / `DraggablePlayerChip.jsx`（見た目は `PlayerChip` の元ピル維持）/ API・DTO。純粋関数と保護ロジックはそのまま。
- **デザイントークン**: モック `preview/_pairings-current.css` の各値が正（cream `#f2ede6`／panel `#ebe4d8`／hairline `#e7e0d4`・`#ddd3c2`／muted `#8a8275`・`#9a9183`／ink `#1A2744`／緑 `#4a6b5a`／紺 `#1A3654`）。実装は Tailwind 任意値（`bg-[#...]`）で対応。
- **テストファースト**: 既存テスト（drag/lock/display/lineText/cardRules）は挙動不変ゆえ**変更せず green を維持**することが回帰の主検証。表示分岐の追加（会場名フォールバック等）に絞って必要なら軽量テストを追加。
- **確認**: 全状態は Claude Design の4カード（A/B/C/D）が正。旧 `pairing-cancelled-opponent-a.html` は参照しない（C が上位）。

> 純UI改修のため `/define-feature` の requirements.md・technical plan は起こさず、本 design-spec を実装契約として `/implement pairings-ui-change` へ直接ハンドオフする（design-screen「片レンズに縮む」ルール）。
