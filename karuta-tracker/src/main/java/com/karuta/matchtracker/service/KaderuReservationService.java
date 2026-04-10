package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.AdjacentRoomConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * かでる2・7 予約画面自動遷移サービス
 *
 * Node.js + Playwrightスクリプトを呼び出し、
 * ログイン→空き状況→スロット選択→申込トレイ画面まで自動遷移する。
 * 利用目的は未入力のまま（誤申込み防止）。
 */
@Service
@Slf4j
public class KaderuReservationService {

    @Value("${kaderu.enabled:false}")
    private boolean enabled;

    @Value("${kaderu.user-id:}")
    private String kaderuUserId;

    @Value("${kaderu.password:}")
    private String kaderuPassword;

    @Value("${kaderu.script-path:scripts/room-checker/open-reserve.js}")
    private String scriptPath;

    @Value("${kaderu.node-command:node}")
    private String nodeCommand;

    /** スクリプト全体のタイムアウト（秒） — JSON出力前にハングした場合に強制終了する */
    private static final int PROCESS_TIMEOUT_SECONDS = 120;

    private static final Map<Integer, String> SLOT_LABELS = Map.of(
            0, "午前 (9:00-12:00)",
            1, "午後 (13:00-16:00)",
            2, "夜間 (17:00-21:00)"
    );

    @PostConstruct
    void validateConfiguration() {
        if (!enabled) {
            log.info("Kaderu reservation feature is disabled (kaderu.enabled=false)");
            return;
        }
        // 有効時はスクリプトパスの存在を検証（fail-fast）
        File scriptFile = new File(scriptPath);
        if (!scriptFile.isAbsolute() && !scriptFile.exists()) {
            File parentResolved = new File("..", scriptPath);
            if (!parentResolved.exists()) {
                log.error("Kaderu script not found at '{}' or '{}'. "
                        + "Set KADERU_SCRIPT_PATH to a valid path or disable with KADERU_ENABLED=false",
                        scriptFile.getAbsolutePath(), parentResolved.getAbsolutePath());
            }
        }
        log.info("Kaderu reservation feature is enabled: scriptPath={}, nodeCommand={}", scriptPath, nodeCommand);
    }

    /**
     * 予約画面（申込トレイ）をブラウザで開く
     *
     * @param roomName 部屋名（すずらん/はまなす/あかなら/えぞまつ）
     * @param date     予約日
     * @param slotIndex 時間帯（0=午前, 1=午後, 2=夜間）
     * @return 実行結果
     */
    public ReservationResult openReservationPage(String roomName, LocalDate date, int slotIndex) {
        if (!enabled) {
            return ReservationResult.error("DISABLED",
                    "予約画面遷移機能は無効です（kaderu.enabled=false）。ローカル環境でのみ利用可能です。");
        }

        // バリデーション
        if (!AdjacentRoomConfig.isValidKaderuRoomName(roomName)) {
            return ReservationResult.error("INVALID_ROOM",
                    "無効な部屋名です: " + roomName);
        }
        if (slotIndex < 0 || slotIndex > 2) {
            return ReservationResult.error("INVALID_SLOT",
                    "無効な時間帯です: " + slotIndex);
        }
        if (date.isBefore(JstDateTimeUtil.today())) {
            return ReservationResult.error("PAST_DATE",
                    "過去の日付は指定できません: " + date);
        }
        if (kaderuUserId.isEmpty() || kaderuPassword.isEmpty()) {
            return ReservationResult.error("NO_CREDENTIALS",
                    "かでる2・7のログイン情報が設定されていません");
        }

        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("Opening reservation page: room={}, date={}, slot={}({})",
                roomName, dateStr, slotIndex, SLOT_LABELS.get(slotIndex));

        try {
            // スクリプトパスの解決: 相対パスの場合はカレントディレクトリと親ディレクトリの両方を探索
            String resolvedScriptPath = scriptPath;
            File scriptFile = new File(scriptPath);
            if (!scriptFile.isAbsolute() && !scriptFile.exists()) {
                // karuta-tracker/ から起動した場合、親ディレクトリ基準で探す
                File parentResolved = new File("..", scriptPath);
                if (parentResolved.exists()) {
                    resolvedScriptPath = parentResolved.getCanonicalPath();
                    log.debug("Resolved script path: {} -> {}", scriptPath, resolvedScriptPath);
                } else {
                    log.error("Script not found at {} or {}", scriptFile.getAbsolutePath(), parentResolved.getAbsolutePath());
                    return ReservationResult.error("SCRIPT_NOT_FOUND",
                            "予約スクリプトが見つかりません: " + scriptPath);
                }
            }

            ProcessBuilder pb = new ProcessBuilder(
                    nodeCommand, resolvedScriptPath,
                    "--room", roomName,
                    "--date", dateStr,
                    "--slot", String.valueOf(slotIndex)
            );
            pb.environment().put("KADERU_USER_ID", kaderuUserId);
            pb.environment().put("KADERU_PASSWORD", kaderuPassword);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // プロセス全体のタイムアウト: JSON出力前にハングした場合にreadLine()の
            // ブロックを解除するため、プロセスを強制終了する
            ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> timeoutTask = watchdog.schedule(() -> {
                if (process.isAlive()) {
                    log.warn("open-reserve.js process timed out after {}s, destroying forcibly",
                            PROCESS_TIMEOUT_SECONDS);
                    process.destroyForcibly();
                }
            }, PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // スクリプトの出力を読み取り（申込トレイ画面到達まで待機）
            StringBuilder output = new StringBuilder();
            String lastJsonLine = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("open-reserve.js: {}", line);
                    // JSON形式の結果行を探す
                    if (line.trim().startsWith("{") && line.trim().endsWith("}")) {
                        lastJsonLine = line.trim();
                        // 成功/失敗いずれでもプロセスの終了を待たずに結果を返す
                        if (lastJsonLine.contains("\"success\":true") || lastJsonLine.contains("\"success\":false")) {
                            break;
                        }
                    }
                }
            } finally {
                timeoutTask.cancel(false);
                watchdog.shutdown();
            }

            // プロセスが残っている場合はタイムアウト付きで終了を待つ
            if (process.isAlive()) {
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    log.warn("open-reserve.js process did not exit in time, destroying forcibly");
                    process.destroyForcibly();
                }
            }

            // タイムアウトでプロセスが強制終了された場合
            if (lastJsonLine == null && !process.isAlive() && process.exitValue() != 0) {
                log.warn("open-reserve.js was terminated (exit={}), output={}",
                        process.exitValue(), output.toString().substring(0, Math.min(output.length(), 500)));
                return ReservationResult.error("TIMEOUT",
                        "予約スクリプトがタイムアウトしました");
            }

            if (lastJsonLine != null && lastJsonLine.contains("\"success\":true")) {
                log.info("Reservation page opened successfully: room={}, date={}, slot={}",
                        roomName, dateStr, slotIndex);
                return ReservationResult.success(roomName, dateStr, SLOT_LABELS.get(slotIndex));
            } else {
                // エラー行からエラー種別を抽出
                String errorType = "UNKNOWN";
                if (lastJsonLine != null) {
                    if (lastJsonLine.contains("LOGIN_FAILED")) errorType = "LOGIN_FAILED";
                    else if (lastJsonLine.contains("NOT_AVAILABLE")) errorType = "NOT_AVAILABLE";
                    else if (lastJsonLine.contains("ROOM_NOT_FOUND")) errorType = "ROOM_NOT_FOUND";
                    else if (lastJsonLine.contains("TRAY_NAVIGATION_FAILED")) errorType = "TRAY_NAVIGATION_FAILED";
                }

                log.warn("Failed to open reservation page: error={}, output={}",
                        errorType, output.toString().substring(0, Math.min(output.length(), 500)));
                return ReservationResult.error(errorType,
                        "予約画面の表示に失敗しました (" + errorType + ")");
            }
        } catch (Exception e) {
            log.error("Error launching reservation script", e);
            return ReservationResult.error("SCRIPT_ERROR", e.getMessage());
        }
    }

    /**
     * Venue IDを指定して予約画面を開く（隣室予約用）
     *
     * @param venueId   かでる会場のVenue ID
     * @param date      予約日
     * @param slotIndex 時間帯（0=午前, 1=午後, 2=夜間）
     * @return 実行結果
     */
    public ReservationResult openReservationPageByVenueId(Long venueId, LocalDate date, int slotIndex) {
        if (!AdjacentRoomConfig.isKaderuRoom(venueId)) {
            return ReservationResult.error("NOT_KADERU_ROOM",
                    "かでる2・7の部屋ではありません: venueId=" + venueId);
        }
        String roomName = AdjacentRoomConfig.getKaderuRoomName(venueId);
        return openReservationPage(roomName, date, slotIndex);
    }

    /**
     * 予約画面遷移の実行結果
     */
    public record ReservationResult(
            boolean success,
            String errorCode,
            String message,
            String roomName,
            String date,
            String timeSlot
    ) {
        public static ReservationResult success(String roomName, String date, String timeSlot) {
            return new ReservationResult(true, null,
                    "申込トレイ画面を開きました。利用目的を入力後「申込み」を押してください。",
                    roomName, date, timeSlot);
        }

        public static ReservationResult error(String errorCode, String message) {
            return new ReservationResult(false, errorCode, message, null, null, null);
        }
    }
}
