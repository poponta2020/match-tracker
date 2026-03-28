---
status: completed
---
# システム設定管理画面 実装手順書

## 実装タスク

### タスク1: バックエンド — LotteryDeadlineHelper に「締め切りなし」対応を追加
- [x] 完了
- **概要:** `lottery_deadline_days_before` が `-1` の場合の挙動を追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java` — `isNoDeadline()` メソッド追加、`getDeadline()` で `-1` 時に null 返却、`isBeforeDeadline()` で `-1` 時に常に true
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/SystemSettingService.java` — `isNoDeadline()` ヘルパーメソッド追加（`getLotteryDeadlineDaysBefore() == -1`）
- **依存タスク:** なし
- **対応Issue:** #63

### タスク2: バックエンド — 手動抽選の締め切り前ガード調整
- [x] 完了
- **概要:** 「締め切りなし」モード時に手動抽選の締め切り前チェックをスキップする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `executeLottery()` の `isBeforeDeadline` チェックに `isNoDeadline` の条件を追加
- **依存タスク:** タスク1
- **対応Issue:** #64

### タスク3: バックエンド — 締め切り日時取得エンドポイント追加
- [x] 完了
- **概要:** `GET /api/lottery/deadline?year={year}&month={month}` エンドポイントを新設。レスポンス: `{ "deadline": "2026-03-29T00:00:00", "noDeadline": false }`
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `getDeadline()` エンドポイント追加
- **依存タスク:** タスク1
- **対応Issue:** #65

### タスク4: フロントエンド — APIクライアント作成
- [x] 完了
- **概要:** システム設定の取得・更新と、締め切り日時取得のAPIクライアントを作成する
- **変更対象ファイル:**
  - **新規** `karuta-tracker-ui/src/api/systemSettings.js` — `getAll()`, `update(key, value)` メソッド
  - `karuta-tracker-ui/src/api/index.js` — エクスポートに追加（存在する場合）
- **依存タスク:** なし
- **対応Issue:** #66

### タスク5: フロントエンド — システム設定管理画面の作成
- [x] 完了
- **概要:** 管理者向けのシステム設定画面を作成する。締め切り日数（「締め切りなし」チェックボックス付き）と一般枠保証割合の2項目。保存確認ダイアログ、保存成功トースト、締め切りプレビュー表示を含む
- **変更対象ファイル:**
  - **新規** `karuta-tracker-ui/src/pages/settings/SystemSettings.jsx` — 管理画面コンポーネント
  - `karuta-tracker-ui/src/App.jsx` — `/admin/settings` ルーティング追加
- **依存タスク:** タスク4
- **対応Issue:** #67

### タスク6: フロントエンド — ナビゲーションメニューに「システム設定」追加
- [x] 完了
- **概要:** ハンバーガーメニューにADMIN以上向けの「システム設定」リンクを追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/NavigationMenu.jsx` — ADMIN以上向けメニュー項目追加（lucide-react の Settings アイコン使用）
- **依存タスク:** タスク5
- **対応Issue:** #68

### タスク7: フロントエンド — 参加登録画面に締め切り表示を追加
- [x] 完了
- **概要:** 参加登録画面の上部に「締め切り: ○月○日（あと○日）」を表示する。締め切り後および「締め切りなし」モード時は非表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx` — 締め切り日時取得APIの呼び出しと表示コンポーネント追加
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #69

### タスク8: バックエンドテスト
- [x] 完了
- **概要:** LotteryDeadlineHelper の「締め切りなし」対応と、新規エンドポイントのテストを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LotteryDeadlineHelperTest.java` — `-1` 時の各メソッドの挙動テスト
- **依存タスク:** タスク1, タスク2, タスク3
- **対応Issue:** #70

## 実装順序
1. タスク1: LotteryDeadlineHelper「締め切りなし」対応（依存なし）
2. タスク4: APIクライアント作成（依存なし、タスク1と並行可能）
3. タスク2: 手動抽選ガード調整（タスク1に依存）
4. タスク3: 締め切り日時取得エンドポイント（タスク1に依存）
5. タスク5: システム設定管理画面（タスク4に依存）
6. タスク6: ナビゲーションメニュー追加（タスク5に依存）
7. タスク7: 参加登録画面の締め切り表示（タスク3, タスク4に依存）
8. タスク8: バックエンドテスト（タスク1〜3に依存）
