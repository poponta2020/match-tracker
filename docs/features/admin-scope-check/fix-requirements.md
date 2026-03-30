---
status: completed
audit_source: 会話内レポート
selected_items: [1, 2, 3, 4, 5, 6, 7, 8, 9]
---
# ADMIN団体スコープチェック 改修要件定義書

## 1. 改修概要

### 対象機能
ADMINロールの団体スコープ検証（各Controllerにおける `adminOrganizationId` チェック）

### 改修の背景
監査レポートにより、ADMINが他団体のリソースを操作できてしまうセキュリティ上の問題が検出された。
練習日の更新・削除と伝助URL・システム設定にはスコープチェックが実装されているが、組み合わせ管理・抽選管理・LINE送信・抜け番活動管理・試合参加者管理ではチェックが欠落している。
また、スコープチェックの実装パターンが `checkAdminScope` / インラインチェック / チェックなしの3種に分散しており、保守性が低い。

### 改修スコープ
1. MatchPairingControllerにADMINの団体スコープチェックを追加
2. LotteryControllerの再抽選・手動編集・結果通知にADMINの団体スコープチェックを追加
3. PracticeSessionControllerの練習日作成（POST）にADMINの団体スコープチェックを追加
4. LineAdminControllerの `sendMatchPairing` にADMINの団体スコープチェックを追加
5. 試合参加者管理エンドポイントにADMINスコープチェック追加
6. ByeActivityControllerの一括作成/削除にADMINスコープチェック追加
7. 共通のADMINスコープ検証ユーティリティを作成し、分散した実装を統一
8. 仕様書の権限記載を実装に合わせて修正
9. App.jsxにロールベースのルート保護を追加

---

## 2. 改修内容

### 2.1 共通基盤: AdminScopeValidator の新設（項目7）

**現状の問題:**
スコープチェックが3パターンに分散している。
- パターンA: `PracticeSessionService.checkAdminScope(sessionId, role, adminOrgId)` — update/deleteのみ
- パターンB: Controller内インラインチェック — 伝助URL, SystemSetting
- パターンC: チェックなし — MatchPairing, Lottery, Line, Bye, 参加者管理

**修正方針:**
`AdminScopeValidator` ユーティリティクラスを新設し、共通の検証メソッドを提供する。

```java
package com.karuta.matchtracker.util;

public class AdminScopeValidator {
    /**
     * ADMINの場合、targetOrgIdがadminOrgIdと一致するか検証する。
     * SUPER_ADMINの場合は何もしない。
     */
    public static void validateScope(String role, Long adminOrgId, Long targetOrgId, String errorMessage) {
        if (!"ADMIN".equals(role)) return;
        if (adminOrgId == null || !adminOrgId.equals(targetOrgId)) {
            throw new ForbiddenException(errorMessage);
        }
    }
}
```

既存の `PracticeSessionService.checkAdminScope` およびインラインチェック箇所もこのユーティリティに置き換える。

**修正後のあるべき姿:**
全てのADMINスコープ検証が `AdminScopeValidator.validateScope()` を経由する統一されたパターンになる。

---

### 2.2 MatchPairingController スコープチェック追加（項目1）

**現状の問題:**
6つの書き込みメソッド全てでADMINスコープチェックが欠落。ADMINが他団体の組み合わせを作成・更新・削除できる。

**修正方針:**
各メソッドで `date` または関連情報から PracticeSession を取得し、`organizationId` を解決して `AdminScopeValidator.validateScope()` で検証。

| メソッド | organizationId の解決方法 |
|---------|-------------------------|
| create | リクエスト内の date → PracticeSession → organizationId |
| createBatch | パラメータ date → PracticeSession → organizationId |
| updatePlayer | matchPairingId → MatchPairing → date → PracticeSession → organizationId |
| delete | matchPairingId → MatchPairing → date → PracticeSession → organizationId |
| deleteByDateAndMatchNumber | パラメータ date → PracticeSession → organizationId |
| autoMatch | パラメータ date → PracticeSession → organizationId |

**注意:** `date` から PracticeSession を取得する際、同一日付に複数団体のセッションが存在しうる。MatchPairingService 内のロジックで特定のセッションに紐づくため、そのセッションの organizationId を検証する。

---

### 2.3 LotteryController スコープチェック追加（項目2）

**現状の問題:**
再抽選・手動編集・結果通知・通知状態取得でADMINスコープチェックが欠落。

**修正方針:**

| メソッド | organizationId の解決方法 |
|---------|-------------------------|
| reExecuteLottery | sessionId → PracticeSession → organizationId |
| editParticipants | リクエスト内 sessionId → PracticeSession → organizationId |
| notifyResults | organizationId パラメータを直接検証 |
| getNotifyStatus | organizationId パラメータを直接検証 |

---

### 2.4 PracticeSessionController 練習日作成スコープチェック追加（項目3）

**現状の問題:**
createSession で `organizationId` をリクエストから受け取るが、ADMINが他団体のIDを指定した場合のバリデーションがない。フロントエンドでは `adminOrganizationId` を自動設定しているが、API直叩きでは防げない。

**修正方針:**
ADMINがリクエストで `organizationId` を指定した場合、`adminOrgId` と一致するか検証する。
`organizationId` が null の場合は既存ロジック通り `adminOrgId` を自動設定（この場合は検証不要）。

---

### 2.5 LineAdminController スコープチェック追加（項目4）

**現状の問題:**
`sendMatchPairing` で任意の `sessionId` を指定可能。他団体の組み合わせをLINE送信できる。

**修正方針:**
`sessionId` → PracticeSession → organizationId を取得し、`AdminScopeValidator.validateScope()` で検証。

---

### 2.6 PracticeSessionController 参加者管理スコープチェック追加（項目5）

**現状の問題:**
`setMatchParticipants`, `addParticipantToMatch`, `removeParticipantFromMatch` でADMINが他団体のセッションの参加者を操作可能。

**修正方針:**
各メソッドで `sessionId` → PracticeSession → organizationId を取得し検証。
`addParticipantToMatch` は `date` パラメータから PracticeSession を特定する必要があるため、既存のセッション特定ロジックを活用する。

---

### 2.7 ByeActivityController スコープチェック追加（項目6）

**現状の問題:**
一括作成・削除でADMINが他団体の抜け番活動を操作可能。

**修正方針:**
- createBatch: `date` → PracticeSession → organizationId で検証
- delete: `byeActivityId` → ByeActivity → 関連する PracticeSession → organizationId で検証

---

### 2.8 仕様書の権限記載修正（項目8）

**現状の問題:**
SPECIFICATION.md のセクション5.2で `/practice/new`, `/practice/:id/edit` が「SUPER_ADMIN」と記載されているが、実装では ADMIN+ である。セクション5.3でも「練習日程作成（SUPER_ADMIN のみ）」と記載されている。

**修正方針:**
- 5.2: `/practice/new` と `/practice/:id/edit` の必要ロールを `ADMIN+` に修正
- 5.3: 「練習日程作成（SUPER_ADMIN のみ）」→「練習日程作成（ADMIN+ のみ）」に修正

---

### 2.9 App.jsx ロールベースルート保護追加（項目9）

**現状の問題:**
全ページが同一の `PrivateRoute`（ログイン有無チェックのみ）で保護されており、PLAYERが `/admin/settings` 等に直接URLアクセスできる。

**修正方針:**
`RoleRoute` コンポーネントを新設し、Admin専用ルートにロール制限を追加。

```jsx
// 権限不足の場合はホームにリダイレクト
<RoleRoute requiredRole="ADMIN">
  <AdminPage />
</RoleRoute>
```

対象ルート:
- `/admin/*` 系 → ADMIN+
- `/players`, `/players/new`, `/players/:id/edit` → SUPER_ADMIN
- `/practice/new`, `/practice/:id/edit` → ADMIN+
- `/matches/bulk-input/:sessionId` → ADMIN+

---

## 3. 技術設計

### 3.1 API変更
なし。既存エンドポイントの内部ロジックに検証を追加するのみ。

### 3.2 DB変更
なし。

### 3.3 フロントエンド変更
- `RoleRoute` コンポーネント新設（`src/components/RoleRoute.jsx`）
- `App.jsx` の該当ルートに `RoleRoute` を適用

### 3.4 バックエンド変更
- `AdminScopeValidator` ユーティリティクラス新設（`src/main/java/com/karuta/matchtracker/util/AdminScopeValidator.java`）
- 各Controllerに `AdminScopeValidator.validateScope()` 呼び出しを追加
- `PracticeSessionService.checkAdminScope` を `AdminScopeValidator` 利用に置き換え
- 既存インラインチェック（伝助URL, SystemSetting）を `AdminScopeValidator` 利用に置き換え

---

## 4. 影響範囲

### 影響を受ける既存機能
- ADMINユーザーの全管理操作（正常利用の範囲では動作変更なし）
- フロントエンドのルーティング（権限不足時にリダイレクトされるようになる）

### 破壊的変更の有無
なし。ADMINが自団体のリソースを操作する限り、従来通り動作する。
他団体のリソースへの操作のみ 403 Forbidden を返すようになる。

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 共通ユーティリティクラス（静的メソッド）を採用 | Service間の依存を増やさず、最もシンプルに統一できるため |
| Controller層でスコープチェックを呼び出す | `role` と `adminOrgId` がリクエスト属性として Controller で取得可能なため。Service層に渡す情報を増やさずに済む |
| 既存の `checkAdminScope` も置き換え | パターン統一による保守性向上。既存の動作は変わらない |
| `RoleRoute` を新設（既存の `PrivateRoute` は変更しない） | 既存のログインチェックロジックに影響を与えないため |
