package com.alex.voicedevbot.domain;

import java.net.URI;
import java.util.Objects;

/** Task'ga bog'langan Merge Request (GitHub'da Pull Request). */
public record MergeRequest(long iid, String title, State state, URI webUrl) {

  public MergeRequest {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(webUrl, "webUrl");
  }

  public enum State {
    OPENED,
    MERGED,
    CLOSED
  }
}
