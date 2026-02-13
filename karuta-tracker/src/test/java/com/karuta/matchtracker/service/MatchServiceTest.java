package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
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

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

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

    @Nested
    @DisplayName("findPlayerMatchesWithFilters メソッド")
    class FindPlayerMatchesWithFiltersTests {

        @Test
        @DisplayName("級位フィルタで対戦履歴を絞り込める")
        void shouldFilterByKyuRank() {
            // Given
            Player opponent1 = Player.builder()
                    .id(3L)
                    .name("A級選手")
                    .kyuRank(Player.KyuRank.A級)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("B級選手")
                    .kyuRank(Player.KyuRank.B級)
                    .build();

            Match match1 = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match match2 = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(4L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match1, match2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent1));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponent2));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent1, opponent2));

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", null, null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("性別フィルタで対戦履歴を絞り込める")
        void shouldFilterByGender() {
            // Given
            Player opponent1 = Player.builder()
                    .id(3L)
                    .name("男性選手")
                    .gender(Player.Gender.MALE)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("女性選手")
                    .gender(Player.Gender.FEMALE)
                    .build();

            Match match1 = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match match2 = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(4L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match1, match2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent1));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponent2));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent1, opponent2));

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, null, "MALE", null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("利き手フィルタで対戦履歴を絞り込める")
        void shouldFilterByDominantHand() {
            // Given
            Player opponent1 = Player.builder()
                    .id(3L)
                    .name("右利き選手")
                    .dominantHand(Player.DominantHand.RIGHT)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("左利き選手")
                    .dominantHand(Player.DominantHand.LEFT)
                    .build();

            Match match1 = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match match2 = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(4L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match1, match2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent1));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponent2));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent1, opponent2));

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, null, null, "RIGHT");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("複数フィルタを組み合わせて絞り込める")
        void shouldFilterByCombinedFilters() {
            // Given
            Player opponent1 = Player.builder()
                    .id(3L)
                    .name("A級・男性・右利き")
                    .kyuRank(Player.KyuRank.A級)
                    .gender(Player.Gender.MALE)
                    .dominantHand(Player.DominantHand.RIGHT)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("A級・女性・右利き")
                    .kyuRank(Player.KyuRank.A級)
                    .gender(Player.Gender.FEMALE)
                    .dominantHand(Player.DominantHand.RIGHT)
                    .build();

            Match match1 = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match match2 = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(4L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match1, match2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent1));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponent2));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent1, opponent2));

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", "MALE", "RIGHT");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("フィルタに合致する対戦がない場合は空リストを返す")
        void shouldReturnEmptyListWhenNoMatchesMatchFilter() {
            // Given
            Player opponent = Player.builder()
                    .id(3L)
                    .name("B級選手")
                    .kyuRank(Player.KyuRank.B級)
                    .build();

            Match match = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent));

            // When: A級でフィルタするが、対戦相手はB級のみ
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", null, null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("対戦相手が未登録の場合はフィルタ対象外になる")
        void shouldExcludeMatchesWithUnregisteredOpponent() {
            // Given
            Match match = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(0L) // 未登録選手
                    .matchDate(today)
                    .winnerId(1L)
                    .opponentName("未登録選手")
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", null, null);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPlayerStatisticsByRank メソッド")
    class GetPlayerStatisticsByRankTests {

        @Test
        @DisplayName("級別統計を正しく計算できる")
        void shouldCalculateStatisticsByRank() {
            // Given
            Player opponentA = Player.builder()
                    .id(3L)
                    .name("A級選手")
                    .kyuRank(Player.KyuRank.A級)
                    .build();
            Player opponentB = Player.builder()
                    .id(4L)
                    .name("B級選手")
                    .kyuRank(Player.KyuRank.B級)
                    .build();

            Match matchA1 = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match matchA2 = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(3L)
                    .build();
            Match matchB1 = Match.builder()
                    .id(3L)
                    .player1Id(1L)
                    .player2Id(4L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(matchA1, matchA2, matchB1));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponentA));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponentB));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponentA, opponentB));

            // When
            StatisticsByRankDto result = matchService.getPlayerStatisticsByRank(1L, null, null, null, null);

            // Then
            assertThat(result.getTotal().getMatches()).isEqualTo(3);
            assertThat(result.getTotal().getWins()).isEqualTo(2);

            Map<String, RankStatisticsDto> byRank = result.getByRank();
            assertThat(byRank.get("A級").getMatches()).isEqualTo(2);
            assertThat(byRank.get("A級").getWins()).isEqualTo(1);
            assertThat(byRank.get("B級").getMatches()).isEqualTo(1);
            assertThat(byRank.get("B級").getWins()).isEqualTo(1);
        }

        @Test
        @DisplayName("性別フィルタで統計を絞り込める")
        void shouldFilterStatisticsByGender() {
            // Given
            Player opponentMale = Player.builder()
                    .id(3L)
                    .name("男性A級")
                    .kyuRank(Player.KyuRank.A級)
                    .gender(Player.Gender.MALE)
                    .build();
            Player opponentFemale = Player.builder()
                    .id(4L)
                    .name("女性A級")
                    .kyuRank(Player.KyuRank.A級)
                    .gender(Player.Gender.FEMALE)
                    .build();

            Match match1 = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match match2 = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(4L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match1, match2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponentMale));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponentFemale));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponentMale, opponentFemale));

            // When
            StatisticsByRankDto result = matchService.getPlayerStatisticsByRank(1L, "MALE", null, null, null);

            // Then
            assertThat(result.getTotal().getMatches()).isEqualTo(1);
            assertThat(result.getByRank().get("A級").getMatches()).isEqualTo(1);
        }

        @Test
        @DisplayName("期間フィルタで統計を絞り込める")
        void shouldFilterStatisticsByDateRange() {
            // Given
            LocalDate oldDate = today.minusDays(10);
            Player opponent = Player.builder()
                    .id(3L)
                    .name("A級選手")
                    .kyuRank(Player.KyuRank.A級)
                    .build();

            Match recentMatch = Match.builder()
                    .id(1L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(today)
                    .winnerId(1L)
                    .build();
            Match oldMatch = Match.builder()
                    .id(2L)
                    .player1Id(1L)
                    .player2Id(3L)
                    .matchDate(oldDate)
                    .winnerId(1L)
                    .build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(recentMatch, oldMatch));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent));

            // When
            LocalDate startDate = today.minusDays(5);
            StatisticsByRankDto result = matchService.getPlayerStatisticsByRank(1L, null, null, startDate, today);

            // Then
            assertThat(result.getTotal().getMatches()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("createMatchSimple メソッド")
    class CreateMatchSimpleTests {

        @Test
        @DisplayName("簡易版で試合結果を登録できる（勝ち）")
        void shouldCreateMatchSimpleWithWin() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("未登録選手")
                    .result("勝ち")
                    .scoreDifference(5)
                    .build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            MatchDto result = matchService.createMatchSimple(request);

            // Then
            assertThat(result).isNotNull();
            verify(matchRepository).save(any(Match.class));
        }

        @Test
        @DisplayName("簡易版で試合結果を登録できる（負け）")
        void shouldCreateMatchSimpleWithLoss() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("未登録選手")
                    .result("負け")
                    .scoreDifference(3)
                    .build();

            Match savedMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .winnerId(0L) // 対戦相手が勝者
                    .scoreDifference(3)
                    .opponentName("未登録選手")
                    .build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            MatchDto result = matchService.createMatchSimple(request);

            // Then
            assertThat(result).isNotNull();
            verify(matchRepository).save(any(Match.class));
        }

        @Test
        @DisplayName("当日以外の日付では登録できない")
        void shouldFailToCreateMatchForNonTodayDate() {
            // Given
            LocalDate pastDate = LocalDate.now().minusDays(1);
            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(pastDate)
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("未登録選手")
                    .result("勝ち")
                    .scoreDifference(5)
                    .build();

            // When & Then
            assertThatThrownBy(() -> matchService.createMatchSimple(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("当日のみ可能");

            verify(matchRepository, never()).save(any());
        }

        @Test
        @DisplayName("練習日として登録されていない日付では登録できない")
        void shouldFailToCreateMatchForNonPracticeDate() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("未登録選手")
                    .result("勝ち")
                    .scoreDifference(5)
                    .build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> matchService.createMatchSimple(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("練習日として登録されている日のみ");

            verify(matchRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しない選手IDではエラー")
        void shouldFailForNonexistentPlayer() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .playerId(999L)
                    .opponentName("未登録選手")
                    .result("勝ち")
                    .scoreDifference(5)
                    .build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> matchService.createMatchSimple(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Player");

            verify(matchRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateMatchSimple メソッド")
    class UpdateMatchSimpleTests {

        @Test
        @DisplayName("簡易版で試合結果を更新できる")
        void shouldUpdateMatchSimple() {
            // Given
            LocalDate today = LocalDate.now();
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();

            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("新しい対戦相手")
                    .result("負け")
                    .scoreDifference(3)
                    .build();

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenReturn(existingMatch);
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            MatchDto result = matchService.updateMatchSimple(1L, request);

            // Then
            assertThat(result).isNotNull();
            verify(matchRepository).save(any(Match.class));
        }

        @Test
        @DisplayName("過去の試合は更新できない")
        void shouldFailToUpdatePastMatch() {
            // Given
            LocalDate pastDate = LocalDate.now().minusDays(1);
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(pastDate)
                    .matchNumber(1)
                    .player1Id(1L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();

            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(LocalDate.now())
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("対戦相手")
                    .result("勝ち")
                    .scoreDifference(5)
                    .build();

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));

            // When & Then
            assertThatThrownBy(() -> matchService.updateMatchSimple(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("過去の試合記録は編集できません");

            verify(matchRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しない試合の更新はエラー")
        void shouldFailForNonexistentMatch() {
            // Given
            MatchSimpleCreateRequest request = MatchSimpleCreateRequest.builder()
                    .matchDate(LocalDate.now())
                    .matchNumber(1)
                    .playerId(1L)
                    .opponentName("対戦相手")
                    .result("勝ち")
                    .scoreDifference(5)
                    .build();

            when(matchRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> matchService.updateMatchSimple(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Match");

            verify(matchRepository, never()).save(any());
        }
    }
}
