package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.dto.PracticeSessionUpdateRequest;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.DensukeMemberMappingRepository;
import com.karuta.matchtracker.repository.DensukeRowIdRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * PracticeSessionServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeSessionService 単体テスト")
class PracticeSessionServiceTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VenueMatchScheduleRepository venueMatchScheduleRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private DensukeUrlRepository densukeUrlRepository;

    @Mock
    private DensukeRowIdRepository densukeRowIdRepository;

    @Mock
    private DensukeMemberMappingRepository densukeMemberMappingRepository;

    @Mock
    private DensukeSyncService densukeSyncService;

    @Mock
    private AdjacentRoomService adjacentRoomService;

    @Mock
    private WaitlistPromotionService waitlistPromotionService;

    @Mock
    private LotteryDeadlineHelper lotteryDeadlineHelper;

    @InjectMocks
    private PracticeSessionService practiceSessionService;

    private PracticeSession testSession;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        // production コード (PracticeSessionService) は JstDateTimeUtil.today() を使うため、
        // テスト側も JST 基準の今日を取得しないと UTC 15:00〜23:59 (= JST 0:00〜8:59) の
        // 時間帯で日付が1日ずれ、Mockito 厳格スタブが PotentialStubbingProblem を投げる
        // (Issue #575)。
        today = JstDateTimeUtil.today();
        testSession = PracticeSession.builder()
                .id(1L)
                .sessionDate(today)
                .totalMatches(10)
                .build();
        // enrichSessionWithParticipants を経由するテストでのみ参照される。
        // 参照しないテスト（NotFound 系・ findNextSessionForPlayer など）で
        // UnnecessaryStubbingException を起こさないよう lenient で登録する。
        lenient().when(lotteryDeadlineHelper.isLotteryDisabled(any())).thenReturn(false);
    }

    @Test
    @DisplayName("IDで練習日を取得できる")
    void testFindById() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceParticipantRepository.findBySessionId(1L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // When
        PracticeSessionDto result = practiceSessionService.findById(1L);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSessionDate()).isEqualTo(today);
        assertThat(result.getTotalMatches()).isEqualTo(10);
        verify(practiceSessionRepository).findById(1L);
    }

    @Test
    @DisplayName("findById: 抽選なし運用団体ではpairingIncludesPending=true、抽選あり運用ではfalseを返す")
    void testFindById_pairingIncludesPendingReflectsLotteryDisabled() {
        // Given: organizationId を持つセッション
        PracticeSession sessionWithOrg = PracticeSession.builder()
                .id(2L)
                .sessionDate(today)
                .totalMatches(10)
                .organizationId(99L)
                .build();
        when(practiceSessionRepository.findById(2L)).thenReturn(Optional.of(sessionWithOrg));
        when(practiceParticipantRepository.findBySessionId(2L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // 抽選なし運用 → pairingIncludesPending=true
        when(lotteryDeadlineHelper.isLotteryDisabled(99L)).thenReturn(true);
        PracticeSessionDto resultDisabled = practiceSessionService.findById(2L);
        assertThat(resultDisabled.getPairingIncludesPending()).isTrue();

        // 抽選あり運用 → pairingIncludesPending=false
        when(lotteryDeadlineHelper.isLotteryDisabled(99L)).thenReturn(false);
        PracticeSessionDto resultEnabled = practiceSessionService.findById(2L);
        assertThat(resultEnabled.getPairingIncludesPending()).isFalse();
    }

    @Test
    @DisplayName("存在しないIDで練習日を取得するとResourceNotFoundExceptionが発生")
    void testFindByIdNotFound() {
        // Given
        when(practiceSessionRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining("999");
        verify(practiceSessionRepository).findById(999L);
    }

    @Test
    @DisplayName("日付で練習日を取得できる")
    void testFindByDate() {
        // Given
        when(practiceSessionRepository.findBySessionDate(today))
                .thenReturn(Optional.of(testSession));

        // When
        PracticeSessionDto result = practiceSessionService.findByDate(today);

        // Then
        assertThat(result.getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).findBySessionDate(today);
    }

    @Test
    @DisplayName("findByDateWithParticipants(date, organizationId): 組織スコープでセッションを取得する")
    void testFindByDateWithParticipants_orgScoped() {
        // Given: 同日に複数団体のセッションがある場面を想定
        Long adminOrgId = 7L;
        PracticeSession orgSession = PracticeSession.builder()
                .id(100L)
                .sessionDate(today)
                .totalMatches(10)
                .organizationId(adminOrgId)
                .build();
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(today, adminOrgId))
                .thenReturn(Optional.of(orgSession));
        when(practiceParticipantRepository.findBySessionId(100L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // When
        PracticeSessionDto result = practiceSessionService.findByDateWithParticipants(today, adminOrgId);

        // Then: 組織スコープ取得のみが使われ、日付のみ取得は呼ばれない
        assertThat(result.getId()).isEqualTo(100L);
        verify(practiceSessionRepository).findBySessionDateAndOrganizationId(today, adminOrgId);
        verify(practiceSessionRepository, never()).findBySessionDate(today);
    }

    @Test
    @DisplayName("findByDateWithParticipants(date, null): organizationId=null は日付のみで取得する（SUPER_ADMIN/PLAYER 経路）")
    void testFindByDateWithParticipants_nullOrgIdUsesDateOnly() {
        // Given
        when(practiceSessionRepository.findBySessionDate(today))
                .thenReturn(Optional.of(testSession));
        when(practiceParticipantRepository.findBySessionId(1L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // When
        PracticeSessionDto result = practiceSessionService.findByDateWithParticipants(today, null);

        // Then: 日付のみで取得され、組織スコープ取得は呼ばれない
        assertThat(result.getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).findBySessionDate(today);
        verify(practiceSessionRepository, never()).findBySessionDateAndOrganizationId(any(), any());
    }

    @Test
    @DisplayName("特定の年月の練習日を取得できる")
    void testFindSessionsByYearMonth() {
        // Given
        int year = today.getYear();
        int month = today.getMonthValue();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(testSession));
        when(practiceParticipantRepository.findBySessionIdIn(any())).thenReturn(List.of());
        when(matchRepository.countByMatchDateIn(any())).thenReturn(List.of());

        // When
        List<PracticeSessionDto> result = practiceSessionService.findSessionsByYearMonth(year, month);

        // Then
        assertThat(result).hasSize(1);
        verify(practiceSessionRepository).findByYearAndMonth(year, month);
    }

    @Test
    @DisplayName("日付が練習日として登録されているか確認できる")
    void testExistsSessionOnDate() {
        // Given
        when(practiceSessionRepository.existsBySessionDate(today)).thenReturn(true);

        // When
        boolean exists = practiceSessionService.existsSessionOnDate(today);

        // Then
        assertThat(exists).isTrue();
        verify(practiceSessionRepository).existsBySessionDate(today);
    }

    @Test
    @DisplayName("練習日を新規登録できる")
    void testCreateSession() {
        // Given
        PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(12)
                .organizationId(1L)
                .build();
        when(practiceSessionRepository.existsBySessionDateAndOrganizationId(today, 1L)).thenReturn(false);
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);
        when(practiceParticipantRepository.findBySessionId(1L)).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // When
        PracticeSessionDto result = practiceSessionService.createSession(request, 1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionDate()).isEqualTo(today);
        verify(practiceSessionRepository).existsBySessionDateAndOrganizationId(today, 1L);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("既存の日付で練習日を登録するとDuplicateResourceExceptionが発生")
    void testCreateSessionDuplicateDate() {
        // Given
        PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(12)
                .organizationId(1L)
                .build();
        when(practiceSessionRepository.existsBySessionDateAndOrganizationId(today, 1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.createSession(request, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining(today.toString());
        verify(practiceSessionRepository).existsBySessionDateAndOrganizationId(today, 1L);
        verify(practiceSessionRepository, never()).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("総試合数を更新できる")
    void testUpdateTotalMatches() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);

        // When
        PracticeSessionDto result = practiceSessionService.updateTotalMatches(1L, 15);

        // Then
        assertThat(result).isNotNull();
        verify(practiceSessionRepository).findById(1L);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("総試合数を負の値に更新するとIllegalArgumentExceptionが発生")
    void testUpdateTotalMatchesNegative() {
        // Given
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.updateTotalMatches(1L, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
        verify(practiceSessionRepository).findById(1L);
        verify(practiceSessionRepository, never()).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("練習日を削除できる")
    void testDeleteSession() {
        // Given
        when(practiceSessionRepository.existsById(1L)).thenReturn(true);

        // When
        practiceSessionService.deleteSession(1L);

        // Then
        verify(practiceSessionRepository).existsById(1L);
        verify(practiceParticipantRepository).deleteBySessionId(1L);
        verify(practiceSessionRepository).deleteById(1L);
    }

    @Test
    @DisplayName("updateSession: BYE(matchNumber=null)はキャンセル対象から除外される")
    void testUpdateSession_byeExcludedFromCancellation() {
        // Given: セッションに通常参加者2名 + BYE1名が存在
        Long sessionId = 1L;
        PracticeSession session = PracticeSession.builder()
                .id(sessionId).sessionDate(today).totalMatches(3).organizationId(1L).build();

        // player1: 通常参加者（リクエストに含まれる → 残る）
        PracticeParticipant normalPp1 = PracticeParticipant.builder()
                .id(100L).sessionId(sessionId).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        // player2: 通常参加者（リクエストに含まれない → CANCELLED）
        PracticeParticipant normalPp2 = PracticeParticipant.builder()
                .id(101L).sessionId(sessionId).playerId(2L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        // player3: BYE（matchNumber=null、リクエストに含まれない → 除外される）
        PracticeParticipant byePp = PracticeParticipant.builder()
                .id(102L).sessionId(sessionId).playerId(3L).matchNumber(null)
                .status(ParticipantStatus.WON).dirty(false).build();

        List<PracticeParticipant> existingParticipants = new ArrayList<>(List.of(normalPp1, normalPp2, byePp));

        Player p1 = new Player(); p1.setId(1L); p1.setName("選手1");

        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionId(sessionId)).thenReturn(existingParticipants);
        when(playerRepository.findAllById(List.of(1L))).thenReturn(List.of(p1));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(session);
        when(practiceParticipantRepository.saveAll(anyList())).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        PracticeSessionUpdateRequest request = PracticeSessionUpdateRequest.builder()
                .sessionDate(today)
                .totalMatches(3)
                .participantIds(List.of(1L)) // player1のみ残す
                .build();

        // When
        practiceSessionService.updateSession(sessionId, request, 1L);

        // Then: player2は CANCELLED になる
        assertThat(normalPp2.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        assertThat(normalPp2.isDirty()).isTrue();

        // BYEは除外されるため、ステータスが変更されない
        assertThat(byePp.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(byePp.isDirty()).isFalse();
    }

    @Test
    @DisplayName("updateSession: 定員拡張+参加者追加時、新規参加者の保存後にキャンセル待ち昇格が実行される")
    void testUpdateSession_capacityExpandWithNewParticipants_promotionRunsAfterSaveAll() {
        // Given: capacity=10 / totalMatches=1 / 既存 WON 2名
        Long sessionId = 1L;
        PracticeSession session = PracticeSession.builder()
                .id(sessionId).sessionDate(today).totalMatches(1).capacity(10)
                .organizationId(1L).build();

        PracticeParticipant pp1 = PracticeParticipant.builder()
                .id(100L).sessionId(sessionId).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        PracticeParticipant pp2 = PracticeParticipant.builder()
                .id(101L).sessionId(sessionId).playerId(2L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        List<PracticeParticipant> existingParticipants = new ArrayList<>(List.of(pp1, pp2));

        Player p1 = new Player(); p1.setId(1L); p1.setName("選手1");
        Player p2 = new Player(); p2.setId(2L); p2.setName("選手2");
        Player p3 = new Player(); p3.setId(3L); p3.setName("選手3");

        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionId(sessionId)).thenReturn(existingParticipants);
        when(playerRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(p1, p2, p3));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(session);
        when(practiceParticipantRepository.saveAll(anyList())).thenReturn(List.of());
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // capacity 10 → 12 + player3 を追加
        PracticeSessionUpdateRequest request = PracticeSessionUpdateRequest.builder()
                .sessionDate(today)
                .totalMatches(1)
                .capacity(12)
                .participantIds(List.of(1L, 2L, 3L))
                .build();

        // When
        practiceSessionService.updateSession(sessionId, request, 1L);

        // Then: 新規参加者の saveAll が先 → 昇格処理が後の順で呼ばれる
        // 先に昇格すると、新規参加者の WON カウント前に昇格数が決まり定員超過の原因となる
        var ordered = inOrder(practiceParticipantRepository, waitlistPromotionService);
        ordered.verify(practiceParticipantRepository).saveAll(anyList());
        ordered.verify(waitlistPromotionService).promoteWaitlistedAfterCapacityIncrease(sessionId);
    }

    @Test
    @DisplayName("updateSession: capacity 未指定（null）の通常編集ではキャンセル待ち昇格が実行されない")
    void testUpdateSession_capacityNotSpecified_doesNotPromoteWaitlisted() {
        // Given: capacity=10 の既存セッション、編集フォームから capacity 未指定で他項目だけ更新するケース
        // PracticeForm の通常編集（日付・会場・メモ等の更新）では request.capacity が null になるため、
        // 「制限解除」と誤判定して昇格処理が走らないことを保証する。
        Long sessionId = 1L;
        PracticeSession session = PracticeSession.builder()
                .id(sessionId).sessionDate(today).totalMatches(1).capacity(10)
                .organizationId(1L).build();

        PracticeParticipant pp1 = PracticeParticipant.builder()
                .id(100L).sessionId(sessionId).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        List<PracticeParticipant> existingParticipants = new ArrayList<>(List.of(pp1));

        Player p1 = new Player(); p1.setId(1L); p1.setName("選手1");

        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionId(sessionId)).thenReturn(existingParticipants);
        when(playerRepository.findAllById(List.of(1L))).thenReturn(List.of(p1));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(session);
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // capacity を含めずメモだけ更新するリクエスト（通常の編集フロー）
        PracticeSessionUpdateRequest request = PracticeSessionUpdateRequest.builder()
                .sessionDate(today)
                .totalMatches(1)
                .notes("メモを更新")
                .participantIds(List.of(1L))
                .build();

        // When
        practiceSessionService.updateSession(sessionId, request, 1L);

        // Then: capacity 未指定では昇格処理が呼ばれない
        verify(waitlistPromotionService, never()).promoteWaitlistedAfterCapacityIncrease(any());
    }

    @Test
    @DisplayName("updateSession: 定員拡張+参加者削除時、削除反映後にキャンセル待ち昇格が実行される")
    void testUpdateSession_capacityExpandWithParticipantRemoval_promotionRunsAfterCancellation() {
        // Given: capacity=10 / totalMatches=1 / 既存 WON 3名
        Long sessionId = 1L;
        PracticeSession session = PracticeSession.builder()
                .id(sessionId).sessionDate(today).totalMatches(1).capacity(10)
                .organizationId(1L).build();

        PracticeParticipant pp1 = PracticeParticipant.builder()
                .id(100L).sessionId(sessionId).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        PracticeParticipant pp2 = PracticeParticipant.builder()
                .id(101L).sessionId(sessionId).playerId(2L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        PracticeParticipant pp3 = PracticeParticipant.builder()
                .id(102L).sessionId(sessionId).playerId(3L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();
        List<PracticeParticipant> existingParticipants = new ArrayList<>(List.of(pp1, pp2, pp3));

        Player p1 = new Player(); p1.setId(1L); p1.setName("選手1");
        Player p2 = new Player(); p2.setId(2L); p2.setName("選手2");

        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionId(sessionId)).thenReturn(existingParticipants);
        when(playerRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(session);
        when(matchRepository.countByMatchDate(today)).thenReturn(0L);

        // capacity 10 → 12 + player3 を削除
        PracticeSessionUpdateRequest request = PracticeSessionUpdateRequest.builder()
                .sessionDate(today)
                .totalMatches(1)
                .capacity(12)
                .participantIds(List.of(1L, 2L))
                .build();

        // When
        practiceSessionService.updateSession(sessionId, request, 1L);

        // Then: 削除対象の player3 が CANCELLED に変更された後で昇格処理が呼ばれる
        // （昇格処理が先だと、削除分の空き枠が昇格対象に含まれない）
        assertThat(pp3.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        verify(waitlistPromotionService).promoteWaitlistedAfterCapacityIncrease(sessionId);
    }

    @Test
    @DisplayName("存在しない練習日を削除するとResourceNotFoundExceptionが発生")
    void testDeleteSessionNotFound() {
        // Given
        when(practiceSessionRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> practiceSessionService.deleteSession(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PracticeSession")
                .hasMessageContaining("999");
        verify(practiceSessionRepository, never()).deleteById(any());
    }

    // === findNextSessionForPlayer テスト ===

    @Test
    @DisplayName("所属団体がない場合はnullを返す")
    void findNextSessionForPlayer_noOrganizations_returnsNull() {
        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of());

        PracticeSession result = practiceSessionService.findNextSessionForPlayer(1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("所属団体の次の練習セッションを返す")
    void findNextSessionForPlayer_withUpcomingSession_returnsSession() {
        LocalDate tomorrow = today.plusDays(1);
        PracticeSession futureSession = PracticeSession.builder()
                .id(2L).sessionDate(tomorrow).organizationId(10L).build();

        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of(10L));
        when(practiceSessionRepository.findUpcomingSessionsByOrganizationIdIn(List.of(10L), today))
                .thenReturn(List.of(futureSession));

        PracticeSession result = practiceSessionService.findNextSessionForPlayer(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("今日の練習で開始時間前なら今日の練習を返す")
    void findNextSessionForPlayer_todayBeforeStart_returnsTodaySession() {
        PracticeSession todaySession = PracticeSession.builder()
                .id(3L).sessionDate(today).organizationId(10L)
                .startTime(LocalTime.of(23, 59)).build();

        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of(10L));
        when(practiceSessionRepository.findUpcomingSessionsByOrganizationIdIn(List.of(10L), today))
                .thenReturn(List.of(todaySession));

        PracticeSession result = practiceSessionService.findNextSessionForPlayer(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("今日の練習で開始時間を過ぎていたら翌日以降の練習を返す")
    void findNextSessionForPlayer_todayAfterStart_returnsFutureSession() {
        LocalDate tomorrow = today.plusDays(1);
        PracticeSession todaySession = PracticeSession.builder()
                .id(3L).sessionDate(today).organizationId(10L)
                .startTime(LocalTime.of(0, 0)).build();
        PracticeSession tomorrowSession = PracticeSession.builder()
                .id(4L).sessionDate(tomorrow).organizationId(10L).build();

        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of(10L));
        when(practiceSessionRepository.findUpcomingSessionsByOrganizationIdIn(List.of(10L), today))
                .thenReturn(List.of(todaySession, tomorrowSession));

        PracticeSession result = practiceSessionService.findNextSessionForPlayer(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("開始時間がnullの今日の練習は返す")
    void findNextSessionForPlayer_todayNullStartTime_returnsTodaySession() {
        PracticeSession todaySession = PracticeSession.builder()
                .id(5L).sessionDate(today).organizationId(10L)
                .startTime(null).build();

        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of(10L));
        when(practiceSessionRepository.findUpcomingSessionsByOrganizationIdIn(List.of(10L), today))
                .thenReturn(List.of(todaySession));

        PracticeSession result = practiceSessionService.findNextSessionForPlayer(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("saveDensukeUrl は手動 URL 更新時に densuke_sd を NULL にクリアする（自動作成→手動上書きの整合性）")
    void saveDensukeUrl_clearsDensukeSd_onManualOverwrite() {
        // 自動作成済みレコード（sd 保持）を手動 URL で上書きするケース
        DensukeUrl existing = DensukeUrl.builder()
                .id(1L).year(2026).month(5).organizationId(10L)
                .url("https://densuke.biz/list?cd=auto123")
                .densukeSd("secret-sd-456")
                .build();
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 10L))
                .thenReturn(Optional.of(existing));
        when(densukeUrlRepository.save(any(DensukeUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        DensukeUrl result = practiceSessionService.saveDensukeUrl(
                2026, 5, "https://densuke.biz/list?cd=manualABC", 10L);

        assertThat(result.getUrl()).isEqualTo("https://densuke.biz/list?cd=manualABC");
        assertThat(result.getDensukeSd()).isNull();
    }

    @Test
    @DisplayName("saveDensukeUrl は新規レコード作成時にも densuke_sd を NULL のままにする")
    void saveDensukeUrl_newRecord_hasNullDensukeSd() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 6, 10L))
                .thenReturn(Optional.empty());
        when(densukeUrlRepository.save(any(DensukeUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        DensukeUrl result = practiceSessionService.saveDensukeUrl(
                2026, 6, "https://densuke.biz/list?cd=newXYZ", 10L);

        assertThat(result.getUrl()).isEqualTo("https://densuke.biz/list?cd=newXYZ");
        assertThat(result.getDensukeSd()).isNull();
    }

    @Test
    @DisplayName("deleteDensukeUrl: 既存レコードを削除し true を返す（自動作成レコードでも同じ扱い）")
    void deleteDensukeUrl_removesExistingRecord() {
        DensukeUrl existing = DensukeUrl.builder()
                .id(42L).year(2026).month(5).organizationId(10L)
                .url("https://densuke.biz/list?cd=autoABC")
                .densukeSd("secret-sd")
                .build();
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 10L))
                .thenReturn(Optional.of(existing));

        boolean result = practiceSessionService.deleteDensukeUrl(2026, 5, 10L);

        assertThat(result).isTrue();
        verify(densukeUrlRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteDensukeUrl: 子テーブル(row_ids, member_mappings)を親より先に削除する（FK制約違反防止）")
    void deleteDensukeUrl_deletesChildrenBeforeParent() {
        DensukeUrl existing = DensukeUrl.builder()
                .id(42L).year(2026).month(5).organizationId(10L)
                .url("https://densuke.biz/list?cd=autoABC")
                .densukeSd("secret-sd")
                .build();
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 10L))
                .thenReturn(Optional.of(existing));

        boolean result = practiceSessionService.deleteDensukeUrl(2026, 5, 10L);

        assertThat(result).isTrue();
        // 削除順が「子 → 親」であることを検証（逆順だと FK 制約違反で 500 になる）
        var inOrder = inOrder(densukeRowIdRepository, densukeMemberMappingRepository, densukeUrlRepository);
        inOrder.verify(densukeRowIdRepository).deleteByDensukeUrlId(42L);
        inOrder.verify(densukeMemberMappingRepository).deleteByDensukeUrlId(42L);
        inOrder.verify(densukeUrlRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteDensukeUrl: 該当レコードが存在しない場合は false を返し delete は呼ばれない")
    void deleteDensukeUrl_returnsFalse_whenNoRecord() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 10L))
                .thenReturn(Optional.empty());

        boolean result = practiceSessionService.deleteDensukeUrl(2026, 5, 10L);

        assertThat(result).isFalse();
        verify(densukeUrlRepository, never()).delete(any(DensukeUrl.class));
        verify(densukeRowIdRepository, never()).deleteByDensukeUrlId(any());
        verify(densukeMemberMappingRepository, never()).deleteByDensukeUrlId(any());
    }

    // === findSessionSummariesByYearMonth: matchCapacityStatuses 計算ロジックのテスト ===

    /**
     * matchCapacityStatuses テスト用の参加者を生成するヘルパー。
     */
    private PracticeParticipant capacityTestParticipant(
            Long sessionId, Long playerId, Integer matchNumber, ParticipantStatus status) {
        return PracticeParticipant.builder()
                .sessionId(sessionId)
                .playerId(playerId)
                .matchNumber(matchNumber)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: capacity が null のセッションは matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_capacityNull_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(null).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: capacity が 0 のセッションは matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_capacityZero_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(0).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: capacity が負のセッションは matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_capacityNegative_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(-1).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: totalMatches が null のセッションは matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_totalMatchesNull_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(null).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: totalMatches が 0 のセッションは matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_totalMatchesZero_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(0).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: totalMatches が 10 以上のセッションは matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_totalMatchesTenOrMore_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(10).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: 全試合で空き多数なら全 AVAILABLE（長さ = totalMatches）")
    void findSessionSummariesByYearMonth_allMatchesAvailable() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // capacity=6, totalMatches=3, 各試合に1人ずつ → remaining=5 → AVAILABLE
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(6).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 11L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 12L, 3, ParticipantStatus.WON)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.AVAILABLE
                );
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: remaining ≤ 2 の試合のみ NEARLY_FULL")
    void findSessionSummariesByYearMonth_partialNearlyFull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // capacity=4, totalMatches=3
        // 試合1: 1人 → remaining=3 → AVAILABLE
        // 試合2: 2人 → remaining=2 → NEARLY_FULL
        // 試合3: 3人 → remaining=1 → NEARLY_FULL
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 20L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 21L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 30L, 3, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 31L, 3, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 32L, 3, ParticipantStatus.WON)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.NEARLY_FULL,
                        PracticeSessionDto.CapacityStatus.NEARLY_FULL
                );
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: remaining = 0 の試合のみ FULL")
    void findSessionSummariesByYearMonth_partialFull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // capacity=4, totalMatches=3
        // 試合1: 4人 → remaining=0 → FULL
        // 試合2: 5人 → remaining=-1 → FULL（超過しても FULL）
        // 試合3: 1人 → remaining=3 → AVAILABLE
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 11L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 12L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 13L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 20L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 21L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 22L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 23L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 24L, 2, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 30L, 3, ParticipantStatus.WON)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(
                        PracticeSessionDto.CapacityStatus.FULL,
                        PracticeSessionDto.CapacityStatus.FULL,
                        PracticeSessionDto.CapacityStatus.AVAILABLE
                );
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: WAITLISTED / CANCELLED / DECLINED / WAITLIST_DECLINED は effectiveCount に含めない")
    void findSessionSummariesByYearMonth_inactiveStatusesExcludedFromEffectiveCount() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // capacity=4, totalMatches=1
        // 実質枠（WON+PENDING+OFFERED） = 1人のみ → remaining=3 → AVAILABLE
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(1).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 11L, 1, ParticipantStatus.WAITLISTED),
                        capacityTestParticipant(1L, 12L, 1, ParticipantStatus.CANCELLED),
                        capacityTestParticipant(1L, 13L, 1, ParticipantStatus.DECLINED),
                        capacityTestParticipant(1L, 14L, 1, ParticipantStatus.WAITLIST_DECLINED)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(PracticeSessionDto.CapacityStatus.AVAILABLE);
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: PENDING / OFFERED は effectiveCount に含める")
    void findSessionSummariesByYearMonth_pendingAndOfferedIncluded() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // capacity=4, totalMatches=1, WON + PENDING + OFFERED = 4 → remaining=0 → FULL
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(1).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 11L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 12L, 1, ParticipantStatus.PENDING),
                        capacityTestParticipant(1L, 13L, 1, ParticipantStatus.OFFERED)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(PracticeSessionDto.CapacityStatus.FULL);
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: 参加者ゼロの試合は AVAILABLE")
    void findSessionSummariesByYearMonth_emptyMatchesAreAvailable() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // capacity=4, totalMatches=3, 参加者ゼロ → 全試合 AVAILABLE
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.AVAILABLE
                );
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: matchCapacityStatuses の長さは totalMatches と一致する")
    void findSessionSummariesByYearMonth_lengthMatchesTotalMatches() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // totalMatches=9（上限ぎりぎり）
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(9).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).hasSize(9);
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: 参加者集計の例外時もセッション一覧を返し matchCapacityStatuses は null にフォールバックする")
    void findSessionSummariesByYearMonth_aggregationFailure_returnsSessionsWithoutMatchCapacityStatuses() {
        int year = today.getYear();
        int month = today.getMonthValue();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(2).capacity(4).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        // 参加者の一括取得で例外発生
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenThrow(new RuntimeException("DB error"));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        // セッション本体は返り、matchCapacityStatuses は null（グリッド非表示扱い）
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: session.capacity 設定済みなら venue 既定値より session 値を優先")
    void findSessionSummariesByYearMonth_sessionCapacityOverridesVenueDefault() {
        int year = today.getYear();
        int month = today.getMonthValue();
        // venue.capacity=99 だが session.capacity=4 が優先される
        Venue venue = Venue.builder().id(100L).name("会場A").capacity(99).build();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(4).venueId(100L).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(anyList())).thenReturn(List.of(venue));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 11L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 12L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 13L, 1, ParticipantStatus.WON)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        // session.capacity=4 で判定: 1試合目 effective=4 → FULL、2/3試合目 → AVAILABLE
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(
                        PracticeSessionDto.CapacityStatus.FULL,
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.AVAILABLE
                );
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: session.capacity が null でも venue 既定値があればフォールバックして判定される")
    void findSessionSummariesByYearMonth_fallbackToVenueDefaultCapacity() {
        int year = today.getYear();
        int month = today.getMonthValue();
        Venue venue = Venue.builder().id(100L).name("わすらもち会場").capacity(4).build();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(null).venueId(100L).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(anyList())).thenReturn(List.of(venue));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of(
                        capacityTestParticipant(1L, 10L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 11L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 12L, 1, ParticipantStatus.WON),
                        capacityTestParticipant(1L, 13L, 1, ParticipantStatus.WON)
                ));

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        // venue 既定 capacity=4 で判定: 1試合目 effective=4 → FULL、2/3試合目 → AVAILABLE
        assertThat(result.get(0).getMatchCapacityStatuses())
                .containsExactly(
                        PracticeSessionDto.CapacityStatus.FULL,
                        PracticeSessionDto.CapacityStatus.AVAILABLE,
                        PracticeSessionDto.CapacityStatus.AVAILABLE
                );
    }

    @Test
    @DisplayName("findSessionSummariesByYearMonth: session.capacity も venue 既定 capacity も null なら matchCapacityStatuses=null")
    void findSessionSummariesByYearMonth_bothCapacityNull_returnsNull() {
        int year = today.getYear();
        int month = today.getMonthValue();
        Venue venue = Venue.builder().id(100L).name("会場A").capacity(null).build();
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(today).totalMatches(3).capacity(null).venueId(100L).build();
        when(practiceSessionRepository.findByYearAndMonth(year, month))
                .thenReturn(List.of(session));
        when(venueRepository.findAllById(anyList())).thenReturn(List.of(venue));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(1L)))
                .thenReturn(List.of());

        List<PracticeSessionDto> result = practiceSessionService.findSessionSummariesByYearMonth(year, month, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchCapacityStatuses()).isNull();
    }
}
