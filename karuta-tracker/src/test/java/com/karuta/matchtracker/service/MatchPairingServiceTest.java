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
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
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

    @Mock
    private LotteryDeadlineHelper lotteryDeadlineHelper;

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

        @Test
        @DisplayName("organizationId 指定時は、当該団体のセッション参加者に紐づくペアリングのみを返す")
        void shouldFilterPairingsByOrganizationScope() {
            // Given: 同日に2団体のペアリングが混在する状況。
            // organizationId=7L のセッション参加者は player1Id=1L, player2Id=2L のみ。
            // 別団体ペアリング (player1Id=100, player2Id=200) は除外されるべき。
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long organizationId = 7L;
            List<MatchPairing> pairings = Arrays.asList(
                    createMatchPairing(1L, sessionDate, 1, 1L, 2L),     // 自団体
                    createMatchPairing(2L, sessionDate, 2, 100L, 200L)  // 別団体
            );
            PracticeSession orgSession = createSession(100L, sessionDate);
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(pairings);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(orgSession));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result = matchPairingService.getByDate(sessionDate, false, organizationId);

            // Then: 自団体ペアリング1件のみが返る
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("organizationId 指定で対象セッション未登録なら空リストを返す（無フィルタにフォールバックしない）")
        void shouldReturnEmptyWhenOrganizationSessionMissing() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long organizationId = 7L;
            List<MatchPairing> pairings = Arrays.asList(
                    createMatchPairing(1L, sessionDate, 1, 1L, 2L)
            );
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(pairings);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.empty());

            // When
            List<MatchPairingDto> result = matchPairingService.getByDate(sessionDate, false, organizationId);

            // Then: 別団体ペアリングが混入してはいけない
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("organizationId 指定時は、片方の選手だけが自団体セッション参加者でも除外する（AND 条件）")
        void shouldExcludePairingsWhenOnlyOnePlayerInOrgSession() {
            // Given: 共有選手 1L のみが自団体セッション参加。
            //   pairing1: (1L, 2L) - 1L のみ自団体 → AND だと除外
            //   pairing2: (1L, 10L) - 1L のみ自団体 → AND だと除外
            //   pairing3: (10L, 20L) - 両方とも別団体 → 除外
            // 旧 OR 条件では pairing1 と pairing2 が通過していたが、
            // AND 条件では3つとも除外され、共有選手を含む別団体ペアリング混入を防ぐ。
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long organizationId = 7L;
            List<MatchPairing> pairings = Arrays.asList(
                    createMatchPairing(1L, sessionDate, 1, 1L, 2L),
                    createMatchPairing(2L, sessionDate, 2, 1L, 10L),
                    createMatchPairing(3L, sessionDate, 3, 10L, 20L)
            );
            PracticeSession orgSession = createSession(100L, sessionDate);
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(pairings);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(orgSession));
            // 自団体セッション参加者は 1L のみ（他は別団体）
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(List.of(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON)
                    ));

            // When
            List<MatchPairingDto> result = matchPairingService.getByDate(sessionDate, false, organizationId);

            // Then: AND 条件で全件除外
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRecentByPlayerId メソッド（選手起点の最近ペアリング）")
    class GetRecentByPlayerIdTests {

        @Test
        @DisplayName("指定選手が含まれる最近のペアリングを選手名付きで返す")
        void shouldReturnRecentPairingsWithPlayerNames() {
            // Given
            Long playerId = 1L;
            List<MatchPairing> pairings = Arrays.asList(
                    createMatchPairing(10L, LocalDate.of(2024, 3, 2), 3, 1L, 2L),
                    createMatchPairing(11L, LocalDate.of(2024, 3, 1), 1, 3L, 1L)
            );
            when(matchPairingRepository.findRecentByPlayerId(eq(playerId), any()))
                    .thenReturn(pairings);
            when(playerRepository.findAllById(anyList()))
                    .thenReturn(Arrays.asList(player1, player2, player3));

            // When
            List<MatchPairingDto> result = matchPairingService.getRecentByPlayerId(playerId);

            // Then: 選手名が解決され、リポジトリの並び順がそのまま維持される
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(10L);
            assertThat(result.get(0).getSessionDate()).isEqualTo(LocalDate.of(2024, 3, 2));
            assertThat(result.get(0).getMatchNumber()).isEqualTo(3);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer1Name()).isEqualTo("選手A");
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
            assertThat(result.get(0).getPlayer2Name()).isEqualTo("選手B");
            assertThat(result.get(1).getId()).isEqualTo(11L);
            assertThat(result.get(1).getPlayer1Name()).isEqualTo("選手C");
            assertThat(result.get(1).getPlayer2Name()).isEqualTo("選手A");
        }

        @Test
        @DisplayName("選手名解決は一括取得（findAllById）で行いN+1を避ける")
        void shouldResolvePlayerNamesInBatch() {
            // Given
            Long playerId = 1L;
            when(matchPairingRepository.findRecentByPlayerId(eq(playerId), any()))
                    .thenReturn(Arrays.asList(
                            createMatchPairing(10L, LocalDate.of(2024, 3, 2), 3, 1L, 2L),
                            createMatchPairing(11L, LocalDate.of(2024, 3, 1), 1, 1L, 3L)
                    ));
            when(playerRepository.findAllById(anyList()))
                    .thenReturn(Arrays.asList(player1, player2, player3));

            // When
            matchPairingService.getRecentByPlayerId(playerId);

            // Then: 選手名解決は findAllById 1回のみ（個別 findById を使わない）
            verify(playerRepository, times(1)).findAllById(anyList());
            verify(playerRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("最大30件で制限して取得する（Pageable のサイズ検証）")
        void shouldLimitToThirtyRecords() {
            // Given
            Long playerId = 1L;
            when(matchPairingRepository.findRecentByPlayerId(eq(playerId), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchPairingService.getRecentByPlayerId(playerId);

            // Then: PageRequest(0, 30) で呼ばれる
            ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                    ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            verify(matchPairingRepository).findRecentByPlayerId(eq(playerId), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
        }

        @Test
        @DisplayName("ペアリングが無い選手は空リストを返す")
        void shouldReturnEmptyListWhenNoPairings() {
            // Given
            Long playerId = 999L;
            when(matchPairingRepository.findRecentByPlayerId(eq(playerId), any()))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result = matchPairingService.getRecentByPlayerId(playerId);

            // Then: 個別 findById は使わない（N+1回避）。collectPlayerNames は空リストで呼ばれ得るため
            // findAllById の呼び出し自体は許容する。
            assertThat(result).isEmpty();
            verify(playerRepository, never()).findById(anyLong());
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

        @Test
        @DisplayName("organizationId 指定時は、当該団体のセッション参加者に紐づくペアリングのみを返す")
        void shouldFilterPairingByOrganizationScope() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long organizationId = 7L;
            List<MatchPairing> pairings = Arrays.asList(
                    createMatchPairing(1L, sessionDate, matchNumber, 1L, 2L),
                    createMatchPairing(2L, sessionDate, matchNumber, 100L, 200L)
            );
            PracticeSession orgSession = createSession(100L, sessionDate);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(pairings);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(orgSession));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result = matchPairingService.getByDateAndMatchNumber(sessionDate, matchNumber, organizationId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer1Id()).isEqualTo(1L);
            assertThat(result.get(0).getPlayer2Id()).isEqualTo(2L);
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
                    .findBySessionIdAndMatchNumberAndStatusIn(anyLong(), anyInt(), any());
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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

        @Test
        @DisplayName("抽選あり運用 (isLotteryDisabled=false): WON のみを組み合わせ対象とし PENDING は除外する")
        void shouldExcludePendingWhenLotteryEnabled() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long organizationId = 7L;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(lotteryDeadlineHelper.isLotteryDisabled(organizationId)).thenReturn(false);
            // organizationId 指定時は組織スコープでのセッション取得が使われる
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)
                    ));
            when(practiceParticipantRepository.findBySessionId(100L))
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
            AutoMatchingResult result = matchPairingService.autoMatch(request, organizationId);

            // Then: WON 2人だけがマッチング対象、PENDING を含む List.of(WON,PENDING) では呼ばれない
            assertThat(result.getPairings()).hasSize(1);
            verify(practiceParticipantRepository).findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON));
            verify(practiceParticipantRepository, never())
                    .findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.PENDING, ParticipantStatus.WON));
            // 組織スコープでセッション取得していること、無スコープ版は呼ばれないことを確認
            // (loadActiveParticipantIdsForMatch と getSessionAllPlayerIds の両方から呼ばれる)
            verify(practiceSessionRepository, atLeastOnce())
                    .findBySessionDateAndOrganizationId(sessionDate, organizationId);
            verify(practiceSessionRepository, never()).findBySessionDate(sessionDate);
        }

        @Test
        @DisplayName("抽選なし運用 (isLotteryDisabled=true): PENDING も組み合わせ対象に含める")
        void shouldIncludePendingWhenLotteryDisabled() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long organizationId = 8L;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(lotteryDeadlineHelper.isLotteryDisabled(organizationId)).thenReturn(true);
            // organizationId 指定時は組織スコープでのセッション取得が使われる
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.PENDING, ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.PENDING),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.PENDING)
                    ));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.PENDING),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.PENDING)
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
            AutoMatchingResult result = matchPairingService.autoMatch(request, organizationId);

            // Then: PENDING 2人もマッチング対象になる
            assertThat(result.getPairings()).hasSize(1);
            verify(practiceParticipantRepository)
                    .findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.PENDING, ParticipantStatus.WON));
            // 組織スコープでセッション取得していること、無スコープ版は呼ばれないことを確認
            // (loadActiveParticipantIdsForMatch と getSessionAllPlayerIds の両方から呼ばれる)
            verify(practiceSessionRepository, atLeastOnce())
                    .findBySessionDateAndOrganizationId(sessionDate, organizationId);
            verify(practiceSessionRepository, never()).findBySessionDate(sessionDate);
        }

        @Test
        @DisplayName("同日に複数団体のセッションがあっても、autoMatch は organizationId 側のセッション参加者のみを対象にする")
        void shouldUseOrganizationScopedSessionWhenMultipleOrgsShareSameDate() {
            // Given: 同じ日付に2団体のセッションがあり、リクエストは organizationId=7L のみを対象とする
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long organizationId = 7L;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession orgSession = createSession(100L, sessionDate);
            when(lotteryDeadlineHelper.isLotteryDisabled(organizationId)).thenReturn(false);
            // 組織スコープのセッション取得のみが使われ、別団体のセッション (sessionId=200L 等) は混入しない
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(orgSession));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)
                    ));
            when(practiceParticipantRepository.findBySessionId(100L))
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
            AutoMatchingResult result = matchPairingService.autoMatch(request, organizationId);

            // Then: 組織スコープ取得のみが使われる
            assertThat(result.getPairings()).hasSize(1);
            verify(practiceSessionRepository, atLeastOnce())
                    .findBySessionDateAndOrganizationId(sessionDate, organizationId);
            verify(practiceSessionRepository, never()).findBySessionDate(sessionDate);
            // 別団体のセッション (例: sessionId=200L) が紛れ込んでいないこと
            verify(practiceParticipantRepository, never())
                    .findBySessionIdAndMatchNumberAndStatusIn(eq(200L), anyInt(), anyList());
        }

        @Test
        @DisplayName("SUPER_ADMIN 経路 (organizationId=null): セッションの組織IDで isLotteryDisabled を判定し、抽選なし運用なら PENDING を含める")
        void shouldUseSessionOrgIdForSuperAdminPath() {
            // Given: SUPER_ADMIN が autoMatch を呼び、organizationId=null を渡す。
            // セッション側に組織ID (8L: 抽選なし運用) があるので、そちらで判定されるべき。
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long sessionOrgId = 8L;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(matchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            session.setOrganizationId(sessionOrgId);

            // 抽選なし運用団体: isLotteryDisabled(8L) = true
            when(lotteryDeadlineHelper.isLotteryDisabled(sessionOrgId)).thenReturn(true);
            // SUPER_ADMIN 経路では organizationId=null で findBySessionDate が呼ばれる
            when(practiceSessionRepository.findBySessionDate(sessionDate))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(
                    100L, 1, List.of(ParticipantStatus.PENDING, ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.PENDING),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.PENDING)
                    ));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.PENDING),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.PENDING)
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

            // Then: セッションの組織ID(8L)で isLotteryDisabled が判定され、PENDING を含む targetStatuses が使われる
            assertThat(result.getPairings()).hasSize(1);
            verify(practiceParticipantRepository)
                    .findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.PENDING, ParticipantStatus.WON));
            verify(practiceParticipantRepository, never())
                    .findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON));
            // セッションの組織ID(8L)で判定されたことを確認 (null では呼ばれない)
            verify(lotteryDeadlineHelper).isLotteryDisabled(sessionOrgId);
            verify(lotteryDeadlineHelper, never()).isLotteryDisabled(null);
        }

        @Test
        @DisplayName("同日複数団体・共有選手がいる場合、別団体の前試合ペアは todayMatches に混入しない（autoMatch 候補から誤除外しない）")
        void shouldExcludeCrossOrgPairsFromTodayMatches() {
            // Given: 同日に2団体のセッションがある。自団体 org=7L のセッション参加者は
            // 1L, 2L, 3L, 4L。共有選手 1L が別団体 org=8L の試合1にも参加し、別団体側
            // で (1L, 99L) のペアが存在する。
            // 自団体の試合2を autoMatch するとき、todayMatches に (1L, 99L) が混入
            // すると、99L はそもそも自団体外なので問題ないが、より重要なのは別団体の
            // ペアが「同日に組まれた」候補として算出され、ペア生成のスコア計算で
            // SAME_DAY_PENALTY_SCORE になりうる点。AND フィルタで除外される。
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatch = 2;
            Long organizationId = 7L;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(currentMatch)
                    .build();

            PracticeSession orgSession = createSession(100L, sessionDate);
            when(lotteryDeadlineHelper.isLotteryDisabled(organizationId)).thenReturn(false);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId))
                    .thenReturn(Optional.of(orgSession));
            // 自団体の試合2参加者: 1L, 2L
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(
                    100L, currentMatch, List.of(ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, currentMatch, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, currentMatch, 2L, ParticipantStatus.WON)
                    ));
            // 自団体セッションの全参加者: 1L, 2L, 3L, 4L
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, currentMatch, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, currentMatch, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 3L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 4L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            // 同日のペアリング: 試合1で別団体 (1L, 99L), 自団体内の (3L, 4L)
            // findBySessionDateAndMatchNumber は autoMatch のロック判定にも使われる
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, currentMatch))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(Arrays.asList(
                            createMatchPairing(10L, sessionDate, 1, 1L, 99L),  // 別団体: 99L は自団体不参加
                            createMatchPairing(11L, sessionDate, 1, 3L, 4L)    // 自団体
                    ));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, currentMatch))
                    .thenReturn(Collections.emptyList());
            // findTodayMatches も別団体の対戦結果を返す: (1L, 99L)
            List<Object[]> crossOrgTodayMatches = new ArrayList<>();
            crossOrgTodayMatches.add(new Object[]{1L, 99L});
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(crossOrgTodayMatches);

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, organizationId);

            // Then: 自団体の (1L, 2L) が候補から除外されてはいけない。
            // 別団体ペア (1L, 99L) は AND フィルタにより todayMatches/todayPairings に
            // 混入しないため、(1L, 2L) は通常通り組まれる。
            assertThat(result.getPairings()).hasSize(1);
            assertThat(result.getPairings().get(0).getPlayer1Id()).isIn(1L, 2L);
            assertThat(result.getPairings().get(0).getPlayer2Id()).isIn(1L, 2L);
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
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
    @DisplayName("手動ロック機能（pairing-manual-lock）")
    class ManualLockTests {

        @Test
        @DisplayName("lock: 二重ブッキングがなければ locked=true で保存する")
        void shouldLockPairing() {
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            MatchPairing pairing = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            when(matchPairingRepository.findById(10L)).thenReturn(Optional.of(pairing));
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(pairing));
            when(matchPairingRepository.save(any(MatchPairing.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));

            MatchPairingDto result = matchPairingService.lock(10L, null);

            assertThat(result.isLocked()).isTrue();
            verify(matchPairingRepository).save(matchPairingCaptor.capture());
            assertThat(matchPairingCaptor.getValue().getLocked()).isTrue();
        }

        @Test
        @DisplayName("lock: 同回戦の別組に対象選手が含まれる場合は DuplicateResourceException（保存しない）")
        void shouldRejectLockWhenDoubleBooking() {
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            MatchPairing target = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            // 別組 (2L, 3L) が選手2Lを共有 → 二重ブッキング
            MatchPairing other = createMatchPairing(11L, sessionDate, matchNumber, 2L, 3L);
            when(matchPairingRepository.findById(10L)).thenReturn(Optional.of(target));
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(target, other));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));

            assertThatThrownBy(() -> matchPairingService.lock(10L, null))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("選手B");

            verify(matchPairingRepository, never()).save(any());
        }

        @Test
        @DisplayName("lock: 存在しないIDは ResourceNotFoundException")
        void shouldThrowWhenLockMissingPairing() {
            when(matchPairingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchPairingService.lock(999L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(matchPairingRepository, never()).save(any());
        }

        @Test
        @DisplayName("unlock: locked=false で保存する（組は保持）")
        void shouldUnlockPairing() {
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            MatchPairing pairing = createMatchPairing(10L, sessionDate, 1, 1L, 2L);
            pairing.setLocked(true);
            when(matchPairingRepository.findById(10L)).thenReturn(Optional.of(pairing));
            when(matchPairingRepository.save(any(MatchPairing.class))).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));

            MatchPairingDto result = matchPairingService.unlock(10L);

            assertThat(result.isLocked()).isFalse();
            verify(matchPairingRepository).save(matchPairingCaptor.capture());
            assertThat(matchPairingCaptor.getValue().getLocked()).isFalse();
            verify(matchPairingRepository, never()).delete(any());
        }

        @Test
        @DisplayName("unlock: 存在しないIDは ResourceNotFoundException")
        void shouldThrowWhenUnlockMissingPairing() {
            when(matchPairingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchPairingService.unlock(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("createBatch: 手動ロック組（結果なし）が保持され、両選手が新規ペアから除外される")
        void shouldPreserveManuallyLockedPairingInCreateBatch() {
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long createdBy = 1L;

            MatchPairing manualLocked = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            manualLocked.setLocked(true);
            MatchPairing unlocked = createMatchPairing(11L, sessionDate, matchNumber, 3L, 4L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(manualLocked, unlocked));
            // 結果は存在しない（手動ロックのみ）
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
            // 新規リクエスト: ロック済み選手(1L)を含むペアは除外され、(3L,4L)のみ保存される
            List<MatchPairingCreateRequest> requests = Arrays.asList(
                    new MatchPairingCreateRequest(sessionDate, matchNumber, 1L, 5L),
                    new MatchPairingCreateRequest(sessionDate, matchNumber, 3L, 4L)
            );
            // saveAll はコピーを返す（本番の saved.addAll(lockedPairings) が引数リストを破壊的に
            // 変更し、検証時にサイズが変わるのを防ぐ）
            when(matchPairingRepository.saveAll(anyList())).thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2, player3, player4));

            matchPairingService.createBatch(sessionDate, matchNumber, requests, List.of(), createdBy, null);

            // 未ロックの既存ペア(11L)のみ削除
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.size() == 1 && deleted.get(0).getId().equals(11L);
            }));
            // ロック済み選手(1L)を含む新規ペアは除外され、(3L,4L)のみ保存
            verify(matchPairingRepository).saveAll(argThat(list -> {
                List<MatchPairing> saved = (List<MatchPairing>) list;
                return saved.size() == 1
                        && saved.get(0).getPlayer1Id().equals(3L)
                        && saved.get(0).getPlayer2Id().equals(4L);
            }));
        }

        @Test
        @DisplayName("autoMatch: 手動ロック組（結果なし）の選手を除外し、lockedPairings に locked=true / hasResult=false で含める")
        void shouldExcludeManuallyLockedPlayersFromAutoMatch() {
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate).matchNumber(matchNumber).build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, 1, List.of(ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 3L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 4L, ParticipantStatus.WON)
                    ));
            // player1 vs player2 は手動ロック（結果なし）
            MatchPairing manualLocked = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            manualLocked.setLocked(true);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(List.of(manualLocked));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());
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

            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            assertThat(result.getPairings()).hasSize(1);
            assertThat(result.getLockedPairings()).hasSize(1);
            AutoMatchingResult.PairingSuggestion locked = result.getLockedPairings().get(0);
            assertThat(locked.getId()).isEqualTo(10L);
            assertThat(locked.isLocked()).isTrue();
            assertThat(locked.isHasResult()).isFalse();
        }

        @Test
        @DisplayName("deleteByDateAndMatchNumber: 手動ロック組（結果なし）が保持される")
        void shouldPreserveManuallyLockedPairingOnDelete() {
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            MatchPairing manualLocked = createMatchPairing(10L, sessionDate, matchNumber, 1L, 2L);
            manualLocked.setLocked(true);
            MatchPairing unlocked = createMatchPairing(11L, sessionDate, matchNumber, 3L, 4L);
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Arrays.asList(manualLocked, unlocked));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            matchPairingService.deleteByDateAndMatchNumber(sessionDate, matchNumber, null);

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

        @Test
        @DisplayName("createBatchで組織スコープ時に他団体のペアリングは削除対象にならない")
        void shouldNotDeleteOtherOrgPairingsOnCreateBatch() {
            // Given: 同日に Org A と Org B のペアリングが混在
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            Long orgAId = 10L;
            Long createdBy = 1L;

            PracticeSession sessionA = new PracticeSession();
            sessionA.setId(100L);
            sessionA.setSessionDate(sessionDate);
            sessionA.setOrganizationId(orgAId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgAId))
                    .thenReturn(Optional.of(sessionA));

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
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber))
                    .thenReturn(Collections.emptyList());

            // 新規ペアリング: player1 vs player2
            MatchPairingCreateRequest req = new MatchPairingCreateRequest();
            req.setPlayer1Id(1L);
            req.setPlayer2Id(2L);
            req.setSessionDate(sessionDate);
            req.setMatchNumber(matchNumber);

            when(matchPairingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findAllById(anyCollection())).thenReturn(Arrays.asList(player1, player2));

            // When: Org A のスコープで createBatch
            matchPairingService.createBatch(sessionDate, matchNumber, List.of(req), null, createdBy, orgAId);

            // Then: Org A のペアリングのみ削除される（Org B は対象外）
            verify(matchPairingRepository).deleteAll(argThat(list -> {
                List<MatchPairing> deleted = (List<MatchPairing>) list;
                return deleted.size() == 1 && deleted.get(0).getId().equals(1L);
            }));
        }

        @Test
        @DisplayName("getOrganizationIdByPairingIdでセッション未参加ならnullを返す")
        void shouldReturnNullWhenPairingPlayerNotInAnySession() {
            // Given: ペアリングのプレイヤーがどのセッションにも参加していない
            Long pairingId = 1L;
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            MatchPairing pairing = createMatchPairing(pairingId, sessionDate, 1, 1L, 2L);
            when(matchPairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));

            PracticeSession session = new PracticeSession();
            session.setId(100L);
            session.setOrganizationId(10L);
            when(practiceSessionRepository.findByDateRange(sessionDate, sessionDate))
                    .thenReturn(List.of(session));
            // セッション参加者に player1, player2 がいない
            PracticeParticipant pp = PracticeParticipant.builder().playerId(99L).sessionId(100L).build();
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(List.of(pp));

            // When
            Long orgId = matchPairingService.getOrganizationIdByPairingId(pairingId);

            // Then: 組織特定不能のためnull
            assertThat(orgId).isNull();
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

    @Nested
    @DisplayName("同日他試合検知の境界テスト（getPairRecentMatches）")
    class SameDayOtherMatchDetectionTests {

        @Test
        @DisplayName("同日の後方試合番号で組まれたペアが当日として検知される")
        void shouldDetectPairFromLaterMatchNumber() {
            // Given: 試合3を編集中、試合5で player1 vs player2 が既に組まれている
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            List<MatchPairing> sameDayPairings = Arrays.asList(
                    createMatchPairing(10L, sessionDate, 5, 1L, 2L)
            );

            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(sameDayPairings);
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<AutoMatchingResult.MatchHistory> result =
                    matchPairingService.getPairRecentMatches(1L, 2L, sessionDate, currentMatchNumber);

            // Then: 当日として検知される
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMatchDate()).isEqualTo(sessionDate);
        }

        @Test
        @DisplayName("同日の自分の試合番号で組まれたペアは検知されない")
        void shouldExcludeOwnMatchNumberPair() {
            // Given: 試合3を編集中、同じ試合3で player1 vs player2 が入っているだけ
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            List<MatchPairing> sameDayPairings = Arrays.asList(
                    createMatchPairing(10L, sessionDate, 3, 1L, 2L)
            );

            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(sameDayPairings);
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<AutoMatchingResult.MatchHistory> result =
                    matchPairingService.getPairRecentMatches(1L, 2L, sessionDate, currentMatchNumber);

            // Then: 自分の試合番号は除外されるため履歴なし
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matchNumber=1 でも同日他試合のペアを検知する")
        void shouldDetectSameDayWhenMatchNumberIsOne() {
            // Given: 試合1を編集中、試合2・試合3に同ペアが存在
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 1;
            List<MatchPairing> sameDayPairings = Arrays.asList(
                    createMatchPairing(10L, sessionDate, 2, 1L, 2L),
                    createMatchPairing(11L, sessionDate, 3, 1L, 2L)
            );

            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(sameDayPairings);
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<AutoMatchingResult.MatchHistory> result =
                    matchPairingService.getPairRecentMatches(1L, 2L, sessionDate, currentMatchNumber);

            // Then: 当日として1件検知される（同一日付の重複はマージされる）
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMatchDate()).isEqualTo(sessionDate);
        }

        @Test
        @DisplayName("前方・後方両方の試合番号のペアをまとめて検知する（試合3編集中に試合1と試合5で組まれた同ペア）")
        void shouldDetectBothForwardAndBackwardMatchNumbers() {
            // Given: 試合3を編集中、試合1と試合5に同ペア
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            List<MatchPairing> sameDayPairings = Arrays.asList(
                    createMatchPairing(10L, sessionDate, 1, 1L, 2L),
                    createMatchPairing(11L, sessionDate, 5, 1L, 2L)
            );

            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(sameDayPairings);
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<AutoMatchingResult.MatchHistory> result =
                    matchPairingService.getPairRecentMatches(1L, 2L, sessionDate, currentMatchNumber);

            // Then: 当日として1件（日付単位では重複排除）
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMatchDate()).isEqualTo(sessionDate);
        }

        @Test
        @DisplayName("getByDateAndMatchNumber: 同日後方試合番号のペアが DTO recentMatches に反映される")
        void shouldIncludeSameDayBackwardPairInDtoRecentMatches() {
            // Given: 試合3の取得時、試合5に同ペアが既存
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            MatchPairing currentPairing = createMatchPairing(1L, sessionDate, currentMatchNumber, 1L, 2L);
            MatchPairing laterPairing = createMatchPairing(10L, sessionDate, 5, 1L, 2L);

            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(List.of(currentPairing));
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(List.of(currentPairing, laterPairing));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result =
                    matchPairingService.getByDateAndMatchNumber(sessionDate, currentMatchNumber);

            // Then: 当日ペア1件がrecentMatchesに反映される（自分の試合番号は除外）
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRecentMatches()).hasSize(1);
            assertThat(result.get(0).getRecentMatches().get(0).getMatchDate()).isEqualTo(sessionDate);
        }

        @Test
        @DisplayName("getByDateAndMatchNumber: 自分の試合番号は recentMatches から除外される")
        void shouldExcludeOwnMatchNumberFromDtoRecentMatches() {
            // Given: 試合3の取得時、同じ試合3にしか同ペアがない（＝自分の試合のみ）
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            MatchPairing currentPairing = createMatchPairing(1L, sessionDate, currentMatchNumber, 1L, 2L);

            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(List.of(currentPairing));
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(List.of(currentPairing));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(playerRepository.findAllById(anyList())).thenReturn(Arrays.asList(player1, player2));
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(Collections.emptyList());

            // When
            List<MatchPairingDto> result =
                    matchPairingService.getByDateAndMatchNumber(sessionDate, currentMatchNumber);

            // Then: 自分の試合番号は履歴から除外されるためrecentMatchesは空
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRecentMatches()).isEmpty();
        }

        @Test
        @DisplayName("autoMatch: 同日後方試合番号のペアが pairings[].recentMatches に反映される")
        void shouldIncludeSameDayBackwardPairInAutoMatchRecentMatches() {
            // Given: 試合3の自動マッチング、試合5で player1(1) vs player2(2) が既に組まれている
            // 試合3の参加者は 1, 2 のみ → 必ず (1,2) がペアになる
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(currentMatchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, currentMatchNumber, List.of(ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, currentMatchNumber, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, currentMatchNumber, 2L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            // 試合3のロック判定用（空）
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(Collections.emptyList());
            // 同日全ペアリング: 試合5に (1,2) が存在
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(List.of(createMatchPairing(10L, sessionDate, 5, 1L, 2L)));

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then: (1,2) がペアになり、recentMatches に当日1件が含まれる
            assertThat(result.getPairings()).hasSize(1);
            AutoMatchingResult.PairingSuggestion suggestion = result.getPairings().get(0);
            assertThat(suggestion.getRecentMatches()).hasSize(1);
            assertThat(suggestion.getRecentMatches().get(0).getMatchDate()).isEqualTo(sessionDate);
            // スコアは同日ペナルティ(-1000)に該当しないこと（表示と計算が分離されているため）
            assertThat(suggestion.getScore()).isNotEqualTo(-1000.0);
        }

        @Test
        @DisplayName("autoMatch: 同日後方試合番号のペアはスコア計算に影響しない（SAME_DAY_PENALTY_SCOREに誤該当しない）")
        void shouldNotPenalizeBackwardMatchNumberPairInAutoMatchScoring() {
            // Given: 試合3の自動マッチング、参加者は 1, 2, 3, 4
            // 試合5で (1,2) と (3,4) が既に組まれている（後方試合番号）
            // この後方同日ペアがスコア計算を汚染すると、autoMatchが正常に組めなくなる可能性があるが、
            // 分離により影響ない（calculatePairScoreには後方同日ペアが渡らない）
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Integer currentMatchNumber = 3;
            AutoMatchingRequest request = AutoMatchingRequest.builder()
                    .sessionDate(sessionDate)
                    .matchNumber(currentMatchNumber)
                    .build();

            PracticeSession session = createSession(100L, sessionDate);
            when(practiceSessionRepository.findBySessionDate(sessionDate)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatusIn(100L, currentMatchNumber, List.of(ParticipantStatus.WON)))
                    .thenReturn(Arrays.asList(
                            createPracticeParticipant(100L, currentMatchNumber, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, currentMatchNumber, 2L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, currentMatchNumber, 3L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, currentMatchNumber, 4L, ParticipantStatus.WON)
                    ));
            when(playerRepository.findAllById(anyCollection()))
                    .thenReturn(Arrays.asList(player1, player2, player3, player4));
            when(matchPairingRepository.findRecentPairingHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findRecentMatchHistory(anyList(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findTodayMatches(any(LocalDate.class), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByMatchDateAndMatchNumber(sessionDate, currentMatchNumber))
                    .thenReturn(Collections.emptyList());
            // 試合5に後方ペア (1,2) と (3,4) が存在
            when(matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate))
                    .thenReturn(List.of(
                            createMatchPairing(10L, sessionDate, 5, 1L, 2L),
                            createMatchPairing(11L, sessionDate, 5, 3L, 4L)
                    ));

            // When
            AutoMatchingResult result = matchPairingService.autoMatch(request, null);

            // Then: 2ペアが生成され、いずれも SAME_DAY_PENALTY_SCORE(-1000) ではないこと
            // （＝同日後方ペアがスコア計算を汚染していない）
            assertThat(result.getPairings()).hasSize(2);
            for (AutoMatchingResult.PairingSuggestion suggestion : result.getPairings()) {
                assertThat(suggestion.getScore())
                        .as("後方試合番号の同日ペアが SAME_DAY_PENALTY_SCORE を引き起こしてはならない")
                        .isNotEqualTo(-1000.0);
            }
        }
    }

    @Nested
    @DisplayName("PLAYER 開放向け：書き込み API の選手ID スコープ検証")
    class WriteScopeValidationTests {

        @Test
        @DisplayName("create: organizationId 指定時、対象セッション参加者でない選手IDは ForbiddenException")
        void shouldRejectCreateWhenPlayerNotInSession() {
            // Given: orgId=7 のセッションに player1(1L) は参加しているが player3(3L) は参加していない
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long orgId = 7L;
            PracticeSession session = createSession(100L, sessionDate);
            session.setOrganizationId(orgId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(List.of(createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON)));

            MatchPairingCreateRequest request = new MatchPairingCreateRequest(sessionDate, 1, 1L, 3L);

            // When & Then
            assertThatThrownBy(() -> matchPairingService.create(request, 1L, orgId))
                    .isInstanceOf(com.karuta.matchtracker.exception.ForbiddenException.class);
            verify(matchPairingRepository, never()).save(any(MatchPairing.class));
        }

        @Test
        @DisplayName("create: organizationId=null は検証スキップ（SUPER_ADMIN 経路）で従来通り保存できる")
        void shouldSkipValidationWhenOrganizationIdIsNull() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(sessionDate, 1, 1L, 3L);
            when(matchPairingRepository.save(any(MatchPairing.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            MatchPairingDto created = matchPairingService.create(request, 1L, null);

            // Then
            assertThat(created).isNotNull();
            verify(practiceSessionRepository, never()).findBySessionDateAndOrganizationId(any(), any());
            verify(matchPairingRepository).save(any(MatchPairing.class));
        }

        @Test
        @DisplayName("createBatch: organizationId 指定時、所属外 player ID は ForbiddenException")
        void shouldRejectBatchWhenPairingContainsNonMember() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long orgId = 7L;
            PracticeSession session = createSession(100L, sessionDate);
            session.setOrganizationId(orgId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(List.of(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)));

            List<MatchPairingCreateRequest> pairings = List.of(
                    new MatchPairingCreateRequest(sessionDate, 1, 1L, 99L)); // 99L は所属外

            // When & Then
            assertThatThrownBy(() -> matchPairingService.createBatch(sessionDate, 1, pairings,
                    Collections.emptyList(), 1L, orgId))
                    .isInstanceOf(com.karuta.matchtracker.exception.ForbiddenException.class);
            verify(matchPairingRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("createBatch: organizationId 指定時、所属外 waitingPlayerId は ForbiddenException")
        void shouldRejectBatchWhenWaitingPlayerIsNonMember() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long orgId = 7L;
            PracticeSession session = createSession(100L, sessionDate);
            session.setOrganizationId(orgId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(List.of(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)));

            // 全ペアは適法だが、待機者に所属外IDが混入
            List<MatchPairingCreateRequest> pairings = List.of(
                    new MatchPairingCreateRequest(sessionDate, 1, 1L, 2L));
            List<Long> waitingPlayerIds = List.of(99L);

            // When & Then
            assertThatThrownBy(() -> matchPairingService.createBatch(sessionDate, 1, pairings,
                    waitingPlayerIds, 1L, orgId))
                    .isInstanceOf(com.karuta.matchtracker.exception.ForbiddenException.class);
            verify(matchPairingRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("updatePlayer: organizationId 指定時、所属外 newPlayerId は ForbiddenException")
        void shouldRejectUpdatePlayerWhenNewPlayerIsNonMember() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long orgId = 7L;
            Long pairingId = 10L;
            MatchPairing pairing = createMatchPairing(pairingId, sessionDate, 1, 1L, 2L);
            when(matchPairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));

            PracticeSession session = createSession(100L, sessionDate);
            session.setOrganizationId(orgId);
            when(practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, orgId))
                    .thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionId(100L))
                    .thenReturn(List.of(
                            createPracticeParticipant(100L, 1, 1L, ParticipantStatus.WON),
                            createPracticeParticipant(100L, 1, 2L, ParticipantStatus.WON)));

            // When & Then: 99L は所属外
            assertThatThrownBy(() -> matchPairingService.updatePlayer(pairingId, 99L, "player1", 1L, orgId))
                    .isInstanceOf(com.karuta.matchtracker.exception.ForbiddenException.class);
            verify(matchPairingRepository, never()).save(any(MatchPairing.class));
        }

        @Test
        @DisplayName("updatePlayer: organizationId=null は検証スキップで従来通り処理される")
        void shouldSkipUpdatePlayerValidationWhenOrgIdIsNull() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long pairingId = 10L;
            MatchPairing pairing = createMatchPairing(pairingId, sessionDate, 1, 1L, 2L);
            when(matchPairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));
            when(matchPairingRepository.save(any(MatchPairing.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            MatchPairingDto updated = matchPairingService.updatePlayer(pairingId, 99L, "player1", 1L, null);

            // Then: organizationId=null では検証スキップ、99L でも更新される
            assertThat(updated).isNotNull();
            verify(practiceSessionRepository, never()).findBySessionDateAndOrganizationId(any(), any());
            verify(matchPairingRepository).save(any(MatchPairing.class));
        }
    }

    /**
     * review round 4 で導入した「両プレイヤーが同一セッションに参加している場合のみ
     * organizationId を返す」設計のテスト。片方の選手だけが別団体セッションにも
     * 参加しているケースで、団体一意特定不能として null を返すことを検証する。
     */
    @Nested
    @DisplayName("getOrganizationIdByPairingId メソッド")
    class GetOrganizationIdByPairingIdTests {

        @Test
        @DisplayName("両プレイヤーが参加する単一セッションがあれば organizationId を返す")
        void shouldReturnOrgIdWhenBothPlayersParticipate() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long pairingId = 10L;
            Long orgId = 100L;
            MatchPairing pairing = createMatchPairing(pairingId, sessionDate, 1, 1L, 2L);
            PracticeSession session = createSession(500L, sessionDate);
            session.setOrganizationId(orgId);

            when(matchPairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));
            when(practiceSessionRepository.findByDateRange(sessionDate, sessionDate))
                    .thenReturn(List.of(session));
            when(practiceParticipantRepository.findBySessionId(500L)).thenReturn(List.of(
                    createPracticeParticipant(500L, 1, 1L, ParticipantStatus.WON),
                    createPracticeParticipant(500L, 1, 2L, ParticipantStatus.WON)));

            // When
            Long resolved = matchPairingService.getOrganizationIdByPairingId(pairingId);

            // Then
            assertThat(resolved).isEqualTo(orgId);
        }

        @Test
        @DisplayName("片方の選手だけが別団体セッションに参加していてもそのセッションは候補外（団体一意特定不能で null）")
        void shouldReturnNullWhenOnlyOnePlayerParticipates() {
            // Given: 同日に2セッション。team A は両参加だが、team B は player1 のみ参加。
            // 旧実装はどちらのセッションも片方参加で「最初に見つかったほう」を返してしまうが、
            // 新実装は team A の orgId に一意特定される。一方、team A 側に両参加セッションがない
            // ケース（下記）では null を返すべき。
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long pairingId = 10L;
            MatchPairing pairing = createMatchPairing(pairingId, sessionDate, 1, 1L, 2L);

            PracticeSession sessionA = createSession(500L, sessionDate);
            sessionA.setOrganizationId(100L);
            PracticeSession sessionB = createSession(600L, sessionDate);
            sessionB.setOrganizationId(200L);

            when(matchPairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));
            when(practiceSessionRepository.findByDateRange(sessionDate, sessionDate))
                    .thenReturn(List.of(sessionA, sessionB));
            // sessionA: player1 のみ参加（player2 は参加していない）
            when(practiceParticipantRepository.findBySessionId(500L)).thenReturn(List.of(
                    createPracticeParticipant(500L, 1, 1L, ParticipantStatus.WON)));
            // sessionB: player2 のみ参加（player1 は参加していない）
            when(practiceParticipantRepository.findBySessionId(600L)).thenReturn(List.of(
                    createPracticeParticipant(600L, 1, 2L, ParticipantStatus.WON)));

            // When
            Long resolved = matchPairingService.getOrganizationIdByPairingId(pairingId);

            // Then: 両参加セッションが0件 → null（ADMIN/PLAYER 側で ForbiddenException となる）
            assertThat(resolved).isNull();
        }

        @Test
        @DisplayName("同日に両プレイヤー参加セッションが2件ある場合は null を返す（団体一意特定不能）")
        void shouldReturnNullWhenMultipleSessionsMatch() {
            // Given
            LocalDate sessionDate = LocalDate.of(2024, 1, 15);
            Long pairingId = 10L;
            MatchPairing pairing = createMatchPairing(pairingId, sessionDate, 1, 1L, 2L);

            PracticeSession sessionA = createSession(500L, sessionDate);
            sessionA.setOrganizationId(100L);
            PracticeSession sessionB = createSession(600L, sessionDate);
            sessionB.setOrganizationId(200L);

            when(matchPairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));
            when(practiceSessionRepository.findByDateRange(sessionDate, sessionDate))
                    .thenReturn(List.of(sessionA, sessionB));
            // 両セッションに両プレイヤーが参加している
            when(practiceParticipantRepository.findBySessionId(500L)).thenReturn(List.of(
                    createPracticeParticipant(500L, 1, 1L, ParticipantStatus.WON),
                    createPracticeParticipant(500L, 1, 2L, ParticipantStatus.WON)));
            when(practiceParticipantRepository.findBySessionId(600L)).thenReturn(List.of(
                    createPracticeParticipant(600L, 1, 1L, ParticipantStatus.WON),
                    createPracticeParticipant(600L, 1, 2L, ParticipantStatus.WON)));

            // When
            Long resolved = matchPairingService.getOrganizationIdByPairingId(pairingId);

            // Then: 候補2件 → null（団体一意特定不能で安全側拒否）
            assertThat(resolved).isNull();
        }
    }
}
