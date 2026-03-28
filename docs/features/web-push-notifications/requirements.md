---
status: completed
---
# Web Push通知 要件定義書（ドラフト）

## 1. 概要
既存のアプリ内通知をWeb Push通知でも配信できるようにする。Web Pushの基盤（バックエンド送信機能・DB・API）は一部構築済みだが、フロントエンドのService Worker・購読フロー・設定UIが未実装。現在はWAITLIST_OFFERのみWeb Push対応しているが、全通知種別に拡大する。

## 2. ユーザーストーリー
### 対象ユーザー
- 全ロール（SUPER_ADMIN / ADMIN / PLAYER）
- 通知種別に応じて適切なロールのユーザーにのみ送信される（例: DENSUKE_UNMATCHED_NAMESは管理者のみ）

### ユーザーの目的
- アプリを開いていなくても、重要な通知（抽選結果、繰り上げ連絡など）をリアルタイムで受け取りたい
- 不要な通知はWeb Push種別単位でOFFにしたい

### 利用シナリオ
1. ユーザーが通知設定画面でWeb Push通知を有効化する
2. ブラウザの通知許可ダイアログが表示され、許可する
3. 以降、アプリ内通知が作成されるたびに、Web Pushでも同じ通知が配信される
4. 通知設定画面で種別ごとにON/OFFを切り替えられる

### 設計方針
- **アプリ内通知**: 全種別で常にON（設定UI不要）
- **Web Push通知**: 全9種別に対応、種別ごとにON/OFF可能
- **LINE通知設定**: 既存のまま維持
- **通知設定画面**: `/settings/line` を `/settings/notifications` に統合・拡張

## 3. 機能要件

### 3.1 画面仕様

#### 通知設定画面（`/settings/notifications`）
既存の `/settings/line` を統合・拡張する。

**画面構成:**
```
通知設定
├── Web Push通知
│   ├── 有効化/無効化ボタン（トグルまたはボタン）
│   └── 種別ごとのON/OFFトグル（Web Push有効時のみ表示）
│       ├── 抽選結果（LOTTERY_ALL_WON / LOTTERY_REMAINING_WON / LOTTERY_WAITLISTED をまとめて1トグル）
│       ├── キャンセル待ち繰り上げ（WAITLIST_OFFER）
│       ├── 繰り上げ期限切れ警告（OFFER_EXPIRING）
│       ├── 繰り上げ期限切れ（OFFER_EXPIRED）
│       ├── LINEチャネル回収警告（CHANNEL_RECLAIM_WARNING）※ADMIN/SUPER_ADMINのみ表示
│       └── 伝助未登録者（DENSUKE_UNMATCHED_NAMES）※ADMIN/SUPER_ADMINのみ表示
└── LINE通知（既存のLineSettings.jsxの内容をそのまま移植）
    ├── LINE連携状態・ワンタイムコード表示
    └── 6種別のON/OFFトグル
```

**表示ルール:**
- Web Push種別トグルは、Web Pushが有効な場合のみ表示（無効時は非表示）
- 管理者向け通知種別（CHANNEL_RECLAIM_WARNING, DENSUKE_UNMATCHED_NAMES）はADMIN/SUPER_ADMINにのみ表示
- LOTTERY_WON（廃止版）は設定画面から除外

#### Web Push有効化フロー
1. 「Web Push通知を有効にする」ボタン押下
2. ブラウザの通知許可ダイアログ表示
3. 許可された場合: Service Worker登録 → Push購読作成 → バックエンドに購読情報送信 → 種別トグル表示
4. 拒否された場合: 「ブラウザの通知設定から通知を許可してください」ガイドメッセージ表示
5. ブラウザ設定で通知がブロック済みの場合: 許可ダイアログを出せないため、同様のガイドメッセージ表示

#### Web Push無効化
- 「無効にする」操作時はバックエンドの購読情報を削除せず、フラグで無効化する
- 再有効化時にブラウザ許可の再取得が不要

### 3.2 ビジネスルール

**対応通知種別（Web Push設定の粒度）:**

| 設定項目名 | 対応するNotificationType | 対象ロール |
|---|---|---|
| 抽選結果 | LOTTERY_ALL_WON, LOTTERY_REMAINING_WON, LOTTERY_WAITLISTED | 全ロール |
| キャンセル待ち繰り上げ | WAITLIST_OFFER | 全ロール |
| 繰り上げ期限切れ警告 | OFFER_EXPIRING | 全ロール |
| 繰り上げ期限切れ | OFFER_EXPIRED | 全ロール |
| LINEチャネル回収警告 | CHANNEL_RECLAIM_WARNING | ADMIN / SUPER_ADMIN |
| 伝助未登録者 | DENSUKE_UNMATCHED_NAMES | ADMIN / SUPER_ADMIN |

**デフォルト設定:** Web Push有効化時、全種別デフォルトON

**複数デバイス対応:** 1ユーザーが複数デバイスで購読登録可能。全デバイスに通知を配信する。

**Web Push通知クリック時の遷移先:**

| 通知種別 | 遷移先 |
|---|---|
| 抽選結果（ALL_WON / REMAINING_WON / WAITLISTED） | `/practice` |
| WAITLIST_OFFER | `/notifications` |
| OFFER_EXPIRING | `/notifications` |
| OFFER_EXPIRED | `/notifications` |
| CHANNEL_RECLAIM_WARNING | `/settings/notifications` |
| DENSUKE_UNMATCHED_NAMES | `/admin/densuke` |

**エラーケース:**
- ブラウザが通知をサポートしていない場合: 「お使いのブラウザはWeb Push通知に対応していません」メッセージ表示
- ブラウザで通知がブロックされている場合: 「ブラウザの設定から通知を許可してください」ガイドメッセージ表示
- Push購読の送信失敗時: エラーメッセージ表示、リトライ可能

## 4. 技術設計

### 4.1 API設計

#### 既存エンドポイント（変更なし）
| メソッド | URL | 機能 |
|---|---|---|
| GET | `/api/push-subscriptions/vapid-public-key` | VAPID公開鍵取得 |
| POST | `/api/push-subscriptions` | Push購読登録 |
| DELETE | `/api/push-subscriptions` | Push購読解除 |

#### 新規エンドポイント
| メソッド | URL | 機能 |
|---|---|---|
| GET | `/api/push-notification-preferences/{playerId}` | Web Push設定取得 |
| PUT | `/api/push-notification-preferences` | Web Push設定更新 |

**GET レスポンス例:**
```json
{
  "playerId": 1,
  "enabled": true,
  "lotteryResult": true,
  "waitlistOffer": true,
  "offerExpiring": true,
  "offerExpired": true,
  "channelReclaimWarning": true,
  "densukeUnmatched": true
}
```

**PUT リクエスト例:**
```json
{
  "playerId": 1,
  "enabled": true,
  "lotteryResult": true,
  "waitlistOffer": false,
  "offerExpiring": true,
  "offerExpired": true,
  "channelReclaimWarning": true,
  "densukeUnmatched": true
}
```

### 4.2 DB設計

#### 新規テーブル: `push_notification_preferences`
| カラム | 型 | 制約 | デフォルト | 説明 |
|---|---|---|---|---|
| id | BIGSERIAL | PK | - | |
| player_id | BIGINT | FK, UNIQUE, NOT NULL | - | プレイヤーID |
| enabled | BOOLEAN | NOT NULL | false | Web Push全体のON/OFF |
| lottery_result | BOOLEAN | NOT NULL | true | 抽選結果 |
| waitlist_offer | BOOLEAN | NOT NULL | true | 繰り上げ連絡 |
| offer_expiring | BOOLEAN | NOT NULL | true | 期限切れ警告 |
| offer_expired | BOOLEAN | NOT NULL | true | 期限切れ |
| channel_reclaim_warning | BOOLEAN | NOT NULL | true | LINE回収警告 |
| densuke_unmatched | BOOLEAN | NOT NULL | true | 伝助未登録者 |
| created_at | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | |

インデックス: `idx_pnp_player` ON `player_id`

### 4.3 フロントエンド設計

#### Service Worker（`public/sw.js`）
- Push通知の受信・表示処理
- 通知クリック時の画面遷移（ペイロードに含まれるURLに遷移）

#### 通知設定画面（`/settings/notifications`）
- 新コンポーネント `NotificationSettings.jsx` を作成
- 既存の `LineSettings.jsx` の内容をセクションとして組み込み
- Web Pushセクションを追加

#### Service Worker登録・購読フロー
```
「有効にする」ボタン押下
→ notificationAPI.getVapidPublicKey() でVAPID公開鍵取得
→ navigator.serviceWorker.register('/sw.js')
→ registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: publicKey })
→ notificationAPI.subscribePush({ playerId, endpoint, p256dhKey, authKey }) でバックエンドに登録
→ push_notification_preferences.enabled = true に更新
```

### 4.4 バックエンド設計

#### 新規クラス
| クラス | 層 | 説明 |
|---|---|---|
| `PushNotificationPreference` | Entity | push_notification_preferences テーブルのマッピング |
| `PushNotificationPreferenceDto` | DTO | リクエスト/レスポンス用DTO |
| `PushNotificationPreferenceRepository` | Repository | CRUD |

#### 既存クラスの変更
| クラス | 変更内容 |
|---|---|
| `NotificationService` | `createAndPush()` 共通メソッド追加。通知保存 + Web Push設定チェック + 送信を一括実行 |
| `NotificationService` | `createOfferNotification()` を `createAndPush()` を使うようリファクタ |
| `NotificationService` | `createLotteryResultNotifications()` に Web Push送信を追加 |
| `NotificationService` | `createOfferExpiredNotification()` に Web Push送信を追加 |
| `PushNotificationService` | 設定チェック（preferences）の参照を追加 |
| `PushSubscriptionController` | 設定APIエンドポイント（GET/PUT）を追加 |
| `LineChannelReclaimScheduler` | 直接 `repository.save()` → `notificationService.createAndPush()` に変更 |

#### DensukeImportService の扱い
`DensukeImportService.notifyAdminsOfUnmatchedNames()` は重複判定ロジックが複雑なため、`createAndPush()` には置き換えず、既存ロジックを維持した上で通知保存後にWeb Push送信のみ追加する。

#### 通知作成の共通メソッド
```java
public Notification createAndPush(Long playerId, NotificationType type,
                                   String title, String message,
                                   String referenceType, Long referenceId, String pushUrl) {
    // 1. アプリ内通知をDBに保存（常に実行）
    Notification notification = Notification.builder()
        .playerId(playerId).type(type).title(title).message(message)
        .referenceType(referenceType).referenceId(referenceId).build();
    notificationRepository.save(notification);

    // 2. Web Push設定チェック → 該当種別がONなら送信
    sendPushIfEnabled(playerId, type, title, message, pushUrl);

    return notification;
}
```

#### VAPID鍵管理
環境変数で管理（既存の仕組みをそのまま利用）:
- `VAPID_PUBLIC_KEY` — 公開鍵
- `VAPID_PRIVATE_KEY` — 秘密鍵
- `VAPID_SUBJECT` — 連絡先（デフォルト: `mailto:admin@example.com`）

## 5. 影響範囲

### 変更が必要な既存ファイル

#### バックエンド
| ファイル | 変更内容 |
|---|---|
| `NotificationService.java` | `createAndPush()` 共通メソッド追加、既存通知作成メソッドのリファクタ |
| `PushNotificationService.java` | 設定チェック（preferences参照）の組み込み |
| `PushSubscriptionController.java` | 設定API（GET/PUT）エンドポイント追加 |
| `LineChannelReclaimScheduler.java` | `repository.save()` → `notificationService.createAndPush()` に変更 |
| `DensukeImportService.java` | 通知保存後にWeb Push送信を追加（既存ロジックは維持） |

#### フロントエンド
| ファイル | 変更内容 |
|---|---|
| `App.jsx` | `/settings/line` → `/settings/notifications` にルーティング変更 |
| `NavigationMenu.jsx` | メニューリンクのパス・ラベル変更（`/settings/notifications`、「通知設定」） |
| `notifications.js`（APIクライアント） | 設定API呼び出し追加 |

#### DB
| 変更 | 内容 |
|---|---|
| 新規テーブル | `push_notification_preferences` |
| 既存テーブル変更 | なし |

### 既存機能への影響
- **LINE通知**: 影響なし（`LineNotificationService` は変更しない）
- **アプリ内通知の表示・既読管理**: 影響なし（`NotificationController` は変更しない）
- **既存のPush購読API**: 影響なし（エンドポイント追加のみ）
- **`/settings/line` URL**: 削除される。ブックマーク等からのアクセスは404になる

## 6. 設計判断の根拠

| 判断 | 理由 |
|---|---|
| `push_notification_preferences` を新規テーブルとして作成 | LINE設定（`line_notification_preferences`）と粒度・種別が異なる。統合するとLINE側のリファクタが必要になりデグレリスクが増す |
| Web Push無効化をフラグ管理（購読削除しない） | 再有効化時にブラウザ許可の再取得が不要になり、UXが良い |
| `createAndPush()` 共通メソッドでアプリ内通知+Web Pushを一括処理 | 新規通知種別追加時にWeb Push対応漏れを防ぐ。保守性向上 |
| DensukeImportServiceは共通メソッドに置き換えない | 重複判定ロジック（同一メッセージスキップ・内容変更時の更新）が複雑で、共通化すると既存ロジックが壊れるリスクがある |
| 抽選結果3種別を1トグルにまとめる | ユーザー視点では「抽選結果の通知が欲しいか否か」で、当選/落選を分けて設定する必要はない |
| Service Workerを `public/sw.js` に配置 | Viteのpublic配下はルートに配信されるため、`/sw.js` としてスコープが `/` になる |
| プレイヤー単位のON/OFF（デバイス単位ではない） | PC無視でシンプルな実装を優先 |
