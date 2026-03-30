package com.karuta.matchtracker.util;

import com.karuta.matchtracker.exception.ForbiddenException;

/**
 * ADMINロールの団体スコープ検証ユーティリティ。
 * ADMINが自団体以外のリソースを操作しようとした場合にForbiddenExceptionをスローする。
 */
public class AdminScopeValidator {

    private AdminScopeValidator() {
    }

    /**
     * ADMINの場合、targetOrgIdがadminOrgIdと一致するか検証する。
     * SUPER_ADMINなどADMIN以外のロールの場合は何もしない。
     *
     * @param role         ユーザーのロール文字列
     * @param adminOrgId   ADMINの管理団体ID
     * @param targetOrgId  操作対象の団体ID
     * @param errorMessage 不一致時のエラーメッセージ
     * @throws ForbiddenException adminOrgIdがnullまたはtargetOrgIdと不一致の場合
     */
    public static void validateScope(String role, Long adminOrgId, Long targetOrgId, String errorMessage) {
        if (!"ADMIN".equals(role)) return;
        if (adminOrgId == null || !adminOrgId.equals(targetOrgId)) {
            throw new ForbiddenException(errorMessage);
        }
    }
}
