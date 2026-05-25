package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kaderu 同期手動トリガーのリクエストボディ。
 *
 * <ul>
 *   <li>ADMIN は省略可（{@code adminOrganizationId} が自動採用される）。</li>
 *   <li>SUPER_ADMIN は対象団体の {@code organizationId} を必須指定する。</li>
 *   <li>ADMIN が自団体以外の id を指定した場合は 403。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KaderuSyncTriggerRequest {

    private Long organizationId;
}
