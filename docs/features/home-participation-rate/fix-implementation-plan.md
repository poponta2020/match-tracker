---
status: completed
---
# ホーム画面参加率 改修実装手順書

## 実装タスク

### タスク1: ParticipationGroupDto 新規作成
- [x] 完了
- **概要:** 団体別参加率グループを表現する新DTOを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/ParticipationGroupDto.java` — 新規作成（organizationId, organizationName, top3, myRate）
- **依存タスク:** なし
- **対応Issue:** #185

### タスク2: HomeDto のフィールド変更
- [x] 完了
- **概要:** 旧フィールド（participationTop3, myParticipationRate）を削除し、participationGroups に置き換え
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/HomeDto.java` — participationTop3, myParticipationRate を削除、participationGroups (List<ParticipationGroupDto>) を追加
- **依存タスク:** タスク1
- **対応Issue:** #186

### タスク3: PracticeParticipantService に団体フィルタ対応追加
- [x] 完了
- **概要:** computeAllParticipationRates に organizationId フィルタ版を追加。getParticipationRateTop3 / getPlayerParticipationRate も対応。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — computeAllParticipationRates(year, month, organizationId) オーバーロード追加。computeAllParticipationRates(year, month, List<Long> orgIds) 全体合算用追加。getParticipationRateTop3 / getPlayerParticipationRate にorganizationId版追加。
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PlayerOrganizationRepository.java` — findPlayerIdsByOrganizationId クエリ追加（必要に応じて）
- **依存タスク:** なし
- **対応Issue:** #187

### タスク4: HomeController の参加率ロジック改修
- [x] 完了
- **概要:** playerIdから所属団体を取得し、1団体/複数団体に応じてParticipationGroupDtoリストを組み立てる
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/HomeController.java` — OrganizationService注入、団体別参加率ロジック実装、HomeDtoの組み立て変更
- **依存タスク:** タスク1, タスク2, タスク3
- **対応Issue:** #188

### タスク5: フロントエンド Home.jsx の表示改修
- [x] 完了
- **概要:** participationGroups に対応した表示ロジックに変更。1団体→ラベルなし、複数団体→団体名ラベル付きで繰り返し描画。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/Home.jsx` — participationTop3/myParticipationRate → participationGroups に変更。複数グループ時のセクション繰り返し描画実装。
- **依存タスク:** タスク4
- **対応Issue:** #189

## 実装順序
1. タスク1（依存なし）— ParticipationGroupDto作成
2. タスク2（タスク1に依存）— HomeDto変更
3. タスク3（依存なし、タスク1,2と並行可能）— Service層の団体フィルタ対応
4. タスク4（タスク1,2,3に依存）— Controller改修
5. タスク5（タスク4に依存）— フロントエンド改修
