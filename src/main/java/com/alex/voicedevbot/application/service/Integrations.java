package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IssueTracker;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Har xizmat uchun adapter: ulanish qaysi provayderniki bo'lsa, o'shaning {@link CodeHost} va
 * {@link IssueTracker}i ishlatiladi. Adapterlar {@code config}da ro'yxatga olinadi.
 */
public class Integrations {

  private final Map<Provider, CodeHost> codeHosts;
  private final Map<Provider, IssueTracker> issueTrackers;

  public Integrations(Map<Provider, CodeHost> codeHosts, Map<Provider, IssueTracker> trackers) {
    this.codeHosts = Map.copyOf(codeHosts);
    this.issueTrackers = Map.copyOf(trackers);
  }

  /** Bot ulana oladigan xizmatlar — kod adapteri borlari, {@link Provider} tartibida. */
  public List<Provider> supported() {
    return Arrays.stream(Provider.values()).filter(codeHosts::containsKey).toList();
  }

  public boolean supports(Provider provider) {
    return codeHosts.containsKey(provider);
  }

  CodeHost codeHost(Provider provider) {
    return find(codeHosts, provider);
  }

  CodeHost codeHost(ProviderConnection connection) {
    return codeHost(connection.provider());
  }

  IssueTracker issueTracker(ProviderConnection connection) {
    return find(issueTrackers, connection.provider());
  }

  /** Adapteri olib tashlangan xizmatning eski ulanishi — "server javob bermadi" kabi ko'rinadi. */
  private static <T> T find(Map<Provider, T> adapters, Provider provider) {
    T adapter = adapters.get(provider);
    if (adapter == null) {
      throw new IntegrationException(
          IntegrationException.Reason.UNAVAILABLE, "No adapter for " + provider, null);
    }
    return adapter;
  }
}
