package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukeWriteStatusDto;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DensukeWriteService の単体テスト
 *
 * 注意: Jsoup.connect() を直接使用しているため、HTTP リクエストが必要なパスは
 * テスト対象外とし、HTTP が不要なパス（URL 未設定・dirty なし・ユーティリティ）をテストする。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeWriteService 単体テスト")
class DensukeWriteServiceTest {

    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private DensukeMemberMappingRepository densukeMemberMappingRepository;
    @Mock private DensukeRowIdRepository densukeRowIdRepository;
    @Mock private PlayerRepository playerRepository;

    @InjectMocks
    private DensukeWriteService densukeWriteService;

    // ----------------------------------------------------------------
    // getStatus テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("初期状態のgetStatusはnull値を返す")
    void testGetStatusInitial() {
        DensukeWriteStatusDto status = densukeWriteService.getStatus(1L);

        assertThat(status.getLastAttemptAt()).isNull();
        assertThat(status.getLastSuccessAt()).isNull();
        assertThat(status.getErrors()).isEmpty();
        assertThat(status.getPendingCount()).isEqualTo(0);
    }

    // ----------------------------------------------------------------
    // writeToDensuke: URL未設定のケース
    // ----------------------------------------------------------------

    @Test
    @DisplayName("DensukeURLが未設定の場合はdirtyクエリを実行しない")
    void testWriteToDensuke_noUrl_skipsAll() {
        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenReturn(List.of());

        densukeWriteService.writeToDensuke();

        verify(practiceParticipantRepository, never()).findDirtyForDensukeSync(any());
    }

    @Test
    @DisplayName("DensukeURLが未設定の場合はpendingCountが0になる")
    void testWriteToDensuke_noUrl_pendingCountZero() {
        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenReturn(List.of());

        densukeWriteService.writeToDensuke();

        assertThat(densukeWriteService.getStatus(1L).getPendingCount()).isEqualTo(0);
    }

    // ----------------------------------------------------------------
    // writeToDensuke: dirty参加者なしのケース
    // ----------------------------------------------------------------

    @Test
    @DisplayName("dirty=trueの参加者がいない場合は何も書き込まない")
    void testWriteToDensuke_noDirtyParticipants_skipsWrite() {
        DensukeUrl url = DensukeUrl.builder()
                .id(1L).year(2026).month(4).organizationId(1L)
                .url("https://densuke.biz/list?cd=test123").build();
        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int y = (int) inv.getArgument(0);
                    int m = (int) inv.getArgument(1);
                    if (y == 2026 && m == 4) return List.of(url);
                    return List.of();
                });

        PracticeSession session = PracticeSession.builder()
                .id(10L).sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), eq(1L)))
                .thenAnswer(inv -> {
                    int y = (int) inv.getArgument(0);
                    int m = (int) inv.getArgument(1);
                    if (y == 2026 && m == 4) return List.of(session);
                    return Collections.emptyList();
                });

        when(practiceParticipantRepository.findDirtyForDensukeSync(any()))
                .thenReturn(Collections.emptyList());

        densukeWriteService.writeToDensuke();

        verify(densukeMemberMappingRepository, never()).findByDensukeUrlIdAndPlayerId(any(), any());
        assertThat(densukeWriteService.getStatus(1L).getPendingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("writeToDensukeForOrganization: URL未登録時はエラーステータスを設定する")
    void testWriteToDensukeForOrganization_noUrl_setsErrorStatus() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2025, 1, 1L))
                .thenReturn(Optional.empty());

        densukeWriteService.writeToDensukeForOrganization(2025, 1, 1L);

        DensukeWriteStatusDto status = densukeWriteService.getStatus(1L);
        assertThat(status.getLastAttemptAt()).isNotNull();
        assertThat(status.getPendingCount()).isEqualTo(0);
        assertThat(status.getErrors()).containsExactly("対象年月の伝助URLが未登録のため書き込みをスキップしました");
    }

    // ----------------------------------------------------------------
    // URL パースユーティリティのテスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("extractCd: cdパラメータを正しく抽出する")
    void testExtractCd_normal() {
        assertThat(DensukeWriteService.extractCd("https://densuke.biz/list?cd=abc123"))
                .isEqualTo("abc123");
    }

    @Test
    @DisplayName("extractCd: パラメータが複数ある場合もcdを返す")
    void testExtractCd_multipleParams() {
        assertThat(DensukeWriteService.extractCd("https://densuke.biz/list?cd=xyz&mi=42"))
                .isEqualTo("xyz");
    }

    @Test
    @DisplayName("extractCd: cdがない場合はnullを返す")
    void testExtractCd_noCd() {
        assertThat(DensukeWriteService.extractCd("https://densuke.biz/list?foo=bar"))
                .isNull();
    }

    // ----------------------------------------------------------------
    // 未入力保護: BYEのみdirtyの場合は同期対象外
    // ----------------------------------------------------------------

    @Test
    @DisplayName("BYE(matchNumber=null)のみdirtyの場合はプレイヤーの書き込みが発生しない")
    void testWriteToDensuke_byeOnlyDirty_skipsWrite() {
        DensukeUrl url = DensukeUrl.builder()
                .id(1L).year(2026).month(4).organizationId(1L)
                .url("https://densuke.biz/list?cd=test123").build();
        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int y = (int) inv.getArgument(0);
                    int m = (int) inv.getArgument(1);
                    if (y == 2026 && m == 4) return List.of(url);
                    return List.of();
                });

        PracticeSession session = PracticeSession.builder()
                .id(10L).sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), eq(1L)))
                .thenAnswer(inv -> {
                    int y = (int) inv.getArgument(0);
                    int m = (int) inv.getArgument(1);
                    if (y == 2026 && m == 4) return List.of(session);
                    return Collections.emptyList();
                });

        // findDirtyForDensukeSync は matchNumber IS NOT NULL なのでBYEを返さない → 空
        when(practiceParticipantRepository.findDirtyForDensukeSync(any()))
                .thenReturn(Collections.emptyList());

        densukeWriteService.writeToDensuke();

        // BYEのみdirtyの場合、書き込み処理に進まない
        verify(densukeMemberMappingRepository, never()).findByDensukeUrlIdAndPlayerId(any(), any());
        assertThat(densukeWriteService.getStatus(1L).getPendingCount()).isEqualTo(0);
    }

    // ----------------------------------------------------------------
    // URL パースユーティリティのテスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("extractBase: ベースURLを正しく抽出する")
    void testExtractBase_normal() {
        assertThat(DensukeWriteService.extractBase("https://densuke.biz/list?cd=abc123"))
                .isEqualTo("https://densuke.biz/");
    }

    @Test
    @DisplayName("extractBase: nullを渡した場合はnullを返す")
    void testExtractBase_null() {
        assertThat(DensukeWriteService.extractBase(null)).isNull();
    }
}
