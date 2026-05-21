package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.FeedInfoDto;
import com.karuta.matchtracker.dto.OrganizationFeedDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IcalCalendarFeedService の単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IcalCalendarFeedService 単体テスト")
class IcalCalendarFeedServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VenueMatchScheduleRepository venueMatchScheduleRepository;

    @InjectMocks
    private IcalCalendarFeedService service;

    private static final String VALID_TOKEN = "token-abc";
    private static final Long PLAYER_ID = 100L;
    private static final Long ORG_ID = 200L;
    private static final Long VENUE_ID = 300L;
    private static final Long SESSION_ID = 400L;
    private static final String BASE_URL = "https://example.com";

    private Player player;
    private Organization organization;
    private Venue venue;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appBaseUrl", BASE_URL);

        // production と同じ JST 基準で今日を取得し、未来日付を作る
        futureDate = JstDateTimeUtil.today().plusDays(7);

        player = Player.builder()
                .id(PLAYER_ID)
                .name("テスト太郎")
                .icalFeedToken(VALID_TOKEN)
                .build();

        organization = Organization.builder()
                .id(ORG_ID)
                .code("test-org")
                .name("テスト団体")
                .color("#22c55e")
                .build();

        venue = Venue.builder()
                .id(VENUE_ID)
                .name("テスト会場")
                .defaultMatchCount(5)
                .build();
    }

    // ============================================================
    // generateIcsForOrgFeed テスト
    // ============================================================

    @Test
    @DisplayName("有効なトークンと所属orgIdで未来の参加練習がVEVENTとして含まれる")
    void generateIcsForOrgFeed_validToken_returnsIcsWithVEvents() {
        // Given
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then
        assertThat(ics).contains("BEGIN:VCALENDAR");
        assertThat(ics).contains("END:VCALENDAR");
        assertThat(ics).contains("BEGIN:VEVENT");
        assertThat(ics).contains("END:VEVENT");
        assertThat(ics).contains("テスト団体");
        assertThat(ics).contains("テスト会場");
    }

    @Test
    @DisplayName("無効なトークンの場合 ResourceNotFoundException")
    void generateIcsForOrgFeed_invalidToken_throwsResourceNotFoundException() {
        // Given
        when(playerRepository.findByIcalFeedTokenAndActive("invalid"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.generateIcsForOrgFeed("invalid", ORG_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("icalFeedToken");
    }

    @Test
    @DisplayName("プレイヤーがorgIdに所属していない場合は ResourceNotFoundException")
    void generateIcsForOrgFeed_unaffiliatedOrgId_throwsResourceNotFoundException() {
        // Given
        stubPlayerByToken();
        stubMembership(ORG_ID, false);

        // When & Then
        assertThatThrownBy(() -> service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PlayerOrganization");
    }

    @Test
    @DisplayName("指定 orgId 以外の所属団体の練習はVEVENTに含まれない")
    void generateIcsForOrgFeed_otherOrgPracticeExcluded() {
        // Given: orgId=200 を指定するが、別の orgId=201 の練習もある
        Long otherOrgId = 201L;
        Long otherSessionId = 401L;
        PracticeSession ownSession = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeSession otherOrgSession = createSession(otherSessionId, futureDate, VENUE_ID, otherOrgId,
                LocalTime.of(13, 0), LocalTime.of(15, 0));
        PracticeParticipant ownParticipation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);
        PracticeParticipant otherParticipation = createParticipation(otherSessionId, PLAYER_ID, 1,
                ParticipantStatus.WON);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(ownParticipation, otherParticipation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(ownSession, otherOrgSession));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: 指定 orgId のセッションのみ VEVENT 化される
        long veventCount = countOccurrences(ics, "BEGIN:VEVENT");
        assertThat(veventCount).isEqualTo(1L);
        assertThat(ics).contains("session-" + SESSION_ID + "-player-" + PLAYER_ID);
        assertThat(ics).doesNotContain("session-" + otherSessionId + "-player-");
    }

    @ParameterizedTest
    @EnumSource(value = ParticipantStatus.class,
            names = {"CANCELLED", "DECLINED", "WAITLISTED", "OFFERED", "WAITLIST_DECLINED"})
    @DisplayName("非アクティブステータス（CANCELLED/DECLINED/WAITLISTED/OFFERED/WAITLIST_DECLINED）はVEVENTから除外される")
    void generateIcsForOrgFeed_nonActiveStatusExcluded(ParticipantStatus inactiveStatus) {
        // Given - WON のセッションと非アクティブステータスのセッション各1件
        Long inactiveSessionId = 401L;
        PracticeSession activeSession = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));

        PracticeParticipant activeParticipation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);
        PracticeParticipant inactiveParticipation = createParticipation(inactiveSessionId,
                PLAYER_ID, 1, inactiveStatus);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(activeParticipation, inactiveParticipation));
        // 非アクティブは filter 後に除外されるので、ロード対象になるのは activeSession のみ
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(activeSession));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then - WON のみ VEVENT 化される
        long veventCount = countOccurrences(ics, "BEGIN:VEVENT");
        assertThat(veventCount).isEqualTo(1L);
        assertThat(ics).doesNotContain("session-" + inactiveSessionId + "-player-");
        assertThat(ics).contains("session-" + SESSION_ID + "-player-" + PLAYER_ID);
    }

    @Test
    @DisplayName("PENDINGステータスもisActive()扱いでVEVENTに含まれる")
    void generateIcsForOrgFeed_pendingStatusIncluded() {
        // Given
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.PENDING);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then
        assertThat(countOccurrences(ics, "BEGIN:VEVENT")).isEqualTo(1L);
        assertThat(ics).contains("session-" + SESSION_ID + "-player-" + PLAYER_ID);
    }

    @Test
    @DisplayName("orgFeed: PlayerOrganization.calendarDisplayName が設定されていればタイトルとX-WR-CALNAMEに使われる")
    void generateIcsForOrgFeed_calendarNameUsesDisplayName() {
        // Given
        String customDisplayName = "マイ団体";
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);
        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName(customDisplayName)
                .build();

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: X-WR-CALNAME と SUMMARY 両方で表示名が使われる
        assertThat(ics).contains("X-WR-CALNAME:" + customDisplayName);
        assertThat(ics).contains(customDisplayName);
        // Organization.name (元の団体名) は使われない
        assertThat(ics).doesNotContain("テスト団体");
    }

    @Test
    @DisplayName("orgFeed: displayName 未設定なら X-WR-CALNAME は Organization.name")
    void generateIcsForOrgFeed_calendarNameFallsBackToOrganizationName() {
        // Given: displayName は未設定
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then
        assertThat(ics).contains("X-WR-CALNAME:" + organization.getName());
    }

    @Test
    @DisplayName("VEVENT.UID は session-{sid}-player-{pid}@match-tracker 形式")
    void generateIcsForOrgFeed_uidFormat() {
        // Given
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then
        String expectedUid = "session-" + SESSION_ID + "-player-" + PLAYER_ID + "@match-tracker";
        assertThat(ics).contains("UID:" + expectedUid);
    }

    @Test
    @DisplayName("orgFeed: 未来の参加が0件でも空のVCALENDARが返る")
    void generateIcsForOrgFeed_zeroParticipations_returnsEmptyCalendar() {
        // Given
        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then
        assertThat(ics).contains("BEGIN:VCALENDAR");
        assertThat(ics).contains("END:VCALENDAR");
        assertThat(ics).doesNotContain("BEGIN:VEVENT");
    }

    @Test
    @DisplayName("orgFeed §4.4 ケース5: 会場 VenueMatchSchedule が未登録 → session 全体時刻が採用される")
    void generateIcsForOrgFeed_noVenueSchedule_usesSessionTime() {
        // Given: session 時刻あり、VenueMatchSchedule は未登録
        LocalTime sessionStart = LocalTime.of(13, 30);
        LocalTime sessionEnd = LocalTime.of(17, 0);
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                sessionStart, sessionEnd);
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: schedule 未登録のため、条件B 不成立で session 全体時刻にフォールバック
        assertThat(ics).contains("T133000");
        assertThat(ics).contains("T170000");
    }

    @Test
    @DisplayName("orgFeed: session.start_time / end_time がなければ VenueMatchSchedule から取得")
    void generateIcsForOrgFeed_fallsBackToVenueMatchSchedule() {
        // Given: session の時刻は NULL
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID, null, null);
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        VenueMatchSchedule schedule = VenueMatchSchedule.builder()
                .id(1L)
                .venueId(VENUE_ID)
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(List.of(schedule));

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: VenueMatchSchedule の 09:00 / 10:30 (JST) が使われる
        assertThat(ics).contains("090000");
        assertThat(ics).contains("103000");
    }

    // ============================================================
    // §4.4 参加試合に応じたイベント時刻 (新仕様) テスト
    // ============================================================

    @Test
    @DisplayName("orgFeed §4.4 ケース1: 全参加レコードに match_number あり・会場スケジュール完備 → スケジュール時刻採用")
    void generateIcsForOrgFeed_allMatchSchedulePresent_usesScheduleTime() {
        // Given: session 12:00-18:00 (敢えて schedule とは異なる広めの時刻)、
        //        会場は match1-6 schedule 完備、参加は match1-6 全件
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(12, 0), LocalTime.of(18, 0));

        List<VenueMatchSchedule> schedules = List.of(
                createSchedule(1, LocalTime.of(13, 0), LocalTime.of(13, 45)),
                createSchedule(2, LocalTime.of(13, 50), LocalTime.of(14, 35)),
                createSchedule(3, LocalTime.of(14, 40), LocalTime.of(15, 25)),
                createSchedule(4, LocalTime.of(15, 30), LocalTime.of(16, 15)),
                createSchedule(5, LocalTime.of(16, 20), LocalTime.of(16, 30)),
                createSchedule(6, LocalTime.of(16, 35), LocalTime.of(17, 20))
        );
        List<PracticeParticipant> participations = List.of(
                createParticipation(SESSION_ID, PLAYER_ID, 1, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 2, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 3, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 4, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 5, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 6, ParticipantStatus.WON)
        );

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(participations);
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(schedules);

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: schedule の min(start)=13:00, max(end)=17:20 が採用される
        assertThat(ics).contains("T130000");
        assertThat(ics).contains("T172000");
        // session 全体時刻 12:00 / 18:00 は出ない
        assertThat(ics).doesNotContain("T120000");
        assertThat(ics).doesNotContain("T180000");
    }

    @Test
    @DisplayName("orgFeed §4.4 ケース2: 部分参加 (match3-6 / 会場は match1-6 完備) → match3.start〜match6.end")
    void generateIcsForOrgFeed_partialParticipation_usesScheduleSubset() {
        // Given: session 13:00-17:00, 会場は match1-6 完備, 参加は match3-6
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(13, 0), LocalTime.of(17, 0));

        List<VenueMatchSchedule> schedules = List.of(
                createSchedule(1, LocalTime.of(13, 0), LocalTime.of(13, 45)),
                createSchedule(2, LocalTime.of(13, 50), LocalTime.of(14, 35)),
                createSchedule(3, LocalTime.of(14, 0), LocalTime.of(14, 45)),
                createSchedule(4, LocalTime.of(14, 50), LocalTime.of(15, 35)),
                createSchedule(5, LocalTime.of(15, 40), LocalTime.of(16, 25)),
                createSchedule(6, LocalTime.of(16, 15), LocalTime.of(17, 0))
        );

        List<PracticeParticipant> participations = List.of(
                createParticipation(SESSION_ID, PLAYER_ID, 3, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 4, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 5, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 6, ParticipantStatus.WON)
        );

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(participations);
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(schedules);

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: match3.start=14:00, match6.end=17:00 が採用される
        assertThat(ics).contains("T140000");
        assertThat(ics).contains("T170000");
        // session 全体の 13:00 開始は出ない
        assertThat(ics).doesNotContain("T130000");
    }

    @Test
    @DisplayName("orgFeed §4.4 ケース3: 全試合参加 (match1-6 全件 / 会場 match1-6 完備) → schedule 全体 min/max")
    void generateIcsForOrgFeed_allMatchesParticipation_usesFullScheduleRange() {
        // Given: session 13:00-17:00, 会場 match1-6 完備で min=13:00, max=17:00 (session と一致)
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(13, 0), LocalTime.of(17, 0));

        List<VenueMatchSchedule> schedules = List.of(
                createSchedule(1, LocalTime.of(13, 0), LocalTime.of(13, 45)),
                createSchedule(2, LocalTime.of(13, 50), LocalTime.of(14, 35)),
                createSchedule(3, LocalTime.of(14, 40), LocalTime.of(15, 25)),
                createSchedule(4, LocalTime.of(15, 30), LocalTime.of(16, 15)),
                createSchedule(5, LocalTime.of(16, 20), LocalTime.of(16, 30)),
                createSchedule(6, LocalTime.of(16, 35), LocalTime.of(17, 0))
        );

        List<PracticeParticipant> participations = List.of(
                createParticipation(SESSION_ID, PLAYER_ID, 1, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 2, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 3, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 4, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 5, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 6, ParticipantStatus.WON)
        );

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(participations);
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(schedules);

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: schedule の min=13:00, max=17:00 → session 全体時刻と一致する範囲
        assertThat(ics).contains("T130000");
        assertThat(ics).contains("T170000");
    }

    @Test
    @DisplayName("orgFeed §4.4 ケース4: 同じ session に match_number あり/null 混在 → session 全体時刻")
    void generateIcsForOrgFeed_mixedNullMatchNumber_usesSessionTime() {
        // Given: session 13:00-17:00, schedule match3 のみ登録、
        //        参加は match=3 と match=null の 2件 (混在)
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(13, 0), LocalTime.of(17, 0));

        List<VenueMatchSchedule> schedules = List.of(
                createSchedule(3, LocalTime.of(14, 0), LocalTime.of(14, 45))
        );

        List<PracticeParticipant> participations = List.of(
                createParticipation(SESSION_ID, PLAYER_ID, 3, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, null, ParticipantStatus.WON)
        );

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(participations);
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(schedules);

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: 条件A 不成立 (null 混在) → session 全体時刻が採用される
        assertThat(ics).contains("T130000");
        assertThat(ics).contains("T170000");
        // schedule match3 の 14:00 / 14:45 は採用されない
        assertThat(ics).doesNotContain("T140000");
        assertThat(ics).doesNotContain("T144500");
    }

    @Test
    @DisplayName("orgFeed §4.4 ケース6: 参加 match_number の一部だけスケジュール登録あり → 登録分の min/max")
    void generateIcsForOrgFeed_partialSchedule_usesPresentSchedulesMinMax() {
        // Given: session 13:00-17:00, schedule は match3, match5 のみ登録 (match4 未登録)、
        //        参加は match3-5
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(13, 0), LocalTime.of(17, 0));

        List<VenueMatchSchedule> schedules = List.of(
                createSchedule(3, LocalTime.of(14, 0), LocalTime.of(14, 45)),
                createSchedule(5, LocalTime.of(15, 30), LocalTime.of(16, 15))
        );

        List<PracticeParticipant> participations = List.of(
                createParticipation(SESSION_ID, PLAYER_ID, 3, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 4, ParticipantStatus.WON),
                createParticipation(SESSION_ID, PLAYER_ID, 5, ParticipantStatus.WON)
        );

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(participations);
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(schedules);

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: 登録のある match3.start=14:00, match5.end=16:15 が採用される
        assertThat(ics).contains("T140000");
        assertThat(ics).contains("T161500");
        // session 全体の 13:00 / 17:00 は出ない
        assertThat(ics).doesNotContain("T130000");
        assertThat(ics).doesNotContain("T170000");
    }

    @Test
    @DisplayName("orgFeed §4.4 ケース7: session.startTime/endTime も null かつスケジュール不在 → 全日イベント")
    void generateIcsForOrgFeed_noTimeAndNoSchedule_returnsAllDayEvent() {
        // Given: session 時刻 null, schedule 不在, 参加=match1 (matchNumber あり)
        LocalDate sessionDate = LocalDate.of(2026, 6, 10);
        PracticeSession session = createSession(SESSION_ID, sessionDate, VENUE_ID, ORG_ID,
                null, null);
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        stubPlayerByToken();
        stubMembership(ORG_ID, true);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

        // Then: 条件B 不成立かつ session 時刻 null → VALUE=DATE 形式の全日イベント
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260610");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260611");
    }

    // ============================================================
    // generateIcsForGuestFeed テスト
    // ============================================================

    @Test
    @DisplayName("guestFeed: 所属外組織の練習だけVEVENTに含まれる")
    void generateIcsForGuestFeed_onlyGuestPracticesIncluded() {
        // Given: プレイヤーは ORG_ID に所属。ゲスト参加 = guestOrgId
        Long guestOrgId = 999L;
        Long guestSessionId = 401L;
        Organization guestOrg = Organization.builder()
                .id(guestOrgId)
                .code("guest-org")
                .name("ゲスト団体")
                .color("#000000")
                .build();

        PracticeSession ownSession = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeSession guestSession = createSession(guestSessionId, futureDate, VENUE_ID, guestOrgId,
                LocalTime.of(13, 0), LocalTime.of(15, 0));
        PracticeParticipant ownParticipation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);
        PracticeParticipant guestParticipation = createParticipation(guestSessionId, PLAYER_ID, 1,
                ParticipantStatus.WON);

        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .build();

        stubPlayerByToken();
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(ownParticipation, guestParticipation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(ownSession, guestSession));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(guestOrg));

        // When
        String ics = service.generateIcsForGuestFeed(VALID_TOKEN);

        // Then: ゲスト参加分のみ含まれる
        long veventCount = countOccurrences(ics, "BEGIN:VEVENT");
        assertThat(veventCount).isEqualTo(1L);
        assertThat(ics).contains("session-" + guestSessionId + "-player-");
        assertThat(ics).doesNotContain("session-" + SESSION_ID + "-player-");
    }

    @Test
    @DisplayName("guestFeed: 該当0件でも空のVCALENDARが返る")
    void generateIcsForGuestFeed_noGuestParticipations_returnsEmptyCalendar() {
        // Given: プレイヤーは ORG_ID 所属で、その団体の練習にのみ参加
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .build();

        stubPlayerByToken();
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));

        // When
        String ics = service.generateIcsForGuestFeed(VALID_TOKEN);

        // Then: VCALENDAR は返るが VEVENT は無い
        assertThat(ics).contains("BEGIN:VCALENDAR");
        assertThat(ics).contains("END:VCALENDAR");
        assertThat(ics).doesNotContain("BEGIN:VEVENT");
    }

    @Test
    @DisplayName("guestFeed: タイトルは Organization.name 固定（カスタマイズ対象外）")
    void generateIcsForGuestFeed_titleUsesOrganizationName() {
        // Given: プレイヤーは ORG_ID 所属で displayName をカスタマイズ済み。
        //        ゲスト参加 = guestOrg。本人の displayName 設定は影響してはいけない。
        Long guestOrgId = 999L;
        Long guestSessionId = 401L;
        Organization guestOrg = Organization.builder()
                .id(guestOrgId)
                .code("guest-org")
                .name("ゲスト団体")
                .color("#000000")
                .build();
        PracticeSession guestSession = createSession(guestSessionId, futureDate, VENUE_ID, guestOrgId,
                LocalTime.of(13, 0), LocalTime.of(15, 0));
        PracticeParticipant guestParticipation = createParticipation(guestSessionId, PLAYER_ID, 1,
                ParticipantStatus.WON);

        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName("マイ団体")
                .build();

        stubPlayerByToken();
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(guestParticipation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(guestSession));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(guestOrg));

        // When
        String ics = service.generateIcsForGuestFeed(VALID_TOKEN);

        // Then: ゲスト団体の Organization.name が使われ、所属団体の displayName は出ない
        assertThat(ics).contains("ゲスト団体");
        assertThat(ics).doesNotContain("マイ団体");
    }

    @Test
    @DisplayName("guestFeed: X-WR-CALNAME は \"ゲスト参加\"")
    void generateIcsForGuestFeed_calendarNameIsGuestSanka() {
        // Given: 参加0件でも X-WR-CALNAME は出る
        stubPlayerByToken();
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForGuestFeed(VALID_TOKEN);

        // Then
        assertThat(ics).contains("X-WR-CALNAME:ゲスト参加");
    }

    @Test
    @DisplayName("guestFeed: 無効トークンは ResourceNotFoundException")
    void generateIcsForGuestFeed_invalidToken_throwsResourceNotFoundException() {
        when(playerRepository.findByIcalFeedTokenAndActive("invalid"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateIcsForGuestFeed("invalid"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("icalFeedToken");
    }

    // ============================================================
    // regenerateTokenForPlayer テスト
    // ============================================================

    @Test
    @DisplayName("新トークンが生成されPlayerに保存される（旧トークンと異なる）")
    void regenerateTokenForPlayer_generatesNewToken() {
        // Given
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        String oldToken = player.getIcalFeedToken();

        // When
        String newToken = service.regenerateTokenForPlayer(PLAYER_ID);

        // Then
        assertThat(newToken).isNotNull();
        assertThat(newToken).isNotEmpty();
        assertThat(newToken).isNotEqualTo(oldToken);
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue().getIcalFeedToken()).isEqualTo(newToken);
    }

    @Test
    @DisplayName("存在しないプレイヤーで再発行は ResourceNotFoundException")
    void regenerateTokenForPlayer_invalidPlayer_throwsResourceNotFoundException() {
        // Given
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.regenerateTokenForPlayer(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("999");
        verify(playerRepository, never()).save(any());
    }

    // ============================================================
    // getFeedInfo テスト
    // ============================================================

    @Test
    @DisplayName("getFeedInfo: organizationFeeds と guestFeed が新DTO形式で返る")
    void getFeedInfo_returnsOrgFeedsAndGuestFeed() {
        // Given
        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName("カスタム名")
                .build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        // When
        FeedInfoDto info = service.getFeedInfo(PLAYER_ID);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.getOrganizationFeeds()).hasSize(1);
        OrganizationFeedDto orgDto = info.getOrganizationFeeds().get(0);
        assertThat(orgDto.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(orgDto.getOrganizationName()).isEqualTo("テスト団体");
        assertThat(orgDto.getDisplayName()).isEqualTo("カスタム名");
        assertThat(orgDto.getUrl()).isEqualTo(
                BASE_URL + "/ical/calendar/" + VALID_TOKEN + "/org/" + ORG_ID + ".ics");

        assertThat(info.getGuestFeed()).isNotNull();
        assertThat(info.getGuestFeed().getUrl()).isEqualTo(
                BASE_URL + "/ical/calendar/" + VALID_TOKEN + "/guest.ics");
    }

    @Test
    @DisplayName("getFeedInfo: 所属0件でも guestFeed は必ず返る")
    void getFeedInfo_noOrganizations_stillReturnsGuestFeed() {
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(Collections.emptyList());

        FeedInfoDto info = service.getFeedInfo(PLAYER_ID);

        assertThat(info.getOrganizationFeeds()).isEmpty();
        assertThat(info.getGuestFeed()).isNotNull();
        assertThat(info.getGuestFeed().getUrl()).isEqualTo(
                BASE_URL + "/ical/calendar/" + VALID_TOKEN + "/guest.ics");
    }

    @Test
    @DisplayName("getFeedInfo: フィードURLは末尾スラッシュなしで連結される")
    void getFeedInfo_baseUrlWithTrailingSlash_normalized() {
        // Given: 末尾スラッシュ付きの BASE_URL
        ReflectionTestUtils.setField(service, "appBaseUrl", BASE_URL + "/");

        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        // When
        FeedInfoDto info = service.getFeedInfo(PLAYER_ID);

        // Then: スラッシュ重複は発生しない
        assertThat(info.getOrganizationFeeds().get(0).getUrl()).isEqualTo(
                BASE_URL + "/ical/calendar/" + VALID_TOKEN + "/org/" + ORG_ID + ".ics");
        assertThat(info.getGuestFeed().getUrl()).isEqualTo(
                BASE_URL + "/ical/calendar/" + VALID_TOKEN + "/guest.ics");
    }

    @Test
    @DisplayName("存在しないプレイヤーで getFeedInfo は ResourceNotFoundException")
    void getFeedInfo_invalidPlayer_throwsResourceNotFoundException() {
        // Given
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getFeedInfo(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("999");
    }

    // ============================================================
    // updateDisplayNames テスト
    // ============================================================

    @Test
    @DisplayName("既存所属組織の表示名を更新できる")
    void updateDisplayNames_updatesCustomDisplayName() {
        // Given
        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName(null)
                .build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        Map<Long, String> displayNames = new HashMap<>();
        displayNames.put(ORG_ID, "カスタム表示名");

        // When
        service.updateDisplayNames(PLAYER_ID, displayNames);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlayerOrganization>> captor = ArgumentCaptor.forClass(List.class);
        verify(playerOrganizationRepository).saveAll(captor.capture());
        List<PlayerOrganization> savedList = captor.getValue();
        assertThat(savedList).hasSize(1);
        assertThat(savedList.get(0).getCalendarDisplayName()).isEqualTo("カスタム表示名");
        assertThat(savedList.get(0).getOrganizationId()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("updateDisplayNames: 戻り値DTOは新形式 (organizationFeeds + guestFeed) ")
    void updateDisplayNames_returnsNewDtoFormat() {
        // Given
        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName(null)
                .build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        Map<Long, String> displayNames = new HashMap<>();
        displayNames.put(ORG_ID, "新しい表示名");

        // When
        FeedInfoDto info = service.updateDisplayNames(PLAYER_ID, displayNames);

        // Then
        assertThat(info.getOrganizationFeeds()).hasSize(1);
        assertThat(info.getOrganizationFeeds().get(0).getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(info.getGuestFeed()).isNotNull();
        assertThat(info.getGuestFeed().getUrl()).contains("/guest.ics");
    }

    @Test
    @DisplayName("表示名の前後空白がトリムされる")
    void updateDisplayNames_trimsWhitespace() {
        // Given
        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName(null)
                .build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        Map<Long, String> displayNames = new HashMap<>();
        displayNames.put(ORG_ID, "  カスタム表示名  ");

        // When
        service.updateDisplayNames(PLAYER_ID, displayNames);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlayerOrganization>> captor = ArgumentCaptor.forClass(List.class);
        verify(playerOrganizationRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getCalendarDisplayName()).isEqualTo("カスタム表示名");
    }

    @Test
    @DisplayName("空文字またはnullはNULLに正規化されて保存")
    void updateDisplayNames_emptyStringNormalizedToNull() {
        // Given
        PlayerOrganization playerOrg1 = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName("古い名前")
                .build();
        Long otherOrgId = ORG_ID + 1;
        PlayerOrganization playerOrg2 = PlayerOrganization.builder()
                .id(2L)
                .playerId(PLAYER_ID)
                .organizationId(otherOrgId)
                .calendarDisplayName("別の名前")
                .build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg1, playerOrg2));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        Map<Long, String> displayNames = new HashMap<>();
        displayNames.put(ORG_ID, ""); // 空文字
        displayNames.put(otherOrgId, "   "); // 空白のみ

        // When
        service.updateDisplayNames(PLAYER_ID, displayNames);

        // Then: どちらも null に正規化される
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlayerOrganization>> captor = ArgumentCaptor.forClass(List.class);
        verify(playerOrganizationRepository).saveAll(captor.capture());
        List<PlayerOrganization> savedList = captor.getValue();
        assertThat(savedList).hasSize(2);
        assertThat(savedList).allMatch(po -> po.getCalendarDisplayName() == null);
    }

    @Test
    @DisplayName("所属していない組織IDのエントリは無視される")
    void updateDisplayNames_unaffiliatedOrgIdIgnored() {
        // Given: プレイヤーは ORG_ID にしか所属していない
        PlayerOrganization playerOrg = PlayerOrganization.builder()
                .id(1L)
                .playerId(PLAYER_ID)
                .organizationId(ORG_ID)
                .calendarDisplayName(null)
                .build();
        Long unaffiliatedOrgId = 9999L;

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));

        Map<Long, String> displayNames = new HashMap<>();
        displayNames.put(unaffiliatedOrgId, "勝手な表示名");

        // When
        service.updateDisplayNames(PLAYER_ID, displayNames);

        // Then: 所属していない組織IDのエントリは保存対象になっていない
        // saveAll は呼ばれない（保存対象が0件のため）
        verify(playerOrganizationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("nullまたは空Mapが渡された場合は更新処理をスキップ")
    void updateDisplayNames_nullOrEmptyMap_skipsUpdate() {
        // Given
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(Collections.emptyList());

        // When: null Map
        service.updateDisplayNames(PLAYER_ID, null);
        // When: 空 Map
        service.updateDisplayNames(PLAYER_ID, Collections.emptyMap());

        // Then: saveAll は呼ばれない
        verify(playerOrganizationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("updateDisplayNames で存在しないプレイヤーは ResourceNotFoundException")
    void updateDisplayNames_invalidPlayer_throwsResourceNotFoundException() {
        // Given
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.updateDisplayNames(999L,
                Collections.singletonMap(1L, "name")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("999");
        verify(playerOrganizationRepository, never()).saveAll(anyList());
    }

    // ============================================================
    // 全日イベントの日付（タイムゾーン非依存）テスト
    // ============================================================

    @Test
    @DisplayName("時刻なしセッションは VALUE=DATE 形式で日付がそのまま出る（UTC JVMでも前日にならない）")
    void generateIcsForOrgFeed_allDayEventCorrectDateInUtcJvm() {
        // Given - JVM TZ を UTC に切り替えて、Render等の本番環境を再現
        TimeZone originalTz = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            LocalDate sessionDate = LocalDate.of(2026, 5, 18);
            // 開始/終了時刻なし、VenueMatchSchedule もなし → 全日イベントになる
            PracticeSession session = createSession(SESSION_ID, sessionDate, VENUE_ID, ORG_ID,
                    null, null);
            PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                    ParticipantStatus.WON);

            stubPlayerByToken();
            stubMembership(ORG_ID, true);
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
            when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                    .thenReturn(Collections.emptyList());
            when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                    .thenReturn(List.of(participation));
            when(practiceSessionRepository.findAllById(anyList()))
                    .thenReturn(List.of(session));
            when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
            when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                    .thenReturn(Collections.emptyList());

            // When
            String ics = service.generateIcsForOrgFeed(VALID_TOKEN, ORG_ID);

            // Then - DTSTART/DTEND が VALUE=DATE 形式かつ前日にずれていない
            assertThat(ics).contains("DTSTART;VALUE=DATE:20260518");
            assertThat(ics).contains("DTEND;VALUE=DATE:20260519");
            // 前日 (20260517) として出力されていないことを明示
            assertThat(ics).doesNotContain("DTSTART;VALUE=DATE:20260517");
        } finally {
            TimeZone.setDefault(originalTz);
        }
    }

    // ============================================================
    // ヘルパー
    // ============================================================

    private void stubPlayerByToken() {
        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
    }

    private void stubMembership(Long orgId, boolean exists) {
        when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(PLAYER_ID, orgId))
                .thenReturn(exists);
    }

    private PracticeSession createSession(Long id, LocalDate date, Long venueId, Long orgId,
                                          LocalTime startTime, LocalTime endTime) {
        return PracticeSession.builder()
                .id(id)
                .sessionDate(date)
                .totalMatches(5)
                .venueId(venueId)
                .organizationId(orgId)
                .startTime(startTime)
                .endTime(endTime)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
    }

    private PracticeParticipant createParticipation(Long sessionId, Long playerId,
                                                    Integer matchNumber, ParticipantStatus status) {
        return PracticeParticipant.builder()
                .id(null)
                .sessionId(sessionId)
                .playerId(playerId)
                .matchNumber(matchNumber)
                .status(status)
                .build();
    }

    private VenueMatchSchedule createSchedule(Integer matchNumber, LocalTime startTime, LocalTime endTime) {
        return VenueMatchSchedule.builder()
                .id((long) matchNumber)
                .venueId(VENUE_ID)
                .matchNumber(matchNumber)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    private long countOccurrences(String text, String substring) {
        if (substring.isEmpty()) return 0;
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
