package com.alex.voicedevbot.application.port.in;

import java.util.List;

/** GitLab ulanishlarini boshqarish natijasi. */
public sealed interface ConnectionResult {

  record Listed(List<ConnectionView> connections) implements ConnectionResult {

    public Listed {
      connections = List.copyOf(connections);
    }
  }

  record Shown(ConnectionView connection) implements ConnectionResult {}

  /** Ulanish qo'shildi yoki tokeni yangilandi. */
  record Saved(ConnectionView connection) implements ConnectionResult {}

  record Removed() implements ConnectionResult {}

  record Rejected(ConnectionProblem problem) implements ConnectionResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements ConnectionResult {}
}
