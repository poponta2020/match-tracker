package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * 抽選締め切りに関するヘルパー
 *
 * MONTHLY（北大かるた会）:
 *   締め切り: 対象月の初日から N日前の0時（Nはsystem_settingsで設定可能）
 *   デフォルト(N=0): 対象月の前月末日の0時
 *   N=-1: 締め切りなし（常に締め切り前扱い）
 *
 * SAME_DAY（わすらもち会）:
 *   締め切り: 練習当日12:00
 *   抽選なし（先着順）
 */
@Component
@RequiredArgsConstructor
public class LotteryDeadlineHelper {

    private final SystemSettingService systemSettingService;
    private final OrganizationRepository organizationRepository;

    /**
     * 締め切りなしモードかどうか（MONTHLYタイプのみ有効）
     */
    public boolean isNoDeadline(Long organizationId) {
        return systemSettingService.isNoDeadline(organizationId);
    }

    /**
     * 指定年月の練習に対する締め切り日時を取得する（MONTHLYタイプ用）
     */
    public LocalDateTime getDeadline(int year, int month, Long organizationId) {
        int daysBefore = systemSettingService.getLotteryDeadlineDaysBefore(organizationId);

        if (daysBefore == -1) {
            return null;
        }

        YearMonth targetMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = targetMonth.atDay(1);
        LocalDate deadlineDate = firstDayOfMonth.minusDays(daysBefore);

        if (daysBefore == 0) {
            YearMonth previousMonth = targetMonth.minusMonths(1);
            deadlineDate = previousMonth.atEndOfMonth();
        }

        return deadlineDate.atStartOfDay();
    }

    /**
     * SAME_DAYタイプの締め切り日時を取得する（練習当日12:00）
     */
    public LocalDateTime getSameDayDeadline(LocalDate sessionDate) {
        return sessionDate.atTime(12, 0);
    }

    /**
     * 指定の練習日がSAME_DAYタイプの締め切り前かどうか
     */
    public boolean isBeforeSameDayDeadline(LocalDate sessionDate) {
        return JstDateTimeUtil.now().isBefore(getSameDayDeadline(sessionDate));
    }

    /**
     * 指定年月の練習がまだ締め切り前かどうか（MONTHLYタイプ用）
     * 締め切りなしモードの場合は常に true
     */
    public boolean isBeforeDeadline(int year, int month, Long organizationId) {
        if (isNoDeadline(organizationId)) {
            return true;
        }
        return JstDateTimeUtil.now().isBefore(getDeadline(year, month, organizationId));
    }

    /**
     * 指定年月の練習が締め切り後かどうか
     */
    public boolean isAfterDeadline(int year, int month, Long organizationId) {
        return !isBeforeDeadline(year, month, organizationId);
    }

    /**
     * 指定日の練習が当日かどうか
     */
    public boolean isToday(LocalDate sessionDate) {
        return sessionDate.equals(JstDateTimeUtil.today());
    }

    /**
     * 当日12:00（正午）以降かどうかを判定する。
     * 当日キャンセル補充フローの境界判定に使用。
     */
    public boolean isAfterSameDayNoon(LocalDate sessionDate) {
        return isToday(sessionDate) && !JstDateTimeUtil.now().isBefore(sessionDate.atTime(12, 0));
    }

    /**
     * 指定年月に対して、自動抽選が実行されるべきかどうかを判定する
     * SAME_DAYタイプの団体は抽選なしのため常にfalse
     */
    public boolean shouldLotteryBeExecuted(int year, int month, Long organizationId) {
        if (organizationId != null) {
            Organization org = organizationRepository.findById(organizationId).orElse(null);
            if (org != null && org.getDeadlineType() == DeadlineType.SAME_DAY) {
                return false;
            }
        }
        return isAfterDeadline(year, month, organizationId);
    }

    /**
     * 繰り上げ通知の応答期限を計算する
     * min(通知から24時間, 練習日前日の23:59)
     */
    public LocalDateTime calculateOfferDeadline(LocalDate sessionDate) {
        LocalDateTime twentyFourHoursLater = JstDateTimeUtil.now().plusHours(24);
        LocalDateTime dayBeforePractice = sessionDate.minusDays(1).atTime(23, 59, 59);
        return twentyFourHoursLater.isBefore(dayBeforePractice) ? twentyFourHoursLater : dayBeforePractice;
    }

    /**
     * 団体のDeadlineTypeを取得する
     */
    public DeadlineType getDeadlineType(Long organizationId) {
        if (organizationId == null) return DeadlineType.MONTHLY;
        return organizationRepository.findById(organizationId)
                .map(Organization::getDeadlineType)
                .orElse(DeadlineType.MONTHLY);
    }
}
