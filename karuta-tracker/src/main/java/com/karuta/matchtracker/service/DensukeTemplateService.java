package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukeTemplateDto;
import com.karuta.matchtracker.dto.DensukeTemplateUpdateRequest;
import com.karuta.matchtracker.entity.DensukeTemplate;
import com.karuta.matchtracker.repository.DensukeTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 伝助テンプレート CRUD サービス
 *
 * 団体ごとに1レコードのテンプレートを管理する。未登録団体にはデフォルト値を返す。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DensukeTemplateService {

    private final DensukeTemplateRepository repository;

    /**
     * 指定団体のテンプレートを取得する。未登録の場合はデフォルト値を返す。
     */
    @Transactional(readOnly = true)
    public DensukeTemplateDto getTemplate(Long organizationId) {
        return repository.findByOrganizationId(organizationId)
                .map(DensukeTemplateDto::fromEntity)
                .orElseGet(() -> DensukeTemplateDto.defaultFor(organizationId));
    }

    /**
     * 指定団体のテンプレートを更新する (upsert)。
     */
    @Transactional
    public DensukeTemplateDto updateTemplate(Long organizationId, DensukeTemplateUpdateRequest request) {
        DensukeTemplate template = repository.findByOrganizationId(organizationId)
                .orElseGet(() -> DensukeTemplate.builder()
                        .organizationId(organizationId)
                        .build());

        template.setTitleTemplate(request.getTitleTemplate());
        template.setDescription(request.getDescription());
        template.setContactEmail(request.getContactEmail());

        DensukeTemplate saved = repository.save(template);
        log.info("DensukeTemplate upserted: organizationId={}, id={}", organizationId, saved.getId());
        return DensukeTemplateDto.fromEntity(saved);
    }
}
