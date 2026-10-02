package com.alex.voicedevbot.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Foydalanuvchi yuborgan audio: havola, yuborilish turi va davomiyligi.
 *
 * @param duration Telegram bergan davomiylik; noma'lum bo'lsa (masalan, hujjat) {@link
 *     Duration#ZERO}
 */
public record SourceAudio(AudioRef ref, AudioKind kind, Duration duration) {

  public SourceAudio {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(duration, "duration");
    if (duration.isNegative()) {
      throw new IllegalArgumentException("Audio duration must not be negative");
    }
  }
}
