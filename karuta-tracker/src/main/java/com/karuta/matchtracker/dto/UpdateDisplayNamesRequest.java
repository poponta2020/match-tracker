package com.karuta.matchtracker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * カレンダー表示名一括更新リクエスト
 * displayNames は organizationId (文字列) → 表示名 のマップ
 * 値が null または空文字なら NULL（カスタマイズ解除）として扱われる
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDisplayNamesRequest {
    private Map<String, String> displayNames;
}
