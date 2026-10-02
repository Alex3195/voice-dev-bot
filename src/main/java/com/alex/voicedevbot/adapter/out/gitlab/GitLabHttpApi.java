package com.alex.voicedevbot.adapter.out.gitlab;

import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IntegrationException.Reason;
import com.alex.voicedevbot.application.port.out.IssueTracker;
import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.Task;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * GitLab REST API v4 ({@code PRIVATE-TOKEN} sarlavhasi bilan). gitlab.com va self-hosted uchun bir
 * xil — faqat manzil farq qiladi. Token hech qachon xato xabariga tushmaydi.
 */
public class GitLabHttpApi implements CodeHost, IssueTracker {

  static final int PAGE_SIZE = 10;
  static final String INITIAL_BRANCH = "main";
  static final String INITIAL_COMMIT = "chore: voice-dev-bot agent rules and docs structure";

  /** GitLab ruxsat beradigan eng katta sahifa. */
  static final int MAX_PAGE_SIZE = 100;

  /** Juda katta {@code docs/} papkasida cheksiz o'qimaslik uchun. */
  static final int MAX_FILES = 500;

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
  public TokenInfo verify(ServerAddress address, AccessToken token) {
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
  public List<Repo> searchRepos(ProviderConnection connection, String query) {
    String search = query.isBlank() ? "" : "&search=" + encode(query);
    JsonNode repos =
        get(
            connection,
            "/projects?membership=true&simple=true&order_by=last_activity_at&per_page="
                + PAGE_SIZE
                + search);
    List<Repo> result = new ArrayList<>();
    repos.forEach(repo -> result.add(repoOf(repo)));
    return result;
  }

  @Override
  public Repo findRepo(ProviderConnection connection, long repoId) {
    return repoOf(get(connection, "/projects/" + repoId));
  }

  @Override
  public Repo findRepo(ProviderConnection connection, String path) {
    return repoOf(get(connection, "/projects/" + encodeSegment(path)));
  }

  @Override
  public List<Namespace> namespaces(ProviderConnection connection) {
    JsonNode namespaces = get(connection, "/namespaces?per_page=50");
    List<Namespace> result = new ArrayList<>();
    namespaces.forEach(
        namespace ->
            result.add(
                new Namespace(
                    namespace.path("id").asLong(),
                    namespace.path("full_path").asString(),
                    "user".equals(namespace.path("kind").asString()))));
    result.sort(
        Comparator.comparing((Namespace namespace) -> !namespace.personal())
            .thenComparing(Namespace::path));
    return result;
  }

  @Override
  public Repo createRepo(
      ProviderConnection connection, long namespaceId, String name, Map<String, String> files) {
    ObjectNode project = json.createObjectNode();
    project.put("name", name);
    project.put("namespace_id", namespaceId);
    project.put("visibility", "private");
    project.put("default_branch", INITIAL_BRANCH);
    Repo repo = repoOf(post(connection, "/projects", project));
    if (!files.isEmpty()) {
      post(connection, "/projects/" + repo.id() + "/repository/commits", commitOf(files));
    }
    return repo;
  }

  @Override
  public List<Task> issues(ProviderConnection connection, Repo repo, int limit) {
    List<Task> result = new ArrayList<>();
    pages(
        connection,
        "/projects/" + repo.id() + "/issues?scope=all&state=all&order_by=created_at&sort=desc",
        limit,
        issue -> result.add(taskOf(issue)));
    return result;
  }

  @Override
  public Task issue(ProviderConnection connection, Repo repo, long iid) {
    return taskOf(get(connection, issuePath(repo.id(), iid)));
  }

  @Override
  public List<MergeRequest> mergeRequests(ProviderConnection connection, Repo repo, long iid) {
    List<MergeRequest> result = new ArrayList<>();
    get(connection, issuePath(repo.id(), iid) + "/related_merge_requests")
        .forEach(
            mergeRequest ->
                result.add(
                    new MergeRequest(
                        mergeRequest.path("iid").asLong(),
                        mergeRequest.path("title").asString(),
                        mergeRequestState(mergeRequest.path("state").asString()),
                        URI.create(mergeRequest.path("web_url").asString()))));
    return result;
  }

  @Override
  public Task createIssue(
      ProviderConnection connection, Repo repo, NewTask task, List<String> labels) {
    ObjectNode issue = json.createObjectNode();
    issue.put("title", task.title());
    issue.put("description", task.description());
    issue.put("labels", String.join(",", labels));
    return taskOf(post(connection, "/projects/" + repo.id() + "/issues", issue));
  }

  @Override
  public Task setIssueOpen(ProviderConnection connection, Repo repo, long iid, boolean open) {
    ObjectNode change = json.createObjectNode();
    change.put("state_event", open ? "reopen" : "close");
    return taskOf(put(connection, issuePath(repo.id(), iid), change));
  }

  @Override
  public List<String> files(
      ProviderConnection connection, Repo repo, String directory, boolean recursive) {
    String path = directory.isEmpty() ? "" : "&path=" + encode(directory);
    List<String> result = new ArrayList<>();
    try {
      pages(
          connection,
          "/projects/" + repo.id() + "/repository/tree?recursive=" + recursive + path,
          MAX_FILES,
          entry -> {
            if ("blob".equals(entry.path("type").asString())) {
              result.add(entry.path("path").asString());
            }
          });
    } catch (IntegrationException e) {
      if (e.reason() == Reason.NOT_FOUND) {
        return List.of();
      }
      throw e;
    }
    return result;
  }

  @Override
  public Optional<String> readFile(ProviderConnection connection, Repo repo, String path) {
    String request = "/projects/" + repo.id() + "/repository/files/" + encodeSegment(path) + "/raw";
    try {
      return Optional.of(
          sendForText(
              request(connection.address(), connection.token(), request).GET().build(),
              "GET " + request));
    } catch (IntegrationException e) {
      if (e.reason() == Reason.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private static String issuePath(long repoId, long iid) {
    return "/projects/" + repoId + "/issues/" + iid;
  }

  private static Task taskOf(JsonNode issue) {
    String dueDate = issue.path("due_date").asString("");
    return new Task(
        issue.path("iid").asLong(),
        issue.path("title").asString(),
        issue.path("description").asString(""),
        "opened".equals(issue.path("state").asString()),
        dueDate.isEmpty() ? Optional.empty() : Optional.of(LocalDate.parse(dueDate)),
        issue.path("merge_requests_count").asInt(0),
        URI.create(issue.path("web_url").asString()));
  }

  /** {@code locked} — merge jarayonidagi ochiq MR. */
  private static MergeRequest.State mergeRequestState(String state) {
    return switch (state.toLowerCase(Locale.ROOT)) {
      case "merged" -> MergeRequest.State.MERGED;
      case "closed" -> MergeRequest.State.CLOSED;
      default -> MergeRequest.State.OPENED;
    };
  }

  /** Sahifalab o'qiydi: sahifa to'lmaguncha yoki {@code limit}ga yetguncha. */
  private void pages(
      ProviderConnection connection, String pathWithQuery, int limit, Consumer<JsonNode> each) {
    int read = 0;
    for (int page = 1; read < limit; page++) {
      JsonNode items =
          get(connection, pathWithQuery + "&per_page=" + MAX_PAGE_SIZE + "&page=" + page);
      for (JsonNode item : items) {
        if (read == limit) {
          return;
        }
        each.accept(item);
        read++;
      }
      if (items.size() < MAX_PAGE_SIZE) {
        return;
      }
    }
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

  private static Repo repoOf(JsonNode repo) {
    return new Repo(
        repo.path("id").asLong(),
        repo.path("path_with_namespace").asString(),
        URI.create(repo.path("web_url").asString()));
  }

  private JsonNode get(ProviderConnection connection, String path) {
    return get(connection.address(), connection.token(), path);
  }

  private JsonNode get(ServerAddress address, AccessToken token, String path) {
    return send(request(address, token, path).GET().build(), "GET " + path);
  }

  private JsonNode post(ProviderConnection connection, String path, JsonNode body) {
    HttpRequest request =
        request(connection.address(), connection.token(), path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return send(request, "POST " + path);
  }

  private JsonNode put(ProviderConnection connection, String path, JsonNode body) {
    HttpRequest request =
        request(connection.address(), connection.token(), path)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return send(request, "PUT " + path);
  }

  private HttpRequest.Builder request(ServerAddress address, AccessToken token, String path) {
    return HttpRequest.newBuilder(api(address, path))
        .timeout(timeout)
        .header("PRIVATE-TOKEN", token.value())
        .header("Accept", "application/json");
  }

  /**
   * @param description xato xabari uchun ({@code GET /user}) — so'rov manzilisiz, unda sir yo'q
   */
  private JsonNode send(HttpRequest request, String description) {
    String body = sendForText(request, description);
    try {
      return json.readTree(body);
    } catch (JacksonException e) {
      throw new IntegrationException(Reason.UNAVAILABLE, description + " returned invalid JSON", e);
    }
  }

  private String sendForText(HttpRequest request, String description) {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IntegrationException(Reason.UNAVAILABLE, description + " failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(Reason.UNAVAILABLE, description + " interrupted", e);
    }
    int status = response.statusCode();
    if (status < HTTP_OK_MIN || status > HTTP_OK_MAX) {
      throw new IntegrationException(
          reasonOf(status, response.body()), description + " returned HTTP " + status, null);
    }
    return response.body();
  }

  /** API manzili, masalan {@code https://gitlab.com/api/v4/user}. */
  static URI api(ServerAddress address, String pathAndQuery) {
    return URI.create(address + "/api/v4" + pathAndQuery);
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

  /** Yo'l qismi: {@code /} ham kodlanadi ({@code %2F}), bo'sh joy — {@code %20}. */
  private static String encodeSegment(String text) {
    return encode(text).replace("+", "%20");
  }
}
