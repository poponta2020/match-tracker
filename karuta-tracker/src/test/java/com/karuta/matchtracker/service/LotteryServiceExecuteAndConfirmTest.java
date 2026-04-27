package com.karuta.matchtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.ConfirmLotteryResponse;
import com.karuta.matchtracker.dto.DensukeWriteResult;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link LotteryService#executeAndConfirmLottery} の伝助書き戻し失敗伝搬テスト。
 *
 * <p>監査レポート（2026-04-27）の指摘:
 * 「{@code DensukeWriteService.writeAllForLotteryConfirmation} は HTTP 400+ や
 * メンバーID取得失敗、一覧ページ取得失敗を内部で握りつぶして正常終了するため、
 * 呼び出し元には成功として見える」を解消したことを担保する。
 *
 * <p>抽選自体は「セッション 0 件 → SUCCESS で早期return」のパスを使い、
 * 伝助書き戻しの戻り値（{@link DensukeWriteResult}）が {@link ConfirmLotteryResponse} に
 * 正しく反映されることだけを検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LotteryService#executeAndConfirmLottery 伝助書き戻し失敗の伝搬")
class LotteryServiceExecuteAndConfirmTest {

    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private NotificationService notificationService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private DensukeWriteService densukeWriteService;
    @Mock private ObjectMapper objectMapper;
    @Mock private LotteryQueryService lotteryQueryService;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;

    @InjectMocks
    private LotteryService lotteryService;

    private static final Long ORG_ID = 1L;
    private static final Long EXECUTOR_ID = 99L;

    /**
     * 「セッション 0 件 → executeLottery が SUCCESS で即 return」ルートのために最小限の
     * mock を仕込む。ここから先は executeAndConfirmLottery の write-back 分岐だけが走る。
     */
    private void stubLotteryExecutionSucceeds() {
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), eq(ORG_ID)))
                .thenReturn(List.of());
        when(lotteryExecutionRepository.save(any(LotteryExecution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("HTTP 400+ エラーが errors に含まれる場合 densukeWriteSucceeded=false で伝搬する")
    void executeAndConfirm_httpErrorPropagatesAsFailure() {
        stubLotteryExecutionSucceeds();
        when(densukeWriteService.writeAllForLotteryConfirmation(eq(ORG_ID), anyInt(), anyInt()))
                .thenReturn(DensukeWriteResult.failure(List.of("選手[山田太郎]: regist HTTP 500")));

        ConfirmLotteryResponse resp = lotteryService.executeAndConfirmLottery(
                2026, 4, EXECUTOR_ID, ORG_ID, 1L, List.of());

        assertThat(resp.getExecution().getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(resp.isDensukeWriteSucceeded()).isFalse();
        assertThat(resp.getDensukeWriteError()).contains("regist HTTP 500");
    }

    @Test
    @DisplayName("メンバーID取得失敗が errors に含まれる場合 densukeWriteSucceeded=false で伝搬する")
    void executeAndConfirm_memberIdFailurePropagatesAsFailure() {
        stubLotteryExecutionSucceeds();
        when(densukeWriteService.writeAllForLotteryConfirmation(eq(ORG_ID), anyInt(), anyInt()))
                .thenReturn(DensukeWriteResult.failure(List.of("選手[佐藤花子]: メンバーIDの取得に失敗")));

        ConfirmLotteryResponse resp = lotteryService.executeAndConfirmLottery(
                2026, 4, EXECUTOR_ID, ORG_ID, 1L, List.of());

        assertThat(resp.isDensukeWriteSucceeded()).isFalse();
        assertThat(resp.getDensukeWriteError()).contains("メンバーIDの取得に失敗");
    }

    @Test
    @DisplayName("リストページ取得失敗が errors に含まれる場合 densukeWriteSucceeded=false で伝搬する")
    void executeAndConfirm_listPageFetchFailurePropagatesAsFailure() {
        stubLotteryExecutionSucceeds();
        when(densukeWriteService.writeAllForLotteryConfirmation(eq(ORG_ID), anyInt(), anyInt()))
                .thenReturn(DensukeWriteResult.failure(List.of("伝助リストページ取得失敗(cd=abc): connect timed out")));

        ConfirmLotteryResponse resp = lotteryService.executeAndConfirmLottery(
                2026, 4, EXECUTOR_ID, ORG_ID, 1L, List.of());

        assertThat(resp.isDensukeWriteSucceeded()).isFalse();
        assertThat(resp.getDensukeWriteError()).contains("伝助リストページ取得失敗");
    }

    @Test
    @DisplayName("複数の errors は densukeWriteError にセミコロン区切りで連結される")
    void executeAndConfirm_multipleErrorsAreJoined() {
        stubLotteryExecutionSucceeds();
        when(densukeWriteService.writeAllForLotteryConfirmation(eq(ORG_ID), anyInt(), anyInt()))
                .thenReturn(DensukeWriteResult.failure(List.of(
                        "選手[山田]: regist HTTP 500",
                        "選手[佐藤]: メンバーIDの取得に失敗")));

        ConfirmLotteryResponse resp = lotteryService.executeAndConfirmLottery(
                2026, 4, EXECUTOR_ID, ORG_ID, 1L, List.of());

        assertThat(resp.isDensukeWriteSucceeded()).isFalse();
        assertThat(resp.getDensukeWriteError())
                .contains("regist HTTP 500")
                .contains("メンバーIDの取得に失敗")
                .contains(";");
    }

    @Test
    @DisplayName("DensukeWriteResult.success() が返ると densukeWriteSucceeded=true で error は null")
    void executeAndConfirm_successResult() {
        stubLotteryExecutionSucceeds();
        when(densukeWriteService.writeAllForLotteryConfirmation(eq(ORG_ID), anyInt(), anyInt()))
                .thenReturn(DensukeWriteResult.success());

        ConfirmLotteryResponse resp = lotteryService.executeAndConfirmLottery(
                2026, 4, EXECUTOR_ID, ORG_ID, 1L, List.of());

        assertThat(resp.isDensukeWriteSucceeded()).isTrue();
        assertThat(resp.getDensukeWriteError()).isNull();
    }

    @Test
    @DisplayName("write-back が例外を投げた場合も densukeWriteSucceeded=false で伝搬する（既存挙動の維持）")
    void executeAndConfirm_exceptionStillMarksFailure() {
        stubLotteryExecutionSucceeds();
        when(densukeWriteService.writeAllForLotteryConfirmation(eq(ORG_ID), anyInt(), anyInt()))
                .thenThrow(new RuntimeException(new IOException("network down")));

        ConfirmLotteryResponse resp = lotteryService.executeAndConfirmLottery(
                2026, 4, EXECUTOR_ID, ORG_ID, 1L, List.of());

        assertThat(resp.isDensukeWriteSucceeded()).isFalse();
        assertThat(resp.getDensukeWriteError()).contains("network down");
    }
}
