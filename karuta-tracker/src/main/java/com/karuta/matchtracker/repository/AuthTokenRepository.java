package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 認証トークンのリポジトリ
 */
@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    /**
     * トークンハッシュで検索する（リクエストごとの検証で使う主経路）
     */
    Optional<AuthToken> findByTokenHash(String tokenHash);

    /**
     * 指定選手の有効なトークンをすべて失効させる
     * パスワード変更・選手の論理削除で使う
     *
     * @return 失効させた件数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthToken t SET t.revokedAt = :revokedAt "
            + "WHERE t.playerId = :playerId AND t.revokedAt IS NULL")
    int revokeAllByPlayerId(@Param("playerId") Long playerId, @Param("revokedAt") LocalDateTime revokedAt);
}
