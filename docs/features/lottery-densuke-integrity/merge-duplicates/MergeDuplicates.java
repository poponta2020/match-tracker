import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * A-4-b PRODUCTION merge runner: 重複4名（川瀬/高橋/山野/むらやま）を統合する。
 * Issue #977 / 親 lottery-densuke-integrity。#932（星野統合, dbtool/M.java）と同方式。
 *
 * 安全機構:
 *   - 全ペアを単一トランザクションで処理（autocommit OFF）
 *   - 各ペアでマージ後の一意制約衝突・自己対戦を事前チェックし、1件でもあれば全体を ROLLBACK
 *   - 各 UPDATE の affected 行数を「事前 SELECT count」と照合し、不一致なら全体を ROLLBACK
 *     （検証後にDBがドリフトした場合の誤適用を防ぐ）
 *   - 書き込み前に revert SQL（backup）を同一TXスナップショットから生成
 *   - 既定は DRY-RUN（必ず ROLLBACK）。COMMIT するには --apply を渡す
 *
 * 使い方:
 *   1) discover.sql [1][2] で 4ペアの (FROM=重複側, TO=マスター側) を確定し pairs.txt に記述
 *      （1行 "FROM,TO  # 任意コメント"）
 *   2) discover.sql [3] で各ペアの CONFLICT.* が全て 0 であることを確認
 *   3) DRY-RUN:  java -Djava.net.preferIPv4Stack=true -cp "<pgjdbc.jar>;." MergeDuplicates pairs.txt backup.sql
 *   4) 問題なければ --apply で本番適用し、discover.sql [1] を再実行して重複解消を確認
 *
 * ※ discover.sql [2] で既知以外の FK 参照列が出た場合は REPOINTS / DELETES に追記すること。
 */
public class MergeDuplicates {
    // 接続情報は環境変数から読む（リポジトリに秘密情報をコミットしない）。
    // 実行例:
    //   DB_URL='jdbc:postgresql://<host>/<db>' DB_USERNAME=<user> DB_PASSWORD=<pass> \
    //     java -Djava.net.preferIPv4Stack=true -cp "<pgjdbc.jar>;." MergeDuplicates pairs.txt backup.sql
    // 値は CLAUDE.local.md（gitignore対象）/ Render Connect タブが一次情報源。
    static final String URL = requireEnv("DB_URL");
    static final String USER = requireEnv("DB_USERNAME");
    static final String PASS = requireEnv("DB_PASSWORD");

    static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("環境変数 " + name + " が未設定です（DB_URL/DB_USERNAME/DB_PASSWORD を設定して実行してください）");
        }
        return v;
    }

    // 再ポイント対象 {table, column}（players を参照する FK 列）
    static final String[][] REPOINTS = {
        {"matches", "player1_id"},
        {"matches", "player2_id"},
        {"matches", "winner_id"},
        {"match_pairings", "player1_id"},
        {"match_pairings", "player2_id"},
        {"practice_participants", "player_id"},
    };
    // 重複行削除対象（TO 側が同等行を保持。player_id で1行想定）
    static final String[] DELETES = {
        "player_organizations",
        "line_notification_preferences",
        "push_notification_preferences",
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.err.println("usage: MergeDuplicates <pairsFile> <backupFile> [--apply]"); System.exit(2); }
        List<long[]> pairs = readPairs(args[0]);
        String backupFile = args[1];
        boolean apply = args.length > 2 && "--apply".equals(args[2]);
        if (pairs.isEmpty()) { System.err.println("no pairs in " + args[0]); System.exit(2); }

        try (Connection con = DriverManager.getConnection(URL, USER, PASS)) {
            con.setAutoCommit(false);
            StringBuilder bk = new StringBuilder("-- REVERT script for A-4-b duplicate merge\nBEGIN;\n");
            boolean allOk = true;

            for (long[] p : pairs) {
                long from = p[0], to = p[1];
                System.out.printf("==== merge #%d -> #%d ====%n", from, to);

                // 1) 衝突チェック（1件でも > 0 なら中断）
                long conflicts = countConflicts(con, from, to);
                if (conflicts > 0) {
                    System.out.printf("CONFLICT: %d rows would violate uniqueness/self-match. ABORT.%n", conflicts);
                    allOk = false; break;
                }

                // 2) backup（revert SQL）を先に生成
                for (String[] rp : REPOINTS) backupRepoint(con, bk, rp[0], rp[1], from);
                for (String tbl : DELETES) backupInserts(con, bk, tbl, from);
                bk.append("UPDATE players SET deleted_at=NULL WHERE id=").append(from).append(";\n");

                // 3) 再ポイント（自己検証: 事前 count == affected）
                try (Statement st = con.createStatement()) {
                    for (String[] rp : REPOINTS) {
                        int pre = count(con, "SELECT count(*) FROM " + rp[0] + " WHERE " + rp[1] + "=" + from);
                        int n = st.executeUpdate("UPDATE " + rp[0] + " SET " + rp[1] + "=" + to + " WHERE " + rp[1] + "=" + from);
                        boolean ok = (n == pre); allOk &= ok;
                        System.out.printf("%-8s %-28s affected=%-3d (pre %-3d)%n", ok ? "OK" : "MISMATCH", rp[0]+"."+rp[1], n, pre);
                        if (!ok) break;
                    }
                    if (allOk) for (String tbl : DELETES) {
                        int pre = count(con, "SELECT count(*) FROM " + tbl + " WHERE player_id=" + from);
                        int n = st.executeUpdate("DELETE FROM " + tbl + " WHERE player_id=" + from);
                        boolean ok = (n == pre); allOk &= ok;
                        System.out.printf("%-8s %-28s affected=%-3d (pre %-3d)%n", ok ? "OK" : "MISMATCH", "del "+tbl, n, pre);
                        if (!ok) break;
                    }
                    if (allOk) {
                        int n = st.executeUpdate("UPDATE players SET deleted_at=now() WHERE id=" + from + " AND deleted_at IS NULL");
                        boolean ok = (n == 1); allOk &= ok;
                        System.out.printf("%-8s %-28s affected=%-3d (pre 1)%n", ok ? "OK" : "MISMATCH", "players.deleted_at", n);
                    }
                }
                if (!allOk) break;
            }

            bk.append("COMMIT;\n");
            Files.writeString(Paths.get(backupFile), bk.toString(), StandardCharsets.UTF_8);
            System.out.println("Backup (revert SQL) written: " + backupFile);
            System.out.println("------");

            if (!allOk) {
                con.rollback();
                System.out.println("ROLLED BACK: conflict or count mismatch. NO changes applied. Re-verify (discover.sql) before retrying.");
            } else if (apply) {
                con.commit();
                System.out.println("COMMITTED: all merges applied. Verify with discover.sql [1] (active_namesakes should drop to 0).");
            } else {
                con.rollback();
                System.out.println("DRY-RUN OK: all statements matched expected counts, no conflicts. ROLLED BACK. Re-run with --apply to COMMIT.");
            }
        }
    }

    static long countConflicts(Connection con, long from, long to) throws SQLException {
        String[] q = {
            "SELECT count(*) FROM practice_participants a JOIN practice_participants b ON a.session_id=b.session_id AND a.match_number IS NOT DISTINCT FROM b.match_number WHERE a.player_id=" + from + " AND b.player_id=" + to,
            "SELECT count(*) FROM matches WHERE (player1_id=" + from + " OR player2_id=" + from + ") AND (CASE WHEN player1_id=" + from + " THEN " + to + " ELSE player1_id END)=(CASE WHEN player2_id=" + from + " THEN " + to + " ELSE player2_id END)",
            "SELECT COALESCE(SUM(cnt-1),0) FROM (SELECT match_date,match_number,CASE WHEN player1_id=" + from + " THEN " + to + " ELSE player1_id END AS p1,CASE WHEN player2_id=" + from + " THEN " + to + " ELSE player2_id END AS p2,count(*) cnt FROM matches GROUP BY 1,2,3,4 HAVING count(*)>1) d",
            "SELECT count(*) FROM match_pairings WHERE (player1_id=" + from + " OR player2_id=" + from + ") AND (CASE WHEN player1_id=" + from + " THEN " + to + " ELSE player1_id END)=(CASE WHEN player2_id=" + from + " THEN " + to + " ELSE player2_id END)",
            "SELECT COALESCE(SUM(cnt-1),0) FROM (SELECT session_date,match_number,LEAST(CASE WHEN player1_id=" + from + " THEN " + to + " ELSE player1_id END,CASE WHEN player2_id=" + from + " THEN " + to + " ELSE player2_id END) lo,GREATEST(CASE WHEN player1_id=" + from + " THEN " + to + " ELSE player1_id END,CASE WHEN player2_id=" + from + " THEN " + to + " ELSE player2_id END) hi,count(*) cnt FROM match_pairings GROUP BY 1,2,3,4 HAVING count(*)>1) d",
        };
        long total = 0;
        for (String s : q) total += count(con, s);
        return total;
    }

    static int count(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static void backupRepoint(Connection con, StringBuilder bk, String table, String col, long from) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM " + table + " WHERE " + col + "=" + from + " ORDER BY id")) {
            while (rs.next())
                bk.append("UPDATE ").append(table).append(" SET ").append(col).append("=").append(from)
                  .append(" WHERE id=").append(rs.getLong(1)).append(";\n");
        }
    }

    static void backupInserts(Connection con, StringBuilder bk, String table, long from) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE player_id=" + from)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                StringBuilder cols = new StringBuilder(), vals = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) { cols.append(", "); vals.append(", "); }
                    cols.append(md.getColumnName(i));
                    vals.append(lit(rs.getObject(i)));
                }
                bk.append("INSERT INTO ").append(table).append(" (").append(cols).append(") VALUES (").append(vals).append(");\n");
            }
        }
    }

    static List<long[]> readPairs(String file) throws IOException {
        List<long[]> pairs = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            String s = line.trim();
            int hash = s.indexOf('#'); if (hash >= 0) s = s.substring(0, hash).trim();
            if (s.isEmpty()) continue;
            String[] parts = s.split(",");
            pairs.add(new long[]{ Long.parseLong(parts[0].trim()), Long.parseLong(parts[1].trim()) });
        }
        return pairs;
    }

    static String lit(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
