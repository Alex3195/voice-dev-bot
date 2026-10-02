package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.ConnectionRepository;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IssueTracker;
import com.alex.voicedevbot.application.port.out.ProjectRepoLinks;
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
import java.util.function.Function;

/**
 * Faol projectning repo'sida amal bajarish: whitelist, faol project, bog'langan repo va token
 * tekshiriladi; xizmat xatosi foydalanuvchiga tushunarli sababga aylanadi.
 */
public class ProjectRepoAccess {

  /**
   * @param today token holati va task muddatlari shu kunga nisbatan
   */
  record Ready(
      ProjectName project,
      ProviderConnection connection,
      Repo repo,
      LocalDate today,
      CodeHost code,
      IssueTracker tracker) {

    long repoId() {
      return repo.id();
    }
  }

  private final AccessPolicy accessPolicy;
  private final UserSettingsLookup settings;
  private final ConnectionRepository connections;
  private final ProjectRepoLinks links;
  private final Integrations integrations;
  private final Clock clock;

  public ProjectRepoAccess(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      ConnectionRepository connections,
      ProjectRepoLinks links,
      Integrations integrations,
      Clock clock) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.connections = Objects.requireNonNull(connections, "connections");
    this.links = Objects.requireNonNull(links, "links");
    this.integrations = Objects.requireNonNull(integrations, "integrations");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @param unavailable {@link RepoUnavailable}ni natija turiga o'giradi (odatda {@code u -> u})
   */
  <R> R run(
      TelegramUserId user, Function<Ready, R> action, Function<RepoUnavailable, R> unavailable) {
    if (!accessPolicy.isAllowed(user)) {
      return unavailable.apply(new RepoUnavailable.AccessDenied());
    }
    Optional<ProjectName> project = settings.current(user).activeProject();
    if (project.isEmpty()) {
      return unavailable.apply(new RepoUnavailable.NoActiveProject());
    }
    Optional<RepoLink> link = links.find(project.get());
    Optional<ProviderConnection> connection =
        link.flatMap(found -> connections.find(found.connectionId()));
    if (link.isEmpty() || connection.isEmpty()) {
      return unavailable.apply(new RepoUnavailable.NotLinked(project.get()));
    }
    LocalDate today = LocalDate.now(clock);
    ConnectionView view = new ConnectionView(connection.get(), connection.get().status(today));
    if (view.status() == TokenStatus.EXPIRED) {
      return unavailable.apply(new RepoUnavailable.NeedsNewToken(view));
    }
    try {
      return action.apply(
          new Ready(
              project.get(),
              connection.get(),
              link.get().repo(),
              today,
              integrations.codeHost(connection.get()),
              integrations.issueTracker(connection.get())));
    } catch (IntegrationException e) {
      return unavailable.apply(
          e.reason() == IntegrationException.Reason.UNAUTHORIZED
              ? new RepoUnavailable.NeedsNewToken(view)
              : new RepoUnavailable.Failed(ConnectionProblems.of(e)));
    }
  }
}
