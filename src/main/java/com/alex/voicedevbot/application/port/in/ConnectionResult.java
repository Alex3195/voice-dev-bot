package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.Provider;
import java.util.List;

/** Ulanishlarni boshqarish natijasi. */
public sealed interface ConnectionResult {

  /**
   * @param providers bot ulana oladigan xizmatlar (adapteri bor)
   */
  record Listed(List<ConnectionView> connections, List<Provider> providers)
      implements ConnectionResult {

    public Listed {
      connections = List.copyOf(connections);
      providers = List.copyOf(providers);
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
