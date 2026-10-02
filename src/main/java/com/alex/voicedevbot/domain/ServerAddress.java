package com.alex.voicedevbot.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * Server manzili: {@code https://gitlab.com}, {@code https://github.com} yoki self-hosted (yo'l
 * bilan ham bo'lishi mumkin, masalan {@code http://10.0.0.5/gitlab}). Oxiridagi {@code /} olib
 * tashlanadi. API manzili provayderga bog'liq — uni adapter yasaydi.
 */
public record ServerAddress(URI uri) {

  public static final ServerAddress GITLAB_COM = parse("https://gitlab.com");
  public static final ServerAddress GITHUB_COM = parse("https://github.com");

  public ServerAddress {
    Objects.requireNonNull(uri, "uri");
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http")) {
      throw new IllegalArgumentException("Server address must use http or https");
    }
    if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null) {
      throw new IllegalArgumentException("Server address must be a plain server URL");
    }
  }

  /** Foydalanuvchi yozgan matn: sxemasiz bo'lsa {@code https://} qo'shiladi. */
  public static ServerAddress parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Server address must not be blank");
    }
    String trimmed = text.strip();
    String withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed;
    while (withScheme.endsWith("/")) {
      withScheme = withScheme.substring(0, withScheme.length() - 1);
    }
    try {
      return new ServerAddress(new URI(withScheme));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Server address is not a valid URL", e);
    }
  }

  /** Botda ko'rsatish uchun: {@code gitlab.com} yoki {@code git.example.uz/gitlab}. */
  public String label() {
    String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
    String path = uri.getPath() == null ? "" : uri.getPath();
    return uri.getHost() + port + path;
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
