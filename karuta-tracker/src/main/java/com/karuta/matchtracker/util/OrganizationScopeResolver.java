package com.karuta.matchtracker.util;

import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 読み取りAPIの組織スコープを解決する共通ユーティリティ。
 *
 * 同日に複数団体の練習セッションが存在する場合、画面側が参照するセッションと
 * バックエンドのペアリング生成対象がズレないよう、ADMIN だけでなく PLAYER /
 * SUPER_ADMIN にも明示的な organizationId を渡せる経路を提供する。
 *
 * ロールごとの解決ルール:
 *  - ADMIN: adminOrganizationId を強制使用。requestedOrganizationId が指定された
 *    場合は adminOrganizationId と一致する必要があり、不一致なら 403。
 *  - PLAYER: requestedOrganizationId が指定された場合は当該ユーザーの所属団体に
 *    含まれる必要があり、不一致なら 403。未指定なら null（従来の日付のみ検索）。
 *  - SUPER_ADMIN: requestedOrganizationId をそのまま返す（未指定なら null）。
 *
 * このユーティリティは @RequireRole 付きエンドポイントから利用することを前提に
 * している（currentUserRole / currentUserId / adminOrganizationId 属性が
 * RoleCheckInterceptor によりセットされている前提）。
 */
@Component
@RequiredArgsConstructor
public class OrganizationScopeResolver {

    private final OrganizationService organizationService;

    /**
     * リクエストとロールに基づき、サービス層に渡す組織スコープを解決する。
     *
     * @param httpRequest             RoleCheckInterceptor によりロール属性が
     *                                セット済みのリクエスト
     * @param requestedOrganizationId クライアントから渡された organizationId
     *                                （任意。null の場合は組織非指定として扱う）
     * @return 組織スコープに使うべき organizationId（null は組織非限定）
     * @throws ForbiddenException     ロールごとの権限ルールに反する場合
     */
    public Long resolveEffectiveOrganizationId(HttpServletRequest httpRequest,
                                                Long requestedOrganizationId) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");

        if ("ADMIN".equals(role)) {
            if (requestedOrganizationId != null
                    && !requestedOrganizationId.equals(adminOrgId)) {
                throw new ForbiddenException("他団体のリソースにはアクセスできません");
            }
            return adminOrgId;
        }
        if ("PLAYER".equals(role)
                && requestedOrganizationId != null
                && currentUserId != null) {
            List<Long> playerOrgIds = organizationService.getPlayerOrganizationIds(currentUserId);
            if (!playerOrgIds.contains(requestedOrganizationId)) {
                throw new ForbiddenException("参加していない団体のリソースにはアクセスできません");
            }
            return requestedOrganizationId;
        }
        return requestedOrganizationId;
    }
}
