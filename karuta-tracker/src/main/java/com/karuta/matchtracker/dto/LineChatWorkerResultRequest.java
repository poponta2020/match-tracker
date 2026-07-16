package com.karuta.matchtracker.dto;

/**
 * ワーカーの結果報告リクエスト（line-chat-reserve-broadcast タスク3）。
 *
 * @param status       新しい状態（RESERVING/RESERVED/FAILED/MANUAL_REVIEW_REQUIRED/DRY_RUN_SUCCEEDED/CANCELLED）
 * @param errorCode    機械可読エラーコード（TARGET_CHAT_MISMATCH / LINE_AUTH_EXPIRED 等。成功時は null）
 * @param errorMessage エラー詳細（本文・認証情報を含めないこと。任意）
 */
public record LineChatWorkerResultRequest(
        String status,
        String errorCode,
        String errorMessage) {
}
