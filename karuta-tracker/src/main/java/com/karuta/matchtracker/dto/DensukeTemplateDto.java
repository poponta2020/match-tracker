package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.DensukeTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 伝助テンプレート DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeTemplateDto {
    private Long organizationId;
    private String titleTemplate;
    private String description;
    private String contactEmail;

    public static DensukeTemplateDto fromEntity(DensukeTemplate entity) {
        return DensukeTemplateDto.builder()
                .organizationId(entity.getOrganizationId())
                .titleTemplate(entity.getTitleTemplate())
                .description(entity.getDescription())
                .contactEmail(entity.getContactEmail())
                .build();
    }

    /**
     * テンプレート未登録団体向けのデフォルト値を返す。
     */
    public static DensukeTemplateDto defaultFor(Long organizationId) {
        return DensukeTemplateDto.builder()
                .organizationId(organizationId)
                .titleTemplate("{year}年{month}月 練習出欠")
                .description("")
                .contactEmail("")
                .build();
    }
}
