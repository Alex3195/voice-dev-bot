package com.alex.voicedevbot.domain;

import java.util.Objects;

/** Project qaysi GitLab ulanishi orqali qaysi repo'ga bog'langan. */
public record RepoLink(long connectionId, Repo repo) {

  public RepoLink {
    Objects.requireNonNull(repo, "repo");
  }
}
