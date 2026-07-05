---
status: completed
---
# 対戦組み合わせ画面 残存admin専用操作のPLAYER開放 要件定義書

## 1. 概要

- **目的:** 対戦組み合わせ画面（`PairingGenerator`）で現在 ADMIN/SUPER_ADMIN 限定になっている残り3操作（参加者追加・全削除・結果込みリセット）を PLAYER にも開放する。
- **背景・動機:** 別機能 [`pairing-player-access-and-empty-cta`](../pairing-player-access-and-empty-cta/fix-requirements.md)（Issue #823-#827、完了済み）で組み合わせ作成・自動マッチング・選手変更・ロック/解除は既に PLAYER に開放済みだが、「参加者追加」「全削除」「結果込みリセット」の3操作は対象外のまま残っていた。今回、PLAYER が「参加者追加」ボタンを押して403エラーになったこと（[Issue調査]）をきっかけに、残り全操作もPLAYERに開放する方針が確定した。

## 2. ユーザーストーリー

- **対象ユーザー:** PLAYER（一般メンバー）
- **目的:** 練習現場でADMINの手を借りずに、自団体の組み合わせ画面上で参加者の追加・組み合わせの全削除・結果込みリセットを行いたい。
- **利用シナリオ:** 練習当日、PLAYERが組み合わせ画面を開き、待機者リストにいない選手を試合に追加したり、組み直しのために既存組み合わせを全削除したり、誤って確定した結果を組み合わせごとリセットしたりする。

## 3. 機能要件

### 3.1 画面仕様

対象画面: `PairingGenerator`（`/pairings`）。画面レイアウト・ボタン配置に変更はない。**表示条件（誰に見えるか）のみ変更する。**

| ボタン/操作 | 現状の表示条件 | 変更後の表示条件 |
|---|---|---|
| 「追加」（参加者セクション・待機リストセクション、[PairingGenerator.jsx:913](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L913), [:1173](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L1173)） | ロール判定なし（誰でも見える。ただしバックエンドがADMIN+限定のため実行すると403） | 変更なし（バックエンドを開放するのでそのまま実行可能になる） |
| 「全削除」（[PairingGenerator.jsx:980-989](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L980-L989)） | `isAdmin() \|\| isSuperAdmin()` | ロール判定を撤去（誰でも見える） |
| 「結果込みリセット」（各組カード内、[PairingGenerator.jsx:1041](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L1041)） | `isAdmin() \|\| isSuperAdmin()` | ロール判定を撤去（誰でも見える） |

- 「全削除」「結果込みリセット」は既存の `window.confirm()` による確認ダイアログ（[:448](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L448), [:487](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx#L487)）をそのまま維持する。新規UIは追加しない（ユーザー確認済み：既存の確認ダイアログで十分）。
- **今回のスコープ外:** `removeParticipantFromMatch`（参加者を1名削除するAPI）は、現在この画面のどのボタンからも呼ばれておらず未配線のため、今回はADMIN+のまま据え置く（ユーザー確認済み）。

### 3.2 ビジネスルール

- **団体スコープ強制（重要）:** 別機能で確立された「PLAYERは自分の所属団体のセッション/組み合わせのみ操作可能」という設計方針を、今回開放する3操作にも同様に適用する。SUPER_ADMINはスコープ強制なし、ADMINは自団体、PLAYERは所属団体（複数可）。他団体のデータに対する操作は403 Forbiddenとする。
  - 「全削除」「結果込みリセット」は既に汎用化済みのスコープ検証（`validateScopeByDate` / `validateScopeByPairingId`、[MatchPairingController.java:287](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java#L287), [:327](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java#L327)）を通っているため、`@RequireRole` にPLAYERを追加するだけでこの方針が自動的に適用される。
  - 「参加者追加」は現状 `checkAdminScopeByDate`（[PracticeSessionService.java:674](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java#L674)）がADMIN専用実装（ADMIN以外は無条件でスコープチェックをスキップ）のため、**PLAYER分岐を新設しないと「他団体の練習日にも参加者を追加できてしまう」認可バイパスになる。** `MatchPairingController.validateScopeByDate` と同じ考え方（PLAYERは `OrganizationService.getPlayerOrganizationIds` で所属団体を取得し、対象日付のセッションがそのいずれかに属するか検証）で拡張する。
- **削除・リセットの不可逆性:** 「全削除」「結果込みリセット」は元に戻せない操作のため、既存の確認ダイアログを削除・弱体化しない。
- **既知の制限（対象外・別Issue）:** 読み取り系API（例: `GET /api/match-pairings/date`、`PracticeSessionController.getSessionById`）の団体スコープ未強制は、別機能のレビューで既に「将来課題」として記録済みの既知の制限であり、本機能では対応しない。

## 4. 技術設計

### 4.1 API設計

| メソッド | パス | 現状 | 変更後 |
|---|---|---|---|
| POST | `/api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}` | `{SUPER_ADMIN, ADMIN}` | `{SUPER_ADMIN, ADMIN, PLAYER}` |
| DELETE | `/api/match-pairings/date-and-match` | `{SUPER_ADMIN, ADMIN}` | `{SUPER_ADMIN, ADMIN, PLAYER}` |
| DELETE | `/api/match-pairings/{id}/with-result` | `{SUPER_ADMIN, ADMIN}` | `{SUPER_ADMIN, ADMIN, PLAYER}` |

**据え置き（変更なし）:**
- `DELETE /api/match-pairings/{id}`（個別削除・UI未使用）
- `DELETE /api/practice-sessions/{sessionId}/matches/{matchNumber}/participants/{playerId}`（`removeParticipantFromMatch`・UI未配線）

リクエスト/レスポンス形式の変更はなし。ロール制御の解放とスコープ検証拡張のみ。

### 4.2 DB設計

- なし。

### 4.3 フロントエンド設計

- [karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx)
  - 行980付近「全削除」ボタンの表示条件から `(isAdmin() || isSuperAdmin())` を撤去
  - 行1041付近「結果込みリセット」ボタンの表示条件から `(isAdmin() || isSuperAdmin())` を撤去
  - 行10の `isAdmin, isSuperAdmin` import は、撤去後に他で使用箇所が無ければ削除（要確認）
- ルーティング（`App.jsx`）・設定メニュー（`SettingsPage.jsx`）は既存機能で開放済みのため変更不要

### 4.4 バックエンド設計

- [karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java)
  - `addParticipantToMatch`（POST `/date/{date}/matches/{matchNumber}/participants/{playerId}`）の `@RequireRole` にPLAYERを追加
- [karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java)
  - `checkAdminScopeByDate`（呼び出し元は `addParticipantToMatch` のみ）をPLAYER分岐対応に拡張。`MatchPairingController.validateScopeByDate` と同じロジック（SUPER_ADMINはスキップ、ADMINは自団体、PLAYERは所属団体いずれかで一致を要求）。呼び出し元が1箇所のみのため、他のADMIN専用エンドポイント（`updateSession`/`deleteSession`/`confirmReservation`/`expandVenue`/`setMatchParticipants`/`removeParticipantFromMatch`が使う`checkAdminScope`（セッションIDベース）は今回のメソッドとは別物であり、無関係・変更なし）に影響しない
  - メソッド名は実装時に `checkScopeByDate` 等へリネームするか検討（呼び出し元1箇所の更新のみで済む）
- [karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java)
  - `deleteByDateAndMatchNumber`（DELETE `/date-and-match`）と `resetWithResult`（DELETE `/{id}/with-result`）の `@RequireRole` にPLAYERを追加。両者とも既に `validateScopeByDate` / `validateScopeByPairingId` を通っているため、スコープ検証ロジック自体の変更は不要
- テスト
  - `MatchPairingControllerTest`: `deleteByDateAndMatchNumber` / `resetWithResult` について、PLAYER自所属団体成功・所属外403のケースを追加（既存の他エンドポイント向けテストパターンを踏襲）
  - `PracticeSessionControllerTest`（または相当のテストクラス）: `addParticipantToMatch` について、PLAYER自所属団体成功・所属外403のケースを追加

### 4.5 ドキュメント変更

- `docs/SPECIFICATION.md`: 該当3エンドポイントの権限表記を「ADMIN+」→「PLAYER+」に修正
- `docs/DESIGN.md`: 権限設計の節があれば、PLAYERスコープ強制の対象操作一覧に3操作を追記

## 5. 影響範囲

### 5.1 既存機能への影響

- **ADMIN / SUPER_ADMIN の操作:** 変化なし。
- **PLAYER から見える操作範囲:** 拡大。組み合わせ画面で「参加者追加」が実際に成功するようになり、「全削除」「結果込みリセット」ボタンが表示・実行可能になる。
- **他団体への影響:** スコープ強制（新設のPLAYER分岐 + 既存の`validateScopeByDate`/`validateScopeByPairingId`）によりブロックされるため、誤操作の波及はなし。

### 5.2 破壊的変更

- なし。API仕様（パス・リクエスト・レスポンス形式）は変更なし。既存のADMIN向けユースケースは全て従来通り動作する。

### 5.3 フロントエンド⇔バックエンド整合性

- 「追加」ボタンは既にフロントに存在し未ガードだったため、バックエンド開放のみで整合が取れる。
- 「全削除」「結果込みリセット」はフロント・バックエンドを同時に開放するため、リリース時のずれによる403連発の懸念はない。

## 6. 設計判断の根拠

- **今回3操作すべてを開放する判断:** ユーザーが「画面上のadmin/super admin専用操作は全部PLAYERに開放してほしい」と明示的に要望。以前の関連機能で「不可逆操作は事故リスクを理由に管理者専用に据え置く」と判断していたが、今回はユーザーが明示的にその判断を上書きし、既存の確認ダイアログ（`window.confirm`）が残ることを条件に承認した。
- **`removeParticipantFromMatch` を対象外とする判断:** 画面上に対応するボタンが存在せず、今回の要望（画面上で操作できずに困っている操作）の範囲外であるため。将来UIが追加される際に別途検討する。
- **PLAYERにも団体スコープ強制を適用する判断:** 複数団体運用が前提のため、開放の代償として他団体データへの誤操作・越境操作を防ぐ必要がある。既存の関連機能で確立した「ADMINと同じセキュリティモデルをPLAYERにも適用する」方針を踏襲し、一貫性を保つ。
- **`checkAdminScopeByDate` を汎用化しつつ他のADMIN専用エンドポイントは`checkAdminScope`（別メソッド）のまま据え置く判断:** 呼び出し元を調査した結果、`checkAdminScopeByDate` の呼び出し元は `addParticipantToMatch` の1箇所のみと判明。汎用化してもADMIN専用の他エンドポイント（`checkAdminScope`を使う別メソッド群）には影響しないため、安全にスコープを広げられる。
