---
status: completed
---
# ADMIN向け当日通知拡張 & 空き枠通知改善 & 所属団体設定UI 要件定義書

## 1. 概要

### 目的
1. 各練習会のADMIN（管理者）が、自団体の当日参加変更（キャンセル・参加・確定・キャンセル待ち変動）をLINE通知で把握できるようにする
2. SAME_DAY_VACANCY（空き枠通知）の送信先を、セッション参加者に限定せず団体の全メンバーに拡大する
3. 当日0:00時点で空き枠がありキャンセル待ちもいない場合に、空き枠通知を自動送信する
4. SUPER_ADMINがADMINの管理団体を設定するUIを追加する

### 背景・動機
- 現状、当日の管理者向けLINE通知（`ADMIN_SAME_DAY_CONFIRMATION`, `ADMIN_WAITLIST_UPDATE` 等）はSUPER_ADMINにのみ送信されている
- 各練習会のADMINは、自分がWON参加者でない限り当日の変更を一切知ることができない
- SAME_DAY_VACANCYの送信先が「セッションの非WON参加者」に限定されており、そもそも登録していない人には届かない
- 空きがありキャンセル待ちもいないセッションが存在しても、能動的に確認しない限り気づけない
- ADMINの管理団体を設定するAPIは存在するが、UIがないためAPI直接呼び出しが必要

## 2. ユーザーストーリー

### 対象ユーザー
- **ADMIN**: 各練習会（わすらもち会、北大かるた会等）の管理者。自団体の練習運営を担当
- **SUPER_ADMIN**: 全団体横断の管理者。ADMINのロール・団体設定を行う

### 利用シナリオ

**シナリオA: ADMINが当日変更を把握する**
1. わすらもち会のADMINが管理者用LINEをリンク済み
2. 練習当日12:00に参加者確定通知（メンバーリスト）がLINEに届く
3. 12:00以降にキャンセルが発生すると、キャンセル通知がLINEに届く
4. 12:00以降に先着参加があると、参加通知がLINEに届く
5. キャンセル待ち列に変動があると、状況通知がLINEに届く
→ ADMINは自団体の当日の状況をリアルタイムに把握できる

**シナリオB: 空き枠に気づいて参加する**
1. わすらもち会の練習日。抽選済みだが定員に達しておらず、キャンセル待ちもいない
2. 当日0:00に、わすらもち会の全メンバーに「空き枠のお知らせ」通知がLINEに届く
3. 通知を見たプレイヤーが「参加する」ボタンを押して参加登録する
→ セッションに登録していなかった人も空き枠の存在に気づける

**シナリオC: SUPER_ADMINがADMINの所属団体を設定する**
1. SUPER_ADMINが選手編集画面を開く
2. ロールを「管理者（ADMIN）」に変更する
3. 管理団体のドロップダウンが表示される
4. 団体を選択して保存する

## 3. 機能要件

### 3.1 ADMIN向け当日通知

#### 3.1.1 通知種別と送信先

| 通知 | 通知タイプ | チャネル | ADMIN送信先 | SUPER_ADMIN送信先 | トグル |
|------|-----------|---------|------------|------------------|--------|
| 当日キャンセル通知 | `ADMIN_SAME_DAY_CANCEL`（新設） | 管理者用 | 該当団体のADMIN | 全SUPER_ADMIN | `admin_same_day_cancel`（新設） |
| 当日参加通知 | `ADMIN_SAME_DAY_CANCEL`（新設） | 管理者用 | 該当団体のADMIN | 全SUPER_ADMIN | `admin_same_day_cancel`（新設） |
| 参加者確定通知（12:00） | `ADMIN_SAME_DAY_CONFIRMATION`（既存） | 管理者用 | 該当団体のADMIN | 全SUPER_ADMIN（変更なし） | `admin_same_day_confirmation`（既存） |
| キャンセル待ち状況通知 | `ADMIN_WAITLIST_UPDATE`（既存） | 管理者用 | 該当団体のADMIN | 全SUPER_ADMIN（変更なし） | `admin_waitlist_update`（既存） |

#### 3.1.2 受信者選定ルール

- **ADMIN**: `player.adminOrganizationId == session.organizationId` のADMINのみ
- **SUPER_ADMIN**: 全SUPER_ADMIN（団体フィルタなし、仕様書通り）
- ADMIN/SUPER_ADMINが同時にWON参加者の場合: 選手用チャネルで通常通知 + 管理者用チャネルで管理者通知 = 2通届く（これは正常動作、全通知種別で統一）
- ADMIN_SAME_DAY_CONFIRMATIONもWON参加者かどうかに関係なく必ず送信する（既存のスキップロジックを廃止）

#### 3.1.3 通知トグル

- ADMINの通知設定は `line_notification_preferences` テーブルの `organizationId=0` レコードで管理（SUPER_ADMINと同じ方式）
- デフォルト: 全ON
- 通知設定画面で個別にON/OFF可能

#### 3.1.4 各通知の内容

**ADMIN_SAME_DAY_CANCEL（キャンセル時）**: WON参加者に送られるものと同じテキスト
- 例: 「○○さんが今日の1試合目をキャンセルしました」

**ADMIN_SAME_DAY_CANCEL（参加時）**: WON参加者に送られるものと同じテキスト
- 例: 「○○さんが今日の1試合目に参加します」

**ADMIN_SAME_DAY_CONFIRMATION**: 既存と同じFlex Message（青ヘッダーのメンバーリスト）

**ADMIN_WAITLIST_UPDATE**: 既存と同じFlex Message（管理者向けキャンセル待ち状況）

### 3.2 SAME_DAY_VACANCY 送信先変更 & 0:00空き枠通知

#### 3.2.1 既存SAME_DAY_VACANCYの送信先変更

**現状**: 当該セッションの非WON参加者（`practice_participants`にレコードがある人のみ）

**変更後**: 当該セッションの団体に所属する全プレイヤー（該当試合のWON参加者を除く）

- `player_organizations`テーブルで団体メンバーを取得し、該当試合のWON参加者を除外
- セッションに登録していないプレイヤーにも届くようになる
- 対象メソッド: `sendSameDayVacancyNotification()`, `sendSameDayVacancyUpdateNotification()`

#### 3.2.2 0:00空き枠通知（新機能）

**発火条件**:
- 毎日0:00 JST にスケジューラが実行
- 当日の練習セッションが存在する
- 抽選が実行済み（`LotteryExecutionRepository`で確認）
- 該当試合の `WON数 < 定員` かつ `WAITLISTED数 = 0`

**送信先（選手向け）**:
- 当該セッションの団体に所属する全プレイヤー（該当試合のWON参加者を除く）
- 通知タイプ: `SAME_DAY_VACANCY`（既存）
- チャネル: PLAYER（選手用）
- トグル: `sameDayVacancy`（既存）

**送信先（管理者向け）**:
- 該当団体のADMIN + 全SUPER_ADMIN
- 通知タイプ: `ADMIN_SAME_DAY_CANCEL`（兼用 — 当日の参加状況変更全般をカバー）
- チャネル: ADMIN（管理者用）
- トグル: `admin_same_day_cancel`

**通知内容**: 既存のSAME_DAY_VACANCY Flex Message（オレンジヘッダー「空き枠のお知らせ」+「参加する」ボタン）をそのまま使用

**試合ごとに判定**: 1セッションに複数試合がある場合、各試合ごとに空き枠・キャンセル待ちを判定し、条件を満たす試合のみ通知

### 3.3 ADMIN所属団体設定UI

#### 3.3.1 画面仕様

**場所**: 選手編集画面（`/players/:id/edit`）のロール選択セクション直下

**表示条件**: SUPER_ADMINが編集中 かつ ロールが「ADMIN」に設定されている場合

**UI要素**:
- ラベル: 「管理団体」
- ドロップダウン: 全団体一覧（`organizationAPI.getAll()`で取得）
- 「スーパー管理者専用」バッジ付き
- 現在の`adminOrganizationId`がある場合はプリセレクト

**保存フロー**:
1. ロールがADMINの場合、基本情報更新 → ロール更新 → 団体紐づけ更新の3段階
2. `organizationAPI.updateAdminOrganization(playerId, organizationId)` を呼び出し
3. ロールがADMINから別のロールに変わった場合、団体紐づけは更新しない（バックエンド側でnullにするかは既存動作に委ねる）

**新規作成時**: ロール選択がないため、団体紐づけは後から編集画面で設定するフロー

### 3.4 通知設定UIの拡張

#### 3.4.1 管理者通知セクション

**現状**: ADMIN + SUPER_ADMINで管理者用LINEがリンク済みの場合に表示（`NotificationSettings.jsx` 行778）

**変更**: 既存の2トグルに加え、`admin_same_day_cancel` トグルを追加

| トグル | 表示名 | 設定キー | 対象通知 |
|--------|--------|---------|---------|
| キャンセル待ち状況通知 | 既存 | `adminWaitlistUpdate` | ADMIN_WAITLIST_UPDATE |
| 参加者確定通知（当日12:00） | 既存 | `adminSameDayConfirmation` | ADMIN_SAME_DAY_CONFIRMATION |
| 当日キャンセル・参加・空き枠通知 | **新設** | `adminSameDayCancel` | ADMIN_SAME_DAY_CANCEL（キャンセル通知、参加通知、0:00空き枠通知） |

## 4. 技術設計

### 4.1 DB設計

#### line_notification_preferences テーブル変更
```sql
ALTER TABLE line_notification_preferences
  ADD COLUMN IF NOT EXISTS admin_same_day_cancel BOOLEAN NOT NULL DEFAULT TRUE;
```

#### line_message_logs テーブル変更
- `notification_type` の CHECK 制約に `ADMIN_SAME_DAY_CANCEL` を追加

### 4.2 バックエンド設計

#### 4.2.1 Entity変更

**`LineMessageLog.java` — LineNotificationType enum**
- `ADMIN_SAME_DAY_CANCEL` を追加（`SAME_DAY_CANCEL` と `SAME_DAY_VACANCY` の間）
- 既存の `getRequiredChannelType()` で `ADMIN_` プレフィックスルールにより自動的に管理者用チャネルを使用

**`LineNotificationPreference.java`**
- `adminSameDayCancel` フィールド追加（Boolean, default true）

**`LineNotificationPreferenceDto.java`**
- `adminSameDayCancel` フィールド追加
- `fromEntity()` メソッドにマッピング追加

#### 4.2.2 Repository変更

**`PlayerRepository.java`**
- 新メソッド追加:
```java
@Query("SELECT p FROM Player p WHERE p.role = :role AND p.adminOrganizationId = :orgId AND p.deletedAt IS NULL")
List<Player> findByRoleAndAdminOrganizationIdAndActive(@Param("role") Player.Role role, @Param("orgId") Long orgId);
```

**`PlayerOrganizationRepository.java`**（既存メソッドを活用）
- `findByOrganizationId(Long organizationId)` — 団体メンバー取得に使用

**`LotteryExecutionRepository.java`**（既存メソッドを活用）
- `existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(year, month, orgId, SUCCESS)` — 抽選実行済み判定に使用

#### 4.2.3 Service変更

**`LineNotificationService.java`**

**(a) 管理者受信者取得ヘルパー（新設）**
```java
private List<Player> getAdminRecipientsForSession(PracticeSession session) {
    List<Player> recipients = new ArrayList<>(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN));
    recipients.addAll(playerRepository.findByRoleAndAdminOrganizationIdAndActive(
        Player.Role.ADMIN, session.getOrganizationId()));
    return recipients;
}
```

**(b) sendSameDayCancelNotification() 変更**
- 既存のWON参加者への送信後に、`getAdminRecipientsForSession()` で取得した管理者に `ADMIN_SAME_DAY_CANCEL` で送信
- WON参加者として既に通知済みの管理者もADMIN通知は別チャネルなので送信する（2通OK）

**(c) sendSameDayJoinNotification() 変更**
- 既存のWON参加者への送信後に、管理者に `ADMIN_SAME_DAY_CANCEL` で同じメッセージを送信

**(d) sendSameDayConfirmationNotification() 変更**
- 既存: SUPER_ADMINのみにADMIN_SAME_DAY_CONFIRMATION送信（WON参加者はスキップ）
- 変更: `getAdminRecipientsForSession()` を使い、該当団体ADMINにも送信。WON参加者かどうかに関係なく全管理者に送信（既存のスキップロジックを廃止）

**(e) sendAdminWaitlistNotification() 変更**
- 既存: `findByRoleAndActive(SUPER_ADMIN)` でSUPER_ADMINのみ
- 変更: `getAdminRecipientsForSession()` を使い、該当団体ADMINにも送信

**(f) isNotificationEnabled() 変更 — 管理者通知トグルの organizationId=0 統一**
- `ADMIN_SAME_DAY_CANCEL` を `organizationId=0` レコードで判定（`ADMIN_SAME_DAY_CONFIRMATION` と同じパターン）
- **既存の `ADMIN_WAITLIST_UPDATE` も修正**: 現状は `isLineTypeEnabled()` 経由で全orgレコードの anyMatch 判定になっているが、他の管理者通知と統一して `organizationId=0` レコードのみで判定するように変更する
- 管理者専用通知（`ADMIN_` プレフィックス）は全て `organizationId=0` で判定するように `isNotificationEnabled()` を統一する

**(g) isLineTypeEnabled() 変更**
- switch文に `case ADMIN_SAME_DAY_CANCEL -> pref.getAdminSameDayCancel()` を追加

**(h) updatePreferences() 変更**
- `pref.setAdminSameDayCancel(dto.isAdminSameDayCancel())` を追加

**(i) sendSameDayVacancyNotification() 変更（送信先拡大）**
- 既存: `practiceParticipantRepository.findBySessionId()` で取得したセッション参加者のうち非WON
- 変更: `playerOrganizationRepository.findByOrganizationId(session.getOrganizationId())` で団体メンバーを取得し、該当試合のWON参加者を除外

**(j) sendSameDayVacancyUpdateNotification() 変更（送信先拡大）**
- (i)と同じ方式で送信先を団体メンバーに拡大

**(k) 0:00空き枠通知の送信メソッド（新設）**
- 既存の `sendSameDayVacancyNotification()` を呼び出す形で実装
- 管理者向けには `getAdminRecipientsForSession()` で取得した管理者に `ADMIN_SAME_DAY_CANCEL` で送信

#### 4.2.4 Scheduler変更

**`SameDayVacancyScheduler.java`（新規作成）**
- 毎日0:00 JST に実行
- 処理フロー:
  1. 当日の全練習セッションを取得
  2. 各セッションについて、抽選実行済みか判定（`LotteryExecutionRepository`）
  3. 各試合について `WON数 < 定員` かつ `WAITLISTED数 = 0` を判定
  4. 条件を満たす試合に対して `sendSameDayVacancyNotification()` を呼び出し
  5. 管理者向けにも送信

### 4.3 フロントエンド設計

#### 4.3.1 PlayerEdit.jsx 変更

- `organizationAPI` と `organizations` state を追加
- `useEffect` で団体一覧を取得
- ロール選択が `ADMIN` の場合に管理団体ドロップダウンを表示
- `handleSubmit` でロール変更後に `updateAdminOrganization` を呼び出し

#### 4.3.2 NotificationSettings.jsx 変更

- `handleToggleAdminLinePref` のデフォルト値に `adminSameDayCancel: true` を追加
- 管理者通知セクションに「当日キャンセル・参加通知」トグルを追加

## 5. 影響範囲

### 変更対象ファイル一覧

#### バックエンド
| ファイル | 変更内容 |
|---------|---------|
| `entity/LineMessageLog.java` (行68付近) | `ADMIN_SAME_DAY_CANCEL` enum値追加 |
| `entity/LineNotificationPreference.java` (行84付近) | `adminSameDayCancel` フィールド追加 |
| `dto/LineNotificationPreferenceDto.java` (行24-42) | フィールド追加 + fromEntity更新 |
| `repository/PlayerRepository.java` (行68付近) | `findByRoleAndAdminOrganizationIdAndActive` メソッド追加 |
| `service/LineNotificationService.java` (複数箇所) | 6通知メソッド変更 + ヘルパー追加 + トグル判定変更 + updatePreferences変更 |
| `scheduler/SameDayVacancyScheduler.java` (新規) | 0:00空き枠通知スケジューラ |

#### フロントエンド
| ファイル | 変更内容 |
|---------|---------|
| `pages/players/PlayerEdit.jsx` (行357-375付近) | 管理団体ドロップダウン追加 |
| `pages/notifications/NotificationSettings.jsx` (行778付近) | `adminSameDayCancel` トグル追加 |

#### DB
| ファイル | 変更内容 |
|---------|---------|
| `database/add_admin_same_day_cancel_preference.sql` (新規) | カラム追加 |
| `database/update_line_message_log_notification_type_check.sql` (更新) | CHECK制約更新 |

#### ドキュメント
| ファイル | 変更内容 |
|---------|---------|
| `docs/SPECIFICATION.md` | 通知仕様・トグル表・画面仕様の更新 |
| `docs/DESIGN.md` | 設計変更の反映 |
| `docs/SCREEN_LIST.md` | PlayerEdit画面の変更反映 |

### 既存機能への影響

- **SUPER_ADMINの通知**: 動作変更なし。引き続き全団体の通知を受信
- **SAME_DAY_VACANCY の送信先**: **動作変更あり**。セッション参加者のみ → 団体全メンバーに拡大。セッションに登録していないプレイヤーにも届くようになる
- **ADMIN_SAME_DAY_CONFIRMATION のWONスキップ**: **動作変更あり**。WON参加者であってもスキップせず管理者通知を送信するように変更
- **選手用チャネルのSAME_DAY_CANCEL**: 変更なし。既存通り
- **通知設定画面**: 既にADMINにも管理者通知セクションが表示される条件（`isAdmin`判定）になっているため、表示ロジックの変更は不要。トグル追加のみ
- **LINE連携フロー**: 変更なし。ADMINが管理者用LINEをリンクしていない場合はスキップ（既存動作）

## 6. テスト要件

### 6.1 バックエンドユニットテスト

**LineNotificationService テスト**
- `ADMIN_SAME_DAY_CANCEL` 送信: キャンセル時・参加時にADMIN/SUPER_ADMINに届くこと
- `ADMIN_SAME_DAY_CONFIRMATION` 送信: WON参加者の管理者にもスキップせず届くこと
- `ADMIN_WAITLIST_UPDATE` 送信: 該当団体のADMINにも届くこと
- 送信先フィルタリング: ADMINは自団体のセッションのみ、SUPER_ADMINは全セッション
- `SAME_DAY_VACANCY` 送信先: 団体メンバー全員に届くこと（セッション未登録者含む）
- 管理者通知トグル: `organizationId=0` レコードのON/OFFが全ADMIN_系通知で正しく動作すること

**SameDayVacancyScheduler テスト**
- 条件判定: 抽選実行済み + WON < 定員 + WAITLISTED = 0 のときのみ通知すること
- 条件不一致: 抽選未実行、定員達成、WAITLISTED存在のいずれかで通知しないこと
- 試合単位: 1セッション内で条件を満たす試合のみ通知すること

### 6.2 フロントエンドテスト

**PlayerEdit**
- ロールをADMINに変更したときに管理団体ドロップダウンが表示されること
- 保存時に `updateAdminOrganization` APIが呼ばれること

**NotificationSettings**
- `adminSameDayCancel` トグルが管理者通知セクションに表示されること
- トグル操作時に `lineAPI.updatePreferences()` が `organizationId=0` で呼ばれること

## 7. 設計判断の根拠

### チャネル設計: 既存ADMINチャネル + フィルタリング方式を採用
- **理由**: `PracticeSession.organizationId` と `Player.adminOrganizationId` が既に存在するため、受信者選定ロジックの変更のみで実現可能。チャネルタイプの追加やDB変更が不要
- **却下案**: 団体ごとにチャネルタイプを分ける案 → チャネル管理の複雑化、既存の2タイプ（PLAYER/ADMIN）設計と不整合

### 通知タイプ: ADMIN_SAME_DAY_CANCEL を新設（キャンセル・参加兼用）
- **理由**: 既存の `getRequiredChannelType()` が `ADMIN_` プレフィックスで管理者チャネルに振り分ける設計。新タイプ追加でプレフィックスルールに自然に乗る。キャンセルと参加は管理者にとって把握したい度合いが並列なので同一トグルで制御

### トグル判定: organizationId=0 方式を継続
- **理由**: 既存の `ADMIN_SAME_DAY_CONFIRMATION` がこの方式で実装済み。ADMINは1団体のみに紐づくため、団体別のトグル管理は不要
