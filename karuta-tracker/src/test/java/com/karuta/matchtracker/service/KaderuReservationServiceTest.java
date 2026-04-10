package com.karuta.matchtracker.service;

import com.karuta.matchtracker.service.KaderuReservationService.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KaderuReservationService テスト")
class KaderuReservationServiceTest {

    private KaderuReservationService service;

    @BeforeEach
    void setUp() {
        service = new KaderuReservationService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "kaderuUserId", "testuser");
        ReflectionTestUtils.setField(service, "kaderuPassword", "testpass");
        ReflectionTestUtils.setField(service, "scriptPath", "scripts/room-checker/open-reserve.js");
        ReflectionTestUtils.setField(service, "nodeCommand", "node");
    }

    @Test
    @DisplayName("機能無効時はDISABLEDエラーを返す")
    void disabled_returnsError() {
        ReflectionTestUtils.setField(service, "enabled", false);

        ReservationResult result = service.openReservationPage(
                "すずらん", LocalDate.now().plusDays(1), 2);

        assertFalse(result.success());
        assertEquals("DISABLED", result.errorCode());
    }

    @Test
    @DisplayName("無効な部屋名でINVALID_ROOMエラー")
    void invalidRoom_returnsError() {
        ReservationResult result = service.openReservationPage(
                "存在しない部屋", LocalDate.now().plusDays(1), 2);

        assertFalse(result.success());
        assertEquals("INVALID_ROOM", result.errorCode());
    }

    @Test
    @DisplayName("無効な時間帯でINVALID_SLOTエラー")
    void invalidSlot_returnsError() {
        ReservationResult result = service.openReservationPage(
                "すずらん", LocalDate.now().plusDays(1), 5);

        assertFalse(result.success());
        assertEquals("INVALID_SLOT", result.errorCode());
    }

    @Test
    @DisplayName("過去日付でPAST_DATEエラー")
    void pastDate_returnsError() {
        ReservationResult result = service.openReservationPage(
                "すずらん", LocalDate.of(2020, 1, 1), 2);

        assertFalse(result.success());
        assertEquals("PAST_DATE", result.errorCode());
    }

    @Test
    @DisplayName("認証情報未設定でNO_CREDENTIALSエラー")
    void noCredentials_returnsError() {
        ReflectionTestUtils.setField(service, "kaderuUserId", "");
        ReflectionTestUtils.setField(service, "kaderuPassword", "");

        ReservationResult result = service.openReservationPage(
                "すずらん", LocalDate.now().plusDays(1), 2);

        assertFalse(result.success());
        assertEquals("NO_CREDENTIALS", result.errorCode());
    }

    @Test
    @DisplayName("スクリプト未存在でSCRIPT_NOT_FOUNDエラー")
    void scriptNotFound_returnsError() {
        ReflectionTestUtils.setField(service, "scriptPath", "nonexistent/path/script.js");

        ReservationResult result = service.openReservationPage(
                "すずらん", LocalDate.now().plusDays(1), 2);

        assertFalse(result.success());
        assertEquals("SCRIPT_NOT_FOUND", result.errorCode());
    }

    @Test
    @DisplayName("非かでる部屋のVenue IDでNOT_KADERU_ROOMエラー")
    void nonKaderuVenueId_returnsError() {
        ReservationResult result = service.openReservationPageByVenueId(
                999L, LocalDate.now().plusDays(1), 2);

        assertFalse(result.success());
        assertEquals("NOT_KADERU_ROOM", result.errorCode());
    }

    @Test
    @DisplayName("かでる部屋のVenue IDで正しい部屋名に変換される（無効化時）")
    void kaderuVenueId_disabled_returnsDisabledError() {
        ReflectionTestUtils.setField(service, "enabled", false);

        // Venue ID 3 = すずらん
        ReservationResult result = service.openReservationPageByVenueId(
                3L, LocalDate.now().plusDays(1), 2);

        assertFalse(result.success());
        // openReservationPage に委譲されてDISABLEDが返る
        assertEquals("DISABLED", result.errorCode());
    }

    @Test
    @DisplayName("ReservationResult.success の値確認")
    void reservationResult_success() {
        ReservationResult result = ReservationResult.success("すずらん", "2026-04-15", "夜間 (17:00-21:00)");

        assertTrue(result.success());
        assertNull(result.errorCode());
        assertEquals("すずらん", result.roomName());
        assertEquals("2026-04-15", result.date());
        assertEquals("夜間 (17:00-21:00)", result.timeSlot());
    }

    @Test
    @DisplayName("ReservationResult.error の値確認")
    void reservationResult_error() {
        ReservationResult result = ReservationResult.error("TEST_ERROR", "テストエラー");

        assertFalse(result.success());
        assertEquals("TEST_ERROR", result.errorCode());
        assertEquals("テストエラー", result.message());
        assertNull(result.roomName());
    }

    @Test
    @DisplayName("機能無効時の起動時検証はエラーなく完了する")
    void validateConfiguration_disabled_noError() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertDoesNotThrow(() -> service.validateConfiguration());
    }

    @Test
    @DisplayName("機能有効・スクリプト未存在でも起動時検証は例外を投げない（ログ警告のみ）")
    void validateConfiguration_enabled_scriptNotFound_logsWarning() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "scriptPath", "nonexistent/path/script.js");
        assertDoesNotThrow(() -> service.validateConfiguration());
    }
}
