package com.alex.voicedevbot.domain;

import java.util.Objects;

/** Jurnalga yozilgan transkript va uning raqami. */
public record LoggedTranscript(long id, TranscriptRecord record) {

  public LoggedTranscript {
    Objects.requireNonNull(record, "record");
  }
}
