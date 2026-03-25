package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineLinkingCode;
import com.karuta.matchtracker.entity.LineLinkingCode.CodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * LINEワンタイムコードリポジトリ
 */
@Repository
public interface LineLinkingCodeRepository extends JpaRepository<LineLinkingCode, Long> {

    /** コード文字列でACTIVEなコードを検索 */
    Optional<LineLinkingCode> findByCodeAndStatus(String code, CodeStatus status);

    /** プレイヤーのACTIVEなコードを検索 */
    Optional<LineLinkingCode> findByPlayerIdAndStatus(Long playerId, CodeStatus status);

    /** チャネルIDでACTIVEなコードを検索 */
    Optional<LineLinkingCode> findByLineChannelIdAndStatus(Long lineChannelId, CodeStatus status);

    /** プレイヤーのACTIVEなコードを全て無効化 */
    @Modifying
    @Query("UPDATE LineLinkingCode c SET c.status = 'INVALIDATED' WHERE c.playerId = :playerId AND c.status = 'ACTIVE'")
    void invalidateAllActiveByPlayerId(@Param("playerId") Long playerId);
}
