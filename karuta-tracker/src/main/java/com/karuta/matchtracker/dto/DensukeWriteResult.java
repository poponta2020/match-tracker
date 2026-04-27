package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 伝助一括書き戻しの結果。
 *
 * <p>抽選確定の DB 更新と伝助への書き戻しは部分成功を許容するため、
 * 内部エラー（HTTP 4xx/5xx、メンバーID 取得失敗、一覧ページ取得失敗等）も
 * 例外ではなく {@code success=false} と {@code errors} で呼び出し元に返す。
 *
 * <p>例外で伝えると外側の {@code @Transactional} がロールバックオンリーとなり、
 * 確定 DB 更新が巻き戻ってしまうため、必ずこの戻り値で表現する。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DensukeWriteResult {

    /** 書き戻し全体の成否（errors が空なら true） */
    private boolean success;

    /** 失敗内容の概要（成功時は空リスト、決して null にはしない） */
    private List<String> errors;

    public static DensukeWriteResult success() {
        return DensukeWriteResult.builder().success(true).errors(List.of()).build();
    }

    public static DensukeWriteResult failure(List<String> errors) {
        return DensukeWriteResult.builder()
                .success(false)
                .errors(List.copyOf(errors))
                .build();
    }
}
