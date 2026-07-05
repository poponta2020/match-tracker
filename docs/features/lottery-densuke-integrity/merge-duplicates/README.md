# A-4-b 重複4名（川瀬/高橋/山野/むらやま）統合手順

要件: `../requirements.md` A-4 ／ 実装手順書 タスク6（Issue #977）。
正規化名キー衝突の**根本原因**である未統合の重複選手を、過去の星野統合（#932, `c:\tmp\dbtool\M.java`）と
**同方式**で統合する。`players.name` の UNIQUE 制約によりリネームでの解消は不可のため、
**マスター選択 → 参照付け替え → 重複側を `deleted_at` 論理削除**で行う。

> スキーマ変更ではない（データ移行）ため `database/` には置かず、監査可能な形で本ディレクトリに
> スクリプト・手順・適用ログを残す（要件 3.2 の方針）。

## 成果物
| ファイル | 役割 |
|---|---|
| `discover.sql` | 事前調査（重複ペア特定・FK参照列列挙・参照件数・マージ後衝突チェック）。すべて読み取り専用 |
| `MergeDuplicates.java` | 本番統合ランナー。単一TX・衝突事前チェック・件数自己検証・自動backup・DRY-RUN既定・`--apply`でCOMMIT |
| `pairs.txt` | 統合ペア定義（FROM=重複側, TO=マスター側）。調査結果を記入 |
| `apply-log.md` | 適用ログ（DRY-RUN結果・`--apply`結果・検証結果を記録）※適用時に追記 |

## 前提（本番接続）
Windows から Render PostgreSQL へは NAT64/IPv6 で SSL が切断されるため、
**JDBC ドライバ + IPv4 強制**（`-Djava.net.preferIPv4Stack=true`）で接続する（`c:\tmp\dbtool` と同じ）。

```bash
JAR=$(cygpath -w "$(ls ~/.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql/*/*/postgresql-*.jar | head -1)")
# 接続情報は環境変数で渡す（リポジトリに秘密情報を置かない）。値は CLAUDE.local.md（gitignore対象）から。
export DB_URL='jdbc:postgresql://<host>/<db>'
export DB_USERNAME='<user>'
export DB_PASSWORD='<pass>'
# 調査（1文=1ファイルで実行。discover.sql の各ブロックを切り出す。Q.java は c:\tmp\dbtool でリポジトリ外）
java -Djava.net.preferIPv4Stack=true -cp "$JAR;." Q <query>.sql
```

> **秘密情報はコミットしない**。`MergeDuplicates.java` は接続情報を環境変数（`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`）から読む。
> 値は `CLAUDE.local.md`（gitignore対象）/ Render ダッシュボード → Connect タブが一次情報源。
> 接続情報は Render のローテーションで変わる。**認証が EOF で失敗する場合は接続情報が古い**。

## 手順
1. **調査**: `discover.sql [1]` で 川瀬/高橋/山野/むらやま の active 重複ペアと id を確定。
   マスター(TO)は正規形の名前（空白等なし）を持つ側を原則とする。判断に迷うペアはユーザーに確認。
2. **FK確認**: `discover.sql [2]` で `players` を参照する FK 列が既知集合
   （matches p1/p2/winner, match_pairings p1/p2, practice_participants player_id ＋
   削除対象 player_organizations / line_notification_preferences / push_notification_preferences）
   に収まることを確認。想定外の列があれば `MergeDuplicates.REPOINTS/DELETES` に追記。
3. **衝突確認**: 各ペアで `discover.sql [3]`（`<FROM>/<TO>` 差し替え）を実行し、`CONFLICT.*` が**全て 0** を確認。
   0でない場合は自動統合せず、先に手動で解消（同一試合の重複参加/対戦など）。
4. **pairs.txt** に 4 ペアを記入。
5. **DRY-RUN**（`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` を export 済みで）:
   `java -Djava.net.preferIPv4Stack=true -cp "$JAR;." MergeDuplicates pairs.txt backup.sql`
   → 全件 `OK` かつ `DRY-RUN OK` を確認（この時点ではDBは変更されない）。
6. **本番適用（要ユーザー承認）**: `... MergeDuplicates pairs.txt backup.sql --apply`
   → `COMMITTED`。生成された `backup.sql` は revert 用に保管。
7. **検証**: `discover.sql [1]` を再実行し、対象 4 名の `active_namesakes` が 0 になったことを確認。
   アプリ側の名寄せ衝突通知（`DENSUKE_NAME_COLLISION`）が次回同期で鳴らないことも確認。
8. `apply-log.md` に結果を記録。

## 現状（未適用）
- **本コミット時点で本番未適用**。理由: 本番 Postgres への接続が認証段階で EOF となり疎通不可
  （Render 無料枠のスピンダウン or 接続情報ローテーションの可能性）。
- 適用には (a) 最新の本番接続情報、(b) 上記手順3の衝突ゼロ確認、(c) **ユーザーの明示的な承認**が必要。
- ツール・調査クエリ・手順は本ディレクトリに整備済みで、接続復旧後すぐ実行できる。
