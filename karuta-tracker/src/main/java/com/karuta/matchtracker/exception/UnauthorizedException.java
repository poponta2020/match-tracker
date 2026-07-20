package com.karuta.matchtracker.exception;

/**
 * 認証されていない場合にスローされる例外（auth-tokenization）
 *
 * HTTP 401 Unauthorized に対応します。
 *
 * 「あなたが誰か分からない」場合に使います。
 * 本人は分かっているが操作が許されていない場合は {@link ForbiddenException}（403）を使います。
 * フロントエンドは 401 を受けると localStorage をクリアして /login へ遷移します。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
