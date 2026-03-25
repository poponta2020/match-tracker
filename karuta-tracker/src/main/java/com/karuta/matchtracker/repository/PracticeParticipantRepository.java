package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 練習参加者リポジトリ
 */
@Repository
public interface PracticeParticipantRepository extends JpaRepository<PracticeParticipant, Long> {

    /**
     * 特定の練習セッションの全参加者を取得
     */
    List<PracticeParticipant> findBySessionId(Long sessionId);

    /**
     * 複数の練習セッションの全参加者を一括取得（N+1対策）
     */
    @Query("SELECT p FROM PracticeParticipant p WHERE p.sessionId IN :sessionIds ORDER BY p.sessionId, p.matchNumber")
    List<PracticeParticipant> findBySessionIdIn(@Param("sessionIds") List<Long> sessionIds);

    /**
     * 特定の練習セッションに特定の選手が参加しているか確認
     */
    boolean existsBySessionIdAndPlayerId(Long sessionId, Long playerId);

    /**
     * 特定の選手が参加した全セッションを取得
     */
    List<PracticeParticipant> findByPlayerId(Long playerId);

    /**
     * 特定の選手が参加したセッション数を取得
     */
    long countByPlayerId(Long playerId);

    /**
     * 特定のセッションの参加者数を取得
     */
    long countBySessionId(Long sessionId);

    /**
     * 特定のセッションの全参加者を削除
     */
    @Modifying
    void deleteBySessionId(Long sessionId);

    /**
     * 特定のセッションから特定の参加者を削除
     */
    @Modifying
    void deleteBySessionIdAndPlayerId(Long sessionId, Long playerId);

    /**
     * 特定の練習セッションの参加選手IDリストを取得
     */
    @Query("SELECT p.playerId FROM PracticeParticipant p WHERE p.sessionId = :sessionId ORDER BY p.playerId")
    List<Long> findPlayerIdsBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 特定の選手の特定セッション・試合への参加確認
     */
    boolean existsBySessionIdAndPlayerIdAndMatchNumber(Long sessionId, Long playerId, Integer matchNumber);

    /**
     * 特定の選手の特定セッション・試合の参加記録を取得
     */
    List<PracticeParticipant> findBySessionIdAndPlayerIdAndMatchNumber(Long sessionId, Long playerId, Integer matchNumber);

    /**
     * 特定の選手の特定月の参加記録を取得
     */
    @Query("SELECT p FROM PracticeParticipant p WHERE p.playerId = :playerId " +
           "AND p.sessionId IN :sessionIds ORDER BY p.sessionId, p.matchNumber")
    List<PracticeParticipant> findByPlayerIdAndSessionIds(@Param("playerId") Long playerId,
                                                           @Param("sessionIds") List<Long> sessionIds);

    /**
     * 特定セッションの特定試合番号の参加者を取得
     */
    @Query("SELECT p FROM PracticeParticipant p WHERE p.sessionId = :sessionId AND p.matchNumber = :matchNumber")
    List<PracticeParticipant> findBySessionIdAndMatchNumber(@Param("sessionId") Long sessionId,
                                                             @Param("matchNumber") Integer matchNumber);

    /**
     * 特定セッションの特定試合の全参加者を削除
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PracticeParticipant p WHERE p.sessionId = :sessionId AND p.matchNumber = :matchNumber")
    void deleteBySessionIdAndMatchNumber(@Param("sessionId") Long sessionId, @Param("matchNumber") Integer matchNumber);

    /**
     * 特定の選手・セッション・試合の参加記録を削除
     */
    @Modifying
    void deleteBySessionIdAndPlayerIdAndMatchNumber(Long sessionId, Long playerId, Integer matchNumber);

    /**
     * 特定の選手が特定月に参加したセッション数を取得（ホーム画面用・軽量）
     */
    @Query("SELECT COUNT(DISTINCT pp.sessionId) FROM PracticeParticipant pp " +
           "JOIN PracticeSession ps ON pp.sessionId = ps.id " +
           "WHERE pp.playerId = :playerId AND YEAR(ps.sessionDate) = :year AND MONTH(ps.sessionDate) = :month")
    int countDistinctSessionsByPlayerAndMonth(@Param("playerId") Long playerId,
                                              @Param("year") int year,
                                              @Param("month") int month);

    /**
     * 特定の選手の特定セッションリストの参加記録を削除
     */
    @Modifying
    @Query("DELETE FROM PracticeParticipant p WHERE p.playerId = :playerId AND p.sessionId IN :sessionIds")
    void deleteByPlayerIdAndSessionIds(@Param("playerId") Long playerId,
                                       @Param("sessionIds") List<Long> sessionIds);

    /**
     * 指定日以降で特定の選手が参加登録しているセッションの試合番号を、日付昇順で取得
     */
    @Query("SELECT pp FROM PracticeParticipant pp " +
           "JOIN PracticeSession ps ON pp.sessionId = ps.id " +
           "WHERE pp.playerId = :playerId AND ps.sessionDate >= :fromDate " +
           "ORDER BY ps.sessionDate ASC, pp.matchNumber ASC")
    List<PracticeParticipant> findUpcomingParticipations(@Param("playerId") Long playerId,
                                                         @Param("fromDate") LocalDate fromDate);

    // ============================================================
    // 抽選システム用クエリ
    // ============================================================

    /**
     * 特定セッション・試合・ステータスの参加者を取得
     */
    List<PracticeParticipant> findBySessionIdAndMatchNumberAndStatus(
            Long sessionId, Integer matchNumber, ParticipantStatus status);

    /**
     * 特定セッション・試合・ステータスの参加者数を取得
     */
    long countBySessionIdAndMatchNumberAndStatus(
            Long sessionId, Integer matchNumber, ParticipantStatus status);

    /**
     * 特定セッション・試合のキャンセル待ちで最も若い番号の参加者を取得
     */
    Optional<PracticeParticipant> findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
            Long sessionId, Integer matchNumber, ParticipantStatus status);

    /**
     * 特定セッション・ステータスの参加者を取得
     */
    List<PracticeParticipant> findBySessionIdAndStatus(Long sessionId, ParticipantStatus status);

    /**
     * 特定選手に指定ステータスの参加記録が存在するか確認
     */
    boolean existsByPlayerIdAndStatus(Long playerId, ParticipantStatus status);

    /**
     * 特定セッションの特定ステータスの参加者が存在するか確認
     */
    boolean existsBySessionIdAndMatchNumberAndStatus(
            Long sessionId, Integer matchNumber, ParticipantStatus status);

    /**
     * 応答期限が切れたOFFERED状態の参加者を取得
     */
    @Query("SELECT p FROM PracticeParticipant p WHERE p.status = 'OFFERED' AND p.offerDeadline < :now")
    List<PracticeParticipant> findExpiredOffers(@Param("now") LocalDateTime now);

    /**
     * 指定セッションの全参加者をステータスで取得（試合番号・キャンセル待ち番号順）
     */
    @Query("SELECT p FROM PracticeParticipant p WHERE p.sessionId = :sessionId " +
           "ORDER BY p.matchNumber ASC, p.status ASC, p.waitlistNumber ASC")
    List<PracticeParticipant> findBySessionIdOrderByMatchAndStatus(@Param("sessionId") Long sessionId);

    /**
     * 指定年月のセッション参加者を全取得（LINE通知一括送信用）
     */
    @Query("SELECT pp FROM PracticeParticipant pp " +
           "JOIN PracticeSession ps ON pp.sessionId = ps.id " +
           "WHERE YEAR(ps.sessionDate) = :year AND MONTH(ps.sessionDate) = :month")
    List<PracticeParticipant> findBySessionDateYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * 指定月のセッションで落選した選手IDリストを取得（月内優先当選判定用）
     */
    @Query("SELECT DISTINCT pp.playerId FROM PracticeParticipant pp " +
           "JOIN PracticeSession ps ON pp.sessionId = ps.id " +
           "WHERE YEAR(ps.sessionDate) = :year AND MONTH(ps.sessionDate) = :month " +
           "AND pp.status IN ('WAITLISTED', 'DECLINED') " +
           "AND ps.id < :currentSessionId")
    List<Long> findMonthlyLoserPlayerIds(@Param("year") int year,
                                         @Param("month") int month,
                                         @Param("currentSessionId") Long currentSessionId);
}
