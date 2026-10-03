package com.alex.voicedevbot.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Jurnalga yozilgan transkript va uning raqami.
 *
 * @param correctedText Claude faqat tanib olish xatolarini tuzatgan matn
 * @param confirmedText foydalanuvchi task'ni tasdiqlagan paytdagi tuzatilgan matn — STT'ni o'lchash
 *     va fine-tuning uchun eng ishonchli "to'g'ri javob"
 */
public record LoggedTranscript(
    long id,
    TranscriptRecord record,
    Optional<String> correctedText,
    Optional<String> confirmedText) {

  public LoggedTranscript {
    Objects.requireNonNull(record, "record");
    Objects.requireNonNull(correctedText, "correctedText");
    Objects.requireNonNull(confirmedText, "confirmedText");
  }

  /** Claude hali tuzatmagan. */
  public LoggedTranscript(long id, TranscriptRecord record) {
    this(id, record, Optional.empty(), Optional.empty());
  }

  /** Oxirgi tuzatilgan matn tasdiqlangan bilan bir xil. */
  public boolean correctionConfirmed() {
    return correctedText.isPresent() && correctedText.equals(confirmedText);
  }
}
