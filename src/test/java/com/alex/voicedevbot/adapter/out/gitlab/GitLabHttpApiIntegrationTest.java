package com.alex.voicedevbot.adapter.out.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.GitLabException;
import com.alex.voicedevbot.application.port.out.GitLabException.Reason;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabNamespace;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** GitLab REST API v4 bilan protokol darajasida — WireMock'dagi soxta GitLab. */
class GitLabHttpApiIntegrationTest {

  private static final GitLabToken TOKEN = new GitLabToken("glpat-secretToken1234");
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
  private GitLabAddress address;
  private GitLabConnection connection;

  @BeforeEach
  void setUp() {
    address = GitLabAddress.parse(gitLab.baseUrl());
    connection =
        new GitLabConnection(1, address, TOKEN, TokenInfo.withoutExpiry("alex", Set.of("api")));
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
            new GitLabRepo(
                42, "alex/elt-imzo", URI.create("https://gitlab.example/alex/elt-imzo")));
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
            new GitLabNamespace(7, "alex", true),
            new GitLabNamespace(8, "akfa", false),
            new GitLabNamespace(9, "akfa/backend", false));
  }

  @Test
  void should_create_private_repo_and_commit_template_files() {
    gitLab.stubFor(post("/api/v4/projects").willReturn(okJson(REPO_JSON)));
    gitLab.stubFor(post("/api/v4/projects/42/repository/commits").willReturn(okJson("{}")));

    GitLabRepo repo =
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
            GitLabException.class, e -> assertThat(e.reason()).isEqualTo(reason))
        .hasMessage("GET /projects/42 returned HTTP " + status)
        .message()
        .doesNotContain(TOKEN.value());
  }

  @Test
  void should_report_invalid_json_and_unreachable_server_as_unavailable() {
    gitLab.stubFor(get("/api/v4/projects/42").willReturn(aResponse().withBody("<html>")));
    GitLabConnection unreachable =
        new GitLabConnection(
            1,
            GitLabAddress.parse("http://127.0.0.1:1"),
            TOKEN,
            TokenInfo.withoutExpiry("alex", Set.of("api")));

    assertThatThrownBy(() -> api.findRepo(connection, 42))
        .isInstanceOfSatisfying(
            GitLabException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    assertThatThrownBy(() -> api.findRepo(unreachable, 42))
        .isInstanceOfSatisfying(
            GitLabException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
  }
}
