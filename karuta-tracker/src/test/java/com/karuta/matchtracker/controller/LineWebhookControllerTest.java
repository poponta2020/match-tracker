package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineConfirmationToken;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.LineChannelService;
import com.karuta.matchtracker.service.LineConfirmationService;
import com.karuta.matchtracker.service.LineLinkingService;
import com.karuta.matchtracker.service.LineMessagingService;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.LotteryDeadlineHelper;
import com.karuta.matchtracker.service.PracticeSessionService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LineWebhookController.class)
@DisplayName("LineWebhookController tests")
class LineWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private LineChannelRepository lineChannelRepository;
    @MockitoBean private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @MockitoBean private PracticeParticipantRepository practiceParticipantRepository;
    @MockitoBean private PracticeSessionRepository practiceSessionRepository;
    @MockitoBean private VenueRepository venueRepository;
    @MockitoBean private PlayerRepository playerRepository;
    @MockitoBean private LineMessagingService lineMessagingService;
    @MockitoBean private LineChannelService lineChannelService;
    @MockitoBean private LineLinkingService lineLinkingService;
    @MockitoBean private WaitlistPromotionService waitlistPromotionService;
    @MockitoBean private LineNotificationService lineNotificationService;
    @MockitoBean private LineConfirmationService lineConfirmationService;
    @MockitoBean private LotteryDeadlineHelper lotteryDeadlineHelper;
    @MockitoBean private PracticeSessionService practiceSessionService;

    @Test
    @DisplayName("invalid signature returns 400")
    void handleWebhook_invalidSignature_returnsBadRequest() throws Exception {
        LineChannel channel = channel();
        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("bad-sign"))).thenReturn(false);

        String body = "{\"events\":[]}";

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "bad-sign")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));
    }

    @Test
    @DisplayName("successful link code message triggers pending lottery push")
    void handleWebhook_messageVerifySuccess_triggersPendingLotteryResults() throws Exception {
        LineChannel channel = channel();
        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineLinkingService.verifyCode("123456", "U111", 10L))
                .thenReturn(LineLinkingService.VerificationResult.SUCCESS);

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("events", java.util.List.of(
                        java.util.Map.of(
                                "type", "message",
                                "replyToken", "reply-token-1",
                                "source", java.util.Map.of("userId", "U111"),
                                "message", java.util.Map.of("type", "text", "text", "123456")
                        )
                ))
        );

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(lineChannelService).sendPendingLotteryResultsForChannel(10L);
        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-1"), anyString());
    }

    @Test
    @DisplayName("confirm same-day join postback executes join with linked player")
    void handleWebhook_confirmSameDayJoin_executesJoin() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = LineChannelAssignment.builder()
                .lineChannelId(10L)
                .playerId(77L)
                .lineUserId("U777")
                .channelType(ChannelType.PLAYER)
                .status(LineChannelAssignment.AssignmentStatus.LINKED)
                .build();
        LineConfirmationToken token = LineConfirmationToken.builder()
                .token("tok123")
                .action("same_day_join")
                .params("{\"sessionId\":\"200\",\"matchNumber\":\"2\"}")
                .playerId(77L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(lineConfirmationService.consumeToken("tok123", 77L)).thenReturn(token);

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("events", java.util.List.of(
                        java.util.Map.of(
                                "type", "postback",
                                "replyToken", "reply-token-2",
                                "source", java.util.Map.of("userId", "U777"),
                                "postback", java.util.Map.of("data", "action=confirm_same_day_join&token=tok123")
                        )
                ))
        );

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(waitlistPromotionService).handleSameDayJoin(200L, 2, 77L);
        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-2"), anyString());
    }

    @Test
    @DisplayName("check_today_participants returns no-session message when player has no organization")
    void checkTodayParticipants_noOrganization_returnsNoSession() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(practiceSessionService.findNextSessionForPlayer(77L)).thenReturn(null);

        String body = postbackBody("action=check_today_participants");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk());

        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-2"), eq("予定されている練習はありません"));
    }

    @Test
    @DisplayName("check_today_participants returns session info when next session exists")
    void checkTodayParticipants_withSession_returnsParticipants() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        PracticeSession session = PracticeSession.builder()
                .id(100L)
                .sessionDate(LocalDate.now())
                .organizationId(1L)
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(practiceSessionService.findNextSessionForPlayer(77L)).thenReturn(session);
        when(practiceParticipantRepository.findBySessionIdAndStatus(eq(100L), any()))
                .thenReturn(java.util.List.of());

        String body = postbackBody("action=check_today_participants");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk());

        // セッションが存在するので「予定されている練習はありません」は送信されない
        verify(lineMessagingService, never()).sendReplyMessage(eq("token"), eq("reply-token-2"), eq("予定されている練習はありません"));
    }

    private LineChannelAssignment linkedAssignment() {
        return LineChannelAssignment.builder()
                .lineChannelId(10L)
                .playerId(77L)
                .lineUserId("U777")
                .channelType(ChannelType.PLAYER)
                .status(LineChannelAssignment.AssignmentStatus.LINKED)
                .build();
    }

    @Test
    @DisplayName("confirm waitlist_accept_all postback executes batch accept")
    void handleWebhook_confirmWaitlistAcceptAll_executesBatchAccept() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        LineConfirmationToken token = LineConfirmationToken.builder()
                .token("tok-all-accept")
                .action("waitlist_accept_all")
                .params("{\"sessionId\":\"200\",\"playerId\":\"77\"}")
                .playerId(77L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(lineConfirmationService.consumeToken("tok-all-accept", 77L)).thenReturn(token);
        when(waitlistPromotionService.respondToOfferAll(200L, 77L, true)).thenReturn(3);

        String body = postbackBody("action=confirm_waitlist_accept_all&token=tok-all-accept");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(waitlistPromotionService).respondToOfferAll(200L, 77L, true);
        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-2"), eq("3試合の参加登録が完了しました。練習頑張ってください！"));
    }

    @Test
    @DisplayName("confirm waitlist_decline_all postback executes batch decline")
    void handleWebhook_confirmWaitlistDeclineAll_executesBatchDecline() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        LineConfirmationToken token = LineConfirmationToken.builder()
                .token("tok-all-decline")
                .action("waitlist_decline_all")
                .params("{\"sessionId\":\"200\",\"playerId\":\"77\"}")
                .playerId(77L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(lineConfirmationService.consumeToken("tok-all-decline", 77L)).thenReturn(token);
        when(waitlistPromotionService.respondToOfferAll(200L, 77L, false)).thenReturn(2);

        String body = postbackBody("action=confirm_waitlist_decline_all&token=tok-all-decline");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(waitlistPromotionService).respondToOfferAll(200L, 77L, false);
        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-2"), eq("2試合のオファーを辞退しました。次の方に通知します。"));
    }

    @Test
    @DisplayName("same_day_join_all postback sends confirmation dialog")
    void handleWebhook_sameDayJoinAll_sendsConfirmation() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        PracticeSession session = PracticeSession.builder()
                .id(300L)
                .sessionDate(LocalDate.now())
                .organizationId(1L)
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(practiceSessionRepository.findById(300L)).thenReturn(Optional.of(session));
        when(lineConfirmationService.createToken(eq("same_day_join_all"), anyString(), eq(77L)))
                .thenReturn("confirm-tok-all");
        when(lineNotificationService.buildConfirmationFlex(
                eq("same_day_join_all"), anyString(), eq(null), anyString(), anyString()))
                .thenReturn(java.util.Map.of("type", "bubble"));

        String body = postbackBody("action=same_day_join_all&sessionId=300");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(lineConfirmationService).createToken(eq("same_day_join_all"), anyString(), eq(77L));
    }

    @Test
    @DisplayName("confirm_same_day_join_all postback calls handleSameDayJoinAll and returns joined count message")
    void handleWebhook_confirmSameDayJoinAll_executesJoinAll() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        LineConfirmationToken token = LineConfirmationToken.builder()
                .token("tok-join-all")
                .action("same_day_join_all")
                .params("{\"sessionId\":\"300\"}")
                .playerId(77L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(lineConfirmationService.consumeToken("tok-join-all", 77L)).thenReturn(token);
        when(waitlistPromotionService.handleSameDayJoinAll(eq(300L), eq(77L), isNull())).thenReturn(2);

        String body = postbackBody("action=confirm_same_day_join_all&token=tok-join-all");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(waitlistPromotionService).handleSameDayJoinAll(eq(300L), eq(77L), isNull());
        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-2"), eq("2試合の参加登録が完了しました！練習頑張ってください！"));
    }

    @Test
    @DisplayName("confirm_same_day_join_all with zero joined returns no-vacancy message")
    void handleWebhook_confirmSameDayJoinAll_zeroJoined_returnsNoVacancy() throws Exception {
        LineChannel channel = channel();
        LineChannelAssignment assignment = linkedAssignment();
        LineConfirmationToken token = LineConfirmationToken.builder()
                .token("tok-join-all-0")
                .action("same_day_join_all")
                .params("{\"sessionId\":\"300\"}")
                .playerId(77L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(lineChannelRepository.findByLineChannelId("CH001")).thenReturn(Optional.of(channel));
        when(lineMessagingService.verifySignature(eq("secret"), anyString(), eq("sig"))).thenReturn(true);
        when(lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                "U777", 10L, LineChannelAssignment.AssignmentStatus.LINKED))
                .thenReturn(Optional.of(assignment));
        when(lineConfirmationService.consumeToken("tok-join-all-0", 77L)).thenReturn(token);
        when(waitlistPromotionService.handleSameDayJoinAll(eq(300L), eq(77L), isNull())).thenReturn(0);

        String body = postbackBody("action=confirm_same_day_join_all&token=tok-join-all-0");

        mockMvc.perform(post("/api/line/webhook/CH001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-line-signature", "sig")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(waitlistPromotionService).handleSameDayJoinAll(eq(300L), eq(77L), isNull());
        verify(lineMessagingService).sendReplyMessage(eq("token"), eq("reply-token-2"), eq("参加可能な空き試合がありませんでした。"));
    }

    private String postbackBody(String data) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("events", java.util.List.of(
                        java.util.Map.of(
                                "type", "postback",
                                "replyToken", "reply-token-2",
                                "source", java.util.Map.of("userId", "U777"),
                                "postback", java.util.Map.of("data", data)
                        )
                ))
        );
    }

    private LineChannel channel() {
        return LineChannel.builder()
                .id(10L)
                .lineChannelId("CH001")
                .channelSecret("secret")
                .channelAccessToken("token")
                .build();
    }
}
