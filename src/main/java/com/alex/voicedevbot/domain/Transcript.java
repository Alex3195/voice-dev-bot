package com.alex.voicedevbot.domain;

/** Ovozdan olingan matn. */
public record Transcript(String text) {

  public Transcript {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Transcript text must not be blank");
    }
    text = text.strip();
  }
}
