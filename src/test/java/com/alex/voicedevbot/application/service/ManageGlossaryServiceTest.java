package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.in.GlossaryCommandResult;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManageGlossaryServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final SpeechLanguage UZ = new SpeechLanguage("uz");

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final ManageGlossaryService service =
      new ManageGlossaryService(
          new AccessPolicy(Set.of(USER)), projects, new UserSettingsLookup(settingsRepository, UZ));

  private void activateEltImzo() {
    projects.save(Project.named(ELT_IMZO));
    settingsRepository.save(UserSettings.defaults(USER, UZ).withActiveProject(ELT_IMZO));
  }

  @Test
  void should_add_terms_to_active_project_without_duplicates() {
    activateEltImzo();

    service.addTerms(USER, List.of("kassa bo'limi", "Klaes"));
    GlossaryCommandResult result = service.addTerms(USER, List.of("klaes", "PVX"));

    assertThat(result)
        .isEqualTo(
            new GlossaryCommandResult.Shown(ELT_IMZO, List.of("kassa bo'limi", "Klaes", "PVX")));
    assertThat(projects.find(ELT_IMZO).orElseThrow().glossary().terms()).hasSize(3);
  }

  @Test
  void should_remove_terms_ignoring_case() {
    activateEltImzo();
    service.addTerms(USER, List.of("kassa", "Klaes"));

    GlossaryCommandResult result = service.removeTerms(USER, List.of("KLAES"));

    assertThat(result).isEqualTo(new GlossaryCommandResult.Shown(ELT_IMZO, List.of("kassa")));
  }

  @Test
  void should_show_glossary_of_active_project() {
    activateEltImzo();

    assertThat(service.showGlossary(USER))
        .isEqualTo(new GlossaryCommandResult.Shown(ELT_IMZO, List.of()));
  }

  @Test
  void should_ask_for_project_when_user_has_no_active_project() {
    assertThat(service.addTerms(USER, List.of("kassa")))
        .isInstanceOf(GlossaryCommandResult.NoActiveProject.class);
  }

  @Test
  void should_reject_blank_term() {
    activateEltImzo();

    assertThatThrownBy(() -> service.addTerms(USER, List.of(" ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_deny_without_side_effects_when_user_is_not_whitelisted() {
    activateEltImzo();

    assertThat(service.addTerms(STRANGER, List.of("kassa")))
        .isInstanceOf(GlossaryCommandResult.AccessDenied.class);
    assertThat(projects.find(ELT_IMZO).orElseThrow().glossary().isEmpty()).isTrue();
  }
}
