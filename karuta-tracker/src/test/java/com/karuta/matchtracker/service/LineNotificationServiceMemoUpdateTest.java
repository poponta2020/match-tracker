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
@DisplayName("LineNotificationService メモ更新通知 集約テスト")
class LineNotificationServiceMemoUpdateTest {

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
    private Player mentee;

    @BeforeEach
    void setUp() {
        mentee = Player.builder().id(2L).name("テストメンティー").build();
        match = Match.builder()
                .id(100L).player1Id(2L).player2Id(3L)
                .matchDate(LocalDate.of(2026, 4, 5)).matchNumber(1)
                .opponentName("対戦相手")
                .build();
    }

    private MentorRelationship buildRelationship(Long mentorId, Long menteeId) {
        MentorRelationship rel = new MentorRelationship();
        rel.setMentorId(mentorId);
        rel.setMenteeId(menteeId);
        return rel;
    }

    @Nested
    @DisplayName("メンティーが存在しない場合")
    class MenteeNotFound {
        @Test
        @DisplayName("メンティーが見つからない場合SKIPPEDを返す")
        void returnsSkippedWhenMenteeNotFound() {
            when(playerRepository.findById(999L)).thenReturn(Optional.empty());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMemoUpdateFlexNotification(999L, match, "テストメモ");

            assertThat(result).isEqualTo(LineNotificationService.SendResult.SKIPPED);
        }
    }

    @Nested
    @DisplayName("メンター関係が存在しない場合")
    class NoMentorRelationship {
        @Test
        @DisplayName("ACTIVEメンターがいない場合SKIPPEDを返す")
        void returnsSkippedWhenNoActiveMentors() {
            when(playerRepository.findById(2L)).thenReturn(Optional.of(mentee));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMemoUpdateFlexNotification(2L, match, "テストメモ");

            assertThat(result).isEqualTo(LineNotificationService.SendResult.SKIPPED);
        }
    }

    @Nested
    @DisplayName("複数メンター送信時のSendResult集約")
    class MemoUpdateAggregation {

        @Test
        @DisplayName("SUCCESS + FAILED混在時はFAILEDを返す")
        void mixedSuccessAndFailedReturnsFailed() {
            when(playerRepository.findById(2L)).thenReturn(Optional.of(mentee));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.SUCCESS)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(20L), eq(LineNotificationType.MENTEE_MEMO_UPDATE), anyString(), anyMap());
            doReturn(LineNotificationService.SendResult.FAILED)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(21L), eq(LineNotificationType.MENTEE_MEMO_UPDATE), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMemoUpdateFlexNotification(2L, match, "テストメモ");

            assertThat(result).isEqualTo(LineNotificationService.SendResult.FAILED);
        }

        @Test
        @DisplayName("全員成功時はSUCCESSを返す")
        void allSuccessReturnsSuccess() {
            when(playerRepository.findById(2L)).thenReturn(Optional.of(mentee));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.SUCCESS)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(anyLong(), eq(LineNotificationType.MENTEE_MEMO_UPDATE), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMemoUpdateFlexNotification(2L, match, "テストメモ");

            assertThat(result).isEqualTo(LineNotificationService.SendResult.SUCCESS);
        }

        @Test
        @DisplayName("SUCCESS + SKIPPED混在時はSKIPPEDを返す")
        void mixedSuccessAndSkippedReturnsSkipped() {
            when(playerRepository.findById(2L)).thenReturn(Optional.of(mentee));
            when(mentorRelationshipRepository.findByMenteeIdAndStatus(2L, MentorRelationship.Status.ACTIVE))
                    .thenReturn(List.of(buildRelationship(20L, 2L), buildRelationship(21L, 2L)));

            doReturn(LineNotificationService.SendResult.SUCCESS)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(20L), eq(LineNotificationType.MENTEE_MEMO_UPDATE), anyString(), anyMap());
            doReturn(LineNotificationService.SendResult.SKIPPED)
                    .when(lineNotificationService)
                    .sendFlexToPlayer(eq(21L), eq(LineNotificationType.MENTEE_MEMO_UPDATE), anyString(), anyMap());

            LineNotificationService.SendResult result = lineNotificationService
                    .sendMemoUpdateFlexNotification(2L, match, "テストメモ");

            assertThat(result).isEqualTo(LineNotificationService.SendResult.SKIPPED);
        }
    }
}
