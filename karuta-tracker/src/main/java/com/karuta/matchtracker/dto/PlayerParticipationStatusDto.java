package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 選手の月間参加状況（抽選ステータス付き）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerParticipationStatusDto {

    /**
     * セッションIDごとの参加情報
     */
    private Map<Long, List<MatchParticipation>> participations;

    /**
     * セッションIDごとに抽選実行済み（SUCCESS）かどうか。
     * 個別セッションのロック表示（ステータス表示固定）の判定に使う。
     */
    private Map<Long, Boolean> lotteryExecuted;

    /**
     * 対象月に1つでも抽選確定済み（SUCCESS）の LotteryExecution が存在するか。
     * 月次抽選レコード（sessionId=null）とセッション単位の再抽選レコードの
     * 両方を考慮する。フロントの resolveAttendanceMode で「当月扱い／来月扱い」
     * の月単位判定に使う（セッション単位ロックの lotteryExecuted とは分離）。
     */
    private Boolean hasAnyExecutedLotteryInMonth;

    /**
     * 対象月が抽選締切前かどうか
     */
    private Boolean beforeDeadline;

    /**
     * B-4: その月×プレイヤーの参加状況の版情報（対象参加行のハッシュ）。
     * 参加登録リクエストに添付して楽観ロックに使う。並行編集で版が変わっていれば 409（再読込要）。
     * サーバは常に非null（行が無くても安定値）を返す。
     */
    private String version;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchParticipation {
        private Long participantId;
        private Integer matchNumber;
        private ParticipantStatus status;
        private Integer waitlistNumber;
    }
}
