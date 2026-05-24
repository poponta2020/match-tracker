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
     * <p>古い順に処理することで、近接ディスパッチされた複数団体の event 同士で
     * run_id の取り合いが起きた場合も、ディスパッチ順 (= run_id 昇順) とイベント順
     * (= triggered_at 昇順) を一致させて正しく割当できる。
     */
    List<KaderuSyncTriggerEvent> findAllByStatusOrderByTriggeredAtAsc(SyncStatus status);

    /**
     * 指定の GitHub run id を既に保持しているイベントがあるかを返す。
     * 同時刻に複数団体の手動同期がディスパッチされた場合に、同じ run_id を
     * 別イベントへ二重割当することを防ぐために使う。
     */
    boolean existsByGithubRunId(Long githubRunId);
}
