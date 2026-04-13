package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchPersonalNote;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchService メモ更新通知トリガー条件テスト")
class MatchServiceMemoNotificationTest {

    @Mock private MatchRepository matchRepository;
    @Mock private MatchPairingRepository matchPairingRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private MatchPersonalNoteRepository matchPersonalNoteRepository;
    @Mock private MentorRelationshipRepository mentorRelationshipRepository;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks
    private MatchService matchService;

    private Match testMatch;
    private Player player1;
    private Player player2;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        player1 = Player.builder().id(1L).name("山田太郎").build();
        player2 = Player.builder().id(2L).name("佐藤花子").build();
        testMatch = Match.builder()
                .id(1L).matchDate(today).matchNumber(1)
                .player1Id(1L).player2Id(2L)
                .winnerId(1L).scoreDifference(5)
                .createdBy(1L).updatedBy(1L)
                .build();
        // AFTER_COMMIT テスト用にトランザクション同期を初期化
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void setupCommonMocksForUpdate() {
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
        when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
    }

    /** 登録済みの AFTER_COMMIT コールバックを手動実行する */
    private void flushAfterCommitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Nested
    @DisplayName("通知が送信されるケース")
    class NotificationSent {

        @Test
        @DisplayName("参加者がメモを新規入力した場合、コミット後に通知が送信される")
        void notifiesMentorsWhenParticipantAddsNewMemo() {
            setupCommonMocksForUpdate();
            when(matchPersonalNoteRepository.findByMatchIdAndPlayerId(1L, 1L))
                    .thenReturn(Optional.of(MatchPersonalNote.builder()
                            .matchId(1L).playerId(1L).notes(null).build()));

            matchService.updateMatch(1L, 1L, 5, 1L, "新しいメモ", null, 1L);
            flushAfterCommitCallbacks();

            verify(lineNotificationService).sendMemoUpdateFlexNotification(eq(1L), any(Match.class), eq("新しいメモ"));
        }

        @Test
        @DisplayName("参加者がメモを更新した場合、コミット後に通知が送信される")
        void notifiesMentorsWhenParticipantUpdatesMemo() {
            setupCommonMocksForUpdate();
            when(matchPersonalNoteRepository.findByMatchIdAndPlayerId(1L, 1L))
                    .thenReturn(Optional.of(MatchPersonalNote.builder()
                            .matchId(1L).playerId(1L).notes("古いメモ").build()));

            matchService.updateMatch(1L, 1L, 5, 1L, "更新されたメモ", null, 1L);
            flushAfterCommitCallbacks();

            verify(lineNotificationService).sendMemoUpdateFlexNotification(eq(1L), any(Match.class), eq("更新されたメモ"));
        }
    }

    @Nested
    @DisplayName("通知が送信されないケース")
    class NotificationNotSent {

        @Test
        @DisplayName("非参加者のplayerIdの場合、メモ保存・通知ともスキップされる")
        void skipsWhenPlayerIdIsNotParticipant() {
            when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
            when(matchRepository.save(any(Match.class))).thenReturn(testMatch);
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            matchService.updateMatch(1L, 1L, 5, 999L, "悪意のあるメモ", null, 999L);
            flushAfterCommitCallbacks();

            verify(matchPersonalNoteRepository, never()).save(any());
            verify(lineNotificationService, never()).sendMemoUpdateFlexNotification(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("currentUserIdとplayerIdが一致しない場合、メモ保存・通知ともスキップされる")
        void skipsWhenCurrentUserIdDoesNotMatchPlayerId() {
            setupCommonMocksForUpdate();

            // updatedBy=1L(参加者)だが、currentUserId=999L(別ユーザー) → なりすまし防止
            matchService.updateMatch(1L, 1L, 5, 1L, "なりすましメモ", null, 999L);
            flushAfterCommitCallbacks();

            verify(matchPersonalNoteRepository, never()).save(any());
            verify(lineNotificationService, never()).sendMemoUpdateFlexNotification(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("メモが変更されていない場合、通知は送信されない")
        void doesNotNotifyWhenMemoUnchanged() {
            setupCommonMocksForUpdate();
            when(matchPersonalNoteRepository.findByMatchIdAndPlayerId(1L, 1L))
                    .thenReturn(Optional.of(MatchPersonalNote.builder()
                            .matchId(1L).playerId(1L).notes("同じメモ").build()));

            matchService.updateMatch(1L, 1L, 5, 1L, "同じメモ", null, 1L);
            flushAfterCommitCallbacks();

            verify(lineNotificationService, never()).sendMemoUpdateFlexNotification(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("メモがnullの場合、通知は送信されない")
        void doesNotNotifyWhenMemoIsNull() {
            setupCommonMocksForUpdate();

            matchService.updateMatch(1L, 1L, 5, 1L, null, null, 1L);
            flushAfterCommitCallbacks();

            verify(lineNotificationService, never()).sendMemoUpdateFlexNotification(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("メモが空白のみの場合、通知は送信されない")
        void doesNotNotifyWhenMemoIsBlank() {
            setupCommonMocksForUpdate();
            when(matchPersonalNoteRepository.findByMatchIdAndPlayerId(1L, 1L))
                    .thenReturn(Optional.of(MatchPersonalNote.builder()
                            .matchId(1L).playerId(1L).notes(null).build()));

            matchService.updateMatch(1L, 1L, 5, 1L, "   ", null, 1L);
            flushAfterCommitCallbacks();

            verify(lineNotificationService, never()).sendMemoUpdateFlexNotification(anyLong(), any(), anyString());
        }
    }
}
