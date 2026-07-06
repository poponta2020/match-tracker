package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.CardRuleNonce;
import com.karuta.matchtracker.repository.CardRuleNonceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardRuleNonceService 単体テスト")
class CardRuleNonceServiceTest {

    @Mock
    private CardRuleNonceRepository repository;

    @InjectMocks
    private CardRuleNonceService service;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Test
    @DisplayName("未登録日の nonce は既定 0")
    void getNonce_unregistered_returnsZero() {
        when(repository.findBySessionDate(DATE)).thenReturn(Optional.empty());
        assertThat(service.getNonce(DATE)).isEqualTo(0);
    }

    @Test
    @DisplayName("登録済み日の nonce を返す")
    void getNonce_registered_returnsValue() {
        when(repository.findBySessionDate(DATE))
                .thenReturn(Optional.of(CardRuleNonce.builder().sessionDate(DATE).nonce(3).build()));
        assertThat(service.getNonce(DATE)).isEqualTo(3);
    }

    @Test
    @DisplayName("setNonce: 新規日は作成して値を返す")
    void setNonce_new_creates() {
        when(repository.findBySessionDate(DATE)).thenReturn(Optional.empty());
        when(repository.save(any(CardRuleNonce.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setNonce(DATE, 2)).isEqualTo(2);
    }

    @Test
    @DisplayName("setNonce: 既存日は上書きする")
    void setNonce_existing_updates() {
        CardRuleNonce existing = CardRuleNonce.builder().sessionDate(DATE).nonce(1).build();
        when(repository.findBySessionDate(DATE)).thenReturn(Optional.of(existing));
        when(repository.save(any(CardRuleNonce.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setNonce(DATE, 5)).isEqualTo(5);
        assertThat(existing.getNonce()).isEqualTo(5);
    }

    @Test
    @DisplayName("setNonce: 負値は 0 にクランプ")
    void setNonce_negative_clampsToZero() {
        when(repository.findBySessionDate(DATE)).thenReturn(Optional.empty());
        when(repository.save(any(CardRuleNonce.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setNonce(DATE, -3)).isEqualTo(0);
    }
}
