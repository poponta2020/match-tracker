# 統計

> **責務:** 選手の対戦成績（総対戦数・勝率）および対戦相手の級別勝率の集計・表示
> **関連画面:** `/matches`（対戦結果一覧内の級別統計表示）
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchController.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchStatisticsDto.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/dto/StatisticsByRankDto.java`、`karuta-tracker-ui/src/pages/matches/MatchList.jsx`

## 機能仕様

### 選手別統計（実装済み — PlayerDetail画面内）

| 指標 | 計算方法 |
|---|---|
| 総対戦数 | 選手が参加した全試合の件数 |
| 勝利数 | `winnerId` が自分のIDに一致する件数 |
| 勝率 | (勝利数 / 総対戦数) × 100、小数第1位で丸め |

### 級別統計（実装済み）

対戦相手の `kyuRank`（A級〜E級）ごとに勝率を算出。
以下のフィルタを適用可能:

- 対戦相手の性別
- 対戦相手の利き手
- 期間（開始日〜終了日）

### 統計専用ページ

**ステータス: 未実装（プレースホルダーのみ）**

`/statistics` ルートは存在するが「実装中...」と表示されるのみ。
