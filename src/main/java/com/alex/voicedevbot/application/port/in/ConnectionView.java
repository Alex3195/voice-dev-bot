package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.TokenStatus;
import java.util.Objects;

/** GitLab ulanishi va bugungi holati. Token faqat {@code masked()} ko'rinishida ko'rsatiladi. */
public record ConnectionView(GitLabConnection connection, TokenStatus status) {

  public ConnectionView {
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(status, "status");
  }

  public long id() {
    return connection.id();
  }
}
