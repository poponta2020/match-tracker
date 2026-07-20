package com.karuta.matchtracker.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Authorization ヘッダーから Bearer トークンを取り出すユーティリティ（auth-tokenization）
 *
 * 認証インターセプタとログアウトエンドポイントで同じ解釈をするために共通化している。
 */
public final class BearerTokenExtractor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private BearerTokenExtractor() {
    }

    /**
     * リクエストの Authorization ヘッダーから生トークンを取り出す
     *
     * @param request HTTPリクエスト
     * @return 生トークン。ヘッダーが無い・Bearer 形式でない・値が空の場合は null
     */
    public static String extract(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }

        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
