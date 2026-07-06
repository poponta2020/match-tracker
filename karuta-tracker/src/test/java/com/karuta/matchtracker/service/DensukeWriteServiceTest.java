package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukeWriteStatusDto;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
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
    @Mock private DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private DensukeScraper densukeScraper;
    @Mock private LineNotificationService lineNotificationService;

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
        // 実装は writeToDensuke() で today() を呼んで今月と来月のURLを取得するため、
        // テストセットアップとサービス呼び出しの間で月またぎが起きないよう today() を固定する
        LocalDate fixedToday = LocalDate.of(2026, 5, 24);
        int currentYear = fixedToday.getYear();
        int currentMonth = fixedToday.getMonthValue();

        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class, CALLS_REAL_METHODS)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(fixedToday);

            DensukeUrl url = DensukeUrl.builder()
                    .id(1L).year(currentYear).month(currentMonth).organizationId(1L)
                    .url("https://densuke.biz/list?cd=test123").build();
            when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                    .thenAnswer(inv -> {
                        int y = (int) inv.getArgument(0);
                        int m = (int) inv.getArgument(1);
                        if (y == currentYear && m == currentMonth) return List.of(url);
                        return List.of();
                    });

            PracticeSession session = PracticeSession.builder()
                    .id(10L).sessionDate(fixedToday.withDayOfMonth(1)).totalMatches(3).build();
            when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), eq(1L)))
                    .thenAnswer(inv -> {
                        int y = (int) inv.getArgument(0);
                        int m = (int) inv.getArgument(1);
                        if (y == currentYear && m == currentMonth) return List.of(session);
                        return Collections.emptyList();
                    });

            when(practiceParticipantRepository.findDirtyForDensukeSync(any()))
                    .thenReturn(Collections.emptyList());

            densukeWriteService.writeToDensuke();

            verify(densukeMemberMappingRepository, never()).findByDensukeUrlIdAndPlayerId(any(), any());
            assertThat(densukeWriteService.getStatus(1L).getPendingCount()).isEqualTo(0);
        }
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
        // 実装は writeToDensuke() で today() を呼んで今月と来月のURLを取得するため、
        // テストセットアップとサービス呼び出しの間で月またぎが起きないよう today() を固定する
        LocalDate fixedToday = LocalDate.of(2026, 5, 24);
        int currentYear = fixedToday.getYear();
        int currentMonth = fixedToday.getMonthValue();

        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class, CALLS_REAL_METHODS)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(fixedToday);

            DensukeUrl url = DensukeUrl.builder()
                    .id(1L).year(currentYear).month(currentMonth).organizationId(1L)
                    .url("https://densuke.biz/list?cd=test123").build();
            when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                    .thenAnswer(inv -> {
                        int y = (int) inv.getArgument(0);
                        int m = (int) inv.getArgument(1);
                        if (y == currentYear && m == currentMonth) return List.of(url);
                        return List.of();
                    });

            PracticeSession session = PracticeSession.builder()
                    .id(10L).sessionDate(fixedToday.withDayOfMonth(1)).totalMatches(3).build();
            when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), eq(1L)))
                    .thenAnswer(inv -> {
                        int y = (int) inv.getArgument(0);
                        int m = (int) inv.getArgument(1);
                        if (y == currentYear && m == currentMonth) return List.of(session);
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

    // ----------------------------------------------------------------
    // B-3: parseAndSaveRowIds の整合判定テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("B-3: join-ID件数がスケジュール件数と不一致なら false（書き込み中止）＋errors記録・保存なし")
    void parseAndSaveRowIds_countMismatch_returnsFalseAndAborts() {
        // schedule: 1セッション×2試合 = 2件
        PracticeSession s = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).totalMatches(2).build();
        org.jsoup.nodes.Document formDoc = org.jsoup.Jsoup.parse("<table class='listtbl'></table>");
        // join-id は1件だけ → 件数不一致
        Map<String, String> joinInputs = new LinkedHashMap<>();
        joinInputs.put("join-999", "");
        java.util.List<String> errors = new java.util.ArrayList<>();

        boolean usable = densukeWriteService.parseAndSaveRowIds(100L, List.of(s), formDoc, joinInputs, errors);

        assertThat(usable).isFalse();
        assertThat(errors).isNotEmpty();
        // stale row_id 継続を防ぐため、保存も既存キャッシュ取得もしない
        verify(densukeRowIdRepository, never()).saveAll(anyList());
        verify(densukeRowIdRepository, never()).findByDensukeUrlId(anyLong());
    }

    @Test
    @DisplayName("B-3: join-ID件数が一致すれば true（書き込み継続）＋未保存分を保存")
    void parseAndSaveRowIds_countMatch_returnsTrue() {
        PracticeSession s = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).totalMatches(2).build();
        org.jsoup.nodes.Document formDoc = org.jsoup.Jsoup.parse("<table class='listtbl'></table>");
        Map<String, String> joinInputs = new LinkedHashMap<>();
        joinInputs.put("join-11", "");
        joinInputs.put("join-22", "");
        when(densukeRowIdRepository.findByDensukeUrlId(100L)).thenReturn(List.of());
        java.util.List<String> errors = new java.util.ArrayList<>();

        boolean usable = densukeWriteService.parseAndSaveRowIds(100L, List.of(s), formDoc, joinInputs, errors);

        assertThat(usable).isTrue();
        verify(densukeRowIdRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("B-3: 編集フォームを解析できない（listtbl不在）なら false（書き込み中止）＋errors記録")
    void parseAndSaveRowIds_parseFailure_returnsFalse() {
        // listtbl の無いページ（エラーページ / HTML変更を模擬）
        org.jsoup.nodes.Document formDoc = org.jsoup.Jsoup.parse("<html><body>error</body></html>");
        Map<String, String> joinInputs = new LinkedHashMap<>();
        joinInputs.put("join-1", "");
        java.util.List<String> errors = new java.util.ArrayList<>();

        boolean usable = densukeWriteService.parseAndSaveRowIds(1L, List.of(), formDoc, joinInputs, errors);

        assertThat(usable).isFalse();
        assertThat(errors).isNotEmpty();
        verify(densukeRowIdRepository, never()).saveAll(anyList());
    }

    // ----------------------------------------------------------------
    // A-4: findDbNameCollisions テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("A-4: DB上に正規化後同名の複数選手がいれば衝突名を返す（dirtyが片方だけでも検知）")
    void findDbNameCollisions_detectsDbDuplicates() {
        Player p1 = Player.builder().id(1L).name("田中").build();
        Player p2 = Player.builder().id(2L).name("田中 ").build(); // 末尾空白 → 正規化後同名
        Player p3 = Player.builder().id(3L).name("佐藤").build();
        when(playerRepository.findAllActive()).thenReturn(List.of(p1, p2, p3));

        java.util.Set<String> collisions = densukeWriteService.findDbNameCollisions();

        assertThat(collisions).containsExactly("田中");
    }

    @Test
    @DisplayName("B-3: parseAndSaveRowIds は row_id 問題を rowIdIssues にも記録する（管理者通知用）")
    void parseAndSaveRowIds_recordsRowIdIssue() {
        PracticeSession s = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).totalMatches(2).build();
        org.jsoup.nodes.Document formDoc = org.jsoup.Jsoup.parse("<table class='listtbl'></table>");
        Map<String, String> joinInputs = new LinkedHashMap<>();
        joinInputs.put("join-1", ""); // 1件 vs schedule 2件 → 不一致
        java.util.List<String> errors = new java.util.ArrayList<>();
        java.util.List<String> rowIdIssues = new java.util.ArrayList<>();

        boolean usable = densukeWriteService.parseAndSaveRowIds(100L, List.of(s), formDoc, joinInputs, errors, rowIdIssues);

        assertThat(usable).isFalse();
        assertThat(errors).isNotEmpty();
        assertThat(rowIdIssues).isNotEmpty();
    }
}
