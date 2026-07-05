# A-4-b 統合 適用ログ

| 日時(JST) | 操作 | 結果 |
|---|---|---|
| 2026-07-05 | 統合ツール・調査クエリ・手順を整備（`MergeDuplicates.java` / `discover.sql` / `README.md` / `pairs.txt`） | 完了 |
| 2026-07-05 | 本番 Postgres 疎通確認（`dbtool` + IPv4強制）＝当初 `EOFException` | 原因: DB が**月次ローテーション**し `CLAUDE.local.md` が旧休止DBを指していた（新DB=`karuta_tracker_0txw`/`dpg-d939fpvavr4c73bm96ag-a` は `available`）。Render API で現行接続情報を取得し復旧 |
| 2026-07-05 | CHECK制約マイグレーション本番適用（`add_densuke_name_collision_notification_type_check.sql` / `add_admin_densuke_confirm_diff_message_log_check.sql`） | **適用・検証済**（`notifications.type` に `DENSUKE_NAME_COLLISION`、`line_message_log.notification_type` に `ADMIN_DENSUKE_CONFIRM_DIFF`/`ADMIN_DENSUKE_NAME_COLLISION`/`ADMIN_DENSUKE_ROWID_ISSUE` が含まれることを `pg_get_constraintdef` で確認） |
| 2026-07-05 | 重複4名（🔰双子）の特定・衝突チェック | 対象確定: 川瀬 43→65 / 高橋 44→66 / 山野 52→68 / むらやま 59→70（🔰版=FROM→クリーン版=TO/master）。conflict（pp_overlap / org_overlap / self-match / uq-dup）**全ペア0** |
| 2026-07-05 | DRY-RUN（`MergeEmojiDups.java`、`--apply` なし） | 全ペア・全FK再ポイントが `affected==事前count` で OK、ROLLBACK |
| 2026-07-05 | **本番適用**（`MergeEmojiDups.java --apply`） | **COMMITTED**。revert SQL=`c:\tmp\dbtool\revert_emoji_dups.sql`。事後検証: 🔰版(43/44/52/59)=論理削除+参照0、クリーン版(65/66/68/70)=matches/pairings/org を統合済・org所属を保持(orgs=1) |

## データ形状と統合方式（重要）
- 🔰版(FROM)は matches/match_pairings/player_organizations を、クリーン版(TO)は practice_participants を持ち、**双方にデータが分散**。
- クリーン版(TO)が `player_organizations` を持たない（from_orgs=1 / to_orgs=0 / org_overlap=0）ため、
  `#932`(M.java) や本ディレクトリ同梱の `MergeDuplicates.java` のように org/prefs を **DELETE すると所属が消える**。
  本統合では org/prefs も含め**全FKを REPOINT**（`MergeEmojiDups.java`）して所属を保持した。
- ⚠️ 同梱 `MergeDuplicates.java` は「TO が org/prefs 行を持つ」前提の DELETE ロジックのため、本ケースのデータには不適。
  今後の統合では TO 側の org/prefs 有無を確認し、無い場合は REPOINT すること（`MergeEmojiDups.java` を参照）。

## 状態
- **本番適用・検証まで完了（2026-07-05）**。全既知の🔰/空白/不可視由来の重複は解消。
