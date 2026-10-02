package com.alex.voicedevbot.adapter.in.telegram;

import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.DocsResult;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IntegrationException.Reason;
import com.alex.voicedevbot.application.service.BrowseDocsService;
import com.alex.voicedevbot.application.service.BrowseTranscriptsService;
import com.alex.voicedevbot.application.service.ChangeLanguageService;
import com.alex.voicedevbot.application.service.LinkRepoService;
import com.alex.voicedevbot.application.service.ManageConnectionsService;
import com.alex.voicedevbot.application.service.ManageGlossaryService;
import com.alex.voicedevbot.application.service.ManageProjectsService;
import com.alex.voicedevbot.application.service.ManageTasksService;
import com.alex.voicedevbot.application.service.ProjectRepoAccess;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.CodeHostAndTracker;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryConnectionRepository;
import com.alex.voicedevbot.support.InMemoryProjectRepoLinks;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Hujjatlar oqimi va uzun hujjatni sahifalash. */
class DocsDialogTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final CodeHostAndTracker api = mock(CodeHostAndTracker.class);
  private final BotConversation conversation = conversation();
  private ProviderConnection connection;

  private BotConversation conversation() {
    AccessPolicy access = new AccessPolicy(Set.of(USER));
    UserSettingsLookup settings =
        new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz"));
    Clock clock =
        Clock.fixed(GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    LinkRepoService repos =
        new LinkRepoService(
            access,
            settings,
            connections,
            links,
            GitLabFixtures.integrations(api),
            project -> Map.of(),
            clock);
    BrowseTranscriptsService transcripts =
        new BrowseTranscriptsService(access, new InMemoryTranscriptionLog());
    ProjectRepoAccess repoAccess =
        new ProjectRepoAccess(
            access, settings, connections, links, GitLabFixtures.integrations(api), clock);
    return new BotConversation(
        new ManageProjectsService(access, projects, settings, settingsRepository),
        new ManageGlossaryService(access, projects, settings),
        new ChangeLanguageService(access, settings, settingsRepository),
        transcripts,
        new ConnectionsDialog(
            new ManageConnectionsService(
                access, connections, GitLabFixtures.integrations(api), clock),
            repos),
        new TaskDialog(new ManageTasksService(repoAccess), repos, transcripts),
        new DocsDialog(new BrowseDocsService(repoAccess)),
        ZoneOffset.UTC);
  }

  @BeforeEach
  void linkedProjectWithDocs() {
    projects.save(Project.named(ELT_IMZO));
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO));
    connection = connections.save(Provider.GITLAB, ServerAddress.GITLAB_COM, TOKEN, VALID);
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));
    when(api.files(connection, REPO.id(), "", false)).thenReturn(List.of("CLAUDE.md"));
    when(api.files(connection, REPO.id(), "docs", true))
        .thenReturn(List.of("docs/roadmap.md", "docs/specs/001-login.md"));
  }

  private Reply press(String action) {
    return conversation.onButton(USER, action).orElseThrow();
  }

  private static List<String> labels(Screen screen) {
    return screen.rows().stream().flatMap(List::stream).map(Button::label).toList();
  }

  private static List<String> actions(Screen screen) {
    return screen.rows().stream().flatMap(List::stream).map(Button::action).toList();
  }

  @Test
  void should_list_documents_from_repo() {
    Screen list = press(Actions.DOCS).screen();

    assertThat(list.html())
        .startsWith(
            "📄 <b>ELT imzo</b> — hujjatlar\n📂 <a href=\"https://gitlab.com/alex/elt-imzo\">");
    assertThat(labels(list))
        .containsExactly(
            "📄 CLAUDE.md", "📄 docs/roadmap.md", "📄 docs/specs/001-login.md", "⬅️ Kartochka");
  }

  @Test
  void should_show_short_document_on_one_page_escaped() {
    when(api.readFile(connection, REPO.id(), "docs/roadmap.md"))
        .thenReturn(Optional.of("# Roadmap\n<b>PR E</b> & D"));

    Screen page = press(Actions.document("docs/roadmap.md", 0)).screen();

    assertThat(page.html())
        .isEqualTo("📄 <b>docs/roadmap.md</b>\n\n# Roadmap\n&lt;b&gt;PR E&lt;/b&gt; &amp; D");
    assertThat(labels(page)).containsExactly("⬅️ Hujjatlar");
  }

  @Test
  void should_page_long_document_by_lines() {
    String line = "x".repeat(99) + "\n";
    when(api.readFile(connection, REPO.id(), "CLAUDE.md")).thenReturn(Optional.of(line.repeat(80)));

    Screen first = press(Actions.document("CLAUDE.md", 0)).screen();
    Screen last = press(Actions.document("CLAUDE.md", 9)).screen();

    assertThat(first.html()).startsWith("📄 <b>CLAUDE.md</b> · 1/3\n\n");
    assertThat(first.html().length()).isLessThan(4096);
    assertThat(actions(first)).containsExactly(Actions.document("CLAUDE.md", 1), Actions.DOCS);
    assertThat(last.html()).startsWith("📄 <b>CLAUDE.md</b> · 3/3");
    assertThat(actions(last)).containsExactly(Actions.document("CLAUDE.md", 1), Actions.DOCS);
  }

  @Test
  void should_return_to_list_when_document_disappeared() {
    Reply reply = press(Actions.document("docs/old.md", 0));

    assertThat(reply.toast()).isEqualTo(DocsDialog.DOCUMENT_NOT_FOUND);
    assertThat(reply.screen().html()).contains("— hujjatlar");
  }

  @Test
  void should_report_gitlab_failure_while_reading() {
    when(api.readFile(any(), anyLong(), anyString()))
        .thenThrow(new IntegrationException(Reason.UNAVAILABLE, "GET raw failed", null));

    Screen screen = press(Actions.document("CLAUDE.md", 0)).screen();

    assertThat(screen.html()).startsWith("⚠️ Serverga ulanib bo'lmadi");
  }

  @Test
  void should_report_failure_when_listing_fails_before_opening() {
    when(api.files(any(), anyLong(), anyString(), anyBoolean()))
        .thenThrow(new IntegrationException(Reason.FORBIDDEN, "GET tree returned 403", null));

    assertThat(press(Actions.document("CLAUDE.md", 0)).screen().html())
        .startsWith("⚠️ Xizmat bu amalga ruxsat bermadi");
  }

  @Test
  void should_ignore_malformed_document_action() {
    assertThat(conversation.onButton(USER, Actions.DOC_PREFIX + "zz")).isEmpty();
  }

  @Test
  void should_split_escaped_text_without_breaking_entities() {
    String text = "a".repeat(DocScreens.PAGE_LENGTH - 2) + "&amp;" + "b".repeat(10);

    List<String> pages = DocScreens.pages(text);

    assertThat(pages).containsExactly("a".repeat(DocScreens.PAGE_LENGTH - 2), "&amp;bbbbbbbbbb");
  }

  @Test
  void should_split_by_word_when_no_line_break_fits() {
    String text = "a".repeat(DocScreens.PAGE_LENGTH - 5) + " " + "b".repeat(20);

    assertThat(DocScreens.pages(text)).hasSize(2).last().isEqualTo("b".repeat(20));
  }

  @Test
  void should_show_placeholder_for_empty_document() {
    Screen page = DocScreens.page(new DocsResult.Opened(ELT_IMZO, "docs/empty.md", "  "), 0);

    assertThat(page.html()).endsWith("<i>bo'sh</i>");
  }
}
