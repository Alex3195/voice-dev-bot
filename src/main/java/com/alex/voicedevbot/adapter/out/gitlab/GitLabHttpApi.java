package com.alex.voicedevbot.adapter.out.gitlab;

import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.application.port.out.GitLabException;
import com.alex.voicedevbot.application.port.out.GitLabException.Reason;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabNamespace;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * GitLab REST API v4 ({@code PRIVATE-TOKEN} sarlavhasi bilan). gitlab.com va self-hosted uchun bir
 * xil — faqat manzil farq qiladi. Token hech qachon xato xabariga tushmaydi.
 */
public class GitLabHttpApi implements GitLabApi {

  static final int PAGE_SIZE = 10;
  static final String INITIAL_BRANCH = "main";
  static final String INITIAL_COMMIT = "chore: voice-dev-bot agent rules and docs structure";

  private static final int HTTP_OK_MIN = 200;
  private static final int HTTP_OK_MAX = 299;
  private static final int HTTP_UNAUTHORIZED = 401;
  private static final int HTTP_FORBIDDEN = 403;
  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_BAD_REQUEST = 400;
  private static final int HTTP_CONFLICT = 409;

  private final HttpClient httpClient;
  private final Duration timeout;
  private final JsonMapper json = JsonMapper.builder().build();

  public GitLabHttpApi(HttpClient httpClient, Duration timeout) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  @Override
  public TokenInfo verify(GitLabAddress address, GitLabToken token) {
    String owner = get(address, token, "/user").path("username").asString();
    JsonNode self = get(address, token, "/personal_access_tokens/self");
    Set<String> scopes = new HashSet<>();
    self.path("scopes").forEach(scope -> scopes.add(scope.asString()));
    String expiresAt = self.path("expires_at").asString("");
    return expiresAt.isEmpty()
        ? TokenInfo.withoutExpiry(owner, scopes)
        : TokenInfo.expiring(owner, scopes, LocalDate.parse(expiresAt));
  }

  @Override
  public List<GitLabRepo> searchRepos(GitLabConnection connection, String query) {
    String search = query.isBlank() ? "" : "&search=" + encode(query);
    JsonNode repos =
        get(
            connection,
            "/projects?membership=true&simple=true&order_by=last_activity_at&per_page="
                + PAGE_SIZE
                + search);
    List<GitLabRepo> result = new ArrayList<>();
    repos.forEach(repo -> result.add(repoOf(repo)));
    return result;
  }

  @Override
  public GitLabRepo findRepo(GitLabConnection connection, long repoId) {
    return repoOf(get(connection, "/projects/" + repoId));
  }

  @Override
  public List<GitLabNamespace> namespaces(GitLabConnection connection) {
    JsonNode namespaces = get(connection, "/namespaces?per_page=50");
    List<GitLabNamespace> result = new ArrayList<>();
    namespaces.forEach(
        namespace ->
            result.add(
                new GitLabNamespace(
                    namespace.path("id").asLong(),
                    namespace.path("full_path").asString(),
                    "user".equals(namespace.path("kind").asString()))));
    result.sort(
        Comparator.comparing((GitLabNamespace namespace) -> !namespace.personal())
            .thenComparing(GitLabNamespace::path));
    return result;
  }

  @Override
  public GitLabRepo createRepo(
      GitLabConnection connection, long namespaceId, String name, Map<String, String> files) {
    ObjectNode project = json.createObjectNode();
    project.put("name", name);
    project.put("namespace_id", namespaceId);
    project.put("visibility", "private");
    project.put("default_branch", INITIAL_BRANCH);
    GitLabRepo repo = repoOf(post(connection, "/projects", project));
    if (!files.isEmpty()) {
      post(connection, "/projects/" + repo.id() + "/repository/commits", commitOf(files));
    }
    return repo;
  }

  private ObjectNode commitOf(Map<String, String> files) {
    ObjectNode commit = json.createObjectNode();
    commit.put("branch", INITIAL_BRANCH);
    commit.put("commit_message", INITIAL_COMMIT);
    ArrayNode actions = commit.putArray("actions");
    files.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            file -> {
              ObjectNode action = actions.addObject();
              action.put("action", "create");
              action.put("file_path", file.getKey());
              action.put("content", file.getValue());
            });
    return commit;
  }

  private static GitLabRepo repoOf(JsonNode repo) {
    return new GitLabRepo(
        repo.path("id").asLong(),
        repo.path("path_with_namespace").asString(),
        URI.create(repo.path("web_url").asString()));
  }

  private JsonNode get(GitLabConnection connection, String path) {
    return get(connection.address(), connection.token(), path);
  }

  private JsonNode get(GitLabAddress address, GitLabToken token, String path) {
    return send(request(address, token, path).GET().build(), "GET " + path);
  }

  private JsonNode post(GitLabConnection connection, String path, JsonNode body) {
    HttpRequest request =
        request(connection.address(), connection.token(), path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return send(request, "POST " + path);
  }

  private HttpRequest.Builder request(GitLabAddress address, GitLabToken token, String path) {
    return HttpRequest.newBuilder(address.api(path))
        .timeout(timeout)
        .header("PRIVATE-TOKEN", token.value())
        .header("Accept", "application/json");
  }

  /**
   * @param description xato xabari uchun ({@code GET /user}) — so'rov manzilisiz, unda sir yo'q
   */
  private JsonNode send(HttpRequest request, String description) {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new GitLabException(Reason.UNAVAILABLE, description + " failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GitLabException(Reason.UNAVAILABLE, description + " interrupted", e);
    }
    int status = response.statusCode();
    if (status < HTTP_OK_MIN || status > HTTP_OK_MAX) {
      throw new GitLabException(
          reasonOf(status, response.body()), description + " returned HTTP " + status, null);
    }
    try {
      return json.readTree(response.body());
    } catch (JacksonException e) {
      throw new GitLabException(Reason.UNAVAILABLE, description + " returned invalid JSON", e);
    }
  }

  /** GitLab band nomni 400 "has already been taken" bilan qaytaradi. */
  private static Reason reasonOf(int status, String body) {
    return switch (status) {
      case HTTP_UNAUTHORIZED -> Reason.UNAUTHORIZED;
      case HTTP_FORBIDDEN -> Reason.FORBIDDEN;
      case HTTP_NOT_FOUND -> Reason.NOT_FOUND;
      case HTTP_CONFLICT -> Reason.CONFLICT;
      case HTTP_BAD_REQUEST ->
          body.contains("has already been taken") ? Reason.CONFLICT : Reason.UNAVAILABLE;
      default -> Reason.UNAVAILABLE;
    };
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
