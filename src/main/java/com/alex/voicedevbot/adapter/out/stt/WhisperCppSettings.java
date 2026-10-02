package com.alex.voicedevbot.adapter.out.stt;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * @param serverUrl whisper-server manzili
 * @param language nutq tili (Whisper kodi, masalan {@code uz})
 * @param prompt boshlang'ich matn: yozuv uslubi va atamalar lug'ati; bo'sh bo'lsa yuborilmaydi
 * @param timeout bitta voice'ni matnga aylantirish uchun maksimal vaqt
 */
public record WhisperCppSettings(URI serverUrl, String language, String prompt, Duration timeout) {

  public WhisperCppSettings {
    Objects.requireNonNull(serverUrl, "serverUrl");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(timeout, "timeout");
  }

  boolean hasPrompt() {
    return !prompt.isBlank();
  }
}
