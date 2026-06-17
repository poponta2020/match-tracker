package com.karuta.matchtracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.karuta.matchtracker.dto.MatchVideoCreateRequest;
import com.karuta.matchtracker.dto.MatchVideoDto;
import com.karuta.matchtracker.dto.MatchVideoUpdateRequest;
import com.karuta.matchtracker.dto.PagedResponse;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchVideo;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.MatchVideoRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 試合動画（動画台帳）の管理サービス
 *
 * 動画の登録・URL差し替え・削除・日付別一覧・倉庫検索を担当する。
 * YouTube URL の検証と動画ID抽出、oEmbed によるタイトル自動取得（fail-soft）、
 * 編集・削除の所有者チェックを行う。
 *
 * <p>動画は {@code matches} / {@code match_pairings} とFKを持たず、
 * (match_date, match_number, player1_id, player2_id) の自然キーで対応付く。
 * player1_id &lt; player2_id をサービス層で正規化したうえで存在チェック・登録を行う。</p>
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class MatchVideoService {

    static final String MSG_INVALID_URL = "YouTubeのURLを入力してください";
    static final String MSG_MATCH_NOT_FOUND = "対象の試合が見つかりません";
    static final String MSG_DUPLICATE = "この試合には既に動画が登録されています";
    static final String MSG_FORBIDDEN = "この動画を編集・削除する権限がありません";
    static final String MSG_INVALID_OPPONENT = "対戦相手が不正です";

    /** 自然キーUNIQUE制約名（match_videos）。DataIntegrityViolation を重複(409)に変換する条件に使う。 */
    static final String UNIQUE_CONSTRAINT_NAME = "uq_match_videos_match";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 倉庫検索で受け付ける年の下限・上限（運用上妥当な範囲。範囲外は400で弾く）。 */
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    static final String MSG_INVALID_YEAR = "年は" + MIN_YEAR + "〜" + MAX_YEAR + "で指定してください";
    static final String MSG_INVALID_MONTH = "月は1〜12で指定してください";

    private static final String OEMBED_ENDPOINT = "https://www.youtube.com/oembed";

    /**
     * 受け付けるYouTube URLから動画ID（11文字）を抽出する正規表現。
     * 対応形式: youtube.com/watch?v= / youtu.be/ / m.youtube.com/watch?v= / youtube.com/shorts/
     * （www有無・http/https・末尾の追加クエリパラメータを許容）
     */
    private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(
            "^https?://" +
            "(?:" +
                "(?:www\\.|m\\.)?youtube\\.com/watch\\?(?:[^&]*&)*v=([A-Za-z0-9_-]{11})" +
                "|" +
                "(?:www\\.|m\\.)?youtube\\.com/shorts/([A-Za-z0-9_-]{11})" +
                "|" +
                "youtu\\.be/([A-Za-z0-9_-]{11})" +
            ")" +
            "(?:[/?&#].*)?$");

    private final MatchVideoRepository matchVideoRepository;
    private final MatchRepository matchRepository;
    private final MatchPairingRepository matchPairingRepository;
    private final PlayerRepository playerRepository;
    private final LineNotificationService lineNotificationService;
    private final RestClient oembedRestClient;

    public MatchVideoService(MatchVideoRepository matchVideoRepository,
                             MatchRepository matchRepository,
                             MatchPairingRepository matchPairingRepository,
                             PlayerRepository playerRepository,
                             LineNotificationService lineNotificationService,
                             RestClient.Builder restClientBuilder) {
        this.matchVideoRepository = matchVideoRepository;
        this.matchRepository = matchRepository;
        this.matchPairingRepository = matchPairingRepository;
        this.playerRepository = playerRepository;
        this.lineNotificationService = lineNotificationService;
        // oEmbed は外部I/O。接続・読取とも短いタイムアウトにし、失敗時は title=null で続行する。
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.oembedRestClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    // ===================== 登録 =====================

    /**
     * 動画を登録する。
     *
     * 処理フロー:
     * 1. YouTube URL検証・videoId抽出（不正なら400相当の IllegalArgumentException）
     * 2. キー正規化（player1Id &lt; player2Id）
     * 3. 対象試合の存在チェック（matches または match_pairings に同自然キーが存在）
     * 4. 重複チェック（既に動画があれば409相当の DuplicateResourceException）
     * 5. oEmbedタイトル取得（fail-soft: 失敗しても title=null で続行）
     * 6. INSERT（created_by / updated_by = 操作ユーザー）
     *
     * @param request   登録リクエスト
     * @param currentUserId 操作ユーザーID
     * @return 登録された動画DTO
     */
    @Transactional
    public MatchVideoDto register(MatchVideoCreateRequest request, Long currentUserId) {
        String videoId = extractYoutubeVideoId(request.getVideoUrl());

        // キー正規化（player1Id < player2Id）
        long p1 = request.getPlayer1Id();
        long p2 = request.getPlayer2Id();
        long normP1 = Math.min(p1, p2);
        long normP2 = Math.max(p1, p2);
        LocalDate matchDate = request.getMatchDate();
        Integer matchNumber = request.getMatchNumber();

        // 選手IDの妥当性チェック（DTOの @Positive・@NotNull と二重防御）:
        // ・正規化後に同一選手（normP1 == normP2）であれば対戦が成立しないため400で拒否する
        // ・正の値であること（normP1 <= 0 は不正IDやゲスト番兵値0）を念のため確認する
        //   ※ 通常は Controller の @Valid（@Positive）で 400 になるが、サービス直呼び出し時の保険として残す
        if (normP1 <= 0 || normP1 == normP2) {
            throw new IllegalArgumentException(MSG_INVALID_OPPONENT);
        }

        // 対象試合の存在チェック（matches または match_pairings）
        if (!matchOrPairingExists(matchDate, matchNumber, normP1, normP2)) {
            throw new ResourceNotFoundException(MSG_MATCH_NOT_FOUND);
        }

        // 重複チェック（自然キーは正規化済み前提）
        matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(matchDate, matchNumber, normP1, normP2)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(MSG_DUPLICATE);
                });

        // oEmbedタイトル取得（fail-soft）
        String title = fetchTitle(request.getVideoUrl());

        MatchVideo video = MatchVideo.builder()
                .matchDate(matchDate)
                .matchNumber(matchNumber)
                .player1Id(normP1)
                .player2Id(normP2)
                .provider("YOUTUBE")
                .videoUrl(request.getVideoUrl())
                .youtubeVideoId(videoId)
                .title(title)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        MatchVideo saved;
        try {
            // saveAndFlush で INSERT を即時 flush し、UNIQUE 制約違反をこの try 内で確実に捕捉する
            // （save だけだと flush が commit 時まで遅延し、例外が try 外に漏れて500化する恐れがある）。
            saved = matchVideoRepository.saveAndFlush(video);
        } catch (DataIntegrityViolationException e) {
            // 重複チェック後〜flush までの間に別リクエストが同一自然キーを登録した場合、
            // uq_match_videos_match の一意制約違反となる。この競合のみ重複として409に変換する
            // （事前の findBy... チェックだけでは TOCTOU で取りこぼすため、最終防衛として扱う）。
            // FK違反やNOT NULL違反など他の整合性エラーまで409にしないよう、自然キー制約由来かを判定し、
            // 該当しなければそのまま再throwして本来のステータス（500等）で扱う。
            if (isMatchVideoUniqueViolation(e)) {
                log.info("試合動画の同時登録による一意制約違反を検知（409に変換）: matchDate={}, matchNumber={}, players=({},{})",
                        matchDate, matchNumber, normP1, normP2);
                throw new DuplicateResourceException(MSG_DUPLICATE);
            }
            log.error("試合動画登録で一意制約以外の整合性違反が発生（再throw）: matchDate={}, matchNumber={}, players=({},{})",
                    matchDate, matchNumber, normP1, normP2, e);
            throw e;
        }
        log.info("試合動画登録: id={}, matchDate={}, matchNumber={}, players=({},{}), by={}",
                saved.getId(), matchDate, matchNumber, normP1, normP2, currentUserId);

        MatchVideoDto dto = toDto(saved);

        // 新規登録時のみ、対戦当事者（登録者を除く）へ LINE 通知をトリガする。
        // 通知の失敗で登録トランザクションを巻き戻さないよう、例外は握りつぶしてログのみ残す
        // （URL差し替え updateUrl では通知しない）。matchId は結果入力済みのときのみ非null。
        try {
            Long matchId = dto != null ? dto.getMatchId() : null;
            lineNotificationService.sendMatchVideoRegisteredNotification(
                    currentUserId, normP1, normP2, matchDate, matchNumber, matchId);
        } catch (Exception e) {
            log.warn("試合動画登録のLINE通知に失敗（登録は継続）: videoId={}, error={}",
                    saved.getId(), e.getMessage());
        }

        return dto;
    }

    // ===================== 更新（URL差し替え） =====================

    /**
     * 動画URLを差し替える。
     *
     * 権限: 登録者本人（createdBy）または ADMIN/SUPER_ADMIN のみ。
     * URLを再検証・videoIdを再抽出し、oEmbedタイトルを再取得（fail-soft）する。
     *
     * @param id        動画ID
     * @param request   更新リクエスト
     * @param currentUserId 操作ユーザーID
     * @param currentUserRole 操作ユーザーのロール
     * @return 更新後の動画DTO
     */
    @Transactional
    public MatchVideoDto updateUrl(Long id, MatchVideoUpdateRequest request, Long currentUserId, Role currentUserRole) {
        MatchVideo video = matchVideoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MatchVideo", id));

        verifyEditPermission(video, currentUserId, currentUserRole);

        String videoId = extractYoutubeVideoId(request.getVideoUrl());
        String title = fetchTitle(request.getVideoUrl());

        video.setVideoUrl(request.getVideoUrl());
        video.setYoutubeVideoId(videoId);
        video.setTitle(title);
        video.setUpdatedBy(currentUserId);

        MatchVideo saved = matchVideoRepository.save(video);
        log.info("試合動画URL差し替え: id={}, by={}", id, currentUserId);

        return toDto(saved);
    }

    // ===================== 削除（物理削除） =====================

    /**
     * 動画を削除する（紐付けの物理削除）。
     *
     * 権限: 登録者本人または ADMIN/SUPER_ADMIN のみ。
     * 削除されるのは台帳の紐付けのみで、YouTube上の動画本体は残る。
     *
     * @param id        動画ID
     * @param currentUserId 操作ユーザーID
     * @param currentUserRole 操作ユーザーのロール
     */
    @Transactional
    public void delete(Long id, Long currentUserId, Role currentUserRole) {
        MatchVideo video = matchVideoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MatchVideo", id));

        verifyEditPermission(video, currentUserId, currentUserRole);

        matchVideoRepository.delete(video);
        log.info("試合動画削除: id={}, by={}", id, currentUserId);
    }

    // ===================== 日付別一覧 =====================

    /**
     * 指定日の動画一覧を取得する（試合番号の昇順）。
     * 当日結果一覧の「動画あり」バッジ表示に使用する。
     *
     * @param date 対戦日
     * @return 動画DTOのリスト
     */
    public List<MatchVideoDto> findByDate(LocalDate date) {
        List<MatchVideo> videos = matchVideoRepository.findByMatchDate(date);
        return enrich(videos);
    }

    // ===================== 倉庫検索 =====================

    /**
     * 動画倉庫の検索（ページング）。
     *
     * - {@code mine=true} の場合は操作ユーザー自身を対象選手とする（playerId 指定より優先）
     * - {@code year}/{@code month} は範囲（startDate/endDate）に変換して絞り込む
     * - 並びは matchDate DESC, matchNumber DESC（リポジトリ実装）
     *
     * @param playerId 対戦者で絞り込む選手ID（null可）
     * @param year     対象年（null可）
     * @param month    対象月（null可、year併用時のみ有効）
     * @param mine     true の場合 currentUserId を playerId として扱う
     * @param page     ページ番号（null時は0）
     * @param size     1ページ件数（null時は20、上限100）
     * @param currentUserId 操作ユーザーID
     * @return ページングレスポンス
     */
    public PagedResponse<MatchVideoDto> search(Long playerId, Integer year, Integer month,
                                               boolean mine, Integer page, Integer size,
                                               Long currentUserId) {
        Long effectivePlayerId = mine ? currentUserId : playerId;

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (year != null) {
            // 不正な year/month をそのまま LocalDate.of に渡すと DateTimeException → 500 になり得るため、
            // ここでユーザー向けの 400（IllegalArgumentException）として弾く。
            // month==null の既存挙動（年のみ絞り込み）は変えず、year 非null時のみ検証する。
            validateYear(year);
            if (month != null) {
                validateMonth(month);
                YearMonthRange range = YearMonthRange.of(year, month);
                startDate = range.start();
                endDate = range.end();
            } else {
                startDate = LocalDate.of(year, 1, 1);
                endDate = LocalDate.of(year, 12, 31);
            }
        }

        Pageable pageable = buildPageable(page, size);
        Page<MatchVideo> result = matchVideoRepository.search(effectivePlayerId, startDate, endDate, pageable);

        // ページ内要素をバッチで enrich（N+1回避）したうえで PagedResponse に詰め替える
        Map<Long, MatchVideoDto> dtoById = enrich(result.getContent()).stream()
                .collect(Collectors.toMap(MatchVideoDto::getId, dto -> dto));
        return PagedResponse.from(result, v -> dtoById.get(v.getId()));
    }

    // ===================== 内部処理 =====================

    /**
     * 指定の自然キー（正規化済み）に一致する試合が matches または match_pairings に存在するか。
     * match_pairings は p1&lt;p2 を保証しないため、順序不問で照合する。
     */
    private boolean matchOrPairingExists(LocalDate matchDate, Integer matchNumber, long normP1, long normP2) {
        boolean inMatches = matchRepository
                .findByMatchDateAndMatchNumberAndPlayers(matchDate, matchNumber, normP1, normP2)
                .isPresent();
        if (inMatches) {
            return true;
        }
        return matchPairingRepository
                .findBySessionDateAndMatchNumberAndPlayers(matchDate, matchNumber, normP1, normP2)
                .isPresent();
    }

    /**
     * 例外チェーンが match_videos の自然キーUNIQUE制約（{@code uq_match_videos_match}）違反に
     * 起因するかを判定する。
     *
     * <p>判定優先順位:
     * <ol>
     *   <li>原因チェーンに Hibernate の {@link org.hibernate.exception.ConstraintViolationException} が
     *       あれば {@code getConstraintName()} を制約名と照合（大文字小文字非依存・部分一致）。
     *       これが最も堅牢（DBエラーメッセージのロケール差に依存しない）。</li>
     *   <li>制約名が取得できない／Hibernate例外が無い場合のフォールバックとして、
     *       原因チェーン各段の {@code getMessage()} に制約名が含まれるかで判定する。</li>
     * </ol>
     *
     * FK違反・NOT NULL違反など他の整合性違反では false を返し、呼び出し側で再throwさせる。
     *
     * @param ex 捕捉した {@link DataIntegrityViolationException}
     * @return 自然キーUNIQUE制約違反と判断できれば true
     */
    static boolean isMatchVideoUniqueViolation(DataIntegrityViolationException ex) {
        String target = UNIQUE_CONSTRAINT_NAME.toLowerCase();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toLowerCase().contains(target)) {
                    return true;
                }
            }
            // フォールバック: 制約名が取れない／Hibernate例外でない場合はメッセージで判定する
            String message = t.getMessage();
            if (message != null && message.toLowerCase().contains(target)) {
                return true;
            }
            // 自己参照ループ（getCause() が自身を返す壊れた例外）での無限ループを防ぐ
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * 編集・削除権限を検証する。登録者本人 or ADMIN/SUPER_ADMIN のみ許可。
     */
    private void verifyEditPermission(MatchVideo video, Long currentUserId, Role currentUserRole) {
        boolean isOwner = currentUserId != null && currentUserId.equals(video.getCreatedBy());
        boolean isAdmin = currentUserRole == Role.ADMIN || currentUserRole == Role.SUPER_ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException(MSG_FORBIDDEN);
        }
    }

    /**
     * YouTube URL を検証し、動画ID（11文字）を抽出する。
     * 形式外の場合は IllegalArgumentException（GlobalExceptionHandler で400に変換）。
     *
     * @param url 動画URL
     * @return 動画ID
     */
    static String extractYoutubeVideoId(String url) {
        if (url == null) {
            throw new IllegalArgumentException(MSG_INVALID_URL);
        }
        Matcher matcher = YOUTUBE_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(MSG_INVALID_URL);
        }
        // いずれかのキャプチャグループに動画IDが入る
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                return matcher.group(i);
            }
        }
        // ここには到達しない想定（パターンが一致したのにグループが取れない場合）
        throw new IllegalArgumentException(MSG_INVALID_URL);
    }

    /**
     * YouTube oEmbed API から動画タイトルを取得する（fail-soft）。
     * タイムアウトや非200・パース失敗時は null を返し、登録・更新を続行させる。
     *
     * @param videoUrl 動画URL
     * @return タイトル（取得失敗時は null）
     */
    String fetchTitle(String videoUrl) {
        try {
            String requestUri = UriComponentsBuilder.fromUriString(OEMBED_ENDPOINT)
                    .queryParam("url", videoUrl)
                    .queryParam("format", "json")
                    .build()
                    .toUriString();
            OEmbedResponse response = oembedRestClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .body(OEmbedResponse.class);
            return response != null ? response.title() : null;
        } catch (Exception e) {
            log.warn("oEmbedタイトル取得に失敗（title=nullで続行）: url={}, error={}", videoUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 倉庫検索の year を検証する。範囲外は 400（IllegalArgumentException）。
     * 極端な値（例: 0 や 99999）で {@code LocalDate.of} が DateTimeException を投げ 500 になるのを防ぐ。
     */
    private static void validateYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException(MSG_INVALID_YEAR);
        }
    }

    /**
     * 倉庫検索の month を検証する。1〜12 以外は 400（IllegalArgumentException）。
     * month=0 / month=13 等で {@code LocalDate.of} が DateTimeException を投げ 500 になるのを防ぐ。
     */
    private static void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException(MSG_INVALID_MONTH);
        }
    }

    private Pageable buildPageable(Integer page, Integer size) {
        int resolvedPage = (page == null || page < 0) ? 0 : page;
        int resolvedSize;
        if (size == null || size < 1) {
            resolvedSize = DEFAULT_PAGE_SIZE;
        } else {
            resolvedSize = Math.min(size, MAX_PAGE_SIZE);
        }
        // 並びはリポジトリの JPQL（matchDate DESC, matchNumber DESC）で確定するため Sort 指定なし
        return PageRequest.of(resolvedPage, resolvedSize);
    }

    /**
     * 単一の動画をDTOへ変換する（選手名・試合結果を解決）。
     */
    private MatchVideoDto toDto(MatchVideo video) {
        List<MatchVideoDto> enriched = enrich(List.of(video));
        return enriched.isEmpty() ? null : enriched.get(0);
    }

    /**
     * 動画リストをDTOへ変換する。選手名・対応する試合結果（matches）を
     * バッチ取得して N+1 を回避する。
     */
    private List<MatchVideoDto> enrich(List<MatchVideo> videos) {
        if (videos.isEmpty()) {
            return List.of();
        }

        // 選手名を一括解決
        List<Long> playerIds = videos.stream()
                .flatMap(v -> Stream.of(v.getPlayer1Id(), v.getPlayer2Id()))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> playerNames = new HashMap<>();
        playerRepository.findAllById(playerIds)
                .forEach(p -> playerNames.put(p.getId(), p.getName()));

        // 対応する試合結果（matches）を日付一括で取得し、自然キーで照合
        List<LocalDate> dates = videos.stream()
                .map(MatchVideo::getMatchDate)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Match> matchesByKey = matchRepository.findByMatchDateIn(dates).stream()
                .collect(Collectors.toMap(MatchVideoService::naturalKey, m -> m, (a, b) -> a));

        return videos.stream()
                .map(v -> {
                    Match match = matchesByKey.get(naturalKey(v));
                    return MatchVideoDto.fromEntity(
                            v,
                            playerNames.get(v.getPlayer1Id()),
                            playerNames.get(v.getPlayer2Id()),
                            match);
                })
                .collect(Collectors.toList());
    }

    private static String naturalKey(Match m) {
        return m.getMatchDate() + "|" + m.getMatchNumber() + "|" + m.getPlayer1Id() + "|" + m.getPlayer2Id();
    }

    private static String naturalKey(MatchVideo v) {
        return v.getMatchDate() + "|" + v.getMatchNumber() + "|" + v.getPlayer1Id() + "|" + v.getPlayer2Id();
    }

    /**
     * oEmbed レスポンス（必要なフィールドのみ）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OEmbedResponse(String title) {
    }

    /**
     * 年月から該当月の開始日・終了日（両端含む）を算出するヘルパー。
     */
    record YearMonthRange(LocalDate start, LocalDate end) {
        static YearMonthRange of(int year, int month) {
            // 防御的ガード（ユーザー向けの 400 メッセージは search 側の validateMonth/validateYear で出す）。
            // 直接呼ばれた場合も DateTimeException → 500 にせず IllegalArgumentException(400) に倒す。
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException(MSG_INVALID_MONTH);
            }
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            return new YearMonthRange(start, end);
        }
    }
}
