package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.LineChatWorkerResultRequest;
import com.karuta.matchtracker.dto.LineChatWorkerTaskDto;
import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineChatReservation;
import com.karuta.matchtracker.entity.LineChatReservation.ReservationStatus;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.service.LineChatReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * VM常駐ワーカー向けのAPI（line-chat-reserve-broadcast タスク3）。
 *
 * <p><b>認証はロール（{@code @RequireRole}）ではなくサービストークン</b>
 * （{@link com.karuta.matchtracker.interceptor.ServiceTokenInterceptor}・{@code X-Service-Token}）で行う。
 * よって本コントローラのエンドポイントには意図的に {@code @RequireRole} を付けない（AC-2）。
 *
 * <ul>
 *   <li>GET {@code /tasks}: 処理対象（PENDING・CANCEL_PENDING）をグループ照合情報つきで返す。</li>
 *   <li>POST {@code /{id}/result}: 結果報告（状態遷移を検証。不正遷移は409）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/line-chat-worker")
@RequiredArgsConstructor
public class LineChatWorkerController {

    private final LineChatReservationService reservationService;
    private final LineBroadcastGroupRepository groupRepository;

    @GetMapping("/tasks")
    public List<LineChatWorkerTaskDto> getTasks() {
        List<LineChatReservation> tasks = reservationService.getWorkerTasks();
        List<Long> groupIds = tasks.stream().map(LineChatReservation::getBroadcastGroupId).distinct().toList();
        Map<Long, LineBroadcastGroup> groups = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(LineBroadcastGroup::getId, Function.identity()));
        return tasks.stream()
                .map(t -> LineChatWorkerTaskDto.fromEntity(t, groups.get(t.getBroadcastGroupId())))
                .toList();
    }

    @PostMapping("/{id}/result")
    public LineChatWorkerTaskDto reportResult(@PathVariable Long id,
                                              @RequestBody LineChatWorkerResultRequest request) {
        ReservationStatus newStatus = parseStatus(request.status());
        LineChatReservation updated = reservationService.applyWorkerResult(
                id, newStatus, request.errorCode(), request.errorMessage());
        LineBroadcastGroup group = groupRepository.findById(updated.getBroadcastGroupId()).orElse(null);
        return LineChatWorkerTaskDto.fromEntity(updated, group);
    }

    private static ReservationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status は必須です");
        }
        try {
            return ReservationStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不正な status: " + raw);
        }
    }
}
