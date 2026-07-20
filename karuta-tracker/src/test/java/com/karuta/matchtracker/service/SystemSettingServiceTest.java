package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.SystemSetting;
import com.karuta.matchtracker.repository.SystemSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingService テスト")
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private SystemSettingService systemSettingService;

    private static final Long ORG_ID = 1L;

    /** 指定キーの保存値をモックする */
    private void stub(String key, String value) {
        SystemSetting s = new SystemSetting();
        s.setSettingKey(key);
        s.setOrganizationId(ORG_ID);
        s.setSettingValue(value);
        when(systemSettingRepository.findBySettingKeyAndOrganizationId(key, ORG_ID))
                .thenReturn(Optional.of(s));
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 未設定ならデフォルト30を返す")
    void weightCapPercentile_unset_returnsDefault30() {
        when(systemSettingRepository.findBySettingKeyAndOrganizationId(
                SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, ORG_ID))
                .thenReturn(Optional.empty());

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(30);
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 範囲内の保存値をそのまま読み取る")
    void weightCapPercentile_readsStoredValue() {
        stub(SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, "50");

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(50);
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 負数は0にクランプ")
    void weightCapPercentile_negative_clampedTo0() {
        stub(SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, "-5");

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(0);
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 101以上は100にクランプ")
    void weightCapPercentile_over100_clampedTo100() {
        stub(SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, "150");

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(100);
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 境界値 0 はそのまま")
    void weightCapPercentile_zero_stays() {
        stub(SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, "0");

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(0);
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 境界値 100 はそのまま")
    void weightCapPercentile_hundred_stays() {
        stub(SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, "100");

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(100);
    }

    @Test
    @DisplayName("getLotteryWeightCapPercentile: 非数値はデフォルト30")
    void weightCapPercentile_nonNumeric_returnsDefault30() {
        stub(SystemSettingService.LOTTERY_WEIGHT_CAP_PERCENTILE, "abc");

        assertThat(systemSettingService.getLotteryWeightCapPercentile(ORG_ID)).isEqualTo(30);
    }
}
