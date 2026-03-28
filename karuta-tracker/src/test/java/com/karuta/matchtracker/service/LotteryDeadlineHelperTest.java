package com.karuta.matchtracker.service;

import com.karuta.matchtracker.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryDeadlineHelper テスト")
class LotteryDeadlineHelperTest {

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private LotteryDeadlineHelper helper;

    private static final Long ORG_ID = 1L;

    @Test
    @DisplayName("締め切りなしモード: isNoDeadline が true を返す")
    void isNoDeadline_whenMinusOne_returnsTrue() {
        when(systemSettingService.isNoDeadline(ORG_ID)).thenReturn(true);

        assertThat(helper.isNoDeadline(ORG_ID)).isTrue();
    }

    @Test
    @DisplayName("通常モード: isNoDeadline が false を返す")
    void isNoDeadline_whenZero_returnsFalse() {
        when(systemSettingService.isNoDeadline(ORG_ID)).thenReturn(false);

        assertThat(helper.isNoDeadline(ORG_ID)).isFalse();
    }

    @Test
    @DisplayName("締め切りなしモード: getDeadline が null を返す")
    void getDeadline_whenNoDeadline_returnsNull() {
        when(systemSettingService.getLotteryDeadlineDaysBefore(ORG_ID)).thenReturn(-1);

        LocalDateTime result = helper.getDeadline(2026, 4, ORG_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("通常モード(N=0): getDeadline が前月末日を返す")
    void getDeadline_whenZero_returnsLastDayOfPreviousMonth() {
        when(systemSettingService.getLotteryDeadlineDaysBefore(ORG_ID)).thenReturn(0);

        LocalDateTime result = helper.getDeadline(2026, 4, ORG_ID);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 31, 0, 0, 0));
    }

    @Test
    @DisplayName("通常モード(N=3): getDeadline が月初の3日前を返す")
    void getDeadline_whenThree_returnsThreeDaysBefore() {
        when(systemSettingService.getLotteryDeadlineDaysBefore(ORG_ID)).thenReturn(3);

        LocalDateTime result = helper.getDeadline(2026, 4, ORG_ID);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 29, 0, 0, 0));
    }

    @Test
    @DisplayName("締め切りなしモード: isBeforeDeadline が常に true を返す")
    void isBeforeDeadline_whenNoDeadline_alwaysTrue() {
        when(systemSettingService.isNoDeadline(ORG_ID)).thenReturn(true);

        assertThat(helper.isBeforeDeadline(2026, 4, ORG_ID)).isTrue();
        assertThat(helper.isBeforeDeadline(2020, 1, ORG_ID)).isTrue();
    }

    @Test
    @DisplayName("締め切りなしモード: isAfterDeadline が常に false を返す")
    void isAfterDeadline_whenNoDeadline_alwaysFalse() {
        when(systemSettingService.isNoDeadline(ORG_ID)).thenReturn(true);

        assertThat(helper.isAfterDeadline(2026, 4, ORG_ID)).isFalse();
        assertThat(helper.isAfterDeadline(2020, 1, ORG_ID)).isFalse();
    }

    @Test
    @DisplayName("締め切りなしモード: shouldLotteryBeExecuted が常に false を返す")
    void shouldLotteryBeExecuted_whenNoDeadline_alwaysFalse() {
        when(systemSettingService.isNoDeadline(ORG_ID)).thenReturn(true);

        assertThat(helper.shouldLotteryBeExecuted(2026, 4, ORG_ID)).isFalse();
    }

    @Test
    @DisplayName("SAME_DAY: getSameDayDeadline が当日12:00を返す")
    void getSameDayDeadline_returnsNoonOfDate() {
        LocalDate date = LocalDate.of(2026, 4, 15);

        LocalDateTime result = helper.getSameDayDeadline(date);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 4, 15, 12, 0));
    }
}
