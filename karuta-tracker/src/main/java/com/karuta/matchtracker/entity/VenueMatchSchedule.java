package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 会場の試合時間割エンティティ
 */
@Entity
@Table(name = "venue_match_schedules", uniqueConstraints = {
    @UniqueConstraint(name = "uk_venue_match", columnNames = {"venue_id", "match_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueMatchSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
