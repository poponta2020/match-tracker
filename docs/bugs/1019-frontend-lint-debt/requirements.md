---
status: approved
issue: 1019
---
# バグ改修要件: フロントエンドの既存lintエラー46件（警告13件）を解消する

## 背景（経緯）

PR #1007（ボトムナビのiOS Safariスクロール追随バグ修正）の出荷時、DoDゲート（`gate-dod.sh` A2 lintチェック）が `npm run lint` のFAILで通らず、`--skip-dod` でバイパスして出荷した。調査の結果、mainブランチ時点で既に46件のエラー・13件の警告が存在しており、PR #1007自体（`Layout.jsx`変更）とは無関係の既存負債と確認済み。フォローアップとして Issue #1019 が切られていたが未着手だった。

今回、口頭伝聞（「DoDスキルが既存の50何件で失敗する」）を契機に調査を行い、Issue #1019 の対応そのものとして改めて改修要件を確定する。

## 再現手順

1. `cd karuta-tracker-ui`
2. `npm install`（未インストールの場合）
3. `npm run lint`

## 期待される動作 / 実際の動作

- 期待: エラー0件・exit code 0 で終了する
- 実際: **46エラー・13警告（exit code 1）** で終了する

## 根本原因

`npm run lint`（`eslint .`）の指摘46エラーは以下6カテゴリに分類される（ファイル:行は代表例）。

| カテゴリ | 件数 | 代表ファイル | 原因 |
|---|---|---|---|
| A. no-unused-vars | 15+ | `public/sw.js`, `ErrorBoundary.jsx`, `MatchParticipantsEditModal.jsx`, `LineScheduleAdmin.jsx`(x3), `BulkResultInput.jsx`(x2), `MatchForm.jsx`(x2), `MentorManagement.jsx`, `PairingSummary.jsx`, `OrganizationSettings.jsx`(x2) | catchの未使用err変数、未使用の分割代入結果など |
| B. no-undef | 3 | `public/sw.js:30,37`（`clients`）, `ErrorBoundary.jsx:50`（`process`） | sw.jsはService Worker専用グローバルがeslint設定に未定義。ErrorBoundaryはVite環境で存在しない`process.env`を参照 |
| C. react-refresh/only-export-components | 3 | `PlayerChip.jsx:19`, `AuthContext.jsx:6`, `BottomNavContext.jsx:5` | 同一ファイルからコンポーネントと非コンポーネント（hook/定数関数）を同時exportしている |
| D. no-irregular-whitespace | 3 | `PairingSummary.jsx:64,72`, `cardRules.js:116` | 全角スペース（U+3000）を日本語表示テキストの区切りとして意図的に使用。`no-irregular-whitespace`はデフォルトでテンプレートリテラルを検査対象にするため誤検知 |
| E. no-useless-catch | 1 | `AuthContext.jsx:66`（register内） | `catch(error){ throw error; }` のみで実質何もしていない |
| F. react-hooks/rules-of-hooks | 20 | `PracticeForm.jsx:128-236` | `PracticeForm`が`/practice/new`と`/practice/:id/edit`を1コンポーネントで兼用しており、`if (isEdit) return <PracticeEditForm/>`の**後**に新規登録モード用のuseState/useEffect/useMemoを呼んでいる。ルートが分かれているため現状クラッシュはしていないが、Reactのフックルール違反 |

（AuthContext.jsxのB/E/C、PlayerChip.jsxのA/Cのように1ファイルに複数カテゴリが該当する箇所あり。件数の重複はファイル単位の実数と一致）

不可視文字調査で `no-irregular-whitespace` の3件は全て日本語文言中の意図的な全角スペースであり、削除すべきバグではないことを確認済み（伝助由来の重複プレイヤー問題[project_densuke_emoji_duplicate_players.md参照]とは無関係）。

## 修正方針

| カテゴリ | 方針 |
|---|---|
| A. no-unused-vars | 機械的に削除・使用箇所に統合。`AuthContext.jsx`の`firstLogin`は用途上リネーム不要のため`_firstLogin`にリネーム（ignoreパターン`^[A-Z_]`適合）。`MatchForm.jsx`の`icon: Icon`・`PlayerChip.jsx`の`as: Component`（リネーム付き分割代入をJSXタグ名としてのみ使うケースの誤検知）は、コードを書き換えるのではなく`eslint.config.js`に`argsIgnorePattern: '^[A-Z_]'`を追加して根本対応（code-review高effortの指摘を反映。両ファイルは元の分割代入表記のまま） |
| B. no-undef | `eslint.config.js`に`public/sw.js`用のfiles override（`globals.serviceworker`）を追加。`ErrorBoundary.jsx`は`process.env.NODE_ENV`を`import.meta.env.DEV`に置換（Vite標準） |
| C. react-refresh/only-export-components | 該当exportに`eslint-disable-next-line`を付与。hookをファイル分離する対応（`useAuth`は28ファイルから参照されており影響範囲が大きい）はNon-goalsとし、今回は行わない。`BottomNavContext.jsx`は呼び出し元が2箇所のみで`useAuth`ほど影響範囲は大きくないが、Non-goalsの方針（useAuth/useBottomNavを同方針で見送り）に合わせてeslint-disableで対応し、コメントに実際の呼び出し箇所数を明記（code-review指摘: 当初のコメントは影響範囲を過大に記載していた） |
| D. no-irregular-whitespace | 当初`eslint.config.js`に`{ skipTemplates: true }`を追加する案だったが、リポジトリ全体のテンプレートリテラルで不可視文字検査が無効化されてしまう（本プロジェクトは伝助由来の不可視文字混入の実害があり検査価値が高い）ため、該当3箇所（`PairingSummary.jsx`×2、`cardRules.js`×1）に`eslint-disable-next-line`を個別付与する方式に変更（code-review高effortの指摘を反映） |
| E. no-useless-catch | 無意味なtry/catchを削除し、呼び出し元にそのままエラーを伝播させる（現状の挙動と同一） |
| F. react-hooks/rules-of-hooks | `PracticeForm`の新規登録モードの中身（現状の111〜712行相当）を`PracticeNewForm`という別コンポーネントに切り出す。`PracticeForm`は`isEdit`に応じて`PracticeEditForm`/`PracticeNewForm`を出し分ける薄いディスパッチャーとし、管理者チェック用`useEffect`は両モード共通のため維持する。挙動は変更しない |

## Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | `karuta-tracker-ui`で`npm run lint`がエラー0件・exit code 0で終了する | auto-test |
| AC-2 | 既存のフロントエンドテスト（`npm test`）がすべてPASSする（デグレなし） | auto-test |
| AC-3 | `PracticeForm.jsx`分割後、新規登録モード（`/practice/new`）・編集モード（`/practice/:id/edit`）それぞれの最小レンダリングテストが存在しPASSする（rules-of-hooks修正の副作用がないことを保証） | auto-test |
| AC-4 | ローカル起動で`/practice/new`と`/practice/:id/edit`を開き、フォーム表示・保存操作が従来通り動作することを目視確認する | verify |

**AC-4実施結果**: ブラウザ操作可能なツールがセッション内に無かったため、フル目視確認の代わりに以下で代替検証した — (1) worktree版フロントエンド（Vite dev）と本番DB接続のバックエンド（Spring Boot）を実起動、(2) Viteが`PracticeForm.jsx`をコンパイルエラーなく配信することを確認、(3) `/api/venues`等バックエンドAPIが200で応答することを確認、(4) AC-3の実DOMレンダリングテスト（RTL）で新規登録/編集モード双方が例外なくレンダリングされることを確認。ブラウザでの最終目視は未実施のため、必要であればユーザー側でのご確認をお願いしたい。

## Non-goals

- 警告13件（react-hooks/exhaustive-deps）の解消 — useEffect依存追加は再フェッチ・ポーリング挙動を変えうるため、個別に動作確認が必要。別Issueに切り出す
- `useAuth`/`useBottomNav`をコンテキストファイルから分離するリファクタ（react-refresh違反の根本対応）— 28ファイルに影響する大規模変更のため今回は`eslint-disable`で対応し見送る
- `gate-dod.sh`のA2 lintチェックの設計変更（PR差分スコープ限定など）— 別スコープとして見送り済み（ユーザー確認済み）

## 修正ステップ

- [x] ステップ1: no-unused-vars 一括修正（A）— 対応AC: AC-1 / 変更領域: 12ファイル前後 / 完了条件: 該当エラー0件
- [x] ステップ2: no-undef 修正（B、eslint.config.js + ErrorBoundary.jsx）— 対応AC: AC-1 / 完了条件: 該当エラー0件
- [x] ステップ3: no-irregular-whitespace 修正（D、eslint.config.js設定変更のみ）— 対応AC: AC-1 / 完了条件: 該当エラー0件・表示文言不変
- [x] ステップ4: react-refresh/only-export-components 修正（C、eslint-disable付与）— 対応AC: AC-1 / 完了条件: 該当エラー0件
- [x] ステップ5: no-useless-catch 修正（E）— 対応AC: AC-1 / 完了条件: 該当エラー0件
- [x] ステップ6: PracticeForm.jsx コンポーネント分割（F）+ 回帰テスト追加 — 対応AC: AC-1, AC-3, AC-4 / 完了条件: rules-of-hooksエラー0件・新規登録/編集モード双方が動作
- [x] ステップ7: 全体確認 — `npm run lint`エラー0件・`npm test`全PASSを最終確認（AC-2）

## 影響範囲

- フロントエンドのみ（`karuta-tracker-ui/`）。バックエンド・DBへの影響なし
- 変更ファイル: `eslint.config.js`, `public/sw.js`, `src/components/ErrorBoundary.jsx`, `src/components/PlayerChip.jsx`, `src/context/AuthContext.jsx`, `src/context/BottomNavContext.jsx`, `src/pages/line/LineScheduleAdmin.jsx`, `src/pages/matches/BulkResultInput.jsx`, `src/pages/matches/MatchForm.jsx`, `src/pages/mentor/MentorManagement.jsx`, `src/pages/pairings/PairingSummary.jsx`, `src/pages/pairings/cardRules.js`, `src/pages/settings/OrganizationSettings.jsx`, `src/pages/practice/PracticeForm.jsx`, `src/components/MatchParticipantsEditModal.jsx`、新規追加 `src/pages/practice/PracticeForm.test.jsx`（計16ファイル程度）
- 実装時の追加所見: `BulkResultInput.jsx`の`matches`state、`PairingSummary.jsx`の`cardRules`stateはsetterのみ呼ばれ値が未参照のデッドステートと判明し削除
- auto-review-loopのcode-review（高effort）で3件の指摘を受け反映済み: (1) `MatchForm.jsx`/`PlayerChip.jsx`の分割代入書き換えを撤回し`eslint.config.js`の`argsIgnorePattern`追加による根本対応に変更、(2) `no-irregular-whitespace`のグローバル`skipTemplates`を該当3行への個別`eslint-disable-next-line`に変更（不可視文字混入の実害があるプロジェクトのため検査範囲を広く保つ）、(3) `BottomNavContext.jsx`のeslint-disableコメントの影響範囲記載を実数（2箇所）に修正
- `PracticeForm.jsx`の分割はコンポーネント構造の変更を伴うが、`isEdit`によるモード分岐ロジックとhookの実行内容自体は変更しないため機能面の挙動は維持される
