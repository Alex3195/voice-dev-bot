package com.alex.voicedevbot.adapter.out.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IntegrationException.Reason;
import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TokenInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** GitLab REST API v4 bilan protokol darajasida — WireMock'dagi soxta GitLab. */
class GitLabHttpApiIntegrationTest {

  private static final AccessToken TOKEN = new AccessToken("glpat-secretToken1234");
  private static final String REPO_JSON =
      """
      {"id": 42, "path_with_namespace": "alex/elt-imzo",
       "web_url": "https://gitlab.example/alex/elt-imzo", "name": "elt-imzo"}
      """;

  @RegisterExtension
  static WireMockExtension gitLab =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final GitLabHttpApi api =
      new GitLabHttpApi(HttpClient.newHttpClient(), Duration.ofSeconds(5));
  private ServerAddress address;
  private ProviderConnection connection;

  @BeforeEach
  void setUp() {
    address = ServerAddress.parse(gitLab.baseUrl());
    connection =
        new ProviderConnection(1, address, TOKEN, TokenInfo.withoutExpiry("alex", Set.of("api")));
  }

  @Test
  void should_read_owner_scopes_and_expiry_when_verifying_token() {
    gitLab.stubFor(get("/api/v4/user").willReturn(okJson("{\"username\": \"alex\"}")));
    gitLab.stubFor(
        get("/api/v4/personal_access_tokens/self")
            .willReturn(
                okJson("{\"scopes\": [\"api\", \"read_user\"], \"expires_at\": \"2027-01-01\"}")));

    TokenInfo info = api.verify(address, TOKEN);

    assertThat(info)
        .isEqualTo(
            TokenInfo.expiring("alex", Set.of("api", "read_user"), LocalDate.of(2027, 1, 1)));
    gitLab.verify(
        getRequestedFor(urlEqualTo("/api/v4/user"))
            .withHeader("PRIVATE-TOKEN", equalTo(TOKEN.value())));
  }

  @Test
  void should_accept_token_without_expiry() {
    gitLab.stubFor(get("/api/v4/user").willReturn(okJson("{\"username\": \"alex\"}")));
    gitLab.stubFor(
        get("/api/v4/personal_access_tokens/self")
            .willReturn(okJson("{\"scopes\": [\"api\"], \"expires_at\": null}")));

    assertThat(api.verify(address, TOKEN))
        .isEqualTo(TokenInfo.withoutExpiry("alex", Set.of("api")));
  }

  @Test
  void should_search_member_repos_with_encoded_query() {
    gitLab.stubFor(
        get(urlPathEqualTo("/api/v4/projects")).willReturn(okJson("[" + REPO_JSON + "]")));

    var repos = api.searchRepos(connection, "elt imzo");

    assertThat(repos)
        .containsExactly(
            new Repo(42, "alex/elt-imzo", URI.create("https://gitlab.example/alex/elt-imzo")));
    gitLab.verify(
        getRequestedFor(
            urlEqualTo(
                "/api/v4/projects?membership=true&simple=true&order_by=last_activity_at"
                    + "&per_page=10&search=elt+imzo")));
  }

  @Test
  void should_find_repo_by_id() {
    gitLab.stubFor(get("/api/v4/projects/42").willReturn(okJson(REPO_JSON)));

    assertThat(api.findRepo(connection, 42).path()).isEqualTo("alex/elt-imzo");
  }

  @Test
  void should_list_personal_namespace_first() {
    gitLab.stubFor(
        get("/api/v4/namespaces?per_page=50")
            .willReturn(
                okJson(
                    """
                    [{"id": 9, "full_path": "akfa/backend", "kind": "group"},
                     {"id": 7, "full_path": "alex", "kind": "user"},
                     {"id": 8, "full_path": "akfa", "kind": "group"}]
                    """)));

    assertThat(api.namespaces(connection))
        .containsExactly(
            new Namespace(7, "alex", true),
            new Namespace(8, "akfa", false),
            new Namespace(9, "akfa/backend", false));
  }

  @Test
  void should_create_private_repo_and_commit_template_files() {
    gitLab.stubFor(post("/api/v4/projects").willReturn(okJson(REPO_JSON)));
    gitLab.stubFor(post("/api/v4/projects/42/repository/commits").willReturn(okJson("{}")));

    Repo repo =
        api.createRepo(
            connection, 7, "elt-imzo", Map.of("docs/roadmap.md", "# R", "CLAUDE.md", "# C"));

    assertThat(repo.id()).isEqualTo(42);
    gitLab.verify(
        postRequestedFor(urlEqualTo("/api/v4/projects"))
            .withRequestBody(
                equalToJson(
                    """
                    {"name": "elt-imzo", "namespace_id": 7, "visibility": "private",
                     "default_branch": "main"}
                    """)));
    gitLab.verify(
        postRequestedFor(urlEqualTo("/api/v4/projects/42/repository/commits"))
            .withRequestBody(
                equalToJson(
                    """
                    {"branch": "main", "commit_message": "${json-unit.any-string}",
                     "actions": [
                       {"action": "create", "file_path": "CLAUDE.md", "content": "# C"},
                       {"action": "create", "file_path": "docs/roadmap.md", "content": "# R"}]}
                    """)));
  }

  @Test
  void should_skip_commit_when_there_are_no_files() {
    gitLab.stubFor(post("/api/v4/projects").willReturn(okJson(REPO_JSON)));

    api.createRepo(connection, 7, "elt-imzo", Map.of());

    gitLab.verify(0, postRequestedFor(urlEqualTo("/api/v4/projects/42/repository/commits")));
  }

  @ParameterizedTest
  @CsvSource({
    "401, '{}', UNAUTHORIZED",
    "403, '{}', FORBIDDEN",
    "404, '{}', NOT_FOUND",
    "409, '{}', CONFLICT",
    "400, '{\"message\": {\"name\": [\"has already been taken\"]}}', CONFLICT",
    "400, '{\"message\": \"bad\"}', UNAVAILABLE",
    "502, '{}', UNAVAILABLE"
  })
  void should_map_http_errors_without_leaking_token(int status, String body, Reason reason) {
    gitLab.stubFor(
        get("/api/v4/projects/42").willReturn(aResponse().withStatus(status).withBody(body)));

    assertThatThrownBy(() -> api.findRepo(connection, 42))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(reason))
        .hasMessage("GET /projects/42 returned HTTP " + status)
        .message()
        .doesNotContain(TOKEN.value());
  }

  @Test
  void should_report_invalid_json_and_unreachable_server_as_unavailable() {
    gitLab.stubFor(get("/api/v4/projects/42").willReturn(aResponse().withBody("<html>")));
    ProviderConnection unreachable =
        new ProviderConnection(
            1,
            ServerAddress.parse("http://127.0.0.1:1"),
            TOKEN,
            TokenInfo.withoutExpiry("alex", Set.of("api")));

    assertThatThrownBy(() -> api.findRepo(connection, 42))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    assertThatThrownBy(() -> api.findRepo(unreachable, 42))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
  }

  private static String issueJson(int iid, String state, String dueDate, int mergeRequests) {
    String due = dueDate == null ? "null" : "\"" + dueDate + "\"";
    return """
        {"iid": %d, "title": "Task %d", "description": null, "state": "%s", "due_date": %s,
         "merge_requests_count": %d,
         "web_url": "https://gitlab.example/alex/elt-imzo/-/issues/%d"}
        """
        .formatted(iid, iid, state, due, mergeRequests, iid);
  }

  @Test
  void should_read_all_issues_page_by_page_up_to_limit() {
    String fullPage =
        IntStream.rangeClosed(1, GitLabHttpApi.MAX_PAGE_SIZE)
            .mapToObj(iid -> issueJson(iid, "opened", null, 0))
            .collect(Collectors.joining(",", "[", "]"));
    String issuesPath =
        "/api/v4/projects/42/issues?scope=all&state=all&order_by=created_at&sort=desc&per_page=100";
    gitLab.stubFor(get(issuesPath + "&page=1").willReturn(okJson(fullPage)));
    gitLab.stubFor(
        get(issuesPath + "&page=2")
            .willReturn(
                okJson(
                    "["
                        + issueJson(101, "closed", "2026-10-01", 2)
                        + ","
                        + issueJson(102, "opened", null, 0)
                        + "]")));

    List<Task> all = api.issues(connection, 42, 500);
    List<Task> limited = api.issues(connection, 42, 101);

    assertThat(all).hasSize(102);
    assertThat(all.get(100))
        .isEqualTo(
            new Task(
                101,
                "Task 101",
                "",
                false,
                Optional.of(LocalDate.of(2026, 10, 1)),
                2,
                URI.create("https://gitlab.example/alex/elt-imzo/-/issues/101")));
    assertThat(limited).hasSize(101);
  }

  @Test
  void should_read_one_issue_and_its_merge_requests() {
    gitLab.stubFor(
        get("/api/v4/projects/42/issues/7").willReturn(okJson(issueJson(7, "opened", null, 1))));
    gitLab.stubFor(
        get("/api/v4/projects/42/issues/7/related_merge_requests")
            .willReturn(
                okJson(
                    """
                    [{"iid": 3, "title": "Fix", "state": "merged", "web_url": "https://mr/3"},
                     {"iid": 4, "title": "Old", "state": "closed", "web_url": "https://mr/4"},
                     {"iid": 5, "title": "Wip", "state": "locked", "web_url": "https://mr/5"}]
                    """)));

    assertThat(api.issue(connection, 42, 7).open()).isTrue();
    assertThat(api.mergeRequests(connection, 42, 7))
        .extracting(MergeRequest::state)
        .containsExactly(
            MergeRequest.State.MERGED, MergeRequest.State.CLOSED, MergeRequest.State.OPENED);
  }

  @Test
  void should_create_issue_with_labels() {
    gitLab.stubFor(
        post("/api/v4/projects/42/issues").willReturn(okJson(issueJson(8, "opened", null, 0))));

    Task created =
        api.createIssue(connection, 42, new NewTask("Login", "Parol"), List.of("ai-task", "bot"));

    assertThat(created.iid()).isEqualTo(8);
    gitLab.verify(
        postRequestedFor(urlEqualTo("/api/v4/projects/42/issues"))
            .withRequestBody(
                equalToJson(
                    "{\"title\": \"Login\", \"description\": \"Parol\","
                        + " \"labels\": \"ai-task,bot\"}")));
  }

  @ParameterizedTest
  @CsvSource({"false, close, closed", "true, reopen, opened"})
  void should_close_or_reopen_issue(boolean open, String event, String state) {
    gitLab.stubFor(
        put("/api/v4/projects/42/issues/7").willReturn(okJson(issueJson(7, state, null, 0))));

    Task task = api.setIssueOpen(connection, 42, 7, open);

    assertThat(task.open()).isEqualTo(open);
    gitLab.verify(
        putRequestedFor(urlEqualTo("/api/v4/projects/42/issues/7"))
            .withRequestBody(equalToJson("{\"state_event\": \"" + event + "\"}")));
  }

  @Test
  void should_list_only_files_of_directory_tree() {
    gitLab.stubFor(
        get("/api/v4/projects/42/repository/tree?recursive=true&path=docs&per_page=100&page=1")
            .willReturn(
                okJson(
                    """
                    [{"path": "docs/specs", "type": "tree"},
                     {"path": "docs/specs/001-login.md", "type": "blob"},
                     {"path": "docs/roadmap.md", "type": "blob"}]
                    """)));
    gitLab.stubFor(
        get("/api/v4/projects/42/repository/tree?recursive=false&per_page=100&page=1")
            .willReturn(okJson("[{\"path\": \"CLAUDE.md\", \"type\": \"blob\"}]")));

    assertThat(api.files(connection, 42, "docs", true))
        .containsExactly("docs/specs/001-login.md", "docs/roadmap.md");
    assertThat(api.files(connection, 42, "", false)).containsExactly("CLAUDE.md");
  }

  @Test
  void should_return_no_files_when_directory_is_missing() {
    gitLab.stubFor(
        get(urlPathEqualTo("/api/v4/projects/42/repository/tree"))
            .willReturn(
                aResponse().withStatus(404).withBody("{\"message\": \"404 Tree Not Found\"}")));

    assertThat(api.files(connection, 42, "docs", true)).isEmpty();
  }

  @Test
  void should_read_raw_file_with_encoded_path() {
    gitLab.stubFor(
        get("/api/v4/projects/42/repository/files/docs%2Fmy%20notes.md/raw")
            .willReturn(aResponse().withBody("# Eslatma\n<b>")));

    assertThat(api.readFile(connection, 42, "docs/my notes.md")).contains("# Eslatma\n<b>");
  }

  @Test
  void should_return_empty_when_file_is_missing_and_fail_on_other_errors() {
    gitLab.stubFor(
        get("/api/v4/projects/42/repository/files/CLAUDE.md/raw")
            .willReturn(aResponse().withStatus(404)));
    gitLab.stubFor(
        get("/api/v4/projects/42/repository/files/README.md/raw")
            .willReturn(aResponse().withStatus(403)));

    assertThat(api.readFile(connection, 42, "CLAUDE.md")).isEmpty();
    assertThatThrownBy(() -> api.readFile(connection, 42, "README.md"))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.FORBIDDEN));
  }

  @Test
  void should_fail_listing_files_on_errors_other_than_not_found() {
    gitLab.stubFor(
        get(urlPathEqualTo("/api/v4/projects/42/repository/tree"))
            .willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> api.files(connection, 42, "", false))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAUTHORIZED));
  }
}
