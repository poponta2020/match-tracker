package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.VenueCreateRequest;
import com.karuta.matchtracker.dto.VenueDto;
import com.karuta.matchtracker.dto.VenueUpdateRequest;
import com.karuta.matchtracker.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会場管理コントローラー
 */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class VenueController {

    private final VenueService venueService;

    /**
     * 全会場を取得
     *
     * @return 会場リスト
     */
    @GetMapping
    public ResponseEntity<List<VenueDto>> getAllVenues() {
        List<VenueDto> venues = venueService.getAllVenues();
        return ResponseEntity.ok(venues);
    }

    /**
     * 会場IDで会場を取得
     *
     * @param id 会場ID
     * @return 会場DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<VenueDto> getVenueById(@PathVariable Long id) {
        VenueDto venue = venueService.getVenueById(id);
        return ResponseEntity.ok(venue);
    }

    /**
     * 会場を新規作成
     *
     * @param request 会場作成リクエスト
     * @return 作成された会場DTO
     */
    @PostMapping
    public ResponseEntity<VenueDto> createVenue(@Valid @RequestBody VenueCreateRequest request) {
        VenueDto venue = venueService.createVenue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(venue);
    }

    /**
     * 会場を更新
     *
     * @param id 会場ID
     * @param request 会場更新リクエスト
     * @return 更新された会場DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<VenueDto> updateVenue(
            @PathVariable Long id,
            @Valid @RequestBody VenueUpdateRequest request) {
        VenueDto venue = venueService.updateVenue(id, request);
        return ResponseEntity.ok(venue);
    }

    /**
     * 会場を削除
     *
     * @param id 会場ID
     * @return レスポンスなし
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVenue(@PathVariable Long id) {
        venueService.deleteVenue(id);
        return ResponseEntity.noContent().build();
    }
}
