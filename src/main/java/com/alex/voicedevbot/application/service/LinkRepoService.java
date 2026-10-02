package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.ConnectionRepository;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.ProjectRepoLinks;
import com.alex.voicedevbot.application.port.out.RepoTemplate;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TokenStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public class LinkRepoService implements LinkRepoUseCase {

  /** GitLab repo nomi: harf, raqam, {@code _ . -} va bo'sh joy. */
  private static final Pattern REPO_NAME = Pattern.compile("[\\p{L}\\p{N}_.\\- ]{1,100}");

  private final AccessPolicy accessPolicy;
  private final UserSettingsLookup settings;
  private final ConnectionRepository connections;
  private final ProjectRepoLinks links;
  private final CodeHost gitLab;
  private final RepoTemplate template;
  private final Clock clock;

  public LinkRepoService(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      ConnectionRepository connections,
      ProjectRepoLinks links,
      CodeHost gitLab,
      RepoTemplate template,
      Clock clock) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.connections = Objects.requireNonNull(connections, "connections");
    this.links = Objects.requireNonNull(links, "links");
    this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
    this.template = Objects.requireNonNull(template, "template");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public RepoLinkResult show(TelegramUserId user) {
    return withProject(user, this::describe);
  }

  @Override
  public RepoLinkResult search(TelegramUserId user, long connectionId, String query) {
    String trimmed = query == null ? "" : query.strip();
    return withConnection(
        user,
        connectionId,
        (project, connection) ->
            new RepoLinkResult.Repos(
                connectionId, trimmed, gitLab.searchRepos(connection, trimmed)));
  }

  @Override
  public RepoLinkResult namespaces(TelegramUserId user, long connectionId) {
    return withConnection(
        user,
        connectionId,
        (project, connection) ->
            new RepoLinkResult.Namespaces(connectionId, gitLab.namespaces(connection)));
  }

  @Override
  public RepoLinkResult link(TelegramUserId user, long connectionId, long repoId) {
    return withConnection(
        user,
        connectionId,
        (project, connection) -> save(project, connection, gitLab.findRepo(connection, repoId)));
  }

  @Override
  public RepoLinkResult create(
      TelegramUserId user, long connectionId, long namespaceId, String name) {
    String trimmed = name == null ? "" : name.strip();
    if (!REPO_NAME.matcher(trimmed).matches()) {
      return accessPolicy.isAllowed(user)
          ? new RepoLinkResult.Failed(ConnectionProblem.INVALID_REPO_NAME)
          : new RepoLinkResult.AccessDenied();
    }
    return withConnection(
        user,
        connectionId,
        (project, connection) ->
            save(
                project,
                connection,
                gitLab.createRepo(connection, namespaceId, trimmed, template.files(project))));
  }

  @Override
  public RepoLinkResult unlink(TelegramUserId user) {
    return withProject(
        user,
        project -> {
          links.unlink(project);
          return describe(project);
        });
  }

  private RepoLinkResult save(ProjectName project, ProviderConnection connection, Repo repo) {
    RepoLink link = new RepoLink(connection.id(), repo);
    links.link(project, link);
    return new RepoLinkResult.Linked(project, link, view(connection));
  }

  private RepoLinkResult describe(ProjectName project) {
    Optional<RepoLink> link = links.find(project);
    Optional<ProviderConnection> connection =
        link.flatMap(found -> connections.find(found.connectionId()));
    if (link.isPresent() && connection.isPresent()) {
      return new RepoLinkResult.Linked(project, link.get(), view(connection.get()));
    }
    return new RepoLinkResult.NotLinked(
        project, connections.findAll().stream().map(this::view).toList());
  }

  private RepoLinkResult withProject(
      TelegramUserId user, Function<ProjectName, RepoLinkResult> action) {
    if (!accessPolicy.isAllowed(user)) {
      return new RepoLinkResult.AccessDenied();
    }
    return settings
        .current(user)
        .activeProject()
        .map(action)
        .orElseGet(RepoLinkResult.NoActiveProject::new);
  }

  /** Tugagan yoki GitLab rad etgan token bilan amal bajarilmaydi — yangilash taklif qilinadi. */
  private RepoLinkResult withConnection(
      TelegramUserId user,
      long connectionId,
      BiFunction<ProjectName, ProviderConnection, RepoLinkResult> action) {
    return withProject(
        user,
        project -> {
          Optional<ProviderConnection> connection = connections.find(connectionId);
          if (connection.isEmpty()) {
            return new RepoLinkResult.Failed(ConnectionProblem.NOT_FOUND);
          }
          ConnectionView view = view(connection.get());
          if (view.status() == TokenStatus.EXPIRED) {
            return new RepoLinkResult.NeedsNewToken(view);
          }
          try {
            return action.apply(project, connection.get());
          } catch (IntegrationException e) {
            return e.reason() == IntegrationException.Reason.UNAUTHORIZED
                ? new RepoLinkResult.NeedsNewToken(view)
                : new RepoLinkResult.Failed(ConnectionProblems.of(e));
          }
        });
  }

  private ConnectionView view(ProviderConnection connection) {
    return new ConnectionView(connection, connection.status(LocalDate.now(clock)));
  }
}
