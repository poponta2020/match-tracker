package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 試合動画 更新リクエスト（URL差し替え）
 *
 * 紐付け先の試合は変更せず、動画URLのみを差し替える。
 */
@Data
public class MatchVideoUpdateRequest {

    @NotBlank(message = "動画URLは必須です")
    private String videoUrl;
}
