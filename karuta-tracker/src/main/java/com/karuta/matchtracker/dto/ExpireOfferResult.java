package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PracticeParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * expireOfferSuppressed() の結果を保持するDTO。
 * 管理者向け通知データと繰り上げ先参加者の情報を含む。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpireOfferResult {

    /** 管理者向け通知データ */
    private AdminWaitlistNotificationData notificationData;

    /** 繰り上げオファーを送った参加者（null=繰り上げ対象なし） */
    private PracticeParticipant promotedParticipant;
}
