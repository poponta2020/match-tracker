package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.CardRuleNonce;
import com.karuta.matchtracker.repository.CardRuleNonceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 札ルール再生成カウンタ(nonce)のDB共有サービス。
 * 未登録日の nonce は 0（既定値は全端末で一致する）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardRuleNonceService {

    private final CardRuleNonceRepository repository;

    public int getNonce(LocalDate date) {
        return repository.findBySessionDate(date)
                .map(CardRuleNonce::getNonce)
                .orElse(0);
    }

    @Transactional
    public int setNonce(LocalDate date, int nonce) {
        int safe = Math.max(0, nonce);
        CardRuleNonce entity = repository.findBySessionDate(date)
                .orElseGet(() -> CardRuleNonce.builder().sessionDate(date).nonce(0).build());
        entity.setNonce(safe);
        repository.save(entity);
        return entity.getNonce();
    }
}
