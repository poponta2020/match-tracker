---
status: approved
issue: 1166
---
# バグ改修要件: ボトムナビのカプセルがホーム経由の遷移でグライドせず瞬間移動する

## 再現手順
1. ログインしてボトムナビを表示する。
2. ホーム以外のアイコン同士でタップ移動 → カプセルが滑らかにスライドする（正常）。
3. ホーム→他アイコン、または他アイコン→ホームでタップ移動 → カプセルが瞬間移動する（バグ）。

## 期待される動作 / 実際の動作
- 期待: ホームを経由する遷移でもカプセルが該当位置へ滑らかにグライドする（既存 AC-17「瞬間移動しない」と同一挙動）。
- 実際: ホームが絡む遷移のみカプセルが瞬間移動する。

## 根本原因
- `/`（ホーム）だけが `<AuthRoute>` でラップされている（[App.jsx](../../../karuta-tracker-ui/src/App.jsx) の `/` ルート）。他の4つのナビ先（`/matches/results`・`/practice`・`/matches`・`/settings`）は `<ProtectedPage>` を直接 `<Routes>` の outlet 位置に置いている。
- React は `<Routes>` outlet 位置の**要素の型**で fiber を調停する。`/` の出入りでは `AuthRoute` ↔ `ProtectedPage` の型不一致になり、共有 `<Layout>`（＝アクティブカプセルの単一 `<span>`）が**アンマウント→リマウント**される。
- リマウント直後のカプセルには「前の位置の transform」が存在しないため、`transition: transform 427ms cubic-bezier(...)` が補間する差分がなく、**新しい位置に瞬間表示**される（＝瞬間移動）。
- 他→他は両端とも `<ProtectedPage>` 型のため Layout fiber が再利用され、カプセルが永続してグライドする（「他→他は正常」というユーザー報告がこの再利用を実証している）。
- 実装参照: [components/Layout.jsx](../../../karuta-tracker-ui/src/components/Layout.jsx)（カプセルの transition）、`components/AuthRoute.jsx`（本 PR で削除）、[components/PrivateRoute.jsx](../../../karuta-tracker-ui/src/components/PrivateRoute.jsx)。

## 修正方針
5つのナビ先すべてが `<Routes>` outlet に**同一型 `<ProtectedPage>`** を置くようにし、`/` 出入りで Layout fiber を再利用させる（＝カプセル永続 → グライド）。`<AuthRoute>` の「認証時 Home / 未認証時 Landing」分岐は `PrivateRoute` の任意フォールバックに移す。

1. `PrivateRoute` に任意 prop `publicFallback` を追加する。未認証時、`publicFallback` があればそれを描画し、無ければ従来通り `/login` へリダイレクト（後方互換）。認証済み・loading・プロフィール未設定チェックは不変。
2. `ProtectedPage`（App.jsx 内）に `publicFallback` を受け取り `PrivateRoute` へ透過する。
3. App.jsx の `/` ルートを `<ProtectedPage publicFallback={<Landing />}><Home /></ProtectedPage>` に変更し、`<AuthRoute>` の使用と import を撤去する。
4. `AuthRoute.jsx` は他に import 元が無く dead code になるため削除する（この修正の直接の帰結。無関係リファクタではない）。

これで認証済みユーザーの5ナビ先はすべて outlet に `<ProtectedPage>` → 同一 fiber 再利用 → カプセルがホーム経由でもグライドする。未認証 `/` は引き続き Landing を表示（nav なし）。

## Acceptance Criteria
| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | ホームを経由する遷移（ホーム→他／他→ホーム）でカプセルが瞬間移動せず滑らかにグライドする（既存 AC-17 のホーム経由での回帰解消） | verify |
| AC-2 | 既存のフロントエンドテスト・lint がすべて成功する（PrivateRoute 改修のデグレなし） | auto-test |
| AC-3 | PrivateRoute の挙動が保たれる: (a) 未認証＋`publicFallback` あり→fallback 描画（/login へリダイレクトしない）／(b) 未認証＋`publicFallback` なし→/login リダイレクト（既存挙動）／(c) 認証済み（kyuRank あり）→children 描画 | auto-test |

### 回帰テストの誠実な範囲（重要）
- **AC-1（グライド）は `verify`**。カプセルの実アニメーションは既存の drag/visual AC（AC-16〜19）と同様に実機/プレビュー目視で確認する。App.jsx の実ルート型を忠実に再現しない手製 MemoryRouter レプリカのテストは、`/` が再び `<AuthRoute>` でラップされても pass してしまい**真の回帰を守れない**ため、あえて auto-test にしない（advisor 助言）。
- **AC-3（PrivateRoute 挙動）は auto-test** で担保する。今回コードを変える唯一の危険箇所（全 protected ルートが通る `PrivateRoute`）を直接検証し、後方互換とフォールバック描画を固定する。

## Non-goals
- App.jsx 全体のレイアウトルート（`<Outlet/>`）化リファクタ（Option B）は今回やらない。将来の別タスク候補。
- 他ページ間（他→他）の遷移挙動は既に正常なので変更しない。
- ボトムナビの見た目・ドラッグ挙動・カプセル位置計算・アニメーション時間の変更はしない。
- カプセル位置をモジュール変数で永続化するハック（Option C）は採らない（リマウントという根本原因を残し、初回描画フラッシュもあるため）。

## 影響範囲
- 変更: `karuta-tracker-ui/src/components/PrivateRoute.jsx`（`publicFallback` 追加）、`karuta-tracker-ui/src/App.jsx`（`/` ルート＋import 変更）、`karuta-tracker-ui/src/components/AuthRoute.jsx`（削除）。
- 新規: `karuta-tracker-ui/src/components/PrivateRoute.test.jsx`（AC-3）。
- `PrivateRoute` は全 protected ルートが経由するため、既存の未認証→/login リダイレクトが他ルートで不変であることをフルスイートで確認する。
