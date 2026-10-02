package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.service.ChangeLanguageService;
import com.alex.voicedevbot.application.service.ManageGlossaryService;
import com.alex.voicedevbot.application.service.ManageProjectsService;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Muloqot oqimlari haqiqiy use-case'lar va xotiradagi repository'lar bilan. */
class BotConversationTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final BotConversation conversation = conversation();

  private BotConversation conversation() {
    AccessPolicy access = new AccessPolicy(Set.of(USER));
    UserSettingsLookup settings =
        new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz"));
    return new BotConversation(
        new ManageProjectsService(access, projects, settings, settingsRepository),
        new ManageGlossaryService(access, projects, settings),
        new ChangeLanguageService(access, settings, settingsRepository));
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
        .containsExactly(Actions.PROJECTS, Actions.GLOSSARY, Actions.LANGUAGES, Actions.HELP);
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
  void should_select_project_by_button_and_mark_it_active() {
    projects.save(Project.named(ELT_IMZO));
    projects.save(Project.named(new ProjectName("Finbank")));

    Reply reply = press(Actions.selectProject("finbank"));

    assertThat(reply.toast()).isEqualTo("✅ Faol project: Finbank");
    assertThat(reply.asNewMessage()).isFalse();
    assertThat(labels(reply.screen()))
        .contains("ELT imzo · 0 atama", "✅ Finbank · 0 atama", "➕ Yangi project");
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
    assertThat(projects.findAll()).isEmpty();
    assertThat(settingsRepository.find(STRANGER)).isEmpty();
  }

  @Test
  void should_offer_every_menu_command() {
    assertThat(BotConversation.MENU)
        .extracting(BotConversation.BotMenuItem::command)
        .containsExactly("start", "project", "glossary", "lang", "help");
    assertThat(BotConversation.MENU)
        .allSatisfy(
            item -> assertThat(conversation.onText(USER, "/" + item.command())).isPresent());
  }
}
