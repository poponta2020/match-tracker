package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukePageCreateRequest;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.DensukeTemplateRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * バリデーション系のテスト。densuke.biz への HTTP 呼び出しは到達しないため副作用なし。
 * 正常系（HTTP 成功）は E2E テストで検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DensukePageCreateService 単体テスト（バリデーション）")
class DensukePageCreateServiceTest {

    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private DensukeTemplateRepository densukeTemplateRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private VenueMatchScheduleRepository venueMatchScheduleRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks
    private DensukePageCreateService service;

    private DensukePageCreateRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = new DensukePageCreateRequest();
        baseRequest.setYear(2026);
        baseRequest.setMonth(5);
        baseRequest.setOrganizationId(1L);
    }

    @Test
    @DisplayName("既存の densuke_urls がある月は IllegalStateException")
    void createPage_throws_whenDensukeUrlAlreadyExists() {
        DensukeUrl existing = DensukeUrl.builder()
                .id(1L).year(2026).month(5).organizationId(1L)
                .url("https://densuke.biz/list?cd=abc").build();
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("既に登録されています");
    }

    @Test
    @DisplayName("practice_sessions が 0 件なら IllegalStateException")
    void createPage_throws_whenNoPracticeSessions() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("練習日が登録されていません");
    }

    @Test
    @DisplayName("会場が Venue マスタに存在しないと IllegalStateException")
    void createPage_throws_whenVenueNotFound() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(LocalDate.of(2026, 5, 10))
                .venueId(999L).totalMatches(1).organizationId(1L)
                .build();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("会場が登録されていません");
    }

    @Test
    @DisplayName("venue_match_schedules に試合時刻が足りないと IllegalStateException")
    void createPage_throws_whenMatchScheduleMissing() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(LocalDate.of(2026, 5, 10))
                .venueId(10L).totalMatches(3).organizationId(1L)
                .build();
        Venue venue = Venue.builder().id(10L).name("テスト会場").defaultMatchCount(3).build();
        // matchNumber=1 のみ、2と3は未登録
        VenueMatchSchedule vms1 = VenueMatchSchedule.builder()
                .id(1L).venueId(10L).matchNumber(1)
                .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(18, 0))
                .build();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(List.of(vms1));

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("試合目の時間割が登録されていません");
    }
}
