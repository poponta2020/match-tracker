---
status: completed
---
# 参加登録画面からの登録解除禁止（締切後） 実装手順書

## 実装タスク

### タスク1: PlayerParticipationStatusDto に beforeDeadline フィールドを追加
- [x] 完了
- **概要:** レスポンスDTOに締切前/後の情報を追加し、フロントエンドが締切状態を判定できるようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerParticipationStatusDto.java` — `private Boolean beforeDeadline` フィールドを追加
- **依存タスク:** なし
- **対応Issue:** #50

### タスク2: バックエンドで beforeDeadline を算出してセット
- [x] 完了
- **概要:** ステータス取得処理で `LotteryDeadlineHelper.isBeforeDeadline()` を呼び出し、DTOにセットする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — `getPlayerParticipationStatus` メソッドで `beforeDeadline` を算出してDTOにセット
- **依存タスク:** タスク1
- **対応Issue:** #51

### タスク3: フロントエンドで締切後の既存登録チェックボックスを disabled 化
- [x] 完了
- **概要:** 参加登録画面で、締切後かつサーバーに保存済みの登録のチェックボックスを disabled＋グレーアウトにする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`
    - `statusRes.data` から `beforeDeadline` を取得して state に保持
    - `toggleMatch` 関数: 締切後かつ `initialParticipations` に含まれる試合のチェック外しを無視する制御を追加
    - チェックボックス描画部: 締切後かつ `initialParticipations` に含まれる場合、`disabled={true}` + グレーアウトスタイルを適用
- **依存タスク:** タスク2
- **対応Issue:** #52

### タスク4: バックエンドのテスト追加
- [x] 完了
- **概要:** `beforeDeadline` が正しくセットされることを確認するテストを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeParticipantServiceTest.java` — `getPlayerParticipationStatus` のテストで `beforeDeadline` の値を検証
- **依存タスク:** タスク2
- **対応Issue:** #53

## 実装順序
1. タスク1（依存なし — DTO変更）
2. タスク2（タスク1に依存 — サービス変更）
3. タスク3（タスク2に依存 — フロントエンド変更）
4. タスク4（タスク2に依存 — テスト追加、タスク3と並行可能）
