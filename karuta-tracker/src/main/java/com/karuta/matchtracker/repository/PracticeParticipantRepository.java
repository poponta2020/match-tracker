package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PracticeParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    void deleteBySessionId(Long sessionId);

    /**
     * 特定のセッションから特定の参加者を削除
     */
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
     * 特定の選手・セッション・試合の参加記録を削除
     */
    void deleteBySessionIdAndPlayerIdAndMatchNumber(Long sessionId, Long playerId, Integer matchNumber);

    /**
     * 特定の選手の特定セッションリストの参加記録を削除
     */
    @Modifying
    @Query("DELETE FROM PracticeParticipant p WHERE p.playerId = :playerId AND p.sessionId IN :sessionIds")
    void deleteByPlayerIdAndSessionIds(@Param("playerId") Long playerId,
                                       @Param("sessionIds") List<Long> sessionIds);
}
