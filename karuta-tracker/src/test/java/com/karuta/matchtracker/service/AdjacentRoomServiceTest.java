package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.AdjacentRoomStatusDto;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.RoomAvailabilityCache;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.RoomAvailabilityCacheRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdjacentRoomService テスト")
class AdjacentRoomServiceTest {

    @Mock
    private RoomAvailabilityCacheRepository roomAvailabilityCacheRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private WaitlistPromotionService waitlistPromotionService;

    @InjectMocks
    private AdjacentRoomService adjacentRoomService;

    @Test
    @DisplayName("かでる和室の隣室空き状況を取得 - 空きあり")
    void getAdjacentRoomAvailability_available() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        RoomAvailabilityCache cache = RoomAvailabilityCache.builder()
                .roomName("はまなす").targetDate(date).timeSlot("evening").status("○").build();
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenReturn(Optional.of(cache));

        AdjacentRoomStatusDto result = adjacentRoomService.getAdjacentRoomAvailability(3L, date);

        assertNotNull(result);
        assertEquals("はまなす", result.getAdjacentRoomName());
        assertEquals("○", result.getStatus());
        assertTrue(result.getAvailable());
        assertEquals(7L, result.getExpandedVenueId());
        assertEquals("すずらん・はまなす", result.getExpandedVenueName());
        assertEquals(24, result.getExpandedCapacity());
    }

    @Test
    @DisplayName("かでる和室の隣室空き状況を取得 - 予約済み")
    void getAdjacentRoomAvailability_booked() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        RoomAvailabilityCache cache = RoomAvailabilityCache.builder()
                .roomName("はまなす").targetDate(date).timeSlot("evening").status("×").build();
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenReturn(Optional.of(cache));

        AdjacentRoomStatusDto result = adjacentRoomService.getAdjacentRoomAvailability(3L, date);

        assertNotNull(result);
        assertFalse(result.getAvailable());
    }

    @Test
    @DisplayName("キャッシュがない場合は「不明」")
    void getAdjacentRoomAvailability_noCache() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenReturn(Optional.empty());

        AdjacentRoomStatusDto result = adjacentRoomService.getAdjacentRoomAvailability(3L, date);

        assertNotNull(result);
        assertEquals("不明", result.getStatus());
        assertFalse(result.getAvailable());
    }

    @Test
    @DisplayName("DB障害時はステータス「不明」でフォールバックする")
    void getAdjacentRoomAvailability_dbError() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenThrow(new DataAccessResourceFailureException("relation \"room_availability_cache\" does not exist"));

        AdjacentRoomStatusDto result = adjacentRoomService.getAdjacentRoomAvailability(3L, date);

        assertNotNull(result);
        assertEquals("不明", result.getStatus());
        assertFalse(result.getAvailable());
        assertEquals("はまなす", result.getAdjacentRoomName());
    }

    @Test
    @DisplayName("かでる和室でないVenueはnullを返す")
    void getAdjacentRoomAvailability_nonKaderu() {
        assertNull(adjacentRoomService.getAdjacentRoomAvailability(1L, LocalDate.now()));
    }

    @Test
    @DisplayName("予約確認 - 正常系")
    void confirmReservation_success() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(3L).capacity(14).build();
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(practiceSessionRepository.save(any())).thenReturn(session);

        adjacentRoomService.confirmReservation(1L, 100L);

        assertNotNull(session.getReservationConfirmedAt());
        assertEquals(100L, session.getUpdatedBy());
        verify(practiceSessionRepository).save(session);
    }

    @Test
    @DisplayName("予約確認 - かでる和室でないVenueはエラー")
    void confirmReservation_nonKaderu() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(1L).capacity(20).build();
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> adjacentRoomService.confirmReservation(1L, 100L));
    }

    @Test
    @DisplayName("会場拡張 - 正常系（予約確認済み・WaitlistPromotionServiceに昇格処理を委譲）")
    void expandVenue_success() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(3L).capacity(14).sessionDate(date)
                .reservationConfirmedAt(LocalDateTime.of(2026, 4, 12, 10, 0)).build();
        Venue expandedVenue = Venue.builder().id(7L).name("すずらん・はまなす").capacity(24).build();
        RoomAvailabilityCache cache = RoomAvailabilityCache.builder()
                .roomName("はまなす").targetDate(date).timeSlot("evening").status("○").build();

        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenReturn(Optional.of(cache));
        when(venueRepository.findById(7L)).thenReturn(Optional.of(expandedVenue));
        when(practiceSessionRepository.save(any())).thenReturn(session);

        adjacentRoomService.expandVenue(1L, 100L);

        assertEquals(7L, session.getVenueId());
        assertEquals(24, session.getCapacity());
        assertEquals(100L, session.getUpdatedBy());
        assertNull(session.getReservationConfirmedAt()); // 拡張後にクリアされる
        verify(practiceSessionRepository).save(session);
        verify(waitlistPromotionService).promoteWaitlistedAfterCapacityIncrease(1L);
    }

    @Test
    @DisplayName("会場拡張 - 予約未確認の場合はエラー")
    void expandVenue_reservationNotConfirmed() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(3L).capacity(14).sessionDate(date)
                .reservationConfirmedAt(null).build();
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adjacentRoomService.expandVenue(1L, 100L));
        assertEquals("隣室の予約が確認されていません。先に予約を完了してください", ex.getMessage());
        verify(practiceSessionRepository, never()).save(any());
        verify(waitlistPromotionService, never()).promoteWaitlistedAfterCapacityIncrease(anyLong());
    }

    @Test
    @DisplayName("会場拡張 - かでる和室でないVenueはエラー")
    void expandVenue_nonKaderu() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(1L).capacity(20).build();
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> adjacentRoomService.expandVenue(1L, 100L));
        verify(waitlistPromotionService, never()).promoteWaitlistedAfterCapacityIncrease(anyLong());
    }

    @Test
    @DisplayName("会場拡張 - 存在しないセッションはエラー")
    void expandVenue_sessionNotFound() {
        when(practiceSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adjacentRoomService.expandVenue(999L, 100L));
        verify(waitlistPromotionService, never()).promoteWaitlistedAfterCapacityIncrease(anyLong());
    }

    @Test
    @DisplayName("会場拡張 - 隣室が予約済みの場合はエラー")
    void expandVenue_adjacentRoomNotAvailable() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(3L).capacity(14).sessionDate(date)
                .reservationConfirmedAt(LocalDateTime.of(2026, 4, 12, 10, 0)).build();
        RoomAvailabilityCache cache = RoomAvailabilityCache.builder()
                .roomName("はまなす").targetDate(date).timeSlot("evening").status("×").build();

        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenReturn(Optional.of(cache));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adjacentRoomService.expandVenue(1L, 100L));
        assertEquals("隣室が空いていないため、会場を拡張できません", ex.getMessage());
        verify(practiceSessionRepository, never()).save(any());
        verify(waitlistPromotionService, never()).promoteWaitlistedAfterCapacityIncrease(anyLong());
    }

    @Test
    @DisplayName("会場拡張 - 隣室が不明の場合はエラー")
    void expandVenue_adjacentRoomUnknown() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(3L).capacity(14).sessionDate(date)
                .reservationConfirmedAt(LocalDateTime.of(2026, 4, 12, 10, 0)).build();

        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot("はまなす", date, "evening"))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adjacentRoomService.expandVenue(1L, 100L));
        assertEquals("隣室が空いていないため、会場を拡張できません", ex.getMessage());
        verify(practiceSessionRepository, never()).save(any());
        verify(waitlistPromotionService, never()).promoteWaitlistedAfterCapacityIncrease(anyLong());
    }
}
