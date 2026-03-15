package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchPairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MatchPairingRepository extends JpaRepository<MatchPairing, Long> {

    /**
     * 指定日の対戦組み合わせを取得
     */
    List<MatchPairing> findBySessionDateOrderByMatchNumber(LocalDate sessionDate);

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     */
    List<MatchPairing> findBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);

    /**
     * 指定日・試合番号の対戦組み合わせを削除
     */
    void deleteBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);

    /**
     * 指定日・試合番号の対戦組み合わせが存在するか確認
     */
    boolean existsBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);

    /**
     * 指定選手リストの過去の組み合わせ履歴を取得（対戦履歴表示用）
     * @param participantIds 選手IDリスト
     * @param startDate 開始日（含む）
     * @param endDate 終了日（含まない）
     * @return [sessionDate, playerA(小さい方), playerB(大きい方)]のリスト
     */
    @Query("SELECT mp.sessionDate, " +
           "CASE WHEN mp.player1Id < mp.player2Id THEN mp.player1Id ELSE mp.player2Id END, " +
           "CASE WHEN mp.player1Id < mp.player2Id THEN mp.player2Id ELSE mp.player1Id END " +
           "FROM MatchPairing mp " +
           "WHERE mp.sessionDate >= :startDate AND mp.sessionDate < :endDate " +
           "AND (mp.player1Id IN :participantIds OR mp.player2Id IN :participantIds) " +
           "ORDER BY mp.sessionDate DESC")
    List<Object[]> findRecentPairingHistory(@Param("participantIds") List<Long> participantIds,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /**
     * 指定日の同日の組み合わせ履歴を取得（当日の他の試合も含む）
     * @param sessionDate 対戦日
     * @param participantIds 選手IDリスト
     * @return [sessionDate, playerA(小さい方), playerB(大きい方)]のリスト
     */
    @Query("SELECT mp.sessionDate, " +
           "CASE WHEN mp.player1Id < mp.player2Id THEN mp.player1Id ELSE mp.player2Id END, " +
           "CASE WHEN mp.player1Id < mp.player2Id THEN mp.player2Id ELSE mp.player1Id END " +
           "FROM MatchPairing mp " +
           "WHERE mp.sessionDate = :sessionDate " +
           "AND (mp.player1Id IN :participantIds OR mp.player2Id IN :participantIds)")
    List<Object[]> findSameDayPairingHistory(@Param("sessionDate") LocalDate sessionDate,
                                              @Param("participantIds") List<Long> participantIds);
}
