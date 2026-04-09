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

import java.time.LocalDate;
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
    @DisplayName("かでる和室でないVenueはnullを返す")
    void getAdjacentRoomAvailability_nonKaderu() {
        assertNull(adjacentRoomService.getAdjacentRoomAvailability(1L, LocalDate.now()));
    }

    @Test
    @DisplayName("会場拡張 - 正常系")
    void expandVenue_success() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(3L).capacity(14).build();
        Venue expandedVenue = Venue.builder().id(7L).name("すずらん・はまなす").capacity(24).build();

        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(venueRepository.findById(7L)).thenReturn(Optional.of(expandedVenue));
        when(practiceSessionRepository.save(any())).thenReturn(session);

        adjacentRoomService.expandVenue(1L);

        assertEquals(7L, session.getVenueId());
        assertEquals(24, session.getCapacity());
        verify(practiceSessionRepository).save(session);
    }

    @Test
    @DisplayName("会場拡張 - かでる和室でないVenueはエラー")
    void expandVenue_nonKaderu() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).venueId(1L).capacity(20).build();
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> adjacentRoomService.expandVenue(1L));
    }

    @Test
    @DisplayName("会場拡張 - 存在しないセッションはエラー")
    void expandVenue_sessionNotFound() {
        when(practiceSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adjacentRoomService.expandVenue(999L));
    }
}
