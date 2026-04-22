package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
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
     * 省略時は初回抽選時の priorityPlayerIds を引き継ぐ。
     */
    @Builder.Default
    private List<Long> priorityPlayerIds = new ArrayList<>();
}
