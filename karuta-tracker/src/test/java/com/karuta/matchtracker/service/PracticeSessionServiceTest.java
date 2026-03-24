package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PracticeSessionServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeSessionService 単体テスト")
class PracticeSessionServiceTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @InjectMocks
    private PracticeSessionService practiceSessionService;

    private PracticeSession testSession;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        testSession = PracticeSession.builder()
                .id(1L)
                .sessionDate(today)
                .totalMatches(10)
                .build();
    }

    @Test
    @DisplayName("全ての練習日を取得できる")
    void testFindAllSessions() {
        // Given
        PracticeSession session2 = PracticeSession.builder()
                .id(2L)
                .sessionDate(today.minusDays(1))
                .totalMatches(8)
                .build();
        when(practiceSessionRepository.findAllOrderBySessionDateDesc())
                .thenReturn(List.of(testSession, session2));

        // When
        List<PracticeSessionDto> result = practiceSessionService.findAllSessions();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).findAllOrderBySessionDateDesc();
    }

    @Test
    @DisplayName("IDで練習日を取得できる")
    void testFindById() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        // When
        PracticeSessionDto result = practiceSessionService.findById(1L);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSessionDate()).isEqualTo(today);
        assertThat(result.getTotalMatches()).isEqualTo(10);
        verify(practiceSessionRepository).findById(1L);
    }

    @Test
    @DisplayName("存在しないIDで練習日を取得するとResourceNotFoundExceptionが発生")
    void testFindByIdNotFound() {
        // Given
        when(practiceSessionRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining("999");
        verify(practiceSessionRepository).findById(999L);
    }

    @Test
    @DisplayName("日付で練習日を取得できる")
    void testFindByDate() {
        // Given
        when(practiceSessionRepository.findBySessionDate(today))
                .thenReturn(Optional.of(testSession));

        // When
        PracticeSessionDto result = practiceSessionService.findByDate(today);

        // Then
        assertThat(result.getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).findBySessionDate(today);
    }

    @Test
    @DisplayName("期間内の練習日を取得できる")
    void testFindSessionsInRange() {
        // Given
        LocalDate startDate = today.minusDays(7);
        LocalDate endDate = today;
        when(practiceSessionRepository.findByDateRange(startDate, endDate))
                .thenReturn(List.of(testSession));

        // When
        List<PracticeSessionDto> result = practiceSessionService.findSessionsInRange(startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        verify(practiceSessionRepository).findByDateRange(startDate, endDate);
    }

    @Test
    @DisplayName("特定の年月の練習日を取得できる")
    void testFindSessionsByYearMonth() {
        // Given
        int year = today.getYear();
        int month = today.getMonthValue();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(testSession));

        // When
        List<PracticeSessionDto> result = practiceSessionService.findSessionsByYearMonth(year, month);

        // Then
        assertThat(result).hasSize(1);
        verify(practiceSessionRepository).findByYearAndMonth(year, month);
    }

    @Test
    @DisplayName("指定日以降の練習日を取得できる")
    void testFindUpcomingSessions() {
        // Given
        when(practiceSessionRepository.findUpcomingSessions(today))
                .thenReturn(List.of(testSession));

        // When
        List<PracticeSessionDto> result = practiceSessionService.findUpcomingSessions(today);

        // Then
        assertThat(result).hasSize(1);
        verify(practiceSessionRepository).findUpcomingSessions(today);
    }

    @Test
    @DisplayName("日付が練習日として登録されているか確認できる")
    void testExistsSessionOnDate() {
        // Given
        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);

        // When
        boolean exists = practiceSessionService.existsSessionOnDate(today);

        // Then
        assertThat(exists).isTrue();
        verify(practiceSessionRepository).existsBySessionDate(today);
    }

    @Test
    @DisplayName("練習日を新規登録できる")
    void testCreateSession() {
        // Given
        PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(12)
                .build();
        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(false);
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);

        // When
        PracticeSessionDto result = practiceSessionService.createSession(request, 1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).existsBySessionDate(today);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("既存の日付で練習日を登録するとDuplicateResourceExceptionが発生")
    void testCreateSessionDuplicateDate() {
        // Given
        PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(12)
                .build();
        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.createSession(request, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining(today.toString());
        verify(practiceSessionRepository).existsBySessionDate(today);
        verify(practiceSessionRepository, never()).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("総試合数を更新できる")
    void testUpdateTotalMatches() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);

        // When
        PracticeSessionDto result = practiceSessionService.updateTotalMatches(1L, 15);

        // Then
        assertThat(result).isNotNull();
        verify(practiceSessionRepository).findById(1L);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("総試合数を負の値に更新するとIllegalArgumentExceptionが発生")
    void testUpdateTotalMatchesNegative() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.updateTotalMatches(1L, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
        verify(practiceSessionRepository).findById(1L);
        verify(practiceSessionRepository, never()).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("練習日を削除できる")
    void testDeleteSession() {
        // Given
        when(practiceSessionRepository.existsById(1L)).thenReturn(true);

        // When
        practiceSessionService.deleteSession(1L);

        // Then
        verify(practiceSessionRepository).existsById(1L);
        verify(practiceSessionRepository).deleteById(1L);
    }

    @Test
    @DisplayName("存在しない練習日を削除するとResourceNotFoundExceptionが発生")
    void testDeleteSessionNotFound() {
        // Given
        when(practiceSessionRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.deleteSession(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining("999");
        verify(practiceSessionRepository, never()).deleteById(any());
    }
}
