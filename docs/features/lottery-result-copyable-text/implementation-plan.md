---
status: completed
---
# 抽選結果コピー可能テキスト出力 実装手順書

## 実装タスク

### タスク1: テキスト整形ヘルパーの実装
- [x] 完了
- **概要:** `LotteryResultDto[]` を要件定義 3.3 の出力フォーマットへ整形する純粋関数群を実装する。データ取得や副作用は持たず、入出力だけのロジック。
- **実装メモ:** ESLint の `react-refresh/only-export-components`（error）に抵触するため、`LotteryResults.jsx` 同一ファイルではなく隣接する `lotteryResultText.js` に切り出して実装した。タスク2で `import` して使用する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/lotteryResultText.js` — 新規作成し、以下のヘルパーを export
    - `getJapaneseWeekday(date: Date): string` — `月`〜`日` の1文字曜日
    - `formatSessionHeader(sessionDate, venueName): string` — `M/D（曜）会場名` 形式（`venueName` が null/空のときは `会場未設定`）
    - `buildCopyText(year: number, month: number, sessions: LotteryResultDto[]): string` — 月ヘッダー + セッション群 + 末尾文言を結合した完成テキストを返す
      - セッション内の全試合で `WAITLISTED` 0名のセッションは丸ごとスキップ
      - 試合内 `WAITLISTED` 0名は `（なし）` 1行
      - `WAITLISTED` のみ抽出し、`waitlistNumber` 昇順
    - `hasAnyWaitlisted(sessions): boolean` — 月全体で WAITLISTED が1名以上いるか（コピーボタン disabled 判定）
- **依存タスク:** なし
- **対応Issue:** #606

### タスク2: 抽選確定済フラグ取得とコピー領域UIの追加
- [ ] 完了
- **概要:** `LotteryResults.jsx` に管理者向けコピー領域（textarea + コピーボタン）を追加する。表示条件は「ADMIN または SUPER_ADMIN」かつ「当該月が抽選確定済」。textarea の初期値はタスク1の `buildCopyText` で生成し、月切替・再フェッチ時に上書きする。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx`
    - 状態追加: `isConfirmed: boolean`、`copyText: string`
    - `useEffect` で `lotteryAPI.isConfirmed(year, month, organizationId)` を呼び出し、結果を `isConfirmed` にセット
      - `organizationId` は `AuthContext.currentPlayer.adminOrganizationId`（既存のキー名は実装時に確認）から取得
      - PLAYER ロールはこのAPI呼び出し自体をスキップ（`isConfirmed` は常に `false` 扱い）
    - `fetchResults` の完了時に `buildCopyText` を呼び `setCopyText` を実行
    - 月切替時にも上記が走るよう、`useEffect` の依存配列を整える
    - JSX: 既存の `results.map(...)` セクションの **下** に管理者向けコピー領域を追加
      - 表示条件: `(role === 'ADMIN' || role === 'SUPER_ADMIN') && isConfirmed`
      - `<textarea>` を `copyText` にバインド（`value`/`onChange`）
      - 直下に `<button>コピー</button>`
        - `onClick`: `navigator.clipboard.writeText(copyText)` → 成功で「コピーしました」、失敗で「コピーに失敗しました」
        - `disabled` 条件: `!hasAnyWaitlisted(results)`
      - フィードバック表示はプロジェクト既存のトースト実装があれば利用、なければ短時間の inline メッセージ表示で実現
- **依存タスク:** タスク1
- **対応Issue:** #607

### タスク3: ドキュメント更新
- [ ] 完了
- **概要:** `docs/SCREEN_LIST.md` の `/lottery/results` 画面説明に管理者向けコピーテキスト機能の存在を追記する。`docs/SPECIFICATION.md` も該当機能セクションに簡潔に追記する。`docs/DESIGN.md` は API 追加・変更がないため原則不要だが、既存記載があれば併せて確認。
- **変更対象ファイル:**
  - `docs/SCREEN_LIST.md` — 抽選結果画面の機能一覧に「管理者向けコピーテキスト出力（LINE告知用）」を追記
  - `docs/SPECIFICATION.md` — 抽選結果機能のセクションに本機能の概要追記
  - `docs/DESIGN.md` — 既存記載状況を確認し、必要に応じてフロントエンド側の追記のみ
- **依存タスク:** タスク2（実装完了後に最終内容で記載）
- **対応Issue:** #608

## 実装順序

1. タスク1（テキスト整形ヘルパー、依存なし）
2. タスク2（コピー領域UI、タスク1に依存）
3. タスク3（ドキュメント更新、タスク2に依存）
