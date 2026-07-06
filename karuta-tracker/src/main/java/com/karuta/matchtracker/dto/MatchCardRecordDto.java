package com.karuta.matchtracker.dto;

import lombok.*;

import java.util.List;

/**
 * 1試合・1プレイヤーの取り札記録（配置＋お手付き詳細）。取得レスポンス兼、保存リクエストに用いる。
 * オーナー(player_id)はヘッダ X-User-Id（currentUserId）で決まるため本DTOには含めない。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCardRecordDto {
    private List<CardPlacementDto> cardPlacements;
    private List<OtetsukiDetailDto> otetsukiDetails;
}
