package com.karuta.matchtracker.entity;

/**
 * LINEチャネルのステータス
 */
public enum LineChannelStatus {
    /** 未割り当て。新規ユーザーに割り当て可能 */
    AVAILABLE,
    /** ユーザーに割り当て済みだが、友だち追加未完了 */
    ASSIGNED,
    /** ユーザーが友だち追加済み。通知送信可能 */
    LINKED,
    /** 無効化。管理者が手動で無効にしたチャネル */
    DISABLED
}
