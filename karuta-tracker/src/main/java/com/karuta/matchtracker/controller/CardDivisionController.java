package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.CardDivisionSubscriptionRequest;
import com.karuta.matchtracker.dto.CardDivisionTextDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.CardDivisionTextService;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 札分け（札組）確認テキスト＋LINE購読トグルの REST。
 * 全プレイヤーが自分の練習会（団体）の「当日」の札分けをテキストで確認でき（画面表示・コピー用）、
 * 練習会ごとに札分けリマインダーの LINE 購読（{@code card_division_reminder}・既定 OFF）を切り替えられる。
 * LINE 送信はスケジューラが同じ {@link CardDivisionTextService} を使う。
 */
@RestController
@RequestMapping("/api/card-division")
@RequiredArgsConstructor
public class CardDivisionController {

    private final CardDivisionTextService cardDivisionTextService;
    private final PracticeSessionRepository practiceSessionRepository;
    private final LineNotificationService lineNotificationService;

    /**
     * 指定団体の当日（既定は JST 今日）の札分けテキストと購読状態を返す。
     * 当日に該当団体のセッションが無ければ {@code hasSession=false}・{@code text=null}
     * （購読状態はセッション有無に依存せず返す）。
     * 日付は指定可能だが、既定は BE の JST 今日（画面とスケジューラで同じ日を指すため）。
     */
    @GetMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<CardDivisionTextDto> getCardDivision(
            @RequestParam Long playerId,
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate target = date != null ? date : JstDateTimeUtil.today();
        boolean subscribed = lineNotificationService.isCardDivisionReminderEnabled(playerId, organizationId);

        Optional<PracticeSession> sessionOpt =
                practiceSessionRepository.findBySessionDateAndOrganizationId(target, organizationId);

        String text = sessionOpt.map(cardDivisionTextService::buildTextForSession).orElse(null);

        return ResponseEntity.ok(CardDivisionTextDto.builder()
                .hasSession(sessionOpt.isPresent())
                .date(target)
                .organizationId(organizationId)
                .text(text)
                .subscribed(subscribed)
                .build());
    }

    /**
     * 札分けリマインダー LINE 購読トグルを per-(player, org) で更新する。
     * {@code card_division_reminder} のみを部分更新し、他の通知種別は保持する
     * （新規行は他種別 ON の既定で作成される。{@link LineNotificationService#setCardDivisionReminder} 参照）。
     */
    @PutMapping("/subscription")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Void> updateSubscription(@RequestBody CardDivisionSubscriptionRequest request) {
        lineNotificationService.setCardDivisionReminder(
                request.getPlayerId(), request.getOrganizationId(), request.isEnabled());
        return ResponseEntity.ok().build();
    }
}
