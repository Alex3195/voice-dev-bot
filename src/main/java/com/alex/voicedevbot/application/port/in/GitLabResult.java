package com.alex.voicedevbot.application.port.in;

import java.util.List;

/** GitLab ulanishlarini boshqarish natijasi. */
public sealed interface GitLabResult {

  record Listed(List<ConnectionView> connections) implements GitLabResult {

    public Listed {
      connections = List.copyOf(connections);
    }
  }

  record Shown(ConnectionView connection) implements GitLabResult {}

  /** Ulanish qo'shildi yoki tokeni yangilandi. */
  record Saved(ConnectionView connection) implements GitLabResult {}

  record Removed() implements GitLabResult {}

  record Rejected(GitLabProblem problem) implements GitLabResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements GitLabResult {}
}
