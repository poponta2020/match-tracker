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
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
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
    private int baseYear;
    private int baseMonth;

    @BeforeEach
    void setUp() {
        // テストは実行日の JST「翌月」を対象にする（当月〜+2ヶ月の範囲内で固定）
        YearMonth ym = YearMonth.now(JstDateTimeUtil.JST).plusMonths(1);
        baseYear = ym.getYear();
        baseMonth = ym.getMonthValue();
        baseRequest = new DensukePageCreateRequest();
        baseRequest.setYear(baseYear);
        baseRequest.setMonth(baseMonth);
        baseRequest.setOrganizationId(1L);
    }

    @Test
    @DisplayName("既存の densuke_urls がある月は IllegalStateException")
    void createPage_throws_whenDensukeUrlAlreadyExists() {
        DensukeUrl existing = DensukeUrl.builder()
                .id(1L).year(baseYear).month(baseMonth).organizationId(1L)
                .url("https://densuke.biz/list?cd=abc").build();
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("既に登録されています");
    }

    @Test
    @DisplayName("practice_sessions が 0 件なら IllegalStateException")
    void createPage_throws_whenNoPracticeSessions() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("練習日が登録されていません");
    }

    @Test
    @DisplayName("会場が Venue マスタに存在しないと IllegalStateException")
    void createPage_throws_whenVenueNotFound() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(LocalDate.of(baseYear, baseMonth, 10))
                .venueId(999L).totalMatches(1).organizationId(1L)
                .build();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
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
                .id(1L).sessionDate(LocalDate.of(baseYear, baseMonth, 10))
                .venueId(10L).totalMatches(3).organizationId(1L)
                .build();
        Venue venue = Venue.builder().id(10L).name("テスト会場").defaultMatchCount(3).build();
        // matchNumber=1 のみ、2と3は未登録
        VenueMatchSchedule vms1 = VenueMatchSchedule.builder()
                .id(1L).venueId(10L).matchNumber(1)
                .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(18, 0))
                .build();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(List.of(vms1));

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("試合目の時間割が登録されていません");
    }

    @Test
    @DisplayName("過去月を指定すると IllegalStateException（API 直叩きでもフロントと同じ制約を適用）")
    void createPage_throws_whenTargetMonthIsInPast() {
        YearMonth past = YearMonth.now(JstDateTimeUtil.JST).minusMonths(1);
        DensukePageCreateRequest req = new DensukePageCreateRequest();
        req.setYear(past.getYear());
        req.setMonth(past.getMonthValue());
        req.setOrganizationId(1L);

        assertThatThrownBy(() -> service.createPage(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作成可能範囲外");
    }

    @Test
    @DisplayName("3ヶ月以上先を指定すると IllegalStateException")
    void createPage_throws_whenTargetMonthIsTooFarInFuture() {
        YearMonth far = YearMonth.now(JstDateTimeUtil.JST).plusMonths(3);
        DensukePageCreateRequest req = new DensukePageCreateRequest();
        req.setYear(far.getYear());
        req.setMonth(far.getMonthValue());
        req.setOrganizationId(1L);

        assertThatThrownBy(() -> service.createPage(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作成可能範囲外");
    }

    @Test
    @DisplayName("saveAndFlush で UNIQUE 制約違反が起きたら『既に登録されています』で IllegalStateException に変換する（同時実行レース）")
    void createPage_throws_whenConcurrentRaceHitsUniqueConstraint() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(LocalDate.of(baseYear, baseMonth, 10))
                .venueId(10L).totalMatches(1).organizationId(1L)
                .build();
        Venue venue = Venue.builder().id(10L).name("テスト会場").defaultMatchCount(1).build();
        VenueMatchSchedule vms1 = VenueMatchSchedule.builder()
                .id(1L).venueId(10L).matchNumber(1)
                .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(18, 0))
                .build();

        // 事前チェックは通過（= もう片方の同時リクエストがまだコミットしていない想定）
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(Optional.empty());
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(baseYear, baseMonth, 1L))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(any())).thenReturn(List.of(venue));
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(List.of(vms1));
        // 仮レコード INSERT のフラッシュで UNIQUE 制約違反（もう片方が先にコミット済み）
        when(densukeUrlRepository.saveAndFlush(any(DensukeUrl.class)))
                .thenThrow(new DataIntegrityViolationException("unique_violation"));

        assertThatThrownBy(() -> service.createPage(baseRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("既に登録されています");
    }

    @Test
    @DisplayName("validateYearMonth: 当月・+2ヶ月は許可、過去月・+3ヶ月は拒否（境界値）")
    void validateYearMonth_boundaries() {
        YearMonth current = YearMonth.of(2026, 5);

        // 許可: 当月、+1、+2
        assertThatCode(() -> DensukePageCreateService.validateYearMonth(2026, 5, current))
                .doesNotThrowAnyException();
        assertThatCode(() -> DensukePageCreateService.validateYearMonth(2026, 6, current))
                .doesNotThrowAnyException();
        assertThatCode(() -> DensukePageCreateService.validateYearMonth(2026, 7, current))
                .doesNotThrowAnyException();

        // 拒否: -1、+3
        assertThatThrownBy(() -> DensukePageCreateService.validateYearMonth(2026, 4, current))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作成可能範囲外");
        assertThatThrownBy(() -> DensukePageCreateService.validateYearMonth(2026, 8, current))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作成可能範囲外");

        // 年跨ぎ: 12月 current → 翌年2月まで許可
        YearMonth dec = YearMonth.of(2026, 12);
        assertThatCode(() -> DensukePageCreateService.validateYearMonth(2027, 2, dec))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DensukePageCreateService.validateYearMonth(2027, 3, dec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作成可能範囲外");

        // 不正な月
        assertThatThrownBy(() -> DensukePageCreateService.validateYearMonth(2026, 13, current))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不正");
    }
}
