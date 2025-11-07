package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PracticeSession;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 練習日登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionCreateRequest {

    @NotNull(message = "練習日は必須です")
    private LocalDate sessionDate;

    @Min(value = 0, message = "総試合数は0以上で入力してください")
    private Integer totalMatches;

    /**
     * リクエストからエンティティへ変換
     */
    public PracticeSession toEntity() {
        return PracticeSession.builder()
                .sessionDate(sessionDate)
                .totalMatches(totalMatches != null ? totalMatches : 0)
                .build();
    }
}
