package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.DensukeScraper.DensukeData;
import com.karuta.matchtracker.service.DensukeScraper.ScheduleEntry;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DensukeScheduleWriteService の単体テスト
 *
 * <p>{@code Jsoup.connect()} は {@link MockedStatic} でモックし、HTTP I/O を排除する。
 * {@link DensukeScraper} はインスタンスメソッドなので Mockito で直接モック。
 *
 * <p>テスト対象は public エントリポイント:
 * <ul>
 *   <li>{@code pushNewSchedulesToDensuke}（{@code notifyOnFailure=true} 経路）</li>
 *   <li>{@code pushSilently}（{@code notifyOnFailure=false} 経路、フラッディング防止検証）</li>
 * </ul>
 *
 * <p>{@code self} 参照（{@code @Lazy} 自己参照）は本テスト経路では使用されないため、
 * 手動コンストラクタで null を渡す。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeScheduleWriteService 単体テスト")
class DensukeScheduleWriteServiceTest {

    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private VenueMatchScheduleRepository venueMatchScheduleRepository;
    @Mock private DensukePageCreateService densukePageCreateService;
    @Mock private DensukeWriteService densukeWriteService;
    @Mock private DensukeScraper densukeScraper;
    @Mock private LineNotificationService lineNotificationService;

    private DensukeScheduleWriteService service;

    private static final int YEAR = 2026;
    private static final int MONTH = 5;
    private static final Long ORG_ID = 1L;
    private static final String DENSUKE_URL = "https://densuke.biz/list?cd=test123";
    private static final String PAGE_ID = "10566891";

    @BeforeEach
    void setUp() {
        // self は public 経路 (pushNewSchedulesToDensuke / pushSilently) では使用されないので null で OK。
        // pushNewSchedulesToDensukeAsync / pushAllForCurrentAndNextMonth を直接テストする場合のみ self が必要。
        service = new DensukeScheduleWriteService(
                densukeUrlRepository,
                practiceSessionRepository,
                venueRepository,
                venueMatchScheduleRepository,
                densukePageCreateService,
                densukeWriteService,
                densukeScraper,
                lineNotificationService,
                /* self = */ null
        );
    }

    // ========================================================================
    // ヘルパー
    // ========================================================================

    private DensukeUrl buildDensukeUrl() {
        return DensukeUrl.builder()
                .id(100L).year(YEAR).month(MONTH).organizationId(ORG_ID)
                .url(DENSUKE_URL).build();
    }

    /**
     * 1セッション (2026/5/10) を持つアプリ側データを返す。
     */
    private PracticeSession buildAppSession(LocalDate date) {
        return PracticeSession.builder()
                .id(10L).sessionDate(date).venueId(20L).totalMatches(3).organizationId(ORG_ID)
                .build();
    }

    private Venue buildVenue() {
        return Venue.builder().id(20L).name("テスト会場").defaultMatchCount(3).build();
    }

    private List<VenueMatchSchedule> buildSchedules() {
        return List.of(
                VenueMatchSchedule.builder().venueId(20L).matchNumber(1)
                        .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(18, 0)).build(),
                VenueMatchSchedule.builder().venueId(20L).matchNumber(2)
                        .startTime(LocalTime.of(18, 5)).endTime(LocalTime.of(19, 5)).build(),
                VenueMatchSchedule.builder().venueId(20L).matchNumber(3)
                        .startTime(LocalTime.of(19, 10)).endTime(LocalTime.of(20, 10)).build()
        );
    }

    /**
     * 伝助スクレイプ結果を生成する。{@code dates} に含まれる日付分のエントリを持つ。
     */
    private DensukeData buildDensukeData(List<LocalDate> dates) {
        DensukeData data = new DensukeData();
        for (LocalDate d : dates) {
            ScheduleEntry e = new ScheduleEntry();
            e.setDate(d);
            e.setMatchNumber(1);
            data.getEntries().add(e);
        }
        return data;
    }

    /**
     * Jsoup の GET/POST フローをモックする。
     *
     * @param updateStatus POST /update のステータスコード（成功なら 302）
     * @param throwIo POST /update 時に IOException を投げる場合 true（GET 経路は成功）
     */
    private void stubJsoupCalls(MockedStatic<Jsoup> jsoupMock,
                                Connection getConnection, Connection postConnection,
                                Connection.Response listResponse, Connection.Response updateResponse,
                                int updateStatus, boolean throwIo) throws IOException {
        // GET base + "list?cd=..." のチェイン
        jsoupMock.when(() -> Jsoup.connect(contains("list?cd="))).thenReturn(getConnection);
        when(getConnection.userAgent(anyString())).thenReturn(getConnection);
        when(getConnection.timeout(anyInt())).thenReturn(getConnection);
        when(getConnection.execute()).thenReturn(listResponse);

        Document listDoc = Jsoup.parse("<html><body><input type=\"hidden\" name=\"id\" value=\"" + PAGE_ID + "\"></body></html>");
        when(listResponse.cookies()).thenReturn(Map.of("cookie1", "value1"));
        when(listResponse.parse()).thenReturn(listDoc);

        // POST base + "update" のチェイン
        jsoupMock.when(() -> Jsoup.connect(contains("update"))).thenReturn(postConnection);
        when(postConnection.data(anyString(), anyString())).thenReturn(postConnection);
        when(postConnection.cookies(any())).thenReturn(postConnection);
        when(postConnection.method(any(Connection.Method.class))).thenReturn(postConnection);
        when(postConnection.userAgent(anyString())).thenReturn(postConnection);
        when(postConnection.referrer(anyString())).thenReturn(postConnection);
        when(postConnection.header(anyString(), anyString())).thenReturn(postConnection);
        when(postConnection.followRedirects(false)).thenReturn(postConnection);
        when(postConnection.ignoreHttpErrors(true)).thenReturn(postConnection);
        when(postConnection.timeout(anyInt())).thenReturn(postConnection);
        if (throwIo) {
            when(postConnection.execute()).thenThrow(new IOException("connection reset"));
        } else {
            when(postConnection.execute()).thenReturn(updateResponse);
            when(updateResponse.statusCode()).thenReturn(updateStatus);
        }
    }

    // ========================================================================
    // テストケース
    // ========================================================================

    @Test
    @DisplayName("testPushSucceedsWithNewSchedules: 伝助にない日付があれば POST /update が呼ばれて成功（302）")
    void testPushSucceedsWithNewSchedules() throws IOException {
        // Given: アプリ側に 5/10 セッションあり、伝助には日程なし
        LocalDate appDate = LocalDate.of(YEAR, MONTH, 10);
        DensukeUrl url = buildDensukeUrl();
        PracticeSession session = buildAppSession(appDate);

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.of(url));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(YEAR, MONTH, ORG_ID))
                .thenReturn(List.of(session));
        when(densukeScraper.scrape(DENSUKE_URL, YEAR))
                .thenReturn(buildDensukeData(List.of())); // 伝助は空 → 全部新規
        when(venueRepository.findAllById(any())).thenReturn(List.of(buildVenue()));
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(buildSchedules());
        when(densukePageCreateService.buildScheduleText(any(), any(), any()))
                .thenReturn(new DensukePageCreateService.BuildResult("5/10(日) テスト会場 1試合目\n2試合目\n3試合目\n", 3));
        when(densukeWriteService.extractPageId(any(Document.class))).thenReturn(PAGE_ID);

        Connection getConn = org.mockito.Mockito.mock(Connection.class);
        Connection postConn = org.mockito.Mockito.mock(Connection.class);
        Connection.Response listResp = org.mockito.Mockito.mock(Connection.Response.class);
        Connection.Response updateResp = org.mockito.Mockito.mock(Connection.Response.class);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            stubJsoupCalls(jsoupMock, getConn, postConn, listResp, updateResp, 302, false);

            // When
            service.pushNewSchedulesToDensuke(YEAR, MONTH, ORG_ID);

            // Then: POST /update が実行された
            verify(postConn).execute();
            // 成功（302）なので通知は発火しない
            verify(lineNotificationService, never())
                    .sendDensukeScheduleSyncFailedNotification(any(), any());
        }
    }

    @Test
    @DisplayName("testPushSkipsWhenNoDiff: アプリと伝助の日程集合が一致なら POST しない")
    void testPushSkipsWhenNoDiff() throws IOException {
        // Given: アプリ側に 5/10 セッションあり、伝助にも同じ日付あり
        LocalDate appDate = LocalDate.of(YEAR, MONTH, 10);
        DensukeUrl url = buildDensukeUrl();
        PracticeSession session = buildAppSession(appDate);

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.of(url));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(YEAR, MONTH, ORG_ID))
                .thenReturn(List.of(session));
        when(densukeScraper.scrape(DENSUKE_URL, YEAR))
                .thenReturn(buildDensukeData(List.of(appDate))); // 同じ日付 → 差分なし

        // When
        service.pushNewSchedulesToDensuke(YEAR, MONTH, ORG_ID);

        // Then: buildScheduleText も Jsoup も呼ばれない
        verify(densukePageCreateService, never()).buildScheduleText(any(), any(), any());
        verify(densukeWriteService, never()).extractPageId(any(Document.class));
        // 通知は発火しない
        verify(lineNotificationService, never())
                .sendDensukeScheduleSyncFailedNotification(any(), any());
    }

    @Test
    @DisplayName("testPushSkipsWhenNoDensukeUrl: densuke_url が無ければ early return（POST もスクレイプも通知もなし）")
    void testPushSkipsWhenNoDensukeUrl() throws IOException {
        // Given: densuke_url 未登録
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.empty());

        // When
        service.pushNewSchedulesToDensuke(YEAR, MONTH, ORG_ID);

        // Then: 一切の副作用なし
        verify(practiceSessionRepository, never()).findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), any());
        verify(densukeScraper, never()).scrape(anyString(), anyInt());
        verify(densukePageCreateService, never()).buildScheduleText(any(), any(), any());
        verify(lineNotificationService, never())
                .sendDensukeScheduleSyncFailedNotification(any(), any());
    }

    @Test
    @DisplayName("testPushNotifiesAdminOnHttpFailure: HTTP 302 以外（500等）で失敗通知が呼ばれる")
    void testPushNotifiesAdminOnHttpFailure() throws IOException {
        // Given: 差分あり → POST /update が 500 を返す
        LocalDate appDate = LocalDate.of(YEAR, MONTH, 10);
        DensukeUrl url = buildDensukeUrl();
        PracticeSession session = buildAppSession(appDate);

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.of(url));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(YEAR, MONTH, ORG_ID))
                .thenReturn(List.of(session));
        when(densukeScraper.scrape(DENSUKE_URL, YEAR))
                .thenReturn(buildDensukeData(List.of()));
        when(venueRepository.findAllById(any())).thenReturn(List.of(buildVenue()));
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(buildSchedules());
        when(densukePageCreateService.buildScheduleText(any(), any(), any()))
                .thenReturn(new DensukePageCreateService.BuildResult("5/10(日) テスト会場 1試合目\n", 1));
        when(densukeWriteService.extractPageId(any(Document.class))).thenReturn(PAGE_ID);

        Connection getConn = org.mockito.Mockito.mock(Connection.class);
        Connection postConn = org.mockito.Mockito.mock(Connection.class);
        Connection.Response listResp = org.mockito.Mockito.mock(Connection.Response.class);
        Connection.Response updateResp = org.mockito.Mockito.mock(Connection.Response.class);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            stubJsoupCalls(jsoupMock, getConn, postConn, listResp, updateResp, 500, false);

            // When
            service.pushNewSchedulesToDensuke(YEAR, MONTH, ORG_ID);

            // Then: 失敗通知が呼ばれる
            verify(lineNotificationService)
                    .sendDensukeScheduleSyncFailedNotification(eq(ORG_ID), anyString());
        }
    }

    @Test
    @DisplayName("testPushNotifiesAdminOnIOException: scrape または POST が IOException を投げたら失敗通知が呼ばれる")
    void testPushNotifiesAdminOnIOException() throws IOException {
        // Given: scrape が IOException を投げる
        LocalDate appDate = LocalDate.of(YEAR, MONTH, 10);
        DensukeUrl url = buildDensukeUrl();
        PracticeSession session = buildAppSession(appDate);

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.of(url));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(YEAR, MONTH, ORG_ID))
                .thenReturn(List.of(session));
        when(densukeScraper.scrape(DENSUKE_URL, YEAR))
                .thenThrow(new IOException("network error"));

        // When
        service.pushNewSchedulesToDensuke(YEAR, MONTH, ORG_ID);

        // Then: 失敗通知が呼ばれる
        verify(lineNotificationService)
                .sendDensukeScheduleSyncFailedNotification(eq(ORG_ID), anyString());
        // POST には到達しない（scrape で early return）
        verify(densukePageCreateService, never()).buildScheduleText(any(), any(), any());
    }

    @Test
    @DisplayName("testPushSkipsAndNotifiesOnVenueMissing: 会場未設定セッションで IllegalStateException が起きた場合、失敗通知 + スキップ")
    void testPushSkipsAndNotifiesOnVenueMissing() throws IOException {
        // Given: 差分セッションあり、buildScheduleText が IllegalStateException
        LocalDate appDate = LocalDate.of(YEAR, MONTH, 10);
        DensukeUrl url = buildDensukeUrl();
        PracticeSession session = buildAppSession(appDate);

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.of(url));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(YEAR, MONTH, ORG_ID))
                .thenReturn(List.of(session));
        when(densukeScraper.scrape(DENSUKE_URL, YEAR))
                .thenReturn(buildDensukeData(List.of()));
        when(venueRepository.findAllById(any())).thenReturn(Collections.emptyList()); // 会場マスタ無し
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(Collections.emptyList());
        when(densukePageCreateService.buildScheduleText(any(), any(), any()))
                .thenThrow(new IllegalStateException(appDate + " の会場が登録されていません"));

        // When
        service.pushNewSchedulesToDensuke(YEAR, MONTH, ORG_ID);

        // Then: 失敗通知が呼ばれ、POST へは到達しない
        verify(lineNotificationService)
                .sendDensukeScheduleSyncFailedNotification(eq(ORG_ID), anyString());
        verify(densukeWriteService, never()).extractPageId(any(Document.class));
    }

    @Test
    @DisplayName("testPushSilentlyDoesNotNotifyOnFailure: pushSilently では HTTP 失敗時も通知が呼ばれない（フラッディング防止）")
    void testPushSilentlyDoesNotNotifyOnFailure() throws IOException {
        // Given: 差分あり → POST /update が 500 を返す（pushSilently 経路）
        LocalDate appDate = LocalDate.of(YEAR, MONTH, 10);
        DensukeUrl url = buildDensukeUrl();
        PracticeSession session = buildAppSession(appDate);

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate(YEAR, MONTH, ORG_ID))
                .thenReturn(Optional.of(url));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(YEAR, MONTH, ORG_ID))
                .thenReturn(List.of(session));
        when(densukeScraper.scrape(DENSUKE_URL, YEAR))
                .thenReturn(buildDensukeData(List.of()));
        when(venueRepository.findAllById(any())).thenReturn(List.of(buildVenue()));
        when(venueMatchScheduleRepository.findByVenueIdIn(any())).thenReturn(buildSchedules());
        when(densukePageCreateService.buildScheduleText(any(), any(), any()))
                .thenReturn(new DensukePageCreateService.BuildResult("5/10(日) テスト会場 1試合目\n", 1));
        when(densukeWriteService.extractPageId(any(Document.class))).thenReturn(PAGE_ID);

        Connection getConn = org.mockito.Mockito.mock(Connection.class);
        Connection postConn = org.mockito.Mockito.mock(Connection.class);
        Connection.Response listResp = org.mockito.Mockito.mock(Connection.Response.class);
        Connection.Response updateResp = org.mockito.Mockito.mock(Connection.Response.class);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            stubJsoupCalls(jsoupMock, getConn, postConn, listResp, updateResp, 500, false);

            // When: pushSilently 経路（スケジューラ用、notifyOnFailure=false）
            service.pushSilently(YEAR, MONTH, ORG_ID);

            // Then: HTTP 失敗でも通知は呼ばれない
            verify(lineNotificationService, never())
                    .sendDensukeScheduleSyncFailedNotification(any(), any());
        }
    }
}
