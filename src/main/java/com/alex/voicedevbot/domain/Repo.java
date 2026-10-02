package com.alex.voicedevbot.domain;

import java.net.URI;
import java.util.Objects;

/**
 * Kod xizmatidagi repo (GitLab project, GitHub repository).
 *
 * @param path namespace bilan to'liq yo'l, masalan {@code alex/elt-imzo}
 */
public record Repo(long id, String path, URI webUrl) {

  public Repo {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Repository path must not be blank");
    }
    Objects.requireNonNull(webUrl, "webUrl");
  }
}
