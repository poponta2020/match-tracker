package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Web Pushサブスクリプションリポジトリ
 */
@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /**
     * 指定プレイヤーの全サブスクリプションを取得
     */
    List<PushSubscription> findByPlayerId(Long playerId);

    /**
     * 指定エンドポイントのサブスクリプションが存在するか確認
     */
    boolean existsByEndpoint(String endpoint);

    /**
     * 指定プレイヤー・エンドポイントのサブスクリプションを削除
     */
    void deleteByPlayerIdAndEndpoint(Long playerId, String endpoint);
}
