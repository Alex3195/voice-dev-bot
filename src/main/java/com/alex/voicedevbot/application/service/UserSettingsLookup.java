package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.Objects;

/** Foydalanuvchi sozlamalari; hali saqlanmagan bo'lsa — standart til, faol projectsiz. */
public class UserSettingsLookup {

  private final UserSettingsRepository repository;
  private final SpeechLanguage defaultLanguage;

  public UserSettingsLookup(UserSettingsRepository repository, SpeechLanguage defaultLanguage) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.defaultLanguage = Objects.requireNonNull(defaultLanguage, "defaultLanguage");
  }

  public UserSettings current(TelegramUserId user) {
    return repository.find(user).orElseGet(() -> UserSettings.defaults(user, defaultLanguage));
  }
}
