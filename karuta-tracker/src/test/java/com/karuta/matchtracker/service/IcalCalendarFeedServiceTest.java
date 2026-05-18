package com.karuta.matchtracker.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    // generateIcsForToken テスト
    // ============================================================

    @Test
    @DisplayName("有効なトークンで未来の参加練習がVEVENTとして含まれる")
    void generateIcsForToken_validToken_returnsIcsWithVEvents() {
        // Given
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

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
    void generateIcsForToken_invalidToken_throwsResourceNotFoundException() {
        // Given
        when(playerRepository.findByIcalFeedTokenAndActive("invalid"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.generateIcsForToken("invalid"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("icalFeedToken");
    }

    @Test
    @DisplayName("CANCELLEDステータスの参加はVEVENTから除外される")
    void generateIcsForToken_cancelledParticipationsExcluded() {
        // Given
        Long cancelledSessionId = 401L;
        PracticeSession activeSession = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeSession cancelledSession = createSession(cancelledSessionId,
                futureDate.plusDays(1), VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));

        PracticeParticipant activeParticipation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);
        PracticeParticipant cancelledParticipation = createParticipation(cancelledSessionId,
                PLAYER_ID, 1, ParticipantStatus.CANCELLED);

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(activeParticipation, cancelledParticipation));
        // CANCELLED は filter 後に除外されるので、ロード対象になるのは activeSession のみ
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(activeSession));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then
        // VEVENT は1つだけ含まれる（CANCELLED の方は除外）
        long veventCount = countOccurrences(ics, "BEGIN:VEVENT");
        assertThat(veventCount).isEqualTo(1L);
        // CANCELLED 側の sessionId に対応する UID が含まれないことを確認
        assertThat(ics).doesNotContain("session-" + cancelledSessionId + "-player-");
        // 残る方の UID は含まれる
        assertThat(ics).contains("session-" + SESSION_ID + "-player-" + PLAYER_ID);
    }

    @Test
    @DisplayName("PlayerOrganization.calendarDisplayName が設定されていればタイトルに使われる")
    void generateIcsForToken_displayNameCustomized() {
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

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(List.of(playerOrg));

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then
        assertThat(ics).contains(customDisplayName);
        // Organization.name (元の団体名) は使われない
        assertThat(ics).doesNotContain("テスト団体");
    }

    @Test
    @DisplayName("ゲスト参加（未所属団体）は Organization.name がタイトルに使われる")
    void generateIcsForToken_guestParticipationUsesOrganizationName() {
        // Given: プレイヤーは ORG_ID に所属していないが、その団体の練習にゲスト参加している
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        // 所属がない（ゲスト）
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then: Organization.name がそのまま使われる
        assertThat(ics).contains("テスト団体");
    }

    @Test
    @DisplayName("VEVENT.UID は session-{sid}-player-{pid}@match-tracker 形式")
    void generateIcsForToken_uidFormat() {
        // Given
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then
        String expectedUid = "session-" + SESSION_ID + "-player-" + PLAYER_ID + "@match-tracker";
        assertThat(ics).contains("UID:" + expectedUid);
    }

    @Test
    @DisplayName("未来の参加が0件でも空のVCALENDARが返る")
    void generateIcsForToken_zeroParticipations_returnsEmptyCalendar() {
        // Given
        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then
        assertThat(ics).contains("BEGIN:VCALENDAR");
        assertThat(ics).contains("END:VCALENDAR");
        assertThat(ics).doesNotContain("BEGIN:VEVENT");
    }

    @Test
    @DisplayName("session.start_time / end_time があればそれが使われる")
    void generateIcsForToken_usesPracticeSessionStartEndTime() {
        // Given
        LocalTime sessionStart = LocalTime.of(13, 30);
        LocalTime sessionEnd = LocalTime.of(17, 0);
        PracticeSession session = createSession(SESSION_ID, futureDate, VENUE_ID, ORG_ID,
                sessionStart, sessionEnd);
        PracticeParticipant participation = createParticipation(SESSION_ID, PLAYER_ID, 1,
                ParticipantStatus.WON);

        // VenueMatchSchedule は別の時刻を返すが、session.start/end 優先のはず
        VenueMatchSchedule unusedSchedule = VenueMatchSchedule.builder()
                .id(1L)
                .venueId(VENUE_ID)
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(List.of(unusedSchedule));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then: 13:30 (JST) → UTC 04:30 として現れる想定
        // UTC タイムスタンプを正規表現で確認するのは脆いので、session 時刻が使われていることを
        // VenueMatchSchedule の時刻が混ざっていないことで確認する
        // (DTSTART/DTEND の時刻部分が 1330/1700 形式で含まれる)
        assertThat(ics).contains("133000");
        assertThat(ics).contains("170000");
        // VenueMatchSchedule の 0900/1030 は使われない
        assertThat(ics).doesNotContain("T090000");
    }

    @Test
    @DisplayName("session.start_time / end_time がなければ VenueMatchSchedule から取得")
    void generateIcsForToken_fallsBackToVenueMatchSchedule() {
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

        when(playerRepository.findByIcalFeedTokenAndActive(VALID_TOKEN))
                .thenReturn(Optional.of(player));
        when(practiceParticipantRepository.findUpcomingParticipations(eq(PLAYER_ID), any(LocalDate.class)))
                .thenReturn(List.of(participation));
        when(practiceSessionRepository.findAllById(anyList()))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(anyList()))
                .thenReturn(List.of(schedule));
        when(organizationRepository.findAllById(any())).thenReturn(List.of(organization));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());

        // When
        String ics = service.generateIcsForToken(VALID_TOKEN);

        // Then: VenueMatchSchedule の 09:00 / 10:30 (JST) が使われる
        assertThat(ics).contains("090000");
        assertThat(ics).contains("103000");
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
    @DisplayName("フィードURLと所属団体が返る")
    void getFeedInfo_returnsUrlAndOrganizations() {
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
        assertThat(info.getUrl()).isEqualTo(BASE_URL + "/ical/calendar/" + VALID_TOKEN + ".ics");
        assertThat(info.getOrganizations()).hasSize(1);
        CalendarOrganizationDto orgDto = info.getOrganizations().get(0);
        assertThat(orgDto.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(orgDto.getOrganizationName()).isEqualTo("テスト団体");
        assertThat(orgDto.getDisplayName()).isEqualTo("カスタム名");
    }

    @Test
    @DisplayName("フィードURLは末尾スラッシュなしで連結される")
    void getFeedInfo_baseUrlWithTrailingSlash_normalized() {
        // Given: 末尾スラッシュ付きの BASE_URL
        ReflectionTestUtils.setField(service, "appBaseUrl", BASE_URL + "/");

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerOrganizationRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Collections.emptyList());
        when(organizationRepository.findAllById(any())).thenReturn(Collections.emptyList());

        // When
        FeedInfoDto info = service.getFeedInfo(PLAYER_ID);

        // Then: スラッシュ重複は発生しない
        assertThat(info.getUrl()).isEqualTo(BASE_URL + "/ical/calendar/" + VALID_TOKEN + ".ics");
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
    // ヘルパー
    // ============================================================

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
