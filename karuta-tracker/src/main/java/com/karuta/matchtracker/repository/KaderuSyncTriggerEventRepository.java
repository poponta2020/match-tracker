package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent;
import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KaderuSyncTriggerEventRepository
        extends JpaRepository<KaderuSyncTriggerEvent, Long> {

    /**
     * 指定団体・指定ステータスのうち最新の1件を返す（重複起動チェック / 進行中表示用）。
     */
    Optional<KaderuSyncTriggerEvent> findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(
            Long organizationId, SyncStatus status);

    /**
     * 指定ステータスの全イベントを triggered_at 昇順で返す（スケジューラー巡回用）。
     *
     * <p>古い順に処理することで、scheduler の各 tick で event を作成順に巡回する。
     * 相関 ID 方式 (run-name に [event:&lt;id&gt;] を埋める) で run と event を一意に
     * 紐付けるため、処理順自体が誤割当の防御線にはならないが、ログの追跡性のため
     * 順序を固定している。
     */
    List<KaderuSyncTriggerEvent> findAllByStatusOrderByTriggeredAtAsc(SyncStatus status);
}
