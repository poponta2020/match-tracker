package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPairingCreateRequest {
    private LocalDate sessionDate;
    private Integer matchNumber;
    private Long player1Id;
    private Long player2Id;

    /**
     * 手動ロックフラグ。一括保存（createBatch）でロック状態をそのまま永続化するために使う。
     * null は false 扱い（{@code Boolean.TRUE.equals(...)} で判定）。
     */
    private Boolean locked;

    /**
     * locked を指定しない既存呼び出し向けの後方互換コンストラクタ（locked は null=false 扱い）。
     * {@code @AllArgsConstructor} で生成される5引数版とは別のオーバーロードとして共存する。
     */
    public MatchPairingCreateRequest(LocalDate sessionDate, Integer matchNumber, Long player1Id, Long player2Id) {
        this.sessionDate = sessionDate;
        this.matchNumber = matchNumber;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
    }
}
