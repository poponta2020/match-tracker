package com.karuta.matchtracker.exception;

/**
 * 権限がない場合にスローされる例外
 *
 * HTTP 403 Forbiddenに対応します。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
