package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserSettingsRepository implements UserSettingsRepository {

  private final Map<TelegramUserId, UserSettings> settings = new HashMap<>();

  @Override
  public Optional<UserSettings> find(TelegramUserId user) {
    return Optional.ofNullable(settings.get(user));
  }

  @Override
  public void save(UserSettings userSettings) {
    settings.put(userSettings.user(), userSettings);
  }
}
