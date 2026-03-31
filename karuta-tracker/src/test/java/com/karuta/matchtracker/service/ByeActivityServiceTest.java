package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.ByeActivity;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.ByeActivityRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ByeActivityServiceの単体テスト（ABSENT関連のPracticeParticipant評価ロジック）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ByeActivityService 単体テスト")
class ByeActivityServiceTest {

    @Mock
    private ByeActivityRepository byeActivityRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @InjectMocks
    private ByeActivityService byeActivityService;

    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 3, 31);
    private static final Long PLAYER_ID = 1L;
    private static final Long SESSION_ID = 100L;
    private static final Long USER_ID = 1L;

    private PracticeSession testSession;
    private Player testPlayer;

    @BeforeEach
    void setUp() {
        testSession = PracticeSession.builder()
                .id(SESSION_ID)
                .sessionDate(SESSION_DATE)
                .totalMatches(7)
                .build();

        testPlayer = new Player();
        testPlayer.setId(PLAYER_ID);
        testPlayer.setName("テスト選手");
    }

    private ByeActivity buildByeActivity(Long id, Integer matchNumber, ActivityType type) {
        return ByeActivity.builder()
                .id(id)
                .sessionDate(SESSION_DATE)
                .matchNumber(matchNumber)
                .playerId(PLAYER_ID)
                .activityType(type)
                .createdBy(USER_ID)
                .updatedBy(USER_ID)
                .build();
    }

    @Nested
    @DisplayName("evaluatePracticeParticipant - create経由")
    class CreateWithAbsent {

        @Test
        @DisplayName("全ByeActivityがABSENTの場合、PracticeParticipantが削除される")
        void allAbsent_deletesPracticeParticipant() {
            // Arrange
            ByeActivityCreateRequest request = ByeActivityCreateRequest.builder()
                    .sessionDate(SESSION_DATE)
                    .matchNumber(1)
                    .playerId(PLAYER_ID)
                    .activityType(ActivityType.ABSENT)
                    .build();

            ByeActivity savedEntity = buildByeActivity(1L, 1, ActivityType.ABSENT);

            when(byeActivityRepository.findBySessionDateAndMatchNumberAndPlayerId(SESSION_DATE, 1, PLAYER_ID))
                    .thenReturn(Optional.empty());
            when(byeActivityRepository.save(any(ByeActivity.class))).thenReturn(savedEntity);
            when(playerRepository.findAllById(any())).thenReturn(List.of(testPlayer));

            // 評価ロジック用のモック: 全ABSENTのシナリオ
            when(byeActivityRepository.findBySessionDateOrderByMatchNumber(SESSION_DATE))
                    .thenReturn(List.of(savedEntity));
            when(practiceSessionRepository.findBySessionDate(SESSION_DATE))
                    .thenReturn(Optional.of(testSession));
            PracticeParticipant existingPp = PracticeParticipant.builder()
                    .id(10L).sessionId(SESSION_ID).playerId(PLAYER_ID).matchNumber(null).build();
            when(practiceParticipantRepository.findByeParticipant(SESSION_ID, PLAYER_ID))
                    .thenReturn(Optional.of(existingPp));

            // Act
            byeActivityService.create(request, USER_ID);

            // Assert
            verify(practiceParticipantRepository).deleteByeParticipant(SESSION_ID, PLAYER_ID);
            verify(practiceParticipantRepository, never()).save(any(PracticeParticipant.class));
        }

        @Test
        @DisplayName("一部のみABSENTの場合、PracticeParticipantは削除されない")
        void partialAbsent_keepsPracticeParticipant() {
            // Arrange
            ByeActivityCreateRequest request = ByeActivityCreateRequest.builder()
                    .sessionDate(SESSION_DATE)
                    .matchNumber(1)
                    .playerId(PLAYER_ID)
                    .activityType(ActivityType.ABSENT)
                    .build();

            ByeActivity absentActivity = buildByeActivity(1L, 1, ActivityType.ABSENT);
            ByeActivity readingActivity = buildByeActivity(2L, 2, ActivityType.READING);

            when(byeActivityRepository.findBySessionDateAndMatchNumberAndPlayerId(SESSION_DATE, 1, PLAYER_ID))
                    .thenReturn(Optional.empty());
            when(byeActivityRepository.save(any(ByeActivity.class))).thenReturn(absentActivity);
            when(playerRepository.findAllById(any())).thenReturn(List.of(testPlayer));

            // 評価ロジック用: 混在シナリオ
            when(byeActivityRepository.findBySessionDateOrderByMatchNumber(SESSION_DATE))
                    .thenReturn(List.of(absentActivity, readingActivity));
            when(practiceSessionRepository.findBySessionDate(SESSION_DATE))
                    .thenReturn(Optional.of(testSession));
            PracticeParticipant existingPp = PracticeParticipant.builder()
                    .id(10L).sessionId(SESSION_ID).playerId(PLAYER_ID).matchNumber(null).build();
            when(practiceParticipantRepository.findByeParticipant(SESSION_ID, PLAYER_ID))
                    .thenReturn(Optional.of(existingPp));

            // Act
            byeActivityService.create(request, USER_ID);

            // Assert
            verify(practiceParticipantRepository, never()).deleteByeParticipant(any(), any());
            verify(practiceParticipantRepository, never()).save(any(PracticeParticipant.class));
        }
    }

    @Nested
    @DisplayName("evaluatePracticeParticipant - update経由")
    class UpdateWithAbsent {

        @Test
        @DisplayName("ABSENT→他アクティビティ変更時にPracticeParticipantが復元される")
        void absentToOther_restoresPracticeParticipant() {
            // Arrange
            ByeActivity existingEntity = buildByeActivity(1L, 1, ActivityType.ABSENT);
            ByeActivityUpdateRequest request = ByeActivityUpdateRequest.builder()
                    .activityType(ActivityType.READING)
                    .build();

            // update後のエンティティ
            ByeActivity updatedEntity = buildByeActivity(1L, 1, ActivityType.READING);

            when(byeActivityRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(byeActivityRepository.save(any(ByeActivity.class))).thenReturn(updatedEntity);
            when(playerRepository.findAllById(any())).thenReturn(List.of(testPlayer));

            // 評価ロジック用: ABSENT以外のみ → 復元が必要
            when(byeActivityRepository.findBySessionDateOrderByMatchNumber(SESSION_DATE))
                    .thenReturn(List.of(updatedEntity));
            when(practiceSessionRepository.findBySessionDate(SESSION_DATE))
                    .thenReturn(Optional.of(testSession));
            when(practiceParticipantRepository.findByeParticipant(SESSION_ID, PLAYER_ID))
                    .thenReturn(Optional.empty());

            // Act
            byeActivityService.update(1L, request, USER_ID);

            // Assert
            ArgumentCaptor<PracticeParticipant> captor = ArgumentCaptor.forClass(PracticeParticipant.class);
            verify(practiceParticipantRepository).save(captor.capture());
            PracticeParticipant restored = captor.getValue();
            assertThat(restored.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(restored.getPlayerId()).isEqualTo(PLAYER_ID);
            assertThat(restored.getMatchNumber()).isNull();
            verify(practiceParticipantRepository, never()).deleteByeParticipant(any(), any());
        }
    }

    @Nested
    @DisplayName("evaluatePracticeParticipant - createBatch経由")
    class CreateBatchWithAbsent {

        @Test
        @DisplayName("バッチ作成で全ABSENTの選手のPracticeParticipantが削除される")
        void batchAllAbsent_deletesPracticeParticipant() {
            // Arrange
            List<ByeActivityBatchItemRequest> items = List.of(
                    ByeActivityBatchItemRequest.builder()
                            .playerId(PLAYER_ID)
                            .activityType(ActivityType.ABSENT)
                            .build()
            );

            ByeActivity savedEntity = buildByeActivity(1L, 1, ActivityType.ABSENT);

            when(byeActivityRepository.saveAll(any())).thenReturn(List.of(savedEntity));
            when(playerRepository.findAllById(any())).thenReturn(List.of(testPlayer));

            // 評価ロジック用
            when(byeActivityRepository.findBySessionDateOrderByMatchNumber(SESSION_DATE))
                    .thenReturn(List.of(savedEntity));
            when(practiceSessionRepository.findBySessionDate(SESSION_DATE))
                    .thenReturn(Optional.of(testSession));
            PracticeParticipant existingPp = PracticeParticipant.builder()
                    .id(10L).sessionId(SESSION_ID).playerId(PLAYER_ID).matchNumber(null).build();
            when(practiceParticipantRepository.findByeParticipant(SESSION_ID, PLAYER_ID))
                    .thenReturn(Optional.of(existingPp));

            // Act
            byeActivityService.createBatch(SESSION_DATE, 1, items, USER_ID);

            // Assert
            verify(practiceParticipantRepository).deleteByeParticipant(SESSION_ID, PLAYER_ID);
        }

        @Test
        @DisplayName("バッチ作成で混在アクティビティの場合、PracticeParticipantは削除されない")
        void batchMixed_keepsPracticeParticipant() {
            // Arrange
            Long player2Id = 2L;
            Player player2 = new Player();
            player2.setId(player2Id);
            player2.setName("選手2");

            List<ByeActivityBatchItemRequest> items = List.of(
                    ByeActivityBatchItemRequest.builder()
                            .playerId(PLAYER_ID)
                            .activityType(ActivityType.ABSENT)
                            .build(),
                    ByeActivityBatchItemRequest.builder()
                            .playerId(player2Id)
                            .activityType(ActivityType.READING)
                            .build()
            );

            ByeActivity absentEntity = buildByeActivity(1L, 1, ActivityType.ABSENT);
            ByeActivity readingEntity = ByeActivity.builder()
                    .id(2L).sessionDate(SESSION_DATE).matchNumber(1)
                    .playerId(player2Id).activityType(ActivityType.READING)
                    .createdBy(USER_ID).updatedBy(USER_ID).build();

            when(byeActivityRepository.saveAll(any())).thenReturn(List.of(absentEntity, readingEntity));
            when(playerRepository.findAllById(any())).thenReturn(List.of(testPlayer, player2));

            // 評価ロジック: player1は全ABSENT, player2はREADINGのみ
            when(byeActivityRepository.findBySessionDateOrderByMatchNumber(SESSION_DATE))
                    .thenReturn(List.of(absentEntity, readingEntity));
            when(practiceSessionRepository.findBySessionDate(SESSION_DATE))
                    .thenReturn(Optional.of(testSession));

            PracticeParticipant pp1 = PracticeParticipant.builder()
                    .id(10L).sessionId(SESSION_ID).playerId(PLAYER_ID).matchNumber(null).build();
            when(practiceParticipantRepository.findByeParticipant(SESSION_ID, PLAYER_ID))
                    .thenReturn(Optional.of(pp1));
            PracticeParticipant pp2 = PracticeParticipant.builder()
                    .id(11L).sessionId(SESSION_ID).playerId(player2Id).matchNumber(null).build();
            when(practiceParticipantRepository.findByeParticipant(SESSION_ID, player2Id))
                    .thenReturn(Optional.of(pp2));

            // Act
            byeActivityService.createBatch(SESSION_DATE, 1, items, USER_ID);

            // Assert: player1のみ削除、player2は削除されない
            verify(practiceParticipantRepository).deleteByeParticipant(SESSION_ID, PLAYER_ID);
            verify(practiceParticipantRepository, never()).deleteByeParticipant(SESSION_ID, player2Id);
        }
    }
}
