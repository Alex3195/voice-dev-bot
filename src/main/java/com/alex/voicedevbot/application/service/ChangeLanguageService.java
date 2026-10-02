package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.LanguageCommandResult;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;

public class ChangeLanguageService implements ChangeLanguageUseCase {

  private final AccessPolicy accessPolicy;
  private final UserSettingsLookup settings;
  private final UserSettingsRepository settingsRepository;

  public ChangeLanguageService(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      UserSettingsRepository settingsRepository) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
  }

  @Override
  public LanguageCommandResult currentLanguage(TelegramUserId user) {
    if (!accessPolicy.isAllowed(user)) {
      return new LanguageCommandResult.AccessDenied();
    }
    return new LanguageCommandResult.Current(settings.current(user).language());
  }

  @Override
  public LanguageCommandResult changeLanguage(TelegramUserId user, SpeechLanguage language) {
    if (!accessPolicy.isAllowed(user)) {
      return new LanguageCommandResult.AccessDenied();
    }
    settingsRepository.save(settings.current(user).withLanguage(language));
    return new LanguageCommandResult.Changed(language);
  }
}
