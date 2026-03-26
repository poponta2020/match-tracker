---
status: completed
---
# 抽選通知まとめ・キャンセル待ち辞退機能 要件定義書

## 1. 概要

### 目的
抽選結果の通知を、プレイヤーごとにまとめて送信する。現状は1試合ごとに個別通知が飛ぶため通知量が多く、ユーザーが確認しきれない。また、キャンセル待ちの辞退・復帰機能を新設し、締切後の新規キャンセル待ち参加にも対応する。

### 背景・動機
- 全試合当選の場合に大量の個別通知が届いても見ない
- キャンセル待ちを望まないプレイヤーが辞退する手段がない
- 抽選締切後に参加したいプレイヤーがキャンセル待ちに入れない

## 2. ユーザーストーリー

### 対象ユーザー
- **プレイヤー**: 抽選結果を受け取り、キャンセル待ちの辞退/復帰を行う
- **管理者（ADMIN / SUPER_ADMIN）**: 抽選結果通知を一括送信する

### 利用シナリオ

**シナリオ1: 全試合当選**
プレイヤーAが来月の練習に5件申し込み、すべて当選した。
→ 通知は1通「申し込んだ練習はすべて当選しました」のみ届く。

**シナリオ2: 一部落選（キャンセル待ち）**
プレイヤーBが5件申し込み、3件当選、2件（2セッションにまたがる）がキャンセル待ちになった。
→ LINE通知:
1. 「落選した試合があります」
2. セッションごとのFlex Message（日付・試合番号・キャンセル待ち番号 + 辞退ボタン）× 2通
3. 「これら以外の申し込みはすべて当選しています」
→ アプリ内通知: 同様の構成で個別レコード、フロントでグルーピング表示

**シナリオ3: 全試合落選**
プレイヤーCが3件申し込み、すべてキャンセル待ちになった。
→ シナリオ2の1と2のみ（3の「これら以外は〜」メッセージは省略）

**シナリオ4: キャンセル待ち辞退**
プレイヤーBが通知またはアプリ上で「キャンセル待ちを辞退する」を押す。
→ 該当セッションの全キャンセル待ちが `WAITLIST_DECLINED` に変更。後続の待ち番号が繰り上がる。

**シナリオ5: キャンセル待ち復帰**
プレイヤーBが辞退後、やはりキャンセル待ちしたいと思い復帰する。
→ `WAITLISTED` に戻り、キャンセル待ち最後尾の番号が付与される。

**シナリオ6: 締切後の新規キャンセル待ち参加**
抽選に申し込んでいなかったプレイヤーDが、締切後に定員超過の試合に参加登録する。
→ 自動的に `WAITLISTED`（最後尾）になる。

**シナリオ7: 締切後の空きがある試合への新規登録**
プレイヤーDが締切後に定員に空きのある試合に参加登録する。
→ 即 `WON` 扱いになる。

## 3. 機能要件

### 3.1 画面仕様

#### 通知一覧ページ（NotificationList.jsx）
- **グルーピング表示**: 同一月の抽選結果通知を視覚的にまとめて表示
  - 全当選通知（`LOTTERY_ALL_WON`）: 1行で「申し込んだ練習はすべて当選しました」
  - キャンセル待ち通知（`LOTTERY_WAITLISTED`）: セッション別に表示（日付・試合番号・キャンセル待ち番号）
  - 当選残り通知（`LOTTERY_REMAINING_WON`）: 「これら以外の申し込みはすべて当選しています」
- **キャンセル待ち辞退ボタン**: `LOTTERY_WAITLISTED` 通知内に「キャンセル待ちを辞退する」ボタンを配置
  - ステータスが `WAITLISTED` の場合のみ表示
  - 押下で確認ダイアログ → API呼び出し → ステータス更新

#### 抽選結果ページ（LotteryResults.jsx）
- **キャンセル待ち辞退ボタン**: 自分のキャンセル待ち試合の横に「辞退」ボタンを追加
  - ステータスが `WAITLISTED` の場合のみ表示
  - 押下で確認ダイアログ → API呼び出し → ステータス更新 → 画面リロード
- **ステータスバッジ追加**: `WAITLIST_DECLINED` 用のバッジ（例: グレー「待ち辞退」）

#### LINE Flex Message（セッション別キャンセル待ち通知）
```
┌─────────────────────────────┐
│  落選した試合のお知らせ       │ ← ヘッダー（赤系）
├─────────────────────────────┤
│  ○月○日                     │
│  ○試合目 キャンセル待ち○番   │
│  ○試合目 キャンセル待ち○番   │
├─────────────────────────────┤
│  [キャンセル待ちを辞退する]   │ ← ボタン（グレー）
└─────────────────────────────┘
```
- セッション単位で1つのFlex Message（Bubble）
- ボタン押下でpostback → そのセッションの全キャンセル待ちを辞退

### 3.2 ビジネスルール

#### 通知まとめルール
| 条件 | アプリ内通知 | LINE通知 |
|------|-------------|----------|
| 全試合当選 | `LOTTERY_ALL_WON` × 1レコード | テキスト1通 |
| 一部落選あり（当選もあり） | セッション別 `LOTTERY_WAITLISTED` + `LOTTERY_REMAINING_WON` × 1 | テキスト1通 + セッション別Flex + テキスト1通 |
| 全試合落選 | セッション別 `LOTTERY_WAITLISTED` のみ | テキスト1通 + セッション別Flexのみ |

#### 通知タイプの変更
| 旧タイプ | 新タイプ | 用途 |
|---------|---------|------|
| `LOTTERY_WON` | **廃止** | — |
| — | `LOTTERY_ALL_WON`（新規） | 全試合当選の一括通知 |
| — | `LOTTERY_REMAINING_WON`（新規） | 落選以外は全当選のまとめ通知 |
| `LOTTERY_WAITLISTED` | `LOTTERY_WAITLISTED`（既存改修） | セッション単位にまとめ（複数試合分を1レコードに） |

#### キャンセル待ち辞退ルール
- `WAITLISTED` → `WAITLIST_DECLINED` への変更のみ許可
- 辞退はセッション単位（そのセッション内の全キャンセル待ち試合をまとめて辞退）
- 辞退後、後続のキャンセル待ち番号を繰り上げる
  - 例: 3番が辞退 → 4番→3番、5番→4番...
- 辞退はいつでも可能（期限なし）

#### キャンセル待ち復帰ルール
- `WAITLIST_DECLINED` → `WAITLISTED` への復帰を許可
- 復帰時のキャンセル待ち番号は最後尾
- 復帰もセッション単位

#### 締切後新規登録ルール
- 抽選締切後かつ抽選実行済みの試合に新規参加登録する場合:
  - 定員超過 → `WAITLISTED`（最後尾番号）で登録
  - 定員に空き → `WON` で登録

#### ステータス体系
| ステータス | 意味 | 遷移元 |
|-----------|------|--------|
| `PENDING` | 申込済み（抽選前） | 初期状態 |
| `WON` | 当選（参加確定） | PENDING, OFFERED |
| `WAITLISTED` | キャンセル待ち中 | PENDING, WAITLIST_DECLINED |
| `OFFERED` | 繰り上げオファー中 | WAITLISTED |
| `DECLINED` | オファー辞退/期限切れ | OFFERED |
| `CANCELLED` | 当選後キャンセル | WON |
| `WAITLIST_DECLINED`（新規） | キャンセル待ち辞退 | WAITLISTED |

### 3.3 エラーケース・境界条件
- キャンセル待ち辞退済みの試合に再度辞退リクエスト → エラー返却
- `WAITLISTED` 以外のステータスから辞退 → エラー返却
- キャンセル待ち復帰時、既に `WAITLISTED` → エラー返却
- 締切後登録で、同一セッション・試合に既に参加済み → 既存のユニーク制約でエラー
- 当月の抽選がまだ実行されていない状態で締切後登録 → `PENDING` のまま（従来通り）

## 4. 技術設計

### 4.1 API設計

#### 新規エンドポイント

**POST /api/lottery/decline-waitlist**
キャンセル待ち辞退（セッション単位）
```
リクエスト:
{
  "sessionId": 123,
  "playerId": 456
}

レスポンス: 200 OK
{
  "declinedCount": 2,
  "message": "2件のキャンセル待ちを辞退しました"
}

エラー:
- 400: 辞退対象のキャンセル待ちがない
- 404: セッションが見つからない
```

**POST /api/lottery/rejoin-waitlist**
キャンセル待ち復帰（セッション単位）
```
リクエスト:
{
  "sessionId": 123,
  "playerId": 456
}

レスポンス: 200 OK
{
  "rejoinedCount": 2,
  "message": "キャンセル待ちに復帰しました（2件）"
}

エラー:
- 400: 復帰対象がない（WAITLIST_DECLINED状態のものがない）
- 404: セッションが見つからない
```

#### 既存エンドポイントの変更

**POST /api/lottery/notify-results**
- 通知まとめロジックに変更（プレイヤーごとにグルーピングして送信）
- レスポンス形式は変更なし

**POST /api/practice-sessions/participations**（参加登録）
- 締切後 + 抽選実行済み + 定員超過の場合、`WAITLISTED`（最後尾）で登録するロジックを追加
- 締切後 + 抽選実行済み + 定員に空きの場合、`WON` で登録

### 4.2 DB設計

#### テーブル変更

**practice_participants テーブル**
- `status` カラムの許容値に `WAITLIST_DECLINED` を追加
- DDL変更不要（VARCHAR(20) のため文字列として格納可能）

**notifications テーブル**
- `type` カラムの許容値に `LOTTERY_ALL_WON`、`LOTTERY_REMAINING_WON` を追加
- `LOTTERY_WON` タイプは廃止（新規作成しないが、既存データは残す）
- DDL変更不要（VARCHAR(30) のため文字列として格納可能）

#### 新規テーブル
なし

### 4.3 フロントエンド設計

#### コンポーネント変更

**NotificationList.jsx（改修）**
- 通知リストをタイプ別にグルーピングして表示するロジックを追加
  - `LOTTERY_WAITLISTED` 通知にはインラインで「キャンセル待ちを辞退する」ボタンを表示
  - `LOTTERY_ALL_WON` / `LOTTERY_REMAINING_WON` 用のアイコン・カラーを追加
- 新しい通知タイプの表示対応:
  - `LOTTERY_ALL_WON`: 緑アイコン「全当選」
  - `LOTTERY_REMAINING_WON`: 緑アイコン
  - `LOTTERY_WAITLISTED`: 黄色アイコン（既存、ただしメッセージがセッション単位に変更）

**LotteryResults.jsx（改修）**
- 自分のキャンセル待ち試合の横に「辞退」ボタンを追加
- `WAITLIST_DECLINED` ステータスのバッジ追加（グレー「待ち辞退」）
- 辞退済みの場合は「復帰」ボタンを表示

#### API モジュール追加

**lottery.js に追加:**
```javascript
declineWaitlist(sessionId, playerId)  // POST /lottery/decline-waitlist
rejoinWaitlist(sessionId, playerId)   // POST /lottery/rejoin-waitlist
```

### 4.4 バックエンド設計

#### Enum変更

**ParticipantStatus.java**
- `WAITLIST_DECLINED` を追加

**NotificationType（Notification.java内）**
- `LOTTERY_ALL_WON` を追加
- `LOTTERY_REMAINING_WON` を追加
- `LOTTERY_WON` は残す（既存データ参照用）が、新規作成では使用しない

#### Service変更

**NotificationService.java**
- `createLotteryResultNotifications()` を全面改修
  - 引数: `List<PracticeParticipant>` → プレイヤーごとにグルーピング
  - 全当選判定 → `LOTTERY_ALL_WON` 1レコード作成
  - 一部落選 → セッション別に `LOTTERY_WAITLISTED` レコード作成 + `LOTTERY_REMAINING_WON` 1レコード作成
  - 全落選 → セッション別に `LOTTERY_WAITLISTED` レコードのみ作成

**LineNotificationService.java**
- `sendLotteryResults()` を全面改修
  - プレイヤーごとにグルーピング
  - 全当選: テキスト1通
  - 一部落選: テキスト（イントロ）+ セッション別Flex + テキスト（クロージング）
  - 全落選: テキスト（イントロ）+ セッション別Flexのみ
- `buildLotteryWaitlistedFlex()` 新規メソッド
  - セッション単位のキャンセル待ち情報をFlex Messageに構築
  - 辞退ボタン付き（postback: `action=waitlist_decline_session&sessionId={id}&playerId={id}`）

**WaitlistPromotionService.java**
- `declineWaitlistBySession()` 新規メソッド
  - セッション内の `WAITLISTED` をすべて `WAITLIST_DECLINED` に変更
  - 後続の待ち番号を繰り上げ
- `rejoinWaitlistBySession()` 新規メソッド
  - セッション内の `WAITLIST_DECLINED` をすべて `WAITLISTED` に変更
  - 各試合の最後尾番号を付与

**PracticeParticipantService.java（既存の参加登録ロジック）**
- `registerParticipations()` を改修
  - 締切後 + 抽選実行済みの場合の分岐を追加
  - 定員超過 → `WAITLISTED`（最後尾）
  - 定員に空き → `WON`

#### Controller変更

**LotteryController.java**
- `declineWaitlist()` 新規エンドポイント
- `rejoinWaitlist()` 新規エンドポイント
- LINE postback ハンドラにキャンセル待ち辞退アクションを追加

#### Repository変更

**PracticeParticipantRepository.java**
- `findBySessionIdAndPlayerIdAndStatus(Long sessionId, Long playerId, ParticipantStatus status)` 追加
- `findBySessionIdAndMatchNumberAndStatusAndWaitlistNumberGreaterThan(...)` 追加（番号繰り上げ用）

## 5. 影響範囲

### 変更が必要な既存ファイル

#### バックエンド
| ファイル | 変更内容 |
|---------|---------|
| `entity/ParticipantStatus.java` | `WAITLIST_DECLINED` 追加 |
| `entity/Notification.java` | `LOTTERY_ALL_WON`, `LOTTERY_REMAINING_WON` 追加 |
| `service/NotificationService.java` | `createLotteryResultNotifications()` 全面改修 |
| `service/LineNotificationService.java` | `sendLotteryResults()` 全面改修、Flex Messageビルダー追加 |
| `service/WaitlistPromotionService.java` | 辞退・復帰メソッド追加、番号繰り上げロジック |
| `service/PracticeParticipantService.java` | 締切後登録ロジック追加 |
| `controller/LotteryController.java` | 辞退・復帰エンドポイント追加 |
| `repository/PracticeParticipantRepository.java` | クエリメソッド追加 |

#### フロントエンド
| ファイル | 変更内容 |
|---------|---------|
| `pages/notifications/NotificationList.jsx` | グルーピング表示、辞退ボタン追加 |
| `pages/lottery/LotteryResults.jsx` | 辞退/復帰ボタン、新ステータスバッジ追加 |
| `api/lottery.js` | `declineWaitlist()`, `rejoinWaitlist()` 追加 |

### 既存機能への影響
- **繰り上げフロー**: `WAITLIST_DECLINED` のプレイヤーは繰り上げ対象外とする（`WAITLISTED` のみが対象、これは既存ロジックのまま）
- **抽選結果ページの表示**: `WAITLIST_DECLINED` バッジの追加が必要
- **既存通知データ**: 旧 `LOTTERY_WON` タイプの通知は表示上そのまま残す（後方互換）
- **参加登録フロー**: 締切後の挙動が変わるため、フロントの登録画面で「キャンセル待ちになります」等の案内表示が望ましい

## 6. 設計判断の根拠

### 通知をDBレコード個別 + フロント側グルーピング（案B）にした理由
- 個別レコードなら既読管理が通知単位で可能
- キャンセル待ち辞退ボタンとの連動が容易（referenceId でセッション特定）
- 既存の通知取得API・表示ロジックの変更を最小限にできる

### キャンセル待ち辞退をセッション単位にした理由
- LINE Flex Message がセッション単位のため、操作粒度を合わせた
- 試合単位だとボタン数が多くなりUIが煩雑になる

### `WAITLIST_DECLINED` を `CANCELLED` と分けた理由
- `CANCELLED` は当選後のキャンセル（繰り上げフローのトリガー）
- `WAITLIST_DECLINED` はキャンセル待ちの辞退（繰り上げフローに影響しない）
- 混同するとデータ分析・ステータス遷移の管理が複雑になる

### 締切後の登録で定員に空きがあれば即 `WON` にした理由
- 抽選が終わっている以上、空きがあるなら待たせる理由がない
- ユーザー体験として直感的
