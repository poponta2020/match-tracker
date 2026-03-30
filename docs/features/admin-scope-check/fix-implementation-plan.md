---
status: completed
---
# ADMIN団体スコープチェック 改修実装手順書

## 実装タスク

### タスク1: AdminScopeValidator ユーティリティクラスの新設
- [x] 完了
- **概要:** 全Controllerで共通利用するADMINスコープ検証ユーティリティを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/util/AdminScopeValidator.java` — 新規作成。`validateScope(String role, Long adminOrgId, Long targetOrgId, String message)` 静的メソッドを提供
- **依存タスク:** なし
- **対応Issue:** #156

---

### タスク2: PracticeSessionController — 練習日作成のスコープチェック追加
- [x] 完了
- **概要:** createSession で ADMINが他団体の organizationId を指定できないよう検証を追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — createSession メソッド（L221-238）に `AdminScopeValidator.validateScope()` 呼び出しを追加。ADMINがリクエストで organizationId を指定した場合、adminOrgId と一致するか検証
- **依存タスク:** タスク1
- **対応Issue:** #157

---

### タスク3: PracticeSessionController — 参加者管理のスコープチェック追加
- [x] 完了
- **概要:** setMatchParticipants, addParticipantToMatch, removeParticipantFromMatch に sessionId ベースのスコープチェックを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — 3メソッド（L352-362, L372-384, L461-471）にスコープチェック追加。sessionId → PracticeSession → organizationId を取得し検証
- **依存タスク:** タスク1
- **対応Issue:** #158

---

### タスク4: PracticeSessionController/Service — 既存チェックの統一
- [x] 完了
- **概要:** 既存の `checkAdminScope` メソッドとインラインチェック（伝助URL, syncDensuke）を `AdminScopeValidator` に置き換え
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `checkAdminScope` メソッド（L462-471）を `AdminScopeValidator.validateScope()` 利用に書き換え
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — saveDensukeUrl（L507-510）, syncDensuke（L537-542）のインラインチェックを `AdminScopeValidator` に置き換え
- **依存タスク:** タスク1
- **対応Issue:** #159

---

### タスク5: MatchPairingController — スコープチェック追加
- [x] 完了
- **概要:** 6つの書き込みメソッド全てに、date → PracticeSession → organizationId によるスコープチェックを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java` — 全6メソッド（create, createBatch, updatePlayer, delete, deleteByDateAndMatchNumber, autoMatch）に httpRequest パラメータ追加（不足している場合）＋ `AdminScopeValidator.validateScope()` 呼び出し
  - **注意:** MatchPairingエンティティには sessionId がなく sessionDate のみ。`PracticeSessionRepository.findBySessionDateAndOrganizationId(date, adminOrgId)` でADMINの団体にセッションが存在するか検証する。存在しない場合は ForbiddenException
- **依存タスク:** タスク1
- **対応Issue:** #160

---

### タスク6: LotteryController — スコープチェック追加
- [x] 完了
- **概要:** reExecuteLottery, editParticipants, notifyResults, getNotifyStatus にスコープチェックを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - reExecuteLottery（L107-114）: sessionId → PracticeSession → organizationId で検証。httpRequest パラメータ追加
    - editParticipants（L367-372）: RequestBody 内の sessionId から検証。httpRequest パラメータ追加
    - notifyResults（L411-443）: ADMINの場合 organizationId を adminOrgId に強制（SystemSettingController と同パターン）。httpRequest パラメータ追加
    - getNotifyStatus（L377-406）: 同上。httpRequest パラメータ追加
- **依存タスク:** タスク1
- **対応Issue:** #161

---

### タスク7: LineAdminController — sendMatchPairing スコープチェック追加
- [x] 完了
- **概要:** sendMatchPairing で sessionId → PracticeSession → organizationId によるスコープチェックを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineAdminController.java` — sendMatchPairing メソッド（L104-109）に httpRequest パラメータ追加＋スコープチェック
- **依存タスク:** タスク1
- **対応Issue:** #162

---

### タスク8: ByeActivityController — スコープチェック追加
- [x] 完了
- **概要:** createBatch と delete にスコープチェックを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/ByeActivityController.java`
    - createBatch（L75-86）: date → PracticeSession → organizationId で検証（httpRequest は既に引数にあり）
    - delete（L106-112）: httpRequest パラメータ追加。byeActivityId → ByeActivity → sessionDate → PracticeSession → organizationId で検証
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/ByeActivityService.java` — delete メソッドで ByeActivity から sessionDate を返す、またはController側で取得するヘルパー追加
- **依存タスク:** タスク1
- **対応Issue:** #163

---

### タスク9: SystemSettingController — 既存チェックの統一
- [x] 完了
- **概要:** SystemSettingController のインラインチェック（三項演算子パターン）を `AdminScopeValidator` に置き換え
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/SystemSettingController.java` — getAll（L30-44）, setValue（L64-86）のインラインチェックを統一パターンに置き換え
- **依存タスク:** タスク1
- **対応Issue:** #164

---

### タスク10: 仕様書の権限記載修正
- [x] 完了
- **概要:** SPECIFICATION.md の権限記載を実装に合わせて修正
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
    - L725: `/practice/new` の必要ロールを `SUPER_ADMIN` → `ADMIN+` に修正
    - L727: `/practice/:id/edit` の必要ロールを `SUPER_ADMIN` → `ADMIN+` に修正
    - L764付近: 「練習日程作成（SUPER_ADMIN のみ）」→「練習日程作成（ADMIN+ のみ）」に修正
- **依存タスク:** なし
- **対応Issue:** #165

---

### タスク11: App.jsx — ロールベースルート保護の追加
- [x] 完了
- **概要:** RoleRoute コンポーネントを新設し、Admin専用ルートにロール制限を追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/RoleRoute.jsx` — 新規作成。`requiredRole` prop に基づき、権限不足時はホーム（`/`）にリダイレクト
  - `karuta-tracker-ui/src/App.jsx` — 該当ルートに `RoleRoute` を適用
    - `/admin/*` 系ルート → `requiredRole="ADMIN"`
    - `/players`, `/players/new`, `/players/:id/edit` → `requiredRole="SUPER_ADMIN"`
    - `/practice/new`, `/practice/:id/edit` → `requiredRole="ADMIN"`
    - `/matches/bulk-input/:sessionId` → `requiredRole="ADMIN"`
- **依存タスク:** なし
- **対応Issue:** #166

---

## 実装順序

1. **タスク1** — AdminScopeValidator（共通基盤、他全てに依存される）
2. **タスク2, 3, 4** — PracticeSessionController/Service の改修（並行可能）
3. **タスク5** — MatchPairingController
4. **タスク6** — LotteryController
5. **タスク7** — LineAdminController
6. **タスク8** — ByeActivityController
7. **タスク9** — SystemSettingController
8. **タスク10** — 仕様書修正（コード変更と並行可能）
9. **タスク11** — フロントエンド RoleRoute（バックエンドと並行可能）
