package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.application.port.in.LanguageCommandResult;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChangeLanguageServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final SpeechLanguage UZ = new SpeechLanguage("uz");
  private static final SpeechLanguage KK = new SpeechLanguage("kk");

  private final InMemoryUserSettingsRepository repository = new InMemoryUserSettingsRepository();
  private final UserSettingsLookup settings = new UserSettingsLookup(repository, UZ);
  private final ChangeLanguageService service =
      new ChangeLanguageService(new AccessPolicy(Set.of(USER)), settings, repository);

  @Test
  void should_report_default_language_when_user_never_changed_it() {
    assertThat(service.currentLanguage(USER)).isEqualTo(new LanguageCommandResult.Current(UZ));
  }

  @Test
  void should_change_language_and_keep_active_project() {
    ProjectName project = new ProjectName("ELT imzo");
    repository.save(UserSettings.defaults(USER, UZ).withActiveProject(project));

    LanguageCommandResult result = service.changeLanguage(USER, KK);

    assertThat(result).isEqualTo(new LanguageCommandResult.Changed(KK));
    assertThat(settings.current(USER))
        .isEqualTo(UserSettings.defaults(USER, KK).withActiveProject(project));
  }

  @Test
  void should_deny_without_saving_when_user_is_not_whitelisted() {
    assertThat(service.changeLanguage(STRANGER, KK))
        .isInstanceOf(LanguageCommandResult.AccessDenied.class);
    assertThat(service.currentLanguage(STRANGER))
        .isInstanceOf(LanguageCommandResult.AccessDenied.class);
    assertThat(repository.find(STRANGER)).isEmpty();
  }
}
