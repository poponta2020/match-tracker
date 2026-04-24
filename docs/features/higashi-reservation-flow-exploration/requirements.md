---
status: completed
---
# higashi-reservation-flow-exploration 要件定義書

## 1. 概要

### 目的
東区民センター（sapporo-community.jp／札幌市民施設予約システム）の予約申込フローを実機探索し、将来の higashi-reservation-proxy 機能の要件定義に必要な技術情報（URL / DOM構造 / ViewState挙動 / 完了判定条件 / エラー挙動等）を取得する。

### 背景・動機
kaderu-reservation-proxy 機能の実装に続き、東区民センターでも同様のリバースプロキシ機能を実装したい。しかし東区民センター側は既存スクリプトが「空き状況読み取り」「申込履歴読み取り」のみで、**予約申込フローは未調査**。ASP.NET WebForms 特有の ViewState / EventValidation / `__doPostBack` ナビゲーション方式で、かでる（PHP）より技術的制約が多い。

実装着手前に探索してフロー全体の挙動を把握する必要があるため、本タスクを先行実施する。

### 方針
Playwright を用いた探索用スクリプトを作成し、ログインから「申込トレイ相当の画面（最終申込ボタン押下直前）」まで自動的に辿り、各ステップの HTML / URL / form 構造 / `__VIEWSTATE` などを詳細にログ出力・ファイル保存する。その結果を Markdown ドキュメントにまとめ、後続機能の要件定義のインプットとする。

## 2. ユーザーストーリー

### 対象ユーザー
- 将来の higashi-reservation-proxy 機能を実装する開発者（Claude Code 自身を想定）

### ユーザーの目的
探索結果ドキュメントを読めば、higashi-reservation-proxy 機能の要件定義・実装着手に必要な技術情報が揃っている状態を作る。

### 利用シナリオ
1. 開発者が higashi-reservation-proxy 機能の要件定義を始めようとする
2. 本探索タスクの成果物 `docs/features/higashi-reservation-flow-exploration/findings.md` を参照
3. ログインフロー / 各画面URL / ViewState の伝播挙動 / 申込トレイ画面の有無 / 完了検知条件 等が全て記載されている
4. これを元に迷わず要件定義と実装に進める

### 探索のゴール地点
**「申込ボタン押下で本申込が確定する、その1つ前の画面」** まで辿って停止。実際の申込は**絶対に実行しない**。

### 基本探索シナリオ
- **会場**: 東区民センター（施設コード 103）
- **部屋**: さくら（和室 042）
- **日付**: 実行日から数日後の空きスロット
- **時間帯**: 夜間 18:00-21:00
- 可能であれば**かっこう（041）単独予約**の挙動も記録（通常運用では単独使用しないが、実装可否判断のため）

### 成果物が答えるべき問い（実装可否判断）
- 東区民センターに「申込トレイ」相当画面は存在するか?
- ViewState / EventValidation は Java 側から抽出・注入可能な形式か? (暗号化レベル、長さ、依存関係)
- リバースプロキシ方式は技術的に実現可能か? 不可能と判明した場合は別方式（リモートブラウザ等）に切り替え判断

### 禁止事項（安全ガード）
- **実際の予約を完了させる**（申込確定ボタン押下）
- 既存の予約データを改変（キャンセル・変更）
- マイページのパスワード変更・個人情報変更
- 同一アカウントでの高頻度ログイン試行（セッション破棄やBAN回避のため）
- 短時間連続アクセス（レート制限回避のため、ステップ間に適切な待機を入れる）

## 3. 機能要件

### 3.1 実行環境
- **ローカル環境のみで手動実行**。GitHub Actions 等での自動実行は対応しない。
- ユーザーが `node scripts/room-checker/explore-higashi-reservation.js --confirm-no-submit` を実行して動かす。
- ブラウザは `headless: false` 固定。ユーザーが画面を目視確認できる状態で動く。

### 3.2 スクリプト配置
- `scripts/room-checker/explore-higashi-reservation.js`

### 3.3 CLI インターフェース

#### 3.3.1 必須フラグ
- `--confirm-no-submit`: このフラグが無ければ即時 `process.exit(1)`。事故的な実申込を防ぐためのガード。

#### 3.3.2 オプションフラグ
- `--room <さくら|かっこう>`: 探索対象の部屋（デフォルト: さくら）
- `--date <YYYY-MM-DD>`: 探索対象の日付（デフォルト: 実行日から3日後）
- `--slot <morning|afternoon|night>`: 時間帯（デフォルト: night）
- `--output-dir <path>`: 出力先ディレクトリ（デフォルト: `scripts/room-checker/exploration-output/higashi-reservation-{timestamp}/`）

### 3.4 環境変数
- `SAPPORO_COMMUNITY_USER_ID`（既存流用）
- `SAPPORO_COMMUNITY_PASSWORD`（既存流用）

### 3.5 探索ステップ

各ステップで以下を全て記録する：

1. ページURL（遷移後）
2. ページタイトル
3. 主要 form 要素の構造（`<input>` `<select>` `<button>` の name/id/type/value）
4. `__VIEWSTATE` / `__EVENTVALIDATION` / `__EVENTTARGET` / `__EVENTARGUMENT` 等の hidden fields の情報：
   - 文字列長
   - 先頭20文字 / 末尾20文字
   - SHA256 hash
   - 前ステップからの変化有無
5. フル HTML スナップショット
6. スクリーンショット（PNG）
7. レスポンスヘッダ（`content-type`, `set-cookie` のキー名のみ、値の機微情報は伏せる）
8. 現在の `document.cookie` の key 一覧（値は伏せる）

### 3.6 各ステップで実行する操作

| ステップ | 操作 |
|---------|-----|
| 01 | ログインページ（UserLogin.aspx）を開く |
| 02 | ユーザーID/パスワード入力 → 「ログイン」ボタンクリック |
| 03 | ログイン成功確認（メニュー画面到達） |
| 04 | 施設・部屋を指定して空き状況画面まで遷移（既存スクリプト `sync-higashi-availability-to-db.js` のロジック流用） |
| 05 | 対象日付・スロットの `○` セルをクリック（`newLinkClick(...)` ハンドラ発火） |
| 06 | 遷移先画面を観察・記録 |
| 07 | 画面内の「次へ進む」「確定」ボタンに相当するものを1ステップずつクリックして進む |
| 08 | **申込確定ボタンの手前で停止**。ボタンの selector は記録するがクリックしない |

### 3.7 成果物

#### 3.7.1 実行時出力
```
scripts/room-checker/exploration-output/higashi-reservation-YYYYMMDD-HHMMSS/
  ├─ step-01-login.html
  ├─ step-01-login.png
  ├─ step-01-login.json
  ├─ step-02-login-submit.html
  ├─ ... 以降各ステップごとに3ファイルセット
  └─ summary.json (全ステップのメタ情報を集約)
```

#### 3.7.2 最終ドキュメント
`docs/features/higashi-reservation-flow-exploration/findings.md`

- スクリプトは **raw data 記録（summary.json）まで** を担当
- findings.md の**分析と結論は Claude が summary.json と HTMLを読んで執筆**
- 構成:
  - 探索環境・実行日時
  - 各ステップの詳細（URL / DOM / form / ViewState）
  - 申込トレイ相当画面の有無と構造分析
  - ViewState / EventValidation の技術分析
  - 完了画面の特徴
  - 発生したエラー・ハマりどころ
  - **プロキシ実装時の推奨アプローチ**（リバースプロキシで実現可能か否かの判断 + 実装方針の提案）

### 3.8 安全ガード詳細
- `--confirm-no-submit` 必須（フラグなしなら `process.exit(1)`）
- 最終的な申込ボタンクリックはコード上に**書かない**（コメントで「ここに来たら手動でクリック」と示すのみ）
- 各ステップで「次のクリック対象のセレクタ・テキスト・位置」をログに出力し、想定外ボタンクリックを防止
- 探索対象日付が「実行日当日」の場合は警告表示（即時予約が発生するリスクを避けるため数日後を推奨）

### 3.9 エラーハンドリング
- ログイン失敗 → エラー詳細をログ + 該当 step の HTML/PNG を保存 + exit 1
- 想定外画面遷移 → その時点の情報を保存 + ユーザーにメッセージ表示 + exit 1
- 申込ボタンに辿り着けない（画面構造が想定と違う） → 詰まった時点までの情報を保存 + 成果物に記録

### 3.10 目標所要時間
- 1回の探索実行で **3分以内**で完了することを目標

## 4. 技術設計

### 4.1 スクリプト構成
- **単一ファイル** `scripts/room-checker/explore-higashi-reservation.js`
- 外部モジュール依存: `playwright`, `node:fs/promises`, `node:path`, `node:crypto`（SHA256用）
- 既存 [sync-higashi-availability-to-db.js](../../../scripts/room-checker/sync-higashi-availability-to-db.js) / [scrape-higashi-history.js](../../../scripts/room-checker/scrape-higashi-history.js) の以下を流用:
  - ログインフロー（scrape-higashi-history.js）
  - メニュー→施設→部屋→空き状況画面までの遷移（sync-higashi-availability-to-db.js）
  - `○` セルのDOM判定ロジック（sync-higashi-availability-to-db.js の `NIGHT_COL_INDEX`, `Available` クラス判定）

### 4.2 実行前チェック
スクリプト冒頭で以下を検証。1つでも NG なら即時 `process.exit(1)` + 明示的エラーメッセージ：

1. `--confirm-no-submit` フラグの存在
2. `SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` 環境変数の存在
3. 出力ディレクトリの書き込み権限
4. （Playwrightのインストール状態は require 時に検出されるので自動）

### 4.3 記録関数の構成

```javascript
async function recordStep(page, stepNumber, stepName) {
  const url = page.url();
  const title = await page.title();
  const html = await page.content();
  const screenshot = await page.screenshot({ fullPage: true });
  const forms = await page.evaluate(() => /* form構造取得 */);
  const viewState = await page.evaluate(() => /* __VIEWSTATE関連取得 */);
  const cookieKeys = /* document.cookie からkey一覧 */;

  // ファイル保存
  await fs.writeFile(`${outputDir}/step-${stepNumber}-${stepName}.html`, html);
  await fs.writeFile(`${outputDir}/step-${stepNumber}-${stepName}.png`, screenshot);
  const meta = {
    step: stepNumber,
    name: stepName,
    url, title, forms,
    viewState: {
      length: viewState.viewstate?.length,
      head20: viewState.viewstate?.substring(0, 20),
      tail20: viewState.viewstate?.slice(-20),
      sha256: viewState.viewstate ? sha256(viewState.viewstate) : null,
      changedFromPrevious: /* 前ステップと比較 */,
    },
    cookieKeys,
    timestamp: new Date().toISOString(),
  };
  await fs.writeFile(`${outputDir}/step-${stepNumber}-${stepName}.json`, JSON.stringify(meta, null, 2));
  summary.push(meta);

  console.log(`[step ${stepNumber}] ${stepName}: ${title} (${url})`);
  console.log(`  次の操作: ${nextActionHint}`);
}
```

### 4.4 ログ出力レベル
- `console.log` でステップ毎の進捗を表示
- 各ステップ終了時に「**待機中... 次の操作: <操作説明>**」をユーザーに表示して目視確認を促す
- ユーザーが確認したら次ステップへ進む（自動進行、待機タイマー2秒程度）

### 4.5 API・DB変更
- なし

### 4.6 成果物ドキュメント作成プロセス
1. スクリプト実行 → `exploration-output/` 配下に生データ出力
2. Claude が生データを読む（summary.json + 各 HTML + PNG）
3. Claude が `docs/features/higashi-reservation-flow-exploration/findings.md` を執筆
   - 分析観点: 「リバースプロキシで実現可能か」「ViewStateをJavaから扱えるか」「セッション一意制約の影響」「他会場への応用可能性」
   - 結論: 後続 higashi-reservation-proxy の実装推奨アプローチ

## 5. 影響範囲

### 5.1 新規作成ファイル
- `scripts/room-checker/explore-higashi-reservation.js`（探索スクリプト本体）
- `docs/features/higashi-reservation-flow-exploration/findings.md`（探索後にClaudeが執筆する分析ドキュメント）

### 5.2 変更ファイル
- `.gitignore` — `scripts/room-checker/exploration-output/` を追加

### 5.3 削除ファイル
- なし

### 5.4 既存機能への影響

| 項目 | 影響 |
|-----|-----|
| バックエンド（Spring Boot） | なし |
| フロントエンド（React） | なし |
| DBスキーマ | なし |
| API | なし |
| 既存スクリプト | 変更なし（参照のみ） |
| GitHub Actions | なし |
| Render環境変数 | なし（既存の `SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` を流用） |
| Node依存 | 既存の `playwright` をそのまま使用、追加依存なし |

本機能は**完全に独立した探索タスク**で、既存機能への影響ゼロ。

### 5.5 共通コンポーネント・ユーティリティへの影響
- なし

### 5.6 API・DBスキーマの互換性
- 変更なし（API/DBに触れない）

### 5.7 外部システム（sapporo-community.jp）への影響
- 探索は1回の予約フローを辿るだけで、通常のユーザー操作と区別がつかない
- サイト側負荷は無視できる水準（数リクエスト）
- 実際の申込は完了させないため、相手側のデータ状態への書き込み変更なし

### 5.8 認証情報のリスク
- 出力されるHTMLやスクリーンショットに機密情報（ユーザーID・セッション情報）が含まれる可能性
- `.gitignore` で `exploration-output/` を除外することで対応

## 6. 設計判断の根拠

### 6.1 なぜ探索タスクを先行させるか
東区民センターは既存スクリプトが読み取り系のみで、予約申込フローが未踏査。いきなりリバースプロキシ機能の要件定義に入ると、ViewState / EventValidation / 申込トレイの有無 等の前提が不明のまま机上設計することになり、実装着手後に仕様乖離で大きな手戻りが発生するリスクがある。探索で実態を把握した後に設計する方が堅実。

### 6.2 なぜ Playwright を使うか
既存の higashi スクリプト（`scrape-higashi-history.js` 等）が Playwright を使用しており、ログインフロー等のコードを流用できる。また Playwright は ASP.NET の `__doPostBack` / ViewState を自動的に処理してくれるため、探索段階ではこれらの詳細に踏み込まずに済む。

### 6.3 なぜ `headless: false` を固定するか
探索中にユーザー（人間）が画面を目視し、想定外の遷移や申込確定ボタンの暴発を即座に検知できるようにするため。完了時に「ここまで来た」ことを視覚的に確認できる意味も大きい。

### 6.4 なぜ `--confirm-no-submit` を必須フラグにするか
探索スクリプトの最大のリスクは「事故的な実申込」。フラグ必須にすることでスクリプトを何気なく `node ...` で実行しても起動しない構造にし、実行者に「申込はしない」意図を明示的に表明させる。コード上に申込確定ボタンのクリック処理を書かないことと合わせて、二重の安全装置になる。

### 6.5 なぜ ViewState を長さ + SHA256 + 先頭/末尾20文字で記録するか
ViewState は暗号化された長大な文字列で、フル保存すると出力ファイルが肥大化する。また内部にセッション情報が含まれる可能性があり、フル保存は機密情報露出リスク。一方、解析観点では「長さ」「ステップ間での変化有無」「先頭末尾の特徴的パターン」が分かれば十分。SHA256 で変化検出ができれば「ViewState は画面遷移ごとに更新される/されない」の挙動が判明し、プロキシ実装方針の決定に十分な情報が得られる。

### 6.6 なぜ findings.md の分析・結論を Claude が執筆するか
後続の higashi-reservation-proxy 機能の実装者（Claude）自身が分析・判断を行うことで、要件定義時に背景知識が直接つながる。スクリプトが機械的に生成するだけの summary よりも、実装意思決定に必要な観点（プロキシ実装可否、ViewState処理方針、セッション管理設計、リスクと回避策）での総合判断を含むドキュメントになる。

### 6.7 なぜ GitHub Actions 連携をしないか
探索は1回〜数回の実行で役目を終える使い捨てタスクで、自動化するメリットがない。むしろ `headless: false` で目視確認する価値が大きいため、ローカル実行に固定するのが合理的。

### 6.8 なぜ分割ファイルにせず単一スクリプトにするか
探索スクリプトは throw-away な性質で、長期保守を前提としない。モジュール化による再利用メリットよりも、1ファイルで全て読めるシンプルさのメリットが勝る。
