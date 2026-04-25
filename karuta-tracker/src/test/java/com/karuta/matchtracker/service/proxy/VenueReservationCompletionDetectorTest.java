package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.proxy.venue.VenueCompletionStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VenueReservationCompletionDetector 単体テスト")
class VenueReservationCompletionDetectorTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    /** 完了画面に到達したと判定する KADERU 用ダミー strategy */
    private static class AlwaysCompleteKaderu implements VenueCompletionStrategy {
        @Override public VenueId venue() { return VenueId.KADERU; }
        @Override public boolean isCompletion(String url, String location, String body) { return true; }
    }

    /** 完了画面に到達していないと判定する KADERU 用ダミー strategy */
    private static class NeverCompleteKaderu implements VenueCompletionStrategy {
        @Override public VenueId venue() { return VenueId.KADERU; }
        @Override public boolean isCompletion(String url, String location, String body) { return false; }
    }

    /** 渡された判定結果をそのまま返す可変 strategy (引数記録用) */
    private static class RecordingStrategy implements VenueCompletionStrategy {
        boolean result;
        String lastUrl;
        String lastLocation;
        String lastBody;
        @Override public VenueId venue() { return VenueId.KADERU; }
        @Override public boolean isCompletion(String url, String location, String body) {
            this.lastUrl = url;
            this.lastLocation = location;
            this.lastBody = body;
            return result;
        }
    }

    private ProxySession kaderuSession(Long practiceSessionId) {
        return ProxySession.builder()
                .token(UUID.randomUUID().toString())
                .venue(VenueId.KADERU)
                .practiceSessionId(practiceSessionId)
                .roomName("はまなす")
                .date(LocalDate.of(2026, 4, 12))
                .slotIndex(2)
                .completed(false)
                .build();
    }

    private PracticeSession entity(Long id, LocalDateTime confirmedAt) {
        PracticeSession ps = new PracticeSession();
        ps.setId(id);
        ps.setReservationConfirmedAt(confirmedAt);
        return ps;
    }

    @Nested
    @DisplayName("陽性検知 (KADERU)")
    class PositiveDetection {

        @Test
        @DisplayName("完了検知時: ProxySession.completed=true + reservation_confirmed_at が更新される")
        void detected_marksSessionCompleted_andSavesEntity() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession session = kaderuSession(123L);

            PracticeSession ps = entity(123L, null);
            when(practiceSessionRepository.findById(123L)).thenReturn(Optional.of(ps));

            boolean result = detector.detectAndMarkComplete(
                    session,
                    "https://k2.p-kashikan.jp/?p=rsv_comp",
                    null,
                    "<html>申込みを受け付けました</html>");

            assertThat(result).isTrue();
            assertThat(session.isCompleted()).isTrue();
            ArgumentCaptor<PracticeSession> captor = ArgumentCaptor.forClass(PracticeSession.class);
            verify(practiceSessionRepository).save(captor.capture());
            PracticeSession saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(123L);
            assertThat(saved.getReservationConfirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("strategy には requestUrl / responseLocation / responseBody がそのまま渡る")
        void strategy_receivesArgumentsAsIs() {
            RecordingStrategy strategy = new RecordingStrategy();
            strategy.result = true;
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(strategy), practiceSessionRepository);
            ProxySession session = kaderuSession(7L);
            when(practiceSessionRepository.findById(7L)).thenReturn(Optional.of(entity(7L, null)));

            detector.detectAndMarkComplete(session, "URL", "LOC", "BODY");

            assertThat(strategy.lastUrl).isEqualTo("URL");
            assertThat(strategy.lastLocation).isEqualTo("LOC");
            assertThat(strategy.lastBody).isEqualTo("BODY");
        }
    }

    @Nested
    @DisplayName("陰性検知 (KADERU)")
    class NegativeDetection {

        @Test
        @DisplayName("非完了時: completed=false 維持、save も呼ばれない")
        void notDetected_doesNotMarkOrSave() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new NeverCompleteKaderu()), practiceSessionRepository);
            ProxySession session = kaderuSession(123L);

            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isFalse();
            assertThat(session.isCompleted()).isFalse();
            verify(practiceSessionRepository, never()).findById(any());
            verify(practiceSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("strategy 未登録 (例: HIGASHI Phase 1)")
    class NoStrategyRegistered {

        @Test
        @DisplayName("strategy 未登録 venue: false 返却 + DB アクセスなし")
        void higashiUnregistered_returnsFalse() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession higashiSession = ProxySession.builder()
                    .token("higashi-token")
                    .venue(VenueId.HIGASHI)
                    .practiceSessionId(456L)
                    .build();

            boolean result = detector.detectAndMarkComplete(
                    higashiSession, "url", null, "body");

            assertThat(result).isFalse();
            assertThat(higashiSession.isCompleted()).isFalse();
            verify(practiceSessionRepository, never()).findById(any());
            verify(practiceSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("冪等性")
    class Idempotency {

        @Test
        @DisplayName("既に reservation_confirmed_at セット済み: completed=true は付くが save は呼ばれない")
        void alreadyConfirmed_skipsOverwrite() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession session = kaderuSession(123L);
            LocalDateTime previous = LocalDateTime.of(2026, 4, 12, 10, 30);
            PracticeSession existing = entity(123L, previous);
            when(practiceSessionRepository.findById(123L)).thenReturn(Optional.of(existing));

            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isTrue();
            assertThat(session.isCompleted()).isTrue();
            // 上書きしない: confirmedAt は最初の値のまま
            assertThat(existing.getReservationConfirmedAt()).isEqualTo(previous);
            verify(practiceSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("DB エラー処理")
    class DbErrorHandling {

        @Test
        @DisplayName("findById が empty: ログ警告 + true 返却 + completed=true (DB スキップ)")
        void findByIdEmpty_returnsTrueButSkipsDb() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession session = kaderuSession(999L);
            when(practiceSessionRepository.findById(999L)).thenReturn(Optional.empty());

            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isTrue();
            assertThat(session.isCompleted()).isTrue();
            verify(practiceSessionRepository, times(1)).findById(999L);
            verify(practiceSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("save が例外: ログエラー + true 返却 + completed=true")
        void saveThrows_returnsTrue() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession session = kaderuSession(123L);
            PracticeSession ps = entity(123L, null);
            when(practiceSessionRepository.findById(123L)).thenReturn(Optional.of(ps));
            when(practiceSessionRepository.save(any())).thenThrow(new RuntimeException("simulated DB failure"));

            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isTrue();
            assertThat(session.isCompleted()).isTrue();
            verify(practiceSessionRepository).save(any());
        }

        @Test
        @DisplayName("session.practiceSessionId が null: completed=true + DB アクセスなし")
        void noPracticeSessionId_skipsDb() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession session = ProxySession.builder()
                    .token(UUID.randomUUID().toString())
                    .venue(VenueId.KADERU)
                    .practiceSessionId(null)
                    .build();

            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isTrue();
            assertThat(session.isCompleted()).isTrue();
            verify(practiceSessionRepository, never()).findById(any());
            verify(practiceSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("strategy.isCompletion が例外: false 返却 + DB アクセスなし")
        void strategyThrows_returnsFalse() {
            VenueCompletionStrategy throwing = new VenueCompletionStrategy() {
                @Override public VenueId venue() { return VenueId.KADERU; }
                @Override public boolean isCompletion(String url, String location, String body) {
                    throw new IllegalStateException("strategy boom");
                }
            };
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(throwing), practiceSessionRepository);
            ProxySession session = kaderuSession(123L);

            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isFalse();
            assertThat(session.isCompleted()).isFalse();
            verify(practiceSessionRepository, never()).findById(any());
            verify(practiceSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("複数 venue 登録時の dispatch")
    class MultiVenueDispatch {

        @Test
        @DisplayName("同じ venue で 2つ登録された場合は最後勝ちで warn ログ (動作には支障なし)")
        void duplicateVenue_lastOneWins() {
            // 検証: 例外なくコンストラクタが通り、登録された片方の strategy が使われる
            VenueCompletionStrategy first = new NeverCompleteKaderu();
            VenueCompletionStrategy second = new AlwaysCompleteKaderu();
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(first, second), practiceSessionRepository);

            ProxySession session = kaderuSession(123L);
            when(practiceSessionRepository.findById(123L)).thenReturn(Optional.of(entity(123L, null)));

            // 後勝ち = AlwaysCompleteKaderu が採用されるはず
            boolean result = detector.detectAndMarkComplete(session, "url", null, "body");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("KADERU と HIGASHI 両方が登録されていれば session.venue で正しく dispatch する")
        void kaderuAndHigashi_dispatchByVenue() {
            VenueCompletionStrategy kaderu = new VenueCompletionStrategy() {
                @Override public VenueId venue() { return VenueId.KADERU; }
                @Override public boolean isCompletion(String u, String l, String b) { return true; }
            };
            VenueCompletionStrategy higashi = new VenueCompletionStrategy() {
                @Override public VenueId venue() { return VenueId.HIGASHI; }
                @Override public boolean isCompletion(String u, String l, String b) { return false; }
            };
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(kaderu, higashi), practiceSessionRepository);
            when(practiceSessionRepository.findById(any())).thenReturn(Optional.of(entity(1L, null)));

            ProxySession kaderuSession = kaderuSession(1L);
            ProxySession higashiSession = ProxySession.builder()
                    .token(UUID.randomUUID().toString())
                    .venue(VenueId.HIGASHI)
                    .practiceSessionId(2L)
                    .build();

            assertThat(detector.detectAndMarkComplete(kaderuSession, "u", null, "b")).isTrue();
            assertThat(detector.detectAndMarkComplete(higashiSession, "u", null, "b")).isFalse();
        }
    }

    @Nested
    @DisplayName("ガード処理")
    class Guards {

        @Test
        @DisplayName("session が null: false を返す (例外を出さない)")
        void nullSession() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            assertThat(detector.detectAndMarkComplete(null, "u", null, "b")).isFalse();
        }

        @Test
        @DisplayName("session.venue が null: false 返却 + warn ログ (DB アクセスなし)")
        void nullVenue() {
            VenueReservationCompletionDetector detector = new VenueReservationCompletionDetector(
                    List.of(new AlwaysCompleteKaderu()), practiceSessionRepository);
            ProxySession session = ProxySession.builder()
                    .token("tok")
                    .venue(null)
                    .practiceSessionId(1L)
                    .build();

            boolean result = detector.detectAndMarkComplete(session, "u", null, "b");

            assertThat(result).isFalse();
            assertThat(session.isCompleted()).isFalse();
            verify(practiceSessionRepository, never()).findById(any());
        }
    }
}
