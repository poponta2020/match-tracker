package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.service.LineMessagingService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineMessageCountSyncScheduler テスト")
class LineMessageCountSyncSchedulerTest {

    @Mock
    private LineChannelRepository lineChannelRepository;
    @Mock
    private LineMessagingService lineMessagingService;

    @InjectMocks
    private LineMessageCountSyncScheduler scheduler;

    private LineChannel buildChannel(Long id, String name, String token) {
        return LineChannel.builder()
                .id(id)
                .channelName(name)
                .channelAccessToken(token)
                .monthlyMessageCount(0)
                .build();
    }

    @Test
    @DisplayName("正常系: 全チャネルの送信数がAPIの値で更新される")
    void syncMessageCounts_updatesAllChannels() {
        LineChannel ch1 = buildChannel(1L, "Channel1", "token1");
        LineChannel ch2 = buildChannel(2L, "Channel2", "token2");
        when(lineChannelRepository.findAll()).thenReturn(List.of(ch1, ch2));
        when(lineMessagingService.getMonthlyMessageConsumption("token1")).thenReturn(42);
        when(lineMessagingService.getMonthlyMessageConsumption("token2")).thenReturn(100);

        scheduler.syncMessageCounts();

        verify(lineChannelRepository).save(ch1);
        verify(lineChannelRepository).save(ch2);
        assertEquals(42, ch1.getMonthlyMessageCount());
        assertEquals(100, ch2.getMonthlyMessageCount());
        assertNotNull(ch1.getMessageCountResetAt());
    }

    @Test
    @DisplayName("API失敗時: 同月内ならカウント据え置き、saveされない")
    void syncMessageCounts_skipsFailedChannels_sameMonth() {
        LineChannel ch1 = buildChannel(1L, "Channel1", "token1");
        // 前回同期が今月 → 月跨ぎフォールバック不要
        ch1.setMessageCountResetAt(JstDateTimeUtil.now());
        ch1.setMonthlyMessageCount(150);

        LineChannel ch2 = buildChannel(2L, "Channel2", "token2");
        when(lineChannelRepository.findAll()).thenReturn(List.of(ch1, ch2));
        when(lineMessagingService.getMonthlyMessageConsumption("token1")).thenReturn(-1);
        when(lineMessagingService.getMonthlyMessageConsumption("token2")).thenReturn(50);

        scheduler.syncMessageCounts();

        verify(lineChannelRepository, times(1)).save(ch2);
        // ch1はsaveされない（同月内なので据え置き）
        verify(lineChannelRepository, never()).save(ch1);
        assertEquals(150, ch1.getMonthlyMessageCount());
        assertEquals(50, ch2.getMonthlyMessageCount());
    }

    @Test
    @DisplayName("月跨ぎ + API失敗: カウントが0にリセットされる")
    void syncMessageCounts_resetsOnMonthChange_whenApiFails() {
        LineChannel ch = buildChannel(1L, "Channel1", "token1");
        ch.setMonthlyMessageCount(200);
        // 前回同期が先月
        ch.setMessageCountResetAt(JstDateTimeUtil.now().minusMonths(1));

        when(lineChannelRepository.findAll()).thenReturn(List.of(ch));
        when(lineMessagingService.getMonthlyMessageConsumption("token1")).thenReturn(-1);

        scheduler.syncMessageCounts();

        verify(lineChannelRepository).save(ch);
        assertEquals(0, ch.getMonthlyMessageCount());
    }

    @Test
    @DisplayName("月跨ぎ + API失敗 + messageCountResetAtがnull: 0にリセットされる")
    void syncMessageCounts_resetsWhenLastSyncIsNull() {
        LineChannel ch = buildChannel(1L, "Channel1", "token1");
        ch.setMonthlyMessageCount(200);
        ch.setMessageCountResetAt(null);

        when(lineChannelRepository.findAll()).thenReturn(List.of(ch));
        when(lineMessagingService.getMonthlyMessageConsumption("token1")).thenReturn(-1);

        scheduler.syncMessageCounts();

        // messageCountResetAtがnullの場合もリセットする（移行直後の既存データ対応）
        verify(lineChannelRepository).save(ch);
        assertEquals(0, ch.getMonthlyMessageCount());
    }

    @Test
    @DisplayName("例外発生時: そのチャネルはスキップし、処理が中断しない")
    void syncMessageCounts_continuesOnException() {
        LineChannel ch1 = buildChannel(1L, "Channel1", "token1");
        LineChannel ch2 = buildChannel(2L, "Channel2", "token2");
        when(lineChannelRepository.findAll()).thenReturn(List.of(ch1, ch2));
        when(lineMessagingService.getMonthlyMessageConsumption("token1"))
                .thenThrow(new RuntimeException("API error"));
        when(lineMessagingService.getMonthlyMessageConsumption("token2")).thenReturn(30);

        scheduler.syncMessageCounts();

        verify(lineChannelRepository, never()).save(ch1);
        verify(lineChannelRepository).save(ch2);
        assertEquals(30, ch2.getMonthlyMessageCount());
    }

    @Test
    @DisplayName("チャネルが0件の場合: エラーなく完了する")
    void syncMessageCounts_noChannels() {
        when(lineChannelRepository.findAll()).thenReturn(List.of());

        scheduler.syncMessageCounts();

        verify(lineMessagingService, never()).getMonthlyMessageConsumption(any());
        verify(lineChannelRepository, never()).save(any());
    }

    @Test
    @DisplayName("InterruptedException発生時: ループが中断しスレッド割り込み状態が復元される")
    void syncMessageCounts_interruptedStopsLoop() {
        LineChannel ch1 = buildChannel(1L, "Channel1", "token1");
        LineChannel ch2 = buildChannel(2L, "Channel2", "token2");
        when(lineChannelRepository.findAll()).thenReturn(List.of(ch1, ch2));
        // ch1の同期は成功するが、直後のsleepで割り込みが発生する想定
        when(lineMessagingService.getMonthlyMessageConsumption("token1")).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return 42;
        });

        scheduler.syncMessageCounts();

        // ch1は同期成功（interrupt前にsave済み）
        verify(lineChannelRepository).save(ch1);
        assertEquals(42, ch1.getMonthlyMessageCount());
        // ch2はループ中断により処理されない
        verify(lineMessagingService, never()).getMonthlyMessageConsumption("token2");
        // スレッドの割り込み状態が復元されていることを検証
        assertTrue(Thread.interrupted(), "割り込みフラグが復元されていること");
    }
}
