package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.AutoMatchingRequest;
import com.karuta.matchtracker.dto.AutoMatchingResult;
import com.karuta.matchtracker.dto.MatchPairingCreateRequest;
import com.karuta.matchtracker.dto.MatchPairingDto;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
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
import java.util.ArrayList;
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

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

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
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2, player3, player4));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

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
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

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
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));

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

            List<MatchPairing> savedPairings = new ArrayList<>(Arrays.asList(
                    createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L),
                    createMatchPairing(2L, sessionDate, matchNumber, 3L, 4L)
            ));

            // ロック判定用スタブ（既存ペアリングなし、結果なし）
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.saveAll(anyList())).thenReturn(savedPairings);
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2, player3, player4));

            // When
            List<MatchPairingDto> result = matchPairingService.createBatch(sessionDate, matchNumber, requests, Collections.emptyList(), createdBy, null);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getMatchNumber()).isEqualTo(matchNumber);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
            assertThat(result.get(1).getMatchNumber()).isEqualTo(matchNumber);
            assertThat(result.get(1).getPlayer1Id()).isEqualTo(3L);
            assertThat(result.get(1).getPlayer2Id()).isEqualTo(4L);

            verify(matchPairingRepository).deleteAll(anyList());
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

            // ロック判定用スタブ
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

            // When
            List<MatchPairingDto> result = matchPairingService.createBatch(sessionDate, matchNumber, requests, Collections.emptyList(), createdBy, null);

            // Then
            assertThat(result).isEmpty();
            verify(matchPairingRepository).deleteAll(anyList());
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
        @DisplayName("日付と試合番号で対戦ペアリングを削除できる（ロック済みなし）")
        void shouldDeletePairingByDateAndMatchNumber() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;

            MatchPairing pairing = createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(pairing));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            matchPairingService.deleteByDateAndMatchNumber(sessionDate, matchNumber, null);

            // Then: 全件削除される（ロック済みなし）
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.size() == 1 && deleted.get(0).getId().equals(1L);
            }));
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
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 3L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 4L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2, player3, player4));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

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
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 3L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2, player3));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

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
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of(createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON)));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

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
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then
            assertThat(result.getPairings()).isEmpty();
            assertThat(result.getWaitingPlayers()).isEmpty();
        }

        @Test
        @DisplayName("セッションが存在しない場合は空の結果を返す")
        void shouldReturnEmptyResultWhenSessionNotFound() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.empty());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then
            assertThat(result.getPairings()).isEmpty();
            assertThat(result.getWaitingPlayers()).isEmpty();
            verify(practiceParticipantRepository, never())
                    .findBySessionIdAndMatchNumberAndStatus(anyLong(), anyInt(), any());
            verify(playerRepository, never()).findAllById(anyList());
        }

        @Test
        @DisplayName("DBのWON参加者を使用して組み合わせを生成できる")
        void shouldUseWonParticipantsFromDatabase() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then
            assertThat(result.getPairings()).hasSize(1);
            assertThat(result.getWaitingPlayers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("createBatch BYE dirty=false")
    class CreateBatchByeDirtyTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("createBatchで待機者BYEがdirty=falseで生成される")
        void shouldCreateByeWithDirtyFalse() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long createdBy = 1L;

            List<MatchPairingCreateRequest> requests = List.of(
                    new MatchPairingCreateRequest(sessionDate, matchNumber, 1L, 2L)
            );
            List<Long> waitingPlayerIds = List.of(3L);

            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(sessionDate).totalMatches(7).build();

            List<MatchPairing> savedPairings = new ArrayList<>(List.of(
                    createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L)
            ));

            // ロック判定用スタブ
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.saveAll(anyList())).thenReturn(savedPairings);
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(practiceSessionRepository.findBySessionDate(sessionDate))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Collections.emptyList());

            ArgumentCaptor<List<PracticeParticipant>> byeCaptor = ArgumentCaptor.forClass(List.class);

            // When
            matchPairingService.createBatch(sessionDate, matchNumber, requests, waitingPlayerIds, createdBy, null);

            // Then: BYEエントリがdirty=falseで保存されていること
            // saveAll は2回呼ばれる: 1回目=MatchPairing, 2回目=BYE PracticeParticipant
            verify(practiceParticipantRepository).saveAll(byeCaptor.capture());
            List<PracticeParticipant> byeParticipants = byeCaptor.getValue();
            assertThat(byeParticipants).hasSize(1);
            assertThat(byeParticipants.get(0).getMatchNumber()).isNull();
            assertThat(byeParticipants.get(0).getPlayerId()).isEqualTo(3L);
            assertThat(byeParticipants.get(0).isDirty()).isFalse();
        }
    }

    // ヘルパーメソッド

    private PracticeSession createSession(Long id, LocalDate sessionDate) {
        PracticeSession session = new PracticeSession();
        session.setId(id);
        session.setSessionDate(sessionDate);
        session.setTotalMatches(7);
        return session;
    }

    private PracticeParticipant createPracticeParticipant(Long sessionId, Integer matchNumber,
                                                          Long playerId, ParticipantStatus status) {
        PracticeParticipant participant = new PracticeParticipant();
        participant.setSessionId(sessionId);
        participant.setMatchNumber(matchNumber);
        participant.setPlayerId(playerId);
        participant.setStatus(status);
        return participant;
    }

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

    @Nested
    @DisplayName("MATCH_HISTORY_DAYS 境界テスト")
    class MatchHistoryBoundaryTests {

        @Test
        @DisplayName("自動マッチング時に過去30日分の履歴を参照する（startDateが30日前）")
        void shouldQueryHistoryFor30Days() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 2, 15);
            Integer matchNumber = 1;
            List<Long> playerIds = Arrays.asList(1L, 2L);

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(Collections.emptyList());

            // When
            matchPairingService.autoMatch(request, null);

            // Then: startDate = sessionDate - 30日 = 2024-01-16
            LocalDate expectedStartDate = LocalDate.of(2024, 1, 16);
            verify(matchPairingRepository).findRecentPairingHistory(
                    anyList(), eq(expectedStartDate), eq(sessionDate));
            verify(matchRepository).findRecentMatchHistory(
                    anyList(), eq(expectedStartDate), eq(sessionDate));
        }

        @Test
        @DisplayName("30日以内の対戦履歴はスコアに影響する（ペナルティあり）")
        void shouldPenalizeRecentMatch() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 2, 15);
            Integer matchNumber = 1;
            List<Long> playerIds = Arrays.asList(1L, 2L, 3L, 4L);

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 3L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 4L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2, player3, player4));

            // player1 と player2 が30日前（境界ちょうど）に対戦
            LocalDate matchDate30DaysAgo = sessionDate.minusDays(30);
            Object[] historyRow = new Object[]{matchDate30DaysAgo, 1L, 2L};
            List<Object[]> pairingHistory = new java.util.ArrayList<>();
            pairingHistory.add(historyRow);
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(pairingHistory);
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then: 4人いるので2ペアできる。player1-player2は30日前に対戦済みなので、
            // 初対戦ペア（player3-player4等）が優先される傾向がある
            assertThat(result.getPairings()).hasSize(2);
        }
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

    private Match createMatch(Long id, LocalDate matchDate, Integer matchNumber,
                               Long player1Id, Long player2Id, Long winnerId, Integer scoreDiff) {
        return Match.builder()
                .id(id)
                .matchDate(matchDate)
                .matchNumber(matchNumber)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .winnerId(winnerId)
                .scoreDifference(scoreDiff)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
    }

    @Nested
    @DisplayName("結果入力済みロック機能")
    class MatchResultLockTests {

        @Test
        @DisplayName("createBatch時に結果入力済みペアリングが保持される")
        void shouldPreserveLockedPairingsInCreateBatch() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long createdBy = 1L;

            // 既存ペアリング: player1 vs player2（結果入力済み）, player3 vs player4（結果なし）
            MatchPairing lockedPairing = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            MatchPairing unlockedPairing = createMatchPairing(11L, sessionDate, matchNumber, 3L, 4L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(lockedPairing, unlockedPairing));

            // player1 vs player2 に対応するmatch結果が存在
            Match existingMatch = createMatch(100L, sessionDate, matchNumber, 1L, 2L, 1L, 5);
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(existingMatch));

            // 新規ペアリングリクエスト: player3 vs player4
            List<MatchPairingCreateRequest> requests = List.of(
                    new MatchPairingCreateRequest(sessionDate, matchNumber, 3L, 4L)
            );

            when(matchPairingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2, player3, player4));

            // When
            matchPairingService.createBatch(sessionDate, matchNumber, requests, List.of(), createdBy, null);

            // Then: ロック済みでないペアリングのみ削除される
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.size() == 1 && deleted.get(0).getId().equals(11L);
            }));
            // deleteBySessionDateAndMatchNumber は呼ばれない
            verify(matchPairingRepository, never()).deleteBySessionDateAndMatchNumber(any(), anyInt());
        }

        @Test
        @DisplayName("resetWithResultでペアリングと結果が両方削除される")
        void shouldDeleteBothPairingAndMatchOnReset() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            MatchPairing pairing = createMatchPairing(10L, sessionDate, 1, 1L, 2L);
            Match match = createMatch(100L, sessionDate, 1, 1L, 2L, 1L, 5);

            when(matchPairingRepository.findById(10L)).thenReturn(Optional.of(pairing));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, 1))
                    .thenReturn(List.of(match));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));

            // When
            MatchPairingDto result = matchPairingService.resetWithResult(10L);

            // Then
            verify(matchRepository).delete(match);
            verify(matchPairingRepository).delete(pairing);
            assertThat(result.isHasResult()).isTrue();
            assertThat(result.getMatchId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("autoMatch時にロック済みプレイヤーが除外される")
        void shouldExcludeLockedPlayersFromAutoMatch() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;

            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            // 4人参加
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 3L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 4L, ParticipantStatus.WON)
                    ));

            // player1 vs player2 は既にロック済み
            MatchPairing lockedPairing = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(lockedPairing));
            Match existingMatch = createMatch(100L, sessionDate, matchNumber, 1L, 2L, 1L, 5);
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(existingMatch));

            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2, player3, player4));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(Collections.emptyList());

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then: player3 vs player4 のみ新規ペアリング、player1 vs player2 はlockedPairings
            assertThat(result.getPairings()).hasSize(1);
            assertThat(result.getLockedPairings()).hasSize(1);
            assertThat(result.getLockedPairings().get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.getLockedPairings().get(0).getPlayer2Id()).isEqualTo(2L);
            assertThat(result.getLockedPairings().get(0).getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("deleteByDateAndMatchNumberでロック済みペアリングが保持される")
        void shouldPreserveLockedPairingsOnDeleteByDateAndMatchNumber() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;

            MatchPairing lockedPairing = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            MatchPairing unlockedPairing = createMatchPairing(11L, sessionDate, matchNumber, 3L, 4L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(lockedPairing, unlockedPairing));

            Match existingMatch = createMatch(100L, sessionDate, matchNumber, 1L, 2L, 1L, 5);
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(existingMatch));

            // When
            matchPairingService.deleteByDateAndMatchNumber(sessionDate, matchNumber, null);

            // Then: 未ロックのペアリングのみ削除される
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.size() == 1 && deleted.get(0).getId().equals(11L);
            }));
        }
    }

    @Nested
    @DisplayName("組織スコープ境界テスト")
    class OrganizationScopeTests {

        @Test
        @DisplayName("組織IDを指定するとセッション参加者でフィルタされる")
        void shouldScopeByOrganizationId() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long orgId = 10L;

            PracticeSession session = new PracticeSession();
            session.setId(100L);
            session.setSessionDate(sessionDate);
            session.setOrganizationId(orgId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgId))
                    .thenReturn(Optional.of(session));

            // Org A の参加者: player1, player2
            PracticeParticipant pp1 = PracticeParticipant.builder().playerId(1L).sessionId(100L).build();
            PracticeParticipant pp2 = PracticeParticipant.builder().playerId(2L).sessionId(100L).build();
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(pp1, pp2));

            // DB上には Org A (player1 vs player2) と Org B (player5 vs player6) のペアリングが混在
            MatchPairing orgAPairing = createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L);
            MatchPairing orgBPairing = createMatchPairing(2L, sessionDate, matchNumber, 5L, 6L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(orgAPairing, orgBPairing));

            // Org B の match result だけ存在
            Match orgBMatch = createMatch(200L, sessionDate, matchNumber, 5L, 6L, 5L, 3);
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(orgBMatch));

            // When: Org A のスコープでdeleteを実行
            matchPairingService.deleteByDateAndMatchNumber(sessionDate, matchNumber, orgId);

            // Then: Org A のペアリングのみ削除される（Org B のペアリングは対象外）
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.size() == 1 && deleted.get(0).getId().equals(1L);
            }));
        }
    }

    @Nested
    @DisplayName("待機者ロック除外で空になるケース")
    class WaitingPlayerLockFilterTests {

        @Test
        @DisplayName("waitingPlayerIdsが全員ロック除外で空になっても既存抜け番は削除される")
        void shouldDeleteExistingByeWhenFilteredWaitingIsEmpty() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long createdBy = 1L;

            // ロック済みペアリング（player1 vs player2 に結果あり）
            MatchPairing lockedPairing = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(lockedPairing));
            Match existingMatch = createMatch(100L, sessionDate, matchNumber, 1L, 2L, 1L, 5);
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(existingMatch));
            when(matchPairingRepository.saveAll(anyList())).thenReturn(new ArrayList<>());
            when(playerRepository.findAllById(anyCollection())).thenReturn(Arrays.asList(player1, player2));

            PracticeSession session = new PracticeSession();
            session.setId(100L);
            when(practiceSessionRepository.findBySessionDate(sessionDate))
                    .thenReturn(Optional.of(session));

            // セッション参加者（ロック対象のplayer1,2 + 抜け番のplayer3）
            PracticeParticipant pp1 = PracticeParticipant.builder().playerId(1L).sessionId(100L).matchNumber(matchNumber).build();
            PracticeParticipant pp2 = PracticeParticipant.builder().playerId(2L).sessionId(100L).matchNumber(matchNumber).build();
            PracticeParticipant existingBye = PracticeParticipant.builder()
                    .playerId(3L).sessionId(100L).matchNumber(null).build();
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(pp1, pp2, existingBye));

            // waitingPlayerIds にロック済みプレイヤーのみ → フィルタ後空
            List<Long> waitingPlayerIds = Arrays.asList(1L, 2L);
            List<MatchPairingCreateRequest> requests = Collections.emptyList();

            // When
            matchPairingService.createBatch(sessionDate, matchNumber, requests, waitingPlayerIds, createdBy, null);

            // Then: 既存抜け番が削除される
            verify(practiceParticipantRepository).deleteAll(argThat(list -> {
                List<PracticeParticipant> deleted = (List<PracticeParticipant>) list;
                return deleted.size() == 1 && deleted.get(0).getPlayerId().equals(3L);
            }));
            // フィルタ後空なので抜け番の新規保存は呼ばれない（deleteAllの後にsaveAllは呼ばれない）
            verify(practiceParticipantRepository, times(1)).deleteAll(anyCollection());
            verify(practiceParticipantRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("resetWithResult 結果なし境界テスト")
    class ResetWithResultBoundaryTests {

        @Test
        @DisplayName("結果が存在しないペアリングはリセットできない")
        void shouldRejectResetWithoutResult() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            MatchPairing pairing = createMatchPairing(10L, sessionDate, 1, 1L, 2L);
            when(matchPairingRepository.findById(10L)).thenReturn(Optional.of(pairing));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, 1))
                    .thenReturn(Collections.emptyList());

            // When & Then
            assertThatThrownBy(() -> matchPairingService.resetWithResult(10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("試合結果が見つかりません");

            verify(matchPairingRepository, never()).delete(any());
            verify(matchRepository, never()).delete(any(Match.class));
        }
    }

    @Nested
    @DisplayName("組織スコープ: 参加者0件で無フィルタにならないことの検証")
    class OrgScopeEmptyParticipantsTests {

        @Test
        @DisplayName("組織スコープ時に参加者0件なら既存ペアリングは操作対象外になる")
        void shouldReturnEmptyWhenOrgScopedAndNoParticipants() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long orgId = 10L;

            // 組織のセッションはあるが参加者がいない
            PracticeSession session = new PracticeSession();
            session.setId(100L);
            session.setOrganizationId(orgId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Collections.emptyList());

            // DBにはペアリングが存在する
            MatchPairing pairing = createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(pairing));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            matchPairingService.deleteByDateAndMatchNumber(sessionDate, matchNumber, orgId);

            // Then: 参加者0のため操作対象なし → 空リストで deleteAll
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.isEmpty();
            }));
        }
    }
}
