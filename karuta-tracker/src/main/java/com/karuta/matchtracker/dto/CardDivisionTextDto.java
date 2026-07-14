package com.karuta.matchtracker.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 札分け確認テキストの取得レスポンス DTO。
 * 当日該当団体のセッションが無い場合は {@code hasSession=false}・{@code text=null}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardDivisionTextDto {
    /** 当日該当団体のセッションが存在するか */
    private boolean hasSession;
    /** 対象日（JST） */
    private LocalDate date;
    /** 団体ID */
    private Long organizationId;
    /** 札分けテキスト全文（セッションが無ければ null。会場名はヘッダに含まれる） */
    private String text;
}
