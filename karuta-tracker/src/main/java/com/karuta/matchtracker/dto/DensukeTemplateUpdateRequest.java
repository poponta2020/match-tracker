package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 伝助テンプレート更新リクエスト DTO
 */
@Data
public class DensukeTemplateUpdateRequest {

    @NotBlank(message = "タイトルテンプレートは必須です")
    @Size(max = 200, message = "タイトルテンプレートは200文字以内で入力してください")
    private String titleTemplate;

    private String description;

    @Size(max = 255, message = "連絡先メールアドレスは255文字以内で入力してください")
    private String contactEmail;
}
