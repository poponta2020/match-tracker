package com.karuta.matchtracker.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GoogleCalendarSyncResponse {

    /**
     * 新規作成イベント数
     */
    private int createdCount;

    /**
     * 更新したイベント数
     */
    private int updatedCount;

    /**
     * 削除したイベント数
     */
    private int deletedCount;

    /**
     * 変更なしスキップ数
     */
    private int unchangedCount;

    /**
     * エラー数
     */
    private int errorCount;

    /**
     * 詳細ログ
     */
    private List<String> details = new ArrayList<>();

    /**
     * エラー詳細
     */
    private List<String> errors = new ArrayList<>();
}
