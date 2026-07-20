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

    /** 空きかどうか（○ のみ。＝オンライン予約可・空き通知の条件） */
    private Boolean available;

    /**
     * 会場拡張が可能かどうか（○ または ●）。
     * ●（要問合せ）は当日など online 予約は不可だが、電話等で手動確保すれば拡張できるため true。
     */
    private Boolean expandable;

    /** 拡張後のVenue ID */
    private Long expandedVenueId;

    /** 拡張後の会場名 */
    private String expandedVenueName;

    /** 拡張後の定員 */
    private Integer expandedCapacity;

    /**
     * 手動拡張会場かどうか（東🌸(6) のみ true）。
     * true の場合、FE は隣室 status チップと空き依存ゲートを外し、
     * 予約済み前提で会場拡張ボタンを常時表示する（{@code AdjacentRoomConfig.isManualExpansionVenue}）。
     */
    private Boolean manualExpansion;
}
