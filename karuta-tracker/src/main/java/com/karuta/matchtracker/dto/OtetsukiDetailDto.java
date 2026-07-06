package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.MatchOtetsukiDetail;
import lombok.*;

/**
 * お手付き詳細DTO。
 * type: HIKKAKE/ANKI_MISS/MISHEARING/OTHER。種類ごとに以下が任意で入る。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtetsukiDetailDto {
    private Integer seq;
    private String type;
    private String hikkakeTarget;
    private String ankiDirection;
    private Integer mishearingReadCardNo;
    private Integer mishearingTouchedCardNo;
    private String otherText;

    public static OtetsukiDetailDto fromEntity(MatchOtetsukiDetail e) {
        return OtetsukiDetailDto.builder()
                .seq(e.getSeq())
                .type(e.getOtetsukiType())
                .hikkakeTarget(e.getHikkakeTarget())
                .ankiDirection(e.getAnkiDirection())
                .mishearingReadCardNo(e.getMishearingReadCardNo())
                .mishearingTouchedCardNo(e.getMishearingTouchedCardNo())
                .otherText(e.getOtherText())
                .build();
    }
}
