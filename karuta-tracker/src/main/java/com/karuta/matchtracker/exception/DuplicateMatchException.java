package com.karuta.matchtracker.exception;

import lombok.Getter;

/**
 * 試合結果が重複している場合の例外
 * 既存の試合IDを含む
 */
@Getter
public class DuplicateMatchException extends RuntimeException {

    private final Long existingMatchId;

    public DuplicateMatchException(String message, Long existingMatchId) {
        super(message);
        this.existingMatchId = existingMatchId;
    }
}
