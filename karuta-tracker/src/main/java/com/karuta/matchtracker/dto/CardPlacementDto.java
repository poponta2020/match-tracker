package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.MatchCardPlacement;
import lombok.*;

/**
 * 取り札配置DTO。takenBy: SELF/OPPONENT, field: ENEMY/OWN, side: LEFT/RIGHT, tier: TOP/MIDDLE/BOTTOM。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardPlacementDto {
    private Integer cardNo;
    private String takenBy;
    private String field;
    private String side;
    private String tier;

    public static CardPlacementDto fromEntity(MatchCardPlacement e) {
        return CardPlacementDto.builder()
                .cardNo(e.getCardNo())
                .takenBy(e.getTakenBy())
                .field(e.getField())
                .side(e.getSide())
                .tier(e.getTier())
                .build();
    }
}
