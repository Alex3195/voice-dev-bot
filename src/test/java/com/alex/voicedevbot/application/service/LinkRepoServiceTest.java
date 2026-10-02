package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.PERSONAL;
import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IntegrationException.Reason;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryConnectionRepository;
import com.alex.voicedevbot.support.InMemoryProjectRepoLinks;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkRepoServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final Map<String, String> FILES = Map.of("CLAUDE.md", "# ELT imzo");

  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final CodeHost api = mock(CodeHost.class);
  private final LinkRepoService service =
      new LinkRepoService(
          new AccessPolicy(Set.of(USER)),
          new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz")),
          connections,
          links,
          api,
          project -> FILES,
          Clock.fixed(
              GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

  private ProviderConnection connection;

  @BeforeEach
  void activeProjectAndConnection() {
    new InMemoryProjectRepository().save(Project.named(ELT_IMZO));
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO));
    connection = connections.save(ServerAddress.GITLAB_COM, TOKEN, VALID);
  }

  @Test
  void should_offer_connections_when_project_has_no_repo() {
    assertThat(service.show(USER))
        .isInstanceOfSatisfying(
            RepoLinkResult.NotLinked.class,
            notLinked -> {
              assertThat(notLinked.project()).isEqualTo(ELT_IMZO);
              assertThat(notLinked.connections()).extracting(v -> v.id()).containsExactly(1L);
            });
  }

  @Test
  void should_search_and_link_existing_repo() {
    when(api.searchRepos(connection, "elt")).thenReturn(List.of(REPO));
    when(api.findRepo(connection, REPO.id())).thenReturn(REPO);

    RepoLinkResult found = service.search(USER, connection.id(), " elt ");
    RepoLinkResult linked = service.link(USER, connection.id(), REPO.id());

    assertThat(found).isEqualTo(new RepoLinkResult.Repos(connection.id(), "elt", List.of(REPO)));
    assertThat(linked).isInstanceOf(RepoLinkResult.Linked.class);
    assertThat(links.find(ELT_IMZO)).contains(new RepoLink(connection.id(), REPO));
    assertThat(service.show(USER)).isInstanceOf(RepoLinkResult.Linked.class);
  }

  @Test
  void should_create_repo_with_template_files_and_link_it() {
    when(api.createRepo(connection, PERSONAL.id(), "elt-imzo", FILES)).thenReturn(REPO);

    RepoLinkResult result = service.create(USER, connection.id(), PERSONAL.id(), " elt-imzo ");

    assertThat(result).isInstanceOf(RepoLinkResult.Linked.class);
    assertThat(links.find(ELT_IMZO)).contains(new RepoLink(connection.id(), REPO));
  }

  @Test
  void should_list_namespaces_for_new_repo() {
    when(api.namespaces(connection)).thenReturn(List.of(PERSONAL));

    assertThat(service.namespaces(USER, connection.id()))
        .isEqualTo(new RepoLinkResult.Namespaces(connection.id(), List.of(PERSONAL)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "bad/name", "emoji 🚀"})
  void should_reject_invalid_repo_name_without_calling_gitlab(String name) {
    assertThat(service.create(USER, connection.id(), PERSONAL.id(), name))
        .isEqualTo(new RepoLinkResult.Failed(ConnectionProblem.INVALID_REPO_NAME));
    verifyNoInteractions(api);
  }

  @Test
  void should_ask_for_new_token_when_it_is_expired_or_rejected() {
    ProviderConnection expired =
        connections.save(
            ServerAddress.parse("git.example.uz"),
            TOKEN,
            GitLabFixtures.expiringOn(GitLabFixtures.TODAY));
    when(api.searchRepos(connection, ""))
        .thenThrow(new IntegrationException(Reason.UNAUTHORIZED, "x", null));

    assertThat(service.search(USER, expired.id(), ""))
        .isInstanceOf(RepoLinkResult.NeedsNewToken.class);
    assertThat(service.search(USER, connection.id(), ""))
        .isInstanceOf(RepoLinkResult.NeedsNewToken.class);
    verify(api).searchRepos(connection, "");
  }

  @Test
  void should_report_gitlab_failures_and_missing_connection() {
    when(api.createRepo(any(), anyLong(), anyString(), any()))
        .thenThrow(new IntegrationException(Reason.CONFLICT, "taken", null));

    assertThat(service.create(USER, connection.id(), PERSONAL.id(), "elt-imzo"))
        .isEqualTo(new RepoLinkResult.Failed(ConnectionProblem.REPO_EXISTS));
    assertThat(service.link(USER, 99, REPO.id()))
        .isEqualTo(new RepoLinkResult.Failed(ConnectionProblem.NOT_FOUND));
    assertThat(links.find(ELT_IMZO)).isEmpty();
  }

  @Test
  void should_unlink_repo() {
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));

    assertThat(service.unlink(USER)).isInstanceOf(RepoLinkResult.NotLinked.class);
    assertThat(links.find(ELT_IMZO)).isEmpty();
  }

  @Test
  void should_treat_link_to_removed_connection_as_not_linked() {
    links.link(ELT_IMZO, new RepoLink(99, REPO));

    assertThat(service.show(USER)).isInstanceOf(RepoLinkResult.NotLinked.class);
  }

  @Test
  void should_require_active_project() {
    settingsRepository.save(UserSettings.defaults(USER, new SpeechLanguage("uz")));

    assertThat(service.show(USER)).isInstanceOf(RepoLinkResult.NoActiveProject.class);
    assertThat(service.link(USER, connection.id(), REPO.id()))
        .isInstanceOf(RepoLinkResult.NoActiveProject.class);
    verifyNoInteractions(api);
  }

  @Test
  void should_deny_stranger_everything() {
    assertThat(service.show(STRANGER)).isInstanceOf(RepoLinkResult.AccessDenied.class);
    assertThat(service.search(STRANGER, connection.id(), "x"))
        .isInstanceOf(RepoLinkResult.AccessDenied.class);
    assertThat(service.namespaces(STRANGER, connection.id()))
        .isInstanceOf(RepoLinkResult.AccessDenied.class);
    assertThat(service.link(STRANGER, connection.id(), 1))
        .isInstanceOf(RepoLinkResult.AccessDenied.class);
    assertThat(service.create(STRANGER, connection.id(), 1, "x"))
        .isInstanceOf(RepoLinkResult.AccessDenied.class);
    assertThat(service.create(STRANGER, connection.id(), 1, "bad/name"))
        .isInstanceOf(RepoLinkResult.AccessDenied.class);
    assertThat(service.unlink(STRANGER)).isInstanceOf(RepoLinkResult.AccessDenied.class);
    verifyNoInteractions(api);
  }
}
