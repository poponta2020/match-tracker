package com.karuta.matchtracker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 伝助への書き込み状況DTO
 */
@Data
@Builder
public class DensukeWriteStatusDto {

    /** 最終書き込み試行日時 */
    private LocalDateTime lastAttemptAt;

    /** 最終書き込み成功日時（全エラーなし） */
    private LocalDateTime lastSuccessAt;

    /** 直近のエラーメッセージ一覧 */
    private List<String> errors;

    /** 書き込み待ち（dirty=true）の参加者数 */
    private int pendingCount;
}
