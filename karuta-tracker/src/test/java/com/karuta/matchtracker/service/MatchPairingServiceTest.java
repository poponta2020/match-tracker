package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.AutoMatchingRequest;
import com.karuta.matchtracker.dto.AutoMatchingResult;
import com.karuta.matchtracker.dto.MatchPairingCreateRequest;
import com.karuta.matchtracker.dto.MatchPairingDto;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchPairingService 単体テスト")
class MatchPairingServiceTest {

    @Mock
    private MatchPairingRepository matchPairingRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private MatchPairingService matchPairingService;

    @Captor
    private ArgumentCaptor<MatchPairing> matchPairingCaptor;

    @Captor
    private ArgumentCaptor<List<MatchPairing>> matchPairingListCaptor;

    private Player player1;
    private Player player2;
    private Player player3;
    private Player player4;

    @BeforeEach
    void setUp() {
        player1 = createPlayer(1L, "選手A");
        player2 = createPlayer(2L, "選手B");
        player3 = createPlayer(3L, "選手C");
        player4 = createPlayer(4L, "選手D");
    }

    @Nested
    @DisplayName("getByDate メソッド")
    class GetByDateTests {

        @Test
        @DisplayName("指定日付の対戦ペアリングを全て取得できる")
        void shouldGetAllPairingsByDate() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            List<MatchPairing> pairings = Arrays.asList(
                    createMatchPairing(1L, sessionDate, 1, 1L, 2L),
                    createMatchPairing(2L, sessionDate, 2, 3L, 4L)
            );

            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(pairings);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(player3));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(player4));

            // When
            List<MatchPairingDto> result = matchPairingService.getByDate(sessionDate);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSessionDate()).isEqualTo(sessionDate);
            assertThat(result.get(0).getMatchNumber()).isEqualTo(1);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
            assertThat(result.get(1).getMatchNumber()).isEqualTo(2);

            verify(matchPairingRepository).findBySessionDateOrderByMatchNumber(sessionDate);
        }

        @Test
        @DisplayName("対戦ペアリングが存在しない場合は空のリストを返す")
        void shouldReturnEmptyListWhenNoPairings() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result = matchPairingService.getByDate(sessionDate);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByDateAndMatchNumber メソッド")
    class GetByDateAndMatchNumberTests {

        @Test
        @DisplayName("指定日付と試合番号の対戦ペアリングを取得できる")
        void shouldGetPairingByDateAndMatchNumber() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 3;
            MatchPairing pairing = createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L);

            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(pairing));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));

            // When
            List<MatchPairingDto> result = matchPairingService.getByDateAndMatchNumber(sessionDate, matchNumber);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSessionDate()).isEqualTo(sessionDate);
            assertThat(result.get(0).getMatchNumber()).isEqualTo(matchNumber);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("対戦ペアリングが見つからない場合は空のリストを返す")
        void shouldReturnEmptyListWhenPairingNotFound() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 99;
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result = matchPairingService.getByDateAndMatchNumber(sessionDate, matchNumber);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create メソッド")
    class CreateTests {

        @Test
        @DisplayName("対戦ペアリングを作成できる")
        void shouldCreatePairing() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long player1Id = 1L;
            Long player2Id = 2L;
            Long createdBy = 1L;

            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    sessionDate, matchNumber, player1Id, player2Id
            );

            MatchPairing savedPairing = createMatchPairing(1L, sessionDate, matchNumber, player1Id, player2Id);
            when(matchPairingRepository.save(any(MatchPairing.class))).thenReturn(savedPairing);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));

            // When
            MatchPairingDto result = matchPairingService.create(request, createdBy);

            // Then
            assertThat(result.getSessionDate()).isEqualTo(sessionDate);
            assertThat(result.getMatchNumber()).isEqualTo(matchNumber);
            assertThat(result.getPlayer1Id()).isEqualTo(player1Id);
            assertThat(result.getPlayer2Id()).isEqualTo(player2Id);

            verify(matchPairingRepository).save(matchPairingCaptor.capture());
            MatchPairing captured = matchPairingCaptor.getValue();
            assertThat(captured.getSessionDate()).isEqualTo(sessionDate);
            assertThat(captured.getMatchNumber()).isEqualTo(matchNumber);
            assertThat(captured.getPlayer1Id()).isEqualTo(player1Id);
            assertThat(captured.getPlayer2Id()).isEqualTo(player2Id);
            assertThat(captured.getCreatedBy()).isEqualTo(createdBy);
        }

        @Test
        @DisplayName("同じ選手同士の対戦の場合はIllegalArgumentExceptionをスローする")
        void shouldThrowExceptionWhenSamePlayer() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long playerId = 1L;

            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    sessionDate, matchNumber, playerId, playerId
            );

            // When & Then
            assertThatThrownBy(() -> matchPairingService.create(request, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("同じ選手を対戦相手に設定できません");

            verify(matchPairingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("createBatch メソッド")
    class CreateBatchTests {

        @Test
        @DisplayName("対戦ペアリングを一括作成できる")
        void shouldCreatePairingsBatch() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long createdBy = 1L;

            List<MatchPairingCreateRequest> requests = Arrays.asList(
                    new MatchPairingCreateRequest(sessionDate, matchNumber, 1L, 2L),
                    new MatchPairingCreateRequest(sessionDate, matchNumber, 3L, 4L)
            );

            List<MatchPairing> savedPairings = Arrays.asList(
                    createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L),
                    createMatchPairing(2L, sessionDate, matchNumber, 3L, 4L)
            );

            when(matchPairingRepository.saveAll(anyList())).thenReturn(savedPairings);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
            when(playerRepository.findById(3L)).thenReturn(Optional.of(player3));
            when(playerRepository.findById(4L)).thenReturn(Optional.of(player4));

            // When
            List<MatchPairingDto> result = matchPairingService.createBatch(sessionDate, matchNumber, requests, createdBy);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getMatchNumber()).isEqualTo(matchNumber);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
            assertThat(result.get(1).getMatchNumber()).isEqualTo(matchNumber);
            assertThat(result.get(1).getPlayer1Id()).isEqualTo(3L);
            assertThat(result.get(1).getPlayer2Id()).isEqualTo(4L);

            verify(matchPairingRepository).deleteBySessionDateAndMatchNumber(sessionDate, matchNumber);
            verify(matchPairingRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("空のリストで一括作成すると空のリストを返す")
        void shouldReturnEmptyListForEmptyBatch() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long createdBy = 1L;
            List<MatchPairingCreateRequest> requests = Collections.emptyList();

            when(matchPairingRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result = matchPairingService.createBatch(sessionDate, matchNumber, requests, createdBy);

            // Then
            assertThat(result).isEmpty();
            verify(matchPairingRepository).deleteBySessionDateAndMatchNumber(sessionDate, matchNumber);
        }
    }

    @Nested
    @DisplayName("delete メソッド")
    class DeleteTests {

        @Test
        @DisplayName("IDで対戦ペアリングを削除できる")
        void shouldDeletePairingById() {
            // Given
            Long pairingId = 1L;

            // When
            matchPairingService.delete(pairingId);

            // Then
            verify(matchPairingRepository).deleteById(pairingId);
        }
    }

    @Nested
    @DisplayName("deleteByDateAndMatchNumber メソッド")
    class DeleteByDateAndMatchNumberTests {

        @Test
        @DisplayName("日付と試合番号で対戦ペアリングを削除できる")
        void shouldDeletePairingByDateAndMatchNumber() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;

            // When
            matchPairingService.deleteByDateAndMatchNumber(sessionDate, matchNumber);

            // Then
            verify(matchPairingRepository).deleteBySessionDateAndMatchNumber(sessionDate, matchNumber);
        }
    }

    @Nested
    @DisplayName("existsByDateAndMatchNumber メソッド")
    class ExistsByDateAndMatchNumberTests {

        @Test
        @DisplayName("対戦ペアリングが存在する場合trueを返す")
        void shouldReturnTrueWhenPairingExists() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            when(matchPairingRepository.existsBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(true);

            // When
            boolean result = matchPairingService.existsByDateAndMatchNumber(sessionDate, matchNumber);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("対戦ペアリングが存在しない場合falseを返す")
        void shouldReturnFalseWhenPairingDoesNotExist() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            when(matchPairingRepository.existsBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(false);

            // When
            boolean result = matchPairingService.existsByDateAndMatchNumber(sessionDate, matchNumber);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("autoMatch メソッド")
    class AutoMatchTests {

        @Test
        @DisplayName("偶数の参加者で自動マッチングできる")
        void shouldAutoMatchEvenNumberOfPlayers() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<Long> playerIds = Arrays.asList(1L, 2L, 3L, 4L);

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .participantIds(playerIds)
                    .build();

            when(playerRepository.findAllById(playerIds))
                    .thenReturn(Arrays.asList(player1, player2, player3, player4));
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request);

            // Then
            assertThat(result.getPairings()).hasSize(2);
            assertThat(result.getWaitingPlayers()).isEmpty();
        }

        @Test
        @DisplayName("奇数の参加者で自動マッチングすると1人が待機者になる")
        void shouldAutoMatchOddNumberOfPlayersWithOneWaiting() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<Long> playerIds = Arrays.asList(1L, 2L, 3L);

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .participantIds(playerIds)
                    .build();

            when(playerRepository.findAllById(playerIds))
                    .thenReturn(Arrays.asList(player1, player2, player3));
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request);

            // Then
            assertThat(result.getPairings()).hasSize(1);
            assertThat(result.getWaitingPlayers()).hasSize(1);
        }

        @Test
        @DisplayName("参加者が1人の場合はペアリングできない")
        void shouldReturnNoPairingsForSinglePlayer() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<Long> playerIds = Arrays.asList(1L);

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .participantIds(playerIds)
                    .build();

            when(playerRepository.findAllById(playerIds))
                    .thenReturn(Arrays.asList(player1));
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request);

            // Then
            assertThat(result.getPairings()).isEmpty();
            assertThat(result.getWaitingPlayers()).hasSize(1);
        }

        @Test
        @DisplayName("参加者が0人の場合は空の結果を返す")
        void shouldReturnEmptyResultForNoPlayers() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<Long> playerIds = Collections.emptyList();

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .participantIds(playerIds)
                    .build();

            when(playerRepository.findAllById(playerIds))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request);

            // Then
            assertThat(result.getPairings()).isEmpty();
            assertThat(result.getWaitingPlayers()).isEmpty();
        }
    }

    // ヘルパーメソッド

    private Player createPlayer(Long id, String name) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setPassword("password");
        player.setGender(Player.Gender.その他);
        player.setDominantHand(Player.DominantHand.右);
        player.setRole(Player.Role.PLAYER);
        return player;
    }

    private MatchPairing createMatchPairing(Long id, LocalDate sessionDate, Integer matchNumber,
                                            Long player1Id, Long player2Id) {
        MatchPairing pairing = new MatchPairing();
        pairing.setId(id);
        pairing.setSessionDate(sessionDate);
        pairing.setMatchNumber(matchNumber);
        pairing.setPlayer1Id(player1Id);
        pairing.setPlayer2Id(player2Id);
        pairing.setCreatedBy(1L);
        return pairing;
    }
}
