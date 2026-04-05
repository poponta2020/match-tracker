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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    // 未入力保護: buildRegistFormData の直接テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("通常同期: dirty行のみformDataに含まれ、未登録マスや非dirty行は含まれない")
    void testBuildRegistFormData_normalSync_onlyDirtyKeysIncluded() {
        // Setup: 2セッション(4/12, 4/19) × 各3試合、プレイヤーは4/12の1試合目のみdirty
        PracticeSession session1 = PracticeSession.builder()
                .id(10L).sessionDate(LocalDate.of(2026, 4, 12)).totalMatches(3).build();
        PracticeSession session2 = PracticeSession.builder()
                .id(20L).sessionDate(LocalDate.of(2026, 4, 19)).totalMatches(3).build();
        List<PracticeSession> sessions = List.of(session1, session2);

        // dirty参加者: 4/12の1試合目のみ
        PracticeParticipant dirtyPp = PracticeParticipant.builder()
                .sessionId(10L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(true).build();
        List<PracticeParticipant> dirtyParticipants = List.of(dirtyPp);

        // 全参加者マップ: 4/12の1試合目=WON, 4/12の2試合目=WON(非dirty), 他は未登録
        Map<String, PracticeParticipant> bySessionAndMatch = Map.of(
                "10_1", dirtyPp,
                "10_2", PracticeParticipant.builder()
                        .sessionId(10L).playerId(1L).matchNumber(2)
                        .status(ParticipantStatus.WON).dirty(false).build()
        );

        // 全スロットにrow IDがある想定
        Map<String, String> rowIdsByKey = new LinkedHashMap<>();
        rowIdsByKey.put("10_1", "101");
        rowIdsByKey.put("10_2", "102");
        rowIdsByKey.put("10_3", "103");
        rowIdsByKey.put("20_1", "201");
        rowIdsByKey.put("20_2", "202");
        rowIdsByKey.put("20_3", "203");

        // Act
        DensukeWriteService.RegistFormResult result = densukeWriteService.buildRegistFormData(
                "pageId", "mi1", "テスト選手",
                dirtyParticipants, sessions, bySessionAndMatch, rowIdsByKey, false);

        // Assert: dirty行(10_1)のjoin-101=3のみ含まれる
        assertThat(result.formData).containsEntry("join-101", "3");
        assertThat(result.writtenKeys).containsExactly("10_1");

        // 非dirty行(10_2)や未登録マス(10_3, 20_*)は含まれない
        assertThat(result.formData).doesNotContainKey("join-102");
        assertThat(result.formData).doesNotContainKey("join-103");
        assertThat(result.formData).doesNotContainKey("join-201");
        assertThat(result.formData).doesNotContainKey("join-202");
        assertThat(result.formData).doesNotContainKey("join-203");
    }

    @Test
    @DisplayName("通常同期: 未登録マス(pp=null)がvalue=1(×)で送信されないこと")
    void testBuildRegistFormData_normalSync_unregisteredNotSent() {
        // Setup: 1セッション×3試合、1試合目のみdirty、2,3試合目は未登録
        PracticeSession session = PracticeSession.builder()
                .id(10L).sessionDate(LocalDate.of(2026, 4, 12)).totalMatches(3).build();

        PracticeParticipant dirtyPp = PracticeParticipant.builder()
                .sessionId(10L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(true).build();

        Map<String, PracticeParticipant> bySessionAndMatch = Map.of("10_1", dirtyPp);
        Map<String, String> rowIdsByKey = new LinkedHashMap<>();
        rowIdsByKey.put("10_1", "101");
        rowIdsByKey.put("10_2", "102");
        rowIdsByKey.put("10_3", "103");

        // Act
        DensukeWriteService.RegistFormResult result = densukeWriteService.buildRegistFormData(
                "pageId", "mi1", "テスト選手",
                List.of(dirtyPp), List.of(session), bySessionAndMatch, rowIdsByKey, false);

        // Assert: 1試合目のみ送信、2,3試合目(未登録)は送信されない
        assertThat(result.formData).containsEntry("join-101", "3");
        assertThat(result.formData).doesNotContainKey("join-102");
        assertThat(result.formData).doesNotContainKey("join-103");
    }

    @Test
    @DisplayName("抽選確定同期: 既存挙動維持（アクティブのみ書き込み、未登録はスキップ）")
    void testBuildRegistFormData_lotteryConfirmation_existingBehavior() {
        // Setup: 1セッション×3試合
        PracticeSession session = PracticeSession.builder()
                .id(10L).sessionDate(LocalDate.of(2026, 4, 12)).totalMatches(3).build();

        PracticeParticipant wonPp = PracticeParticipant.builder()
                .sessionId(10L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        PracticeParticipant cancelledPp = PracticeParticipant.builder()
                .sessionId(10L).playerId(1L).matchNumber(2)
                .status(ParticipantStatus.CANCELLED).dirty(false).build();

        Map<String, PracticeParticipant> bySessionAndMatch = Map.of(
                "10_1", wonPp, "10_2", cancelledPp);
        Map<String, String> rowIdsByKey = new LinkedHashMap<>();
        rowIdsByKey.put("10_1", "101");
        rowIdsByKey.put("10_2", "102");
        rowIdsByKey.put("10_3", "103");

        // Act: lotteryConfirmation=true
        DensukeWriteService.RegistFormResult result = densukeWriteService.buildRegistFormData(
                "pageId", "mi1", "テスト選手",
                List.of(wonPp, cancelledPp), List.of(session), bySessionAndMatch, rowIdsByKey, true);

        // Assert: WONのみ送信、CANCELLED・未登録はスキップ
        assertThat(result.formData).containsEntry("join-101", "3");
        assertThat(result.formData).doesNotContainKey("join-102");
        assertThat(result.formData).doesNotContainKey("join-103");
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

    // ----------------------------------------------------------------
    // saveMemberMapping: 重複防止テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("saveMemberMapping: 競合なしの場合はtrueを返しマッピングが保存される")
    void testSaveMemberMapping_noConflict_returnsTrue() {
        when(densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(1L, "mi1"))
                .thenReturn(Optional.empty());
        when(densukeMemberMappingRepository.save(any(DensukeMemberMapping.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        boolean result = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "テスト選手");

        assertThat(result).isTrue();
        verify(densukeMemberMappingRepository).save(any(DensukeMemberMapping.class));
    }

    @Test
    @DisplayName("saveMemberMapping: 同一プレイヤーで既存の場合はtrueを返す（再利用）")
    void testSaveMemberMapping_samePlayer_returnsTrue_noSave() {
        DensukeMemberMapping existing = DensukeMemberMapping.builder()
                .densukeUrlId(1L).playerId(100L).densukeMemberId("mi1").build();
        when(densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(1L, "mi1"))
                .thenReturn(Optional.of(existing));

        boolean result = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "テスト選手");

        assertThat(result).isTrue();
        verify(densukeMemberMappingRepository, never()).save(any(DensukeMemberMapping.class));
    }

    @Test
    @DisplayName("saveMemberMapping: 別プレイヤーに競合する場合はfalseを返し保存しない")
    void testSaveMemberMapping_conflict_returnsFalse() {
        DensukeMemberMapping existing = DensukeMemberMapping.builder()
                .densukeUrlId(1L).playerId(200L).densukeMemberId("mi1").build();
        when(densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(1L, "mi1"))
                .thenReturn(Optional.of(existing));

        boolean result = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "テスト選手");

        assertThat(result).isFalse();
        verify(densukeMemberMappingRepository, never()).save(any(DensukeMemberMapping.class));
    }

    @Test
    @DisplayName("saveMemberMapping: 一意制約例外後に別プレイヤーが登録済みならfalseを返す")
    void testSaveMemberMapping_dataIntegrityViolation_otherPlayer_returnsFalse() {
        DensukeMemberMapping otherPlayerMapping = DensukeMemberMapping.builder()
                .densukeUrlId(1L).playerId(200L).densukeMemberId("mi1").build();
        when(densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(1L, "mi1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(otherPlayerMapping));
        when(densukeMemberMappingRepository.save(any(DensukeMemberMapping.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        boolean result = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "テスト選手");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("saveMemberMapping: 一意制約例外後に同一プレイヤーが登録済みならtrueを返す（TOCTOU救済）")
    void testSaveMemberMapping_dataIntegrityViolation_samePlayer_returnsTrue() {
        DensukeMemberMapping samePlayerMapping = DensukeMemberMapping.builder()
                .densukeUrlId(1L).playerId(100L).densukeMemberId("mi1").build();
        when(densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(1L, "mi1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(samePlayerMapping));
        when(densukeMemberMappingRepository.save(any(DensukeMemberMapping.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        boolean result = densukeWriteService.saveMemberMapping(1L, 100L, "mi1", "テスト選手");

        assertThat(result).isTrue();
    }
}
