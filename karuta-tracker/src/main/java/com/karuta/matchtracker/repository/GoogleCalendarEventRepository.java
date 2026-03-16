package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.GoogleCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleCalendarEventRepository extends JpaRepository<GoogleCalendarEvent, Long> {

    /**
     * プレイヤーの全イベントマッピングを取得
     */
    List<GoogleCalendarEvent> findByPlayerId(Long playerId);

    /**
     * プレイヤー×セッションのマッピングを取得
     */
    Optional<GoogleCalendarEvent> findByPlayerIdAndSessionId(Long playerId, Long sessionId);

    /**
     * プレイヤーの複数セッションのマッピングを一括取得
     */
    @Query("SELECT g FROM GoogleCalendarEvent g WHERE g.playerId = :playerId AND g.sessionId IN :sessionIds")
    List<GoogleCalendarEvent> findByPlayerIdAndSessionIdIn(
        @Param("playerId") Long playerId,
        @Param("sessionIds") List<Long> sessionIds);
}
