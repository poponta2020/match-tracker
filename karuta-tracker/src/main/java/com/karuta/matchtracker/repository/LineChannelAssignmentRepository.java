package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineAssignmentStatus;
import com.karuta.matchtracker.entity.LineChannelAssignment;
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

    /**
     * プレイヤーのアクティブな割り当てを取得
     */
    Optional<LineChannelAssignment> findByPlayerIdAndStatusIn(Long playerId, List<LineAssignmentStatus> statuses);

    /**
     * チャネルIDでアクティブな割り当てを取得
     */
    Optional<LineChannelAssignment> findByLineChannelIdAndStatusIn(Long lineChannelId, List<LineAssignmentStatus> statuses);

    /**
     * LINE userIdで割り当てを取得
     */
    Optional<LineChannelAssignment> findByLineUserIdAndStatus(String lineUserId, LineAssignmentStatus status);

    /**
     * チャネルDBのIDでLINKED状態の割り当てを取得
     */
    Optional<LineChannelAssignment> findByLineChannelIdAndStatus(Long lineChannelId, LineAssignmentStatus status);

    /**
     * 回収対象の割り当てを検索（一定期間ログインなし & 警告未送信）
     */
    @Query("SELECT a FROM LineChannelAssignment a " +
           "JOIN Player p ON a.playerId = p.id " +
           "WHERE a.status IN ('PENDING', 'LINKED') " +
           "AND a.reclaimWarnedAt IS NULL " +
           "AND (p.lastLoginAt IS NULL OR p.lastLoginAt < :threshold)")
    List<LineChannelAssignment> findReclaimCandidates(@Param("threshold") LocalDateTime threshold);

    /**
     * 回収警告済み & 猶予期間経過 & 依然未ログインの割り当てを検索
     */
    @Query("SELECT a FROM LineChannelAssignment a " +
           "JOIN Player p ON a.playerId = p.id " +
           "WHERE a.status IN ('PENDING', 'LINKED') " +
           "AND a.reclaimWarnedAt IS NOT NULL " +
           "AND a.reclaimWarnedAt < :graceDeadline " +
           "AND (p.lastLoginAt IS NULL OR p.lastLoginAt < a.reclaimWarnedAt)")
    List<LineChannelAssignment> findReclaimExpired(@Param("graceDeadline") LocalDateTime graceDeadline);

    /**
     * 指定プレイヤーIDリストのLINKED割り当てを一括取得
     */
    @Query("SELECT a FROM LineChannelAssignment a WHERE a.playerId IN :playerIds AND a.status = 'LINKED'")
    List<LineChannelAssignment> findLinkedByPlayerIds(@Param("playerIds") List<Long> playerIds);
}
