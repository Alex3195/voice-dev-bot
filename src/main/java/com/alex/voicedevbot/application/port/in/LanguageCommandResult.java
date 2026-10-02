package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.SpeechLanguage;

/** Til buyrug'i natijasi. */
public sealed interface LanguageCommandResult {

  record Current(SpeechLanguage language) implements LanguageCommandResult {}

  record Changed(SpeechLanguage language) implements LanguageCommandResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements LanguageCommandResult {}
}
