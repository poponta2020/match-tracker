package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PlayerOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlayerOrganizationRepository extends JpaRepository<PlayerOrganization, Long> {

    List<PlayerOrganization> findByPlayerId(Long playerId);

    List<PlayerOrganization> findByPlayerIdIn(List<Long> playerIds);

    List<PlayerOrganization> findByOrganizationId(Long organizationId);

    boolean existsByPlayerIdAndOrganizationId(Long playerId, Long organizationId);

    /**
     * UNIQUE (player_id, organization_id) と競合しない場合のみ挿入する原子的 INSERT。
     * 並列リクエストとの競合で save() が一意制約違反を投げると、参加中トランザクションが
     * rollback-only にマークされ呼び出し元のコミットが UnexpectedRollbackException になるため、
     * 例外を発生させないこの経路で冪等な自動所属を実現する（Issue #1037）。
     * ネイティブ INSERT は {@code @PrePersist} を通らないため created_at を引数で受け取る。
     *
     * @return 挿入行数（1: 挿入成功 / 0: 既存または並列リクエストが先に登録済み）
     */
    @Modifying
    @Query(value = "INSERT INTO player_organizations (player_id, organization_id, created_at) "
            + "VALUES (:playerId, :organizationId, :createdAt) "
            + "ON CONFLICT (player_id, organization_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("playerId") Long playerId,
                       @Param("organizationId") Long organizationId,
                       @Param("createdAt") LocalDateTime createdAt);

    void deleteByPlayerId(Long playerId);

    void deleteByPlayerIdAndOrganizationId(Long playerId, Long organizationId);
}
