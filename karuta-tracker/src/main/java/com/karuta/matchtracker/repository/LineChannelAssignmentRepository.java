package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LINEチャネル割り当てリポジトリ
 */
@Repository
public interface LineChannelAssignmentRepository extends JpaRepository<LineChannelAssignment, Long> {

    /** プレイヤーのアクティブな割り当てを取得 */
    @Query("SELECT a FROM LineChannelAssignment a WHERE a.playerId = :playerId AND a.status IN ('PENDING', 'LINKED')")
    Optional<LineChannelAssignment> findActiveByPlayerId(@Param("playerId") Long playerId);

    /** チャネルのアクティブな割り当てを取得 */
    @Query("SELECT a FROM LineChannelAssignment a WHERE a.lineChannelId = :channelId AND a.status IN ('PENDING', 'LINKED')")
    Optional<LineChannelAssignment> findActiveByChannelId(@Param("channelId") Long channelId);

    /** LINKED状態の割り当てをプレイヤーIDで取得 */
    Optional<LineChannelAssignment> findByPlayerIdAndStatus(Long playerId, AssignmentStatus status);

    /** LINE連携済みの全プレイヤーIDリストを取得 */
    @Query("SELECT a.playerId FROM LineChannelAssignment a WHERE a.status = 'LINKED'")
    List<Long> findAllLinkedPlayerIds();

    /** 回収対象の割り当てを検索（N日以上未ログイン） */
    @Query("""
        SELECT a FROM LineChannelAssignment a
        JOIN Player p ON a.playerId = p.id
        WHERE a.status IN ('PENDING', 'LINKED')
        AND p.lastLoginAt < :threshold
        AND a.reclaimWarnedAt IS NULL
        """)
    List<LineChannelAssignment> findReclaimCandidates(@Param("threshold") LocalDateTime threshold);

    /** 回収猶予期間経過済みの割り当てを検索 */
    @Query("""
        SELECT a FROM LineChannelAssignment a
        JOIN Player p ON a.playerId = p.id
        WHERE a.status IN ('PENDING', 'LINKED')
        AND a.reclaimWarnedAt IS NOT NULL
        AND a.reclaimWarnedAt < :graceDeadline
        AND p.lastLoginAt < :threshold
        """)
    List<LineChannelAssignment> findReclaimExpired(
        @Param("threshold") LocalDateTime threshold,
        @Param("graceDeadline") LocalDateTime graceDeadline
    );
}
