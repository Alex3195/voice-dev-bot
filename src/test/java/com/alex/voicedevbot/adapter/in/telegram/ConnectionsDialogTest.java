package com.alex.voicedevbot.adapter.in.telegram;

import static com.alex.voicedevbot.support.GitLabFixtures.PERSONAL;
import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
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
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ulanishlar va repo bog'lash oqimlari — haqiqiy use-case'lar, soxta GitLab. */
class ConnectionsDialogTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final CodeHostAndTracker api = mock(CodeHostAndTracker.class);
  private final BotConversation conversation = conversation();

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
            project -> Map.of("CLAUDE.md", "# " + project.value()),
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

  private Screen text(String text) {
    return conversation.onText(USER, text).orElseThrow();
  }

  private Reply press(String action) {
    return conversation.onButton(USER, action).orElseThrow();
  }

  private static List<String> actions(Screen screen) {
    return screen.rows().stream().flatMap(List::stream).map(Button::action).toList();
  }

  private static List<String> labels(Screen screen) {
    return screen.rows().stream().flatMap(List::stream).map(Button::label).toList();
  }

  private ProviderConnection connectGitLabCom() {
    return connections.save(Provider.GITLAB, ServerAddress.GITLAB_COM, TOKEN, VALID);
  }

  @Test
  void should_connect_gitlab_com_with_token_and_ask_to_delete_token_message() {
    // given
    when(api.verify(ServerAddress.GITLAB_COM, TOKEN)).thenReturn(VALID);
    Screen empty = press(Actions.CONNECTIONS).screen();
    press(Actions.CONNECTION_ADD);

    // when
    Screen question = press(Actions.connectDefault(Provider.GITLAB)).screen();
    boolean secretExpected = conversation.expectsSecret(USER);
    Screen saved = text(TOKEN.value());

    // then
    assertThat(empty.html()).contains("Hali ulanish yo'q");
    assertThat(question.html())
        .contains("<b>🦊 gitlab.com</b> uchun token yuboring")
        .contains(
            "https://gitlab.com/-/user_settings/personal_access_tokens?name=voice-dev-bot&amp;scopes=api");
    assertThat(secretExpected).isTrue();
    assertThat(saved.html())
        .startsWith("✅ Ulandi: <b>gitlab.com · @alex</b>")
        .contains("<code>glpat-…1234</code>")
        .doesNotContain(TOKEN.value());
    assertThat(conversation.expectsSecret(USER)).isFalse();
    assertThat(labels(press(Actions.CONNECTIONS).screen())).contains("🟢 🦊 gitlab.com · @alex");
  }

  @Test
  void should_keep_asking_for_self_hosted_address_until_it_is_valid() {
    Screen ask = press(Actions.connectOther(Provider.GITLAB)).screen();
    Screen again = text("ftp://x");
    Screen token = text("git.example.uz/gitlab");

    assertThat(ask.html()).startsWith("✍️ GitLab server manzilini yozing");
    assertThat(again.html()).startsWith("⚠️ Manzil noto'g'ri");
    assertThat(token.html()).contains("<b>🦊 git.example.uz/gitlab</b> uchun token");
    assertThat(conversation.expectsSecret(USER)).isTrue();
  }

  @Test
  void should_explain_rejected_token() {
    when(api.verify(any(), any()))
        .thenThrow(new IntegrationException(Reason.UNAUTHORIZED, "x", null));
    press(Actions.connectDefault(Provider.GITLAB));

    Screen rejected = text(TOKEN.value());

    assertThat(rejected.html()).startsWith("⚠️ Token qabul qilinmadi");
    assertThat(actions(rejected)).containsExactly(Actions.CONNECTIONS);
  }

  @Test
  void should_show_renew_and_remove_connection() {
    ProviderConnection connection = connectGitLabCom();
    when(api.verify(any(), any())).thenReturn(VALID);

    Screen detail = press(Actions.CONNECTION_SHOW + connection.id()).screen();
    Screen renewQuestion = press(Actions.CONNECTION_RENEW + connection.id()).screen();
    Screen renewed = text(GitLabFixtures.NEW_TOKEN.value());
    Screen confirm = press(Actions.CONNECTION_REMOVE_ASK + connection.id()).screen();
    Screen removed = press(Actions.CONNECTION_REMOVE + connection.id()).screen();

    assertThat(detail.html()).contains("👤 @alex").contains("⏳ 2027-01-01 gacha");
    assertThat(renewQuestion.html()).contains("uchun yangi token yuboring");
    assertThat(renewed.html()).startsWith("✅ Ulandi").contains("glpat-…en99");
    assertThat(confirm.html()).contains("ulanishini o'chirasizmi?");
    assertThat(removed.html()).isEqualTo("🗑 Ulanish o'chirildi");
    assertThat(connections.findAll()).isEmpty();
    assertThat(press(Actions.CONNECTION_RENEW + connection.id()).screen().html())
        .startsWith("⚠️ Topilmadi");
    assertThat(press(Actions.CONNECTION_REMOVE_ASK + connection.id()).screen().html())
        .startsWith("⚠️ Topilmadi");
  }

  @Test
  void should_link_existing_repo_from_project_card() {
    // given
    ProviderConnection connection = connectGitLabCom();
    text("/addproject ELT imzo");
    when(api.searchRepos(connection, "")).thenReturn(List.of());
    when(api.searchRepos(connection, "elt")).thenReturn(List.of(REPO));
    when(api.findRepo(connection, REPO.id())).thenReturn(REPO);

    // when
    Screen choose = press(Actions.REPO).screen();
    Screen recent = press(Actions.REPO_PICK + connection.id()).screen();
    Screen found = text("elt");
    Screen linked = press(actions(found).getFirst()).screen();
    Screen card = press(Actions.selectProject(ELT_IMZO.key())).screen();

    // then
    assertThat(choose.html()).startsWith("🔗 <b>ELT imzo</b> — repo ulash");
    assertThat(labels(choose)).contains("📂 Mavjud repo'ni tanlash", "➕ Yangi repo yaratish");
    assertThat(recent.html()).contains("Hech narsa topilmadi");
    assertThat(labels(found)).containsExactly("📂 alex/elt-imzo", "⬅️ Orqaga");
    assertThat(linked.html())
        .startsWith("✅ Repo ulandi")
        .contains("<a href=\"https://gitlab.com/alex/elt-imzo\">alex/elt-imzo</a>");
    assertThat(labels(card)).contains("🟢 alex/elt-imzo");
  }

  @Test
  void should_create_repo_in_chosen_namespace() {
    ProviderConnection connection = connectGitLabCom();
    text("/addproject ELT imzo");
    when(api.namespaces(connection)).thenReturn(List.of(PERSONAL));
    when(api.createRepo(connection, PERSONAL.id(), "elt-imzo", Map.of("CLAUDE.md", "# ELT imzo")))
        .thenReturn(REPO);

    Screen namespaces = press(Actions.REPO_NEW + connection.id()).screen();
    Screen askName = press(actions(namespaces).getFirst()).screen();
    Screen created = text("elt-imzo");

    assertThat(labels(namespaces)).containsExactly("👤 alex", "⬅️ Orqaga");
    assertThat(askName.html()).startsWith("✍️ Yangi repo nomini yozing");
    assertThat(created.html()).startsWith("✅ Repo yaratildi va ulandi");
  }

  @Test
  void should_show_problem_when_repo_name_is_taken() {
    ProviderConnection connection = connectGitLabCom();
    text("/addproject ELT imzo");
    when(api.createRepo(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any()))
        .thenThrow(new IntegrationException(Reason.CONFLICT, "taken", null));
    press(Actions.REPO_NAMESPACE + connection.id() + ":" + PERSONAL.id());

    Screen failed = text("elt-imzo");

    assertThat(failed.html()).startsWith("⚠️ Bu joyda shu nomli repo allaqachon bor");
    assertThat(actions(failed)).containsExactly(Actions.REPO_CHOOSE);
  }

  @Test
  void should_offer_token_renewal_when_gitlab_rejects_it_during_repo_search() {
    ProviderConnection connection = connectGitLabCom();
    text("/addproject ELT imzo");
    when(api.searchRepos(any(), any()))
        .thenThrow(new IntegrationException(Reason.UNAUTHORIZED, "x", null));

    Screen screen = press(Actions.REPO_PICK + connection.id()).screen();

    assertThat(screen.html()).startsWith("Amalni bajarish uchun tokenni yangilang.");
    assertThat(actions(screen)).containsExactly(Actions.CONNECTION_RENEW + connection.id());
  }

  @Test
  void should_switch_and_unlink_repo() {
    ProviderConnection connection = connectGitLabCom();
    text("/addproject ELT imzo");
    when(api.findRepo(connection, REPO.id())).thenReturn(REPO);
    press(Actions.REPO_LINK + connection.id() + ":" + REPO.id());

    Screen choose = press(Actions.REPO_CHOOSE).screen();
    Screen unlinked = press(Actions.REPO_UNLINK).screen();

    assertThat(choose.html()).contains("repo ulash");
    assertThat(unlinked.html()).startsWith("❌ Repo uzildi");
    assertThat(links.find(ELT_IMZO)).isEmpty();
  }

  @Test
  void should_offer_gitlab_connection_when_none_exists_and_require_active_project() {
    Screen noProject = press(Actions.REPO).screen();
    text("/addproject ELT imzo");
    Screen noConnection = press(Actions.REPO).screen();

    assertThat(noProject.html()).startsWith("🔗 Repo projectga ulanadi");
    assertThat(noConnection.html()).contains("Avval xizmat (GitLab) tokenini qo'shing");
    assertThat(actions(noConnection)).contains(Actions.CONNECTION_ADD);
  }

  @Test
  void should_offer_repo_right_after_project_is_created() {
    assertThat(actions(text("/addproject ELT imzo"))).contains(Actions.REPO);
  }

  @Test
  void should_cancel_waiting_for_token_when_command_or_button_follows() {
    press(Actions.connectDefault(Provider.GITLAB));

    Screen home = text("/start");
    press(Actions.connectDefault(Provider.GITLAB));
    press(Actions.CANCEL);

    assertThat(home.html()).startsWith("🎙 <b>Voice Dev Bot</b>");
    assertThat(conversation.expectsSecret(USER)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"gl:c:abc", "gl:l:1", "gl:ns:x:y", "gl:unknown", "gl:r:"})
  void should_ignore_malformed_gitlab_buttons(String data) {
    assertThat(conversation.onButton(USER, data)).isEmpty();
  }

  @Test
  void should_never_answer_stranger() {
    ProviderConnection connection = connectGitLabCom();

    for (String action :
        List.of(
            Actions.CONNECTIONS,
            Actions.CONNECTION_ADD,
            Actions.connectDefault(Provider.GITLAB),
            Actions.connectOther(Provider.GITLAB),
            Actions.CONNECTION_SHOW + connection.id(),
            Actions.CONNECTION_RENEW + connection.id(),
            Actions.CONNECTION_REMOVE_ASK + connection.id(),
            Actions.CONNECTION_REMOVE + connection.id(),
            Actions.REPO,
            Actions.REPO_CHOOSE,
            Actions.REPO_PICK + connection.id(),
            Actions.REPO_NEW + connection.id(),
            Actions.REPO_LINK + connection.id() + ":1",
            Actions.REPO_NAMESPACE + connection.id() + ":1",
            Actions.REPO_UNLINK)) {
      assertThat(conversation.onButton(STRANGER, action)).as(action).isEmpty();
    }
    assertThat(conversation.expectsSecret(STRANGER)).isFalse();
    assertThat(connections.findAll()).hasSize(1);
  }

  @Test
  void should_offer_only_supported_providers_when_adding_connection() {
    Screen choose = press(Actions.CONNECTION_ADD).screen();

    assertThat(choose.html()).isEqualTo("➕ <b>Ulanish qo'shish</b>\n\nQaysi xizmatga ulanamiz?");
    assertThat(labels(choose))
        .containsExactly(
            "🦊 gitlab.com", "✍️ GitLab — o'z serveri (self-hosted)", "✖️ Bekor qilish");
    assertThat(actions(choose))
        .containsSequence(
            Actions.connectDefault(Provider.GITLAB), Actions.connectOther(Provider.GITLAB));
  }

  @Test
  void should_list_connections_with_provider_icon_and_shared_token_note() {
    connectGitLabCom();

    Screen list = press(Actions.CONNECTIONS).screen();

    assertThat(list.html())
        .startsWith("🔗 <b>Ulanishlar</b>\n\n🟢 🦊 gitlab.com · @alex — ⏳ 2027-01-01 gacha")
        .endsWith("<i>Bitta token shu serverdagi barcha projectlar uchun ishlatiladi.</i>");
    assertThat(press(actions(list).getFirst()).screen().html())
        .startsWith("🔗 <b>🦊 GitLab · gitlab.com</b>");
  }

  @Test
  void should_ignore_unknown_provider_and_self_hosted_for_cloud_only_provider() {
    assertThat(conversation.onButton(USER, Actions.CONNECTION_DEFAULT + "NOPE")).isEmpty();
    assertThat(conversation.onButton(USER, Actions.connectOther(Provider.GITHUB))).isEmpty();
  }

  @Test
  void should_reject_provider_without_adapter_after_token() {
    press(Actions.connectDefault(Provider.GITHUB));

    Screen rejected = text(TOKEN.value());

    assertThat(rejected.html()).startsWith("⚠️ Bu xizmat hali qo'llab-quvvatlanmaydi.");
  }
}
