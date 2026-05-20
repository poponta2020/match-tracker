package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ゲスト参加用のiCalフィード情報DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestFeedDto {

    private String url;
}
