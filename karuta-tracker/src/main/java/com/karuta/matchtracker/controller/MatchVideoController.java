package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.MatchVideoCreateRequest;
import com.karuta.matchtracker.dto.MatchVideoDateCandidateDto;
import com.karuta.matchtracker.dto.MatchVideoDto;
import com.karuta.matchtracker.dto.MatchVideoUpdateRequest;
import com.karuta.matchtracker.dto.PagedResponse;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.MatchVideoService;
import com.karuta.matchtracker.util.OrganizationScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 試合動画（動画台帳）管理のRESTコントローラ
 *
 * 登録・閲覧は全ロール。編集・削除の所有者チェックはサービス層で行う。
 */
@RestController
@RequestMapping("/api/match-videos")
@RequiredArgsConstructor
@Slf4j
public class MatchVideoController {

    private final MatchVideoService matchVideoService;
    private final OrganizationScopeResolver organizationScopeResolver;

    /**
     * 動画を登録する。
     *
     * @param request 登録リクエスト
     * @return 登録された動画
     */
    @PostMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchVideoDto> register(@Valid @RequestBody MatchVideoCreateRequest request,
                                                  HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("POST /api/match-videos - 動画登録 matchDate={}, matchNumber={}, by={}",
                request.getMatchDate(), request.getMatchNumber(), currentUserId);
        MatchVideoDto created = matchVideoService.register(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 動画URLを差し替える（登録者本人 or ADMIN+）。
     *
     * @param id      動画ID
     * @param request 更新リクエスト
     * @return 更新後の動画
     */
    @PutMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchVideoDto> updateUrl(@PathVariable Long id,
                                                   @Valid @RequestBody MatchVideoUpdateRequest request,
                                                   HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        log.info("PUT /api/match-videos/{} - 動画URL差し替え by={}", id, currentUserId);
        MatchVideoDto updated = matchVideoService.updateUrl(id, request, currentUserId, currentUserRole);
        return ResponseEntity.ok(updated);
    }

    /**
     * 動画を削除する（紐付け削除＝物理削除。登録者本人 or ADMIN+）。
     *
     * @param id 動画ID
     * @return レスポンスなし
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        log.info("DELETE /api/match-videos/{} - 動画削除 by={}", id, currentUserId);
        matchVideoService.delete(id, currentUserId, currentUserRole);
        return ResponseEntity.noContent().build();
    }

    /**
     * 指定日の動画一覧を取得する。
     *
     * @param date 対戦日
     * @return 動画一覧
     */
    @GetMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<MatchVideoDto>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /api/match-videos?date={} - 日付別動画一覧", date);
        return ResponseEntity.ok(matchVideoService.findByDate(date));
    }

    /**
     * 指定日の動画登録候補を取得する（動画倉庫の登録モーダル「日付から」用）。
     *
     * <p>組み合わせ（match_pairings）と試合結果（matches）を自然キーで統合・重複排除し、
     * 各候補に登録済みフラグ（registered）・結果有無（hasResult）・試合ID（matchId）を付与して返す。</p>
     *
     * <p><b>参加日スコープ（hasSessionOnDateForUser）は適用しない</b>ため、その日の練習に
     * 参加していないユーザー（撮影担当・第三者登録）でも候補を取得できる（動画登録は全選手可の仕様）。
     * <b>組織スコープは維持</b>し、{@link OrganizationScopeResolver} で effectiveOrgId を解決して
     * 他団体の候補混入を防ぐ（{@code search} と同じ流儀）。</p>
     *
     * @param date           対戦日
     * @param organizationId 組織ID（任意。ロールごとのスコープ解決は OrganizationScopeResolver に委譲）
     * @return 候補リスト（matchNumber 昇順）
     */
    @GetMapping("/date-candidates")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<MatchVideoDateCandidateDto>> getDateCandidates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long organizationId,
            HttpServletRequest httpRequest) {
        log.debug("GET /api/match-videos/date-candidates?date={}, organizationId={} - 日付別動画登録候補",
                date, organizationId);
        Long effectiveOrgId = organizationScopeResolver.resolveEffectiveOrganizationId(httpRequest, organizationId);
        return ResponseEntity.ok(matchVideoService.getDateCandidates(date, effectiveOrgId));
    }

    /**
     * 動画倉庫の検索（ページング）。
     *
     * @param playerId 対戦者で絞り込む選手ID（任意）
     * @param year     対象年（任意）
     * @param month    対象月（任意・year併用時のみ有効）
     * @param mine     true の場合は自分が対戦者の動画のみ（playerIdより優先）
     * @param page     ページ番号（デフォルト0）
     * @param size     1ページ件数（デフォルト20・上限100）
     * @return ページングされた動画一覧
     */
    @GetMapping("/search")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<PagedResponse<MatchVideoDto>> search(
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.debug("GET /api/match-videos/search - playerId={}, year={}, month={}, mine={}, page={}, size={}",
                playerId, year, month, mine, page, size);
        PagedResponse<MatchVideoDto> result =
                matchVideoService.search(playerId, year, month, mine, page, size, currentUserId);
        return ResponseEntity.ok(result);
    }
}
