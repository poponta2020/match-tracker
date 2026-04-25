package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.venue.VenueRewriteStrategy;
import org.springframework.stereotype.Component;

/**
 * かでる2・7 用の {@link VenueRewriteStrategy} 実装。
 *
 * <p>Kaderu は通常の PHP form + 軽量 JS で動作し、コア書き換え (URL/バナー/Location/fetch/XHR フック) のみで
 * 動作する想定 (venues/kaderu.md §4 参照)。よって本実装は no-op + 空 injectScript で開始し、
 * 実機検証で問題が見つかれば追記する方針とする。</p>
 */
@Component
public class KaderuRewriteStrategy implements VenueRewriteStrategy {

    @Override
    public VenueId venue() {
        return VenueId.KADERU;
    }

    @Override
    public String rewriteHtml(String html, String proxyToken) {
        return html;
    }

    @Override
    public String injectScript() {
        return "";
    }
}
