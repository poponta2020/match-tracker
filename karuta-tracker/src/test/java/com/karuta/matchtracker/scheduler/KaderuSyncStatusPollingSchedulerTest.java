package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.service.KaderuSyncTriggerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * KaderuSyncStatusPollingScheduler の単体テスト。
 *
 * <p>scheduler が KaderuSyncTriggerService のメソッドを「Spring の bean 呼び出し」として
 * 叩いていることを検証する。同一クラス内の self-invocation だと @Transactional プロキシが
 * バイパスされるため、独立呼び出しになっているかを保証する。
 *
 * <p>本テストでは Mockito モックの service を注入することで、scheduler が
 * listPendingIds() / processPendingEvent() を呼ぶ「外側ループ責務」と
 * 「1件の例外が他に波及しない」性質を検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KaderuSyncStatusPollingScheduler 単体テスト")
class KaderuSyncStatusPollingSchedulerTest {

    @Mock private KaderuSyncTriggerService kaderuSyncTriggerService;

    @InjectMocks
    private KaderuSyncStatusPollingScheduler scheduler;

    @Test
    @DisplayName("pending なし → processPendingEvent は呼ばない")
    void poll_doesNothingWhenNoPending() {
        when(kaderuSyncTriggerService.listPendingIds()).thenReturn(Collections.emptyList());

        scheduler.pollPendingKaderuSyncEvents();

        verify(kaderuSyncTriggerService, never()).processPendingEvent(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("listPendingIds が返した全 id に対して processPendingEvent を Service 経由で呼ぶ")
    void poll_invokesProcessForEachPendingId() {
        when(kaderuSyncTriggerService.listPendingIds()).thenReturn(List.of(1L, 2L, 3L));

        scheduler.pollPendingKaderuSyncEvents();

        verify(kaderuSyncTriggerService).processPendingEvent(1L);
        verify(kaderuSyncTriggerService).processPendingEvent(2L);
        verify(kaderuSyncTriggerService).processPendingEvent(3L);
        verify(kaderuSyncTriggerService, times(3))
                .processPendingEvent(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("1件の processPendingEvent 例外は他のイベント処理に波及しない")
    void poll_isolatesPerEventExceptions() {
        when(kaderuSyncTriggerService.listPendingIds()).thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("boom")).when(kaderuSyncTriggerService).processPendingEvent(1L);

        scheduler.pollPendingKaderuSyncEvents();

        // 2件目は実行されること
        verify(kaderuSyncTriggerService).processPendingEvent(2L);
    }

    @Test
    @DisplayName("listPendingIds 自体が例外 → 処理を打ち切り、processPendingEvent は呼ばない")
    void poll_abortsWhenListPendingFails() {
        when(kaderuSyncTriggerService.listPendingIds())
                .thenThrow(new RuntimeException("db down"));

        scheduler.pollPendingKaderuSyncEvents();

        verify(kaderuSyncTriggerService, never()).processPendingEvent(org.mockito.ArgumentMatchers.anyLong());
    }
}
