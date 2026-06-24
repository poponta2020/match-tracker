---
status: completed
audit_source: ユーザー直接要望（監査レポートなし）
selected_items: [1]
---
# 対戦変更導線の統合（組み合わせ作成画面へ一本化） 改修要件定義書

## 1. 改修概要
- **対象機能**: 結果一括入力画面（BulkResultInput, ルート `/matches/bulk-input/:sessionId`）の「対戦変更」導線
- **改修の背景**:
  - 結果一括入力画面の右上「対戦変更」ボタンは、現状**別画面への遷移ではなく、同じ画面内の簡易編集モード**（選手名タップで対戦相手を入替）に切り替わる仕組みになっている。
  - 一方、同等以上の機能を持つ「組み合わせ作成」画面（PairingGenerator, ルート `/pairings`）が別に存在し、役割が重複してユーザーが混乱する。
  - 高機能で便利な「組み合わせ作成」画面に一本化したい。
- **改修スコープ**:
  1. 「対戦変更」ボタンを、画面内編集モードのトグルから、組み合わせ作成画面 `/pairings` への遷移に変更する
  2. 不要になる画面内編集モード関連の実装を削除する
  3. 組み合わせ作成画面の「戻る」先を、遷移元（結果入力画面）に戻れるようにする
  4. 未保存の入力結果がある状態で遷移する場合、確認ダイアログを表示する

## 2. 改修内容

### 2.1 「対戦変更」ボタンの遷移化（BulkResultInput）
- **現状**: onClick で `editMode` をトグル（BulkResultInput.jsx L446-459）。editMode 中は「完了」表示。
- **修正後**: `/pairings?date=<sessionDate>&matchNumber=<currentMatchNumber>&from=<遷移元パス>` へ `navigate` する。
  - 既存の「対戦組み合わせを作成する」ボタン（空状態, BulkResultInput.jsx L495）と同じ遷移パターンを踏襲する。
  - ボタンラベルは「対戦変更」を維持（トグルの「完了」表示は廃止）。

### 2.2 画面内編集モードの削除（BulkResultInput）
- **削除対象**:
  - state: `editMode` / `selectingPairing` / `updatingPairing`、および削除後に書き込み専用となる `participants`
  - 関数: `handlePlayerChange`（L217-253）/ `getAvailablePlayers`（L255-274）
  - UI: 選手選択リスト（L598-615）
  - 分岐: 選手名タップ時の editMode 分岐（L522-530, L576-584）、ヒント文の editMode 分岐（L505）、抜け番セクション・保存ボタンの `!editMode` 表示条件（L622, L664）
  - 未使用化する import（`X` アイコン等）
- **維持**: 抜け番算出に使う参加者データ取得（`practiceAPI.getParticipants` → ローカル変数 `allParticipants` → `computeByePlayers`）は維持する。

### 2.3 戻り先の動的化（PairingGenerator）
- **現状**: `<PageHeader backTo="/settings" />` 固定（PairingGenerator.jsx L720, L731）。`PageHeader` は `navigate(backTo)` で戻る（PageHeader.jsx L11）。
- **修正後**: `const backTo = searchParams.get('from') || '/settings';` を定義し、`backTo={backTo}` を渡す。
  - `from` が無い通常導線（設定画面・ホーム等から）では従来どおり `/settings` に戻る。

### 2.4 未保存結果の確認ダイアログ（BulkResultInput）
- `changedMatches.size > 0` のとき、遷移前に `window.confirm(...)` で確認する（コードベース標準の確認方式。PairingGenerator 等も同方式）。
- キャンセル時は遷移しない。

## 3. 技術設計

### 3.1 API変更
- なし（純粋にフロントの導線変更）。

### 3.2 DB変更
- なし。

### 3.3 フロントエンド変更
- `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — ボタン遷移化、未保存確認ダイアログ、編集モード関連コード削除
- `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — `backTo` の動的化（`from` クエリパラメータ対応）

### 3.4 バックエンド変更
- なし。

## 4. 影響範囲
- **`pairingAPI.updatePlayer`**（api/pairings.js L36）はフロントでは BulkResultInput のみが使用。削除後はフロント未使用となるが、バックエンドのエンドポイントは存続するため API クライアントメソッドは残す（無害・将来再利用可）。
- **PairingGenerator の通常導線**（設定画面・ホーム等から）は `from` 無しのため戻り先は従来どおり `/settings`。後方互換あり。
- **抜け番表示**は参加者データ取得を維持するため影響なし。
- **破壊的変更**: なし（API/DBスキーマ変更なし）。
- **デグレ懸念**: 編集モード削除により結果入力画面内での簡易な選手入替はできなくなるが、より高機能な組み合わせ作成画面で代替する（意図した統合）。

## 5. 設計判断の根拠
- 既に空状態ボタンで `/pairings?date=...&matchNumber=...` への遷移パターンが存在するため、同パターンを流用し一貫性・低リスクを確保する。
- 戻り先は `from` クエリパラメータ方式を採用。`location.state` よりも URL に状態が残りリロード耐性がある。通常導線はデフォルト `/settings` で後方互換を維持する。
- 確認ダイアログは `window.confirm` を採用。コードベース全体の確認UI慣習に合わせる。
