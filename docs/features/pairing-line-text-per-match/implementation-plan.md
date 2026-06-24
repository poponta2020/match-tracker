---
status: completed
---
# 対戦組み合わせ：LINE単一試合テキスト生成 ＆ 札ルールの日付シード決定化 実装手順書

> 同一ブランチ/PRで Part A（札ルール決定論化）→ Part B（LINE単一試合テキスト）の順に実装する。
> バックエンド・DB変更なし（フロントエンド完結）。

## 実装タスク

### タスク1: cardRules.js を日付シード決定論化する（Part A 基盤）
- [x] 完了
- **概要:** 札ルール生成を `Math.random()` 依存から「日付（＋再生成カウンタ nonce）をシードにした決定論的生成」へ変更する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/cardRules.js`
    - 追加: `hashSeed(date, nonce)`（文字列→32bit整数。cyrb53/FNV-1a系）
    - 追加: `mulberry32(seed)`（決定論PRNG `() => number(0..1)`）
    - 追加: `loadNonce(date)` / `saveNonce(date, n)`（localStorage、既定0）
    - 追加: 公開ヘルパ `getCardRules(date, totalMatches)` = `generateCardRules(totalMatches, mulberry32(hashSeed(date, loadNonce(date))))`
    - 変更: `pickRandom(arr, n, rng)` … `Math.random` を引数 `rng` に置換し、**seeded Fisher-Yates** で均一・エンジン非依存に
    - 変更: `generateCardRules(totalMatches, rng)` … `Math.random` 直呼びを `rng` へ置換、`prefix` 引数を廃止
    - 廃止: `loadCardRules` / `saveCardRules` / `reconcileCardRules`（札ルール配列の保存・突合は不要）。`isValidCardRule` は流用しないなら削除
    - 変更: `cleanupOldCardRules` … 今日以外の **nonce キー（＋旧形式 `card-rules:<date>` キー）** を削除する役割へ
- **依存タスク:** なし
- **対応Issue:** #893

### タスク2: cardRules のテストを決定性ベースへ更新する（Part A）
- [x] 完了
- **概要:** 決定論化に合わせ、同一シードで同一出力・シード継続で先頭安定を検証。prefix系テストを置換し、廃止関数のテストを整理。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/cardRules.test.js`
    - 追加: 「同一 `(date, nonce)` → 同一札ルール列」「`generateCardRules(5, rng)` の先頭3件 == `generateCardRules(3, 同シードrng)`（決定論列の先頭安定）」
    - 追加: `hashSeed`/`mulberry32` の決定性（同入力→同出力）、`loadNonce`/`saveNonce` の round-trip
    - 維持: 既存の構造検証（type/digits長/サイクル/サイクル間制約/digits範囲）は seeded rng で実行
    - 置換: `prefix で続行` ブロック → シード継続による先頭安定テスト
    - 整理: 廃止した関数（loadCardRules/saveCardRules/reconcileCardRules）のテスト削除
- **依存タスク:** タスク1
- **対応Issue:** #894

### タスク3: PairingSummary を改修する（Part A取得切替 ＋ Part B単一試合モード）
- [x] 完了
- **概要:** 札ルール取得を決定論ヘルパへ切替、再生成を nonce 化。URL `matchNumber` による単一試合モードを追加。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx`
    - 取得切替: localStorage 配列復元＋reconcile を撤去し `getCardRules(date, totalMatches)` を使用（`canPersist`/`saveCardRules` 分岐を nonce 方式へ）
    - 再生成: `handleRegenerate` を `saveNonce(date, loadNonce(date)+1)` → `getCardRules` 再計算に変更
    - 単一試合モード: `useSearchParams` から `matchNumber` 取得、`generateText(date, matchData, cardRules, targetMatchNumber?)` に対象試合番号を渡す（`targetMatchNumber` 指定時は日付見出し＋該当ブロックのみ出力、試合番号は `N`、札ルールは `cardRules[N-1]`）
    - バリデーション: `matchNumber` が 1..totalMatches 外なら全試合モードへフォールバック
    - 再生成ボタン: 単一試合モードでは非表示
- **依存タスク:** タスク1
- **対応Issue:** #895

### タスク4: PairingGenerator に生成導線（セグメントトグル＋生成ボタン）を実装する（Part B）
- [x] 完了
- **概要:** 既存の単一「LINE送信用テキスト生成」Link を `[ 全試合 | 〇試合目 ]` セグメントトグル＋生成ボタンへ置換し、単一は `?matchNumber=N` を付けて遷移。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - 置換: L790-803 の Link をトグル＋生成ボタンのセクションへ
    - 追加 state: `lineTextTarget ∈ 'all' | 'single'`
    - 有効/表示条件: 全試合=`allComplete`、個別=`matchExistsMap[matchNumber]`、セクション=いずれか true。初期選択・タブ切替時の自動フォールバック
    - 遷移: 全試合→`/pairings/summary?date=${sessionDate}`、単一→`/pairings/summary?date=${sessionDate}&matchNumber=${matchNumber}`
- **依存タスク:** タスク3
- **対応Issue:** #896

### タスク5: フロントエンドの統合テストを追加する
- [x] 完了
- **概要:** 単一試合モード・決定性、トグル・遷移URLを検証。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.integration.test.jsx` — `matchNumber` ありで単一試合テキスト（日付見出し＋N試合目＋札ルール＋ペア）、全試合の該当ブロックと一致、無効 matchNumber の全試合フォールバック、単一モードで再生成ボタン非表示
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — トグルの有効/表示条件、生成ボタンの遷移URL（全試合/単一）
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #897

### タスク6: ドキュメントを更新する
- [x] 完了
- **概要:** 仕様・画面一覧・設計書へ本機能（Part A/B）を反映。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 札ルール決定論化、LINE単一試合テキスト生成
  - `docs/SCREEN_LIST.md` — 対戦組み合わせ画面の生成導線（2択）、札ルール一覧の単一試合モード
  - `docs/DESIGN.md` — cardRules 決定論設計（seed/nonce/Fisher-Yates）、PairingSummary/Generator の変更
- **依存タスク:** タスク1〜5
- **対応Issue:** #898

## 実装順序
1. タスク1: cardRules.js 決定論化（依存なし・基盤）
2. タスク2: cardRules テスト更新（タスク1に依存）
3. タスク3: PairingSummary 改修（タスク1に依存）
4. タスク4: PairingGenerator 生成導線（タスク3に依存）
5. タスク5: 統合テスト（タスク3・4に依存）
6. タスク6: ドキュメント更新（全タスクに依存）
