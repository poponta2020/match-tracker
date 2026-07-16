package com.karuta.matchtracker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 予約状況一覧＋要確認アラート状態（AC-9・管理画面の予約状況セクション）。
 */
@Data
@Builder
public class LineChatReservationsDto {

    private List<LineChatReservationDto> reservations;
    /** MANUAL_REVIEW_REQUIRED の予約が含まれるか＝要確認アラート */
    private boolean hasManualReviewRequired;
}
