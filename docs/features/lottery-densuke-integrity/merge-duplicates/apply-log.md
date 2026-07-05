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
