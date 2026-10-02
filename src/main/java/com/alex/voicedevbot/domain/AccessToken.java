package com.alex.voicedevbot.domain;

import java.util.regex.Pattern;

/**
 * GitLab access token. Sir: {@link #toString()} va {@link #masked()} faqat chetlarini ko'rsatadi.
 */
public record AccessToken(String value) {

  private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._\\-]{8,512}");
  private static final int VISIBLE_SUFFIX = 4;

  public AccessToken {
    if (value == null || !ALLOWED.matcher(value.strip()).matches()) {
      throw new IllegalArgumentException("GitLab token has invalid format");
    }
    value = value.strip();
  }

  /** {@code glpat-…a1b2} — botda va logda shu ko'rinish. */
  public String masked() {
    int dash = value.indexOf('-');
    String prefix =
        dash > 0 && dash < value.length() - VISIBLE_SUFFIX ? value.substring(0, dash + 1) : "";
    return prefix + "…" + value.substring(value.length() - VISIBLE_SUFFIX);
  }

  @Override
  public String toString() {
    return "AccessToken[" + masked() + "]";
  }
}
