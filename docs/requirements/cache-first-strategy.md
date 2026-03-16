# キャッシュファースト戦略 要件定義書

## 1. 概要

### 1.1 目的
画面遷移時の体感速度を向上させるため、フロントエンドにキャッシュファースト戦略（Stale-While-Revalidate）を導入する。

### 1.2 背景
- 現状、全ての画面遷移で毎回サーバーにAPIリクエストが飛び、レスポンスが返るまでスピナーが表示される
- 「戻る」操作で同じ画面を再表示する際も、フルフェッチが走る
- 選手一覧など複数画面で使う共通データが、画面ごとに独立して取得されている
- Render無料プランのコールドスタート（最大30秒以上）も相まって、初回アクセス時の体験が悪い

### 1.3 用語定義
| 用語 | 説明 |
|------|------|
| キャッシュファースト | 一度取得したデータをキャッシュに保持し、次回アクセス時はキャッシュを即座に表示する戦略 |
| Stale-While-Revalidate（SWR） | キャッシュを即表示しつつ、バックグラウンドでサーバーから最新データを取得し、差分があればUIを更新するパターン |
| staleTime | キャッシュを「新鮮」とみなす時間。この期間内はサーバーへのリクエストを飛ばさない |
| gcTime | キャッシュをメモリに保持する時間。この期間を過ぎるとガベージコレクションされる |
| invalidation | 書き込み操作の成功後に、関連するキャッシュを明示的に無効化（再取得を促す）すること |
| QueryKey | キャッシュの識別子。エンドポイント＋パラメータの組み合わせで一意に定まる |

---

## 2. 現状分析

### 2.1 画面別のAPIコール状況

#### ホーム画面（`/`）
- `GET /matches/player/{id}?limit=5`（最近の試合5件）
- `GET /practice-sessions/next-participation`（次の練習）
- `GET /practice-sessions/participations/player/{id}`（月間参加情報）
- `GET /matches/player/{id}/period/count`（月間試合数）
- 上記4本をPromise.allで並列取得
- ウィンドウフォーカス時に手動で再取得（addEventListener）

#### 試合一覧（`/matches`）
- `GET /matches/player/{id}`（フィルタ付き試合一覧）
- `GET /matches/player/{id}/statistics-by-rank`（級別統計）
- `GET /players`（選手検索フォーカス時に遅延取得、useRefで1回のみ）

#### 練習カレンダー（`/practice`）
- `GET /practice-sessions/year-month/summary`（月間練習サマリー）
- `GET /practice-sessions/participations/player/{id}`（月間参加情報）
- 月切り替え時に再取得
- セルクリック時に `GET /practice-sessions/{id}`（練習詳細）

#### 組み合わせ作成（`/pairings`）
- `GET /match-pairings/exists`（試合番号ごとの存在チェック）
- `GET /match-pairings/date-and-match`（組み合わせ取得）
- `GET /match-pairings/pair-history`（ペアの対戦履歴、ペアごとに1回）
- `GET /players`（選手一覧、オンデマンド取得）
- useRefによる部分的なキャッシュあり

#### その他の画面
- マイページ: `GET /players/{id}`
- 試合詳細: `GET /matches/{id}`
- 試合結果一括入力: `GET /practice-sessions/{id}` + `GET /match-pairings/date-and-match`
- 練習参加登録: `GET /practice-sessions/year-month/summary` + `GET /practice-sessions/participations/player/{id}`
- 選手管理: `GET /players`
- 会場管理: `GET /venues`

### 2.2 重複取得されているデータ

| データ | 取得される場面 | 現状 |
|--------|--------------|------|
| 選手一覧（`GET /players`） | 試合一覧、組み合わせ作成、参加者編集モーダル、選手管理 | 各画面で独立取得 |
| 月間参加情報（`GET .../participations/player/{id}`） | ホーム、練習カレンダー、練習参加登録 | 同じ月のデータを別々に取得 |
| 練習サマリー（`GET .../year-month/summary`） | 練習カレンダー、練習参加登録 | 同じ月のデータを別々に取得 |

### 2.3 既存のキャッシュ機構

| 箇所 | 方法 | 対象 |
|------|------|------|
| PairingGenerator | useRef（pairingsCache） | 試合番号ごとの組み合わせ |
| MatchList | useRef（playersLoadedRef） | 選手一覧の取得済みフラグ |
| Home | window focusイベント | フォーカス時に全データ再取得 |

いずれもコンポーネントのアンマウントで消失し、画面間での共有はされていない。

---

## 3. 要求事項

### 3.1 機能要件

#### F-1: Stale-While-Revalidateによるデータ取得
- 一度取得したAPIレスポンスをメモリキャッシュに保持する
- 再度同じデータが必要になった際、キャッシュを即座にUIに表示する
- バックグラウンドでサーバーから最新データを取得し、差分があればUIを自動更新する
- キャッシュが「新鮮」な期間（staleTime）内は、サーバーへのリクエストを送信しない

#### F-2: リクエスト重複排除
- 同一のQueryKeyを持つリクエストが複数のコンポーネントから同時に発行された場合、1回のみサーバーにリクエストを送信し、結果を全コンポーネントに配信する

#### F-3: 書き込み後のキャッシュ無効化
- データの作成・更新・削除が成功した後、関連するキャッシュを無効化し、次回アクセス時に最新データが取得されるようにする

#### F-4: ウィンドウフォーカス時の自動再取得
- ブラウザタブを切り替えて戻った際、staleTimeを超えたデータを自動的に再取得する
- 現在ホーム画面にのみ手動実装されている機能を、全画面に統一的に適用する

#### F-5: 画面間でのキャッシュ共有
- 選手一覧などの共通データを、画面をまたいで共有する
- 画面Aで取得した選手一覧を、画面Bでも利用可能にする

### 3.2 非機能要件

| 項目 | 要件 |
|------|------|
| バックエンド変更 | なし（既存APIをそのまま利用） |
| キャッシュの保存先 | メモリのみ（ブラウザリロードでクリア） |
| 既存UIの変更 | スピナー表示のタイミングが変わる以外、見た目の変更なし |
| 既存機能の動作 | 全ての既存機能が変わらず動作すること |
| エラーハンドリング | 既存のエラー表示がそのまま機能すること |
| バンドルサイズ影響 | TanStack Query追加分（約12KB gzip）のみ |

---

## 4. 設計

### 4.1 導入ライブラリ

**TanStack Query v5（旧React Query）**

選定理由:
- Stale-While-Revalidate、リクエスト重複排除、ウィンドウフォーカス再取得が標準搭載
- React 19対応済み
- 広く採用されており、ドキュメント・エコシステムが充実
- 軽量（gzip約12KB）

### 4.2 データ分類とキャッシュ設定

データの変更頻度と利用パターンに基づき、3カテゴリに分類する。

#### カテゴリA: マスタデータ（変更頻度: 低）

管理者が管理する参照データ。日常的にはほぼ変更されない。

| データ | QueryKey | staleTime | gcTime |
|--------|----------|-----------|--------|
| 選手一覧 | `['players']` | 5分 | 30分 |
| 選手詳細 | `['players', id]` | 5分 | 30分 |
| 会場一覧 | `['venues']` | 10分 | 30分 |
| 会場詳細 | `['venues', id]` | 10分 | 30分 |

refetchOnWindowFocus: **false**（変更頻度が低いため不要）

#### カテゴリB: 業務データ（変更頻度: 中）

練習日程や参加情報など、練習日前後に更新される。

| データ | QueryKey | staleTime | gcTime |
|--------|----------|-----------|--------|
| 練習サマリー（月別） | `['practice-sessions', 'summary', year, month]` | 30秒 | 5分 |
| 練習詳細 | `['practice-sessions', id]` | 30秒 | 5分 |
| 参加情報（月別） | `['participations', playerId, year, month]` | 30秒 | 5分 |
| 次の練習 | `['next-participation', playerId]` | 30秒 | 5分 |
| 対戦組み合わせ | `['pairings', date, matchNumber]` | 30秒 | 5分 |
| 組み合わせ存在チェック | `['pairings', 'exists', date, matchNumber]` | 30秒 | 5分 |

refetchOnWindowFocus: **true**

#### カテゴリC: ユーザー固有データ（変更頻度: 練習日のみ高）

個人の試合結果や統計。練習日に集中して更新される。

| データ | QueryKey | staleTime | gcTime |
|--------|----------|-----------|--------|
| 試合一覧（プレイヤー別） | `['matches', 'player', playerId, params]` | 1分 | 10分 |
| 試合詳細 | `['matches', id]` | 1分 | 10分 |
| 級別統計 | `['matches', 'statistics-by-rank', playerId, params]` | 1分 | 10分 |
| 月間試合数 | `['matches', 'count', playerId, startDate, endDate]` | 1分 | 10分 |
| 対戦履歴（ペア別） | `['pairings', 'pair-history', player1Id, player2Id]` | 1分 | 5分 |

refetchOnWindowFocus: **true**

### 4.3 キャッシュ無効化マッピング

書き込み操作が成功した際に、以下のキャッシュを無効化する。

| 操作 | 無効化するQueryKey |
|------|-------------------|
| **試合結果の入力** | `['matches', 'player', *]`, `['matches', 'statistics-by-rank', *]`, `['matches', 'count', *]` |
| **試合結果の編集** | 上記に加えて `['matches', id]` |
| **試合結果の削除** | 上記と同じ |
| **練習日程の作成/編集** | `['practice-sessions', *]`, `['next-participation', *]` |
| **練習日程の削除** | 上記と同じ |
| **参加登録の保存** | `['participations', *]`, `['practice-sessions', 'summary', *]` |
| **組み合わせの保存/削除** | `['pairings', date, *]` |
| **選手の登録/編集/削除** | `['players']`, `['players', id]` |
| **会場の登録/編集/削除** | `['venues']`, `['venues', id]` |
| **プロフィールの編集** | `['players', id]` |
| **Densuke同期** | `['practice-sessions', 'summary', *]`, `['participations', *]` |

### 4.4 QueryClient デフォルト設定

```
defaultOptions:
  queries:
    staleTime: 30秒（デフォルト。各クエリで上書き可能）
    gcTime: 5分
    refetchOnWindowFocus: true
    retry: 1（1回リトライ。コールドスタートの初回失敗を救済）
    retryDelay: 1000ms
```

### 4.5 カスタムhooks構成

既存のAPI呼び出しをラップするカスタムhooksを作成する。

```
src/hooks/
├── usePlayersQuery.js       # 選手一覧、選手詳細
├── useMatchesQuery.js       # 試合一覧、試合詳細、統計
├── usePracticeQuery.js      # 練習サマリー、練習詳細、参加情報
├── usePairingsQuery.js      # 組み合わせ、対戦履歴
├── useVenuesQuery.js        # 会場一覧、会場詳細
└── useMutations.js          # 書き込み操作（invalidation込み）
```

### 4.6 移行対象の画面と影響範囲

| 画面 | 変更内容 | 影響度 |
|------|---------|--------|
| **Home** | useEffect → useQuery×4に置換。手動focus再取得を削除 | 中 |
| **MatchList** | useEffect → useQuery×2に置換。useRefキャッシュを削除 | 中 |
| **MatchDetail** | useEffect → useQueryに置換 | 小 |
| **MatchForm** | 書き込み部分をuseMutationに置換 | 小 |
| **PracticeList** | useEffect → useQuery×2に置換 | 中 |
| **PracticeDetail** | useEffect → useQueryに置換 | 小 |
| **PracticeForm** | 書き込み部分をuseMutationに置換 | 小 |
| **PracticeParticipation** | useEffect → useQuery×2、保存をuseMutationに | 中 |
| **PairingGenerator** | useRefキャッシュ → useQueryに統合 | 大 |
| **BulkResultInput** | 書き込み部分をuseMutationに置換 | 小 |
| **MatchResultsView** | useEffect → useQueryに置換 | 小 |
| **PlayerList** | useEffect → useQueryに置換 | 小 |
| **PlayerDetail** | useEffect → useQueryに置換 | 小 |
| **PlayerEdit** | 書き込み部分をuseMutationに置換 | 小 |
| **VenueList** | useEffect → useQueryに置換 | 小 |
| **VenueForm** | 書き込み部分をuseMutationに置換 | 小 |
| **Profile** | useEffect → useQueryに置換 | 小 |
| **ProfileEdit** | useEffect → useQuery、保存をuseMutationに | 小 |
| **Login/Register** | 変更なし（書き込みのみ、キャッシュ対象外） | なし |

---

## 5. 実装作業リスト

| # | 作業 | 概要 |
|---|------|------|
| 1 | TanStack Queryのインストール | `npm install @tanstack/react-query` |
| 2 | QueryClientProvider設定 | main.jsx または App.jsx にProviderを追加、デフォルト設定を定義 |
| 3 | QueryKey定数の定義 | 全QueryKeyを一箇所にまとめて管理する定数ファイルを作成 |
| 4 | カスタムhooksの作成 | 各APIモジュールに対応するuseQuery/useMutationのhooks |
| 5 | カテゴリA（マスタデータ）の移行 | PlayerList, VenueList, 選手検索等 |
| 6 | カテゴリB（業務データ）の移行 | PracticeList, PracticeParticipation, PairingGenerator等 |
| 7 | カテゴリC（ユーザー固有データ）の移行 | Home, MatchList, MatchDetail等 |
| 8 | 書き込み操作のuseMutation化 | 全POST/PUT/DELETE操作のinvalidation設定 |
| 9 | 既存キャッシュ機構の削除 | useRefキャッシュ、手動focus再取得の削除 |
| 10 | 動作確認・staleTime調整 | 全画面の遷移パターンを確認、キャッシュ設定の微調整 |

---

## 6. 期待される効果

### 6.1 画面遷移の体感速度

| シナリオ | 現状 | 導入後 |
|---------|------|--------|
| ホーム → 試合一覧 → ホームに戻る | 毎回4本のAPIコール + スピナー | キャッシュから即表示（0ms）。裏で最新化 |
| 試合一覧で選手検索のフォーカス | 選手リストをフルフェッチ（数百ms） | キャッシュ済みなら即表示 |
| 練習カレンダーの月切り替え → 元の月に戻る | 再度サーバーから取得 | 前月のデータはキャッシュから即表示 |
| 試合詳細 → 戻る → 別の試合詳細 → 戻る → 最初の試合 | 毎回フェッチ | 既に見た試合はキャッシュから |
| 組み合わせ作成で試合番号を切り替え → 戻す | useRefキャッシュ（既存） | TanStack Queryに統合、画面遷移しても保持 |
| ブラウザタブを切り替えて戻る | ホーム画面のみ再取得 | 全画面でstale判定後に自動再取得 |

### 6.2 APIリクエスト数の削減

| シナリオ | 現状のリクエスト数 | 導入後 |
|---------|------------------|--------|
| 選手一覧を3画面で使用 | 3回 | 1回（キャッシュ共有） |
| 月間参加情報を3画面で使用 | 3回 | 1回（同一月の場合） |
| ホーム→他画面→ホーム（1分以内） | 8回（往復分） | 4回（staleTime内はスキップ） |

---

## 7. 認識合わせが必要な事項

以下の点について、方針の確認をお願いします。

### 7.1 キャッシュの永続化について

**現在の方針**: メモリキャッシュのみ（ブラウザリロードでクリア）

- リロードするとキャッシュは全て消え、次回アクセス時にサーバーから再取得する
- IndexedDB等への永続化は行わない
- リロードは頻繁ではないため、体感速度への影響は限定的

**確認事項**: これで問題ないか。もしリロード後もキャッシュを残したい場合は、TanStack Queryの`persistQueryClient`プラグインでIndexedDBに保存する拡張が可能（追加工数：小）

### 7.2 staleTimeの設定値について

**現在の方針**:

| カテゴリ | staleTime | 意味 |
|---------|-----------|------|
| マスタデータ（選手・会場） | 5分 | 5分間は同じデータを使い回し、サーバーに聞きに行かない |
| 業務データ（練習・参加情報） | 30秒 | 30秒以内の再アクセスではサーバーに聞きに行かない |
| ユーザー固有データ（試合結果） | 1分 | 1分以内の再アクセスではサーバーに聞きに行かない |

**トレードオフ**: staleTimeが長いほど体感は速いが、データの鮮度が下がる。ただし、自分自身の書き込み操作の後は即座にinvalidateするため、「自分の操作が反映されない」ということは起きない。影響があるのは「他の人が更新したデータが反映されるまでの遅延」のみ。

**確認事項**: この設定値の感覚は妥当か。練習中に他の人の入力がリアルタイムで見えなくても30秒〜1分程度の遅延は許容できるか。

### 7.3 PairingGeneratorの移行について

PairingGeneratorは現在useRefで独自のキャッシュ機構を持っており、試合番号の切り替え時にキャッシュを活用している。TanStack Queryに置き換えると：

- 利点: 画面遷移してもキャッシュが残る、他の画面と統一的に管理できる
- リスク: 既存の複雑なキャッシュロジック（組み合わせの保存前チェック等）との整合性を慎重に確認する必要がある

**確認事項**: PairingGeneratorも含めて一括で移行するか、PairingGeneratorは既存のuseRefキャッシュを維持して後回しにするか。

### 7.4 バックエンド側のHTTPキャッシュヘッダーについて

**現在の方針**: バックエンド変更なし（フロントエンドのみで完結）

TanStack Queryのキャッシュはフロントエンドのメモリ上で動作するため、サーバー側のCache-Controlヘッダーがなくても機能する。ただし、将来的にサーバー側にもCache-Controlヘッダーを追加すれば、ブラウザのHTTPキャッシュとの二段構えになり、さらに高速化できる。

**確認事項**: 今回はフロントエンドのみで進めてよいか。バックエンド側のCache-Controlは将来対応としてよいか。

### 7.5 DevToolsの導入について

TanStack Queryには開発用のDevToolsパネル（`@tanstack/react-query-devtools`）があり、キャッシュの状態をリアルタイムで可視化できる。本番ビルドでは自動的に除外される。

**確認事項**: 開発効率のためDevToolsも合わせて導入してよいか。

### 7.6 エラー時のリトライについて

**現在の方針**: 1回リトライ（retryDelay: 1000ms）

Renderの無料プランではコールドスタート時にリクエストがタイムアウトすることがある。1回のリトライを入れることで、コールドスタート中の初回失敗を救済できる。

**確認事項**: リトライ1回で十分か。コールドスタート対策としてリトライ回数を増やす（例: 2回）ほうが良いか。

---

## 8. スコープ外（今回は対応しないもの）

| 項目 | 理由 |
|------|------|
| Service Worker / PWA化 | キャッシュファーストとは別施策。将来検討 |
| IndexedDBへの永続化 | メモリキャッシュで十分な効果が見込める。7.1の確認結果次第で追加可能 |
| バックエンドのHTTPキャッシュヘッダー | フロントエンドのみで効果が出る。将来追加可能 |
| オフライン対応 | ネットワーク切断時の書き込みキューイング等は今回対象外 |
| Viteのコード分割 | 別施策として独立して実施可能。今回のスコープには含めない |
| サーバー側のキャッシュ（Redis等） | インフラ変更を伴うため対象外 |

---

## 9. リスクと対策

| リスク | 影響 | 対策 |
|--------|------|------|
| キャッシュとサーバーの不整合 | 古いデータが表示される | staleTimeを適切に設定＋書き込み後にinvalidate＋ウィンドウフォーカスで再取得 |
| PairingGeneratorの移行で不具合 | 組み合わせ作成機能に影響 | 7.3の判断次第で後回し可能 |
| TanStack Queryの学習コスト | 今後の保守時に理解が必要 | カスタムhooksで抽象化し、使う側はシンプルに保つ |
| バンドルサイズ増加 | 初回ロード時間への影響 | 約12KB（gzip）の増加で実用上無視できる |

---

*作成日: 2026-03-16*
*ステータス: レビュー待ち*
