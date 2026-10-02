package com.alex.voicedevbot.adapter.out.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IntegrationException.Reason;
import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.Provider;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** GitHub REST API bilan protokol darajasida — WireMock'dagi soxta GitHub. */
class GitHubHttpApiIntegrationTest {

  private static final AccessToken TOKEN = new AccessToken("github_pat_secretToken1234");
  private static final Repo REPO =
      new Repo(7, "alex/elt-imzo", URI.create("https://github.com/alex/elt-imzo"));
  private static final String REPO_JSON =
      """
      {"id": 7, "full_name": "alex/elt-imzo", "html_url": "https://github.com/alex/elt-imzo",
       "default_branch": "main"}
      """;
  private static final String USER_JSON = "{\"id\": 1, \"login\": \"alex\"}";

  @RegisterExtension
  static WireMockExtension gitHub =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final GitHubHttpApi api =
      new GitHubHttpApi(
          HttpClient.newHttpClient(), Duration.ofSeconds(5), URI.create(gitHub.baseUrl() + "/"));
  private final ProviderConnection connection =
      new ProviderConnection(
          1,
          Provider.GITHUB,
          ServerAddress.GITHUB_COM,
          TOKEN,
          TokenInfo.withoutExpiry("alex", Set.of()));

  private static String issueJson(int number, String state, boolean pull) {
    return """
        {"number": %d, "title": "Task %d", "body": null, "state": "%s",
         "html_url": "https://github.com/alex/elt-imzo/issues/%d"%s}
        """
        .formatted(
            number, number, state, number, pull ? ", \"pull_request\": {\"url\": \"x\"}" : "");
  }

  @Test
  void should_verify_fine_grained_token_with_expiry_and_send_api_headers() {
    gitHub.stubFor(
        get("/user")
            .willReturn(
                okJson(USER_JSON)
                    .withHeader(
                        "github-authentication-token-expiration", "2027-01-01 10:00:00 +0500")));

    TokenInfo info = api.verify(ServerAddress.GITHUB_COM, TOKEN);

    assertThat(info).isEqualTo(TokenInfo.expiring("alex", Set.of(), LocalDate.of(2027, 1, 1)));
    gitHub.verify(
        getRequestedFor(urlEqualTo("/user"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN.value()))
            .withHeader("X-GitHub-Api-Version", equalTo(GitHubHttpApi.API_VERSION))
            .withHeader("Accept", equalTo("application/vnd.github+json")));
  }

  @Test
  void should_read_classic_token_scopes_and_require_repo() {
    gitHub.stubFor(
        get("/user").willReturn(okJson(USER_JSON).withHeader("X-OAuth-Scopes", "repo, read:org")));

    assertThat(api.verify(ServerAddress.GITHUB_COM, TOKEN))
        .isEqualTo(TokenInfo.withoutExpiry("alex", Set.of("repo", "read:org")));

    gitHub.stubFor(
        get("/user").willReturn(okJson(USER_JSON).withHeader("X-OAuth-Scopes", "read:user")));
    assertThatThrownBy(() -> api.verify(ServerAddress.GITHUB_COM, TOKEN))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.MISSING_SCOPE));
  }

  @Test
  void should_find_repo_by_owner_and_name() {
    gitHub.stubFor(get(urlEqualTo("/repos/alex/elt-imzo")).willReturn(okJson(REPO_JSON)));

    assertThat(api.findRepo(connection, "alex/elt-imzo")).isEqualTo(REPO);
  }

  @Test
  void should_filter_recent_repos_by_name() {
    gitHub.stubFor(
        get(urlPathEqualTo("/user/repos"))
            .willReturn(
                okJson(
                    """
                    [{"id": 7, "full_name": "alex/elt-imzo", "html_url": "https://github.com/alex/elt-imzo"},
                     {"id": 8, "full_name": "akfa/finbank", "html_url": "https://github.com/akfa/finbank"}]
                    """)));

    assertThat(api.searchRepos(connection, " ELT ")).containsExactly(REPO);
    assertThat(api.searchRepos(connection, "")).hasSize(2);
  }

  @Test
  void should_find_repo_by_id_and_list_personal_namespace_first() {
    gitHub.stubFor(get("/repositories/7").willReturn(okJson(REPO_JSON)));
    gitHub.stubFor(get("/user").willReturn(okJson(USER_JSON)));
    gitHub.stubFor(
        get(urlPathEqualTo("/user/orgs"))
            .willReturn(okJson("[{\"id\": 30, \"login\": \"akfa\"}]")));

    assertThat(api.findRepo(connection, 7)).isEqualTo(REPO);
    assertThat(api.namespaces(connection))
        .containsExactly(new Namespace(1, "alex", true), new Namespace(30, "akfa", false));
  }

  @Test
  void should_create_private_repo_and_commit_template_files_at_once() {
    gitHub.stubFor(get("/user").willReturn(okJson(USER_JSON)));
    gitHub.stubFor(get(urlPathEqualTo("/user/orgs")).willReturn(okJson("[]")));
    gitHub.stubFor(post("/user/repos").willReturn(okJson(REPO_JSON)));
    String git = "/repos/alex/elt-imzo/git";
    gitHub.stubFor(
        get(git + "/ref/heads/main").willReturn(okJson("{\"object\": {\"sha\": \"c1\"}}")));
    gitHub.stubFor(get(git + "/commits/c1").willReturn(okJson("{\"tree\": {\"sha\": \"t1\"}}")));
    gitHub.stubFor(post(git + "/trees").willReturn(okJson("{\"sha\": \"t2\"}")));
    gitHub.stubFor(post(git + "/commits").willReturn(okJson("{\"sha\": \"c2\"}")));
    gitHub.stubFor(patch(urlEqualTo(git + "/refs/heads/main")).willReturn(okJson("{}")));

    Repo created =
        api.createRepo(
            connection, 1, "elt-imzo", Map.of("CLAUDE.md", "# ELT", "docs/roadmap.md", "# R"));

    assertThat(created).isEqualTo(REPO);
    gitHub.verify(
        postRequestedFor(urlEqualTo("/user/repos"))
            .withRequestBody(
                equalToJson("{\"name\": \"elt-imzo\", \"private\": true, \"auto_init\": true}")));
    gitHub.verify(
        postRequestedFor(urlEqualTo(git + "/trees"))
            .withRequestBody(
                equalToJson(
                    """
                    {"base_tree": "t1", "tree": [
                      {"path": "CLAUDE.md", "mode": "100644", "type": "blob", "content": "# ELT"},
                      {"path": "docs/roadmap.md", "mode": "100644", "type": "blob", "content": "# R"}]}
                    """)));
    gitHub.verify(
        postRequestedFor(urlEqualTo(git + "/commits"))
            .withRequestBody(
                equalToJson(
                    "{\"message\": \""
                        + GitHubHttpApi.INITIAL_COMMIT
                        + "\", \"tree\": \"t2\", \"parents\": [\"c1\"]}")));
    gitHub.verify(
        patchRequestedFor(urlEqualTo(git + "/refs/heads/main"))
            .withRequestBody(equalToJson("{\"sha\": \"c2\"}")));
  }

  @Test
  void should_create_repo_in_organization_without_files() {
    gitHub.stubFor(get("/user").willReturn(okJson(USER_JSON)));
    gitHub.stubFor(
        get(urlPathEqualTo("/user/orgs"))
            .willReturn(okJson("[{\"id\": 30, \"login\": \"akfa\"}]")));
    gitHub.stubFor(post("/orgs/akfa/repos").willReturn(okJson(REPO_JSON)));

    api.createRepo(connection, 30, "elt-imzo", Map.of());

    gitHub.verify(
        postRequestedFor(urlEqualTo("/orgs/akfa/repos"))
            .withRequestBody(equalToJson("{\"auto_init\": false}", true, true)));
    gitHub.verify(0, getRequestedFor(urlPathMatching("/repos/.*")));
  }

  @Test
  void should_report_taken_name_and_unknown_owner() {
    gitHub.stubFor(get("/user").willReturn(okJson(USER_JSON)));
    gitHub.stubFor(get(urlPathEqualTo("/user/orgs")).willReturn(okJson("[]")));
    gitHub.stubFor(
        post("/user/repos")
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withBody("{\"errors\": [{\"message\": \"name already exists\"}]}")));

    assertThatThrownBy(() -> api.createRepo(connection, 1, "elt-imzo", Map.of()))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.CONFLICT));
    assertThatThrownBy(() -> api.createRepo(connection, 99, "elt-imzo", Map.of()))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
  }

  @Test
  void should_list_files_of_directory_and_of_whole_tree() {
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/contents")
            .willReturn(
                okJson(
                    """
                    [{"type": "file", "path": "CLAUDE.md"}, {"type": "dir", "path": "docs"}]
                    """)));
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/git/trees/HEAD?recursive=1")
            .willReturn(
                okJson(
                    """
                    {"tree": [{"type": "tree", "path": "docs"},
                              {"type": "blob", "path": "docs/roadmap.md"},
                              {"type": "blob", "path": "docs-old.md"},
                              {"type": "blob", "path": "src/Main.java"}]}
                    """)));

    assertThat(api.files(connection, REPO, "", false)).containsExactly("CLAUDE.md");
    assertThat(api.files(connection, REPO, "docs", true)).containsExactly("docs/roadmap.md");
  }

  @ParameterizedTest
  @CsvSource({"404, true", "409, true", "401, false"})
  void should_treat_missing_or_empty_repo_tree_as_no_files(int status, boolean empty) {
    gitHub.stubFor(
        get(urlPathMatching("/repos/alex/elt-imzo/git/trees/.*"))
            .willReturn(aResponse().withStatus(status).withBody("{}")));

    if (empty) {
      assertThat(api.files(connection, REPO, "docs", true)).isEmpty();
    } else {
      assertThatThrownBy(() -> api.files(connection, REPO, "docs", true))
          .isInstanceOf(IntegrationException.class);
    }
  }

  @Test
  void should_read_raw_file_with_encoded_path_or_return_empty() {
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/contents/docs/my%20notes.md")
            .withHeader("Accept", equalTo("application/vnd.github.raw+json"))
            .willReturn(aResponse().withBody("# Eslatma\n<b>")));
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/contents/README.md").willReturn(aResponse().withStatus(404)));
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/contents/CLAUDE.md").willReturn(aResponse().withStatus(403)));

    assertThat(api.readFile(connection, REPO, "docs/my notes.md")).contains("# Eslatma\n<b>");
    assertThat(api.readFile(connection, REPO, "README.md")).isEmpty();
    assertThatThrownBy(() -> api.readFile(connection, REPO, "CLAUDE.md"))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.FORBIDDEN));
  }

  @Test
  void should_list_issues_without_pull_requests_marking_ones_linked_to_pull() {
    String fullPage =
        IntStream.rangeClosed(1, GitHubHttpApi.MAX_PAGE_SIZE)
            .mapToObj(number -> issueJson(number, "open", number % 2 == 0))
            .collect(Collectors.joining(",", "[", "]"));
    String issues =
        "/repos/alex/elt-imzo/issues?state=all&sort=created&direction=desc&per_page=100";
    gitHub.stubFor(get(issues + "&page=1").willReturn(okJson(fullPage)));
    gitHub.stubFor(
        get(issues + "&page=2").willReturn(okJson("[" + issueJson(101, "closed", false) + "]")));
    gitHub.stubFor(
        get(urlPathEqualTo("/search/issues"))
            .withQueryParam("q", equalTo("repo:alex/elt-imzo is:issue linked:pr"))
            .willReturn(okJson("{\"items\": [{\"number\": 101}]}")));

    List<Task> all = api.issues(connection, REPO, 500);
    List<Task> limited = api.issues(connection, REPO, 3);

    assertThat(all).hasSize(51);
    assertThat(all.getLast())
        .isEqualTo(
            new Task(
                101,
                "Task 101",
                "",
                false,
                Optional.empty(),
                1,
                URI.create("https://github.com/alex/elt-imzo/issues/101")));
    assertThat(limited).extracting(Task::iid).containsExactly(1L, 3L, 5L);
  }

  @Test
  void should_still_list_issues_when_search_is_rate_limited() {
    gitHub.stubFor(
        get(urlPathEqualTo("/repos/alex/elt-imzo/issues"))
            .willReturn(okJson("[" + issueJson(1, "open", false) + "]")));
    gitHub.stubFor(get(urlPathEqualTo("/search/issues")).willReturn(aResponse().withStatus(403)));

    assertThat(api.issues(connection, REPO, 10)).extracting(Task::mergeRequests).containsExactly(0);
  }

  @Test
  void should_take_pull_requests_of_issue_from_timeline() {
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/issues/3").willReturn(okJson(issueJson(3, "open", false))));
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/issues/3/timeline?per_page=100")
            .willReturn(
                okJson(
                    """
                    [{"event": "labeled"},
                     {"event": "cross-referenced", "source": {"issue": {"number": 9, "title": "Old",
                       "state": "closed", "html_url": "https://pr/9",
                       "pull_request": {"merged_at": null}}}},
                     {"event": "cross-referenced", "source": {"issue": {"number": 10, "title": "Fix",
                       "state": "closed", "html_url": "https://pr/10",
                       "pull_request": {"merged_at": "2026-10-01T10:00:00Z"}}}},
                     {"event": "cross-referenced", "source": {"issue": {"number": 11, "title": "Wip",
                       "state": "open", "html_url": "https://pr/11", "pull_request": {}}}},
                     {"event": "cross-referenced", "source": {"issue": {"number": 11, "title": "Wip",
                       "state": "open", "html_url": "https://pr/11", "pull_request": {}}}},
                     {"event": "cross-referenced", "source": {"issue": {"number": 12,
                       "title": "Boshqa issue", "state": "open", "html_url": "https://i/12"}}}]
                    """)));

    Task task = api.issue(connection, REPO, 3);

    assertThat(task.mergeRequests()).isEqualTo(3);
    assertThat(api.mergeRequests(connection, REPO, 3))
        .extracting(MergeRequest::iid, MergeRequest::state)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(9L, MergeRequest.State.CLOSED),
            org.assertj.core.groups.Tuple.tuple(10L, MergeRequest.State.MERGED),
            org.assertj.core.groups.Tuple.tuple(11L, MergeRequest.State.OPENED));
  }

  @Test
  void should_create_issue_with_labels_and_close_it() {
    gitHub.stubFor(
        post("/repos/alex/elt-imzo/issues").willReturn(okJson(issueJson(8, "open", false))));
    gitHub.stubFor(
        patch(urlEqualTo("/repos/alex/elt-imzo/issues/8"))
            .willReturn(okJson(issueJson(8, "closed", false))));
    gitHub.stubFor(
        get("/repos/alex/elt-imzo/issues/8/timeline?per_page=100").willReturn(okJson("[]")));

    Task created =
        api.createIssue(connection, REPO, new NewTask("Login", "Parol"), List.of("ai-task"));
    Task closed = api.setIssueOpen(connection, REPO, 8, false);

    assertThat(created.open()).isTrue();
    assertThat(closed.open()).isFalse();
    gitHub.verify(
        postRequestedFor(urlEqualTo("/repos/alex/elt-imzo/issues"))
            .withRequestBody(
                equalToJson(
                    "{\"title\": \"Login\", \"body\": \"Parol\", \"labels\": [\"ai-task\"]}")));
    gitHub.verify(
        patchRequestedFor(urlEqualTo("/repos/alex/elt-imzo/issues/8"))
            .withRequestBody(equalToJson("{\"state\": \"closed\"}")));
  }

  @ParameterizedTest
  @CsvSource({
    "401, '', UNAUTHORIZED",
    "403, '', FORBIDDEN",
    "404, '', NOT_FOUND",
    "422, 'Validation Failed', UNAVAILABLE",
    "500, '', UNAVAILABLE"
  })
  void should_map_http_errors_without_leaking_token(int status, String body, Reason reason) {
    gitHub.stubFor(
        get("/repositories/7").willReturn(aResponse().withStatus(status).withBody(body)));

    assertThatThrownBy(() -> api.findRepo(connection, 7))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            e -> {
              assertThat(e.reason()).isEqualTo(reason);
              assertThat(e.getMessage()).doesNotContain(TOKEN.value());
            });
  }

  @Test
  void should_report_invalid_json_and_unreachable_server_as_unavailable() {
    gitHub.stubFor(get("/repositories/7").willReturn(aResponse().withBody("<html>")));
    GitHubHttpApi unreachable =
        new GitHubHttpApi(
            HttpClient.newHttpClient(), Duration.ofSeconds(1), URI.create("http://127.0.0.1:9"));

    assertThatThrownBy(() -> api.findRepo(connection, 7))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    assertThatThrownBy(() -> unreachable.findRepo(connection, 7))
        .isInstanceOfSatisfying(
            IntegrationException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
  }

  @Test
  void should_use_cloud_api_for_github_com_and_api_v3_for_other_server() {
    assertThat(api.api(ServerAddress.GITHUB_COM, "/user"))
        .isEqualTo(URI.create(gitHub.baseUrl() + "/user"));
    assertThat(api.api(ServerAddress.parse("git.firma.uz"), "/user"))
        .isEqualTo(URI.create("https://git.firma.uz/api/v3/user"));
  }
}
