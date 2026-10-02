package com.alex.voicedevbot.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * GitLab server manzili: {@code https://gitlab.com} yoki self-hosted (yo'l bilan ham bo'lishi
 * mumkin, masalan {@code http://10.0.0.5/gitlab}). Oxiridagi {@code /} olib tashlanadi.
 */
public record ServerAddress(URI uri) {

  public static final ServerAddress GITLAB_COM = parse("https://gitlab.com");

  public ServerAddress {
    Objects.requireNonNull(uri, "uri");
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http")) {
      throw new IllegalArgumentException("GitLab address must use http or https");
    }
    if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null) {
      throw new IllegalArgumentException("GitLab address must be a plain server URL");
    }
  }

  /** Foydalanuvchi yozgan matn: sxemasiz bo'lsa {@code https://} qo'shiladi. */
  public static ServerAddress parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("GitLab address must not be blank");
    }
    String trimmed = text.strip();
    String withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed;
    while (withScheme.endsWith("/")) {
      withScheme = withScheme.substring(0, withScheme.length() - 1);
    }
    try {
      return new ServerAddress(new URI(withScheme));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("GitLab address is not a valid URL", e);
    }
  }

  /** Botda ko'rsatish uchun: {@code gitlab.com} yoki {@code git.example.uz/gitlab}. */
  public String label() {
    String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
    String path = uri.getPath() == null ? "" : uri.getPath();
    return uri.getHost() + port + path;
  }

  /** API manzili, masalan {@code https://gitlab.com/api/v4/user}. */
  public URI api(String pathAndQuery) {
    return URI.create(uri + "/api/v4" + pathAndQuery);
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
