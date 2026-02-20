package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 対戦結果のRepositoryインターフェース
 *
 * 対戦結果の検索、統計情報の取得クエリを提供します。
 */
@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * 日付別の対戦結果を取得（試合番号の昇順）
     *
     * @param matchDate 対戦日
     * @return 対戦結果のリスト
     */
    @Query("SELECT m FROM Match m WHERE m.matchDate = :matchDate ORDER BY m.matchNumber ASC")
    List<Match> findByMatchDateOrderByMatchNumber(@Param("matchDate") LocalDate matchDate);

    /**
     * 選手の対戦結果を取得（日付の降順）
     *
     * @param playerId 選手ID
     * @return 対戦結果のリスト
     */
    @Query("SELECT m FROM Match m WHERE m.player1Id = :playerId OR m.player2Id = :playerId ORDER BY m.matchDate DESC, m.matchNumber ASC")
    List<Match> findByPlayerId(@Param("playerId") Long playerId);

    /**
     * 選手の期間内の対戦結果を取得
     *
     * @param playerId 選手ID
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 対戦結果のリスト
     */
    @Query("SELECT m FROM Match m WHERE (m.player1Id = :playerId OR m.player2Id = :playerId) " +
           "AND m.matchDate BETWEEN :startDate AND :endDate ORDER BY m.matchDate ASC")
    List<Match> findByPlayerIdAndDateRange(@Param("playerId") Long playerId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    /**
     * 2人の選手間の対戦履歴を取得（日付の降順）
     *
     * @param player1Id 選手1のID
     * @param player2Id 選手2のID
     * @return 対戦結果のリスト
     */
    @Query("SELECT m FROM Match m WHERE " +
           "(m.player1Id = :player1Id AND m.player2Id = :player2Id) OR " +
           "(m.player1Id = :player2Id AND m.player2Id = :player1Id) " +
           "ORDER BY m.matchDate DESC")
    List<Match> findByTwoPlayers(@Param("player1Id") Long player1Id,
                                   @Param("player2Id") Long player2Id);

    /**
     * 選手の総対戦数を取得
     *
     * @param playerId 選手ID
     * @return 総対戦数
     */
    @Query("SELECT COUNT(m) FROM Match m WHERE m.player1Id = :playerId OR m.player2Id = :playerId")
    long countByPlayerId(@Param("playerId") Long playerId);

    /**
     * 選手の勝利数を取得
     *
     * @param playerId 選手ID
     * @return 勝利数
     */
    @Query("SELECT COUNT(m) FROM Match m WHERE m.winnerId = :playerId")
    long countWinsByPlayerId(@Param("playerId") Long playerId);

    /**
     * 日付と試合番号で対戦結果を検索
     *
     * @param matchDate 対戦日
     * @param matchNumber 試合番号
     * @return 対戦結果のリスト
     */
    @Query("SELECT m FROM Match m WHERE m.matchDate = :matchDate AND m.matchNumber = :matchNumber")
    List<Match> findByMatchDateAndMatchNumber(@Param("matchDate") LocalDate matchDate,
                                                @Param("matchNumber") Integer matchNumber);

    /**
     * 特定の日付に対戦結果が存在するか確認
     *
     * @param matchDate 対戦日
     * @return 存在する場合true
     */
    boolean existsByMatchDate(LocalDate matchDate);

    /**
     * 特定の日付の対戦数を取得
     *
     * @param matchDate 対戦日
     * @return 対戦数
     */
    long countByMatchDate(LocalDate matchDate);

    /**
     * 複数の日付の対戦数を一括取得（N+1対策）
     *
     * @param dates 日付リスト
     * @return [matchDate, count] の配列リスト
     */
    @Query("SELECT m.matchDate, COUNT(m) FROM Match m WHERE m.matchDate IN :dates GROUP BY m.matchDate")
    List<Object[]> countByMatchDateIn(@Param("dates") List<LocalDate> dates);

    /**
     * 選手が作成または更新した対戦結果を取得
     * 編集・削除権限の判定に使用
     *
     * @param playerId 選手ID
     * @return 対戦結果のリスト
     */
    @Query("SELECT m FROM Match m WHERE m.createdBy = :playerId OR m.updatedBy = :playerId")
    List<Match> findByCreatedByOrUpdatedBy(@Param("playerId") Long playerId);

    /**
     * 特定の選手の同日同試合番号の試合が存在するか確認
     *
     * @param playerId 選手ID
     * @param matchDate 対戦日
     * @param matchNumber 試合番号
     * @return 存在する場合true
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Match m WHERE (m.player1Id = :playerId OR m.player2Id = :playerId) AND m.matchDate = :matchDate AND m.matchNumber = :matchNumber")
    boolean existsByPlayerIdAndMatchDateAndMatchNumber(@Param("playerId") Long playerId,
                                                        @Param("matchDate") LocalDate matchDate,
                                                        @Param("matchNumber") Integer matchNumber);

    /**
     * 特定の選手の同日同試合番号の試合を取得
     *
     * @param playerId 選手ID
     * @param matchDate 対戦日
     * @param matchNumber 試合番号
     * @return 試合エンティティ（存在しない場合はnull）
     */
    @Query("SELECT m FROM Match m WHERE (m.player1Id = :playerId OR m.player2Id = :playerId) AND m.matchDate = :matchDate AND m.matchNumber = :matchNumber")
    Match findByPlayerIdAndMatchDateAndMatchNumber(@Param("playerId") Long playerId,
                                                    @Param("matchDate") LocalDate matchDate,
                                                    @Param("matchNumber") Integer matchNumber);

    /**
     * 過去の対戦履歴を取得（自動マッチング用）
     *
     * @param participantIds 参加者IDリスト
     * @param startDate 開始日
     * @param endDate 終了日（この日は含まない）
     * @return [matchDate, playerA, playerB]のリスト
     */
    @Query("SELECT m.matchDate, " +
           "CASE WHEN m.player1Id < m.player2Id THEN m.player1Id ELSE m.player2Id END, " +
           "CASE WHEN m.player1Id < m.player2Id THEN m.player2Id ELSE m.player1Id END " +
           "FROM Match m " +
           "WHERE m.matchDate >= :startDate AND m.matchDate < :endDate " +
           "AND (m.player1Id IN :participantIds OR m.player2Id IN :participantIds) " +
           "ORDER BY m.matchDate DESC")
    List<Object[]> findRecentMatchHistory(@Param("participantIds") List<Long> participantIds,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * 同日の既存対戦を取得（自動マッチング用）
     *
     * @param matchDate 対戦日
     * @param currentMatchNumber 現在の試合番号より小さい試合のみ取得
     * @return [playerA, playerB]のリスト
     */
    @Query("SELECT " +
           "CASE WHEN m.player1Id < m.player2Id THEN m.player1Id ELSE m.player2Id END, " +
           "CASE WHEN m.player1Id < m.player2Id THEN m.player2Id ELSE m.player1Id END " +
           "FROM Match m " +
           "WHERE m.matchDate = :matchDate AND m.matchNumber < :currentMatchNumber")
    List<Object[]> findTodayMatches(@Param("matchDate") LocalDate matchDate,
                                    @Param("currentMatchNumber") Integer currentMatchNumber);
}
