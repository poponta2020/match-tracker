package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.CardDivisionTextDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.CardDivisionTextService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 札分け（札組）確認テキストの取得 REST。
 * 全プレイヤーが自分の練習会（団体）の「当日」の札分けをテキストで確認できる。
 * 画面表示・コピー用（LINE 送信はスケジューラが同じ {@link CardDivisionTextService} を使う）。
 */
@RestController
@RequestMapping("/api/card-division")
@RequiredArgsConstructor
public class CardDivisionController {

    private final CardDivisionTextService cardDivisionTextService;
    private final PracticeSessionRepository practiceSessionRepository;

    /**
     * 指定団体の当日（既定は JST 今日）の札分けテキストを返す。
     * 当日に該当団体のセッションが無ければ {@code hasSession=false}・{@code text=null}。
     * 日付は指定可能だが、既定は BE の JST 今日（画面とスケジューラで同じ日を指すため）。
     */
    @GetMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<CardDivisionTextDto> getCardDivision(
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate target = date != null ? date : JstDateTimeUtil.today();

        Optional<PracticeSession> sessionOpt =
                practiceSessionRepository.findBySessionDateAndOrganizationId(target, organizationId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.ok(CardDivisionTextDto.builder()
                    .hasSession(false)
                    .date(target)
                    .organizationId(organizationId)
                    .text(null)
                    .build());
        }

        String text = cardDivisionTextService.buildTextForSession(sessionOpt.get());
        return ResponseEntity.ok(CardDivisionTextDto.builder()
                .hasSession(true)
                .date(target)
                .organizationId(organizationId)
                .text(text)
                .build());
    }
}
