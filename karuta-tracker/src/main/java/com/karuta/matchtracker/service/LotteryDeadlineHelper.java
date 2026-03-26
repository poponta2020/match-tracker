package com.karuta.matchtracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * 抽選締め切りに関するヘルパー
 *
 * 締め切り: 対象月の初日から N日前の0時（Nはsystem_settingsで設定可能）
 * デフォルト(N=0): 対象月の前月末日の0時
 * 例: N=3, 4月の練習 → 3月29日 00:00:00
 */
@Component
@RequiredArgsConstructor
public class LotteryDeadlineHelper {

    private final SystemSettingService systemSettingService;

    /**
     * 指定年月の練習に対する締め切り日時を取得する
     *
     * @param year  対象年
     * @param month 対象月
     * @return 締め切り日時
     */
    public LocalDateTime getDeadline(int year, int month) {
        int daysBefore = systemSettingService.getLotteryDeadlineDaysBefore();
        YearMonth targetMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = targetMonth.atDay(1);
        LocalDate deadlineDate = firstDayOfMonth.minusDays(daysBefore);

        // daysBefore=0の場合は前月末日の0時（従来通りの動作）
        if (daysBefore == 0) {
            YearMonth previousMonth = targetMonth.minusMonths(1);
            deadlineDate = previousMonth.atEndOfMonth();
        }

        return deadlineDate.atStartOfDay();
    }

    /**
     * 指定年月の練習がまだ締め切り前かどうか
     */
    public boolean isBeforeDeadline(int year, int month) {
        return LocalDateTime.now().isBefore(getDeadline(year, month));
    }

    /**
     * 指定年月の練習が締め切り後かどうか
     */
    public boolean isAfterDeadline(int year, int month) {
        return !isBeforeDeadline(year, month);
    }

    /**
     * 指定日の練習が当日かどうか
     */
    public boolean isToday(LocalDate sessionDate) {
        return sessionDate.equals(LocalDate.now());
    }

    /**
     * 指定年月に対して、自動抽選が実行されるべきかどうかを判定する
     * （締め切りを過ぎている場合にtrue）
     */
    public boolean shouldLotteryBeExecuted(int year, int month) {
        return isAfterDeadline(year, month);
    }

    /**
     * 繰り上げ通知の応答期限を計算する
     * min(通知から24時間, 練習日前日の23:59)
     */
    public LocalDateTime calculateOfferDeadline(LocalDate sessionDate) {
        LocalDateTime twentyFourHoursLater = LocalDateTime.now().plusHours(24);
        LocalDateTime dayBeforePractice = sessionDate.minusDays(1).atTime(23, 59, 59);
        return twentyFourHoursLater.isBefore(dayBeforePractice) ? twentyFourHoursLater : dayBeforePractice;
    }
}
