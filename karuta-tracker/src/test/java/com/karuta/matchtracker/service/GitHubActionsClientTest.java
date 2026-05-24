package com.karuta.matchtracker.service;

import com.karuta.matchtracker.service.GitHubActionsClient.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GitHubActionsClient の URL 組み立て・ヘッダ・エラー処理を検証するテスト。
 *
 * <p>MockRestServiceServer を RestClient.Builder にバインドし、実際に生成されるリクエスト URL が
 * GitHub REST API 仕様の {@code /repos/{owner}/{repo}/...} 形式になっていることを確認する。
 * 単一パス変数で {@code owner/repo} を渡すと {@code %2F} エンコードされて 404 になるため、
 * リグレッション防止の意味でも URL 検証は必須。
 */
@DisplayName("GitHubActionsClient 単体テスト (URL 組み立て / エラー)")
class GitHubActionsClientTest {

    private static final String OWNER = "poponta2020";
    private static final String REPO_NAME = "match-tracker";
    private static final String REPO = OWNER + "/" + REPO_NAME;
    private static final String PAT = "test-pat";
    private static final String WORKFLOW = "sync-kaderu-reservations-manual.yml";

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private GitHubActionsClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubActionsClient(PAT, REPO, builder);
    }

    @Test
    @DisplayName("dispatchWorkflow: 正規の /repos/{owner}/{repo}/... 形式で POST し Bearer / inputs を送る")
    void dispatchWorkflow_buildsCorrectUrlAndBody() {
        mockServer.expect(requestTo("https://api.github.com/repos/poponta2020/match-tracker"
                        + "/actions/workflows/" + WORKFLOW + "/dispatches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + PAT))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andExpect(jsonPath("$.ref").value("main"))
                .andExpect(jsonPath("$.inputs.org").value("hokudai"))
                .andExpect(jsonPath("$.inputs.eventId").value("42"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.dispatchWorkflow(WORKFLOW, "main", Map.of("org", "hokudai", "eventId", "42"));

        mockServer.verify();
    }

    @Test
    @DisplayName("dispatchWorkflow: 422 などのエラーは RuntimeException に包んで投げる")
    void dispatchWorkflow_wrapsErrorResponses() {
        mockServer.expect(requestTo("https://api.github.com/repos/poponta2020/match-tracker"
                        + "/actions/workflows/" + WORKFLOW + "/dispatches"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"invalid input\"}"));

        assertThatThrownBy(() ->
                client.dispatchWorkflow(WORKFLOW, "main", Map.of("org", "hokudai", "eventId", "42")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GitHub Actions");
    }

    @Test
    @DisplayName("listRecentRuns: 2変数パスでクエリパラメータ (event/created/per_page) を付けて GET")
    void listRecentRuns_buildsCorrectUrlAndQuery() {
        String body = "{\"total_count\":1,\"workflow_runs\":[{\"id\":987,\"status\":\"queued\","
                + "\"conclusion\":null,\"created_at\":\"2026-05-24T15:00:00Z\","
                + "\"html_url\":\"https://github.com/x\",\"display_title\":\"Kaderu sync [event:42] hokudai\"}]}";
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.github.com/repos/poponta2020/match-tracker"
                                + "/actions/workflows/" + WORKFLOW + "/runs")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PAT))
                .andExpect(queryParam("event", "workflow_dispatch"))
                .andExpect(queryParam("per_page", "20"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<WorkflowRun> runs = client.listRecentRuns(WORKFLOW, Instant.parse("2026-05-24T14:55:00Z"));

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).id()).isEqualTo(987L);
        assertThat(runs.get(0).displayTitle()).contains("[event:42]");
        mockServer.verify();
    }

    @Test
    @DisplayName("getWorkflowRun: 2変数パスで runId まで含めた URL を GET")
    void getWorkflowRun_buildsCorrectUrl() {
        String body = "{\"id\":123,\"status\":\"completed\",\"conclusion\":\"success\","
                + "\"created_at\":\"2026-05-24T15:00:00Z\",\"html_url\":\"https://github.com/x\","
                + "\"display_title\":\"Kaderu sync [event:42] hokudai\"}";
        mockServer.expect(requestTo("https://api.github.com/repos/poponta2020/match-tracker/actions/runs/123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PAT))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<WorkflowRun> run = client.getWorkflowRun(123L);

        assertThat(run).isPresent();
        assertThat(run.get().isCompleted()).isTrue();
        assertThat(run.get().isSuccess()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("PAT 未設定の Bean に対する呼び出しは ResponseStatusException(503)")
    void noPat_throws503() {
        RestClient.Builder builder2 = RestClient.builder();
        MockRestServiceServer.bindTo(builder2).build();
        GitHubActionsClient noPatClient = new GitHubActionsClient("", REPO, builder2);

        assertThatThrownBy(() -> noPatClient.dispatchWorkflow(WORKFLOW, "main", Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(503);
    }

    @Test
    @DisplayName("GITHUB_REPO が 'owner/repo' 形式でなければ起動時に IllegalArgumentException")
    void invalidRepoConfig_throwsAtConstruction() {
        assertThatThrownBy(() -> new GitHubActionsClient(PAT, "no-slash-here", RestClient.builder()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GITHUB_REPO");
        assertThatThrownBy(() -> new GitHubActionsClient(PAT, "/missing-owner", RestClient.builder()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitHubActionsClient(PAT, "missing-repo/", RestClient.builder()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
