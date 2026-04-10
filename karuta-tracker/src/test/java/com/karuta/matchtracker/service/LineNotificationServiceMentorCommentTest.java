package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineNotificationService メンターコメント通知 集約テスト")
class LineNotificationServiceMentorCommentTest {

    @Mock private LineChannelRepository lineChannelRepository;
    @Mock private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock private LineMessageLogService lineMessageLogService;
    @Mock private LineMessagingService lineMessagingService;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LotteryQueryService lotteryQueryService;
    @Mock private VenueRepository venueRepository;
    @Mock private MentorRelationshipRepository mentorRelationshipRepository;

    @Spy
    @InjectMocks
    private LineNotificationService lineNotificationService;

    private Match match;
    private Player author;
    private MatchComment comment;

    @BeforeEach
    void setUp() {
        author = Player.builder().id(10L).name("テスト投稿者").build();
        match = Match.builder()
                .id(100L).player1Id(2L).player2Id(3L)
                .matchDate(LocalDate.of(2026, 4, 5)).matchNumber(1)
                .opponentName("対戦相手")
                .build();
        comment = MatchComment.builder()
                .id(1L).matchId(100L).menteeId(2L).authorId(10L).content("テスト").build();
    }

    private MentorRelationship buildRelationship(Long mentorId, Long menteeId) {
        MentorRelationship rel = new MentorRelationship();
        rel.setMentorId(mentorId);
        rel.setMenteeId(menteeId);
        return rel;
    }

    @Nested
    @DisplayName("複数メンター送信時のSendResult集約")
    class MentorCommentAggregation {

        @Test
        @DisplayName("SUCCESS + FAILED混在時はFAILEDを返す（再送可能にする）")
        void mixedSuccessAndFailedReturnsFailed() {
            when(playerRepository.findById(10L)).thenReturn(Optional.of(author));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.SUCCESS)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(20L), eq(LineNotificationType.MENTOR_COMMENT), anyString(), anyMap());
            doReturn(LineNotificationService.SendResult.FAILED)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(21L), eq(LineNotificationType.MENTOR_COMMENT), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMentorCommentFlexNotification(10L, 2L, match, List.of(comment), true);

            assertThat(result).isEqualTo(LineNotificationService.SendResult.FAILED);
        }

        @Test
        @DisplayName("SUCCESS + SKIPPED混在時はSUCCESSを返す")
        void mixedSuccessAndSkippedReturnsSuccess() {
            when(playerRepository.findById(10L)).thenReturn(Optional.of(author));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.SUCCESS)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(20L), eq(LineNotificationType.MENTOR_COMMENT), anyString(), anyMap());
            doReturn(LineNotificationService.SendResult.SKIPPED)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(21L), eq(LineNotificationType.MENTOR_COMMENT), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMentorCommentFlexNotification(10L, 2L, match, List.of(comment), true);

            assertThat(result).isEqualTo(LineNotificationService.SendResult.SUCCESS);
        }

        @Test
        @DisplayName("全員成功時はSUCCESSを返す")
        void allSuccessReturnsSuccess() {
            when(playerRepository.findById(10L)).thenReturn(Optional.of(author));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.SUCCESS)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(anyLong(), eq(LineNotificationType.MENTOR_COMMENT), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMentorCommentFlexNotification(10L, 2L, match, List.of(comment), true);

            assertThat(result).isEqualTo(LineNotificationService.SendResult.SUCCESS);
        }

        @Test
        @DisplayName("全員失敗時はFAILEDを返す")
        void allFailedReturnsFailed() {
            when(playerRepository.findById(10L)).thenReturn(Optional.of(author));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.FAILED)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(anyLong(), eq(LineNotificationType.MENTOR_COMMENT), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMentorCommentFlexNotification(10L, 2L, match, List.of(comment), true);

            assertThat(result).isEqualTo(LineNotificationService.SendResult.FAILED);
        }
    }
}
