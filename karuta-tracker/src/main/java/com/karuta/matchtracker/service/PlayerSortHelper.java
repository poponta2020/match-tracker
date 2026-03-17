package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.NextParticipationDto;
import com.karuta.matchtracker.entity.Player;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * 選手ソート用ヘルパー
 * ソート順: 級位（A級→E級）→ 段位（八段→無段）→ 名前（あいうえお順）→ 未設定は最後
 */
public class PlayerSortHelper {

    private static final Collator JAPANESE_COLLATOR = Collator.getInstance(Locale.JAPANESE);

    /**
     * KyuRankのソート順（A級が先）。ordinalは E=0,D=1,C=2,B=3,A=4 なので逆順にする。
     */
    private static int kyuRankOrder(Player.KyuRank kyuRank) {
        if (kyuRank == null) return Integer.MAX_VALUE;
        return Player.KyuRank.values().length - 1 - kyuRank.ordinal();
    }

    /**
     * DanRankのソート順（八段が先）。ordinalは 無段=0,...八段=8 なので逆順にする。
     */
    private static int danRankOrder(Player.DanRank danRank) {
        if (danRank == null) return Integer.MAX_VALUE;
        return Player.DanRank.values().length - 1 - danRank.ordinal();
    }

    /**
     * Playerエンティティ用のComparator
     */
    public static Comparator<Player> playerComparator() {
        return Comparator
                .comparingInt((Player p) -> kyuRankOrder(p.getKyuRank()))
                .thenComparingInt(p -> danRankOrder(p.getDanRank()))
                .thenComparing(p -> p.getName() != null ? p.getName() : "", JAPANESE_COLLATOR);
    }

    /**
     * NextParticipationDto.ParticipantInfo用のComparator
     */
    public static Comparator<NextParticipationDto.ParticipantInfo> participantInfoComparator() {
        return Comparator
                .comparingInt((NextParticipationDto.ParticipantInfo p) -> kyuRankOrder(p.getKyuRank()))
                .thenComparingInt(p -> danRankOrder(p.getDanRank()))
                .thenComparing(p -> p.getName() != null ? p.getName() : "", JAPANESE_COLLATOR);
    }
}
