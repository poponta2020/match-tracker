package com.karuta.matchtracker.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * メモリ診断ロガー（Issue #953）。
 *
 * <p>本番（Render free, コンテナ上限 512Mi）で約5時間ごとに発生しているコンテナレベルの OOM kill の
 * 真因（ヒープ外メモリのどの領域が増加しているか）を切り分けるための観測専用コンポーネント。</p>
 *
 * <p>ヒープは {@code -Xmx200m} に制限済みのため、512Mi 超過の犯人はヒープ外
 * （メタスペース / コードキャッシュ / NIO ダイレクトバッファ / スレッドスタック / その他ネイティブ）の
 * いずれか。本ロガーは各領域と実 RSS を定期的に1行で出力する。</p>
 *
 * <p>Render free はシェル（{@code jcmd}）が使えないため、{@link ManagementFactory} の MXBean 群・
 * Linux の {@code /proc/self/status}（実 RSS）に加え、NMT サマリは {@code DiagnosticCommandMBean} 経由で
 * プログラム的に取得してアプリログへ残す方式を採る（既存の Render ログ取得ツールで読み出せる）。</p>
 *
 * <p>出力間隔は Densuke 同期スケジューラ（{@code DensukeSyncScheduler}, 5分間隔）と揃えており、
 * 同期サイクル直後にメモリが跳ねるか（= Densuke 由来か）を相関で判断できるようにしている。</p>
 *
 * <p>ログのマーカーは {@code MEM-DIAG}。例: {@code MEM-DIAG rss=412MB heap=120/200MB ...}</p>
 */
@Slf4j
@Component
public class MemoryDiagnosticsLogger {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    /**
     * メモリフットプリントを1行で出力する。観測専用のため例外は握りつぶし、本番動作に影響を与えない。
     */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void logMemoryFootprint() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

            long metaspaceUsed = -1L;
            long codeCacheUsed = 0L;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                MemoryUsage usage = pool.getUsage();
                if (usage == null) {
                    continue;
                }
                String name = pool.getName();
                if ("Metaspace".equals(name)) {
                    metaspaceUsed = usage.getUsed();
                } else if (name != null && name.contains("Code")) {
                    // "CodeHeap 'non-nmethods'" 等を合算
                    codeCacheUsed += usage.getUsed();
                }
            }

            long directUsed = 0L;
            long directCapacity = 0L;
            long mappedUsed = 0L;
            List<BufferPoolMXBean> bufferPools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
            for (BufferPoolMXBean bp : bufferPools) {
                if ("direct".equals(bp.getName())) {
                    directUsed = bp.getMemoryUsed();
                    directCapacity = bp.getTotalCapacity();
                } else if ("mapped".equals(bp.getName())) {
                    mappedUsed = bp.getMemoryUsed();
                }
            }

            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            long rssMb = readResidentSetSizeMb();

            log.info("MEM-DIAG rss={}MB heap={}/{}MB nonHeap={}MB metaspace={}MB codeCache={}MB "
                            + "directBuf={}/{}MB mapped={}MB threads={}",
                    rssMb,
                    heap.getUsed() / BYTES_PER_MB, heap.getMax() / BYTES_PER_MB,
                    nonHeap.getUsed() / BYTES_PER_MB,
                    metaspaceUsed < 0 ? -1 : metaspaceUsed / BYTES_PER_MB,
                    codeCacheUsed / BYTES_PER_MB,
                    directUsed / BYTES_PER_MB, directCapacity / BYTES_PER_MB,
                    mappedUsed / BYTES_PER_MB,
                    threadCount);
        } catch (Exception e) {
            // 観測専用。失敗してもアプリ本体に影響させない。
            log.debug("MEM-DIAG の出力に失敗しました: {}", e.getMessage());
        }
    }

    /**
     * NMT（Native Memory Tracking）サマリを {@code DiagnosticCommandMBean}（{@code jcmd VM.native_memory summary} 相当）
     * から取得し、改行を {@code " | "} に畳んで1行で出力する（Issue #953 の根本原因切り分け）。
     *
     * <p>Render free はシェルが無く {@code jcmd} を実行できないため、プログラム的に同等情報を得る。
     * 出力の各カテゴリ committed と Total committed を MEM-DIAG の実 RSS と突き合わせ、
     * 「JVM が本当に必要としている分」と「OS に返らず glibc が保持している分（RSS − committed）」を定量化する。</p>
     *
     * <p>NMT 有効化（{@code -XX:NativeMemoryTracking=summary}）が前提。未有効時はその旨が返るだけで害はない。
     * 30分間隔。観測専用のため例外は握りつぶす。マーカーは {@code NMT-DUMP}。</p>
     */
    @Scheduled(fixedDelay = 1_800_000L, initialDelay = 180_000L)
    public void logNativeMemorySummary() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName diagnostic = new ObjectName("com.sun.management:type=DiagnosticCommand");
            Object result = server.invoke(
                    diagnostic,
                    "vmNativeMemory",
                    new Object[] { new String[] { "summary" } },
                    new String[] { String[].class.getName() });
            String summary = String.valueOf(result).replace("\r", " ").replace("\n", " | ");
            log.info("NMT-DUMP {}", summary);
        }
        catch (Exception e) {
            // 観測専用。失敗してもアプリ本体に影響させない。
            log.warn("NMT-DUMP の出力に失敗しました: {}", e.getMessage());
        }
    }

    /**
     * Linux の {@code /proc/self/status} から VmRSS（実常駐メモリ）を MB で読む。
     * Render コンテナの OOM killer はこの RSS を監視するため、最も直接的な指標。
     * 取得できない環境（Windows のローカル開発等）では -1 を返す。
     */
    private long readResidentSetSizeMb() {
        try {
            Path statusPath = Path.of("/proc/self/status");
            if (!Files.isReadable(statusPath)) {
                return -1L;
            }
            for (String line : Files.readAllLines(statusPath)) {
                if (line.startsWith("VmRSS:")) {
                    // 形式: "VmRSS:\t  123456 kB"
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]) / 1024L;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("RSS の取得に失敗しました: {}", e.getMessage());
        }
        return -1L;
    }
}
