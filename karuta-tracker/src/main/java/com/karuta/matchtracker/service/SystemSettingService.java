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
     * 設定値を取得する
     */
    public Optional<String> getValue(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue);
    }

    /**
     * 設定値をint型で取得する（デフォルト値付き）
     */
    public int getIntValue(String key, int defaultValue) {
        return getValue(key)
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
    public int getLotteryDeadlineDaysBefore() {
        return getIntValue(LOTTERY_DEADLINE_DAYS_BEFORE, 0);
    }

    /**
     * 締め切りなしモードかどうか（-1の場合）
     */
    public boolean isNoDeadline() {
        return getLotteryDeadlineDaysBefore() == -1;
    }

    /**
     * 一般枠の最低保証割合を取得する（0〜100、デフォルト30%）
     */
    public int getLotteryNormalReservePercent() {
        return getIntValue(LOTTERY_NORMAL_RESERVE_PERCENT, 30);
    }

    /**
     * 設定値を保存する
     */
    @Transactional
    public SystemSetting setValue(String key, String value, Long updatedBy) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> {
                    SystemSetting s = new SystemSetting();
                    s.setSettingKey(key);
                    return s;
                });
        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);
        return systemSettingRepository.save(setting);
    }

    /**
     * 全設定を取得する
     */
    public List<SystemSetting> getAll() {
        return systemSettingRepository.findAll();
    }
}
