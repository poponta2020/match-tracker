package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * ページング結果の汎用レスポンスDTO
 *
 * Spring Data の {@link Page}（{@code PageImpl}）はそのままシリアライズすると
 * フィールド構造が安定しない（バージョン間で警告が出る）ため、
 * 必要なフィールドのみを明示的に持つ専用レスポンスに変換して返す。
 *
 * @param <T> 要素の型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {

    /** 現在ページの要素 */
    private List<T> content;

    /** 現在のページ番号（0始まり） */
    private int page;

    /** 1ページあたりの件数 */
    private int size;

    /** 全件数 */
    private long totalElements;

    /** 総ページ数 */
    private int totalPages;

    /**
     * Spring の {@link Page} を、要素を変換しつつ {@link PagedResponse} に詰め替える。
     *
     * @param source  元のページ
     * @param mapper  要素変換関数
     * @param <S>     元の要素の型
     * @param <T>     変換後の要素の型
     * @return ページングレスポンス
     */
    public static <S, T> PagedResponse<T> from(Page<S> source, Function<S, T> mapper) {
        List<T> mapped = source.getContent().stream().map(mapper).toList();
        return PagedResponse.<T>builder()
                .content(mapped)
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .build();
    }
}
