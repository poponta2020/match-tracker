package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.CreateVenueProxySessionRequest;
import com.karuta.matchtracker.dto.CreateVenueProxySessionResponse;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyException;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会場予約リバースプロキシのエンドポイント。
 *
 * <p>Phase 1 では KADERU のみ有効。Controller は認可・入力受け渡し・例外レスポンス整形に限定し、
 * 会場別 dispatch と HTML/ヘッダ書き換えは {@link VenueReservationProxyService} に委譲する。</p>
 */
@RestController
@RequestMapping("/api/venue-reservation-proxy")
@RequiredArgsConstructor
@Slf4j
public class VenueReservationProxyController {

    private final VenueReservationProxyService venueReservationProxyService;

    @PostMapping("/session")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<CreateVenueProxySessionResponse> createSession(
            @Valid @RequestBody CreateVenueProxySessionRequest request) {
        log.info("POST /api/venue-reservation-proxy/session: venue={} practiceSessionId={} room={} date={} slot={}",
                request.getVenue(), request.getPracticeSessionId(), request.getRoomName(),
                request.getDate(), request.getSlotIndex());
        return ResponseEntity.ok(venueReservationProxyService.createSession(request));
    }

    @GetMapping("/view")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<String> view(@RequestParam String token) {
        return venueReservationProxyService.view(token);
    }

    @RequestMapping("/fetch/**")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<byte[]> fetch(@RequestParam String token, HttpServletRequest request) {
        return venueReservationProxyService.fetch(token, request);
    }

    @ExceptionHandler(VenueReservationProxyException.class)
    public ResponseEntity<VenueReservationProxyErrorResponse> handleVenueReservationProxyException(
            VenueReservationProxyException ex) {
        HttpStatus status = statusFor(ex);
        if (status.is5xxServerError()) {
            log.error("Venue reservation proxy error: code={} venue={} message={}",
                    ex.getErrorCode(), ex.getVenue(), ex.getMessage(), ex);
        } else {
            log.warn("Venue reservation proxy request rejected: code={} venue={} message={}",
                    ex.getErrorCode(), ex.getVenue(), ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(new VenueReservationProxyErrorResponse(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getVenue()));
    }

    private static HttpStatus statusFor(VenueReservationProxyException ex) {
        if (VenueReservationProxyException.SCRIPT_ERROR.equals(ex.getErrorCode())) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    public record VenueReservationProxyErrorResponse(
            String errorCode,
            String message,
            VenueId venue
    ) {
    }
}
