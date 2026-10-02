package com.alex.voicedevbot.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Saqlangan ulanish: qaysi xizmat, server, token va token haqida xizmat aytgan ma'lumot. Shu
 * serverdagi barcha projectlar uchun bitta ulanish ishlatiladi.
 */
public record ProviderConnection(
    long id, Provider provider, ServerAddress address, AccessToken token, TokenInfo info) {

  public ProviderConnection {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(info, "info");
  }

  public TokenStatus status(LocalDate today) {
    return info.status(today);
  }

  /** Botda: {@code gitlab.com · @alex}. */
  public String label() {
    return address.label() + " · @" + info.owner();
  }

  /** Token xizmat talab qiladigan ruxsatga egami. */
  public boolean hasRequiredScope() {
    return provider.requiredScope().map(info::hasScope).orElse(true);
  }
}
