package com.karuta.matchtracker.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.karuta.matchtracker.dto.GoogleCalendarSyncResponse;
import com.karuta.matchtracker.entity.GoogleCalendarEvent;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.GoogleCalendarEventRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final GoogleCalendarEventRepository googleCalendarEventRepository;
    private final VenueRepository venueRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;

    @Value("${google.calendar.application-name:karuta-match-tracker}")
    private String applicationName;

    /**
     * Google Calendarと同期
     *
     * @param accessToken GISから取得したアクセストークン
     * @param playerId    対象プレイヤーID
     * @return 同期結果
     */
    @Transactional
    public GoogleCalendarSyncResponse sync(String accessToken, Long playerId) {
        GoogleCalendarSyncResponse response = new GoogleCalendarSyncResponse();

        try {
            Calendar calendarService = buildCalendarService(accessToken);

            // 1. 今日以降の、このプレイヤーが参加している練習セッションを取得
            LocalDate today = LocalDate.now();
            List<PracticeParticipant> participations =
                practiceParticipantRepository.findUpcomingParticipations(playerId, today);

            // セッションIDのユニークリスト
            Set<Long> participatingSessionIds = participations.stream()
                .map(PracticeParticipant::getSessionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

            // セッション情報を一括取得
            Map<Long, PracticeSession> sessionMap = practiceSessionRepository
                .findAllById(participatingSessionIds).stream()
                .collect(Collectors.toMap(PracticeSession::getId, s -> s));

            // 2. 既存のGoogleCalendarEventマッピングを取得
            List<GoogleCalendarEvent> existingMappings =
                googleCalendarEventRepository.findByPlayerId(playerId);
            Map<Long, GoogleCalendarEvent> existingBySessionId = existingMappings.stream()
                .collect(Collectors.toMap(GoogleCalendarEvent::getSessionId, e -> e));

            // 3. 会場情報を一括取得
            Set<Long> venueIds = sessionMap.values().stream()
                .map(PracticeSession::getVenueId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, Venue> venueMap = venueRepository.findAllById(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, v -> v));

            // 3.5. 会場ごとの試合時間割を一括取得
            Map<Long, Map<Integer, VenueMatchSchedule>> scheduleMap = new HashMap<>();
            if (!venueIds.isEmpty()) {
                List<VenueMatchSchedule> schedules =
                    venueMatchScheduleRepository.findByVenueIdIn(new ArrayList<>(venueIds));
                for (VenueMatchSchedule s : schedules) {
                    scheduleMap
                        .computeIfAbsent(s.getVenueId(), k -> new HashMap<>())
                        .put(s.getMatchNumber(), s);
                }
            }

            // 3.6. セッションIDごとのプレイヤー参加試合番号をまとめる
            Map<Long, List<Integer>> sessionMatchNumbers = new HashMap<>();
            for (PracticeParticipant p : participations) {
                if (p.getMatchNumber() != null) {
                    sessionMatchNumbers
                        .computeIfAbsent(p.getSessionId(), k -> new ArrayList<>())
                        .add(p.getMatchNumber());
                }
            }

            // 4. CREATE / UPDATE: 参加中セッションごとに処理
            for (Long sessionId : participatingSessionIds) {
                PracticeSession session = sessionMap.get(sessionId);
                if (session == null) continue;

                GoogleCalendarEvent existing = existingBySessionId.get(sessionId);
                String venueName = resolveVenueName(session, venueMap);
                LocalTime eventStartTime = null;
                LocalTime eventEndTime = null;

                // プレイヤーの参加試合番号から開始・終了時刻を決定
                if (session.getVenueId() != null) {
                    Map<Integer, VenueMatchSchedule> venueSchedule =
                        scheduleMap.get(session.getVenueId());
                    List<Integer> matchNums = sessionMatchNumbers.get(sessionId);

                    if (venueSchedule != null && matchNums != null && !matchNums.isEmpty()) {
                        int minMatch = Collections.min(matchNums);
                        int maxMatch = Collections.max(matchNums);
                        VenueMatchSchedule firstSchedule = venueSchedule.get(minMatch);
                        VenueMatchSchedule lastSchedule = venueSchedule.get(maxMatch);
                        if (firstSchedule != null) {
                            eventStartTime = firstSchedule.getStartTime();
                        }
                        if (lastSchedule != null) {
                            eventEndTime = lastSchedule.getEndTime();
                        }
                    }
                }

                try {
                    if (existing == null) {
                        // 新規作成
                        Event event = buildEvent(session, venueName, eventStartTime, eventEndTime);
                        Event created = calendarService.events()
                            .insert("primary", event).execute();

                        GoogleCalendarEvent mapping = GoogleCalendarEvent.builder()
                            .playerId(playerId)
                            .sessionId(sessionId)
                            .googleEventId(created.getId())
                            .syncedSessionUpdatedAt(session.getUpdatedAt())
                            .build();
                        googleCalendarEventRepository.save(mapping);

                        response.setCreatedCount(response.getCreatedCount() + 1);
                        response.getDetails().add(
                            String.format("%s 作成", session.getSessionDate()));
                        log.debug("Created calendar event for session {} ({})",
                            sessionId, session.getSessionDate());
                    } else {
                        // 常にupdateを試みる（Google側で削除されていた場合は再作成）
                        try {
                            Event event = buildEvent(session, venueName, eventStartTime, eventEndTime);
                            calendarService.events()
                                .update("primary", existing.getGoogleEventId(), event).execute();

                            existing.setSyncedSessionUpdatedAt(session.getUpdatedAt());
                            googleCalendarEventRepository.save(existing);

                            response.setUpdatedCount(response.getUpdatedCount() + 1);
                            response.getDetails().add(
                                String.format("%s 更新", session.getSessionDate()));
                            log.debug("Updated calendar event for session {} ({})",
                                sessionId, session.getSessionDate());
                        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
                            if (ex.getStatusCode() == 404) {
                                // Google側で削除済み → マッピング削除して再作成
                                log.info("Event {} not found on Google, recreating for session {}",
                                    existing.getGoogleEventId(), sessionId);
                                googleCalendarEventRepository.delete(existing);

                                Event event = buildEvent(session, venueName, eventStartTime, eventEndTime);
                                Event created = calendarService.events()
                                    .insert("primary", event).execute();

                                GoogleCalendarEvent mapping = GoogleCalendarEvent.builder()
                                    .playerId(playerId)
                                    .sessionId(sessionId)
                                    .googleEventId(created.getId())
                                    .syncedSessionUpdatedAt(session.getUpdatedAt())
                                    .build();
                                googleCalendarEventRepository.save(mapping);

                                response.setCreatedCount(response.getCreatedCount() + 1);
                                response.getDetails().add(
                                    String.format("%s 再作成", session.getSessionDate()));
                            } else {
                                throw ex;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Calendar sync error for session {}: {}", sessionId, e.getMessage());
                    response.setErrorCount(response.getErrorCount() + 1);
                    response.getErrors().add(
                        String.format("%s: %s", session.getSessionDate(), e.getMessage()));
                }
            }

            // 5. DELETE: DBにマッピングがあるが参加していないセッションのイベントを削除
            for (GoogleCalendarEvent mapping : existingMappings) {
                if (!participatingSessionIds.contains(mapping.getSessionId())) {
                    deleteCalendarEvent(calendarService, mapping, response);
                }
            }

            log.info("Calendar sync completed for player {}: created={}, updated={}, deleted={}, unchanged={}, errors={}",
                playerId, response.getCreatedCount(), response.getUpdatedCount(),
                response.getDeletedCount(), response.getUnchangedCount(), response.getErrorCount());

        } catch (Exception e) {
            log.error("Calendar sync failed for player {}", playerId, e);
            response.getErrors().add("同期に失敗しました: " + e.getMessage());
            response.setErrorCount(response.getErrorCount() + 1);
        }

        return response;
    }

    private boolean isSessionUpdated(GoogleCalendarEvent existing, PracticeSession session) {
        return existing.getSyncedSessionUpdatedAt() == null
            || !existing.getSyncedSessionUpdatedAt().equals(session.getUpdatedAt());
    }

    private String resolveVenueName(PracticeSession session, Map<Long, Venue> venueMap) {
        if (session.getVenueId() == null) return "未定";
        Venue venue = venueMap.get(session.getVenueId());
        return venue != null ? venue.getName() : "未定";
    }

    private void deleteCalendarEvent(Calendar calendarService, GoogleCalendarEvent mapping,
                                     GoogleCalendarSyncResponse response) {
        try {
            calendarService.events()
                .delete("primary", mapping.getGoogleEventId()).execute();
            googleCalendarEventRepository.delete(mapping);
            response.setDeletedCount(response.getDeletedCount() + 1);
            response.getDetails().add(
                String.format("セッションID %d のイベントを削除", mapping.getSessionId()));
        } catch (Exception e) {
            // 404 = Google側で既に削除済み、マッピングだけクリーンアップ
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                googleCalendarEventRepository.delete(mapping);
                response.setDeletedCount(response.getDeletedCount() + 1);
            } else {
                log.error("Calendar delete error for event {}: {}",
                    mapping.getGoogleEventId(), e.getMessage());
                response.setErrorCount(response.getErrorCount() + 1);
                response.getErrors().add("削除エラー: " + e.getMessage());
            }
        }
    }

    /**
     * アクセストークンからCalendarサービスを構築
     */
    private Calendar buildCalendarService(String accessToken) throws Exception {
        GoogleCredentials credentials = GoogleCredentials.create(
            new AccessToken(accessToken, null));
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
            .setApplicationName(applicationName)
            .build();
    }

    /**
     * PracticeSessionからGoogle Calendar Eventを構築
     *
     * @param session       練習セッション
     * @param venueName     会場名
     * @param eventStartTime プレイヤーの最初の試合の開始時刻（nullの場合は終日イベント）
     * @param eventEndTime   プレイヤーの最後の試合の終了時刻（nullの場合はstartTime+4h）
     */
    private Event buildEvent(PracticeSession session, String venueName,
                             LocalTime eventStartTime, LocalTime eventEndTime) {
        Event event = new Event();
        event.setSummary("わすら@" + venueName);
        event.setDescription(session.getTotalMatches() + "試合");
        event.setLocation(venueName);

        LocalDate date = session.getSessionDate();

        if (eventStartTime != null) {
            // 試合時間割から時刻が取れた場合: DateTime型イベント
            LocalDateTime startLdt = LocalDateTime.of(date, eventStartTime);
            EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                    startLdt.atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()))
                .setTimeZone("Asia/Tokyo");
            event.setStart(start);

            LocalTime endTime = eventEndTime != null ? eventEndTime : eventStartTime.plusHours(4);
            LocalDateTime endLdt = LocalDateTime.of(date, endTime);
            EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                    endLdt.atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()))
                .setTimeZone("Asia/Tokyo");
            event.setEnd(end);
        } else {
            // 時刻なしの場合: 終日イベント
            EventDateTime allDay = new EventDateTime()
                .setDate(new com.google.api.client.util.DateTime(date.toString()))
                .setTimeZone("Asia/Tokyo");
            event.setStart(allDay);
            event.setEnd(allDay);
        }

        return event;
    }
}
