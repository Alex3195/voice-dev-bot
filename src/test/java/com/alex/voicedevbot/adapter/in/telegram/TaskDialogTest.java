package com.alex.voicedevbot.adapter.in.telegram;

import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
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
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TaskStatus;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.CodeHostAndTracker;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryConnectionRepository;
import com.alex.voicedevbot.support.InMemoryProjectRepoLinks;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tasklar oqimlari — haqiqiy use-case'lar, soxta GitLab API. */
class TaskDialogTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final LocalDate TODAY = GitLabFixtures.TODAY;
  private static final List<String> LABELS = List.of(ManageTasksUseCase.AI_TASK_LABEL);

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final InMemoryTranscriptionLog transcriptLog = new InMemoryTranscriptionLog();
  private final CodeHostAndTracker api = mock(CodeHostAndTracker.class);
  private final BotConversation conversation = conversation();
  private ProviderConnection connection;

  private BotConversation conversation() {
    AccessPolicy access = new AccessPolicy(Set.of(USER));
    UserSettingsLookup settings =
        new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz"));
    Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    LinkRepoService repos =
        new LinkRepoService(
            access,
            settings,
            connections,
            links,
            GitLabFixtures.integrations(api),
            project -> Map.of(),
            clock);
    BrowseTranscriptsService transcripts = new BrowseTranscriptsService(access, transcriptLog);
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
  void linkedProject() {
    projects.save(Project.named(ELT_IMZO));
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO));
    connection = connections.save(Provider.GITLAB, ServerAddress.GITLAB_COM, TOKEN, VALID);
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));
  }

  private static Task task(long iid, String title, boolean open, LocalDate due, int mrs) {
    return new Task(
        iid,
        title,
        "Tavsif <b>",
        open,
        Optional.ofNullable(due),
        mrs,
        URI.create("https://gitlab.com/alex/elt-imzo/-/issues/" + iid));
  }

  private void issues(Task... tasks) {
    when(api.issues(connection, REPO, ManageTasksService.ISSUE_LIMIT)).thenReturn(List.of(tasks));
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

  @Test
  void should_group_tasks_by_status_with_counts() {
    issues(
        task(1, "A", true, TODAY.minusDays(2), 0),
        task(2, "B", true, null, 0),
        task(3, "C", true, null, 1),
        task(4, "D", false, null, 1),
        task(5, "E", false, null, 0),
        task(6, "F", true, null, 0));

    Screen overview = press(Actions.TASKS).screen();

    assertThat(overview.html())
        .startsWith("✅ <b>ELT imzo</b> — tasklar\n📂 <a href=\"https://gitlab.com/alex/elt-imzo\">")
        .contains(
            "⏰ Muddati o'tgan — 1\n🟢 Ochiq — 2\n🔀 MR ochilgan — 1\n✅ Bajarilgan — 1\n⚪ Yopiq — 1");
    assertThat(labels(overview))
        .containsExactly(
            "⏰ Muddati o'tgan (1)",
            "🟢 Ochiq (2)",
            "🔀 MR ochilgan (1)",
            "✅ Bajarilgan (1)",
            "⚪ Yopiq (1)",
            "➕ Yangi task",
            "⬅️ Kartochka");
  }

  @Test
  void should_invite_to_create_first_task_when_repo_has_none() {
    issues();

    Screen overview = press(Actions.TASKS).screen();

    assertThat(overview.html()).endsWith("Hali task yo'q — birinchisini yarating.");
    assertThat(actions(overview)).contains(Actions.TASK_NEW);
  }

  @Test
  void should_list_group_and_open_task_with_merge_requests() {
    Task task = task(3, "Login sahifasi", true, null, 1);
    issues(task, task(2, "B", true, null, 0));
    when(api.issue(connection, REPO, 3)).thenReturn(task);
    when(api.mergeRequests(connection, REPO, 3))
        .thenReturn(
            List.of(
                new MergeRequest(
                    5, "Fix login", MergeRequest.State.OPENED, URI.create("https://mr/5"))));

    Screen group = press(Actions.taskGroup(TaskStatus.IN_REVIEW, 0)).screen();
    Screen opened = press(actions(group).getFirst()).screen();

    assertThat(group.html()).startsWith("🔀 MR ochilgan · <b>ELT imzo</b>");
    assertThat(labels(group)).containsExactly("#3 · Login sahifasi", "⬅️ Tasklar");
    assertThat(opened.html())
        .startsWith("🔀 <b>#3 Login sahifasi</b>\n🔀 MR ochilgan\n")
        .contains("Tavsif &lt;b&gt;")
        .contains("🔀 ochiq · <a href=\"https://mr/5\">Fix login</a>");
    assertThat(labels(opened)).containsExactly("✔️ Yopish", "⬅️ 🔀 MR ochilgan", "✅ Tasklar");
  }

  @Test
  void should_page_group_and_show_due_date() {
    Task[] many = new Task[ManageTasksService.PAGE_SIZE + 1];
    for (int i = 0; i < many.length; i++) {
      many[i] = task(i + 1, "T" + (i + 1), true, TODAY.minusDays(i + 1L), 0);
    }
    issues(many);

    Screen first = press(Actions.taskGroup(TaskStatus.OVERDUE, 0)).screen();
    Screen second = press(Actions.taskGroup(TaskStatus.OVERDUE, 1)).screen();

    assertThat(labels(first).getFirst()).isEqualTo("#9 · T9 · 📅 23.09.2026");
    assertThat(actions(first)).contains(Actions.taskGroup(TaskStatus.OVERDUE, 1));
    assertThat(second.html()).contains("· sahifa 2");
    assertThat(actions(second)).contains(Actions.taskGroup(TaskStatus.OVERDUE, 0));
  }

  @Test
  void should_close_and_reopen_task() {
    when(api.setIssueOpen(connection, REPO, 3, false)).thenReturn(task(3, "Login", false, null, 0));
    when(api.setIssueOpen(connection, REPO, 3, true)).thenReturn(task(3, "Login", true, null, 0));
    when(api.mergeRequests(connection, REPO, 3)).thenReturn(List.of());

    Screen closed = press(Actions.TASK_CLOSE + 3).screen();
    Screen reopened = press(Actions.TASK_REOPEN + 3).screen();

    assertThat(closed.html()).startsWith("✔️ Task yopildi\n\n⚪ <b>#3 Login</b>");
    assertThat(labels(closed)).contains("↩️ Qayta ochish");
    assertThat(reopened.html()).startsWith("↩️ Task qayta ochildi\n\n🟢 <b>#3 Login</b>");
  }

  @Test
  void should_create_task_from_title_and_description_only_after_confirmation() {
    Task created = task(12, "Login", true, null, 0);
    NewTask draft = new NewTask("Login", "Parolni tiklash");
    when(api.createIssue(connection, REPO, draft, LABELS)).thenReturn(created);

    Screen askTitle = press(Actions.TASK_NEW).screen();
    Screen askDescription = text("Login");
    Screen confirm = text("Parolni tiklash");
    verify(api, never()).createIssue(any(), any(), any(), any());
    Screen done = press(Actions.TASK_CONFIRM).screen();

    assertThat(askTitle.html()).startsWith("✍️ <b>ELT imzo</b> uchun yangi task sarlavhasini");
    assertThat(askDescription.html()).startsWith("✍️ Tavsifni yozing");
    assertThat(confirm.html())
        .contains("📁 ELT imzo → 📂 alex/elt-imzo · 🏷 ai-task")
        .contains("<b>Login</b>\n\nParolni tiklash");
    assertThat(labels(confirm))
        .containsExactly("✅ Yaratish", "✏️ Sarlavha", "✏️ Tavsif", "✖️ Bekor qilish");
    assertThat(done.html()).startsWith("✅ Task yaratildi: <b>#12 Login</b>");
    assertThat(actions(done)).contains(Actions.openTask(12));
    assertThat(press(Actions.TASK_CONFIRM).screen().html()).startsWith("ℹ️ Qoralama topilmadi");
  }

  @Test
  void should_create_task_without_description() {
    when(api.createIssue(connection, REPO, new NewTask("Login", ""), LABELS))
        .thenReturn(task(1, "Login", true, null, 0));
    press(Actions.TASK_NEW);
    text("Login");

    Screen confirm = press(Actions.TASK_SKIP_DESCRIPTION).screen();
    press(Actions.TASK_CONFIRM);

    assertThat(confirm.html()).contains("<i>tavsifsiz</i>");
    verify(api).createIssue(connection, REPO, new NewTask("Login", ""), LABELS);
  }

  @Test
  void should_ask_title_again_when_it_is_too_long() {
    press(Actions.TASK_NEW);

    Screen invalid = text("x".repeat(NewTask.MAX_TITLE_LENGTH + 1));
    Screen next = text("Login");

    assertThat(invalid.html()).isEqualTo(TaskDialog.INVALID_TITLE);
    assertThat(next.html()).startsWith("✍️ Tavsifni yozing");
  }

  @Test
  void should_draft_task_from_transcript_and_allow_editing_before_creation() {
    long id =
        transcriptLog.append(
            TranscriptRecord.of(
                UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO),
                new Transcription(
                    new Transcript("Login sahifasini tuzat. Parol tiklansin"), "", "whisper"),
                new SourceAudio(new AudioRef("f", "audio/ogg"), AudioKind.VOICE, Duration.ZERO),
                Instant.parse("2026-10-02T09:00:00Z")));
    Screen transcript =
        conversation.transcript(
            USER, "Login sahifasini tuzat. Parol tiklansin", OptionalLong.of(id));
    NewTask edited = new NewTask("Login tuzatish", "Login sahifasini tuzat. Parol tiklansin");
    when(api.createIssue(connection, REPO, edited, LABELS))
        .thenReturn(task(4, "Login tuzatish", true, null, 0));

    Reply draft = press(actions(transcript).getFirst());
    Screen askEdit = press(Actions.TASK_EDIT_TITLE).screen();
    Screen confirm = text("Login tuzatish");
    press(Actions.TASK_CONFIRM);

    assertThat(labels(transcript).getFirst()).isEqualTo("✅ Task yaratish");
    assertThat(draft.asNewMessage()).isTrue();
    assertThat(draft.screen().html())
        .contains("<b>Login sahifasini tuzat</b>\n\nLogin sahifasini tuzat. Parol tiklansin");
    assertThat(askEdit.html()).contains("Hozirgi:\n<code>Login sahifasini tuzat</code>");
    assertThat(confirm.html()).contains("<b>Login tuzatish</b>");
    verify(api).createIssue(connection, REPO, edited, LABELS);
  }

  @Test
  void should_edit_description_and_return_to_confirmation() {
    press(Actions.TASK_NEW);
    text("Login");
    text("eski");

    press(Actions.TASK_EDIT_DESCRIPTION);
    Screen back = press(Actions.TASK_REVIEW).screen();
    press(Actions.TASK_EDIT_DESCRIPTION);
    Screen confirm = text("yangi");

    assertThat(back.html()).contains("<b>Login</b>\n\neski");
    assertThat(confirm.html()).contains("<b>Login</b>\n\nyangi");
  }

  @Test
  void should_discard_draft() {
    press(Actions.TASK_NEW);
    text("Login");
    text("tavsif");

    Screen discarded = press(Actions.TASK_DISCARD).screen();

    assertThat(discarded.html()).isEqualTo("✖️ Task yaratilmadi.");
    assertThat(press(Actions.TASK_CONFIRM).screen().html()).startsWith("ℹ️ Qoralama topilmadi");
    verify(api, never()).createIssue(any(), any(), any(), any());
  }

  @Test
  void should_treat_text_after_cancel_as_normal_message() {
    press(Actions.TASK_NEW);
    press(Actions.CANCEL);

    assertThat(text("Login").html()).startsWith("🎙 <b>Voice Dev Bot</b>");
  }

  @Test
  void should_ignore_malformed_task_actions() {
    assertThat(conversation.onButton(USER, Actions.TASK_GROUP + "NOPE:0")).isEmpty();
    assertThat(conversation.onButton(USER, Actions.TASK_OPEN + "abc")).isEmpty();
    assertThat(conversation.onButton(USER, Actions.TASK_PREFIX + "unknown")).isEmpty();
  }

  @Test
  void should_ignore_stranger() {
    assertThat(conversation.onButton(STRANGER, Actions.TASKS)).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.TASK_NEW)).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.taskFromTranscript(1))).isEmpty();
  }
}
