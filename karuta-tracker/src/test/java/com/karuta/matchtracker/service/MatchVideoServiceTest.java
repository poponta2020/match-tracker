package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MatchVideoCreateRequest;
import com.karuta.matchtracker.dto.MatchVideoDto;
import com.karuta.matchtracker.dto.MatchVideoUpdateRequest;
import com.karuta.matchtracker.dto.PagedResponse;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.entity.MatchVideo;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.MatchVideoRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestClient;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MatchVideoServiceの単体テスト
 *
 * oEmbed の HTTP 呼び出し（{@code fetchTitle}）は service を spy 化してスタブし、
 * 外部I/Oに依存せずビジネスロジックの分岐を検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchVideoService 単体テスト")
class MatchVideoServiceTest {

    @Mock
    private MatchVideoRepository matchVideoRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchPairingRepository matchPairingRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private LineNotificationService lineNotificationService;

    private MatchVideoService matchVideoService;

    private Player player1;
    private Player player2;
    private LocalDate today;

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String VALID_VIDEO_ID = "dQw4w9WgXcQ";

    @BeforeEach
    void setUp() {
        today = LocalDate.of(2026, 6, 12);

        player1 = Player.builder().id(1L).name("山田太郎").build();
        player2 = Player.builder().id(2L).name("佐藤花子").build();

        // 実 RestClient.Builder を渡して構築（ネットワーク呼び出しは fetchTitle のスタブで遮断）。
        // 個々のテストで fetchTitle の戻り値を制御するため spy 化する。
        MatchVideoService real = new MatchVideoService(
                matchVideoRepository, matchRepository, matchPairingRepository,
                playerRepository, lineNotificationService, RestClient.builder());
        matchVideoService = spy(real);
    }

    private MatchVideoCreateRequest createRequest(Long p1, Long p2, String url) {
        MatchVideoCreateRequest req = new MatchVideoCreateRequest();
        req.setMatchDate(today);
        req.setMatchNumber(1);
        req.setPlayer1Id(p1);
        req.setPlayer2Id(p2);
        req.setVideoUrl(url);
        return req;
    }

    @Nested
    @DisplayName("YouTube URL 検証・動画ID抽出")
    class UrlValidation {

        @Test
        @DisplayName("youtube.com/watch?v= 形式を受理し動画IDを抽出できる")
        void testWatchUrl() {
            assertThat(MatchVideoService.extractYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
            // www なし・追加クエリパラメータあり
            assertThat(MatchVideoService.extractYoutubeVideoId("http://youtube.com/watch?v=abcdefghijk&t=30s"))
                    .isEqualTo("abcdefghijk");
        }

        @Test
        @DisplayName("youtu.be/ 形式を受理し動画IDを抽出できる")
        void testYoutuBeUrl() {
            assertThat(MatchVideoService.extractYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
            assertThat(MatchVideoService.extractYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=xxxx"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("m.youtube.com/watch 形式を受理し動画IDを抽出できる")
        void testMobileUrl() {
            assertThat(MatchVideoService.extractYoutubeVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("youtube.com/shorts/ 形式を受理し動画IDを抽出できる")
        void testShortsUrl() {
            assertThat(MatchVideoService.extractYoutubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
                    .isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("YouTube以外のURLは拒否される")
        void testNonYoutubeUrl() {
            assertThatThrownBy(() -> MatchVideoService.extractYoutubeVideoId("https://vimeo.com/123456789"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("YouTubeのURLを入力してください");
        }

        @Test
        @DisplayName("動画IDの長さが不正なURLは拒否される")
        void testInvalidVideoIdLength() {
            // 10文字（短い）
            assertThatThrownBy(() -> MatchVideoService.extractYoutubeVideoId("https://youtu.be/shortid123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("YouTubeのURLを入力してください");
            // 12文字（長い）→ 11文字一致後に余計な文字が続くため不一致
            assertThatThrownBy(() -> MatchVideoService.extractYoutubeVideoId("https://www.youtube.com/watch?v=toolongvideoid"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("nullや空文字は拒否される")
        void testNullAndBlank() {
            assertThatThrownBy(() -> MatchVideoService.extractYoutubeVideoId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("YouTubeのURLを入力してください");
            assertThatThrownBy(() -> MatchVideoService.extractYoutubeVideoId("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("動画登録")
    class Register {

        @Test
        @DisplayName("matches に試合があれば登録できる")
        void testRegisterWhenMatchExists() {
            doReturn("動画タイトル").when(matchVideoService).fetchTitle(anyString());
            Match match = buildMatch(1L, 1L, 2L);
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(match));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenAnswer(inv -> {
                        MatchVideo v = inv.getArgument(0);
                        v.setId(100L);
                        return v;
                    });
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of(match));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            MatchVideoDto result = matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L);

            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getYoutubeVideoId()).isEqualTo(VALID_VIDEO_ID);
            assertThat(result.getTitle()).isEqualTo("動画タイトル");
            assertThat(result.getPlayer1Name()).isEqualTo("山田太郎");
            assertThat(result.getPlayer2Name()).isEqualTo("佐藤花子");
            // matches があるので結果情報が付く
            assertThat(result.getMatchId()).isEqualTo(1L);
            assertThat(result.getWinnerId()).isEqualTo(1L);
            assertThat(result.getScoreDifference()).isEqualTo(5);

            ArgumentCaptor<MatchVideo> captor = ArgumentCaptor.forClass(MatchVideo.class);
            verify(matchVideoRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(1L);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(1L);
            assertThat(captor.getValue().getProvider()).isEqualTo("YOUTUBE");
        }

        @Test
        @DisplayName("matches になく match_pairings に試合があれば登録できる")
        void testRegisterWhenOnlyPairingExists() {
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchPairingRepository.findBySessionDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildPairing(1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenAnswer(inv -> { MatchVideo v = inv.getArgument(0); v.setId(101L); return v; });
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            MatchVideoDto result = matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L);

            assertThat(result.getId()).isEqualTo(101L);
            // matches がないので結果情報は null
            assertThat(result.getMatchId()).isNull();
            assertThat(result.getWinnerId()).isNull();
            assertThat(result.getScoreDifference()).isNull();
        }

        @Test
        @DisplayName("match_pairings の選手順序が逆でも一致して登録できる")
        void testRegisterPairingReverseOrder() {
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            // 正規化後は (1,2) で照合され、pairing 側は (2,1) でも順序不問クエリで一致する想定
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchPairingRepository.findBySessionDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildPairing(2L, 1L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenAnswer(inv -> { MatchVideo v = inv.getArgument(0); v.setId(102L); return v; });
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // 入力も逆順 (p1=2, p2=1) で渡し、正規化されることも確認
            MatchVideoDto result = matchVideoService.register(createRequest(2L, 1L, VALID_URL), 1L);

            assertThat(result.getId()).isEqualTo(102L);
            ArgumentCaptor<MatchVideo> captor = ArgumentCaptor.forClass(MatchVideo.class);
            verify(matchVideoRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getPlayer1Id()).isEqualTo(1L);
            assertThat(captor.getValue().getPlayer2Id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("キー正規化: player1Id > player2Id の入力は入れ替えて存在チェックされる")
        void testKeyNormalization() {
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenAnswer(inv -> { MatchVideo v = inv.getArgument(0); v.setId(103L); return v; });
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            // 入力は (p1=2, p2=1)
            matchVideoService.register(createRequest(2L, 1L, VALID_URL), 1L);

            // 正規化後 (1,2) で存在チェック・重複チェックが行われる
            verify(matchRepository).findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L);
            verify(matchVideoRepository).findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L);
        }

        @Test
        @DisplayName("matches にも match_pairings にも試合がなければ404")
        void testRegisterMatchNotFound() {
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchPairingRepository.findBySessionDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("対象の試合が見つかりません");

            verify(matchVideoRepository, never()).save(any());
        }

        @Test
        @DisplayName("不正なURLは試合存在チェック前に400で弾く")
        void testRegisterInvalidUrl() {
            assertThatThrownBy(() -> matchVideoService.register(createRequest(1L, 2L, "https://vimeo.com/1"), 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("YouTubeのURLを入力してください");

            verifyNoInteractions(matchRepository, matchPairingRepository, matchVideoRepository);
        }

        @Test
        @DisplayName("既に動画が登録済みなら409")
        void testRegisterDuplicate() {
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildVideo(99L, 1L)));

            assertThatThrownBy(() -> matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("この試合には既に動画が登録されています");

            verify(matchVideoRepository, never()).save(any());
        }

        @Test
        @DisplayName("重複チェック通過後の同時登録で一意制約違反が起きたら409に変換される")
        void testRegisterConcurrentUniqueViolation() {
            // 事前の存在チェック・重複チェックは通過するが、save時にUNIQUE制約違反が発生する状況
            // （別リクエストが先に同一自然キーを登録したTOCTOU競合）
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_match_videos_match violation"));

            assertThatThrownBy(() -> matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("この試合には既に動画が登録されています");
        }

        @Test
        @DisplayName("同一選手ID（player1Id == player2Id）は400（IllegalArgumentException）で弾く")
        void testRegisterSamePlayer() {
            assertThatThrownBy(() -> matchVideoService.register(createRequest(5L, 5L, VALID_URL), 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("対戦相手が不正です");

            // 存在チェック・保存に到達しない
            verifyNoInteractions(matchRepository, matchPairingRepository, matchVideoRepository);
        }

        @Test
        @DisplayName("ゲスト番兵値0などの非正IDは400（IllegalArgumentException）で弾く（DTO @Positive の防御的二重化）")
        void testRegisterNonPositivePlayerId() {
            // player2Id=0（システム未登録ゲストの番兵値）。正規化で normP1=0 となり防御ガードに掛かる
            assertThatThrownBy(() -> matchVideoService.register(createRequest(0L, 3L, VALID_URL), 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("対戦相手が不正です");

            verifyNoInteractions(matchRepository, matchPairingRepository, matchVideoRepository);
        }

        @Test
        @DisplayName("自然キーUNIQUE制約（Hibernate getConstraintName）由来の整合性違反は409に変換される")
        void testRegisterUniqueViolationByConstraintName() {
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            // メッセージには制約名を含めず、Hibernate の ConstraintViolationException#getConstraintName のみで判定させる
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement",
                    new ConstraintViolationException(
                            "constraint violation",
                            new SQLException("duplicate key value"),
                            "uq_match_videos_match"));
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class))).thenThrow(dive);

            assertThatThrownBy(() -> matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("この試合には既に動画が登録されています");
        }

        @Test
        @DisplayName("一意制約以外（FK違反等）のDataIntegrityViolationExceptionは409に変換されずそのまま再throwされる")
        void testRegisterNonUniqueIntegrityViolationRethrown() {
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            // 制約名・メッセージとも uq_match_videos_match を含まないFK違反相当の整合性違反
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement",
                    new ConstraintViolationException(
                            "foreign key violation",
                            new SQLException("FK constraint failed"),
                            "fk_match_videos_some_other"));
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class))).thenThrow(dive);

            // 重複(409)に化けず、元のDataIntegrityViolationExceptionがそのまま伝播する
            // （GlobalExceptionHandler 側で 500 等として扱われる）
            assertThatThrownBy(() -> matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .isNotInstanceOf(DuplicateResourceException.class)
                    .isSameAs(dive);
        }

        @Test
        @DisplayName("oEmbedタイトル取得に失敗してもtitle=nullで登録成功（fail-soft）")
        void testRegisterFailSoftTitle() {
            // fetchTitle が null を返す（内部で例外を握りつぶした状況を模す）
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenAnswer(inv -> { MatchVideo v = inv.getArgument(0); v.setId(104L); return v; });
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            MatchVideoDto result = matchVideoService.register(createRequest(1L, 2L, VALID_URL), 1L);

            assertThat(result.getId()).isEqualTo(104L);
            assertThat(result.getTitle()).isNull();
            ArgumentCaptor<MatchVideo> captor = ArgumentCaptor.forClass(MatchVideo.class);
            verify(matchVideoRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getTitle()).isNull();
        }
    }

    @Nested
    @DisplayName("登録時のLINE通知トリガ")
    class RegisterNotification {

        /** matches に試合がある状態で register を成功させる共通スタブ（saved.id=110）。 */
        private void stubSuccessfulRegister(Long p1, Long p2) {
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            when(matchRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.of(buildMatch(1L, 1L, 2L)));
            when(matchVideoRepository.findByMatchDateAndMatchNumberAndPlayers(today, 1, 1L, 2L))
                    .thenReturn(Optional.empty());
            when(matchVideoRepository.saveAndFlush(any(MatchVideo.class)))
                    .thenAnswer(inv -> { MatchVideo v = inv.getArgument(0); v.setId(110L); return v; });
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of(buildMatch(1L, 1L, 2L)));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));
        }

        @Test
        @DisplayName("登録成功時、当事者・試合日・試合番号・matchId とともに通知メソッドが呼ばれる")
        void testNotificationTriggeredOnRegister() {
            stubSuccessfulRegister(1L, 2L);

            // 登録者=99（第三者）
            matchVideoService.register(createRequest(1L, 2L, VALID_URL), 99L);

            // matches に結果があるので matchId=1L が渡る（リンク先 /matches/1）
            verify(lineNotificationService).sendMatchVideoRegisteredNotification(
                    eq(99L), eq(1L), eq(2L), eq(today), eq(1), eq(1L));
        }

        @Test
        @DisplayName("登録者=player1 のとき、入力順が逆でも正規化後の当事者IDで通知が呼ばれる")
        void testNotificationUsesNormalizedPlayerIds() {
            stubSuccessfulRegister(1L, 2L);

            // 入力は逆順 (p1=2, p2=1)、登録者=1（player1相当）
            matchVideoService.register(createRequest(2L, 1L, VALID_URL), 1L);

            // 正規化後 player1Id=1, player2Id=2 で通知が呼ばれる（除外判定は通知メソッド側の責務）
            verify(lineNotificationService).sendMatchVideoRegisteredNotification(
                    eq(1L), eq(1L), eq(2L), eq(today), eq(1), eq(1L));
        }

        @Test
        @DisplayName("URL差し替え（updateUrl）では通知しない")
        void testNoNotificationOnUpdate() {
            doReturn("新タイトル").when(matchVideoService).fetchTitle(anyString());
            MatchVideo video = buildVideo(210L, 10L);
            when(matchVideoRepository.findById(210L)).thenReturn(Optional.of(video));
            when(matchVideoRepository.save(any(MatchVideo.class))).thenAnswer(inv -> inv.getArgument(0));
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            MatchVideoUpdateRequest req = new MatchVideoUpdateRequest();
            req.setVideoUrl("https://youtu.be/abcdefghijk");
            matchVideoService.updateUrl(210L, req, 10L, Role.PLAYER);

            verifyNoInteractions(lineNotificationService);
        }

        @Test
        @DisplayName("通知メソッドが例外を投げても register は成功し、結果DTOを返す（通知のtry-catchが効く）")
        void testRegisterSucceedsWhenNotificationThrows() {
            stubSuccessfulRegister(1L, 2L);
            doThrow(new RuntimeException("LINE API down"))
                    .when(lineNotificationService)
                    .sendMatchVideoRegisteredNotification(any(), any(), any(), any(), any(), any());

            MatchVideoDto result = matchVideoService.register(createRequest(1L, 2L, VALID_URL), 99L);

            // 通知が例外を投げても登録自体は成功（保存済み・DTO返却）
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(110L);
            verify(matchVideoRepository).saveAndFlush(any(MatchVideo.class));
        }
    }

    @Nested
    @DisplayName("更新（URL差し替え）の権限")
    class UpdatePermission {

        @Test
        @DisplayName("登録者本人(PLAYER)は更新できる")
        void testUpdateByOwner() {
            doReturn("新タイトル").when(matchVideoService).fetchTitle(anyString());
            MatchVideo video = buildVideo(200L, 10L); // createdBy=10
            when(matchVideoRepository.findById(200L)).thenReturn(Optional.of(video));
            when(matchVideoRepository.save(any(MatchVideo.class))).thenAnswer(inv -> inv.getArgument(0));
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            MatchVideoUpdateRequest req = new MatchVideoUpdateRequest();
            req.setVideoUrl("https://youtu.be/abcdefghijk");

            MatchVideoDto result = matchVideoService.updateUrl(200L, req, 10L, Role.PLAYER);

            assertThat(result.getYoutubeVideoId()).isEqualTo("abcdefghijk");
            assertThat(result.getTitle()).isEqualTo("新タイトル");
            ArgumentCaptor<MatchVideo> captor = ArgumentCaptor.forClass(MatchVideo.class);
            verify(matchVideoRepository).save(captor.capture());
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(10L);
        }

        @Test
        @DisplayName("ADMINは他人の動画でも更新できる")
        void testUpdateByAdmin() {
            doReturn(null).when(matchVideoService).fetchTitle(anyString());
            MatchVideo video = buildVideo(201L, 10L); // createdBy=10
            when(matchVideoRepository.findById(201L)).thenReturn(Optional.of(video));
            when(matchVideoRepository.save(any(MatchVideo.class))).thenAnswer(inv -> inv.getArgument(0));
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            MatchVideoUpdateRequest req = new MatchVideoUpdateRequest();
            req.setVideoUrl(VALID_URL);

            // 操作者=99（別人）だが ADMIN
            MatchVideoDto result = matchVideoService.updateUrl(201L, req, 99L, Role.ADMIN);
            assertThat(result).isNotNull();
            verify(matchVideoRepository).save(any());
        }

        @Test
        @DisplayName("他人のPLAYERは更新できず403")
        void testUpdateByOtherPlayerForbidden() {
            MatchVideo video = buildVideo(202L, 10L); // createdBy=10
            when(matchVideoRepository.findById(202L)).thenReturn(Optional.of(video));

            MatchVideoUpdateRequest req = new MatchVideoUpdateRequest();
            req.setVideoUrl(VALID_URL);

            // 操作者=99（別人）かつ PLAYER
            assertThatThrownBy(() -> matchVideoService.updateUrl(202L, req, 99L, Role.PLAYER))
                    .isInstanceOf(ForbiddenException.class);

            verify(matchVideoRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しない動画の更新は404")
        void testUpdateNotFound() {
            when(matchVideoRepository.findById(999L)).thenReturn(Optional.empty());
            MatchVideoUpdateRequest req = new MatchVideoUpdateRequest();
            req.setVideoUrl(VALID_URL);

            assertThatThrownBy(() -> matchVideoService.updateUrl(999L, req, 1L, Role.PLAYER))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("削除の権限")
    class DeletePermission {

        @Test
        @DisplayName("登録者本人(PLAYER)は削除できる")
        void testDeleteByOwner() {
            MatchVideo video = buildVideo(300L, 10L);
            when(matchVideoRepository.findById(300L)).thenReturn(Optional.of(video));

            matchVideoService.delete(300L, 10L, Role.PLAYER);

            verify(matchVideoRepository).delete(video);
        }

        @Test
        @DisplayName("SUPER_ADMINは他人の動画でも削除できる")
        void testDeleteBySuperAdmin() {
            MatchVideo video = buildVideo(301L, 10L);
            when(matchVideoRepository.findById(301L)).thenReturn(Optional.of(video));

            matchVideoService.delete(301L, 99L, Role.SUPER_ADMIN);

            verify(matchVideoRepository).delete(video);
        }

        @Test
        @DisplayName("他人のPLAYERは削除できず403")
        void testDeleteByOtherPlayerForbidden() {
            MatchVideo video = buildVideo(302L, 10L);
            when(matchVideoRepository.findById(302L)).thenReturn(Optional.of(video));

            assertThatThrownBy(() -> matchVideoService.delete(302L, 99L, Role.PLAYER))
                    .isInstanceOf(ForbiddenException.class);

            verify(matchVideoRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("日付別一覧")
    class FindByDate {

        @Test
        @DisplayName("指定日の動画一覧を取得し選手名と結果を解決する")
        void testFindByDate() {
            MatchVideo video = buildVideo(400L, 1L);
            when(matchVideoRepository.findByMatchDate(today)).thenReturn(List.of(video));
            when(matchRepository.findByMatchDateIn(List.of(today)))
                    .thenReturn(List.of(buildMatch(1L, 1L, 2L)));
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            List<MatchVideoDto> result = matchVideoService.findByDate(today);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlayer1Name()).isEqualTo("山田太郎");
            assertThat(result.get(0).getMatchId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("動画が無い日は空リストを返す")
        void testFindByDateEmpty() {
            when(matchVideoRepository.findByMatchDate(today)).thenReturn(List.of());
            List<MatchVideoDto> result = matchVideoService.findByDate(today);
            assertThat(result).isEmpty();
            verifyNoInteractions(playerRepository);
        }
    }

    @Nested
    @DisplayName("倉庫検索（年月→範囲変換・mine・ページング）")
    class Search {

        @Test
        @DisplayName("年月指定はその月の開始日〜末日に変換して検索する")
        void testSearchYearMonthRange() {
            MatchVideo video = buildVideo(500L, 1L);
            Page<MatchVideo> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
            when(matchVideoRepository.search(eq(1L), any(), any(), any())).thenReturn(page);
            when(matchRepository.findByMatchDateIn(any())).thenReturn(List.of());
            when(playerRepository.findAllById(any())).thenReturn(List.of(player1, player2));

            PagedResponse<MatchVideoDto> result =
                    matchVideoService.search(1L, 2026, 6, false, 0, 20, 99L);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(20);

            ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(matchVideoRepository).search(eq(1L), startCaptor.capture(), endCaptor.capture(), any());
            assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 6, 30));
        }

        @Test
        @DisplayName("年のみ指定はその年全体に変換して検索する")
        void testSearchYearOnly() {
            Page<MatchVideo> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(matchVideoRepository.search(any(), any(), any(), any())).thenReturn(page);

            matchVideoService.search(null, 2026, null, false, 0, 20, 99L);

            ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(matchVideoRepository).search(any(), startCaptor.capture(), endCaptor.capture(), any());
            assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("年月未指定なら日付範囲はnullで検索する")
        void testSearchNoDateRange() {
            Page<MatchVideo> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(matchVideoRepository.search(any(), any(), any(), any())).thenReturn(page);

            matchVideoService.search(5L, null, null, false, 0, 20, 99L);

            verify(matchVideoRepository).search(eq(5L), eq(null), eq(null), any());
        }

        @Test
        @DisplayName("mine=true は操作ユーザーを対象選手として扱い playerId より優先する")
        void testSearchMinePriority() {
            Page<MatchVideo> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(matchVideoRepository.search(any(), any(), any(), any())).thenReturn(page);

            // playerId=5 を渡しても mine=true なので currentUserId=99 が使われる
            matchVideoService.search(5L, null, null, true, 0, 20, 99L);

            verify(matchVideoRepository).search(eq(99L), any(), any(), any());
        }

        @Test
        @DisplayName("size上限100でクランプされ、負のpageは0に補正される")
        void testSearchPagingBounds() {
            Page<MatchVideo> page = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
            when(matchVideoRepository.search(any(), any(), any(), any())).thenReturn(page);

            matchVideoService.search(null, null, null, false, -1, 9999, 99L);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(matchVideoRepository).search(any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("page/sizeがnullならデフォルト(0/20)で検索する")
        void testSearchDefaultPaging() {
            Page<MatchVideo> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(matchVideoRepository.search(any(), any(), any(), any())).thenReturn(page);

            matchVideoService.search(null, null, null, false, null, null, 99L);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(matchVideoRepository).search(any(), any(), any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("month=13 は400（IllegalArgumentException）で弾き検索しない")
        void testSearchInvalidMonthTooLarge() {
            assertThatThrownBy(() -> matchVideoService.search(1L, 2026, 13, false, 0, 20, 99L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("月は1〜12で指定してください");

            verifyNoInteractions(matchVideoRepository);
        }

        @Test
        @DisplayName("month=0 は400（IllegalArgumentException）で弾き検索しない")
        void testSearchInvalidMonthZero() {
            assertThatThrownBy(() -> matchVideoService.search(1L, 2026, 0, false, 0, 20, 99L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("月は1〜12で指定してください");

            verifyNoInteractions(matchVideoRepository);
        }

        @Test
        @DisplayName("極端に小さいyear(0)は400（IllegalArgumentException）で弾き検索しない")
        void testSearchInvalidYearTooSmall() {
            assertThatThrownBy(() -> matchVideoService.search(1L, 0, 6, false, 0, 20, 99L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("年は2000〜2100で指定してください");

            verifyNoInteractions(matchVideoRepository);
        }

        @Test
        @DisplayName("極端に大きいyear(99999)は400（IllegalArgumentException）で弾き検索しない")
        void testSearchInvalidYearTooLarge() {
            assertThatThrownBy(() -> matchVideoService.search(1L, 99999, null, false, 0, 20, 99L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("年は2000〜2100で指定してください");

            verifyNoInteractions(matchVideoRepository);
        }

        @Test
        @DisplayName("正常系(year=2026, month=6)は引き続きその月の範囲で検索できる")
        void testSearchValidYearMonthStillWorks() {
            Page<MatchVideo> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(matchVideoRepository.search(any(), any(), any(), any())).thenReturn(page);

            matchVideoService.search(1L, 2026, 6, false, 0, 20, 99L);

            ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(matchVideoRepository).search(eq(1L), startCaptor.capture(), endCaptor.capture(), any());
            assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 6, 30));
        }
    }

    // ===================== fixture builders =====================

    private Match buildMatch(Long id, Long p1, Long p2) {
        return Match.builder()
                .id(id)
                .matchDate(today)
                .matchNumber(1)
                .player1Id(p1)
                .player2Id(p2)
                .winnerId(1L)
                .scoreDifference(5)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
    }

    private MatchPairing buildPairing(Long p1, Long p2) {
        return MatchPairing.builder()
                .id(1L)
                .sessionDate(today)
                .matchNumber(1)
                .player1Id(p1)
                .player2Id(p2)
                .createdBy(1L)
                .build();
    }

    private MatchVideo buildVideo(Long id, Long createdBy) {
        return MatchVideo.builder()
                .id(id)
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .provider("YOUTUBE")
                .videoUrl(VALID_URL)
                .youtubeVideoId(VALID_VIDEO_ID)
                .title("既存タイトル")
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
    }
}
