package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.DocsResult;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.domain.AccessPolicy;
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
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrowseDocsServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");

  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final CodeHost api = mock(CodeHost.class);
  private final BrowseDocsService service =
      new BrowseDocsService(
          new ProjectRepoAccess(
              new AccessPolicy(Set.of(USER)),
              new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz")),
              connections,
              links,
              Clock.fixed(
                  GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)),
          api);

  private ProviderConnection connection;

  @BeforeEach
  void linkedProject() {
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO));
    connection = connections.save(ServerAddress.GITLAB_COM, TOKEN, VALID);
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));
  }

  @Test
  void should_list_markdown_documents_in_standard_order() {
    when(api.files(connection, REPO.id(), "", false))
        .thenReturn(List.of("build.gradle.kts", "README.md", "CLAUDE.md"));
    when(api.files(connection, REPO.id(), "docs", true))
        .thenReturn(
            List.of(
                "docs/specs/001-login.md",
                "docs/architecture.md",
                "docs/decisions/002-db.md",
                "docs/decisions/001-stack.md",
                "docs/diagram.png",
                "docs/roadmap.md"));

    DocsResult result = service.list(USER);

    assertThat(result)
        .isEqualTo(
            new DocsResult.Listed(
                ELT_IMZO,
                REPO,
                List.of(
                    "CLAUDE.md",
                    "README.md",
                    "docs/roadmap.md",
                    "docs/decisions/001-stack.md",
                    "docs/decisions/002-db.md",
                    "docs/specs/001-login.md",
                    "docs/architecture.md")));
  }

  @Test
  void should_open_document_content() {
    when(api.readFile(connection, REPO.id(), "docs/roadmap.md"))
        .thenReturn(Optional.of("# Roadmap"));

    assertThat(service.open(USER, "docs/roadmap.md"))
        .isEqualTo(new DocsResult.Opened(ELT_IMZO, "docs/roadmap.md", "# Roadmap"));
  }

  @Test
  void should_report_missing_document() {
    when(api.readFile(connection, REPO.id(), "docs/old.md")).thenReturn(Optional.empty());

    assertThat(service.open(USER, "docs/old.md"))
        .isEqualTo(new RepoUnavailable.Failed(ConnectionProblem.NOT_FOUND));
  }

  @ParameterizedTest
  @ValueSource(strings = {".env", "src/Main.md", "docs/../.env.md", "/etc/passwd.md", "docs/a.txt"})
  void should_not_read_files_outside_documents(String path) {
    assertThat(service.open(USER, path))
        .isEqualTo(new RepoUnavailable.Failed(ConnectionProblem.NOT_FOUND));
    verifyNoInteractions(api);
  }

  @Test
  void should_deny_stranger_without_calling_gitlab() {
    assertThat(service.list(STRANGER)).isEqualTo(new RepoUnavailable.AccessDenied());
    verifyNoInteractions(api);
  }
}
