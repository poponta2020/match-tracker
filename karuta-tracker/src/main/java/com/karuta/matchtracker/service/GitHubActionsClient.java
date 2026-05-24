package com.karuta.matchtracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GitHub Actions API クライアント。
 *
 * <p>workflow_dispatch でジョブを起動し、その進行状況を取得するための薄いラッパー。
 * 認証は環境変数 {@code GITHUB_PAT}（fine-grained PAT 推奨）を Bearer ヘッダで付与する。
 * リポジトリ指定は環境変数 {@code GITHUB_REPO}（デフォルト {@code poponta2020/match-tracker}）。
 *
 * <p>PAT 未設定時は起動時に警告ログを出し、メソッド呼び出し時に 503 を返す。
 * 起動失敗で全体を止めないため Bean 自体は生成する。
 */
@Component
@Slf4j
public class GitHubActionsClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String ACCEPT_HEADER = "application/vnd.github+json";

    private final String pat;
    private final String repo;
    private final RestClient restClient;

    /** ログ ZIP のダウンロードは S3 リダイレクトを跨ぐので JDK HttpClient を直接使う。 */
    private static final HttpClient LOG_HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GitHubActionsClient(
            @Value("${GITHUB_PAT:}") String pat,
            @Value("${GITHUB_REPO:poponta2020/match-tracker}") String repo,
            RestClient.Builder restClientBuilder) {
        this.pat = pat == null ? "" : pat.trim();
        this.repo = repo;
        this.restClient = restClientBuilder
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", ACCEPT_HEADER)
                .defaultHeader("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .build();
    }

    @PostConstruct
    void warnIfPatMissing() {
        if (pat.isEmpty()) {
            log.warn("GITHUB_PAT is not configured. Kaderu manual sync trigger will return 503 until set.");
        } else {
            log.info("GitHubActionsClient initialized for repo={} (PAT length={})", repo, pat.length());
        }
    }

    /**
     * 指定ワークフローを workflow_dispatch で起動する。
     *
     * @param workflowFileName 例: {@code "sync-kaderu-reservations-manual.yml"}
     * @param ref              ブランチ名（例: {@code "main"}）
     * @param inputs           inputs（例: {@code Map.of("org", "hokudai")}）
     * @throws ResponseStatusException 503: PAT 未設定 / 500: GitHub API 呼び出し失敗
     */
    public void dispatchWorkflow(String workflowFileName, String ref, Map<String, Object> inputs) {
        ensurePatAvailable();
        Map<String, Object> body = Map.of(
                "ref", ref,
                "inputs", inputs == null ? Map.of() : inputs
        );
        try {
            restClient.post()
                    .uri("/repos/{repo}/actions/workflows/{file}/dispatches", repo, workflowFileName)
                    .header("Authorization", "Bearer " + pat)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String bodyText = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("GitHub workflow dispatch failed: status={} body={}", res.getStatusCode(), bodyText);
                        throw new RuntimeException("GitHub Actionsの起動に失敗しました: HTTP "
                                + res.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to dispatch workflow {} on repo {}: {}", workflowFileName, repo, e.getMessage(), e);
            throw new RuntimeException("GitHub Actionsの起動に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 指定ワークフローのうち、{@code createdAfter} 以降に作成された
     * workflow_dispatch イベントの run を新しい順で返す。
     *
     * @param workflowFileName ワークフローファイル名
     * @param createdAfter     これ以降に作成された run のみ返す（dispatch 直後の run_id 解決用）
     * @return 該当 run のリスト（取得失敗時は空リスト）
     */
    public List<WorkflowRun> listRecentRuns(String workflowFileName, Instant createdAfter) {
        ensurePatAvailable();
        String createdFilter = ">=" + DateTimeFormatter.ISO_INSTANT.format(createdAfter);
        try {
            WorkflowRunsPage page = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{repo}/actions/workflows/{file}/runs")
                            .queryParam("event", "workflow_dispatch")
                            .queryParam("created", createdFilter)
                            .queryParam("per_page", "20")
                            .build(repo, workflowFileName))
                    .header("Authorization", "Bearer " + pat)
                    .retrieve()
                    .body(WorkflowRunsPage.class);
            return page == null || page.workflowRuns() == null ? List.of() : page.workflowRuns();
        } catch (Exception e) {
            log.warn("Failed to list workflow runs (workflow={}, createdAfter={}): {}",
                    workflowFileName, createdAfter, e.getMessage());
            return List.of();
        }
    }

    /**
     * 指定 run の最新状態を取得する。
     */
    public Optional<WorkflowRun> getWorkflowRun(long runId) {
        ensurePatAvailable();
        try {
            WorkflowRun run = restClient.get()
                    .uri("/repos/{repo}/actions/runs/{id}", repo, runId)
                    .header("Authorization", "Bearer " + pat)
                    .retrieve()
                    .body(WorkflowRun.class);
            return Optional.ofNullable(run);
        } catch (Exception e) {
            log.warn("Failed to get workflow run {}: {}", runId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 指定 run のログ ZIP をダウンロードし、全 .txt エントリを連結したテキストを返す。
     * 取得や解凍に失敗した場合は {@link Optional#empty()}。
     *
     * <p>サマリー抽出用途のため、失敗しても呼び出し元では「summary なし」として完了通知を出す。
     */
    public Optional<String> fetchWorkflowLogText(long runId) {
        ensurePatAvailable();
        URI url = URI.create(GITHUB_API_BASE + "/repos/" + repo + "/actions/runs/" + runId + "/logs");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url)
                    .header("Authorization", "Bearer " + pat)
                    .header("Accept", ACCEPT_HEADER)
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = LOG_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Workflow log download returned status {} for run {}", response.statusCode(), runId);
                return Optional.empty();
            }
            return Optional.of(extractTextFromZip(response.body()));
        } catch (Exception e) {
            log.warn("Failed to fetch workflow log for run {}: {}", runId, e.getMessage());
            return Optional.empty();
        }
    }

    private void ensurePatAvailable() {
        if (pat.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GITHUB_PAT が未設定のため Kaderu 同期トリガーは利用できません");
        }
    }

    private static String extractTextFromZip(byte[] zipBytes) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".txt")) continue;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                sb.append(out.toString(StandardCharsets.UTF_8));
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowRunsPage(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("workflow_runs") List<WorkflowRun> workflowRuns) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowRun(
            long id,
            String status,
            String conclusion,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("display_title") String displayTitle) {

        /** status が "completed" なら true。それ以外 (queued / in_progress / null) は false。 */
        public boolean isCompleted() {
            return "completed".equalsIgnoreCase(status);
        }

        /** conclusion が "success" なら true。それ以外 (failure / cancelled / timed_out / null) は false。 */
        public boolean isSuccess() {
            return "success".equalsIgnoreCase(conclusion);
        }
    }
}
