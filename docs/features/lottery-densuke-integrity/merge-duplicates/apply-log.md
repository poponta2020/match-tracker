# A-4-b 統合 適用ログ

| 日時(JST) | 操作 | 結果 |
|---|---|---|
| 2026-07-05 | 統合ツール・調査クエリ・手順を整備（`MergeDuplicates.java` / `discover.sql` / `README.md` / `pairs.txt`） | 完了 |
| 2026-07-05 | 本番 Postgres 疎通確認（`dbtool/Q.java` + IPv4強制） | **失敗**: 認証段階で `EOFException`（Render 無料枠スピンダウン or 接続情報ローテーションの可能性）。調査・DRY-RUN・適用は保留 |

## 保留中（次アクション）
1. 最新の本番接続情報を反映（Render Connect タブ → `CLAUDE.local.md` / `MergeDuplicates.java` / `dbtool/Q.java`）。
2. `discover.sql [1][2][3]` を実行し 4ペアの id 確定・FK確認・衝突ゼロ確認。
3. `pairs.txt` を記入し **DRY-RUN**（`--apply` なし）で全件 OK を確認。
4. **ユーザー承認後** `--apply` で本番適用し、`discover.sql [1]` で `active_namesakes=0` を検証。
5. 本ログに DRY-RUN / 適用 / 検証の結果を追記。

## 川瀬/高橋/山野/むらやま（4ペア）の後日談
本日中に別途 `c:\tmp\dbtool\MergeEmojiDups.java`（本ツールと同方式、`player_organizations`等は削除でなくrepoint）で4ペアとも本番統合済み（詳細はIssue #977コメント参照）。

## 谷口晴哉（5件目・新規発見）

| 日時(JST) | 操作 | 結果 |
|---|---|---|
| 2026-07-05 17:38頃 | ユーザーよりLINE通知報告（名寄せ衝突）。Issue #993 作成 | - |
| 2026-07-05 | `q_taniguchi2.sql` / `pair_check_taniguchi.sql` で id=88(U+FE0F付き,2026-03-30作成)→id=49(正常表記,2026-03-15作成) を確定。参照は`practice_participants`6件のみ、衝突0件を確認 | 完了 |
| 2026-07-05 | DB全体重複スキャンで谷口晴哉が唯一の未解消重複と確認（他ペアなし） | 完了 |
| 2026-07-05 | `pairs.txt` に `88,49` を追記し本ツールで DRY-RUN | 全件OK、衝突なし |
| 2026-07-05 | ユーザー承認後 `--apply` で本番COMMIT | **COMMITTED** |
| 2026-07-05 17:57 | 検証: `players.id=88.deleted_at` 設定済み、重複スキャン `active_namesakes=0`（全体） | 解消確認済み |

Issue: https://github.com/poponta2020/match-tracker/issues/993（クローズ済み）
