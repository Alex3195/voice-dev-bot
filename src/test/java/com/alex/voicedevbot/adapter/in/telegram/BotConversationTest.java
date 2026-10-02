package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.application.service.BrowseTranscriptsService;
import com.alex.voicedevbot.application.service.ChangeLanguageService;
import com.alex.voicedevbot.application.service.LinkRepoService;
import com.alex.voicedevbot.application.service.ManageGitLabService;
import com.alex.voicedevbot.application.service.ManageGlossaryService;
import com.alex.voicedevbot.application.service.ManageProjectsService;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.InMemoryGitLabConnectionRepository;
import com.alex.voicedevbot.support.InMemoryProjectRepoLinks;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

/** Muloqot oqimlari haqiqiy use-case'lar va xotiradagi repository'lar bilan. */
class BotConversationTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryTranscriptionLog transcriptLog = new InMemoryTranscriptionLog();
  private final InMemoryGitLabConnectionRepository gitLabConnections =
      new InMemoryGitLabConnectionRepository();
  private final InMemoryProjectRepoLinks repoLinks = new InMemoryProjectRepoLinks();
  private final GitLabApi gitLabApi = Mockito.mock(GitLabApi.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T09:00:00Z"), ZoneOffset.UTC);
  private final BotConversation conversation = conversation();

  private BotConversation conversation() {
    AccessPolicy access = new AccessPolicy(Set.of(USER));
    UserSettingsLookup settings =
        new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz"));
    return new BotConversation(
        new ManageProjectsService(access, projects, settings, settingsRepository),
        new ManageGlossaryService(access, projects, settings),
        new ChangeLanguageService(access, settings, settingsRepository),
        new BrowseTranscriptsService(access, transcriptLog),
        new GitLabDialog(
            new ManageGitLabService(access, gitLabConnections, gitLabApi, clock),
            new LinkRepoService(
                access,
                settings,
                gitLabConnections,
                repoLinks,
                gitLabApi,
                project -> java.util.Map.of("CLAUDE.md", project.value()),
                clock)),
        ZoneOffset.UTC);
  }

  /** Jurnalga yozadi; {@code project} {@code null} bo'lsa — projectsiz. */
  private void logTranscript(ProjectName project, String text, Instant at) {
    UserSettings speaker = UserSettings.defaults(USER, new SpeechLanguage("uz"));
    transcriptLog.append(
        TranscriptRecord.of(
            project == null ? speaker : speaker.withActiveProject(project),
            new Transcription(new Transcript(text), "", "whisper.cpp large-v3"),
            new SourceAudio(
                new AudioRef("file-" + text.length(), "audio/ogg"),
                AudioKind.VOICE,
                Duration.ofSeconds(75)),
            at));
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
  void should_show_home_menu_on_start() {
    Screen home = text("/start");

    assertThat(home.html()).contains("Faol project: <i>tanlanmagan</i>").contains("O'zbek");
    assertThat(actions(home))
        .containsExactly(Actions.PROJECTS, Actions.GLOSSARY, Actions.SETTINGS, Actions.HELP);
  }

  @Test
  void should_show_home_menu_for_plain_text_without_pending_question() {
    assertThat(text("salom").html()).startsWith("🎙 <b>Voice Dev Bot</b>");
  }

  @Test
  void should_create_project_through_button_and_typed_name() {
    Screen question = press(Actions.NEW_PROJECT).screen();
    Screen created = text("ELT imzo");

    assertThat(question.html()).startsWith("✍️ Yangi project nomini yozing");
    assertThat(created.html())
        .startsWith("✅ Project qo'shildi va faol qilindi: <b>ELT imzo</b>")
        .contains("Lug'at bo'sh");
    assertThat(actions(created)).contains(Actions.ADD_TERMS);
    assertThat(text("/start").html()).contains("Faol project: <b>ELT imzo</b>");
  }

  @Test
  void should_add_terms_through_button_and_typed_list() {
    text("/addproject ELT imzo");

    Screen question = press(Actions.ADD_TERMS).screen();
    Screen glossary = text("kassa bo'limi, Klaes ,PVX,");

    assertThat(question.html()).contains("<b>ELT imzo</b> uchun atamalarni vergul bilan yozing");
    assertThat(glossary.html())
        .startsWith("✅ Lug'at yangilandi")
        .contains("— 3 atama")
        .contains("<code>kassa bo'limi</code> · <code>Klaes</code> · <code>PVX</code>");
    assertThat(actions(glossary)).contains(Actions.ADD_TERMS, Actions.REMOVE_MODE, Actions.HOME);
  }

  @Test
  void should_keep_waiting_for_name_when_typed_name_is_invalid() {
    press(Actions.NEW_PROJECT);

    assertThat(text("a".repeat(101)).html()).isEqualTo(BotConversation.INVALID_INPUT);
    assertThat(text("ELT imzo").html()).startsWith("✅ Project qo'shildi");
  }

  @Test
  void should_forget_pending_question_when_cancelled() {
    press(Actions.NEW_PROJECT);

    Reply cancelled = press(Actions.CANCEL);
    text("ELT imzo");

    assertThat(cancelled.screen().html()).startsWith("🎙 <b>Voice Dev Bot</b>");
    assertThat(projects.findAll()).isEmpty();
  }

  @Test
  void should_report_existing_project_when_typed_name_already_exists() {
    projects.save(Project.named(ELT_IMZO));
    press(Actions.NEW_PROJECT);

    assertThat(text("elt imzo").html()).startsWith("ℹ️ Bu project allaqachon bor: <b>ELT imzo</b>");
  }

  @Test
  void should_open_project_card_and_make_project_active_when_pressed() {
    projects.save(Project.named(ELT_IMZO));
    projects.save(new Project(new ProjectName("Finbank"), Glossary.of(List.of("PVX"))));

    Reply reply = press(Actions.selectProject("finbank"));

    assertThat(reply.asNewMessage()).isFalse();
    assertThat(reply.screen().html()).startsWith("📁 <b>Finbank</b> · ✅ faol\n📖 Lug'atda 1 atama");
    assertThat(labels(reply.screen()))
        .containsExactly(
            "📝 Transkriptlar",
            "📖 Lug'at",
            "✅ Tasklar",
            "📄 Hujjatlar",
            "🔗 Repo ulash",
            "⬅️ Projectlar");
    assertThat(labels(press(Actions.PROJECTS).screen()))
        .contains("ELT imzo · 0 atama", "✅ Finbank · 1 atama");
  }

  @Test
  void should_keep_card_and_say_soon_for_unfinished_sections() {
    projects.save(Project.named(ELT_IMZO));

    Reply reply = press(Actions.soon(ELT_IMZO.key()));

    assertThat(reply.toast()).isEqualTo(BotConversation.SOON_TOAST);
    assertThat(reply.screen().html()).startsWith("📁 <b>ELT imzo</b>");
  }

  @Test
  void should_list_project_transcripts_newest_first_with_time_duration_and_preview() {
    // given
    projects.save(Project.named(ELT_IMZO));
    logTranscript(ELT_IMZO, "eski", Instant.parse("2026-10-01T08:00:00Z"));
    logTranscript(
        ELT_IMZO,
        "login sahifasida parolni tiklash tugmasini qo'shish kerak",
        Instant.parse("2026-10-02T09:30:00Z"));
    logTranscript(null, "projectsiz", Instant.parse("2026-10-02T10:00:00Z"));

    // when
    Screen list = press(Actions.transcripts(ELT_IMZO.key(), 0)).screen();

    // then
    assertThat(list.html()).startsWith("📝 <b>ELT imzo</b> — transkriptlar");
    assertThat(labels(list))
        .containsExactly(
            "02.10 09:30 · 1:15 · login sahifasida parolni tiklash tugmasi…",
            "01.10 08:00 · 1:15 · eski",
            "⬅️ Orqaga");
    assertThat(actions(list).getFirst()).isEqualTo(Actions.openTranscript(2));
    assertThat(actions(list).getLast()).isEqualTo(Actions.selectProject(ELT_IMZO.key()));
  }

  @Test
  void should_page_through_transcripts() {
    projects.save(Project.named(ELT_IMZO));
    for (int i = 0; i < 10; i++) {
      logTranscript(ELT_IMZO, "matn " + i, Instant.parse("2026-10-02T09:00:00Z").plusSeconds(i));
    }

    Screen first = press(Actions.transcripts(ELT_IMZO.key(), 0)).screen();
    Screen second = press(Actions.transcripts(ELT_IMZO.key(), 1)).screen();

    assertThat(labels(first)).contains("▶️").doesNotContain("◀️");
    assertThat(second.html()).contains("sahifa 2");
    assertThat(labels(second)).hasSize(4).contains("◀️").doesNotContain("▶️");
  }

  @Test
  void should_say_when_project_has_no_transcripts_yet() {
    projects.save(Project.named(ELT_IMZO));

    Screen list = press(Actions.transcripts(ELT_IMZO.key(), 0)).screen();

    assertThat(list.html()).contains("Hali transkript yo'q");
    assertThat(labels(list)).containsExactly("⬅️ Orqaga");
  }

  @Test
  void should_open_transcript_as_new_message_with_its_audio() {
    projects.save(Project.named(ELT_IMZO));
    logTranscript(ELT_IMZO, "kassa <bo'limi>", Instant.parse("2026-10-02T09:30:00Z"));

    Reply reply = press(Actions.openTranscript(1));

    assertThat(reply.asNewMessage()).isTrue();
    assertThat(reply.screen().html())
        .startsWith("📝 <b>Transkript #1</b>\n<i>🕒 02.10.2026 09:30 · 1:15\n📁 ELT imzo · 🌐 uz")
        .endsWith("\n\nkassa &lt;bo'limi&gt;");
    assertThat(reply.screen().attachments())
        .containsExactly(new Screen.Attachment(AudioKind.VOICE, "file-15"));
    assertThat(actions(reply.screen())).containsExactly(Actions.transcripts(ELT_IMZO.key(), 0));
  }

  @Test
  void should_report_missing_transcript() {
    assertThat(press(Actions.openTranscript(42)).toast()).isEqualTo("Transkript topilmadi");
  }

  @Test
  void should_list_transcripts_without_project_from_settings() {
    logTranscript(null, "projectsiz", Instant.parse("2026-10-02T09:30:00Z"));

    Screen settings = press(Actions.SETTINGS).screen();
    Screen list = press(Actions.transcripts(null, 0)).screen();

    assertThat(settings.html()).startsWith("⚙️ <b>Sozlamalar</b>").contains("O'zbek");
    assertThat(actions(settings))
        .containsExactly(
            Actions.LANGUAGES, Actions.GITLAB, Actions.transcripts(null, 0), Actions.HOME);
    assertThat(list.html()).startsWith("📝 <b>Projectsiz transkriptlar</b>");
    assertThat(labels(list)).containsExactly("02.10 09:30 · 1:15 · projectsiz", "⬅️ Orqaga");
    assertThat(actions(list).getLast()).isEqualTo(Actions.SETTINGS);
  }

  @Test
  void should_tell_when_transcripts_project_no_longer_exists() {
    assertThat(press(Actions.transcripts("o'chirilgan", 0)).toast())
        .isEqualTo(BotConversation.PROJECT_NOT_FOUND);
  }

  @ParameterizedTest
  @ValueSource(strings = {"tr:", "tr:abc", "tr:xyz:1", "tr:-:-1", "tro:", "tro:abc"})
  void should_ignore_malformed_transcript_buttons(String data) {
    assertThat(conversation.onButton(USER, data)).isEmpty();
  }

  @Test
  void should_tell_when_pressed_project_no_longer_exists() {
    assertThat(press(Actions.selectProject("o'chirilgan")).toast()).isEqualTo("Project topilmadi");
  }

  @Test
  void should_remove_term_by_button() {
    text("/addproject ELT imzo");
    text("/glossary add kassa, Klaes");

    Reply reply = press(Actions.removeTerm("Klaes"));

    assertThat(reply.toast()).isEqualTo("❌ O'chirildi: Klaes");
    assertThat(labels(reply.screen())).containsExactly("❌ kassa", "✔️ Tayyor");
    assertThat(press(Actions.removeTerm("yo'q")).toast()).isEqualTo("Atama topilmadi");
  }

  @Test
  void should_lay_out_remove_buttons_two_per_row() {
    text("/addproject ELT imzo");
    text("/glossary add a, b, c");

    Screen removeMode = press(Actions.REMOVE_MODE).screen();

    assertThat(removeMode.rows()).hasSize(3);
    assertThat(removeMode.rows().getFirst()).hasSize(2);
  }

  @Test
  void should_change_language_by_button() {
    Reply reply = press(Actions.setLanguage("kk"));

    assertThat(reply.toast()).isEqualTo("✅ Nutq tili: 🇰🇿 Qozoq / Qoraqalpoq");
    assertThat(labels(reply.screen())).contains("✅ 🇰🇿 Qozoq / Qoraqalpoq", "🇺🇿 O'zbek");
    assertThat(press(Actions.LANGUAGES).screen().html()).contains("Qoraqalpoqcha uchun");
    assertThat(actions(reply.screen()).getLast()).isEqualTo(Actions.SETTINGS);
  }

  @Test
  void should_point_to_projects_when_glossary_has_no_active_project() {
    assertThat(actions(press(Actions.GLOSSARY).screen())).contains(Actions.PROJECTS);
    assertThat(press(Actions.ADD_TERMS).screen().html()).contains("Avval projectni tanlang");
  }

  @Test
  void should_show_active_project_under_transcript_and_open_projects_in_new_message() {
    text("/addproject ELT imzo");

    Screen transcript = conversation.transcript(USER, "kassa <bo'limi>");
    Reply projectsReply = press(actions(transcript).getFirst());

    assertThat(transcript.html())
        .startsWith("📝 <b>Matn</b>\n\nkassa &lt;bo'limi&gt;")
        .endsWith("<i>📁 ELT imzo · 🌐 uz</i>");
    assertThat(labels(transcript)).containsExactly("📁 Projectni almashtirish");
    assertThat(projectsReply.asNewMessage()).isTrue();
    assertThat(projectsReply.screen().html()).startsWith("📁 <b>Projectlar</b>");
  }

  @Test
  void should_offer_project_choice_under_transcript_when_none_is_active() {
    Screen transcript = conversation.transcript(USER, "matn");

    assertThat(transcript.html()).contains("project tanlanmagan");
    assertThat(labels(transcript)).containsExactly("📁 Project tanlash");
  }

  @Test
  void should_support_text_commands_as_shortcuts() {
    assertThat(text("/addproject").html()).startsWith("✍️ Yangi project nomini yozing");
    assertThat(text("/addproject ELT imzo").html()).contains("<b>ELT imzo</b>");
    assertThat(text("/project Yo'q").html()).startsWith("⚠️ Project topilmadi");
    assertThat(text("/project elt imzo").html()).startsWith("✅ Faol project: <b>ELT imzo</b>");
    assertThat(text("/project").html()).startsWith("📁 <b>Projectlar</b>");
    assertThat(text("/glossary add PVX, Klaes").html()).contains("— 2 atama");
    assertThat(text("/glossary remove pvx").html()).contains("— 1 atama");
    assertThat(text("/glossary remove").html()).contains("o'chirish uchun atamani bosing");
    assertThat(text("/glossary").html()).contains("<code>Klaes</code>");
    assertThat(text("/glossary add").html()).contains("uchun atamalarni vergul bilan yozing");
    assertThat(labels(text("/lang ru"))).contains("✅ 🇷🇺 Rus");
    assertThat(text("/lang").html()).startsWith("🌐 <b>Nutq tili</b>");
    assertThat(text("/help@voice_dev_bot").html()).startsWith("❓ <b>Qanday ishlatiladi</b>");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lang uzbek", "/glossary rename x"})
  void should_report_invalid_command_input(String command) {
    assertThat(text(command).html()).isEqualTo(BotConversation.INVALID_INPUT);
  }

  @Test
  void should_ignore_unknown_command_and_unknown_button() {
    assertThat(conversation.onText(USER, "/unknown")).isEmpty();
    assertThat(conversation.onButton(USER, "unknown")).isEmpty();
  }

  @Test
  void should_escape_project_names_and_terms() {
    projects.save(new Project(new ProjectName("A<b>&C"), Glossary.of(List.of("<script>"))));
    text("/project A<b>&C");

    assertThat(text("/glossary").html())
        .contains("<b>A&lt;b&gt;&amp;C</b>")
        .contains("<code>&lt;script&gt;</code>");
  }

  @Test
  void should_never_answer_stranger_and_never_wait_for_his_input() {
    assertThat(conversation.onText(STRANGER, "/start")).isEmpty();
    assertThat(conversation.onText(STRANGER, "/help")).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.NEW_PROJECT)).isEmpty();
    assertThat(conversation.onText(STRANGER, "ELT imzo")).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.setLanguage("kk"))).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.ADD_TERMS)).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.removeTerm("x"))).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.SETTINGS)).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.transcripts(null, 0))).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.transcripts("elt imzo", 0))).isEmpty();
    assertThat(conversation.onButton(STRANGER, Actions.openTranscript(1))).isEmpty();
    assertThat(projects.findAll()).isEmpty();
    assertThat(settingsRepository.find(STRANGER)).isEmpty();
  }

  @Test
  void should_offer_every_menu_command() {
    assertThat(BotConversation.MENU)
        .extracting(BotConversation.BotMenuItem::command)
        .containsExactly("start", "project", "glossary", "settings", "lang", "help");
    assertThat(BotConversation.MENU)
        .allSatisfy(
            item -> assertThat(conversation.onText(USER, "/" + item.command())).isPresent());
  }
}
