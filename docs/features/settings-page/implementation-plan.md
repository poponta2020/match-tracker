---
status: completed
---
# Settings Page 実装手順書

## 実装タスク

### タスク1: SettingsPageコンポーネント新規作成
- [x] 完了
- **概要:** Settingsページ本体を作成。独自ヘッダー（「Settings」タイトル + LogOutアイコン）、3列グリッドでアイコン項目を表示。ロールに応じた表示制御。Googleカレンダー同期のOAuthロジックをNavigationMenuから移植。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx` — 新規作成
    - 独自ヘッダー: 左に「Settings」タイトル、右にLogOutアイコン
    - 3列グリッド（`grid grid-cols-3`）でアイコン項目を表示
    - 各アイテム: 角丸背景（`#4a6b5a`系）+ Lucideアイコン + ラベルテキスト
    - ロール判定: `isAdmin()`, `isSuperAdmin()` で表示制御
    - Googleカレンダー同期: NavigationMenuの `handleCalendarSync`, `executeSyncWithToken`, OAuth戻り処理を移植
    - ログアウト: `logout()` + `navigate('/login')` をヘッダー右のLogOutアイコンに配置
    - カレンダー同期メッセージ表示（成功/エラー）も移植
- **依存タスク:** なし
- **対応Issue:** #142

### タスク2: App.jsxにルート追加
- [x] 完了
- **概要:** `/settings` ルートをApp.jsxに追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/App.jsx`
    - `import SettingsPage from './pages/SettingsPage';` を追加
    - `<Route path="/settings" element={<ProtectedPage><SettingsPage /></ProtectedPage>} />` を設定セクションに追加
- **依存タスク:** タスク1
- **対応Issue:** #143

### タスク3: ボトムナビにSettingsタブ追加
- [x] 完了
- **概要:** Layout.jsxのボトムナビに5番目のSettingsタブを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/Layout.jsx`
    - Lucide `Settings` アイコンをimport追加
    - `bottomNavItems` 配列に `{ name: 'Settings', href: '/settings', icon: Settings }` を追加
    - `isBottomNavActive` に `/settings` の判定を追加: `pathname === '/settings'` でexact match
- **依存タスク:** なし
- **対応Issue:** #144

### タスク4: トップヘッダー変更（ハンバーガーメニュー削除 + ユーザーアイコン追加）
- [x] 完了
- **概要:** NavigationMenu.jsxからハンバーガーメニューを完全削除し、ユーザーアイコン（プロフィール遷移）を追加。Googleカレンダー同期関連のロジックはSettingsPageに移動済みのため削除。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/NavigationMenu.jsx`
    - 削除するもの:
      - `useState` の `menuOpen` 状態
      - `useRef` の `menuRef`
      - Googleカレンダー同期関連の全state・ロジック（`calSyncing`, `calSyncMessage`, `calSyncError`, `executeSyncWithToken`, `handleCalendarSync`, OAuthリダイレクト処理）
      - メニュー外クリックのuseEffect
      - ドロップダウンメニューのJSX全体
      - 不要になったLucideアイコンのimport（`Menu`, `Calendar`, `MapPin`, `Shuffle`, `Users`, `LogOut`, `RefreshCw`, `X`, `MessageSquare`, `Settings`, `ClipboardList`, `Building2`）
      - `calendarAPI` のimport
      - `isAdmin`, `isSuperAdmin` のimport
    - 追加するもの:
      - ユーザーアイコン（Lucide `User`、`Link to="/profile"`）を通知ベルの隣に配置
    - 残るもの:
      - ヘッダーバー（背景色、固定位置、レイアウト）
      - プレイヤー名表示
      - 通知ベルアイコン（未読数バッジ付き）
      - ユーザーアイコン（新規追加）
    - コンポーネントのpropsから不要なものがあれば整理（`unreadCount` は残す）
- **依存タスク:** タスク1（Googleカレンダー同期ロジックがSettingsPageに移植済みであること）
- **対応Issue:** #145

## 実装順序
1. タスク1: SettingsPageコンポーネント新規作成（依存なし）
2. タスク3: ボトムナビにSettingsタブ追加（依存なし）— タスク1と並行可能
3. タスク2: App.jsxにルート追加（タスク1に依存）
4. タスク4: トップヘッダー変更（タスク1に依存）
