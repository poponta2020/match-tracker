package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoMatchingRequest {
    private LocalDate sessionDate;
    private Integer matchNumber;

    /**
     * 「ロック以外を再シャッフル」で、クライアントの現在ロック状態を尊重するための入力（後方互換・nullable）。
     *
     * <ul>
     *   <li>{@code null}（キー欠落。既存の新規作成フロー）: 従来どおり DB の {@code hasResult} /
     *       {@code locked} フラグから保持組を導出する（挙動不変）。</li>
     *   <li>非 null（空配列を含む。再シャッフル）: 手動ロックはこの {@code lockedPairs} を正とし、
     *       DB の {@code locked} フラグは参照しない（ローカルで解除した組は再シャッフル対象・未保存で
     *       ロックした組は保持）。結果入力済み（{@code hasResult}）は常に DB から保護する。</li>
     * </ul>
     */
    private List<LockedPairInput> lockedPairs;

    /**
     * 再シャッフル時に保持する組（手動ロック）を表すクライアント入力。
     * 未保存の組も含まれ得るため DB 行の有無に依存しない（選手IDのペアで指定する）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LockedPairInput {
        private Long player1Id;
        private Long player2Id;
    }
}
