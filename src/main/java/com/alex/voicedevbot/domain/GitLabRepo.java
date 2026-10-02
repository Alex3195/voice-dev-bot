package com.alex.voicedevbot.domain;

import java.net.URI;
import java.util.Objects;

/**
 * GitLab'dagi repo (project).
 *
 * @param path namespace bilan to'liq yo'l, masalan {@code alex/elt-imzo}
 */
public record GitLabRepo(long id, String path, URI webUrl) {

  public GitLabRepo {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Repository path must not be blank");
    }
    Objects.requireNonNull(webUrl, "webUrl");
  }
}
