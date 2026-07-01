---
status: completed
completed_sections: [ユーザーストーリー, 機能要件, 技術設計, 影響範囲]
next_section: null
slug: match-form-redesign
related: design-spec.md（status: locked）
---
# 試合フォーム リデザイン 要件定義書（既存改修 / delta）

> ロジックのレンズ。**視覚は [design-spec.md](design-spec.md)（locked）を参照**し、本書では再記述しない。
> 既存 `MatchForm.jsx` の改修。差分のみ記述する。

## 1. 概要
- **目的:** 1試合入力フォーム（MatchForm）を design-spec の Anti-Slop 階層デザインへ刷新し、あわせて対戦相手の選択フローを「当日参加者＋抜け番のプルダウン／未参加者は検索」に改善する。あわせて①対戦組み合わせ・結果閲覧（MatchResultsView）②結果の一括入力（BulkResultInput）のヘッダーに会場名を表示。
- **背景:** design-spec round 0〜6 で視覚確定（locked）。本書は実装に必要なロジック差分を確定する。
- **調査で判明した前提:**
  - 会場名は `PracticeSessionDto.venueName` に既出（`PracticeSessionService.enrichSessionWithParticipants` で設定）。**表示するだけ**。
  - 相手の級は `PlayerDto.kyuRank` で取得済み（MatchForm は `playerAPI.getAll` で全選手保持）。**frontend で opponentId と突合**して表示（`MatchPairingDto` 改修不要）。
  - 実ロジック差分は「対戦相手の選択モデル」と、それに伴う**未参加者の自動参加登録（サーバ側）**のみ。

## 2. ユーザーストーリー
- **対象:** 選手本人（PLAYER）が練習中／後に自分の1試合結果を入力。
- **目的:** 相手を素早く正確に選び、結果・枚数差・お手付き・メモを最小手数で記録。
- **価値:** 当日参加者がプルダウンに出て選びやすく、稀な未参加者も検索で拾える。相手の級が一目で分かる。視覚も整理され見やすい。

## 3. 機能要件（差分）
> 画面レイアウト・配色・コンポーネント様式は design-spec を参照。ここでは挙動・データ・ルールのみ。

### 3.1 対戦相手の選択モデル【新】
- **プルダウン母集団 = その練習セッションの参加者（自分を除く）＋「抜け番」。**
  - 参加者は `PracticeSessionDto` の参加者情報から取得（現状の `playerAPI.getAll` 全件ではなく当日参加者に限定）。
  - ペアリングが存在する試合は従来どおり相手を自動セット（design-spec：名前＋級＋▽＋🔍）。▽（名前タップ）／プルダウンで変更可能。
- **「未参加から検索」ボタン（🔍）= 当日未参加の全選手を簡易インクリメンタル検索**して選択。
  - UIは**簡易インクリメンタル検索**（名前で絞り込む簡易リスト。新規デザイン不要＝design-screen に戻らない）。母集団 = `playerAPI.getAll` − 当日参加者 − 自分。
- **未参加者を相手に選んで保存したとき = サーバ側でその相手を自動参加登録する【決定】。**
  - **方式（決定）:** 試合保存（`MatchService` の create / createDetailed）の中で、相手が当日セッション未参加なら**サーバ側で参加登録を行う**。直接の参加登録API（`POST /participations`）の「PLAYERは自分のみ」ガードは**温存**し、試合記録に伴う副作用としてサーバ主導で登録する。
  - **登録粒度:** 記録対象の試合（matchNumber）に対して登録する（セッション全試合への一括登録は避ける）。参加データモデル（セッション単位 or 試合単位）に合わせて実装時に確定。
  - 既存「参加未登録（自分）」モーダルの挙動は不変。
- **抜け番 = プルダウンの「抜け番」選択に一本化【決定】。**
  - プルダウンで「抜け番」を選ぶと抜け番モード（活動種別選択 UI、design-spec：bye）へ遷移。
  - 現状の「抜け番として記録する」ボタンは**廃止**。自動判定（自分のペアリング無し＋他にペアリング有り等）は維持。

### 3.2 表示項目【新】
- **ヘッダー = 日付＋会場名**（design-spec：3画面共通）。会場名は `venueName`。`venueName` が無い（venueId null）場合は**会場名を出さず日付のみ**。
  - 適用画面: MatchForm（`getByDate`）／MatchResultsView（`getByDate`）／BulkResultInput（`getById`）。
- **相手の級 = kyuRank の頭文字のみ「(A)」【決定】。** kyuRank 未設定の選手は**非表示**。表示用に短縮ラベル整形（例 "A級"→"(A)"）を frontend ヘルパー（`utils/rank.js`）で行う。danRank・所属は出さない。

### 3.3 据え置き（変更なし。design-spec 参照）
- 数値レンジ：枚数差 0〜25、お手付き 未入力(null)＋0〜20。
- 指導：枚数差ピッカー先頭「指導」、`isLesson=true`/`scoreDifference=null`（登録相手の試合のみ）。
- 結果トグル・メモ・保存/上書き（409）・各モーダル・スワイプ挙動は現状ロジック維持（見た目のみ design-spec へ）。

## 4. 技術設計（差分）
### 4.1 API
- **会場名/級: 追加・変更なし**（既存DTOで充足）。
- **要確認1:** `practiceAPI.getById`（BulkResultInput 用、`GET /practice-sessions/{id}`）のレスポンスに `venueName` が埋まるか（`enrichSessionWithParticipants` を通るか）。通らなければ getById 経路でも `venueName` を埋める（薄い backend 調整）。
- **変更2【新・必須】:** 試合作成（`MatchController` → `MatchService.create` / `createDetailed`）に、**未参加の相手をサーバ側で自動参加登録**する処理を追加。`practiceParticipantService` を内部呼び出し（PLAYERガードを通さないサーバ内処理）。冪等（既に参加済みなら何もしない）。

### 4.2 DB
- **変更なし。** Venue.name / Player.kyuRank / 参加（participation）テーブルとも既存。マイグレーション不要。

### 4.3 フロントエンド（主戦場）
- `MatchForm.jsx`:
  - opponent 母集団を「当日参加者＋抜け番」に変更（`PracticeSessionDto` の参加者を使用）。
  - 「未参加から検索」UI（簡易インクリメンタル検索）を追加。母集団 = 全選手 − 参加者 − 自分。
  - 相手確定時の級表示（opponentId × `availablePlayers` 突合 → kyuRank → "(A)"）。
  - 抜け番をプルダウン選択へ統合、旧「抜け番として記録する」ボタン削除。
  - 見た目を design-spec の最新形へ全面マークアップ刷新（`_match-form-final.css`/`_match-form-ext.css` 相当を本番 Tailwind/CSS へ移植）。
- `MatchResultsView.jsx` / `BulkResultInput.jsx`: ヘッダーに会場名（`venueName`）を追加表示（design-spec：日付＋会場ヘッダーの横展開）。
- `utils/rank.js`: kyuRank → "(A)" 短縮ヘルパー追加。
- デザイントークン（warm-taupe／green-700・red-700／角丸10px／type scale 等、design-spec §8）の Tailwind 落とし込み方針を実装時に確定。

### 4.4 バックエンド
- `MatchService`（および呼ぶ Controller）: 上記「変更2」の自動参加登録を実装。`MatchService` から `PracticeParticipantService` を内部利用。
- getById の `venueName`（要確認1）の調整があればここ。

## 5. 影響範囲
- **MatchForm.jsx**（主）: 対戦相手選択ロジック・抜け番導線・保存フロー・全面再スタイル。
- **MatchResultsView.jsx / BulkResultInput.jsx**: ヘッダーに会場名追加（表示のみ）。
- **MatchService（自動参加登録）**: 相手の参加状態を変えるため、**参加者数・容量（capacity/matchCapacityStatuses）・ペアリング・抽選・通知**等の既存挙動への波及を要確認。冪等性・トランザクション境界（試合保存と参加登録の整合）に注意。
- **utils/rank.js / PlayerChip 等**: 級表示ヘルパー追加（既存表示は不変）。
- デザイントークン追加が他画面の見た目に影響しないようスコープを限定。

### 5.1 自動参加登録の影響検証結果（タスク6 / #967）
試合保存時の自動参加登録（`createMatch` → `PracticeParticipantService.autoRegisterMatchParticipant`、status=WON・冪等）について、各既存挙動への波及を検証した。**想定外の副作用なし**。
- **参加者数 / 容量（matchCapacityStatuses）**: 集計は WON/PENDING（+OFFERED）を数える。自動WONは「実際に対戦した＝参加した」事実を反映するため、カウント・定員状況が正しく増える。定員超過時は `FULL` 表示になるが、これは実態（定員外の選手が対戦した）を示すもので破綻なし。
- **ペアリング**: `createMatch` 内で `autoCreateMatchPairingIfAbsent`（pairing 生成）を実行した**後**に自動参加登録するため順序衝突なし。pairing 生成ロジックは試合記録時の参加登録に影響されない。
- **抽選**: 抽選は締切前の PENDING を対象に動く。自動登録は試合実施後の記録時に WON で行うため抽選対象にならず、既存の抽選実行（PENDING ベース）に干渉しない。
- **通知**: `autoRegisterMatchParticipant` は `densukeSyncService.triggerWriteAsync()` を呼ばず、`notifySameDayJoinIfApplicable` 等の参加通知も発火しない（試合保存ホットパスでの外部書き込み・通知スパムを回避）。参加レコードは `dirty=true` で保存され、**次回の通常の伝助同期で反映**される（整合は保たれる）。
- **冪等性 / トランザクション**: 既にアクティブ参加なら no-op。`createMatch`（@Transactional）と同一トランザクションで実行し、試合保存と参加登録の整合を担保。
- **回帰テスト**: `MatchServiceTest`（委譲とセッション特定・簡易版での非実行）／`PracticeParticipantServiceTest`（WON・dirty=true・冪等・引数ガード）で担保。

## 6. 設計判断の根拠
- venue/grade は既存DTOで賄えるため backend を増やさない（調査で確認）。相手の級はフロント突合で `MatchPairingDto` 改修を回避。
- 未参加者の自動参加登録は**サーバ側（試合保存の副作用）**で実現＝直接APIの「PLAYERは自分のみ」ガードを温存しつつ、データ整合（対戦したなら参加者）を確保（ユーザー決定）。
- 検索は簡易実装でデザインループに戻らない（ユーザー決定）。
- 抜け番はプルダウン一本化で入口を単純化（ユーザー決定／design-spec 準拠）。

## デザインへの宿題（→ /design-screen match-form-redesign）
- **なし**（design-spec は locked、検索UIも簡易実装で新規デザイン不要）。
