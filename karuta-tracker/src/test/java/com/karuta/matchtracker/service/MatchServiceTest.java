package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MatchCreateRequest;
import com.karuta.matchtracker.dto.MatchDto;
import com.karuta.matchtracker.dto.MatchStatisticsDto;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
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
 * MatchServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchService 単体テスト")
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private MatchService matchService;

    private Match testMatch;
    private Player player1;
    private Player player2;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        player1 = Player.builder()
                .id(1L)
                .name("山田太郎")
                .build();

        player2 = Player.builder()
                .id(2L)
                .name("佐藤花子")
                .build();

        testMatch = Match.builder()
                .id(1L)
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .winnerId(1L)
                .scoreDifference(5)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
    }

    @Test
    @DisplayName("日付別の試合結果を取得できる")
    void testFindMatchesByDate() {
        // Given
        when(matchRepository.findByMatchDateOrderByMatchNumber(today))
                .thenReturn(List.of(testMatch));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        List<MatchDto> result = matchService.findMatchesByDate(today);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayer1Name()).isEqualTo("山田太郎");
        assertThat(result.get(0).getPlayer2Name()).isEqualTo("佐藤花子");
        verify(matchRepository).findByMatchDateOrderByMatchNumber(today);
    }

    @Test
    @DisplayName("特定の日付に試合が存在するか確認できる")
    void testExistsMatchOnDate() {
        // Given
        when(matchRepository.existsByMatchDate(today)).thenReturn(true);

        // When
        boolean exists = matchService.existsMatchOnDate(today);

        // Then
        assertThat(exists).isTrue();
        verify(matchRepository).existsByMatchDate(today);
    }

    @Test
    @DisplayName("選手の試合履歴を取得できる")
    void testFindPlayerMatches() {
        // Given
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(testMatch));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        List<MatchDto> result = matchService.findPlayerMatches(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchDate()).isEqualTo(today);
        verify(playerRepository).existsById(1L);
        verify(matchRepository).findByPlayerId(1L);
    }

    @Test
    @DisplayName("存在しない選手の試合履歴を取得するとResourceNotFoundExceptionが発生")
    void testFindPlayerMatchesNotFound() {
        // Given
        when(playerRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> matchService.findPlayerMatches(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("999");
        verify(playerRepository).existsById(999L);
        verify(matchRepository, never()).findByPlayerId(any());
    }

    @Test
    @DisplayName("選手の期間内の試合履歴を取得できる")
    void testFindPlayerMatchesInPeriod() {
        // Given
        LocalDate startDate = today.minusDays(7);
        LocalDate endDate = today;
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(matchRepository.findByPlayerIdAndDateRange(1L, startDate, endDate))
                .thenReturn(List.of(testMatch));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        List<MatchDto> result = matchService.findPlayerMatchesInPeriod(1L, startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        verify(matchRepository).findByPlayerIdAndDateRange(1L, startDate, endDate);
    }

    @Test
    @DisplayName("2人の選手間の対戦履歴を取得できる")
    void testFindMatchesBetweenPlayers() {
        // Given
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerRepository.existsById(2L)).thenReturn(true);
        when(matchRepository.findByTwoPlayers(1L, 2L)).thenReturn(List.of(testMatch));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        List<MatchDto> result = matchService.findMatchesBetweenPlayers(1L, 2L);

        // Then
        assertThat(result).hasSize(1);
        verify(matchRepository).findByTwoPlayers(1L, 2L);
    }

    @Test
    @DisplayName("選手の統計情報を取得できる")
    void testGetPlayerStatistics() {
        // Given
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
        when(matchRepository.countByPlayerId(1L)).thenReturn(10L);
        when(matchRepository.countWinsByPlayerId(1L)).thenReturn(6L);

        // When
        MatchStatisticsDto result = matchService.getPlayerStatistics(1L);

        // Then
        assertThat(result.getPlayerId()).isEqualTo(1L);
        assertThat(result.getPlayerName()).isEqualTo("山田太郎");
        assertThat(result.getTotalMatches()).isEqualTo(10L);
        assertThat(result.getWins()).isEqualTo(6L);
        assertThat(result.getWinRate()).isEqualTo(60.0);
        verify(matchRepository).countByPlayerId(1L);
        verify(matchRepository).countWinsByPlayerId(1L);
    }

    @Test
    @DisplayName("試合結果を新規登録できる")
    void testCreateMatch() {
        // Given
        MatchCreateRequest request = MatchCreateRequest.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .winnerId(1L)
                .scoreDifference(5)
                .createdBy(1L)
                .build();

        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerRepository.existsById(2L)).thenReturn(true);
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        MatchDto result = matchService.createMatch(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPlayer1Name()).isEqualTo("山田太郎");
        verify(matchRepository).save(any(Match.class));
    }

    @Test
    @DisplayName("勝者が対戦者以外の場合はIllegalArgumentExceptionが発生")
    void testCreateMatchInvalidWinner() {
        // Given
        MatchCreateRequest request = MatchCreateRequest.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .winnerId(3L)  // 対戦者以外
                .scoreDifference(5)
                .createdBy(1L)
                .build();

        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerRepository.existsById(2L)).thenReturn(true);
        when(playerRepository.existsById(3L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Winner must be one of the players");
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    @DisplayName("同じ選手同士の対戦は登録できない")
    void testCreateMatchSamePlayer() {
        // Given
        MatchCreateRequest request = MatchCreateRequest.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(1L)  // 同じ選手
                .winnerId(1L)
                .scoreDifference(5)
                .createdBy(1L)
                .build();

        when(playerRepository.existsById(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot play against themselves");
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    @DisplayName("試合結果を更新できる")
    void testUpdateMatch() {
        // Given
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        MatchDto result = matchService.updateMatch(1L, 2L, 3, 1L);

        // Then
        assertThat(result).isNotNull();
        verify(matchRepository).findById(1L);
        verify(matchRepository).save(any(Match.class));
    }

    @Test
    @DisplayName("試合結果を削除できる")
    void testDeleteMatch() {
        // Given
        when(matchRepository.existsById(1L)).thenReturn(true);

        // When
        matchService.deleteMatch(1L);

        // Then
        verify(matchRepository).existsById(1L);
        verify(matchRepository).deleteById(1L);
    }

    @Test
    @DisplayName("存在しない試合を削除するとResourceNotFoundExceptionが発生")
    void testDeleteMatchNotFound() {
        // Given
        when(matchRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> matchService.deleteMatch(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Match")
                .hasMessageContaining("999");
        verify(matchRepository, never()).deleteById(any());
    }
}
