---
status: completed
---
# 対戦組み合わせ：LINE単一試合テキスト生成 ＆ 札ルールの日付シード決定化 要件定義書

> 本機能は**2つのスコープ**を1つのブランチ/PRでまとめて対応する（ユーザー判断）。
> - **Part A（基盤）**：札ルールを「日付から決定的に導出（シード化）」し、端末・再訪・過去日でブレない決定論的生成にする。
> - **Part B（本題）**：LINE送信用テキストを「全試合」だけでなく「選択中の試合番号（〇試合目）」単位でも生成できるようにする。
>
> Part A は Part B の「単一試合と全試合で札ルールが食い違わない」一貫性を根本から保証する基盤であり、先に実装する。

## 1. 概要

### 目的
- **Part A**：札ルール（札組）の決まり方を、`Math.random()` ＋ 配列丸ごと localStorage 保存（当日のみ）から、**「日付（＋再生成カウンタ）をシードにした決定論的生成」**へ変更し、同じ日・同じ試合番号なら常に同じ札ルールになるようにする。
- **Part B**：対戦組み合わせの「LINE送信用テキスト生成」に、**選択中の試合番号の組み合わせだけ**を全試合と同じフォーマット・同じ札ルールで生成する手段を追加する。

### 背景・動機
- 現状の札ルールは `Math.random()` 由来で非決定的（[cardRules.js:22-25](../../../karuta-tracker-ui/src/pages/pairings/cardRules.js#L22-L25), [cardRules.js:68](../../../karuta-tracker-ui/src/pages/pairings/cardRules.js#L68)）、保存は当日のみ（[PairingSummary.jsx:83](../../../karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx#L83)）、保存先は端末ローカルの localStorage（[cardRules.js:138-144](../../../karuta-tracker-ui/src/pages/pairings/cardRules.js#L138-L144)）。このため①開くたび／②別端末／③過去日で札ルールがブレ得る。
- Part B で「単一試合だけ」のテキストを出すと、全試合テキストと札ルールが食い違うリスクがあるため、Part A で決定論化して根本解決する。
- 「LINE送信用テキスト生成」のボタンを単純に2つ並べると冗長になるため、「全試合 / 〇試合目」の2択（セグメントトグル）に整理する。

---

## 2. ユーザーストーリー

- **対象ユーザー**：対戦組み合わせを作成しLINEで共有する運営担当（現状の対戦組み合わせ画面の利用者と同じ）。
- **Part A の価値**：同じ日の札ルールは、いつ・どの端末で開いても同じ。全試合テキストと単一試合テキストで札ルールが食い違わない。
- **Part B の価値**：その日の全試合だけでなく、選択中の1試合分の組み合わせだけを、全試合と同一フォーマット・同一札ルールでテキスト化してLINEに貼り付けられる。
- **利用シナリオ（Part B）**：
  1. 対戦組み合わせ画面で試合番号タブを選ぶ。
  2. 「全試合 / 〇試合目」のセグメントトグルで対象を選び、生成ボタンを押す。
  3. 札ルール一覧画面に遷移し、対象（全試合 or 単一試合）のテキストが表示される。
  4. 「コピー」でクリップボードへコピーし、LINEに貼って送信する。

---

## 3. 機能要件

### 3.A Part A：札ルールの日付シード決定化

#### 3.A.1 生成方式
- 札ルールは **`(date, nonce, totalMatches)` の純関数**として決定論的に生成する。
  - **シード** = `hash(date文字列 + nonce)`（整数）。
  - シード付きPRNG（決定論的乱数）を1つ生成し、既存の3試合サイクル生成ロジック（`ones`/`nuki`/`tens` とサイクル間制約）をその乱数で駆動する。
  - 同一シードなら**必ず同一の札ルール列**になる。`totalMatches` を増やしても、先頭の試合の札ルールは変わらない（決定論的列の先頭が安定するため）。
- 既存の **`prefix` 引数 / `reconcileCardRules`（不足分追加生成）/ 札ルール配列の localStorage 保存**は不要になるため廃止・簡素化する。

#### 3.A.2 再生成カウンタ（nonce）
- 「札を再生成」を残すため、**日付ごとの再生成カウンタ `nonce`（整数, 既定0）**を localStorage に保存する。
  - 既定（`nonce=0`）：シードは日付のみに依存 → **全端末・過去日・再訪で完全一致**。保存が無くても再現できる。
  - 「札を再生成」押下時：`nonce` を +1 して保存し、`hash(date + nonce)` で再生成。
  - 結果として **「再生成していない既定状態」は全端末で不変**、**「再生成した端末」だけが枝分かれ**する（サーバ保存しない以上、これは仕様上の許容事項）。

#### 3.A.3 既存データ・掃除
- 旧形式の札ルール配列キー（`karuta-tracker:card-rules:<date>`）は読まなくなる。`cleanupOldCardRules` は **今日以外の nonce キー（および旧形式キー）を削除**する役割に変更する。
- デプロイ当日、既に旧形式で当日分が保存されていても、新方式は日付シードから再計算するため**当日の表示札ルールが一度だけ変わり得る**（運用上の軽微な一回性。必要なら再生成で対応可）。

### 3.B Part B：LINE単一試合テキスト生成

#### 3.B.1 対戦組み合わせ画面（PairingGenerator）— 生成導線
現在「全試合が揃ったときのみ表示される単一の『LINE送信用テキスト生成』ボタン」（[PairingGenerator.jsx:790-803](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L790-L803)）を、次の構成へ置き換える。

- **配置**：従来ボタンと同じ位置（試合番号タブ直下）。
- **構成**：
  1. **セグメントトグル** `[ 全試合 | {matchNumber}試合目 ]`
     - 「全試合」：**全試合の組み合わせが揃っている（allComplete）ときのみ有効**。
     - 「{matchNumber}試合目」：**選択中の試合の組み合わせが完成している（`matchExistsMap[matchNumber]`）ときのみ有効**。ラベルの数字は試合番号タブの選択に追従。
  2. **生成ボタン（1つ）**：選択中セグメントに応じて札ルール一覧画面へ遷移。
- **セクション表示条件**：`allComplete || matchExistsMap[matchNumber]` のいずれか true で表示。両方 false なら非表示。
  - `allComplete` が true のときは選択中試合も必ず完成しているため、両セグメントが有効。
- **選択の初期値とフォールバック**：
  - `allComplete` true → 初期は「全試合」。
  - `allComplete` false かつ選択中試合のみ完成 → 「{matchNumber}試合目」。
  - 試合番号タブ切替で現在選択中セグメントが無効化されたら、もう一方の有効セグメントへ自動フォールバック。両方無効ならセクション非表示。
- **遷移先URL**：
  - 全試合：`/pairings/summary?date=${sessionDate}`
  - 単一試合：`/pairings/summary?date=${sessionDate}&matchNumber=${matchNumber}`

#### 3.B.2 札ルール一覧画面（PairingSummary）— 表示
- URLクエリ `matchNumber` の有無で表示モードを切替。
  - **なし** → 従来どおり全試合テキスト。
  - **あり（有効値）** → 単一試合モード。対象試合のテキストのみ表示。
- **単一試合テキストのフォーマット**（全試合テキストの該当ブロックと完全一致）：
  ```
  {M}月{D}日
  {N}試合目　{札ルールのdescription}
  {player1Name}　{player2Name}
  ...
  ```
  - 日付見出し（`M月D日`）を含む。試合番号は実際の `N`（=URLの matchNumber）を表示（配列先頭でも「1試合目」固定にしない）。
  - 札ルールは対象試合に対応するもの（`cardRules[N-1]`）。Part A により全試合テキストの該当ブロックと一致する。
- **コピー機能**：従来どおり。
- **「札を再生成」ボタン**：**当日（今日）かつ全試合モードのときのみ表示**する。単一試合モードでは非表示（札ルールはその日全体の概念であり、単一試合画面から全体を再生成するのは誤操作・混乱のもと）。過去日・他日も非表示とし、決定論の既定札ルール（全端末一致）を表示する（レビュー指摘対応: cleanup の「今日以外の nonce 削除」方針と整合させ、過去日再生成が次回ロードで既定に戻る不整合を解消）。
- 画面タイトル等その他構造は現状維持。

#### 3.B.3 ビジネスルール（Part B）
- **試合番号の対応（重要）**：`matchNumber` は1始まり、`cardRules`/`matchData` は0始まり配列。単一試合は `matchNumber=N → index N-1` を厳守。表示する試合番号も `N`（1始まり）。
- **妥当性チェック**：`matchNumber` が数値でない／1未満／`totalMatches` 超過は無効とみなし**全試合モードにフォールバック**。
- 対象試合のペアが空でもエラーにせず、日付見出し＋`N試合目　{札ルール}` を表示（URL直接アクセス防御）。

---

## 4. 技術設計

### 4.1 API設計
- **新規・変更なし**。既存 `practiceAPI.getByDate`、`pairingAPI.getByDateAndMatchNumber` を利用。フロントエンド完結。

### 4.2 DB設計
- **変更なし**（DBマイグレーションは発生しない）。Part A の nonce は localStorage 管理。

### 4.3 フロントエンド設計

#### cardRules.js（Part A の中心）
- 追加：
  - `hashSeed(date, nonce)` — 文字列→32bit整数ハッシュ（例：cyrb53 / FNV-1a 系）。
  - `mulberry32(seed)` — 決定論的PRNG（`() => number(0..1)`）。
  - `loadNonce(date)` / `saveNonce(date, n)` — 再生成カウンタの localStorage 入出力。
- 変更：
  - `pickRandom(arr, n, rng)` — `Math.random` を引数の `rng` に置換。**エンジン非依存の決定性確保のため、`sort(() => rng()-0.5)` の偏ったシャッフルを seeded Fisher-Yates に変更**する。
  - `generateCardRules(totalMatches, rng)` — `Math.random` 直呼びを `rng` に置換。`prefix` 引数を廃止。
  - `loadCardRules`/`saveCardRules`/`reconcileCardRules` — 札ルール配列の保存は不要のため廃止（`isValidCardRule` は不要なら削除、流用するなら残置）。
  - `cleanupOldCardRules` — 今日以外の nonce キー（＋旧形式キー）の削除に役割変更。
- 公開ヘルパ（例）：`getCardRules(date, totalMatches)` = `generateCardRules(totalMatches, mulberry32(hashSeed(date, loadNonce(date))))`。

#### PairingSummary.jsx
- 札ルール取得を `getCardRules(date, totalMatches)` に置換（localStorage 配列復元・reconcile を撤去）。
- 「札を再生成」：`saveNonce(date, loadNonce(date)+1)` → `getCardRules` 再計算。単一試合モードでは非表示。
- `useSearchParams` から `matchNumber` を取得し、`generateText(date, matchData, cardRules, targetMatchNumber?)` に対象試合番号を渡す。`targetMatchNumber` 指定時は日付見出し＋該当ブロックのみ出力。
- `matchNumber` バリデーション（1..totalMatches）と無効時の全試合フォールバック。

#### PairingGenerator.jsx
- 既存の単一 Link（L790-803）をセグメントトグル＋生成ボタンへ置換。
- 追加 state（例）`lineTextTarget ∈ 'all' | 'single'`。既存の `matchNumber`/`matchExistsMap`/`currentSession.totalMatches`/`sessionDate` で有効・無効・表示条件・遷移URLを決定。

#### 状態管理
- すべてコンポーネントローカルの `useState` で完結。Context 追加なし。

### 4.4 バックエンド設計
- **変更なし**（Controller/Service/Repository/Entity/DTO いずれも改修不要）。

---

## 5. 影響範囲

### 変更が必要な既存ファイル
- `karuta-tracker-ui/src/pages/pairings/cardRules.js` — Part A（決定論化・nonce・掃除）。
- `karuta-tracker-ui/src/pages/pairings/cardRules.test.js` — 決定性テスト追加、prefix系テストをシード継続テストへ置換、不要になった関数のテスト整理。
- `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx` — Part A取得切替＋Part B単一試合モード・再生成nonce化。
- `karuta-tracker-ui/src/pages/pairings/PairingSummary.integration.test.jsx` — 単一試合モード／決定性のテスト。
- `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — セグメントトグル＋生成導線。
- `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — トグル・遷移URLのテスト（必要に応じて）。
- ドキュメント：`docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md`。

### 既存機能への影響
- **札ルールの値そのものが変わる**：決定論化により、これまで表示されていた（ランダムな）札ルールとは異なる値になる。当日分が旧形式で保存済みでもデプロイ後に再計算される（一回性の変化）。**札ルールの構造・種類・サイクル制約は不変**。
- **全試合テキスト生成**：`matchNumber` 無指定時は従来挙動を維持（後方互換）。
- **LINE抽選通知（lottery）系**：別機能であり影響なし。
- **API/DB**：影響なし（フロント完結）。
- **共通コンポーネント**：変更なし。

---

## 6. 設計判断の根拠

- **Part A を Part B より先に実装する理由**：単一試合と全試合で札ルールが食い違わない一貫性を、保存に頼らず根本から保証できる。Part B の「過去日で札ルールがズレ得る」懸念も Part A で完全に解消する。
- **日付シード決定論（保存不要）を採用した理由（Q2回答）**：保存ゼロで全端末・過去日・再訪が一致し、実装も最小。サーバ保存（DB変更）を避けつつ「ずれない」を満たす。
- **再生成を nonce で残す理由（Q3回答）**：日付決定論のままでは同日で別ルールを出せないため、再生成カウンタで両立。既定状態は全端末一致、再生成した端末のみ枝分かれする点はサーバ保存しない以上の許容事項。
- **seeded Fisher-Yates へ変更する理由**：`sort(() => rng()-0.5)` は分布が偏り、かつ決定論的乱数では比較関数が非推移的になりエンジン依存の挙動を生む。Fisher-Yates で均一かつエンジン非依存の決定性を担保する。
- **フロントエンド完結とした理由**：札ルールは元々クライアント概念、組み合わせ取得APIは既存で揃う。DBマイグレーション事故（CLAUDE.md記載のIssue #518型）のリスクを持ち込まない。
- **表示条件（全試合=全完成時のみ／個別=その試合完成時のみ）（Q3-表示回答）**：未完成の組み合わせを誤送信するのを防ぐ。
- **単一試合モードで「札を再生成」を隠す理由**：再生成は全試合分の札ルールを変える操作で、単一試合画面からの実行は誤認・混乱を招くため全試合モードに集約。
- **同一PRでまとめる判断（Q1回答）**：通常は無関係変更を分離する方針だが、本件は札ルール一貫性で直結するためユーザー判断により同一ブランチ/PRで対応する。
