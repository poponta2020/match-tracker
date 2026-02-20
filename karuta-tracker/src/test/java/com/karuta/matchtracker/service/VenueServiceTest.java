package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.VenueCreateRequest;
import com.karuta.matchtracker.dto.VenueDto;
import com.karuta.matchtracker.dto.VenueUpdateRequest;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * VenueServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueService 単体テスト")
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VenueMatchScheduleRepository scheduleRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private VenueService venueService;

    private Venue testVenue;
    private VenueMatchSchedule testSchedule;
    private VenueCreateRequest createRequest;
    private VenueUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        testVenue = Venue.builder()
                .id(1L)
                .name("東京会場")
                .defaultMatchCount(5)
                .build();

        testSchedule = VenueMatchSchedule.builder()
                .id(1L)
                .venueId(1L)
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        VenueCreateRequest.MatchScheduleRequest scheduleRequest = VenueCreateRequest.MatchScheduleRequest.builder()
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        createRequest = VenueCreateRequest.builder()
                .name("大阪会場")
                .defaultMatchCount(6)
                .schedules(List.of(scheduleRequest))
                .build();

        VenueUpdateRequest.MatchScheduleRequest updateScheduleRequest = VenueUpdateRequest.MatchScheduleRequest.builder()
                .matchNumber(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 30))
                .build();

        updateRequest = VenueUpdateRequest.builder()
                .name("東京新会場")
                .defaultMatchCount(7)
                .schedules(List.of(updateScheduleRequest))
                .build();
    }

    // ===== getAllVenues テスト =====

    @Test
    @DisplayName("全会場をスケジュール付きで取得できる")
    void testGetAllVenues_ReturnsAllWithSchedules() {
        // Given
        when(venueRepository.findAll()).thenReturn(List.of(testVenue));
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(1L)).thenReturn(List.of(testSchedule));

        // When
        List<VenueDto> result = venueService.getAllVenues();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("東京会場");
        assertThat(result.get(0).getSchedules()).hasSize(1);
        assertThat(result.get(0).getSchedules().get(0).getMatchNumber()).isEqualTo(1);
        verify(venueRepository).findAll();
        verify(scheduleRepository).findByVenueIdOrderByMatchNumberAsc(1L);
    }

    @Test
    @DisplayName("会場がない場合は空のリストを返す")
    void testGetAllVenues_EmptyList_ReturnsEmpty() {
        // Given
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<VenueDto> result = venueService.getAllVenues();

        // Then
        assertThat(result).isEmpty();
        verify(venueRepository).findAll();
    }

    // ===== getVenueById テスト =====

    @Test
    @DisplayName("IDで会場をスケジュール付きで取得できる")
    void testGetVenueById_ExistingId_ReturnsVenueWithSchedules() {
        // Given
        when(venueRepository.findById(1L)).thenReturn(Optional.of(testVenue));
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(1L)).thenReturn(List.of(testSchedule));

        // When
        VenueDto result = venueService.getVenueById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("東京会場");
        assertThat(result.getDefaultMatchCount()).isEqualTo(5);
        assertThat(result.getSchedules()).hasSize(1);
        verify(venueRepository).findById(1L);
    }

    @Test
    @DisplayName("存在しないIDでResourceNotFoundExceptionが発生")
    void testGetVenueById_NonExistingId_ThrowsResourceNotFound() {
        // Given
        when(venueRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> venueService.getVenueById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Venue")
                .hasMessageContaining("999");
        verify(venueRepository).findById(999L);
    }

    // ===== createVenue テスト =====

    @Test
    @DisplayName("会場を正常に作成できる")
    void testCreateVenue_ValidRequest_ReturnsCreatedVenue() {
        // Given
        Venue savedVenue = Venue.builder()
                .id(2L)
                .name("大阪会場")
                .defaultMatchCount(6)
                .build();

        when(venueRepository.existsByName("大阪会場")).thenReturn(false);
        when(venueRepository.save(any(Venue.class))).thenReturn(savedVenue);
        when(scheduleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(2L)).thenReturn(Collections.emptyList());

        // When
        VenueDto result = venueService.createVenue(createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("大阪会場");
        assertThat(result.getDefaultMatchCount()).isEqualTo(6);
        verify(venueRepository).existsByName("大阪会場");
        verify(venueRepository).save(any(Venue.class));
        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("スケジュール付きで会場を作成できる")
    void testCreateVenue_WithSchedules_SchedulesSaved() {
        // Given
        Venue savedVenue = Venue.builder()
                .id(2L)
                .name("大阪会場")
                .defaultMatchCount(6)
                .build();

        VenueMatchSchedule savedSchedule = VenueMatchSchedule.builder()
                .id(1L)
                .venueId(2L)
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        when(venueRepository.existsByName("大阪会場")).thenReturn(false);
        when(venueRepository.save(any(Venue.class))).thenReturn(savedVenue);
        when(scheduleRepository.saveAll(anyList())).thenReturn(List.of(savedSchedule));
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(2L)).thenReturn(List.of(savedSchedule));

        // When
        VenueDto result = venueService.createVenue(createRequest);

        // Then
        assertThat(result.getSchedules()).hasSize(1);
        assertThat(result.getSchedules().get(0).getMatchNumber()).isEqualTo(1);
        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("会場名が重複している場合はDuplicateResourceExceptionが発生")
    void testCreateVenue_DuplicateName_ThrowsDuplicateResource() {
        // Given
        when(venueRepository.existsByName("大阪会場")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> venueService.createVenue(createRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("大阪会場");
        verify(venueRepository).existsByName("大阪会場");
        verify(venueRepository, never()).save(any(Venue.class));
    }

    // ===== updateVenue テスト =====

    @Test
    @DisplayName("会場を正常に更新できる")
    void testUpdateVenue_ValidRequest_ReturnsUpdatedVenue() {
        // Given
        Venue updatedVenue = Venue.builder()
                .id(1L)
                .name("東京新会場")
                .defaultMatchCount(7)
                .build();

        when(venueRepository.findById(1L)).thenReturn(Optional.of(testVenue));
        when(venueRepository.findByName("東京新会場")).thenReturn(Optional.empty());
        when(venueRepository.save(any(Venue.class))).thenReturn(updatedVenue);
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(1L)).thenReturn(Collections.emptyList());

        // When
        VenueDto result = venueService.updateVenue(1L, updateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("東京新会場");
        assertThat(result.getDefaultMatchCount()).isEqualTo(7);
        verify(scheduleRepository).deleteByVenueId(1L);
        verify(entityManager).flush();
        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("更新時にスケジュールが置換される")
    void testUpdateVenue_SchedulesReplaced_OldDeleted() {
        // Given
        when(venueRepository.findById(1L)).thenReturn(Optional.of(testVenue));
        when(venueRepository.findByName("東京新会場")).thenReturn(Optional.empty());
        when(venueRepository.save(any(Venue.class))).thenReturn(testVenue);
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(1L)).thenReturn(Collections.emptyList());

        // When
        venueService.updateVenue(1L, updateRequest);

        // Then
        verify(scheduleRepository).deleteByVenueId(1L);
        verify(entityManager).flush();
        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("自身と同じ名前での更新はエラーにならない")
    void testUpdateVenue_SameName_NoError() {
        // Given
        VenueUpdateRequest sameNameRequest = VenueUpdateRequest.builder()
                .name("東京会場")  // 現在と同じ名前
                .defaultMatchCount(7)
                .schedules(List.of(VenueUpdateRequest.MatchScheduleRequest.builder()
                        .matchNumber(1)
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(11, 30))
                        .build()))
                .build();

        when(venueRepository.findById(1L)).thenReturn(Optional.of(testVenue));
        when(venueRepository.findByName("東京会場")).thenReturn(Optional.of(testVenue));  // 自分自身が返る
        when(venueRepository.save(any(Venue.class))).thenReturn(testVenue);
        when(scheduleRepository.findByVenueIdOrderByMatchNumberAsc(1L)).thenReturn(Collections.emptyList());

        // When
        VenueDto result = venueService.updateVenue(1L, sameNameRequest);

        // Then
        assertThat(result).isNotNull();
        verify(venueRepository).save(any(Venue.class));
    }

    @Test
    @DisplayName("存在しないIDの更新でResourceNotFoundExceptionが発生")
    void testUpdateVenue_NonExistingId_ThrowsResourceNotFound() {
        // Given
        when(venueRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> venueService.updateVenue(999L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Venue")
                .hasMessageContaining("999");
        verify(venueRepository).findById(999L);
        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    @DisplayName("他の会場と名前が重複する場合はDuplicateResourceExceptionが発生")
    void testUpdateVenue_DuplicateName_ThrowsDuplicateResource() {
        // Given
        Venue anotherVenue = Venue.builder()
                .id(2L)
                .name("東京新会場")
                .defaultMatchCount(5)
                .build();

        when(venueRepository.findById(1L)).thenReturn(Optional.of(testVenue));
        when(venueRepository.findByName("東京新会場")).thenReturn(Optional.of(anotherVenue));

        // When & Then
        assertThatThrownBy(() -> venueService.updateVenue(1L, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("東京新会場");
        verify(venueRepository, never()).save(any(Venue.class));
    }

    // ===== deleteVenue テスト =====

    @Test
    @DisplayName("会場とスケジュールを正常に削除できる")
    void testDeleteVenue_ExistingId_DeletesVenueAndSchedules() {
        // Given
        when(venueRepository.findById(1L)).thenReturn(Optional.of(testVenue));

        // When
        venueService.deleteVenue(1L);

        // Then
        verify(scheduleRepository).deleteByVenueId(1L);
        verify(entityManager).flush();
        verify(venueRepository).delete(testVenue);
    }

    @Test
    @DisplayName("存在しないIDの削除でResourceNotFoundExceptionが発生")
    void testDeleteVenue_NonExistingId_ThrowsResourceNotFound() {
        // Given
        when(venueRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> venueService.deleteVenue(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Venue")
                .hasMessageContaining("999");
        verify(venueRepository).findById(999L);
        verify(venueRepository, never()).delete(any(Venue.class));
    }
}
