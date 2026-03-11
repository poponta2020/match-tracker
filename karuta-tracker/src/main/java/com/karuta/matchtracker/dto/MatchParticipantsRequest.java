package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 試合別参加者設定リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchParticipantsRequest {

    @NotNull(message = "参加者IDリストは必須です")
    private List<Long> playerIds;
}
