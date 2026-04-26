package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.karuta.matchtracker.service.proxy.VenueId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KaderuCompletionStrategy 単体テスト")
class KaderuCompletionStrategyTest {

    private KaderuCompletionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new KaderuCompletionStrategy();
    }

    @Test
    @DisplayName("venue() は KADERU を返す")
    void venueReturnsKaderu() {
        assertThat(strategy.venue()).isEqualTo(VenueId.KADERU);
    }

    @Nested
    @DisplayName("URL 条件")
    class UrlMatching {

        @Test
        @DisplayName("requestUrl に ?p=rsv_comp を含むと true")
        void requestUrlRsvComp() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=rsv_comp";
            assertThat(strategy.isCompletion(url, null, null)).isTrue();
        }

        @Test
        @DisplayName("requestUrl に ?p=fix_comp を含むと true")
        void requestUrlFixComp() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=fix_comp";
            assertThat(strategy.isCompletion(url, null, null)).isTrue();
        }

        @Test
        @DisplayName("requestUrl のパスに /complete を含むと true")
        void requestUrlCompletePath() {
            String url = "https://k2.p-kashikan.jp/kaderu27/complete";
            assertThat(strategy.isCompletion(url, null, null)).isTrue();
        }

        @Test
        @DisplayName("responseLocation に完了 URL を含むと true (リダイレクト経由)")
        void responseLocationMatches() {
            String location = "/kaderu27/index.php?p=rsv_comp&id=12345";
            assertThat(strategy.isCompletion("https://k2.p-kashikan.jp/kaderu27/index.php", location, null))
                    .isTrue();
        }

        @Test
        @DisplayName("申込フォーム画面 URL (?p=apply) は false")
        void applyFormIsNotCompletion() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=apply";
            String body = "<html><body>予約申込</body></html>";
            assertThat(strategy.isCompletion(url, null, body)).isFalse();
        }

        @Test
        @DisplayName("申込トレイ URL (?p=rsv_search) は false")
        void requestTrayIsNotCompletion() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=rsv_search";
            assertThat(strategy.isCompletion(url, null, null)).isFalse();
        }

        @Test
        @DisplayName("マイページ URL (?p=my_page) は false")
        void myPageIsNotCompletion() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=my_page";
            assertThat(strategy.isCompletion(url, null, null)).isFalse();
        }

        @Test
        @DisplayName("?p=rsv_completion (rsv_comp の prefix 拡張) は word-boundary により陰性")
        void rsvCompletionPrefixExtensionIsNotCompletion() {
            // word-boundary の (\?|&)p=rsv_comp(&|$) により、p=rsv_completion はマッチしない。
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=rsv_completion";
            assertThat(strategy.isCompletion(url, null, null)).isFalse();
        }

        @Test
        @DisplayName("/incomplete-page のような substring は word-boundary により陰性")
        void incompletePathIsNotCompletion() {
            // (?<![A-Za-z0-9])complete(?![A-Za-z0-9]) により、incomplete はマッチしない。
            String url = "https://k2.p-kashikan.jp/kaderu27/incomplete-page";
            assertThat(strategy.isCompletion(url, null, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("HTML 本文条件")
    class BodyMatching {

        @Test
        @DisplayName("body に「申込みを受け付けました」を含むと true")
        void bodyAcceptedMessage() {
            String body = "<html><body>申込みを受け付けました。</body></html>";
            assertThat(strategy.isCompletion(null, null, body)).isTrue();
        }

        @Test
        @DisplayName("body に「申込番号: 12345」を含むと true (番号値が直後に続くパターン)")
        void bodyApplicationNumber() {
            String body = "<html><body>申込番号: 12345</body></html>";
            assertThat(strategy.isCompletion(null, null, body)).isTrue();
        }

        @Test
        @DisplayName("body に「申込番号」ラベル単独 (番号値が後続しない) は陰性")
        void bodyApplicationNumberLabelOnlyIsNotCompletion() {
            // 申込トレイ画面に「申込番号」ラベルだけが含まれていても、番号値が直後に続いていなければ陰性。
            String body = "<html><body><label>申込番号</label><input name=\"number\"></body></html>";
            assertThat(strategy.isCompletion(null, null, body)).isFalse();
        }

        @Test
        @DisplayName("body に「予約を受付ました」を含むと true")
        void bodyReservationAccepted() {
            String body = "<html><body>予約を受付ました</body></html>";
            assertThat(strategy.isCompletion(null, null, body)).isTrue();
        }

        @Test
        @DisplayName("body に「予約完了」を含むと true")
        void bodyReservationCompleted() {
            String body = "<html><body>予約完了画面です</body></html>";
            assertThat(strategy.isCompletion(null, null, body)).isTrue();
        }

        @Test
        @DisplayName("一般的な body (申込フォーム / マイページ等) は false")
        void generalBodyIsNotCompletion() {
            String applyForm = "<html><body><h1>予約申込</h1><form>...</form></body></html>";
            String myPage = "<html><body><h1>マイページ</h1></body></html>";
            String availability = "<html><body><table><tr><td>はまなす</td><td>○</td></tr></table></body></html>";
            assertThat(strategy.isCompletion(null, null, applyForm)).isFalse();
            assertThat(strategy.isCompletion(null, null, myPage)).isFalse();
            assertThat(strategy.isCompletion(null, null, availability)).isFalse();
        }

        @Test
        @DisplayName("単に「完了」だけは陽性ではない (一般語のため除外している)")
        void mereCompleteWordIsNotCompletion() {
            String body = "<html><body>処理が完了しました</body></html>";
            assertThat(strategy.isCompletion(null, null, body)).isFalse();
        }
    }

    @Nested
    @DisplayName("URL + HTML の組み合わせ / どちらか1つでも陽性で true")
    class CombinedMatching {

        @Test
        @DisplayName("URL は陰性だが body が陽性なら true")
        void onlyBodyMatches() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=apply";
            String body = "<html><body>申込みを受け付けました</body></html>";
            assertThat(strategy.isCompletion(url, null, body)).isTrue();
        }

        @Test
        @DisplayName("body は陰性だが URL が陽性なら true")
        void onlyUrlMatches() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=rsv_comp";
            String body = "<html><body>予約申込</body></html>";
            assertThat(strategy.isCompletion(url, null, body)).isTrue();
        }

        @Test
        @DisplayName("URL も body も陰性なら false")
        void neitherMatches() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=my_page";
            String body = "<html><body>マイページ</body></html>";
            assertThat(strategy.isCompletion(url, null, body)).isFalse();
        }
    }

    @Nested
    @DisplayName("null / 空文字 安全性")
    class NullSafety {

        @Test
        @DisplayName("全引数が null でも例外を出さず false を返す")
        void allNull() {
            assertThat(strategy.isCompletion(null, null, null)).isFalse();
        }

        @Test
        @DisplayName("全引数が空文字でも例外を出さず false を返す")
        void allEmpty() {
            assertThat(strategy.isCompletion("", "", "")).isFalse();
        }

        @Test
        @DisplayName("一部だけ null でも残りで判定できる")
        void partialNull() {
            String url = "https://k2.p-kashikan.jp/kaderu27/index.php?p=rsv_comp";
            assertThat(strategy.isCompletion(url, null, null)).isTrue();
        }
    }
}
