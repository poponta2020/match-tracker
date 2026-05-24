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
     * 指定ステータスの全イベント（スケジューラー巡回用）。
     */
    List<KaderuSyncTriggerEvent> findAllByStatus(SyncStatus status);

    /**
     * 指定の GitHub run id を既に保持しているイベントがあるかを返す。
     * 同時刻に複数団体の手動同期がディスパッチされた場合に、同じ run_id を
     * 別イベントへ二重割当することを防ぐために使う。
     */
    boolean existsByGithubRunId(Long githubRunId);
}
