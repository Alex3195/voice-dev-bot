package com.alex.voicedevbot.adapter.out.stt;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * @param serverUrl whisper-server manzili
 * @param basePrompts til kodi → shu tilning namuna matni (yozuv uslubini barqarorlashtiradi)
 * @param timeout bitta audio'ni matnga aylantirish uchun maksimal vaqt
 */
public record WhisperCppSettings(URI serverUrl, Map<String, String> basePrompts, Duration timeout) {

  public WhisperCppSettings {
    Objects.requireNonNull(serverUrl, "serverUrl");
    basePrompts = Map.copyOf(basePrompts);
    Objects.requireNonNull(timeout, "timeout");
  }
}
