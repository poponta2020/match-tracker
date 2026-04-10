package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MatchCommentCreateRequest;
import com.karuta.matchtracker.dto.MatchCommentDto;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchComment;
import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.entity.MentorRelationship.Status;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.MatchCommentRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchCommentServiceTest {

    @Mock private MatchCommentRepository matchCommentRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private MentorRelationshipRepository mentorRelationshipRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks
    private MatchCommentService service;

    private Player author;
    private Match match;

    @BeforeEach
    void setUp() {
        author = new Player();
        author.setId(1L);
        author.setName("テスト選手");

        match = new Match();
        match.setId(100L);
        match.setPlayer1Id(2L);
        match.setPlayer2Id(3L);
    }

    @Nested
    @DisplayName("createComment")
    class CreateComment {

        @Test
        @DisplayName("メンティー本人がコメントを投稿できる")
        void menteeCanPost() {
            MatchCommentCreateRequest request = new MatchCommentCreateRequest(2L, "テストコメント");
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));
            when(matchCommentRepository.save(any())).thenAnswer(inv -> {
                MatchComment c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(playerRepository.findById(2L)).thenReturn(Optional.of(author));

            MatchCommentDto result = service.createComment(100L, request, 2L);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("メンター関係がないユーザーはコメント投稿できない")
        void noRelationshipForbidden() {
            MatchCommentCreateRequest request = new MatchCommentCreateRequest(2L, "テストコメント");
            when(mentorRelationshipRepository.findByMentorIdAndStatus(99L, Status.ACTIVE))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.createComment(100L, request, 99L))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("メンティーの試合でないmatchIdでエラー")
        void wrongMatchForbidden() {
            MatchCommentCreateRequest request = new MatchCommentCreateRequest(2L, "テストコメント");
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));

            // player 5 is not in match (player1=2, player2=3)
            MatchCommentCreateRequest wrongRequest = new MatchCommentCreateRequest(5L, "テストコメント");
            // mentee=5, currentUser=5 → mentee本人なのでアクセスOK、でもmatchが5の試合ではない
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));

            assertThatThrownBy(() -> service.createComment(100L, wrongRequest, 5L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("sendCommentNotification")
    class SendCommentNotification {

        private MatchComment unnotifiedComment;

        @BeforeEach
        void setUp() {
            unnotifiedComment = MatchComment.builder()
                    .id(1L).matchId(100L).menteeId(2L).authorId(10L)
                    .content("テストコメント").lineNotified(false).build();
        }

        @Test
        @DisplayName("SUCCESS時のみlineNotifiedがtrueに更新される")
        void successUpdatesLineNotified() {
            match.setPlayer1Id(2L);
            match.setPlayer2Id(10L);
            MentorRelationship rel = new MentorRelationship();
            rel.setMentorId(10L);
            rel.setMenteeId(2L);

            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, Status.ACTIVE))
                    .thenReturn(List.of(rel));
            when(matchCommentRepository.findUnnotifiedByMatchIdAndMenteeIdAndAuthorId(100L, 2L, 10L))
                    .thenReturn(List.of(unnotifiedComment));
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));
            when(lineNotificationService.sendMentorCommentFlexNotification(
                    eq(10L), eq(2L), any(), any(), eq(false)))
                    .thenReturn(LineNotificationService.SendResult.SUCCESS);

            Map<String, Object> result = service.sendCommentNotification(100L, 2L, 10L);

            assertThat(result.get("result")).isEqualTo("SUCCESS");
            assertThat(unnotifiedComment.getLineNotified()).isTrue();
            verify(matchCommentRepository).saveAll(any());
        }

        @Test
        @DisplayName("FAILED時はlineNotifiedが更新されない")
        void failedDoesNotUpdateLineNotified() {
            match.setPlayer1Id(2L);
            match.setPlayer2Id(10L);
            MentorRelationship rel = new MentorRelationship();
            rel.setMentorId(10L);
            rel.setMenteeId(2L);

            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, Status.ACTIVE))
                    .thenReturn(List.of(rel));
            when(matchCommentRepository.findUnnotifiedByMatchIdAndMenteeIdAndAuthorId(100L, 2L, 10L))
                    .thenReturn(List.of(unnotifiedComment));
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));
            when(lineNotificationService.sendMentorCommentFlexNotification(
                    eq(10L), eq(2L), any(), any(), eq(false)))
                    .thenReturn(LineNotificationService.SendResult.FAILED);

            Map<String, Object> result = service.sendCommentNotification(100L, 2L, 10L);

            assertThat(result.get("result")).isEqualTo("FAILED");
            assertThat(unnotifiedComment.getLineNotified()).isFalse();
            verify(matchCommentRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("SKIPPED時はlineNotifiedが更新されない")
        void skippedDoesNotUpdateLineNotified() {
            match.setPlayer1Id(2L);
            match.setPlayer2Id(10L);
            MentorRelationship rel = new MentorRelationship();
            rel.setMentorId(10L);
            rel.setMenteeId(2L);

            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, Status.ACTIVE))
                    .thenReturn(List.of(rel));
            when(matchCommentRepository.findUnnotifiedByMatchIdAndMenteeIdAndAuthorId(100L, 2L, 10L))
                    .thenReturn(List.of(unnotifiedComment));
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));
            when(lineNotificationService.sendMentorCommentFlexNotification(
                    eq(10L), eq(2L), any(), any(), eq(false)))
                    .thenReturn(LineNotificationService.SendResult.SKIPPED);

            Map<String, Object> result = service.sendCommentNotification(100L, 2L, 10L);

            assertThat(result.get("result")).isEqualTo("SKIPPED");
            assertThat(unnotifiedComment.getLineNotified()).isFalse();
            verify(matchCommentRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("未通知コメントがない場合はエラー")
        void emptyUnnotifiedThrows() {
            when(matchCommentRepository.findUnnotifiedByMatchIdAndMenteeIdAndAuthorId(100L, 2L, 2L))
                    .thenReturn(List.of());
            when(matchRepository.findById(100L)).thenReturn(Optional.of(match));

            assertThatThrownBy(() -> service.sendCommentNotification(100L, 2L, 2L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("updateComment - 関係解除後の編集不可")
    class UpdateAfterRelationRemoval {

        @Test
        @DisplayName("関係解除後のメンターはコメント編集できない")
        void forbiddenAfterRemoval() {
            MatchComment existingComment = MatchComment.builder()
                    .id(1L).matchId(100L).menteeId(2L).authorId(10L).content("元コメント").build();
            when(matchCommentRepository.findActiveById(1L)).thenReturn(Optional.of(existingComment));
            // メンター関係なし
            when(mentorRelationshipRepository.findByMentorIdAndStatus(10L, Status.ACTIVE))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.updateComment(100L, 1L, "更新", 10L))
                    .isInstanceOf(ForbiddenException.class);
        }
    }
}
