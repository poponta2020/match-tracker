import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/**
 * A-4-b 本番統合: 🔰プレフィックス版(FROM) を クリーン版(TO/master) へ統合する（4ペア）。
 * 川瀬 43->65 / 高橋 44->66 / 山野 52->68 / むらやま 59->70。
 *
 * #932(M.java) と同方式だが、クリーン版(TO)が player_organizations を持たない本データに合わせ、
 * player_organizations / line_prefs / push_prefs は DELETE ではなく REPOINT する（所属喪失を防ぐ）。
 * 事前の org_overlap / pp_overlap / self-match は全ペア 0 を確認済み（repoint 安全）。
 *
 * 安全機構: 単一TX・全FK repoint・各文の affected==事前count 検証・自動backup(revert SQL)・
 *   DRY-RUN既定(必ずROLLBACK)・--apply でCOMMIT・件数不一致で全体ROLLBACK。
 * 接続情報は環境変数(DB_URL/DB_USERNAME/DB_PASSWORD)から読む。
 */
public class MergeEmojiDups {
    static final long[][] PAIRS = { {43,65}, {44,66}, {52,68}, {59,70} }; // {from(🔰), to(clean)}

    // repoint 対象 {table, column}
    static final String[][] REPOINTS = {
        {"matches", "player1_id"}, {"matches", "player2_id"}, {"matches", "winner_id"},
        {"match_pairings", "player1_id"}, {"match_pairings", "player2_id"},
        {"practice_participants", "player_id"},
        {"player_organizations", "player_id"},
        {"line_notification_preferences", "player_id"},
        {"push_notification_preferences", "player_id"},
    };

    static String env(String n){ String v=System.getenv(n); if(v==null||v.isBlank()) throw new IllegalStateException("env "+n+" not set"); return v; }

    public static void main(String[] args) throws Exception {
        boolean apply = args.length > 0 && "--apply".equals(args[0]);
        String backupFile = "revert_emoji_dups.sql";
        try (Connection con = DriverManager.getConnection(env("DB_URL"), env("DB_USERNAME"), env("DB_PASSWORD"))) {
            con.setAutoCommit(false);
            StringBuilder bk = new StringBuilder("-- REVERT script for A-4-b emoji-dup merge\nBEGIN;\n");
            boolean allOk = true;

            for (long[] p : PAIRS) {
                long from = p[0], to = p[1];
                System.out.printf("==== merge #%d(🔰) -> #%d(clean) ====%n", from, to);

                // 1) 衝突チェック（0でなければ中断）
                long conflicts = conflicts(con, from, to);
                if (conflicts > 0) { System.out.printf("CONFLICT total=%d -> ABORT%n", conflicts); allOk=false; break; }

                // 2) backup（revert）を先に生成
                for (String[] rp : REPOINTS) backupRepoint(con, bk, rp[0], rp[1], from);
                bk.append("UPDATE players SET deleted_at=NULL WHERE id=").append(from).append(";\n");

                // 3) repoint（自己検証: 事前count == affected）
                try (Statement st = con.createStatement()) {
                    for (String[] rp : REPOINTS) {
                        int pre = count(con, "SELECT count(*) FROM "+rp[0]+" WHERE "+rp[1]+"="+from);
                        int n = st.executeUpdate("UPDATE "+rp[0]+" SET "+rp[1]+"="+to+" WHERE "+rp[1]+"="+from);
                        boolean ok = (n==pre); allOk &= ok;
                        System.out.printf("%-8s %-32s affected=%-3d (pre %-3d)%n", ok?"OK":"MISMATCH", rp[0]+"."+rp[1], n, pre);
                        if(!ok) break;
                    }
                    if (allOk) {
                        int n = st.executeUpdate("UPDATE players SET deleted_at=now() WHERE id="+from+" AND deleted_at IS NULL");
                        boolean ok=(n==1); allOk&=ok;
                        System.out.printf("%-8s %-32s affected=%-3d (pre 1)%n", ok?"OK":"MISMATCH", "players.deleted_at", n);
                    }
                }
                if(!allOk) break;
            }
            bk.append("COMMIT;\n");
            Files.writeString(Paths.get(backupFile), bk.toString(), StandardCharsets.UTF_8);
            System.out.println("Backup(revert SQL): "+backupFile);
            System.out.println("------");

            if(!allOk){ con.rollback(); System.out.println("ROLLED BACK: conflict or count mismatch. NO changes."); }
            else if(apply){ con.commit(); System.out.println("COMMITTED: 4 merges applied."); }
            else { con.rollback(); System.out.println("DRY-RUN OK: all matched, no conflicts. ROLLED BACK. Re-run with --apply to COMMIT."); }
        }
    }

    static long conflicts(Connection con, long from, long to) throws SQLException {
        String[] q = {
            // pp overlap (session,match)
            "SELECT count(*) FROM practice_participants a JOIN practice_participants b ON a.session_id=b.session_id AND a.match_number IS NOT DISTINCT FROM b.match_number WHERE a.player_id="+from+" AND b.player_id="+to,
            // org overlap (repoint unique)
            "SELECT count(*) FROM player_organizations a JOIN player_organizations b ON a.organization_id=b.organization_id WHERE a.player_id="+from+" AND b.player_id="+to,
            // matches self-match
            "SELECT count(*) FROM matches WHERE (player1_id="+from+" OR player2_id="+from+") AND (CASE WHEN player1_id="+from+" THEN "+to+" ELSE player1_id END)=(CASE WHEN player2_id="+from+" THEN "+to+" ELSE player2_id END)",
            // matches uq dup after
            "SELECT COALESCE(SUM(cnt-1),0) FROM (SELECT match_date,match_number,CASE WHEN player1_id="+from+" THEN "+to+" ELSE player1_id END p1,CASE WHEN player2_id="+from+" THEN "+to+" ELSE player2_id END p2,count(*) cnt FROM matches GROUP BY 1,2,3,4 HAVING count(*)>1) d",
            // pairings self-pair
            "SELECT count(*) FROM match_pairings WHERE (player1_id="+from+" OR player2_id="+from+") AND (CASE WHEN player1_id="+from+" THEN "+to+" ELSE player1_id END)=(CASE WHEN player2_id="+from+" THEN "+to+" ELSE player2_id END)",
            // pairings uq dup after
            "SELECT COALESCE(SUM(cnt-1),0) FROM (SELECT session_date,match_number,LEAST(CASE WHEN player1_id="+from+" THEN "+to+" ELSE player1_id END,CASE WHEN player2_id="+from+" THEN "+to+" ELSE player2_id END) lo,GREATEST(CASE WHEN player1_id="+from+" THEN "+to+" ELSE player1_id END,CASE WHEN player2_id="+from+" THEN "+to+" ELSE player2_id END) hi,count(*) cnt FROM match_pairings GROUP BY 1,2,3,4 HAVING count(*)>1) d",
        };
        long t=0; for(String s:q) t+=count(con,s); return t;
    }
    static int count(Connection con,String sql) throws SQLException { try(Statement st=con.createStatement();ResultSet rs=st.executeQuery(sql)){return rs.next()?rs.getInt(1):0;} }
    static void backupRepoint(Connection con,StringBuilder bk,String table,String col,long from) throws SQLException {
        try(Statement st=con.createStatement();ResultSet rs=st.executeQuery("SELECT id FROM "+table+" WHERE "+col+"="+from+" ORDER BY id")){
            while(rs.next()) bk.append("UPDATE ").append(table).append(" SET ").append(col).append("=").append(from).append(" WHERE id=").append(rs.getLong(1)).append(";\n");
        }
    }
}
