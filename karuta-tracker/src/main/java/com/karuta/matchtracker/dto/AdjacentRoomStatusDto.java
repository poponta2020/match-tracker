package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 隣室空き状況DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjacentRoomStatusDto {

    /** 隣室名（かでるサイト上の名前） */
    private String adjacentRoomName;

    /** 空き状態（○/×/-/●/休館/不明） */
    private String status;

    /** 空きかどうか */
    private Boolean available;

    /** 拡張後のVenue ID */
    private Long expandedVenueId;

    /** 拡張後の会場名 */
    private String expandedVenueName;

    /** 拡張後の定員 */
    private Integer expandedCapacity;
}
