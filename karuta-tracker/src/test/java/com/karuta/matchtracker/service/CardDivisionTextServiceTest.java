package com.karuta.matchtracker.service;

import com.karuta.matchtracker.service.CardDivisionTextService.CardRule;
import com.karuta.matchtracker.util.Kimariji;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 札分けテキスト生成サービスの単体テスト。
 *
 * <ul>
 *   <li>AC-1: サーバー側の札組導出が既存 {@code cardRules.js}（{@code getCardRules}）の出力と
 *       ゴールデン一致する。フィクスチャは cardRules.js を <b>実際に走らせて</b>採取したもの
 *       （3サイクル位置すべて・サイクル境界をまたぐ totalMatches 3/4/6/7・nonce≠0 を網羅）。</li>
 *   <li>AC-2: 抜き行に {@code 番号(決まり字)抜き}（41→41(こひ)、100→100(もも)）。一の位・十の位に決まり字なし。</li>
 *   <li>AC-3: ヘッダ {@code 【M/D 会場名】} の月・日で10の位0を省略（7/5・10/9・12/25）。</li>
 * </ul>
 *
 * PRNG 生成（generateCardRules）とテキスト整形（buildText）はリポジトリ依存を持たないため、
 * サービスは {@code null} 依存で構築する。
 */
@DisplayName("CardDivisionTextService 札分けテキスト生成")
class CardDivisionTextServiceTest {

    private final CardDivisionTextService service = new CardDivisionTextService(null, null);

    // ------------------------------------------------------------------
    // AC-1: ゴールデン・クロス言語パリティ（cardRules.js を実行して採取）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-1: cardRules.js とゴールデン一致（3サイクル位置・totalMatches 3/4/6/7・nonce≠0）")
    void goldenParityWithCardRulesJs() {
        // date=2026-07-05, nonce=0 は totalMatches を伸ばしても先頭試合が安定することも同時に検証する
        assertRules("2026-07-05", 0, 3, List.of(
                ones(2, 3, 4, 8, 9), nuki("79", 0, 6, 7), tens(3, 4, 5, 8, 9)));

        assertRules("2026-07-05", 0, 4, List.of(
                ones(2, 3, 4, 8, 9), nuki("79", 0, 6, 7), tens(3, 4, 5, 8, 9),
                ones(0, 2, 4, 8, 9)));

        assertRules("2026-07-05", 0, 6, List.of(
                ones(2, 3, 4, 8, 9), nuki("79", 0, 6, 7), tens(3, 4, 5, 8, 9),
                ones(0, 2, 4, 8, 9), nuki("77", 3, 6, 7), tens(1, 2, 4, 5, 9)));

        assertRules("2026-07-05", 0, 7, List.of(
                ones(2, 3, 4, 8, 9), nuki("79", 0, 6, 7), tens(3, 4, 5, 8, 9),
                ones(0, 2, 4, 8, 9), nuki("77", 3, 6, 7), tens(1, 2, 4, 5, 9),
                ones(1, 3, 4, 7, 9)));

        assertRules("2026-10-09", 2, 3, List.of(
                ones(0, 2, 3, 5, 9), nuki("16", 1, 7, 8), tens(0, 2, 4, 5, 6)));

        assertRules("2026-12-25", 1, 6, List.of(
                ones(2, 3, 5, 7, 9), nuki("45", 0, 1, 4), tens(2, 5, 6, 7, 9),
                ones(0, 5, 6, 8, 9), nuki("14", 1, 3, 4), tens(0, 5, 6, 8, 9)));

        assertRules("2026-07-05", 5, 3, List.of(
                ones(0, 1, 3, 7, 8), nuki("02", 2, 4, 5), tens(0, 1, 3, 6, 9)));
    }

    // ------------------------------------------------------------------
    // AC-2 / AC-3: テキスト整形（決まり字付与・ヘッダ0省略）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-2/AC-3: 抜き行の決まり字（100→もも 含む）・一の位/十の位は決まり字なし・ヘッダ 7/5")
    void reminderTextFormat() {
        // 2026-07-05 nonce=20 tm=6: 5試合目の抜きが removedCard="00" → parseInt||100 → 100(もも)
        String text = service.buildText(LocalDate.of(2026, 7, 5), "かでる2・7", 6, 20);

        assertThat(text).isEqualTo(String.join("\n",
                "【7/5 かでる2・7】",
                "1試合目：一の位1.3.4.5.7",
                "2試合目：0.2.9　91(きり)抜き",
                "3試合目：十の位1.3.5.6.7",
                "4試合目：一の位1.2.5.7.8",
                "5試合目：0.3.6　100(もも)抜き",
                "6試合目：十の位1.2.4.7.8"));
    }

    @Test
    @DisplayName("AC-2: 決まり字マスタの補正値（41=こひ, 1=あきの, 100=もも, 68=こころに, 82=おも）")
    void kimarijiMasterValues() {
        assertThat(Kimariji.of(41)).isEqualTo("こひ");
        assertThat(Kimariji.of(1)).isEqualTo("あきの");
        assertThat(Kimariji.of(100)).isEqualTo("もも");
        assertThat(Kimariji.of(68)).isEqualTo("こころに");
        assertThat(Kimariji.of(82)).isEqualTo("おも");
    }

    @Test
    @DisplayName("AC-3: ヘッダの月・日で10の位0を省略（7/5・10/9・12/25）")
    void headerTensDigitZeroSuppression() {
        assertThat(service.buildText(LocalDate.of(2026, 7, 5), "会場", 1, 0)).startsWith("【7/5 会場】");
        assertThat(service.buildText(LocalDate.of(2026, 10, 9), "会場", 1, 0)).startsWith("【10/9 会場】");
        assertThat(service.buildText(LocalDate.of(2026, 12, 25), "会場", 1, 0)).startsWith("【12/25 会場】");
    }

    @Test
    @DisplayName("会場名が空ならヘッダは 【M/D】（会場なし）")
    void headerWithoutVenue() {
        assertThat(service.buildText(LocalDate.of(2026, 7, 5), null, 1, 0)).startsWith("【7/5】");
        assertThat(service.buildText(LocalDate.of(2026, 7, 5), "  ", 1, 0)).startsWith("【7/5】");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void assertRules(String isoDate, int nonce, int totalMatches, List<CardRule> expected) {
        List<CardRule> actual = service.generateCardRules(isoDate, nonce, totalMatches);
        assertThat(actual)
                .as("generateCardRules(%s, nonce=%d, tm=%d)", isoDate, nonce, totalMatches)
                .isEqualTo(expected);
    }

    private static CardRule ones(Integer... digits) {
        return new CardRule("ones", List.of(digits), null);
    }

    private static CardRule tens(Integer... digits) {
        return new CardRule("tens", List.of(digits), null);
    }

    private static CardRule nuki(String removedCard, Integer... digits) {
        return new CardRule("nuki", List.of(digits), removedCard);
    }
}
