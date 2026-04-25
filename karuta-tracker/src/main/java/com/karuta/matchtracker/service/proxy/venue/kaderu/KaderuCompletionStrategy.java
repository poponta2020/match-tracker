package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.venue.VenueCompletionStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * かでる2・7 用の {@link VenueCompletionStrategy} 実装。
 *
 * <p>申込完了画面の判定は以下のいずれか1つでも陽性なら {@code true} を返す:</p>
 * <ul>
 *   <li>リクエスト URL もしくは {@code Location} ヘッダに完了画面固有のクエリ/パスを含む</li>
 *   <li>レスポンス HTML 本文に完了画面固有の文言を含む</li>
 * </ul>
 *
 * <p>誤陽性を避けるため、申込フォーム画面 (例: {@code ?p=apply}) と区別できるパターンのみを採用する。
 * 文言判定では「完了」単体のような一般語は使わない。</p>
 *
 * <p><strong>本パターンは暫定値。</strong> Phase 1 の実機検証 (E2E ステップで実申込を1回完走させる) で
 * 確定値に絞り込み / 拡張すること。venues/kaderu.md §5 を参照。</p>
 */
@Component
public class KaderuCompletionStrategy implements VenueCompletionStrategy {

    /**
     * URL / Location ヘッダで完了画面と判定する部分文字列パターン。
     * いずれか1つでも含まれていれば陽性。
     *
     * <p>暫定値: 実機検証で確定すること。</p>
     */
    private static final List<String> URL_COMPLETION_TOKENS = List.of(
            "p=rsv_comp",
            "p=fix_comp",
            "/complete"
    );

    /**
     * HTML 本文で完了画面と判定する部分文字列パターン。
     * いずれか1つでも含まれていれば陽性。
     *
     * <p>暫定値: 実機検証で確定すること。一般語 (単に「完了」等) は誤陽性を招くため使用しない。</p>
     */
    private static final List<String> BODY_COMPLETION_TOKENS = List.of(
            "申込みを受け付けました",
            "申込番号",
            "予約を受付ました",
            "予約完了"
    );

    @Override
    public VenueId venue() {
        return VenueId.KADERU;
    }

    @Override
    public boolean isCompletion(String requestUrl, String responseLocation, String responseBody) {
        if (matchesUrlToken(requestUrl) || matchesUrlToken(responseLocation)) {
            return true;
        }
        return matchesBodyToken(responseBody);
    }

    private static boolean matchesUrlToken(String url) {
        if (url == null || url.isEmpty()) return false;
        for (String token : URL_COMPLETION_TOKENS) {
            if (url.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBodyToken(String body) {
        if (body == null || body.isEmpty()) return false;
        for (String token : BODY_COMPLETION_TOKENS) {
            if (body.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
