package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.LineChatWorkerSessionWarningRequest;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.service.LineChatReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link LineChatWorkerController#reportSessionWarning} の入力検証（line-chat-auto-relogin タスク2）。
 * サービストークン認証は {@code ServiceTokenInterceptorTest} が担保するため、ここでは契約検証のみ。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineChatWorkerController.reportSessionWarning")
class LineChatWorkerControllerTest {

    @Mock private LineChatReservationService reservationService;
    @Mock private LineBroadcastGroupRepository groupRepository;

    private LineChatWorkerController controller() {
        return new LineChatWorkerController(reservationService, groupRepository);
    }

    @Test
    @DisplayName("daysRemaining が null なら 400 を投げ、警告を送らない（欠落を『まもなく失効』に丸めない）")
    void nullDaysRemainingRejected() {
        assertThatThrownBy(() -> controller().reportSessionWarning(
                new LineChatWorkerSessionWarningRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("daysRemaining");
        verify(reservationService, never()).warnSessionExpiring(anyInt());
    }

    @Test
    @DisplayName("正当な残日数はサービスへそのまま渡す（0以下も許容）")
    void validDaysRemainingRelayed() {
        controller().reportSessionWarning(new LineChatWorkerSessionWarningRequest(2));
        verify(reservationService).warnSessionExpiring(2);

        controller().reportSessionWarning(new LineChatWorkerSessionWarningRequest(0));
        verify(reservationService).warnSessionExpiring(0);
    }
}
