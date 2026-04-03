package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.dto.PracticeSessionUpdateRequest;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * PracticeSessionServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeSessionService 単体テスト")
class PracticeSessionServiceTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VenueMatchScheduleRepository venueMatchScheduleRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private DensukeUrlRepository densukeUrlRepository;

    @Mock
    private DensukeSyncService densukeSyncService;

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
    @DisplayName("IDで練習日を取得できる")
    void testFindById() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceParticipantRepository.findBySessionId(1L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

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
    @DisplayName("特定の年月の練習日を取得できる")
    void testFindSessionsByYearMonth() {
        // Given
        int year = today.getYear();
        int month = today.getMonthValue();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(testSession));
        when(practiceParticipantRepository.findBySessionIdIn(any())).thenReturn(List.of());
        when(matchRepository.countByMatchDateIn(any())).thenReturn(List.of());

        // When
        List<PracticeSessionDto> result = practiceSessionService.findSessionsByYearMonth(year, month);

        // Then
        assertThat(result).hasSize(1);
        verify(practiceSessionRepository).findByYearAndMonth(year, month);
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
                .organizationId(1L)
                .build();
        when(practiceSessionRepository.existsBySessionDateAndOrganizationId(today, 1L)).thenReturn(false);
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);
        when(practiceParticipantRepository.findBySessionId(1L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // When
        PracticeSessionDto result = practiceSessionService.createSession(request, 1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).existsBySessionDateAndOrganizationId(today, 1L);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("既存の日付で練習日を登録するとDuplicateResourceExceptionが発生")
    void testCreateSessionDuplicateDate() {
        // Given
        PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(12)
                .organizationId(1L)
                .build();
        when(practiceSessionRepository.existsBySessionDateAndOrganizationId(today, 1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.createSession(request, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining(today.toString());
        verify(practiceSessionRepository).existsBySessionDateAndOrganizationId(today, 1L);
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
        verify(practiceParticipantRepository).deleteBySessionId(1L);
        verify(practiceSessionRepository).deleteById(1L);
    }

    @Test
    @DisplayName("updateSession: BYE(matchNumber=null)はキャンセル対象から除外される")
    void testUpdateSession_byeExcludedFromCancellation() {
        // Given: セッションに通常参加者2名 + BYE1名が存在
        Long sessionId = 1L;
        PracticeSession session = PracticeSession.builder()
                .id(sessionId).sessionDate(today).totalMatches(3).organizationId(1L).build();

        // player1: 通常参加者（リクエストに含まれる → 残る）
        PracticeParticipant normalPp1 = PracticeParticipant.builder()
                .id(100L).sessionId(sessionId).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        // player2: 通常参加者（リクエストに含まれない → CANCELLED）
        PracticeParticipant normalPp2 = PracticeParticipant.builder()
                .id(101L).sessionId(sessionId).playerId(2L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        // player3: BYE（matchNumber=null、リクエストに含まれない → 除外される）
        PracticeParticipant byePp = PracticeParticipant.builder()
                .id(102L).sessionId(sessionId).playerId(3L).matchNumber(null)
                .status(ParticipantStatus.WON).dirty(false).build();

        List<PracticeParticipant> existingParticipants = new ArrayList<>(List.of(normalPp1, normalPp2, byePp));

        Player p1 = new Player(); p1.setId(1L); p1.setName("選手1");

        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionId(sessionId)).thenReturn(existingParticipants);
        when(playerRepository.findAllById(List.of(1L))).thenReturn(List.of(p1));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(session);
        when(practiceParticipantRepository.saveAll(anyList())).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        PracticeSessionUpdateRequest request = PracticeSessionUpdateRequest.builder()
                .sessionDate(today)
                .totalMatches(3)
                .participantIds(List.of(1L)) // player1のみ残す
                .build();

        // When
        practiceSessionService.updateSession(sessionId, request, 1L);

        // Then: player2は CANCELLED になる
        assertThat(normalPp2.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        assertThat(normalPp2.isDirty()).isTrue();

        // BYEは除外されるため、ステータスが変更されない
        assertThat(byePp.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(byePp.isDirty()).isFalse();
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
