package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.Optional;

/** Foydalanuvchi sozlamalari: til va faol project. */
public interface UserSettingsRepository {

  Optional<UserSettings> find(TelegramUserId user);

  void save(UserSettings settings);
}
