package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukePageCreateRequest;
import com.karuta.matchtracker.dto.DensukePageCreateResponse;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 伝助ページ作成サービス
 *
 * アプリ側の練習日データから densuke.biz にページを新規作成し、
 * 発行された cd/sd を densuke_urls に保存する。
 * 処理完了後 (AFTER_COMMIT) に LINE 通知を発火する。
 *
 * 作成フォーム仕様は docs/features/densuke-page-creator/densuke-form-spec.md 参照。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DensukePageCreateService {

    private final DensukeUrlRepository densukeUrlRepository;
    private final DensukeTemplateRepository densukeTemplateRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueRepository venueRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;
    private final OrganizationRepository organizationRepository;
    private final LineNotificationService lineNotificationService;

    private static final String DENSUKE_CREATE_URL = "https://www.densuke.biz/create";
    private static final String DENSUKE_LIST_URL_PREFIX = "https://densuke.biz/list?cd=";
    private static final String DEFAULT_TITLE_TEMPLATE = "{year}年{month}月 練習出欠";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    /** LocalDate.getDayOfWeek().getValue() は 月=1..日=7。modulo 7 で日=0..土=6 にそろえる。 */
    private static final String[] WEEKDAY_JP = {"日", "月", "火", "水", "木", "金", "土"};

    private static final Pattern CD_PATTERN = Pattern.compile("cd=([A-Za-z0-9]+)");
    private static final Pattern SD_PATTERN = Pattern.compile("sd=([A-Za-z0-9.]+)");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Transactional
    public DensukePageCreateResponse createPage(DensukePageCreateRequest request) {
        int year = request.getYear();
        int month = request.getMonth();
        Long organizationId = request.getOrganizationId();

        // 0. 作成対象年月の範囲チェック（当月〜+2ヶ月のみ。UI の canCreatePage と同等の制約を API 側にも強制）
        validateYearMonth(year, month, YearMonth.now(JstDateTimeUtil.JST));

        // 1. 既存 URL の重複チェック（早期失敗）
        if (densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId).isPresent()) {
            throw new IllegalStateException(year + "年" + month + "月の伝助URLは既に登録されています");
        }

        // 2. 対象月の練習セッション取得
        List<PracticeSession> sessions = practiceSessionRepository
                .findByYearAndMonthAndOrganizationId(year, month, organizationId);
        if (sessions.isEmpty()) {
            throw new IllegalStateException(year + "年" + month + "月に練習日が登録されていません");
        }

        // 3. 会場・試合時刻マスタのロード
        List<Long> venueIds = sessions.stream()
                .map(PracticeSession::getVenueId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Venue> venueMap = venueRepository.findAllById(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, v -> v));
        Map<Long, Map<Integer, VenueMatchSchedule>> scheduleMap = venueMatchScheduleRepository
                .findByVenueIdIn(venueIds).stream()
                .collect(Collectors.groupingBy(
                        VenueMatchSchedule::getVenueId,
                        Collectors.toMap(VenueMatchSchedule::getMatchNumber, s -> s)
                ));

        // 4. schedule 文字列組み立て + 不整合チェック
        BuildResult built = buildScheduleText(sessions, venueMap, scheduleMap);

        // 5. テンプレート + overrides の解決
        ResolvedTemplate resolved = resolveTemplate(organizationId, year, month, request.getOverrides());

        // 6. 排他制御のための仮レコード先行確保
        //    densuke_urls の UNIQUE(year, month, organization_id) により、同時実行された2つ目の
        //    トランザクションは 1つ目がコミット/ロールバックするまでブロックされ、その後ユニーク違反で失敗する。
        //    これにより densuke.biz への二重 POST（オーファンページ発生）を防ぐ。
        DensukeUrl urlEntity = DensukeUrl.builder()
                .year(year)
                .month(month)
                .organizationId(organizationId)
                .url(DENSUKE_LIST_URL_PREFIX + "pending")
                .build();
        try {
            densukeUrlRepository.saveAndFlush(urlEntity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    year + "年" + month + "月の伝助URLは既に登録されています", e);
        }

        // 7. densuke.biz に新規作成 POST（失敗時はロールバックで仮レコードも消える）
        CdSdPair cdSd;
        try {
            cdSd = postCreateRequest(resolved.title, built.scheduleText,
                    resolved.description, resolved.contactEmail);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to create Densuke page (org={}, year={}, month={})",
                    organizationId, year, month, e);
            throw new IllegalStateException("伝助ページの作成に失敗しました: " + e.getMessage(), e);
        }

        String densukeUrl = DENSUKE_LIST_URL_PREFIX + cdSd.cd;

        // 8. 仮レコードを実 URL / sd で更新
        urlEntity.setUrl(densukeUrl);
        urlEntity.setDensukeSd(cdSd.sd);
        densukeUrlRepository.save(urlEntity);

        // 9. AFTER_COMMIT で LINE 通知を非同期ディスパッチ
        //    sendDensukePageCreatedNotification は @Async 指定で TaskExecutor 上に fire-and-forget
        //    される。afterCommit 内で呼ぶのは「コミット前の未反映レコードを非同期スレッドが読んで
        //    しまう」のを避けるため。ここでの try/catch は @Async ディスパッチ自体が失敗した場合の
        //    保険（通常発生しないが、エグゼキューター満杯等で RejectedExecutionException が出ると
        //    呼び出し元に伝播してしまうため握りつぶしてログ化する）。
        String lineMessage = buildLineMessage(resolved.organizationName, year, month, densukeUrl);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    lineNotificationService.sendDensukePageCreatedNotification(organizationId, lineMessage);
                } catch (Exception e) {
                    log.warn("DENSUKE_PAGE_CREATED async dispatch failed: org={}, err={}",
                            organizationId, e.getMessage());
                }
            }
        });

        log.info("Densuke page created: org={}, year={}, month={}, cd={}, dates={}, matchSlots={}",
                organizationId, year, month, cdSd.cd, sessions.size(), built.matchSlotCount);

        return DensukePageCreateResponse.builder()
                .cd(cdSd.cd)
                .url(densukeUrl)
                .createdDateCount(sessions.size())
                .createdMatchSlotCount(built.matchSlotCount)
                .build();
    }

    // ========================================================================
    // 年月バリデーション
    // ========================================================================

    /**
     * 作成対象年月が「当月〜+2ヶ月」の範囲内にあることを検証する。
     * 範囲外（過去月・3ヶ月以上先）は IllegalStateException として 400 を返す。
     *
     * package-private としてテストから任意の「現在年月」を注入できるようにしている。
     */
    static void validateYearMonth(int year, int month, YearMonth current) {
        YearMonth target;
        try {
            target = YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new IllegalStateException("年月の指定が不正です: " + year + "/" + month, e);
        }
        YearMonth maxAllowed = current.plusMonths(2);
        if (target.isBefore(current) || target.isAfter(maxAllowed)) {
            throw new IllegalStateException(
                    year + "年" + month + "月は作成可能範囲外です（当月〜+2ヶ月のみ作成可能）");
        }
    }

    // ========================================================================
    // schedule 組み立てとバリデーション
    // ========================================================================

    // package-private: フォーマットの回帰テストから直接呼び出せるよう可視性を広げている
    BuildResult buildScheduleText(List<PracticeSession> sessions,
                                   Map<Long, Venue> venueMap,
                                   Map<Long, Map<Integer, VenueMatchSchedule>> scheduleMap) {
        StringBuilder sb = new StringBuilder();
        int matchSlotCount = 0;

        for (PracticeSession session : sessions) {
            Long venueId = session.getVenueId();
            Venue venue = (venueId != null) ? venueMap.get(venueId) : null;
            if (venue == null) {
                throw new IllegalStateException(
                        session.getSessionDate() + " の会場が登録されていません");
            }

            int totalMatches = (session.getTotalMatches() != null)
                    ? session.getTotalMatches()
                    : venue.getDefaultMatchCount();

            Map<Integer, VenueMatchSchedule> venueSchedules =
                    scheduleMap.getOrDefault(venueId, Collections.emptyMap());

            // 不整合チェック: total_matches 分の時刻があるか
            for (int mn = 1; mn <= totalMatches; mn++) {
                if (!venueSchedules.containsKey(mn)) {
                    throw new IllegalStateException(
                            "会場「" + venue.getName() + "」に" + mn
                                    + "試合目の時間割が登録されていません");
                }
            }

            LocalDate date = session.getSessionDate();
            int m = date.getMonthValue();
            int d = date.getDayOfMonth();
            String weekday = WEEKDAY_JP[date.getDayOfWeek().getValue() % 7];

            // 新フォーマット:
            //   1試合目行:      「M/D(曜) 会場名 1試合目」（日付・会場のヘッダーを兼ねる）
            //   2試合目以降:   「N試合目」のみ（DensukeScraper は currentDate/currentVenue を
            //                                    前行から引き継ぐため、日付・会場省略でも同期可能）
            for (int mn = 1; mn <= totalMatches; mn++) {
                if (mn == 1) {
                    sb.append(m).append('/').append(d)
                            .append('(').append(weekday).append(") ")
                            .append(venue.getName()).append(' ')
                            .append(mn).append("試合目\n");
                } else {
                    sb.append(mn).append("試合目\n");
                }
                matchSlotCount++;
            }
        }

        return new BuildResult(sb.toString(), matchSlotCount);
    }

    // ========================================================================
    // テンプレート解決
    // ========================================================================

    private ResolvedTemplate resolveTemplate(Long organizationId, int year, int month,
                                              DensukePageCreateRequest.Overrides overrides) {
        DensukeTemplate template = densukeTemplateRepository
                .findByOrganizationId(organizationId).orElse(null);

        String titleTemplate = (template != null && template.getTitleTemplate() != null)
                ? template.getTitleTemplate() : DEFAULT_TITLE_TEMPLATE;
        String description = (template != null && template.getDescription() != null)
                ? template.getDescription() : "";
        String contactEmail = (template != null && template.getContactEmail() != null)
                ? template.getContactEmail() : "";

        if (overrides != null) {
            if (overrides.getTitle() != null) titleTemplate = overrides.getTitle();
            if (overrides.getDescription() != null) description = overrides.getDescription();
            if (overrides.getContactEmail() != null) contactEmail = overrides.getContactEmail();
        }

        Organization org = organizationRepository.findById(organizationId).orElse(null);
        String orgName = (org != null) ? org.getName() : "";

        String title = applyPlaceholders(titleTemplate, year, month, orgName);
        return new ResolvedTemplate(title, description, contactEmail, orgName);
    }

    private String applyPlaceholders(String template, int year, int month, String orgName) {
        return template
                .replace("{year}", String.valueOf(year))
                .replace("{month}", String.valueOf(month))
                .replace("{organization_name}", orgName != null ? orgName : "");
    }

    // ========================================================================
    // densuke.biz への POST
    // ========================================================================

    private CdSdPair postCreateRequest(String title, String scheduleText,
                                        String description, String contactEmail)
            throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("postfix", "");
        fields.put("eventname", title);
        fields.put("schedule", scheduleText);
        fields.put("explain", description != null ? description : "");
        fields.put("email", contactEmail != null ? contactEmail : "");
        fields.put("pw", "0");
        fields.put("password", "");
        fields.put("eventchoice", "1");

        String body = fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(DENSUKE_CREATE_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://www.densuke.biz")
                .header("Referer", "https://www.densuke.biz/confirm")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<Void> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());

        if (res.statusCode() != 302) {
            throw new IOException("伝助からの応答が不正です: HTTP " + res.statusCode());
        }

        String location = res.headers().firstValue("Location")
                .orElseThrow(() -> new IOException("Location ヘッダーが取得できませんでした"));

        Matcher cdMatcher = CD_PATTERN.matcher(location);
        if (!cdMatcher.find()) {
            throw new IOException("伝助レスポンスから cd を抽出できませんでした: Location=" + location);
        }
        String cd = cdMatcher.group(1);

        Matcher sdMatcher = SD_PATTERN.matcher(location);
        String sd = sdMatcher.find() ? sdMatcher.group(1) : null;

        return new CdSdPair(cd, sd);
    }

    // ========================================================================
    // LINE 通知メッセージ組み立て
    // ========================================================================

    private String buildLineMessage(String orgName, int year, int month, String densukeUrl) {
        String titleLine = month + "月の練習日程が出ました";
        String prefix = (orgName != null && !orgName.isEmpty()) ? orgName + "の" : "";
        String body = prefix + year + "年" + month + "月の練習出欠ページが作成されました。\n"
                + "以下のリンクから出欠を登録してください:\n" + densukeUrl;
        return titleLine + "\n\n" + body;
    }

    // ========================================================================
    // 内部データクラス
    // ========================================================================

    record BuildResult(String scheduleText, int matchSlotCount) {}

    private record ResolvedTemplate(String title, String description, String contactEmail, String organizationName) {}

    private record CdSdPair(String cd, String sd) {}
}
