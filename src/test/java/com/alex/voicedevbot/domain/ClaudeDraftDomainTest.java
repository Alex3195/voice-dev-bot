package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClaudeDraftDomainTest {

  private static final ModelId OPUS = new ModelId("claude-opus-5-5");

  private static TaskDraft draft(String title, List<String> criteria) {
    return new TaskDraft(
        title,
        " Imzolash sahifasida sertifikat muddati ko'rinsin. ",
        criteria,
        TaskType.FEATURE,
        List.of(new TermCorrection(" elt imza ", "ELT imzo")),
        "Sertifikat muddati ko'rsatiladi.");
  }

  @Test
  void should_render_issue_with_requirement_and_acceptance_criteria() {
    NewTask task =
        draft(" Sertifikat muddatini ko'rsatish ", List.of(" Sana ko'rinadi ", " ", "Rangli"))
            .toNewTask();

    assertThat(task.title()).isEqualTo("Sertifikat muddatini ko'rsatish");
    assertThat(task.description())
        .isEqualTo(
            """
            ## Talab
            Imzolash sahifasida sertifikat muddati ko'rinsin.

            ## Acceptance criteria
            - [ ] Sana ko'rinadi
            - [ ] Rangli""");
  }

  @Test
  void should_omit_criteria_section_when_there_are_none() {
    assertThat(draft("Muddat", List.of()).toNewTask().description())
        .isEqualTo("## Talab\nImzolash sahifasida sertifikat muddati ko'rinsin.");
  }

  @Test
  void should_reject_blank_or_too_long_title() {
    assertThatThrownBy(() -> draft("  ", List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> draft("x".repeat(NewTask.MAX_TITLE_LENGTH + 1), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_strip_correction_words() {
    assertThat(draft("Muddat", List.of()).corrections())
        .containsExactly(new TermCorrection("elt imza", "ELT imzo"));
    assertThatThrownBy(() -> new TermCorrection("x", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TermCorrection(null, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"Claude", "claude opus", "-claude", "claude/opus"})
  void should_reject_invalid_model_id(String value) {
    assertThatThrownBy(() -> new ModelId(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_negative_token_counts() {
    assertThat(new LlmUsage(OPUS, 10, 0, 0, 5).outputTokens()).isEqualTo(5);
    assertThatThrownBy(() -> new LlmUsage(OPUS, -1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LlmUsage(OPUS, 0, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_list_only_missing_valid_terms_once() {
    Glossary glossary = Glossary.of(List.of("ELT imzo", "PVX"));

    assertThat(
            glossary.missing(
                List.of("elt IMZO", " Klaes ", "klaes", " ", "x".repeat(101), "PVX", "Akfa")))
        .containsExactly("Klaes", "Akfa");
  }

  @Test
  void should_keep_model_choice_in_user_settings() {
    UserSettings settings =
        UserSettings.defaults(new TelegramUserId(1), new SpeechLanguage("uz")).withModel(OPUS);

    assertThat(settings.model()).contains(OPUS);
    assertThat(settings.withLanguage(new SpeechLanguage("kk")).model()).contains(OPUS);
    assertThat(settings.withActiveProject(new ProjectName("ELT imzo")).model()).contains(OPUS);
    assertThat(settings).isNotEqualTo(settings.withModel(new ModelId("claude-sonnet-5-5")));
    assertThat(settings.toString()).contains("model=claude-opus-5-5");
    assertThat(UserSettings.defaults(new TelegramUserId(1), new SpeechLanguage("uz")).model())
        .isEmpty();
  }
}
