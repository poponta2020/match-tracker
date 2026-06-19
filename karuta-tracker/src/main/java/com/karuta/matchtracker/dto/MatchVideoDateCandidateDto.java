package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 動画登録モーダル「日付から」の候補1件を表すDTO。
 *
 * <p>指定日の対戦カード（組み合わせ {@code match_pairings} と試合結果 {@code matches}）を
 * 自然キー {@code (matchDate, matchNumber, min(p1,p2), max(p1,p2))} で統合・重複排除した
 * 1スロットを表す。結果(matches)があるスロットは {@code hasResult=true} / {@code matchId} 付き、
 * 組み合わせ(pairings)のみのスロットは {@code hasResult=false} / {@code matchId=null}。</p>
 *
 * <p>{@code registered} は同自然キーの動画({@code match_videos})が既に登録済みかを表し、
 * フロントの「登録済み」グレーアウト判定に使う。</p>
 *
 * <p>{@code player1Id}/{@code player2Id} は自然キー正規化（player1Id &lt; player2Id）後の
 * 生IDをそのまま入れる。フロント側はこの生IDで「相手未登録(0/null)」を判定するため、
 * 表示用の入れ替え等は行わない。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchVideoDateCandidateDto {

    private LocalDate matchDate;
    private Integer matchNumber;
    private Long player1Id;
    private String player1Name;
    private Long player2Id;
    private String player2Name;

    /** 同自然キーの試合結果（matches）が存在する場合 true。 */
    private boolean hasResult;

    /** 同自然キーの試合結果（matches）のID。結果未入力なら null。 */
    private Long matchId;

    /** 同自然キーの動画（match_videos）が登録済みなら true。 */
    private boolean registered;
}
