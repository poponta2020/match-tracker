# karuta-tracker-ui（React フロントエンド）

React 19 / Vite / Tailwind CSS v3。ソースルート: `src/`

## 構成

- `pages/` — 機能別ページ（matches / players / practice / pairings / lottery / notifications / venues / videos / mentor / settings ほか）。ルーティングは `App.jsx`
- `api/` — Axios クライアント。`client.js` が共通設定で、1リソース=1ファイル（例: `lottery.js`）。fetch の直書き禁止
- `components/` — 共通コンポーネント（Layout / PageHeader / モーダル類 / MatchCarousel）
- `context/` — AuthContext（認証）・BottomNavContext（ボトムナビ表示制御）
- `utils/` / `hooks/`

## 命名規約（ファイルパスは規約から推測できる）

- 画面: `pages/<機能>/<PascalCase>.jsx`。URL→コンポーネントの対応は `docs/SCREEN_LIST.md` が正典
- API 呼び出しは `api/<resource>.js` に集約

## 規約

- ユーザー向け文言は日本語
- lint: `npm run lint`／テスト: `npm run test`（スワイプ・カルーセル系のフレークは `vitest run --no-file-parallelism` で逐次実行して切り分け）
- デザイントークン: `tailwind.config.js`・`src/index.css`（claude.ai/design の「Match Tracker Design System」と対応）

機能→実装ファイルの対応は `docs/spec/<ドメイン>.md` 冒頭の「主要実装」を参照。
