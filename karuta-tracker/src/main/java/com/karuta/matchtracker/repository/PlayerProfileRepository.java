package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PlayerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 選手情報履歴のRepositoryインターフェース
 *
 * 選手の級・段位の変更履歴を管理するクエリを提供します。
 */
@Repository
public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {

    /**
     * 選手の現在有効なプロフィールを取得
     *
     * @param playerId 選手ID
     * @return 現在有効なプロフィール（任意）
     */
    @Query("SELECT pp FROM PlayerProfile pp WHERE pp.playerId = :playerId AND pp.validTo IS NULL")
    Optional<PlayerProfile> findCurrentByPlayerId(@Param("playerId") Long playerId);

    /**
     * 選手の全プロフィール履歴を取得（有効開始日の降順）
     *
     * @param playerId 選手ID
     * @return プロフィール履歴のリスト
     */
    @Query("SELECT pp FROM PlayerProfile pp WHERE pp.playerId = :playerId ORDER BY pp.validFrom DESC")
    List<PlayerProfile> findAllByPlayerIdOrderByValidFromDesc(@Param("playerId") Long playerId);

    /**
     * 特定の日付時点で有効だったプロフィールを取得
     * 過去の対戦時の級・段位を取得するために使用
     *
     * @param playerId 選手ID
     * @param date 対象日付
     * @return その日時点で有効だったプロフィール（任意）
     */
    @Query("SELECT pp FROM PlayerProfile pp WHERE pp.playerId = :playerId " +
           "AND pp.validFrom <= :date " +
           "AND (pp.validTo IS NULL OR pp.validTo > :date)")
    Optional<PlayerProfile> findByPlayerIdAndDate(@Param("playerId") Long playerId,
                                                    @Param("date") LocalDate date);

    /**
     * 選手IDでプロフィールを全て削除
     * CASCADE削除の代替として使用
     *
     * @param playerId 選手ID
     */
    void deleteByPlayerId(Long playerId);
}
