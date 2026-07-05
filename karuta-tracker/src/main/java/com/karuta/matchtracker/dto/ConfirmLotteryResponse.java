package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LotteryExecution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/lottery/confirm のレスポンス。
 *
 * 抽選確定（DB）と伝助への一括書き戻しは部分成功を許容する。
 * 確定 DB 更新は成功したが伝助書き戻しが失敗した場合、{@code densukeWriteSucceeded = false}
 * と {@code densukeWriteError} で失敗を呼び出し元に伝搬する。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmLotteryResponse {

    /** 抽選実行履歴本体 */
    private LotteryExecution execution;

    /** 伝助書き戻しが成功したか（書き戻し対象外の場合は true） */
    private boolean densukeWriteSucceeded;

    /** 伝助書き戻し失敗時のエラーメッセージ概要（成功時は null） */
    private String densukeWriteError;

    /**
     * A-3: 確定書き戻し直前に検知した伝助差分（アプリ側○書き戻し予定なのに伝助側×＝不参加の
     * 反転リスク）の対象一覧。差分は確定をブロックしない（管理者が手動対応するための可視化用）。
     * 空リスト（null にはしない）。
     */
    @lombok.Builder.Default
    private java.util.List<String> densukeDiffs = java.util.List.of();
}
