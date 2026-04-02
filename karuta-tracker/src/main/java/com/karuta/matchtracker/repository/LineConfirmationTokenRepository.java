package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineConfirmationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * LINE操作確認トークンリポジトリ
 */
@Repository
public interface LineConfirmationTokenRepository extends JpaRepository<LineConfirmationToken, Long> {

    /** トークン文字列で検索 */
    Optional<LineConfirmationToken> findByToken(String token);

    /** 指定日時より前に期限切れのトークンを全削除 */
    @Modifying
    @Query("DELETE FROM LineConfirmationToken t WHERE t.expiresAt < :now")
    void deleteByExpiresAtBefore(@Param("now") LocalDateTime now);
}
