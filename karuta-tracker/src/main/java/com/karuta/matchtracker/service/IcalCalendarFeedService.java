package com.karuta.matchtracker.service;

import biweekly.Biweekly;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.DateEnd;
import biweekly.property.DateStart;
import biweekly.util.DateTimeComponents;
import biweekly.util.ICalDate;
import com.karuta.matchtracker.dto.FeedInfoDto;
import com.karuta.matchtracker.dto.GuestFeedDto;
import com.karuta.matchtracker.dto.OrganizationFeedDto;
import com.karuta.matchtracker.entity.Organization;
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
import java.util.HashSet;
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
 * 所属団体カレンダー・ゲスト参加カレンダーに分割した iCalendar (RFC 5545)
 * 形式のテキストとして出力する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IcalCalendarFeedService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final HexFormat TOKEN_HEX = HexFormat.of();
    private static final int TOKEN_BYTE_LENGTH = 24;
    private static final String PRODUCT_ID = "-//karuta-match-tracker//iCal Feed//JP";
    private static final String GUEST_CALENDAR_NAME = "ゲスト参加";
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
     * 所属団体カレンダーの iCal フィードを生成する。
     *
     * @param token プレイヤーのフィードトークン
     * @param orgId 所属団体ID
     * @return iCalendar 形式のテキスト
     * @throws ResourceNotFoundException トークンに対応するプレイヤーが存在しない、または
     *                                   プレイヤーが orgId に所属していない場合
     */
    @Transactional(readOnly = true)
    public String generateIcsForOrgFeed(String token, Long orgId) {
        Player player = loadPlayerByToken(token);
        Long playerId = player.getId();

        if (!playerOrganizationRepository.existsByPlayerIdAndOrganizationId(playerId, orgId)) {
            throw new ResourceNotFoundException(
                    String.format("PlayerOrganization not found with playerId: %d, organizationId: %d",
                            playerId, orgId));
        }

        Organization organization = organizationRepository.findById(orgId).orElse(null);
        Map<Long, String> displayNameByOrgId = playerOrganizationRepository.findByPlayerId(playerId).stream()
                .filter(po -> po.getCalendarDisplayName() != null && !po.getCalendarDisplayName().isBlank())
                .collect(Collectors.toMap(
                        PlayerOrganization::getOrganizationId,
                        PlayerOrganization::getCalendarDisplayName,
                        (a, b) -> a));

        String calendarName = resolveOrgCalendarName(orgId, organization, displayNameByOrgId);

        List<PracticeParticipant> participations = loadActiveParticipations(playerId).stream()
                .collect(Collectors.toList());

        return buildIcsForParticipations(
                playerId,
                participations,
                calendarName,
                session -> Objects.equals(session.getOrganizationId(), orgId),
                organization != null ? Map.of(orgId, organization) : Collections.emptyMap(),
                displayNameByOrgId);
    }

    /**
     * ゲスト参加カレンダーの iCal フィードを生成する。
     *
     * @param token プレイヤーのフィードトークン
     * @return iCalendar 形式のテキスト
     * @throws ResourceNotFoundException トークンに対応するプレイヤーが存在しない場合
     */
    @Transactional(readOnly = true)
    public String generateIcsForGuestFeed(String token) {
        Player player = loadPlayerByToken(token);
        Long playerId = player.getId();

        Set<Long> memberOrgIds = playerOrganizationRepository.findByPlayerId(playerId).stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toSet());

        List<PracticeParticipant> participations = loadActiveParticipations(playerId);

        return buildIcsForParticipations(
                playerId,
                participations,
                GUEST_CALENDAR_NAME,
                session -> session.getOrganizationId() != null
                        && !memberOrgIds.contains(session.getOrganizationId()),
                Collections.emptyMap(),
                Collections.emptyMap());
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

    private Player loadPlayerByToken(String token) {
        return playerRepository.findByIcalFeedTokenAndActive(token)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "icalFeedToken", token));
    }

    private List<PracticeParticipant> loadActiveParticipations(Long playerId) {
        LocalDate today = JstDateTimeUtil.today();
        return practiceParticipantRepository.findUpcomingParticipations(playerId, today).stream()
                .filter(p -> p.getStatus() != null && p.getStatus().isActive())
                .collect(Collectors.toList());
    }

    private String buildIcsForParticipations(Long playerId,
                                             List<PracticeParticipant> activeParticipations,
                                             String calendarName,
                                             java.util.function.Predicate<PracticeSession> sessionFilter,
                                             Map<Long, Organization> presetOrganizationMap,
                                             Map<Long, String> displayNameByOrgId) {
        ICalendar ical = buildBaseCalendar(calendarName);

        if (activeParticipations.isEmpty()) {
            return Biweekly.write(ical).version(ICalVersion.V2_0).tz(JST_TIMEZONE, true).go();
        }

        List<Long> participatingSessionIds = activeParticipations.stream()
                .map(PracticeParticipant::getSessionId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, PracticeSession> sessionMap = practiceSessionRepository
                .findAllById(participatingSessionIds).stream()
                .collect(Collectors.toMap(PracticeSession::getId, s -> s));

        List<Long> filteredSessionIds = participatingSessionIds.stream()
                .filter(sid -> {
                    PracticeSession s = sessionMap.get(sid);
                    return s != null && sessionFilter.test(s);
                })
                .collect(Collectors.toList());

        if (filteredSessionIds.isEmpty()) {
            return Biweekly.write(ical).version(ICalVersion.V2_0).tz(JST_TIMEZONE, true).go();
        }

        Set<Long> venueIds = filteredSessionIds.stream()
                .map(sessionMap::get)
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

        Set<Long> organizationIds = filteredSessionIds.stream()
                .map(sessionMap::get)
                .map(PracticeSession::getOrganizationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Organization> organizationMap = new HashMap<>(presetOrganizationMap);
        Set<Long> missingOrgIds = new HashSet<>(organizationIds);
        missingOrgIds.removeAll(organizationMap.keySet());
        if (!missingOrgIds.isEmpty()) {
            organizationRepository.findAllById(missingOrgIds).forEach(o -> organizationMap.put(o.getId(), o));
        }

        Map<Long, List<Integer>> sessionMatchNumbers = new HashMap<>();
        for (PracticeParticipant p : activeParticipations) {
            if (!filteredSessionIds.contains(p.getSessionId())) continue;
            if (p.getMatchNumber() != null) {
                sessionMatchNumbers
                        .computeIfAbsent(p.getSessionId(), k -> new ArrayList<>())
                        .add(p.getMatchNumber());
            }
        }

        for (Long sessionId : filteredSessionIds) {
            PracticeSession session = sessionMap.get(sessionId);
            if (session == null) continue;

            VEvent event = buildEvent(playerId, session, venueMap, scheduleMap,
                    sessionMatchNumbers.get(sessionId), organizationMap, displayNameByOrgId);
            ical.addEvent(event);
        }

        return Biweekly.write(ical).version(ICalVersion.V2_0).tz(JST_TIMEZONE, true).go();
    }

    private ICalendar buildBaseCalendar(String calendarName) {
        ICalendar ical = new ICalendar();
        ical.setProductId(PRODUCT_ID);
        if (calendarName != null && !calendarName.isBlank()) {
            ical.addExperimentalProperty("X-WR-CALNAME", calendarName);
        }
        return ical;
    }

    private FeedInfoDto buildFeedInfo(Player player) {
        List<PlayerOrganization> playerOrgs = playerOrganizationRepository.findByPlayerId(player.getId());
        List<Long> orgIds = playerOrgs.stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toList());
        Map<Long, Organization> orgMap = organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));

        List<OrganizationFeedDto> orgFeeds = playerOrgs.stream()
                .map(po -> {
                    Organization org = orgMap.get(po.getOrganizationId());
                    return OrganizationFeedDto.builder()
                            .organizationId(po.getOrganizationId())
                            .organizationName(org != null ? org.getName() : null)
                            .displayName(po.getCalendarDisplayName())
                            .url(buildOrgFeedUrl(player.getIcalFeedToken(), po.getOrganizationId()))
                            .build();
                })
                .collect(Collectors.toList());

        GuestFeedDto guestFeed = GuestFeedDto.builder()
                .url(buildGuestFeedUrl(player.getIcalFeedToken()))
                .build();

        return FeedInfoDto.builder()
                .organizationFeeds(orgFeeds)
                .guestFeed(guestFeed)
                .build();
    }

    private String buildOrgFeedUrl(String token, Long orgId) {
        return normalizedBaseUrl() + "/ical/calendar/" + token + "/org/" + orgId + ".ics";
    }

    private String buildGuestFeedUrl(String token) {
        return normalizedBaseUrl() + "/ical/calendar/" + token + "/guest.ics";
    }

    private String normalizedBaseUrl() {
        String base = appBaseUrl == null ? "" : appBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
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
            // 全日イベント (VALUE=DATE)。
            // setDateStart(Date, false) は JVM デフォルトTZ で日付を解釈するため、Render などの UTC 環境では
            // 前日として出力されてしまう。biweekly の DateTimeComponents で日付成分を直接保持し、TZ非依存にする。
            LocalDate nextDay = date.plusDays(1);
            DateTimeComponents startComp = new DateTimeComponents(
                    date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            DateTimeComponents endComp = new DateTimeComponents(
                    nextDay.getYear(), nextDay.getMonthValue(), nextDay.getDayOfMonth());
            event.setDateStart(new DateStart(new ICalDate(startComp, false)));
            event.setDateEnd(new DateEnd(new ICalDate(endComp, false)));
        }

        return event;
    }

    private String resolveOrgCalendarName(Long orgId,
                                          Organization organization,
                                          Map<Long, String> displayNameByOrgId) {
        String override = displayNameByOrgId.get(orgId);
        if (override != null && !override.isBlank()) {
            return override;
        }
        return organization != null ? organization.getName() : "練習";
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
