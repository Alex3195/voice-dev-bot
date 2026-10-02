package com.alex.voicedevbot.domain;

import java.util.Locale;

/**
 * Project nomi. Taqqoslash katta-kichik harfga qaramaydi: "ELT imzo" va "elt IMZO" — bitta project.
 */
public record ProjectName(String value) {

  static final int MAX_LENGTH = 100;

  public ProjectName {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Project name must not be blank");
    }
    value = value.strip();
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Project name is longer than " + MAX_LENGTH);
    }
  }

  public boolean sameAs(ProjectName other) {
    return key().equals(other.key());
  }

  /** Bazada noyoblik va qidiruv uchun normallashtirilgan ko'rinish. */
  public String key() {
    return value.toLowerCase(Locale.ROOT);
  }
}
