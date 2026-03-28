package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.SystemSetting;
import com.karuta.matchtracker.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * システム設定サービス
 */
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    public static final String LOTTERY_DEADLINE_DAYS_BEFORE = "lottery_deadline_days_before";
    public static final String LOTTERY_NORMAL_RESERVE_PERCENT = "lottery_normal_reserve_percent";

    private final SystemSettingRepository systemSettingRepository;

    /**
     * 団体ごとの設定値を取得する
     */
    public Optional<String> getValue(String key, Long organizationId) {
        return systemSettingRepository.findBySettingKeyAndOrganizationId(key, organizationId)
                .map(SystemSetting::getSettingValue);
    }

    /**
     * 団体ごとの設定値をint型で取得する（デフォルト値付き）
     */
    public int getIntValue(String key, Long organizationId, int defaultValue) {
        return getValue(key, organizationId)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    /**
     * 締切日数を取得する（月初から何日前）
     */
    public int getLotteryDeadlineDaysBefore(Long organizationId) {
        return getIntValue(LOTTERY_DEADLINE_DAYS_BEFORE, organizationId, 0);
    }

    /**
     * 締め切りなしモードかどうか（-1の場合）
     */
    public boolean isNoDeadline(Long organizationId) {
        return getLotteryDeadlineDaysBefore(organizationId) == -1;
    }

    /**
     * 一般枠の最低保証割合を取得する（0〜100、デフォルト30%）
     */
    public int getLotteryNormalReservePercent(Long organizationId) {
        return getIntValue(LOTTERY_NORMAL_RESERVE_PERCENT, organizationId, 30);
    }

    /**
     * 団体ごとの設定値を保存する
     */
    @Transactional
    public SystemSetting setValue(String key, String value, Long organizationId, Long updatedBy) {
        SystemSetting setting = systemSettingRepository.findBySettingKeyAndOrganizationId(key, organizationId)
                .orElseGet(() -> {
                    SystemSetting s = new SystemSetting();
                    s.setSettingKey(key);
                    s.setOrganizationId(organizationId);
                    return s;
                });
        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);
        return systemSettingRepository.save(setting);
    }

    /**
     * 団体ごとの全設定を取得する
     */
    public List<SystemSetting> getAllByOrganization(Long organizationId) {
        return systemSettingRepository.findByOrganizationId(organizationId);
    }

    /**
     * 全設定を取得する（SUPER_ADMIN用）
     */
    public List<SystemSetting> getAll() {
        return systemSettingRepository.findAll();
    }
}
