package com.alex.voicedevbot.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Bot ulanadigan xizmat. Bitta ulanish (server + token egasi) shu serverdagi barcha projectlar
 * uchun ishlatiladi.
 */
public enum Provider {
  GITLAB("GitLab", ServerAddress.GITLAB_COM, true, Optional.of("api")),
  /** Token turi va ruxsatlari GitHub adapteri bilan aniqlanadi (roadmap → PR H). */
  GITHUB("GitHub", ServerAddress.GITHUB_COM, false, Optional.empty());

  private final String displayName;
  private final ServerAddress defaultAddress;
  private final boolean selfHosted;
  private final Optional<String> requiredScope;

  Provider(
      String displayName,
      ServerAddress defaultAddress,
      boolean selfHosted,
      Optional<String> requiredScope) {
    this.displayName = displayName;
    this.defaultAddress = Objects.requireNonNull(defaultAddress, "defaultAddress");
    this.selfHosted = selfHosted;
    this.requiredScope = requiredScope;
  }

  public String displayName() {
    return displayName;
  }

  /** Bulutdagi server: {@code gitlab.com}, {@code github.com}. */
  public ServerAddress defaultAddress() {
    return defaultAddress;
  }

  /** O'z serveringizni (self-hosted) ulash mumkinmi. */
  public boolean selfHosted() {
    return selfHosted;
  }

  /** Bot repo yaratadi, issue ochadi va yopadi — shu ruxsat bo'lmasa token rad etiladi. */
  public Optional<String> requiredScope() {
    return requiredScope;
  }
}
