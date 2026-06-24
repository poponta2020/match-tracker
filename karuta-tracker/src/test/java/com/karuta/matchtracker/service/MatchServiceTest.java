package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchVideo;
import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchPersonalNoteRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.MatchVideoRepository;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
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

import org.mockito.ArgumentCaptor;

/**
 * MatchServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchService 単体テスト")
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchVideoRepository matchVideoRepository;

    @Mock
    private MatchPairingRepository matchPairingRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private MatchPersonalNoteRepository matchPersonalNoteRepository;

    @Mock
    private MentorRelationshipRepository mentorRelationshipRepository;

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
        List<MatchDto> result = matchService.findMatchesByDate(today, null);

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
        List<MatchDto> result = matchService.findPlayerMatches(1L, null);

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
        assertThatThrownBy(() -> matchService.findPlayerMatches(999L, null))
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
        List<MatchDto> result = matchService.findPlayerMatchesInPeriod(1L, startDate, endDate, null);

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
        List<MatchDto> result = matchService.findMatchesBetweenPlayers(1L, 2L, null);

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

        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerRepository.existsById(2L)).thenReturn(true);
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        MatchDto result = matchService.createMatch(request, 1L, Player.Role.PLAYER);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPlayer1Name()).isEqualTo("山田太郎");
        assertThat(result.getMatchDate()).isEqualTo(today);
        assertThat(result.getMatchNumber()).isEqualTo(1);
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

        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerRepository.existsById(2L)).thenReturn(true);
        when(playerRepository.existsById(3L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> matchService.createMatch(request, 1L, Player.Role.PLAYER))
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

        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
        when(playerRepository.existsById(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> matchService.createMatch(request, 1L, Player.Role.PLAYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot play against themselves");
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    @DisplayName("指導試合として新規登録できる（isLesson=true・枚数差はnull保存）")
    void testCreateLessonMatch() {
        // Given
        MatchCreateRequest request = MatchCreateRequest.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .winnerId(1L)          // 勝ち=指導した側
                .scoreDifference(10)   // 指導試合では無視され null 保存される
                .isLesson(true)
                .createdBy(1L)
                .build();

        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerRepository.existsById(2L)).thenReturn(true);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        MatchDto result = matchService.createMatch(request, 1L, Player.Role.PLAYER);

        // Then
        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match saved = captor.getValue();
        assertThat(saved.getIsLesson()).isTrue();
        assertThat(saved.getScoreDifference()).isNull();
        assertThat(saved.getWinnerId()).isEqualTo(1L);
        assertThat(result.getIsLesson()).isTrue();
    }

    @Test
    @DisplayName("試合結果を更新できる")
    void testUpdateMatch() {
        // Given
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When
        MatchDto result = matchService.updateMatch(1L, 2L, 3, 1L, null, null, false, 1L, Player.Role.PLAYER);

        // Then
        assertThat(result).isNotNull();
        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match saved = captor.getValue();
        assertThat(saved.getWinnerId()).isEqualTo(2L);
        assertThat(saved.getScoreDifference()).isEqualTo(3);
        assertThat(saved.getUpdatedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("指導試合として更新できる（isLesson=true・枚数差はnull保存）")
    void testUpdateMatchAsLesson() {
        // Given
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        // When: winnerId=1L(指導した側)・scoreDifference=8は無視されnull保存・isLesson=true
        MatchDto result = matchService.updateMatch(1L, 1L, 8, 1L, null, null, true, 1L, Player.Role.PLAYER);

        // Then
        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match saved = captor.getValue();
        assertThat(saved.getIsLesson()).isTrue();
        assertThat(saved.getScoreDifference()).isNull();
        assertThat(saved.getWinnerId()).isEqualTo(1L);
        assertThat(result.getIsLesson()).isTrue();
    }

    @Test
    @DisplayName("通常試合の更新で枚数差がnullの場合はIllegalArgumentException")
    void testUpdateMatchRejectsNullScoreForNonLesson() {
        // Given
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));

        // When & Then: 通常試合（isLesson=false）で scoreDifference=null は拒否
        assertThatThrownBy(() ->
                matchService.updateMatch(1L, 1L, null, 1L, null, null, false, 1L, Player.Role.PLAYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("枚数差");
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    @DisplayName("簡易更新は指導試合フラグを解除する（通常試合化）")
    void testUpdateMatchSimpleClearsLessonFlag() {
        // Given: 既存は指導試合
        Match lessonMatch = Match.builder()
                .id(1L).matchDate(today).matchNumber(1)
                .player1Id(1L).player2Id(2L).winnerId(1L)
                .isLesson(true)
                .createdBy(1L).updatedBy(1L).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(lessonMatch));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

        MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
        request.setMatchDate(today);
        request.setMatchNumber(1);
        request.setPlayerId(1L);
        request.setOpponentName("佐藤花子");
        request.setResult("勝ち");
        request.setScoreDifference(5);

        // When
        matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

        // Then: 指導フラグが解除され、枚数差が保存される
        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        assertThat(captor.getValue().getIsLesson()).isFalse();
        assertThat(captor.getValue().getScoreDifference()).isEqualTo(5);
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
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", null, null, null);

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
                    .gender(Player.Gender.男性)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("女性選手")
                    .gender(Player.Gender.女性)
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
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, null, "男性", null, null);

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
                    .dominantHand(Player.DominantHand.右)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("左利き選手")
                    .dominantHand(Player.DominantHand.左)
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
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, null, null, "右", null);

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
                    .gender(Player.Gender.男性)
                    .dominantHand(Player.DominantHand.右)
                    .build();
            Player opponent2 = Player.builder()
                    .id(4L)
                    .name("A級・女性・右利き")
                    .kyuRank(Player.KyuRank.A級)
                    .gender(Player.Gender.女性)
                    .dominantHand(Player.DominantHand.右)
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
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", "男性", "右", null);

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
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", null, null, null);

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
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, "A級", null, null, null);

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
            assertThat(result.getTotal().getTotal()).isEqualTo(3);
            assertThat(result.getTotal().getWins()).isEqualTo(2);

            Map<String, RankStatisticsDto> byRank = result.getByRank();
            assertThat(byRank.get("A級").getTotal()).isEqualTo(2);
            assertThat(byRank.get("A級").getWins()).isEqualTo(1);
            assertThat(byRank.get("B級").getTotal()).isEqualTo(1);
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
                    .gender(Player.Gender.男性)
                    .build();
            Player opponentFemale = Player.builder()
                    .id(4L)
                    .name("女性A級")
                    .kyuRank(Player.KyuRank.A級)
                    .gender(Player.Gender.女性)
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
            StatisticsByRankDto result = matchService.getPlayerStatisticsByRank(1L, "男性", null, null, null);

            // Then
            assertThat(result.getTotal().getTotal()).isEqualTo(1);
            assertThat(result.getByRank().get("A級").getTotal()).isEqualTo(1);
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
            assertThat(result.getTotal().getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("指導回数・被指導回数を集計でき、指導試合も通常統計に計上される")
        void shouldCountLessonsGivenAndReceived() {
            // Given
            Player opponent = Player.builder()
                    .id(3L)
                    .name("初心者")
                    .kyuRank(Player.KyuRank.E級)
                    .build();

            // 指導試合: player(1L) が勝ち = 指導した側
            Match lessonGiven = Match.builder()
                    .id(1L).player1Id(1L).player2Id(3L).matchDate(today)
                    .winnerId(1L).isLesson(true).build();
            // 指導試合: player(1L) が負け = 指導された側
            Match lessonReceived = Match.builder()
                    .id(2L).player1Id(1L).player2Id(3L).matchDate(today)
                    .winnerId(3L).isLesson(true).build();
            // 通常試合（勝ち）
            Match normalWin = Match.builder()
                    .id(3L).player1Id(1L).player2Id(3L).matchDate(today)
                    .winnerId(1L).scoreDifference(5).isLesson(false).build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L))
                    .thenReturn(List.of(lessonGiven, lessonReceived, normalWin));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent));

            // When
            StatisticsByRankDto result = matchService.getPlayerStatisticsByRank(1L, null, null, null, null);

            // Then: 指導試合も通常統計（試合数・勝数）に計上される
            assertThat(result.getTotal().getTotal()).isEqualTo(3);
            assertThat(result.getTotal().getWins()).isEqualTo(2);
            // 指導回数=1・被指導回数=1
            assertThat(result.getLessonGivenCount()).isEqualTo(1);
            assertThat(result.getLessonReceivedCount()).isEqualTo(1);
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
            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("未登録選手");
            request.setResult("勝ち");
            request.setScoreDifference(5);

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            MatchDto result = matchService.createMatchSimple(request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getPlayer1Id()).isEqualTo(1L);
            assertThat(saved.getPlayer2Id()).isEqualTo(0L);
            assertThat(saved.getWinnerId()).isEqualTo(1L);
            assertThat(saved.getOpponentName()).isEqualTo("未登録選手");
            assertThat(saved.getScoreDifference()).isEqualTo(5);
        }

        @Test
        @DisplayName("簡易版で試合結果を登録できる（負け）")
        void shouldCreateMatchSimpleWithLoss() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("未登録選手");
            request.setResult("負け");
            request.setScoreDifference(3);

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            MatchDto result = matchService.createMatchSimple(request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getPlayer1Id()).isEqualTo(1L);
            assertThat(saved.getPlayer2Id()).isEqualTo(0L);
            assertThat(saved.getWinnerId()).isEqualTo(0L);
            assertThat(saved.getOpponentName()).isEqualTo("未登録選手");
            assertThat(saved.getScoreDifference()).isEqualTo(3);
        }

        @Test
        @DisplayName("練習日として登録されていない日付では登録できない")
        void shouldFailToCreateMatchForNonPracticeDate() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("未登録選手");
            request.setResult("勝ち");
            request.setScoreDifference(5);

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> matchService.createMatchSimple(request, 1L, Player.Role.PLAYER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("練習日として登録されている日のみ");

            verify(matchRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しない選手IDではエラー")
        void shouldFailForNonexistentPlayer() {
            // Given
            LocalDate today = LocalDate.now();
            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(999L);
            request.setOpponentName("未登録選手");
            request.setResult("勝ち");
            request.setScoreDifference(5);

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> matchService.createMatchSimple(request, 1L, Player.Role.PLAYER))
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
                    .player2Id(0L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("新しい対戦相手");
            request.setResult("負け");
            request.setScoreDifference(3);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));

            // When
            MatchDto result = matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getWinnerId()).isEqualTo(0L);
            assertThat(saved.getOpponentName()).isEqualTo("新しい対戦相手");
            assertThat(saved.getScoreDifference()).isEqualTo(3);
            assertThat(saved.getUpdatedBy()).isEqualTo(1L);
        }

        @Test
        @DisplayName("登録済み選手同士の試合でplayer2が負けた場合、winnerId=player1Idになる")
        void shouldSetWinnerToPlayer1WhenPlayer2Loses() {
            // Given
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(0L)
                    .scoreDifference(0)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(2L); // player2が記録
            request.setOpponentName("山田太郎");
            request.setResult("負け");
            request.setScoreDifference(3);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, player2));

            // When
            matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getWinnerId()).isEqualTo(1L); // player1が勝者
            assertThat(saved.getPlayer1Id()).isEqualTo(1L); // 変更されない
            assertThat(saved.getPlayer2Id()).isEqualTo(2L); // 変更されない
        }

        @Test
        @DisplayName("登録済み選手同士の試合でplayer1が負けた場合、winnerId=player2Idになる")
        void shouldSetWinnerToPlayer2WhenPlayer1Loses() {
            // Given
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(0L)
                    .scoreDifference(0)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L); // player1が記録
            request.setOpponentName("佐藤花子");
            request.setResult("負け");
            request.setScoreDifference(5);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, player2));

            // When
            matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getWinnerId()).isEqualTo(2L); // player2が勝者
            assertThat(saved.getPlayer1Id()).isEqualTo(1L); // 変更されない
            assertThat(saved.getPlayer2Id()).isEqualTo(2L); // 変更されない
        }

        @Test
        @DisplayName("登録済み選手同士の試合で勝った場合、winnerId=自分のIDになる")
        void shouldSetWinnerToSelfWhenWin() {
            // Given
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(0L)
                    .scoreDifference(0)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(2L); // player2が記録
            request.setOpponentName("山田太郎");
            request.setResult("勝ち");
            request.setScoreDifference(7);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, player2));

            // When
            matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getWinnerId()).isEqualTo(2L); // player2自身が勝者
            assertThat(saved.getPlayer1Id()).isEqualTo(1L); // 変更されない
            assertThat(saved.getPlayer2Id()).isEqualTo(2L); // 変更されない
        }

        @Test
        @DisplayName("存在しない試合の更新はエラー")
        void shouldFailForNonexistentMatch() {
            // Given
            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(LocalDate.now());
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("対戦相手");
            request.setResult("勝ち");
            request.setScoreDifference(5);

            when(matchRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> matchService.updateMatchSimple(999L, request, 1L, Player.Role.PLAYER))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Match");

            verify(matchRepository, never()).save(any());
        }

        @Test
        @DisplayName("更新時にplayer1Id/player2Idが変化しないこと")
        void shouldNotChangePlayerIds() {
            // Given
            LocalDate today = LocalDate.now();
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("佐藤花子");
            request.setResult("勝ち");
            request.setScoreDifference(3);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, player2));

            // When
            matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getPlayer1Id()).isEqualTo(1L);
            assertThat(saved.getPlayer2Id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("player2側から編集してもplayer1Idが上書きされないこと")
        void shouldNotOverwritePlayer1IdWhenEditedByPlayer2() {
            // Given: player2(id=2L)がplayer1(id=1L)との試合を編集するケース
            LocalDate today = LocalDate.now();
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(2L)
                    .scoreDifference(5)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(2L);
            request.setOpponentName("田中太郎");
            request.setResult("負け");
            request.setScoreDifference(3);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, player2));

            // When
            matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getPlayer1Id()).isEqualTo(1L);
            assertThat(saved.getPlayer2Id()).isEqualTo(2L);
            assertThat(saved.getWinnerId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("両者登録済み試合でRESULT_LOSEのとき相手IDがwinnerIdになること")
        void shouldSetOpponentAsWinnerOnLose() {
            // Given
            LocalDate today = LocalDate.now();
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("佐藤花子");
            request.setResult("負け");
            request.setScoreDifference(3);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, player2));

            // When
            matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER);

            // Then
            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            Match saved = captor.getValue();
            assertThat(saved.getWinnerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("非参加者のplayerIdで更新するとエラー")
        void shouldRejectNonParticipantPlayerId() {
            // Given
            LocalDate today = LocalDate.now();
            Match existingMatch = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();

            Player player3 = Player.builder()
                    .id(3L)
                    .name("鈴木一郎")
                    .build();

            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayerId(3L);
            request.setOpponentName("対戦相手");
            request.setResult("勝ち");
            request.setScoreDifference(5);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(existingMatch));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(player3));

            // When & Then
            assertThatThrownBy(() -> matchService.updateMatchSimple(1L, request, 1L, Player.Role.PLAYER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("参加者ではありません");

            verify(matchRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ペアリング自動生成テスト")
    class AutoCreateMatchPairingTests {

        @Test
        @DisplayName("createMatch時に両プレイヤー登録済みの場合ペアリングが自動生成される")
        void shouldAutoCreatePairingOnCreateMatch() {
            // Given
            MatchCreateRequest request = new MatchCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayer1Id(1L);
            request.setPlayer2Id(2L);
            request.setWinnerId(1L);
            request.setScoreDifference(5);
            request.setCreatedBy(1L);

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.existsById(1L)).thenReturn(true);
            when(playerRepository.existsById(2L)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
            when(matchPairingRepository.findBySessionDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // When
            matchService.createMatch(request, 1L, Player.Role.PLAYER);

            // Then: ペアリングが自動生成される
            ArgumentCaptor<MatchPairing> captor = ArgumentCaptor.forClass(MatchPairing.class);
            verify(matchPairingRepository).save(captor.capture());
            MatchPairing saved = captor.getValue();
            assertThat(saved.getSessionDate()).isEqualTo(today);
            assertThat(saved.getMatchNumber()).isEqualTo(1);
            assertThat(saved.getPlayer1Id()).isEqualTo(1L);
            assertThat(saved.getPlayer2Id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("createMatch時にペアリングが既存の場合は重複生成されない")
        void shouldNotDuplicatePairingOnCreateMatch() {
            // Given
            MatchCreateRequest request = new MatchCreateRequest();
            request.setMatchDate(today);
            request.setMatchNumber(1);
            request.setPlayer1Id(1L);
            request.setPlayer2Id(2L);
            request.setWinnerId(1L);
            request.setScoreDifference(5);
            request.setCreatedBy(1L);

            MatchPairing existingPairing = MatchPairing.builder()
                    .id(10L).sessionDate(today).matchNumber(1).player1Id(1L).player2Id(2L).createdBy(1L).build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.existsById(1L)).thenReturn(true);
            when(playerRepository.existsById(2L)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
            when(matchPairingRepository.findBySessionDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(existingPairing));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // When
            matchService.createMatch(request, 1L, Player.Role.PLAYER);

            // Then: ペアリングは保存されない
            verify(matchPairingRepository, never()).save(any(MatchPairing.class));
        }
    }

    @Nested
    @DisplayName("勝敗表示の視点ロジック")
    class DetermineResultPerspectiveTests {

        @Test
        @DisplayName("閲覧者が勝者の場合は「勝ち」と表示される")
        void shouldShowWinWhenViewerIsWinner() {
            // Given: player1(id=1)が勝者、閲覧者もplayer1
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(eq(1L), anyList())).thenReturn(List.of());

            // When
            MatchDto result = matchService.findById(1L, 1L);

            // Then
            assertThat(result.getResult()).isEqualTo("勝ち");
        }

        @Test
        @DisplayName("閲覧者が敗者の場合は「負け」と表示される")
        void shouldShowLoseWhenViewerIsLoser() {
            // Given: player1(id=1)が勝者、閲覧者はplayer2(id=2)
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(eq(2L), anyList())).thenReturn(List.of());

            // When
            MatchDto result = matchService.findById(1L, 2L);

            // Then
            assertThat(result.getResult()).isEqualTo("負け");
        }

        @Test
        @DisplayName("閲覧者が非参加者の場合はplayer1基準にフォールバックする")
        void shouldFallbackToPlayer1WhenViewerIsNotParticipant() {
            // Given: player1(id=1)が勝者、閲覧者はid=99(非参加者)
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(eq(99L), anyList())).thenReturn(List.of());

            // When
            MatchDto result = matchService.findById(1L, 99L);

            // Then: player1基準なので「勝ち」
            assertThat(result.getResult()).isEqualTo("勝ち");
        }

        @Test
        @DisplayName("閲覧者がnullの場合はplayer1基準にフォールバックする")
        void shouldFallbackToPlayer1WhenViewerIsNull() {
            // Given: player1(id=1)が勝者、閲覧者はnull
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // When
            MatchDto result = matchService.findById(1L, null);

            // Then: player1基準なので「勝ち」
            assertThat(result.getResult()).isEqualTo("勝ち");
        }

        @Test
        @DisplayName("引き分けの場合は閲覧者に関わらず「引き分け」と表示される")
        void shouldShowDrawRegardlessOfViewer() {
            // Given: 引き分け（winnerId=0）
            Match drawMatch = Match.builder()
                    .id(2L)
                    .matchDate(today)
                    .matchNumber(2)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(0L)
                    .build();
            when(matchRepository.findById(2L)).thenReturn(Optional.of(drawMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(eq(1L), anyList())).thenReturn(List.of());

            // When
            MatchDto result = matchService.findById(2L, 1L);

            // Then
            assertThat(result.getResult()).isEqualTo("引き分け");
        }

        @Test
        @DisplayName("findMatchesByDateで非参加者が閲覧してもplayer1基準になる")
        void shouldFallbackToPlayer1InFindMatchesByDate() {
            // Given: player1(id=1)が勝者、閲覧者はid=99(非参加者)
            when(matchRepository.findByMatchDateOrderByMatchNumber(today))
                    .thenReturn(List.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // When
            List<MatchDto> result = matchService.findMatchesByDate(today, 99L);

            // Then: player1基準なので「勝ち」
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getResult()).isEqualTo("勝ち");
        }

        @Test
        @DisplayName("findMatchesBetweenPlayersで非参加者が閲覧してもplayer1基準になる")
        void shouldFallbackToPlayer1InFindMatchesBetweenPlayers() {
            // Given: player1(id=1)が勝者、閲覧者はid=99(非参加者)
            when(playerRepository.existsById(1L)).thenReturn(true);
            when(playerRepository.existsById(2L)).thenReturn(true);
            when(matchRepository.findByTwoPlayers(1L, 2L)).thenReturn(List.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // When
            List<MatchDto> result = matchService.findMatchesBetweenPlayers(1L, 2L, 99L);

            // Then: player1基準なので「勝ち」
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getResult()).isEqualTo("勝ち");
        }
    }

    @Nested
    @DisplayName("findById with viewedPlayerId")
    class FindByIdWithViewedPlayerId {

        @Test
        @DisplayName("試合参加者でないplayerIdを指定するとエラー")
        void invalidViewedPlayerIdThrows() {
            Match match = new Match();
            match.setId(1L);
            match.setPlayer1Id(2L);
            match.setPlayer2Id(3L);
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

            assertThatThrownBy(() -> matchService.findById(1L, 10L, 99L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("viewedPlayerIdが自分自身の場合はメンター検証なしで取得できる")
        void selfViewIsAllowedWithoutMentorCheck() {
            Match match = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .build();
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(anyLong(), anyList()))
                    .thenReturn(List.of());

            MatchDto result = matchService.findById(1L, 1L, 1L);

            assertThat(result).isNotNull();
            verify(mentorRelationshipRepository, never())
                    .findByMentorIdAndStatus(any(), any());
        }

        @Test
        @DisplayName("ACTIVEメンターは他選手の対戦詳細を閲覧できる")
        void activeMentorCanViewMenteeMatch() {
            Match match = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(2L)
                    .player2Id(3L)
                    .winnerId(2L)
                    .scoreDifference(5)
                    .build();
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            MentorRelationship rel = MentorRelationship.builder()
                    .mentorId(10L)
                    .menteeId(2L)
                    .status(MentorRelationship.Status.ACTIVE)
                    .build();
            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(rel));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(anyLong(), anyList()))
                    .thenReturn(List.of());

            MatchDto result = matchService.findById(1L, 10L, 2L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("メンターがメンティー詳細を見ると、メンティー視点（player2が負け）で勝敗が算出される")
        void mentorSeesMatchFromMenteePerspective() {
            // メンティー(id=2) が player2 として登場し、player1(id=3) が勝った試合
            Player mentee = Player.builder().id(2L).name("メンティー").build();
            Player opponentForMentee = Player.builder().id(3L).name("ライバル").build();
            Match match = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(3L)
                    .player2Id(2L)
                    .winnerId(3L)
                    .scoreDifference(5)
                    .build();
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
            when(playerRepository.findAllById(any())).thenReturn(List.of(mentee, opponentForMentee));
            MentorRelationship rel = MentorRelationship.builder()
                    .mentorId(10L)
                    .menteeId(2L)
                    .status(MentorRelationship.Status.ACTIVE)
                    .build();
            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(rel));
            when(matchPersonalNoteRepository.findByPlayerIdAndMatchIdIn(anyLong(), anyList()))
                    .thenReturn(List.of());

            MatchDto result = matchService.findById(1L, 10L, 2L);

            assertThat(result).isNotNull();
            // メンティー視点で見ると「負け」、相手は player1（ライバル）
            assertThat(result.getResult()).isEqualTo("負け");
            assertThat(result.getOpponentName()).isEqualTo("ライバル");
        }

        @Test
        @DisplayName("非メンターが他選手の対戦詳細を閲覧しようとするとForbiddenException")
        void nonMentorCannotViewOtherPlayerMatch() {
            Match match = Match.builder()
                    .id(1L)
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(2L)
                    .player2Id(3L)
                    .winnerId(2L)
                    .scoreDifference(5)
                    .build();
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> matchService.findById(1L, 10L, 2L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("メンター関係");
        }
    }

    @Nested
    @DisplayName("venue_id 解決ロジック (createMatchSimple 経由)")
    class VenueResolutionTests {

        private MatchSimpleCreateRequest buildSimpleRequest(LocalDate date) {
            MatchSimpleCreateRequest request = new MatchSimpleCreateRequest();
            request.setMatchDate(date);
            request.setMatchNumber(1);
            request.setPlayerId(1L);
            request.setOpponentName("未登録選手");
            request.setResult("勝ち");
            request.setScoreDifference(5);
            return request;
        }

        private void stubCommonCreatePath(LocalDate date) {
            when(practiceSessionRepository.existsBySessionDate(date)).thenReturn(true);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1));
        }

        @Test
        @DisplayName("PracticeParticipant 経由で venue_id が解決される")
        void shouldResolveVenueIdViaPracticeParticipant() {
            LocalDate today = LocalDate.now();
            stubCommonCreatePath(today);
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of(10L));

            matchService.createMatchSimple(buildSimpleRequest(today), 1L, Player.Role.PLAYER);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            assertThat(captor.getValue().getVenueId()).isEqualTo(10L);
            // 1段目で見つかったので 2段目は呼ばれない
            verify(practiceSessionRepository, never()).findDistinctVenueIdsBySessionDate(any());
        }

        @Test
        @DisplayName("PracticeParticipant 経由で見つからない場合、同日一意の venue が採用される")
        void shouldResolveVenueIdViaSameDayUniqueVenue() {
            LocalDate today = LocalDate.now();
            stubCommonCreatePath(today);
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findDistinctVenueIdsBySessionDate(today))
                    .thenReturn(List.of(20L));

            matchService.createMatchSimple(buildSimpleRequest(today), 1L, Player.Role.PLAYER);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            assertThat(captor.getValue().getVenueId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("同日複数の練習会場が混在する場合は venue_id を NULL のままにする")
        void shouldLeaveVenueIdNullWhenMultipleVenuesOnSameDay() {
            LocalDate today = LocalDate.now();
            stubCommonCreatePath(today);
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findDistinctVenueIdsBySessionDate(today))
                    .thenReturn(List.of(20L, 30L));

            matchService.createMatchSimple(buildSimpleRequest(today), 1L, Player.Role.PLAYER);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            assertThat(captor.getValue().getVenueId()).isNull();
        }

        @Test
        @DisplayName("該当する練習会がない場合は venue_id を NULL のままにする")
        void shouldLeaveVenueIdNullWhenNoMatchingSession() {
            LocalDate today = LocalDate.now();
            stubCommonCreatePath(today);
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findDistinctVenueIdsBySessionDate(today))
                    .thenReturn(List.of());

            matchService.createMatchSimple(buildSimpleRequest(today), 1L, Player.Role.PLAYER);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            assertThat(captor.getValue().getVenueId()).isNull();
        }

        @Test
        @DisplayName("createMatch: ADMIN 代理登録でも player1/player2 の参加 venue が採用される（登録者基準のリグレッション防止）")
        void shouldResolveVenueBasedOnParticipantsNotCreatedBy() {
            LocalDate today = LocalDate.now();
            MatchCreateRequest request = MatchCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .createdBy(99L)
                    .build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.existsById(1L)).thenReturn(true);
            when(playerRepository.existsById(2L)).thenReturn(true);
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            // player1, player2 が同じ会場 (10) に active 参加
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of(10L));
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(2L, today, 1))
                    .thenReturn(List.of(10L));

            // ADMIN (id=99) が代理登録（player1/2 のいずれでもない）
            matchService.createMatch(request, 99L, Player.Role.ADMIN);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            // ADMIN(99)の参加 venue ではなく、player1/2 の参加 venue (=10) が採用される
            assertThat(captor.getValue().getVenueId()).isEqualTo(10L);
            // 1段目で確定したので 2段目 fallback は呼ばれない
            verify(practiceSessionRepository, never()).findDistinctVenueIdsBySessionDate(any());
            // ADMIN(99)のクエリは呼ばれない（参加者基準なので player1/2 のみ）
            verify(practiceParticipantRepository, never())
                    .findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(eq(99L), any(), any());
        }

        @Test
        @DisplayName("createMatch: player1 と player2 が別会場に参加している場合は同日一意 fallback")
        void shouldFallbackWhenPlayer1AndPlayer2DifferOnVenue() {
            LocalDate today = LocalDate.now();
            MatchCreateRequest request = MatchCreateRequest.builder()
                    .matchDate(today)
                    .matchNumber(1)
                    .player1Id(1L)
                    .player2Id(2L)
                    .winnerId(1L)
                    .scoreDifference(5)
                    .createdBy(1L)
                    .build();

            when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);
            when(playerRepository.existsById(1L)).thenReturn(true);
            when(playerRepository.existsById(2L)).thenReturn(true);
            when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            // player1 と player2 が別会場に参加（参加者集約で size=2 → 1段目では確定しない）
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of(10L));
            when(practiceParticipantRepository.findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(2L, today, 1))
                    .thenReturn(List.of(20L));
            // 2段目: 同日一意 venue = 30
            when(practiceSessionRepository.findDistinctVenueIdsBySessionDate(today))
                    .thenReturn(List.of(30L));

            matchService.createMatch(request, 1L, Player.Role.PLAYER);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            assertThat(captor.getValue().getVenueId()).isEqualTo(30L);
        }

        @Test
        @DisplayName("同じ選手が同日別試合番号で別会場に参加していても、対象試合番号の venue だけ採用する（match_number 絞り込み）")
        void shouldFilterByMatchNumberAndIgnoreOtherMatchNumberVenues() {
            LocalDate today = LocalDate.now();
            // buildSimpleRequest の matchNumber は常に 1
            stubCommonCreatePath(today);
            // 対象試合番号=1 の venue は会場A (10) のみ
            when(practiceParticipantRepository
                    .findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(1L, today, 1))
                    .thenReturn(List.of(10L));

            matchService.createMatchSimple(buildSimpleRequest(today), 1L, Player.Role.PLAYER);

            ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
            verify(matchRepository).save(captor.capture());
            // 同じ選手が matchNumber=2 で会場B に参加していたとしても、対象 matchNumber=1 の
            // クエリしか呼ばれないため venue=10 で一意特定される
            assertThat(captor.getValue().getVenueId()).isEqualTo(10L);
            verify(practiceParticipantRepository, never())
                    .findVenueIdsByPlayerIdAndSessionDateAndMatchNumber(any(), any(), eq(2));
            // 1段目で確定したので 2段目 fallback は呼ばれない
            verify(practiceSessionRepository, never()).findDistinctVenueIdsBySessionDate(any());
        }
    }

    @Nested
    @DisplayName("試合動画 (video) 付与ロジック")
    class MatchVideoEnrichmentTests {

        private MatchVideo buildVideo(LocalDate matchDate, int matchNumber, long p1, long p2) {
            return MatchVideo.builder()
                    .id(100L)
                    .matchDate(matchDate)
                    .matchNumber(matchNumber)
                    .player1Id(Math.min(p1, p2))
                    .player2Id(Math.max(p1, p2))
                    .videoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                    .youtubeVideoId("dQw4w9WgXcQ")
                    .title("第1試合 山田太郎 vs 佐藤花子")
                    .createdBy(1L)
                    .updatedBy(1L)
                    .build();
        }

        @Test
        @DisplayName("単体取得: 動画がある試合では video がセットされる")
        void shouldAttachVideoOnFindByIdWhenVideoExists() {
            // Given: testMatch (date=today, number=1, p1=1, p2=2) に動画が存在
            MatchVideo video = buildVideo(today, 1, 1L, 2L);
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(video));

            // When
            MatchDto result = matchService.findById(1L, 1L);

            // Then
            assertThat(result.getVideo()).isNotNull();
            assertThat(result.getVideo().getId()).isEqualTo(100L);
            assertThat(result.getVideo().getVideoUrl())
                    .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
            assertThat(result.getVideo().getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
            assertThat(result.getVideo().getTitle()).isEqualTo("第1試合 山田太郎 vs 佐藤花子");
        }

        @Test
        @DisplayName("単体取得: 動画がない試合では video が null")
        void shouldLeaveVideoNullOnFindByIdWhenNoVideo() {
            // Given: 自然キー照合で動画が見つからない
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());

            // When
            MatchDto result = matchService.findById(1L, 1L);

            // Then
            assertThat(result.getVideo()).isNull();
        }

        @Test
        @DisplayName("単体取得: player1Id > player2Id でも min/max 正規化して照合する")
        void shouldNormalizeKeyWhenAttachingVideoOnFindById() {
            // Given: player1Id=5, player2Id=3 の試合（正規化前）。照合は (3,5) で行われる
            Player p3 = Player.builder().id(3L).name("選手3").build();
            Player p5 = Player.builder().id(5L).name("選手5").build();
            Match match = Match.builder()
                    .id(7L)
                    .matchDate(today)
                    .matchNumber(2)
                    .player1Id(5L)
                    .player2Id(3L)
                    .winnerId(5L)
                    .scoreDifference(4)
                    .build();
            MatchVideo video = buildVideo(today, 2, 3L, 5L);
            when(matchRepository.findById(7L)).thenReturn(Optional.of(match));
            when(playerRepository.findAllById(any())).thenReturn(List.of(p3, p5));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 2, 3L, 5L))
                    .thenReturn(Optional.of(video));

            // When
            MatchDto result = matchService.findById(7L, 5L);

            // Then: 正規化キー (3,5) で照合され video がセットされる
            assertThat(result.getVideo()).isNotNull();
            assertThat(result.getVideo().getId()).isEqualTo(100L);
            verify(matchVideoRepository).findByMatchDateAndMatchNumberAndPlayers(today, 2, 3L, 5L);
        }

        @Test
        @DisplayName("個人別一覧: 動画がある試合のみ video がセットされ、findByPlayerId は1回だけ呼ばれる")
        void shouldAttachVideosOnlyToMatchesWithVideoInPlayerList() {
            // Given: player1(=1) の2試合。match1(vs3) に動画あり、match2(vs4) に動画なし
            Player opponent1 = Player.builder().id(3L).name("対戦相手A").build();
            Player opponent2 = Player.builder().id(4L).name("対戦相手B").build();
            Match match1 = Match.builder()
                    .id(1L).player1Id(1L).player2Id(3L)
                    .matchDate(today).matchNumber(1).winnerId(1L).build();
            Match match2 = Match.builder()
                    .id(2L).player1Id(1L).player2Id(4L)
                    .matchDate(today).matchNumber(2).winnerId(1L).build();

            MatchVideo videoForMatch1 = buildVideo(today, 1, 1L, 3L);

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match1, match2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent1));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(opponent2));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent1, opponent2));
            // 対象選手の全動画を1クエリで返す（match1 のみ動画あり）
            when(matchVideoRepository.findByPlayerId(1L)).thenReturn(List.of(videoForMatch1));

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, null, null, null, null);

            // Then
            assertThat(result).hasSize(2);
            MatchDto dto1 = result.stream().filter(d -> d.getId().equals(1L)).findFirst().orElseThrow();
            MatchDto dto2 = result.stream().filter(d -> d.getId().equals(2L)).findFirst().orElseThrow();
            assertThat(dto1.getVideo()).isNotNull();
            assertThat(dto1.getVideo().getId()).isEqualTo(100L);
            assertThat(dto1.getVideo().getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
            assertThat(dto2.getVideo()).isNull();
            // N+1回避: findByPlayerId は一覧全体で1回だけ
            verify(matchVideoRepository, times(1)).findByPlayerId(1L);
            // 一覧では自然キー単体照合クエリは使わない
            verify(matchVideoRepository, never())
                    .findByMatchDateAndMatchNumberAndPlayers(any(), any(), any(), any());
        }

        @Test
        @DisplayName("個人別一覧: 対象選手に動画が1件もなければ全試合の video が null")
        void shouldLeaveAllVideosNullWhenPlayerHasNoVideos() {
            // Given
            Player opponent = Player.builder().id(3L).name("対戦相手A").build();
            Match match = Match.builder()
                    .id(1L).player1Id(1L).player2Id(3L)
                    .matchDate(today).matchNumber(1).winnerId(1L).build();

            when(playerRepository.existsById(1L)).thenReturn(true);
            when(matchRepository.findByPlayerId(1L)).thenReturn(List.of(match));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(opponent));
            when(playerRepository.findAllById(anyList())).thenReturn(List.of(player1, opponent));
            when(matchVideoRepository.findByPlayerId(1L)).thenReturn(List.of());

            // When
            List<MatchDto> result = matchService.findPlayerMatchesWithFilters(1L, null, null, null, null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVideo()).isNull();
            verify(matchVideoRepository, times(1)).findByPlayerId(1L);
        }
    }

}