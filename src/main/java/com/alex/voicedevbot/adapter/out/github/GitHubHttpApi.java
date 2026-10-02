package com.alex.voicedevbot.adapter.out.github;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * GitHub REST API ({@code Authorization: Bearer} bilan): github.com — {@code api.github.com}.
 * Classic token ({@code repo} ruxsati) ham, fine-grained token ham ishlaydi. Token hech qachon xato
 * xabariga tushmaydi.
 *
 * <p>GitHub'da issue'ning muddati yo'q; unga havola qilgan PR'lar issue timeline'idan olinadi.
 */
public class GitHubHttpApi implements CodeHost, IssueTracker {

  static final int PAGE_SIZE = 10;
  static final int MAX_PAGE_SIZE = 100;
  static final int MAX_FILES = 500;
  static final String API_VERSION = "2022-11-28";

  /** Classic token shu ruxsatsiz repo yarata olmaydi va issue ocholmaydi. */
  static final String REQUIRED_CLASSIC_SCOPE = "repo";

  static final String INITIAL_COMMIT = "chore: voice-dev-bot agent rules and docs structure";

  private static final int HTTP_OK_MIN = 200;
  private static final int HTTP_OK_MAX = 299;
  private static final int HTTP_UNAUTHORIZED = 401;
  private static final int HTTP_FORBIDDEN = 403;
  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_CONFLICT = 409;
  private static final int HTTP_UNPROCESSABLE = 422;
  private static final int DATE_LENGTH = 10;

  private final HttpClient httpClient;
  private final Duration timeout;
  private final String cloudApi;
  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * @param cloudApi github.com API manzili ({@code https://api.github.com}; testda — soxta server)
   */
  public GitHubHttpApi(HttpClient httpClient, Duration timeout, URI cloudApi) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.cloudApi = Objects.requireNonNull(cloudApi, "cloudApi").toString().replaceAll("/+$", "");
  }

  @Override
  public TokenInfo verify(ServerAddress address, AccessToken token) {
    HttpResponse<String> response =
        exchange(request(address, token, "/user").GET().build(), "GET /user");
    String login = parse(response, "GET /user").path("login").asString();
    Optional<String> classicScopes = response.headers().firstValue("x-oauth-scopes");
    Set<String> scopes =
        classicScopes
            .map(
                header ->
                    Arrays.stream(header.split(","))
                        .map(String::strip)
                        .filter(scope -> !scope.isEmpty())
                        .collect(Collectors.toSet()))
            .orElseGet(Set::of);
    if (classicScopes.isPresent() && !scopes.contains(REQUIRED_CLASSIC_SCOPE)) {
      throw new IntegrationException(
          Reason.MISSING_SCOPE, "GitHub classic token has no repo scope", null);
    }
    Optional<LocalDate> expiresAt =
        response
            .headers()
            .firstValue("github-authentication-token-expiration")
            .filter(value -> value.length() >= DATE_LENGTH)
            .map(value -> LocalDate.parse(value.substring(0, DATE_LENGTH)));
    return expiresAt
        .map(date -> TokenInfo.expiring(login, scopes, date))
        .orElseGet(() -> TokenInfo.withoutExpiry(login, scopes));
  }

  /** GitHub'da a'zo repo'lar ichidan qidiruv yo'q — oxirgi faollari olinib, nomi bo'yicha. */
  @Override
  public List<Repo> searchRepos(ProviderConnection connection, String query) {
    String needle = query.strip().toLowerCase(Locale.ROOT);
    List<Repo> result = new ArrayList<>();
    for (JsonNode repo :
        get(
            connection,
            "/user/repos?affiliation=owner,collaborator,organization_member&sort=pushed&per_page="
                + MAX_PAGE_SIZE)) {
      if (result.size() == PAGE_SIZE) {
        break;
      }
      if (repo.path("full_name").asString().toLowerCase(Locale.ROOT).contains(needle)) {
        result.add(repoOf(repo));
      }
    }
    return result;
  }

  @Override
  public Repo findRepo(ProviderConnection connection, long repoId) {
    return repoOf(get(connection, "/repositories/" + repoId));
  }

  @Override
  public Repo findRepo(ProviderConnection connection, String path) {
    return repoOf(get(connection, "/repos/" + encodePath(path)));
  }

  @Override
  public List<Namespace> namespaces(ProviderConnection connection) {
    JsonNode user = get(connection, "/user");
    List<Namespace> result = new ArrayList<>();
    result.add(new Namespace(user.path("id").asLong(), user.path("login").asString(), true));
    get(connection, "/user/orgs?per_page=" + MAX_PAGE_SIZE)
        .forEach(
            org ->
                result.add(
                    new Namespace(org.path("id").asLong(), org.path("login").asString(), false)));
    result.sort(
        Comparator.comparing((Namespace namespace) -> !namespace.personal())
            .thenComparing(Namespace::path));
    return result;
  }

  /**
   * Bo'sh repo'ga Git Data API bilan commit qilib bo'lmaydi — repo README bilan yaratiladi, keyin
   * barcha fayllar bitta commit bilan qo'shiladi.
   */
  @Override
  public Repo createRepo(
      ProviderConnection connection, long namespaceId, String name, Map<String, String> files) {
    ObjectNode body = json.createObjectNode();
    body.put("name", name);
    body.put("private", true);
    body.put("auto_init", !files.isEmpty());
    JsonNode created = post(connection, ownerReposPath(connection, namespaceId), body);
    if (!files.isEmpty()) {
      commitFiles(
          connection,
          created.path("full_name").asString(),
          created.path("default_branch").asString("main"),
          files);
    }
    return repoOf(created);
  }

  @Override
  public List<String> files(
      ProviderConnection connection, Repo repo, String directory, boolean recursive) {
    try {
      return recursive
          ? treeFiles(connection, repo, directory)
          : listing(connection, repo, directory);
    } catch (IntegrationException e) {
      if (e.reason() == Reason.NOT_FOUND || e.reason() == Reason.CONFLICT) {
        return List.of();
      }
      throw e;
    }
  }

  @Override
  public Optional<String> readFile(ProviderConnection connection, Repo repo, String path) {
    String request = repoPath(repo) + "/contents/" + encodePath(path);
    try {
      return Optional.of(
          exchange(
                  request(connection.address(), connection.token(), request)
                      .setHeader("Accept", "application/vnd.github.raw+json")
                      .GET()
                      .build(),
                  "GET " + request)
              .body());
    } catch (IntegrationException e) {
      if (e.reason() == Reason.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  @Override
  public List<MergeRequest> mergeRequests(ProviderConnection connection, Repo repo, long iid) {
    Map<Long, MergeRequest> pulls = new LinkedHashMap<>();
    for (JsonNode event :
        get(connection, issuePath(repo, iid) + "/timeline?per_page=" + MAX_PAGE_SIZE)) {
      JsonNode source = event.path("source").path("issue");
      if ("cross-referenced".equals(event.path("event").asString()) && source.has("pull_request")) {
        pulls.putIfAbsent(source.path("number").asLong(), pullOf(source));
      }
    }
    return List.copyOf(pulls.values());
  }

  /** Ro'yxatda PR'lar ham keladi — ular tashlab yuboriladi. */
  @Override
  public List<Task> issues(ProviderConnection connection, Repo repo, int limit) {
    Set<Long> linked = linkedToPulls(connection, repo);
    List<Task> result = new ArrayList<>();
    pages(
        connection,
        repoPath(repo) + "/issues?state=all&sort=created&direction=desc",
        issue -> {
          if (result.size() < limit && !issue.has("pull_request")) {
            long number = issue.path("number").asLong();
            result.add(taskOf(issue, linked.contains(number) ? 1 : 0));
          }
          return result.size() < limit;
        });
    return result;
  }

  @Override
  public Task issue(ProviderConnection connection, Repo repo, long iid) {
    return withPulls(connection, repo, get(connection, issuePath(repo, iid)));
  }

  @Override
  public Task createIssue(
      ProviderConnection connection, Repo repo, NewTask task, List<String> labels) {
    ObjectNode issue = json.createObjectNode();
    issue.put("title", task.title());
    issue.put("body", task.description());
    ArrayNode labelNames = issue.putArray("labels");
    labels.forEach(labelNames::add);
    return taskOf(post(connection, repoPath(repo) + "/issues", issue), 0);
  }

  @Override
  public Task setIssueOpen(ProviderConnection connection, Repo repo, long iid, boolean open) {
    ObjectNode change = json.createObjectNode();
    change.put("state", open ? "open" : "closed");
    return withPulls(connection, repo, patch(connection, issuePath(repo, iid), change));
  }

  /** API manzili: github.com — bulut API; boshqa server (Enterprise, keyin) — {@code /api/v3}. */
  URI api(ServerAddress address, String pathAndQuery) {
    String base = address.equals(ServerAddress.GITHUB_COM) ? cloudApi : address + "/api/v3";
    return URI.create(base + pathAndQuery);
  }

  private String ownerReposPath(ProviderConnection connection, long namespaceId) {
    return namespaces(connection).stream()
        .filter(namespace -> namespace.id() == namespaceId)
        .findFirst()
        .map(
            namespace ->
                namespace.personal() ? "/user/repos" : "/orgs/" + namespace.path() + "/repos")
        .orElseThrow(
            () -> new IntegrationException(Reason.NOT_FOUND, "GitHub owner not found", null));
  }

  private void commitFiles(
      ProviderConnection connection, String fullName, String branch, Map<String, String> files) {
    String git = "/repos/" + fullName + "/git";
    String parent =
        get(connection, git + "/ref/heads/" + branch).path("object").path("sha").asString();
    String baseTree =
        get(connection, git + "/commits/" + parent).path("tree").path("sha").asString();
    ObjectNode tree = json.createObjectNode();
    tree.put("base_tree", baseTree);
    ArrayNode entries = tree.putArray("tree");
    files.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            file -> {
              ObjectNode entry = entries.addObject();
              entry.put("path", file.getKey());
              entry.put("mode", "100644");
              entry.put("type", "blob");
              entry.put("content", file.getValue());
            });
    String treeSha = post(connection, git + "/trees", tree).path("sha").asString();
    ObjectNode commit = json.createObjectNode();
    commit.put("message", INITIAL_COMMIT);
    commit.put("tree", treeSha);
    commit.putArray("parents").add(parent);
    String commitSha = post(connection, git + "/commits", commit).path("sha").asString();
    ObjectNode ref = json.createObjectNode();
    ref.put("sha", commitSha);
    patch(connection, git + "/refs/heads/" + branch, ref);
  }

  private List<String> listing(ProviderConnection connection, Repo repo, String directory) {
    String path = directory.isEmpty() ? "" : "/" + encodePath(directory);
    JsonNode entries = get(connection, repoPath(repo) + "/contents" + path);
    List<String> result = new ArrayList<>();
    if (entries.isArray()) {
      entries.forEach(
          entry -> {
            if ("file".equals(entry.path("type").asString())) {
              result.add(entry.path("path").asString());
            }
          });
    }
    return result;
  }

  /** Bo'sh repo'da daraxt yo'q (HTTP 409) — bo'sh ro'yxat. */
  private List<String> treeFiles(ProviderConnection connection, Repo repo, String directory) {
    String prefix = directory.isEmpty() ? "" : directory + "/";
    List<String> result = new ArrayList<>();
    for (JsonNode entry :
        get(connection, repoPath(repo) + "/git/trees/HEAD?recursive=1").path("tree")) {
      String path = entry.path("path").asString();
      if (result.size() < MAX_FILES
          && "blob".equals(entry.path("type").asString())
          && path.startsWith(prefix)) {
        result.add(path);
      }
    }
    return result;
  }

  /**
   * PR bilan bog'langan issue raqamlari — bitta qidiruv so'rovi. Qidiruv limiti tugasa ro'yxat
   * baribir ko'rsatiladi, faqat "MR ochilgan" belgisisiz.
   */
  private Set<Long> linkedToPulls(ProviderConnection connection, Repo repo) {
    String query = encode("repo:" + repo.path() + " is:issue linked:pr");
    Set<Long> numbers = new HashSet<>();
    try {
      get(connection, "/search/issues?q=" + query + "&per_page=" + MAX_PAGE_SIZE)
          .path("items")
          .forEach(item -> numbers.add(item.path("number").asLong()));
    } catch (IntegrationException e) {
      if (e.reason() == Reason.UNAUTHORIZED) {
        throw e;
      }
    }
    return numbers;
  }

  private Task withPulls(ProviderConnection connection, Repo repo, JsonNode issue) {
    long number = issue.path("number").asLong();
    return taskOf(issue, mergeRequests(connection, repo, number).size());
  }

  private static Task taskOf(JsonNode issue, int pulls) {
    return new Task(
        issue.path("number").asLong(),
        issue.path("title").asString(),
        issue.path("body").asString(""),
        "open".equals(issue.path("state").asString()),
        Optional.empty(),
        pulls,
        URI.create(issue.path("html_url").asString()));
  }

  private static MergeRequest pullOf(JsonNode pull) {
    boolean merged = !pull.path("pull_request").path("merged_at").asString("").isEmpty();
    MergeRequest.State state =
        merged
            ? MergeRequest.State.MERGED
            : "closed".equals(pull.path("state").asString())
                ? MergeRequest.State.CLOSED
                : MergeRequest.State.OPENED;
    return new MergeRequest(
        pull.path("number").asLong(),
        pull.path("title").asString(),
        state,
        URI.create(pull.path("html_url").asString()));
  }

  private static Repo repoOf(JsonNode repo) {
    return new Repo(
        repo.path("id").asLong(),
        repo.path("full_name").asString(),
        URI.create(repo.path("html_url").asString()));
  }

  private static String repoPath(Repo repo) {
    return "/repos/" + repo.path();
  }

  private static String issuePath(Repo repo, long iid) {
    return repoPath(repo) + "/issues/" + iid;
  }

  /**
   * @param each {@code false} qaytarsa o'qish to'xtaydi
   */
  private void pages(
      ProviderConnection connection, String pathWithQuery, Predicate<JsonNode> each) {
    for (int page = 1; ; page++) {
      JsonNode items =
          get(connection, pathWithQuery + "&per_page=" + MAX_PAGE_SIZE + "&page=" + page);
      for (JsonNode item : items) {
        if (!each.test(item)) {
          return;
        }
      }
      if (items.size() < MAX_PAGE_SIZE) {
        return;
      }
    }
  }

  private JsonNode get(ProviderConnection connection, String path) {
    HttpRequest request = request(connection.address(), connection.token(), path).GET().build();
    return parse(exchange(request, "GET " + path), "GET " + path);
  }

  private JsonNode post(ProviderConnection connection, String path, JsonNode body) {
    return send(connection, "POST", path, body);
  }

  private JsonNode patch(ProviderConnection connection, String path, JsonNode body) {
    return send(connection, "PATCH", path, body);
  }

  private JsonNode send(ProviderConnection connection, String method, String path, JsonNode body) {
    HttpRequest request =
        request(connection.address(), connection.token(), path)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return parse(exchange(request, method + " " + path), method + " " + path);
  }

  private HttpRequest.Builder request(ServerAddress address, AccessToken token, String path) {
    return HttpRequest.newBuilder(api(address, path))
        .timeout(timeout)
        .header("Authorization", "Bearer " + token.value())
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", API_VERSION)
        .header("User-Agent", "voice-dev-bot");
  }

  /**
   * @param description xato xabari uchun ({@code GET /user}) — unda sir yo'q
   */
  private HttpResponse<String> exchange(HttpRequest request, String description) {
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
    return response;
  }

  private JsonNode parse(HttpResponse<String> response, String description) {
    try {
      return json.readTree(response.body());
    } catch (JacksonException e) {
      throw new IntegrationException(Reason.UNAVAILABLE, description + " returned invalid JSON", e);
    }
  }

  /** GitHub band nomni 422 "name already exists" bilan qaytaradi; bo'sh repo daraxti — 409. */
  private static Reason reasonOf(int status, String body) {
    return switch (status) {
      case HTTP_UNAUTHORIZED -> Reason.UNAUTHORIZED;
      case HTTP_FORBIDDEN -> Reason.FORBIDDEN;
      case HTTP_NOT_FOUND -> Reason.NOT_FOUND;
      case HTTP_CONFLICT -> Reason.CONFLICT;
      case HTTP_UNPROCESSABLE ->
          body.contains("already exists") ? Reason.CONFLICT : Reason.UNAVAILABLE;
      default -> Reason.UNAVAILABLE;
    };
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  /** Har bo'lak alohida kodlanadi, {@code /} saqlanadi; bo'sh joy — {@code %20}. */
  private static String encodePath(String path) {
    return Arrays.stream(path.split("/"))
        .map(segment -> encode(segment).replace("+", "%20"))
        .collect(Collectors.joining("/"));
  }
}
