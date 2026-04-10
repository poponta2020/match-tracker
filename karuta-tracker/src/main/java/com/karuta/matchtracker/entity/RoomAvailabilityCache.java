package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * かでる2・7部屋空き状況キャッシュエンティティ
 */
@Entity
@Table(name = "room_availability_cache", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_name", "target_date", "time_slot"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAvailabilityCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** かでるサイトでの部屋名（「すずらん」等） */
    @Column(name = "room_name", nullable = false, length = 50)
    private String roomName;

    /** 対象日付 */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** 時間帯（「evening」= 夜間17-21） */
    @Column(name = "time_slot", nullable = false, length = 20)
    private String timeSlot;

    /** 空き状態（○/×/-/●/休館） */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    /** スクレイピング実行日時 */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
