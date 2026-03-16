package com.karuta.matchtracker.dto;

import lombok.Data;

@Data
public class GoogleCalendarSyncRequest {

    /**
     * フロントエンドGISから取得したGoogleアクセストークン
     */
    private String accessToken;

    /**
     * 同期対象のプレイヤーID
     */
    private Long playerId;
}
