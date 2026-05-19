package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.IcalCalendarFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * iCalフィード公開エンドポイント
 *
 * Googleカレンダー等の購読クライアントが直接取得する。認証なし、
 * 推測困難な ical_feed_token がアクセス制御の役割を果たす。
 *
 * パスが /ical/... のため WebConfig のインターセプター対象 (/api/**) からは外れる。
 */
@RestController
@RequestMapping("/ical/calendar")
@RequiredArgsConstructor
@Slf4j
public class IcalCalendarFeedController {

    private final IcalCalendarFeedService icalCalendarFeedService;

    @GetMapping(value = "/{token}/org/{orgId}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getOrgFeed(@PathVariable String token, @PathVariable Long orgId) {
        try {
            String ics = icalCalendarFeedService.generateIcsForOrgFeed(token, orgId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                    .body(ics);
        } catch (ResourceNotFoundException ex) {
            log.debug("iCal org feed not found for token/orgId");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/{token}/guest.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getGuestFeed(@PathVariable String token) {
        try {
            String ics = icalCalendarFeedService.generateIcsForGuestFeed(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                    .body(ics);
        } catch (ResourceNotFoundException ex) {
            log.debug("iCal guest feed token not found");
            return ResponseEntity.notFound().build();
        }
    }
}
