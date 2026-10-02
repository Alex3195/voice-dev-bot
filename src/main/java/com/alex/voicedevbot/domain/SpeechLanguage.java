package com.alex.voicedevbot.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nutq tili — STT dvigateliga beriladigan ISO 639-1 kodi ({@code uz}, {@code kk}, {@code ru}).
 *
 * <p>Qoraqalpoq tili Whisper'da yo'q — bunday foydalanuvchilar uchun eng yaqini {@code kk}.
 */
public record SpeechLanguage(String code) {

  private static final Pattern ISO_639_1 = Pattern.compile("[a-z]{2}");

  public SpeechLanguage {
    if (code == null) {
      throw new IllegalArgumentException("Language code must not be null");
    }
    code = code.strip().toLowerCase(Locale.ROOT);
    if (!ISO_639_1.matcher(code).matches()) {
      throw new IllegalArgumentException("Language code must be two letters: " + code);
    }
  }
}
