package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukeTemplateDto;
import com.karuta.matchtracker.dto.DensukeTemplateUpdateRequest;
import com.karuta.matchtracker.entity.DensukeTemplate;
import com.karuta.matchtracker.repository.DensukeTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeTemplateService 単体テスト")
class DensukeTemplateServiceTest {

    @Mock
    private DensukeTemplateRepository repository;

    @InjectMocks
    private DensukeTemplateService service;

    @Test
    @DisplayName("getTemplate: 未登録団体にはデフォルト値を返す")
    void getTemplate_returnsDefault_whenNotRegistered() {
        when(repository.findByOrganizationId(10L)).thenReturn(Optional.empty());

        DensukeTemplateDto dto = service.getTemplate(10L);

        assertThat(dto.getOrganizationId()).isEqualTo(10L);
        assertThat(dto.getTitleTemplate()).isEqualTo("{year}年{month}月 練習出欠");
        assertThat(dto.getDescription()).isEmpty();
        assertThat(dto.getContactEmail()).isEmpty();
    }

    @Test
    @DisplayName("getTemplate: 登録済み団体は保存値をそのまま返す")
    void getTemplate_returnsSavedValues_whenRegistered() {
        DensukeTemplate saved = DensukeTemplate.builder()
                .id(1L)
                .organizationId(10L)
                .titleTemplate("{year}年{month}月 カスタム出欠")
                .description("説明文")
                .contactEmail("owner@example.com")
                .build();
        when(repository.findByOrganizationId(10L)).thenReturn(Optional.of(saved));

        DensukeTemplateDto dto = service.getTemplate(10L);

        assertThat(dto.getTitleTemplate()).isEqualTo("{year}年{month}月 カスタム出欠");
        assertThat(dto.getDescription()).isEqualTo("説明文");
        assertThat(dto.getContactEmail()).isEqualTo("owner@example.com");
    }

    @Test
    @DisplayName("updateTemplate: 既存レコードがあれば上書き更新")
    void updateTemplate_updates_whenExists() {
        DensukeTemplate existing = DensukeTemplate.builder()
                .id(1L)
                .organizationId(10L)
                .titleTemplate("旧タイトル")
                .description("旧説明")
                .contactEmail("old@example.com")
                .build();
        when(repository.findByOrganizationId(10L)).thenReturn(Optional.of(existing));
        when(repository.save(any(DensukeTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        DensukeTemplateUpdateRequest req = new DensukeTemplateUpdateRequest();
        req.setTitleTemplate("新タイトル {year}");
        req.setDescription("新説明");
        req.setContactEmail("new@example.com");

        DensukeTemplateDto dto = service.updateTemplate(10L, req);

        assertThat(dto.getTitleTemplate()).isEqualTo("新タイトル {year}");
        assertThat(dto.getDescription()).isEqualTo("新説明");
        assertThat(dto.getContactEmail()).isEqualTo("new@example.com");
        verify(repository).save(any(DensukeTemplate.class));
    }

    @Test
    @DisplayName("updateTemplate: 未登録なら新規作成して保存")
    void updateTemplate_creates_whenNotExists() {
        when(repository.findByOrganizationId(10L)).thenReturn(Optional.empty());
        when(repository.save(any(DensukeTemplate.class))).thenAnswer(inv -> {
            DensukeTemplate t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        DensukeTemplateUpdateRequest req = new DensukeTemplateUpdateRequest();
        req.setTitleTemplate("新規タイトル");
        req.setDescription("説明");

        DensukeTemplateDto dto = service.updateTemplate(10L, req);

        assertThat(dto.getTitleTemplate()).isEqualTo("新規タイトル");
        assertThat(dto.getOrganizationId()).isEqualTo(10L);
        verify(repository).save(any(DensukeTemplate.class));
    }
}
