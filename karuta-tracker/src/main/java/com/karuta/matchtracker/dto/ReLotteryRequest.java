package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * セッション再抽選リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReLotteryRequest {

    /**
     * 管理者が指定する優先選手のIDリスト（任意）。
     * null の場合は直近の抽選実行時の priorityPlayerIds を引き継ぐ。
     * 空リスト [] の場合は優先選手なし（明示的クリア）として扱う。
     */
    private List<Long> priorityPlayerIds;
}
