---
status: completed
---
# Settings Page 要件定義書（ドラフト）

## 1. 概要
- **目的:** ハンバーガーメニューに散在していた設定・管理画面への導線を、専用のSettingsページにまとめることで、アクセスしやすく視認性の高いUIにする
- **背景:** 現在、各種設定・管理画面への遷移はホーム画面のハンバーガーメニューに集約されているが、メニューを開かないと項目が見えず、発見性が低い

## 2. ユーザーストーリー
- **対象ユーザー:** 全ユーザー（一般ユーザー、Admin、Super Admin）
- **ユーザーの目的:** 設定・管理画面に素早くアクセスしたい
- **利用シナリオ:**
  1. ボトムナビの「Settings」タブをタップ
  2. Settingsページが開き、アイコングリッド（5行×3列のランチャースタイル）で各画面への遷移先が表示される
  3. ロールに応じて表示される項目が異なる（権限のない項目は非表示）
  4. アイコンをタップして目的の画面に遷移
- **トップヘッダーの変更:**
  - ハンバーガーメニューを完全削除
  - 通知ベルアイコン（既存）+ ユーザーアイコン（新規追加、プロフィール画面へ遷移）のみに簡素化

## 3. 機能要件

### 3.1 Settingsページ画面仕様

**レイアウト:**
- トップナビ: 左に「Settings」タイトル、右上にログアウトアイコン（Lucide `LogOut`）
- メインエリア: 3列グリッド（ランチャースタイル）で上から左→右に詰めて表示

**グリッドアイテムのスタイル:**
- 丸or角丸の背景色付きエリア（アプリテーマカラー `#4a6b5a` 系で統一）にLucideアイコン
- アイコン下にラベルテキスト
- タップで対応画面に遷移

**グリッド項目一覧（ログアウト除く、ロール別表示制御）:**

| # | ラベル | アイコン | 遷移先 | 全員 | Admin | SuperAdmin |
|---|--------|----------|--------|------|-------|------------|
| 1 | プロフィール | User | /profile | o | o | o |
| 2 | 参加練習会 | Building2 | /settings/organizations | o | o | o |
| 3 | 通知設定 | MessageSquare | /settings/notifications | o | o | o |
| 4 | Googleカレンダー同期 | RefreshCw | （OAuth連携アクション） | o | o | o |
| 5 | 練習日登録 | Calendar | /practice/new | - | o | o |
| 6 | 組み合わせ作成 | Shuffle | /pairings | - | o | o |
| 7 | LINE通知スケジュール | MessageSquare | /admin/line/schedule | - | o | o |
| 8 | 伝助管理 | ClipboardList | /admin/densuke | - | o | o |
| 9 | システム設定 | Settings | /admin/settings | - | o | o |
| 10 | 選手管理 | Users | /players | - | - | o |
| 11 | 会場管理 | MapPin | /venues | - | - | o |
| 12 | LINEチャネル管理 | MessageSquare | /admin/line/channels | - | - | o |

- ロールに応じて項目を非表示（グレーアウトではない）
- 一般ユーザー: 4項目（2行）、Admin: 9項目（3行）、SuperAdmin: 12項目（4行）

**ログアウト:**
- Settingsページのトップナビ右上にLogOutアイコンとして配置
- タップで認証情報クリア → `/login` へ遷移

### 3.2 トップヘッダー変更仕様

**変更後の構成:**
- 左側: プレイヤー名（既存のまま）
- 右側: 通知ベルアイコン（既存）+ ユーザーアイコン（新規、Lucide `User`、タップで `/profile` へ遷移）
- ハンバーガーメニュー（Menu アイコン + ドロップダウン）を完全削除

### 3.3 ボトムナビ変更仕様

**変更後の構成（5タブ）:**
| # | ラベル | アイコン | 遷移先 |
|---|--------|----------|--------|
| 1 | Home | Home | / |
| 2 | Match | Swords | /matches/results |
| 3 | Schedule | Calendar | /practice |
| 4 | Record | BarChart3 | /matches |
| 5 | Settings | Settings | /settings |

- Settingsタブは右端（5番目）に追加
- アクティブ判定: pathname が `/settings` で始まる場合

### 3.4 ビジネスルール
- Googleカレンダー同期はページ遷移ではなくOAuth連携フローをそのまま実行
- ロールベースの表示制御は現在のハンバーガーメニューと同一ロジック
- ログアウト処理は現在のハンバーガーメニューと同一ロジック

## 4. 技術設計

### 4.1 API設計
- API・DBの変更なし（フロントエンドのみの変更）

### 4.2 DB設計
- 変更なし

### 4.3 フロントエンド設計

**新規ファイル:**
- `src/pages/SettingsPage.jsx` — Settingsページ本体
  - 独自のトップナビ（「Settings」タイトル + LogOutアイコン）
  - 3列グリッドでアイコン項目を表示
  - ロールに応じた項目フィルタリング
  - Googleカレンダー同期のOAuthフロー呼び出し

**変更ファイル:**
- `src/App.jsx`
  - `/settings` ルート追加（SettingsPage）
  - 既存の `/settings/organizations`, `/settings/notifications` はそのまま維持
- `src/components/Layout.jsx`
  - ボトムナビに5番目のSettingsタブ追加（`w-1/4` → `w-1/5`）
  - アイコン: Lucide `Settings`、ラベル: "Settings"
  - アクティブ判定: `pathname === '/settings'`（子ルートと区別するためexact match）
- `src/components/NavigationMenu.jsx`
  - ハンバーガーメニュー（Menuアイコン + ドロップダウン全体）を削除
  - ユーザーアイコン（Lucide `User`）を追加、タップで `/profile` へ遷移
  - 通知ベルアイコンは既存のまま維持
  - コンポーネント名は `NavigationMenu` のまま（ヘッダーバーとして継続使用）

**コンポーネント構成（SettingsPage）:**
```
SettingsPage
├── ヘッダー（Settings タイトル + LogOut アイコン）
└── グリッドコンテナ（grid grid-cols-3）
    └── SettingsGridItem × N
        ├── アイコン（丸/角丸背景 + Lucideアイコン）
        └── ラベルテキスト
```

**状態管理:**
- 既存の認証コンテキスト（useAuth等）からユーザーロールを取得
- Googleカレンダー同期の状態は既存ロジックを流用

### 4.4 バックエンド設計
- 変更なし

## 5. 影響範囲

**変更が必要な既存ファイル:**
| ファイル | 変更内容 |
|----------|----------|
| `src/App.jsx` | `/settings` ルート追加 |
| `src/components/Layout.jsx` | ボトムナビに5番目タブ追加、幅調整 |
| `src/components/NavigationMenu.jsx` | ハンバーガーメニュー削除、ユーザーアイコン追加 |

**既存機能への影響:**
- `/settings/organizations` と `/settings/notifications` の既存ルートは影響なし
- トップヘッダーからハンバーガーメニューが消えるため、全画面でヘッダー表示が変わる
- ボトムナビが4タブ→5タブになり、各タブの横幅がやや狭くなる

## 6. 設計判断の根拠

- **フロントエンドのみの変更:** 既存のルート・API・DBには手を加えず、UIの導線変更のみで実現できるため
- **NavigationMenu.jsxのリファクタリング:** ハンバーガーメニューのロジック（項目定義・ロール判定・Googleカレンダー同期）をSettingsPageに移植し、NavigationMenuからは削除する
- **ボトムナビのアクティブ判定:** `/settings` のexact matchにすることで、`/settings/organizations` 等の子ルートではSettingsタブがアクティブにならないようにする（子ルートは設定画面の中の個別ページなので、ボトムナビ上ではSettingsがアクティブで問題ないが、厳密な判定を行う）
- **Settingsページのルートパス `/settings`:** 既存の `/settings/*` 子ルートとの親子関係が自然
