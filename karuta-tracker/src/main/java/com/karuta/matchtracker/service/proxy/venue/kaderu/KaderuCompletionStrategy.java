package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.venue.VenueCompletionStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * かでる2・7 用の {@link VenueCompletionStrategy} 実装。
 *
 * <p>申込完了画面の判定は以下のいずれか1つでも陽性なら {@code true} を返す:</p>
 * <ul>
 *   <li>リクエスト URL もしくは {@code Location} ヘッダに完了画面固有のクエリ/パスを含む</li>
 *   <li>レスポンス HTML 本文に完了画面固有の文言を含む</li>
 * </ul>
 *
 * <p>誤陽性を避けるため、申込フォーム画面 (例: {@code ?op=apply}) と区別できるパターンのみを採用する。
 * 文言判定では「完了」単体のような一般語は使わない。
 * URL/本文ともに word-boundary を考慮した正規表現で照合し、
 * {@code op=rsv_completion} のような関連性のないパターンや、本文中の単独の「申込番号」ラベルだけでは
 * 陽性にしない。</p>
 *
 * <p><strong>本パターンは暫定値。</strong> Phase 1 の実機検証 (E2E ステップで実申込を1回完走させる) で
 * 確定値に絞り込み / 拡張すること。venues/kaderu.md §5 を参照。</p>
 */
@Component
public class KaderuCompletionStrategy implements VenueCompletionStrategy {

    /**
     * URL / Location ヘッダで完了画面と判定する正規表現パターン。
     * いずれか1つでもマッチすれば陽性。
     *
     * <p>クエリは {@code (\?|&)(op|p)=...(&|$)} で word-boundary を強制し、
     * {@code op=rsv_completion} のようなプレフィックス一致を排除する。
     * パスは前後を非英数字で区切る形で {@code complete} を照合し、
     * {@code /incomplete-page} のような substring 一致を排除する。</p>
     *
     * <p>暫定値: 実機検証で確定すること。</p>
     */
    private static final List<Pattern> URL_COMPLETION_PATTERNS = List.of(
            Pattern.compile("(\\?|&)(?:op|p)=rsv_comp(&|$)"),
            Pattern.compile("(\\?|&)(?:op|p)=fix_comp(&|$)"),
            Pattern.compile("(?<![A-Za-z0-9])complete(?![A-Za-z0-9])")
    );

    /**
     * HTML 本文で完了画面と判定する正規表現パターン。
     * いずれか1つでもマッチすれば陽性。
     *
     * <p>「申込番号」は単独ラベルとしてフォーム/トレイ画面にも現れる可能性があるため、
     * {@code 申込番号[:：\s]*[0-9０-９]+} のように「番号値が直後に続く」前提を加えて誤陽性を避ける。
     * 一般語 (単に「完了」等) は使用しない。</p>
     *
     * <p>暫定値: 実機検証で確定すること。</p>
     */
    private static final List<Pattern> BODY_COMPLETION_PATTERNS = List.of(
            Pattern.compile("申込みを受け付けました"),
            Pattern.compile("申込番号[:：\\s]*[0-9０-９]+"),
            Pattern.compile("予約を受付ました"),
            Pattern.compile("予約完了")
    );

    @Override
    public VenueId venue() {
        return VenueId.KADERU;
    }

    @Override
    public boolean isCompletion(String requestUrl, String responseLocation, String responseBody) {
        if (matchesUrlPattern(requestUrl) || matchesUrlPattern(responseLocation)) {
            return true;
        }
        return matchesBodyPattern(responseBody);
    }

    private static boolean matchesUrlPattern(String url) {
        if (url == null || url.isEmpty()) return false;
        for (Pattern pattern : URL_COMPLETION_PATTERNS) {
            if (pattern.matcher(url).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBodyPattern(String body) {
        if (body == null || body.isEmpty()) return false;
        for (Pattern pattern : BODY_COMPLETION_PATTERNS) {
            if (pattern.matcher(body).find()) {
                return true;
            }
        }
        return false;
    }
}
