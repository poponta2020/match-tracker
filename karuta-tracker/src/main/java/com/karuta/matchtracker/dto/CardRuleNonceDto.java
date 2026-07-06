package com.karuta.matchtracker.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 札ルール再生成カウンタ(nonce)のDTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardRuleNonceDto {
    private LocalDate date;
    private Integer nonce;
}
