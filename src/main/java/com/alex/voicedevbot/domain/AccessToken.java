package com.alex.voicedevbot.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xizmat (GitLab, GitHub) access tokeni. Sir: {@link #toString()} va {@link #masked()} faqat
 * chetlarini ko'rsatadi.
 */
public record AccessToken(String value) {

  private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._\\-]{8,512}");
  private static final int VISIBLE_SUFFIX = 4;

  /**
   * GitLab ({@code glpat-}, {@code glrt-}) va GitHub ({@code github_pat_}, {@code ghp_}) tokenlari.
   */
  private static final Pattern KNOWN_PREFIX =
      Pattern.compile("gl[a-z]{2,4}-|github_pat_|gh[pousr]_");

  public AccessToken {
    if (value == null || !ALLOWED.matcher(value.strip()).matches()) {
      throw new IllegalArgumentException("Access token has invalid format");
    }
    value = value.strip();
  }

  /**
   * {@code glpat-…a1b2}, {@code github_pat_…a1b2} — botda va logda shu ko'rinish. Prefiks faqat
   * ma'lum formatlardan olinadi, aks holda sirning bir qismi ochilib qolishi mumkin.
   */
  public String masked() {
    Matcher prefix = KNOWN_PREFIX.matcher(value);
    String shown =
        prefix.lookingAt() && prefix.end() < value.length() - VISIBLE_SUFFIX ? prefix.group() : "";
    return shown + "…" + value.substring(value.length() - VISIBLE_SUFFIX);
  }

  @Override
  public String toString() {
    return "AccessToken[" + masked() + "]";
  }
}
