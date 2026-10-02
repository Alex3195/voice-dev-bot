package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsAndHintsTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final SpeechLanguage UZ = new SpeechLanguage("uz");

  @Test
  void should_normalize_language_code() {
    assertThat(new SpeechLanguage(" KK ").code()).isEqualTo("kk");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "uzb", "u1", "ўз"})
  void should_reject_invalid_language_code(String code) {
    assertThatThrownBy(() -> new SpeechLanguage(code)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_start_without_active_project_and_keep_values_when_changed() {
    ProjectName project = new ProjectName("ELT imzo");
    UserSettings defaults = UserSettings.defaults(USER, UZ);

    UserSettings changed =
        defaults.withActiveProject(project).withLanguage(new SpeechLanguage("kk"));

    assertThat(defaults.activeProject()).isEmpty();
    assertThat(changed.user()).isEqualTo(USER);
    assertThat(changed.activeProject()).contains(project);
    assertThat(changed.language().code()).isEqualTo("kk");
    assertThat(changed)
        .isEqualTo(UserSettings.defaults(USER, new SpeechLanguage("kk")).withActiveProject(project))
        .hasSameHashCodeAs(
            UserSettings.defaults(USER, new SpeechLanguage("kk")).withActiveProject(project));
    assertThat(changed.toString()).contains("kk").contains("ELT imzo");
  }

  @Test
  void should_copy_vocabulary_of_hints() {
    List<String> vocabulary = new ArrayList<>(List.of("Klaes"));

    TranscriptionHints hints = new TranscriptionHints(UZ, vocabulary);
    vocabulary.add("PVX");

    assertThat(hints.vocabulary()).containsExactly("Klaes");
    assertThat(TranscriptionHints.languageOnly(UZ).vocabulary()).isEmpty();
  }
}
