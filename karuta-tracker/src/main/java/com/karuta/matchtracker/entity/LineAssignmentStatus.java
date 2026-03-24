package com.karuta.matchtracker.entity;

/**
 * LINEチャネル割り当てのステータス
 */
public enum LineAssignmentStatus {
    /** 割り当て済み・友だち追加待ち */
    PENDING,
    /** 友だち追加済み・連携完了 */
    LINKED,
    /** ユーザーによる解除またはブロック */
    UNLINKED,
    /** システムによる回収 */
    RECLAIMED
}
