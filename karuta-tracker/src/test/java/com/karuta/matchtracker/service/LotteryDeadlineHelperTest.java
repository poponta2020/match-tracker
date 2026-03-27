package com.karuta.matchtracker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryDeadlineHelper テスト")
class LotteryDeadlineHelperTest {

    @Mock
    private SystemSettingService systemSettingService;

    @InjectMocks
    private LotteryDeadlineHelper helper;

    @Test
    @DisplayName("締め切りなしモード: isNoDeadline が true を返す")
    void isNoDeadline_whenMinusOne_returnsTrue() {
        when(systemSettingService.isNoDeadline()).thenReturn(true);

        assertThat(helper.isNoDeadline()).isTrue();
    }

    @Test
    @DisplayName("通常モード: isNoDeadline が false を返す")
    void isNoDeadline_whenZero_returnsFalse() {
        when(systemSettingService.isNoDeadline()).thenReturn(false);

        assertThat(helper.isNoDeadline()).isFalse();
    }

    @Test
    @DisplayName("締め切りなしモード: getDeadline が null を返す")
    void getDeadline_whenNoDeadline_returnsNull() {
        when(systemSettingService.getLotteryDeadlineDaysBefore()).thenReturn(-1);

        LocalDateTime result = helper.getDeadline(2026, 4);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("通常モード(N=0): getDeadline が前月末日を返す")
    void getDeadline_whenZero_returnsLastDayOfPreviousMonth() {
        when(systemSettingService.getLotteryDeadlineDaysBefore()).thenReturn(0);

        LocalDateTime result = helper.getDeadline(2026, 4);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 31, 0, 0, 0));
    }

    @Test
    @DisplayName("通常モード(N=3): getDeadline が月初の3日前を返す")
    void getDeadline_whenThree_returnsThreeDaysBefore() {
        when(systemSettingService.getLotteryDeadlineDaysBefore()).thenReturn(3);

        LocalDateTime result = helper.getDeadline(2026, 4);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 29, 0, 0, 0));
    }

    @Test
    @DisplayName("締め切りなしモード: isBeforeDeadline が常に true を返す")
    void isBeforeDeadline_whenNoDeadline_alwaysTrue() {
        when(systemSettingService.isNoDeadline()).thenReturn(true);

        assertThat(helper.isBeforeDeadline(2026, 4)).isTrue();
        assertThat(helper.isBeforeDeadline(2020, 1)).isTrue();
    }

    @Test
    @DisplayName("締め切りなしモード: isAfterDeadline が常に false を返す")
    void isAfterDeadline_whenNoDeadline_alwaysFalse() {
        when(systemSettingService.isNoDeadline()).thenReturn(true);

        assertThat(helper.isAfterDeadline(2026, 4)).isFalse();
        assertThat(helper.isAfterDeadline(2020, 1)).isFalse();
    }

    @Test
    @DisplayName("締め切りなしモード: shouldLotteryBeExecuted が常に false を返す")
    void shouldLotteryBeExecuted_whenNoDeadline_alwaysFalse() {
        when(systemSettingService.isNoDeadline()).thenReturn(true);

        assertThat(helper.shouldLotteryBeExecuted(2026, 4)).isFalse();
    }
}
