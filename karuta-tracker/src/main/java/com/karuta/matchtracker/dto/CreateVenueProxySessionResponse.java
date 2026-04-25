package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.service.proxy.VenueId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/venue-reservation-proxy/session のレスポンス。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateVenueProxySessionResponse {

    /** プロキシセッション識別子 (UUID) */
    private String proxyToken;

    /** 申込トレイ画面取得用URL (/api/venue-reservation-proxy/view?token=...) */
    private String viewUrl;

    /** 会場識別子 (フロント側で念のため確認に使う) */
    private VenueId venue;
}
