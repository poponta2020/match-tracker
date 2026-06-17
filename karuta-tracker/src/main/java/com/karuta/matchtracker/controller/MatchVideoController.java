package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.MatchVideoCreateRequest;
import com.karuta.matchtracker.dto.MatchVideoDateCandidateDto;
import com.karuta.matchtracker.dto.MatchVideoDto;
import com.karuta.matchtracker.dto.MatchVideoUpdateRequest;
import com.karuta.matchtracker.dto.PagedResponse;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.MatchVideoService;
import com.karuta.matchtracker.service.OrganizationService;
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
    private final OrganizationService organizationService;

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
     * <p><b>このエンドポイント限定の特例（PLAYER 限定）</b>: フロントは {@code organizationId} を渡さず、
     * {@link OrganizationScopeResolver} は PLAYER 未指定時に {@code null}（組織非限定）を返すため、
     * そのままでは同日の他団体候補が混入し得る。これを避けるため、<b>操作ロールが PLAYER で</b>
     * effectiveOrgId が {@code null} の場合に限り、<b>操作ユーザーの単一所属団体を既定スコープとして補完</b>する
     * （{@link #resolveDefaultOrganizationIdForCandidates}）。動画登録候補は実運用上、操作者の所属団体に
     * 限定するのが自然なため。0 または複数所属の場合は {@code null} のまま（＝非限定。複数所属時の一意解決は
     * アプリ全体の別課題に委ねる）。<b>SUPER_ADMIN / ADMIN ではこの既定解決を行わない</b>:
     * SUPER_ADMIN は全団体横断（未指定なら非限定のまま）、ADMIN は {@code adminOrganizationId} で
     * resolver がスコープ済みのため。SUPER_ADMIN がたまたま単一団体に所属していてもその団体に勝手に
     * 絞らない。この特例は当エンドポイント限定で、他エンドポイントの「PLAYER 未指定→null」挙動は
     * 変えない。参加日スコープとは独立しており、非参加ユーザー（撮影担当等）でも所属団体の候補は見られる。</p>
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
        // 動画登録候補は単一所属PLAYERを既定で所属団体にスコープする（当エンドポイント限定の特例）。
        // OrganizationScopeResolver が null（PLAYER 未指定など組織非限定）を返し、かつ操作ロールが
        // PLAYER のときに限り、操作ユーザーの所属団体がちょうど1件ならその団体IDで補完し、
        // 他団体候補の混入を防ぐ。SUPER_ADMIN / ADMIN は既定解決の所属引きを行わず、
        // resolver の結果（SUPER_ADMIN 未指定なら null＝非限定）のままにする
        // （SUPER_ADMIN が単一所属を持っていてもその団体に勝手に絞らない）。
        String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");
        if (effectiveOrgId == null && "PLAYER".equals(currentUserRole)) {
            Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
            effectiveOrgId = resolveDefaultOrganizationIdForCandidates(currentUserId);
        }
        return ResponseEntity.ok(matchVideoService.getDateCandidates(date, effectiveOrgId));
    }

    /**
     * 動画登録候補（{@code date-candidates}）専用の既定組織スコープを解決する。
     *
     * <p>操作ロールが <b>PLAYER</b> かつ {@link OrganizationScopeResolver} が組織非限定（{@code null}）と
     * 判断した場合に限り呼ばれる（呼び出し側でロール判定済み）。操作ユーザーの所属団体が
     * <b>ちょうど1件</b>のときだけその団体IDを返し、当該団体にスコープする。
     * 0 件（未所属）または複数所属の場合は {@code null} を返し、従来どおり組織非限定として扱う
     * （複数所属時の一意な団体決定はアプリ全体の別課題に委ねる）。</p>
     *
     * <p>SUPER_ADMIN / ADMIN ではそもそも呼ばれない（呼び出し側で PLAYER 限定にしているため）。
     * 仮に PLAYER で {@code currentUserId} が所属を持たない場合も自然に {@code null} に倒れる。
     * この既定解決は当エンドポイント限定の特例であり、他エンドポイントの組織解決挙動には影響しない。</p>
     *
     * @param currentUserId 操作ユーザーID（null 可。null の場合は組織非限定）
     * @return 単一所属ならその団体ID、0/複数所属または currentUserId が null なら {@code null}
     */
    Long resolveDefaultOrganizationIdForCandidates(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        List<Long> orgIds = organizationService.getPlayerOrganizationIds(currentUserId);
        if (orgIds != null && orgIds.size() == 1) {
            return orgIds.get(0);
        }
        return null;
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
