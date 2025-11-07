package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * エラーレスポンスDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    /**
     * エラーメッセージ
     */
    private String message;

    /**
     * HTTPステータスコード
     */
    private int status;

    /**
     * エラー詳細（バリデーションエラーなど）
     */
    private List<String> details;

    /**
     * タイムスタンプ
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * リクエストパス
     */
    private String path;

    /**
     * 単一メッセージ用のコンストラクタ
     */
    public ErrorResponse(String message, int status, String path) {
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
}
