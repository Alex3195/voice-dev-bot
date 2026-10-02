package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.GlossaryCommandResult;
import com.alex.voicedevbot.application.port.in.LanguageCommandResult;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult.ProjectSummary;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TelegramCommandsTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final SpeechLanguage UZ = new SpeechLanguage("uz");

  private final ManageProjectsUseCase projects = mock(ManageProjectsUseCase.class);
  private final ManageGlossaryUseCase glossary = mock(ManageGlossaryUseCase.class);
  private final ChangeLanguageUseCase language = mock(ChangeLanguageUseCase.class);
  private final TelegramCommands commands = new TelegramCommands(projects, glossary, language);

  @Test
  void should_recognize_commands_by_leading_slash() {
    assertThat(TelegramCommands.isCommand("/help")).isTrue();
    assertThat(TelegramCommands.isCommand("salom")).isFalse();
    assertThat(TelegramCommands.isCommand(null)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/help", "/start", "/HELP@voice_dev_bot"})
  void should_show_help_with_current_language(String text) {
    when(language.currentLanguage(USER)).thenReturn(new LanguageCommandResult.Current(UZ));

    assertThat(commands.handle(USER, text))
        .hasValueSatisfying(
            reply -> assertThat(reply).contains("/glossary add").endsWith("Hozirgi til: uz"));
  }

  @Test
  void should_stay_silent_on_help_when_user_is_not_whitelisted() {
    when(language.currentLanguage(USER)).thenReturn(new LanguageCommandResult.AccessDenied());

    assertThat(commands.handle(USER, "/help")).isEmpty();
  }

  @Test
  void should_add_project_with_full_name() {
    when(projects.addProject(USER, ELT_IMZO)).thenReturn(new ProjectCommandResult.Added(ELT_IMZO));

    assertThat(commands.handle(USER, "/addproject   ELT imzo "))
        .contains("Project qo'shildi va faol qilindi: ELT imzo");
  }

  @Test
  void should_explain_usage_when_project_name_is_missing() {
    assertThat(commands.handle(USER, "/addproject")).contains("Foydalanish: /addproject <nom>");
    verifyNoInteractions(projects);
  }

  @Test
  void should_select_project_or_list_projects() {
    when(projects.selectProject(USER, ELT_IMZO))
        .thenReturn(new ProjectCommandResult.Selected(ELT_IMZO));
    when(projects.listProjects(USER))
        .thenReturn(
            new ProjectCommandResult.Listed(
                List.of(
                    new ProjectSummary(ELT_IMZO, 3, true),
                    new ProjectSummary(new ProjectName("Finbank"), 0, false))));

    assertThat(commands.handle(USER, "/project ELT imzo")).contains("Faol project: ELT imzo");
    assertThat(commands.handle(USER, "/project"))
        .hasValueSatisfying(
            reply ->
                assertThat(reply).contains("▶ ELT imzo — 3 atama").contains("• Finbank — 0 atama"));
  }

  @Test
  void should_describe_every_project_outcome() {
    when(projects.addProject(any(), any()))
        .thenReturn(new ProjectCommandResult.AlreadyExists(ELT_IMZO));
    when(projects.selectProject(any(), any()))
        .thenReturn(new ProjectCommandResult.NotFound(ELT_IMZO));
    when(projects.listProjects(USER)).thenReturn(new ProjectCommandResult.Listed(List.of()));

    assertThat(commands.handle(USER, "/addproject elt imzo"))
        .hasValueSatisfying(reply -> assertThat(reply).contains("allaqachon bor"));
    assertThat(commands.handle(USER, "/project x"))
        .hasValueSatisfying(reply -> assertThat(reply).contains("topilmadi"));
    assertThat(commands.handle(USER, "/project"))
        .hasValueSatisfying(reply -> assertThat(reply).contains("Hali project yo'q"));
  }

  @Test
  void should_stay_silent_on_project_commands_when_user_is_not_whitelisted() {
    when(projects.listProjects(USER)).thenReturn(new ProjectCommandResult.AccessDenied());

    assertThat(commands.handle(USER, "/project")).isEmpty();
  }

  @Test
  void should_add_comma_separated_terms_to_glossary() {
    when(glossary.addTerms(USER, List.of("kassa bo'limi", "Klaes", "PVX")))
        .thenReturn(
            new GlossaryCommandResult.Shown(ELT_IMZO, List.of("kassa bo'limi", "Klaes", "PVX")));

    assertThat(commands.handle(USER, "/glossary add kassa bo'limi, Klaes ,PVX,"))
        .contains("ELT imzo lug'ati (3):\nkassa bo'limi, Klaes, PVX");
  }

  @Test
  void should_show_or_remove_glossary_terms() {
    when(glossary.showGlossary(USER))
        .thenReturn(new GlossaryCommandResult.Shown(ELT_IMZO, List.of()));
    when(glossary.removeTerms(USER, List.of("Klaes")))
        .thenReturn(new GlossaryCommandResult.NoActiveProject());

    assertThat(commands.handle(USER, "/glossary"))
        .hasValueSatisfying(reply -> assertThat(reply).contains("lug'ati bo'sh"));
    assertThat(commands.handle(USER, "/glossary remove Klaes"))
        .hasValueSatisfying(reply -> assertThat(reply).startsWith("Avval project tanlang"));
  }

  @Test
  void should_stay_silent_on_glossary_when_user_is_not_whitelisted() {
    when(glossary.showGlossary(USER)).thenReturn(new GlossaryCommandResult.AccessDenied());

    assertThat(commands.handle(USER, "/glossary")).isEmpty();
  }

  @Test
  void should_show_and_change_language() {
    SpeechLanguage kazakh = new SpeechLanguage("kk");
    when(language.currentLanguage(USER)).thenReturn(new LanguageCommandResult.Current(UZ));
    when(language.changeLanguage(USER, kazakh))
        .thenReturn(new LanguageCommandResult.Changed(kazakh));

    assertThat(commands.handle(USER, "/lang"))
        .hasValueSatisfying(reply -> assertThat(reply).startsWith("Nutq tili: uz"));
    assertThat(commands.handle(USER, "/lang KK")).contains("Nutq tili o'zgardi: kk");
  }

  @Test
  void should_stay_silent_on_language_when_user_is_not_whitelisted() {
    when(language.changeLanguage(any(), any()))
        .thenReturn(new LanguageCommandResult.AccessDenied());

    assertThat(commands.handle(USER, "/lang kk")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lang uzbek", "/glossary rename x", "/glossary add  , "})
  void should_report_invalid_input(String text) {
    when(glossary.addTerms(USER, List.of())).thenThrow(new IllegalArgumentException("empty"));

    assertThat(commands.handle(USER, text)).contains(TelegramCommands.INVALID_INPUT);
  }

  @Test
  void should_ignore_unknown_command() {
    assertThat(commands.handle(USER, "/unknown")).isEqualTo(Optional.empty());
    verifyNoInteractions(projects, glossary, language);
  }

  @Test
  void should_pass_only_command_argument_to_use_case() {
    when(projects.selectProject(USER, ELT_IMZO))
        .thenReturn(new ProjectCommandResult.Selected(ELT_IMZO));

    commands.handle(USER, "/project@voice_dev_bot ELT imzo");

    verify(projects).selectProject(USER, ELT_IMZO);
  }
}
