package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.service.proxy.VenueId;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * POST /api/venue-reservation-proxy/session のリクエストボディ。
 */
@Data
public class CreateVenueProxySessionRequest {

    @NotNull(message = "venue は必須です")
    private VenueId venue;

    @NotNull(message = "practiceSessionId は必須です")
    private Long practiceSessionId;

    @NotNull(message = "roomName は必須です")
    private String roomName;

    @NotNull(message = "date は必須です")
    private LocalDate date;

    /**
     * 時間帯スロットインデックス (0=午前 / 1=午後 / 2=夜間 等、会場別)。
     * primitive int のため null は受け付けない設計。Bean Validation の @NotNull は対象外。
     */
    private int slotIndex;

    /**
     * フロントエンドの絶対 URL (オリジン + パス、例: {@code https://app.example.com/practice})。
     * プロキシ画面の「アプリに戻る」リンクと、別オリジン構成での完了通知 (postMessage) の
     * targetOrigin として使用する。
     *
     * <p>API オリジンとフロントエンドオリジンが同一なら省略可。省略時はバナーは
     * {@code /practice} (相対 URL) と {@code BroadcastChannel} のみを利用する。</p>
     */
    private String returnUrl;
}
