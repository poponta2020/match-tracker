package com.karuta.matchtracker.service;

import biweekly.Biweekly;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import com.karuta.matchtracker.dto.CalendarOrganizationDto;
import com.karuta.matchtracker.dto.FeedInfoDto;
import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerOrganization;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * iCalカレンダーフィード生成サービス
 *
 * プレイヤーごとに発行された固定トークンを用いて、未来の参加練習を
 * iCalendar (RFC 5545) 形式のテキストとして出力する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IcalCalendarFeedService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final HexFormat TOKEN_HEX = HexFormat.of();
    private static final int TOKEN_BYTE_LENGTH = 24;
    private static final String PRODUCT_ID = "-//karuta-match-tracker//iCal Feed//JP";
    private static final TimeZone JST_TIMEZONE = TimeZone.getTimeZone(JstDateTimeUtil.JST);

    private final PlayerRepository playerRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueRepository venueRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /**
     * トークンから iCal フィード（VCALENDAR テキスト）を生成する。
     *
     * @param token プレイヤーのフィードトークン
     * @return iCalendar 形式のテキスト
     * @throws ResourceNotFoundException トークンに対応するアクティブなプレイヤーが存在しない場合
     */
    @Transactional(readOnly = true)
    public String generateIcsForToken(String token) {
        Player player = playerRepository.findByIcalFeedTokenAndActive(token)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "icalFeedToken", token));

        Long playerId = player.getId();
        LocalDate today = JstDateTimeUtil.today();

        List<PracticeParticipant> participations = practiceParticipantRepository
                .findUpcomingParticipations(playerId, today).stream()
                .filter(p -> p.getStatus() != ParticipantStatus.CANCELLED)
                .collect(Collectors.toList());

        ICalendar ical = new ICalendar();
        ical.setProductId(PRODUCT_ID);

        if (participations.isEmpty()) {
            return Biweekly.write(ical).version(ICalVersion.V2_0).tz(JST_TIMEZONE, true).go();
        }

        // セッションIDをユニーク化（順序維持）
        List<Long> participatingSessionIds = participations.stream()
                .map(PracticeParticipant::getSessionId)
                .distinct()
                .collect(Collectors.toList());

        // 関連エンティティを一括取得
        Map<Long, PracticeSession> sessionMap = practiceSessionRepository
                .findAllById(participatingSessionIds).stream()
                .collect(Collectors.toMap(PracticeSession::getId, s -> s));

        Set<Long> venueIds = sessionMap.values().stream()
                .map(PracticeSession::getVenueId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Venue> venueMap = venueRepository.findAllById(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, v -> v));

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

        Set<Long> organizationIds = sessionMap.values().stream()
                .map(PracticeSession::getOrganizationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Organization> organizationMap = organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));

        Map<Long, String> displayNameByOrgId = playerOrganizationRepository.findByPlayerId(playerId).stream()
                .filter(po -> po.getCalendarDisplayName() != null && !po.getCalendarDisplayName().isBlank())
                .collect(Collectors.toMap(
                        PlayerOrganization::getOrganizationId,
                        PlayerOrganization::getCalendarDisplayName,
                        (a, b) -> a));

        // セッション単位で試合番号をまとめる
        Map<Long, List<Integer>> sessionMatchNumbers = new HashMap<>();
        for (PracticeParticipant p : participations) {
            if (p.getMatchNumber() != null) {
                sessionMatchNumbers
                        .computeIfAbsent(p.getSessionId(), k -> new ArrayList<>())
                        .add(p.getMatchNumber());
            }
        }

        for (Long sessionId : participatingSessionIds) {
            PracticeSession session = sessionMap.get(sessionId);
            if (session == null) continue;

            VEvent event = buildEvent(playerId, session, venueMap, scheduleMap,
                    sessionMatchNumbers.get(sessionId), organizationMap, displayNameByOrgId);
            ical.addEvent(event);
        }

        return Biweekly.write(ical).version(ICalVersion.V2_0).tz(JST_TIMEZONE, true).go();
    }

    /**
     * プレイヤーの iCal フィードトークンを再発行する。
     *
     * @param playerId 対象プレイヤーID
     * @return 新しいトークン
     * @throws ResourceNotFoundException プレイヤーが見つからない場合
     */
    @Transactional
    public String regenerateTokenForPlayer(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        String newToken = generateRandomToken();
        player.setIcalFeedToken(newToken);
        playerRepository.save(player);

        log.info("Regenerated iCal feed token for player {}", playerId);
        return newToken;
    }

    /**
     * フィードURLと所属団体・表示名の一覧を取得する。
     *
     * @param playerId 対象プレイヤーID
     * @return フィード情報
     * @throws ResourceNotFoundException プレイヤーが見つからない場合
     */
    @Transactional(readOnly = true)
    public FeedInfoDto getFeedInfo(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));
        return buildFeedInfo(player);
    }

    /**
     * 所属団体ごとのカレンダー表示名を一括更新する。
     *
     * 渡された Map のうち、プレイヤーが所属していない団体IDは無視する。
     * 値が null または空文字の場合は NULL にリセットする。
     *
     * @param playerId     対象プレイヤーID
     * @param displayNames {organizationId -> displayName} のマップ
     * @return 更新後のフィード情報
     * @throws ResourceNotFoundException プレイヤーが見つからない場合
     */
    @Transactional
    public FeedInfoDto updateDisplayNames(Long playerId, Map<Long, String> displayNames) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        if (displayNames != null && !displayNames.isEmpty()) {
            List<PlayerOrganization> playerOrgs = playerOrganizationRepository.findByPlayerId(playerId);
            Map<Long, PlayerOrganization> byOrgId = playerOrgs.stream()
                    .collect(Collectors.toMap(PlayerOrganization::getOrganizationId, po -> po));

            List<PlayerOrganization> toSave = new ArrayList<>();
            for (Map.Entry<Long, String> entry : displayNames.entrySet()) {
                PlayerOrganization po = byOrgId.get(entry.getKey());
                if (po == null) {
                    log.debug("Skipping displayName update for non-member organization {} (player {})",
                            entry.getKey(), playerId);
                    continue;
                }
                String value = entry.getValue();
                String normalized = (value == null || value.isBlank()) ? null : value.trim();
                po.setCalendarDisplayName(normalized);
                toSave.add(po);
            }

            if (!toSave.isEmpty()) {
                playerOrganizationRepository.saveAll(toSave);
            }
        }

        return buildFeedInfo(player);
    }

    // ============================================================
    // 内部ヘルパー
    // ============================================================

    private FeedInfoDto buildFeedInfo(Player player) {
        String url = buildFeedUrl(player.getIcalFeedToken());

        List<PlayerOrganization> playerOrgs = playerOrganizationRepository.findByPlayerId(player.getId());
        List<Long> orgIds = playerOrgs.stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toList());
        Map<Long, Organization> orgMap = organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));

        List<CalendarOrganizationDto> orgDtos = playerOrgs.stream()
                .map(po -> {
                    Organization org = orgMap.get(po.getOrganizationId());
                    return CalendarOrganizationDto.builder()
                            .organizationId(po.getOrganizationId())
                            .organizationName(org != null ? org.getName() : null)
                            .displayName(po.getCalendarDisplayName())
                            .build();
                })
                .collect(Collectors.toList());

        return FeedInfoDto.builder()
                .url(url)
                .organizations(orgDtos)
                .build();
    }

    private String buildFeedUrl(String token) {
        String base = appBaseUrl == null ? "" : appBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/ical/calendar/" + token + ".ics";
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        TOKEN_RANDOM.nextBytes(bytes);
        return TOKEN_HEX.formatHex(bytes);
    }

    private VEvent buildEvent(Long playerId,
                              PracticeSession session,
                              Map<Long, Venue> venueMap,
                              Map<Long, Map<Integer, VenueMatchSchedule>> scheduleMap,
                              List<Integer> matchNumbers,
                              Map<Long, Organization> organizationMap,
                              Map<Long, String> displayNameByOrgId) {
        VEvent event = new VEvent();

        event.setUid("session-" + session.getId() + "-player-" + playerId + "@match-tracker");

        String displayName = resolveDisplayName(session, organizationMap, displayNameByOrgId);
        String venueName = resolveVenueName(session, venueMap);
        event.setSummary(displayName + "＠" + venueName);
        event.setLocation(venueName);

        Integer minMatch = null;
        Integer maxMatch = null;
        if (matchNumbers != null && !matchNumbers.isEmpty()) {
            minMatch = Collections.min(matchNumbers);
            maxMatch = Collections.max(matchNumbers);
        }

        if (minMatch != null && maxMatch != null) {
            String desc = (minMatch.equals(maxMatch))
                    ? "試合数: " + minMatch + "試合"
                    : "試合数: " + minMatch + "〜" + maxMatch + "試合";
            event.setDescription(desc);
        }

        LocalTime startTime = session.getStartTime();
        LocalTime endTime = session.getEndTime();

        if ((startTime == null || endTime == null) && session.getVenueId() != null
                && matchNumbers != null && !matchNumbers.isEmpty()) {
            Map<Integer, VenueMatchSchedule> venueSchedule = scheduleMap.get(session.getVenueId());
            if (venueSchedule != null) {
                VenueMatchSchedule firstSchedule = venueSchedule.get(minMatch);
                VenueMatchSchedule lastSchedule = venueSchedule.get(maxMatch);
                if (startTime == null && firstSchedule != null) {
                    startTime = firstSchedule.getStartTime();
                }
                if (endTime == null && lastSchedule != null) {
                    endTime = lastSchedule.getEndTime();
                }
            }
        }

        LocalDate date = session.getSessionDate();
        if (startTime != null) {
            LocalDateTime startLdt = LocalDateTime.of(date, startTime);
            LocalTime resolvedEnd = endTime != null ? endTime : startTime.plusHours(4);
            LocalDateTime endLdt = LocalDateTime.of(date, resolvedEnd);

            Date startDate = Date.from(startLdt.atZone(JstDateTimeUtil.JST).toInstant());
            Date endDateTime = Date.from(endLdt.atZone(JstDateTimeUtil.JST).toInstant());
            event.setDateStart(startDate, true);
            event.setDateEnd(endDateTime, true);
        } else {
            // 全日イベント: 日付のみ（hasTime=false）
            Date dayStart = Date.from(date.atStartOfDay(JstDateTimeUtil.JST).toInstant());
            Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(JstDateTimeUtil.JST).toInstant());
            event.setDateStart(dayStart, false);
            event.setDateEnd(dayEnd, false);
        }

        return event;
    }

    private String resolveDisplayName(PracticeSession session,
                                      Map<Long, Organization> organizationMap,
                                      Map<Long, String> displayNameByOrgId) {
        Long orgId = session.getOrganizationId();
        if (orgId == null) return "練習";
        String override = displayNameByOrgId.get(orgId);
        if (override != null && !override.isBlank()) {
            return override;
        }
        Organization org = organizationMap.get(orgId);
        return org != null ? org.getName() : "練習";
    }

    private String resolveVenueName(PracticeSession session, Map<Long, Venue> venueMap) {
        if (session.getVenueId() == null) return "未定";
        Venue venue = venueMap.get(session.getVenueId());
        return venue != null ? venue.getName() : "未定";
    }
}
