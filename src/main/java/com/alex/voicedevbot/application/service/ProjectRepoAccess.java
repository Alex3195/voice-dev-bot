package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.out.GitLabConnectionRepository;
import com.alex.voicedevbot.application.port.out.GitLabException;
import com.alex.voicedevbot.application.port.out.ProjectRepoLinks;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.ProjectName;
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
 * tekshiriladi; GitLab xatosi foydalanuvchiga tushunarli sababga aylanadi.
 */
public class ProjectRepoAccess {

  /**
   * @param today token holati va task muddatlari shu kunga nisbatan
   */
  record Ready(ProjectName project, GitLabConnection connection, GitLabRepo repo, LocalDate today) {

    long repoId() {
      return repo.id();
    }
  }

  private final AccessPolicy accessPolicy;
  private final UserSettingsLookup settings;
  private final GitLabConnectionRepository connections;
  private final ProjectRepoLinks links;
  private final Clock clock;

  public ProjectRepoAccess(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      GitLabConnectionRepository connections,
      ProjectRepoLinks links,
      Clock clock) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.connections = Objects.requireNonNull(connections, "connections");
    this.links = Objects.requireNonNull(links, "links");
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
    Optional<GitLabConnection> connection =
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
      return action.apply(new Ready(project.get(), connection.get(), link.get().repo(), today));
    } catch (GitLabException e) {
      return unavailable.apply(
          e.reason() == GitLabException.Reason.UNAUTHORIZED
              ? new RepoUnavailable.NeedsNewToken(view)
              : new RepoUnavailable.Failed(GitLabProblems.of(e)));
    }
  }
}
