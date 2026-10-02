package com.alex.voicedevbot.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** GitLab tekshirgan token ma'lumoti: egasi, ruxsatlari va tugash sanasi (bo'lmasligi mumkin). */
public final class TokenInfo {

  /** Bot repo yaratadi, issue ochadi va yopadi — {@code read_api} yetmaydi. */
  public static final String REQUIRED_SCOPE = "api";

  static final int WARN_DAYS_BEFORE_EXPIRY = 7;

  private final String owner;
  private final Set<String> scopes;
  private final LocalDate expiresAt;

  private TokenInfo(String owner, Set<String> scopes, LocalDate expiresAt) {
    if (owner == null || owner.isBlank()) {
      throw new IllegalArgumentException("Token owner must not be blank");
    }
    this.owner = owner;
    this.scopes = Set.copyOf(scopes);
    this.expiresAt = expiresAt;
  }

  public static TokenInfo expiring(String owner, Set<String> scopes, LocalDate expiresAt) {
    return new TokenInfo(owner, scopes, Objects.requireNonNull(expiresAt, "expiresAt"));
  }

  /** Muddatsiz token (self-hosted GitLab'da ruxsat etilishi mumkin). */
  public static TokenInfo withoutExpiry(String owner, Set<String> scopes) {
    return new TokenInfo(owner, scopes, null);
  }

  public String owner() {
    return owner;
  }

  public Set<String> scopes() {
    return scopes;
  }

  public Optional<LocalDate> expiresAt() {
    return Optional.ofNullable(expiresAt);
  }

  public boolean hasRequiredScope() {
    return scopes.contains(REQUIRED_SCOPE);
  }

  /** GitLab token'ni {@code expires_at} kuni boshlanishi bilan o'chiradi. */
  public TokenStatus status(LocalDate today) {
    if (expiresAt == null) {
      return TokenStatus.ACTIVE;
    }
    if (!today.isBefore(expiresAt)) {
      return TokenStatus.EXPIRED;
    }
    return ChronoUnit.DAYS.between(today, expiresAt) <= WARN_DAYS_BEFORE_EXPIRY
        ? TokenStatus.EXPIRING_SOON
        : TokenStatus.ACTIVE;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TokenInfo that
        && owner.equals(that.owner)
        && scopes.equals(that.scopes)
        && Objects.equals(expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, scopes, expiresAt);
  }

  @Override
  public String toString() {
    return "TokenInfo[owner=%s, scopes=%s, expiresAt=%s]".formatted(owner, scopes, expiresAt);
  }
}
