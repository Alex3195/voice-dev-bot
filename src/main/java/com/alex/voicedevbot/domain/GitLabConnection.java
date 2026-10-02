package com.alex.voicedevbot.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Saqlangan GitLab ulanishi: server, token va token haqida GitLab aytgan ma'lumot. */
public record GitLabConnection(long id, GitLabAddress address, GitLabToken token, TokenInfo info) {

  public GitLabConnection {
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
}
