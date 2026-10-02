package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.Transcript;
import java.util.Objects;
import java.util.OptionalLong;

/** Ovozli xabarni qayta ishlash natijasi. */
public sealed interface VoiceHandlingResult {

  /**
   * Ovoz muvaffaqiyatli matnga aylantirildi.
   *
   * @param journalId jurnaldagi yozuv raqami; jurnalga yozib bo'lmagan bo'lsa — bo'sh
   */
  record Transcribed(Transcript transcript, OptionalLong journalId) implements VoiceHandlingResult {

    public Transcribed {
      Objects.requireNonNull(transcript, "transcript");
      Objects.requireNonNull(journalId, "journalId");
    }
  }

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements VoiceHandlingResult {}
}
