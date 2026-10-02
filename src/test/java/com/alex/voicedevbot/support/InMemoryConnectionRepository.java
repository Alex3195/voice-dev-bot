package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.out.ConnectionRepository;
import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TokenInfo;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link ConnectionRepository} xotirada: server + egasi bo'yicha upsert. */
public class InMemoryConnectionRepository implements ConnectionRepository {

  private final Map<Long, ProviderConnection> connections = new HashMap<>();
  private final Map<Long, LocalDate> alertedOn = new HashMap<>();
  private long nextId = 1;

  @Override
  public ProviderConnection save(ServerAddress address, AccessToken token, TokenInfo info) {
    long id =
        connections.values().stream()
            .filter(c -> c.address().equals(address) && c.info().owner().equals(info.owner()))
            .map(ProviderConnection::id)
            .findFirst()
            .orElseGet(() -> nextId++);
    ProviderConnection saved = new ProviderConnection(id, address, token, info);
    connections.put(id, saved);
    alertedOn.remove(id);
    return saved;
  }

  @Override
  public List<ProviderConnection> findAll() {
    return connections.values().stream()
        .sorted(
            Comparator.comparing((ProviderConnection c) -> c.address().toString())
                .thenComparing(c -> c.info().owner()))
        .toList();
  }

  @Override
  public Optional<ProviderConnection> find(long id) {
    return Optional.ofNullable(connections.get(id));
  }

  @Override
  public void remove(long id) {
    connections.remove(id);
  }

  @Override
  public boolean claimExpiryAlert(long id, LocalDate day) {
    return !day.equals(alertedOn.put(id, day));
  }
}
