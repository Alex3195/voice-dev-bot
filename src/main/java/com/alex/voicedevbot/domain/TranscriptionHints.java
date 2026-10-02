package com.alex.voicedevbot.domain;

import java.util.List;
import java.util.Objects;

/**
 * STT'ga beriladigan ishoralar: qaysi tilda gapirilgan va qaysi atamalar uchrashi mumkin.
 *
 * @param vocabulary faol project lug'ati; project tanlanmagan bo'lsa bo'sh
 */
public record TranscriptionHints(SpeechLanguage language, List<String> vocabulary) {

  public TranscriptionHints {
    Objects.requireNonNull(language, "language");
    vocabulary = List.copyOf(vocabulary);
  }

  public static TranscriptionHints languageOnly(SpeechLanguage language) {
    return new TranscriptionHints(language, List.of());
  }
}
