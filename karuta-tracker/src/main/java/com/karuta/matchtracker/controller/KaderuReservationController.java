package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.KaderuReservationService;
import com.karuta.matchtracker.service.KaderuReservationService.ReservationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * かでる2・7 予約画面遷移コントローラー
 */
@RestController
@RequestMapping("/api/kaderu")
@RequiredArgsConstructor
@Slf4j
public class KaderuReservationController {

    private final KaderuReservationService kaderuReservationService;

    /**
     * 予約画面（申込トレイ）をブラウザで開く
     *
     * @param roomName  部屋名（すずらん/はまなす/あかなら/えぞまつ）
     * @param date      予約日 (YYYY-MM-DD)
     * @param slotIndex 時間帯（0=午前, 1=午後, 2=夜間）
     */
    @PostMapping("/open-reserve")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<ReservationResult> openReserve(
            @RequestParam String roomName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "2") int slotIndex) {

        log.info("POST /api/kaderu/open-reserve: room={}, date={}, slot={}", roomName, date, slotIndex);
        ReservationResult result = kaderuReservationService.openReservationPage(roomName, date, slotIndex);

        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Venue IDを指定して予約画面を開く（隣室予約用）
     *
     * @param venueId   かでる会場のVenue ID
     * @param date      予約日 (YYYY-MM-DD)
     * @param slotIndex 時間帯（0=午前, 1=午後, 2=夜間）
     */
    @PostMapping("/open-reserve-by-venue")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<ReservationResult> openReserveByVenue(
            @RequestParam Long venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "2") int slotIndex) {

        log.info("POST /api/kaderu/open-reserve-by-venue: venueId={}, date={}, slot={}", venueId, date, slotIndex);
        ReservationResult result = kaderuReservationService.openReservationPageByVenueId(venueId, date, slotIndex);

        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
