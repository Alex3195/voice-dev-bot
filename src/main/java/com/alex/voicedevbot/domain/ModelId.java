package com.alex.voicedevbot.domain;

import java.util.regex.Pattern;

/** Claude modeli identifikatori, masalan {@code claude-opus-5-5}. */
public record ModelId(String value) {

  private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9.\\-]{0,63}");

  public ModelId {
    if (value == null || !VALID.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid model id");
    }
  }
}
